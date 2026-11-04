/*
 * Grabador de WiFi Casa (modo aprendizaje).
 * Se inyecta en el panel del router dentro de la app y anota lo que el panel envía y recibe
 * (peticiones XHR y fetch, formularios, páginas y scripts propios del panel), para poder
 * escribir después la receta de ese router. No envía nada a Internet: todo va a la app
 * a través de window.WifiCasaRec, y la app lo sanea antes de guardarlo.
 */
(function () {
  var MAX = 20000;
  function cut(s) {
    s = s == null ? '' : String(s);
    return s.length > MAX ? s.slice(0, MAX) + '…[recortado]' : s;
  }
  function out(o) {
    try {
      var bridge = window.WifiCasaRec || (window.top && window.top.WifiCasaRec);
      if (bridge) bridge.record(JSON.stringify(o));
    } catch (e) { /* nada */ }
  }
  function headersToObj(h) {
    var r = {};
    try {
      if (!h) return r;
      if (typeof h.forEach === 'function') { h.forEach(function (v, k) { r[k] = String(v); }); return r; }
      for (var k in h) if (Object.prototype.hasOwnProperty.call(h, k)) r[k] = String(h[k]);
    } catch (e) { /* nada */ }
    return r;
  }
  function bodyToStr(b) {
    if (b == null) return '';
    if (typeof b === 'string') return b;
    try {
      if (typeof URLSearchParams !== 'undefined' && b instanceof URLSearchParams) return b.toString();
      if (typeof FormData !== 'undefined' && b instanceof FormData) {
        var parts = [];
        b.forEach(function (v, k) { parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(typeof v === 'string' ? v : '[archivo]')); });
        return parts.join('&');
      }
    } catch (e) { /* nada */ }
    return '[contenido no textual]';
  }

  function rec(w) {
    if (!w || w.__wcRec) return;
    try { w.__wcRec = true; } catch (e) { return; }
    var d = w.document;

    // --- XMLHttpRequest
    var XP = w.XMLHttpRequest && w.XMLHttpRequest.prototype;
    if (XP) {
      var O = XP.open, S = XP.send, H = XP.setRequestHeader;
      XP.open = function (m, u) {
        this.__wc = { type: 'xhr', method: String(m).toUpperCase(), url: String(u), headers: {}, page: w.location.href };
        return O.apply(this, arguments);
      };
      XP.setRequestHeader = function (k, v) {
        if (this.__wc) this.__wc.headers[k] = String(v);
        return H.apply(this, arguments);
      };
      XP.send = function (b) {
        var x = this, r = x.__wc;
        if (r) {
          r.body = bodyToStr(b);
          r.t = Date.now();
          x.addEventListener('loadend', function () {
            try {
              r.status = x.status;
              r.response = (x.responseType === '' || x.responseType === 'text') ? cut(x.responseText) : '[' + x.responseType + ']';
              r.responseHeaders = x.getAllResponseHeaders();
            } catch (e) { /* nada */ }
            out(r);
          });
        }
        return S.apply(this, arguments);
      };
    }

    // --- fetch
    if (w.fetch) {
      var F = w.fetch;
      w.fetch = function (input, init) {
        var r = {
          type: 'fetch',
          method: String((init && init.method) || (input && input.method) || 'GET').toUpperCase(),
          url: String((input && input.url) || input),
          headers: headersToObj(init && init.headers),
          body: bodyToStr(init && init.body),
          page: w.location.href,
          t: Date.now()
        };
        return F.apply(this, arguments).then(function (res) {
          try {
            res.clone().text().then(function (t) { r.status = res.status; r.response = cut(t); out(r); });
          } catch (e) { out(r); }
          return res;
        }, function (err) { r.error = String(err); out(r); throw err; });
      };
    }

    // --- formularios clásicos (sin JavaScript)
    d.addEventListener('submit', function (ev) {
      var f = ev.target, fields = [];
      try {
        for (var i = 0; i < f.elements.length; i++) {
          var el = f.elements[i];
          if (!el.name) continue;
          if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) continue;
          if (el.type === 'password') { out({ type: 'secret', value: el.value }); fields.push([el.name, '«CONTRASEÑA»']); }
          else fields.push([el.name, el.value]);
        }
      } catch (e) { /* nada */ }
      out({ type: 'form', method: String(f.method || 'GET').toUpperCase(), url: String(f.action || w.location.href), fields: fields, t: Date.now() });
    }, true);

    // --- lo que escribe en campos de contraseña: solo para poder borrarlo de la grabación
    d.addEventListener('change', function (ev) {
      var el = ev.target;
      if (el && el.type === 'password' && el.value) out({ type: 'secret', value: el.value });
    }, true);

    // --- qué pulsa el usuario (para entender la secuencia)
    d.addEventListener('click', function (ev) {
      var el = ev.target;
      if (!el) return;
      var txt = String(el.innerText || el.value || el.title || el.alt || el.id || '').trim().slice(0, 60);
      out({ type: 'click', tag: el.tagName, text: txt, id: el.id || '', name: el.name || '', page: w.location.href, t: Date.now() });
    }, true);

    // --- foto de la página y scripts propios del panel (ahí suele estar cómo se cifra la contraseña)
    function snapshot() {
      out({ type: 'page', url: w.location.href, title: d.title, html: cut(d.documentElement ? d.documentElement.outerHTML : ''), t: Date.now() });
      var scripts = d.getElementsByTagName('script');
      for (var i = 0; i < scripts.length; i++) {
        var src = scripts[i].src;
        if (!src || src.indexOf(w.location.origin) !== 0) continue;
        if (/jquery|bootstrap|angular|react|vue|lodash|moment|polyfill|\.min\.js/i.test(src)) continue;
        (function (u) {
          try {
            var x = new w.XMLHttpRequest();
            x.__wc = null; // no grabar esta descarga como petición del panel
            O.call(x, 'GET', u, true);
            x.onload = function () { out({ type: 'script', url: u, code: cut(x.responseText) }); };
            S.call(x);
          } catch (e) { /* nada */ }
        })(src);
      }
    }
    if (d.readyState === 'loading') d.addEventListener('DOMContentLoaded', snapshot); else snapshot();

    // --- marcos (iframes) del mismo origen: muchos paneles antiguos los usan
    w.setInterval(function () {
      try {
        for (var i = 0; i < w.frames.length; i++) {
          try { rec(w.frames[i]); } catch (e) { /* otro origen */ }
        }
      } catch (e) { /* nada */ }
    }, 800);
  }

  rec(window);
})();
