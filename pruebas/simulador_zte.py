#!/usr/bin/env python3
"""
Simulador del panel web del ZTE Livebox 6s (ZXHN F6640P), solo para pruebas.

Reproduce lo que la app espera del router real (cookies SID, tokens, login con
sha256, cabecera Check, XML de dispositivos y reglas). No sustituye a probar con
el router de verdad: sirve para detectar si un cambio en el código rompe el flujo.

Uso:  python3 simulador_zte.py [puerto]      (por defecto 8765, contraseña "clave-prueba")
Extras: GET /__log devuelve las peticiones recibidas; GET /__reset reinicia el estado.
"""
import base64
import hashlib
import json
import random
import string
import sys
import threading


def jdump(o):
    # El router real responde JSON compacto, sin espacios.
    return json.dumps(o, separators=(",", ":"), ensure_ascii=False)
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

PASSWORD = "clave-prueba"
USER = "admin"
FORM_TOKEN = "f0rmT0k3n"
LOGIN_TOKEN = "73120948"

LOCK = threading.Lock()


def fresh_state():
    return {
        "sessions": {},
        "log": [],
        "devices": [
            {"HostName": "Port&#225;til-Salón", "MACAddress": "AA:BB:CC:00:00:01", "IPAddress": "192.168.1.34",
             "Active": "1", "DevType": "1", "Mode": 0},
            {"HostName": "Movil-Marga", "MACAddress": "aa:bb:cc:00:00:02", "IPAddress": "192.168.1.35",
             "Active": "1", "DevType": "2", "Mode": 0},
            {"HostName": "Tele", "MACAddress": "aa:bb:cc:00:00:03", "IPAddress": "192.168.1.40",
             "Active": "0", "DevType": "", "Mode": 2},
        ],
    }


STATE = fresh_state()


def rand(n):
    return "".join(random.choice(string.ascii_letters + string.digits) for _ in range(n))


def hex_escape(s):
    return "".join("\\x%02x" % ord(c) for c in s)


def xml_obj(tag, instances):
    out = []
    for inst in instances:
        pairs = "".join("<ParaName>%s</ParaName><ParaValue>%s</ParaValue>" % (k, v) for k, v in inst.items())
        out.append("<Instance>%s</Instance>" % pairs)
    return "<ajax_response_xml_root><IF_ERRORSTR>SUCC</IF_ERRORSTR><%s>%s</%s></ajax_response_xml_root>" % (
        tag, "".join(out), tag)


def find_dev(mac):
    for d in STATE["devices"]:
        if d["MACAddress"].lower() == (mac or "").lower():
            return d
    return None


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def _send(self, body, ctype="text/html", cookie=None, code=200):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        if cookie:
            self.send_header("Set-Cookie", cookie)
        self.end_headers()
        self.wfile.write(data)

    def _session(self):
        ck = self.headers.get("Cookie", "")
        for part in ck.split(";"):
            part = part.strip()
            if part.startswith("SID="):
                sid = part[4:]
                if sid in STATE["sessions"]:
                    return sid, STATE["sessions"][sid]
        return None, None

    def do_GET(self):
        with LOCK:
            self._handle("GET", None)

    def do_POST(self):
        n = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(n).decode("utf-8")
        with LOCK:
            self._handle("POST", body)

    def _handle(self, method, body):
        global STATE
        url = urlsplit(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query, keep_blank_values=True).items()}
        if url.path == "/__log":
            return self._send(json.dumps(STATE["log"]), "application/json")
        if url.path == "/__fail_next_post":
            # Simula que el router cierra la sesión: el próximo guardado responde SessionTimeout.
            STATE["fail_next_post"] = True
            return self._send("ok")
        if url.path == "/__reset":
            STATE = fresh_state()
            return self._send("ok")

        STATE["log"].append({"method": method, "path": self.path, "body": body,
                             "check": self.headers.get("Check") is not None,
                             "xrw": self.headers.get("X-Requested-With")})
        sid, sess = self._session()
        t = q.get("_type")
        tag = q.get("_tag")

        if method == "GET" and url.path == "/" and not t:
            if sess is None:
                sid = rand(16)
                STATE["sessions"][sid] = {"logged": False, "token": rand(12)}
                return self._send("<html>login Frm_Password</html>", cookie="SID=%s; path=/; HttpOnly" % sid)
            if not sess["logged"]:
                return self._send("<html>login Frm_Password</html>")
            sess["token"] = rand(12)
            return self._send('<script>var _sessionTmpToken = "%s";</script>' % hex_escape(sess["token"]))

        if sess is None:
            return self._send("<html>login_entry Frm_Password</html>")

        if t == "loginData" and tag == "login_entry" and method == "GET":
            return self._send(jdump({"lockingTime": 0, "sess_token": FORM_TOKEN, "login_need_refresh": False}),
                              "application/json")
        if t == "loginData" and tag == "login_token":
            return self._send("<ajax_response_xml_root>%s</ajax_response_xml_root>" % LOGIN_TOKEN, "text/xml")
        if t == "loginData" and tag == "login_entry" and method == "POST":
            f = {k: v[0] for k, v in parse_qs(body, keep_blank_values=True).items()}
            want = hashlib.sha256((PASSWORD + LOGIN_TOKEN).encode()).hexdigest()
            if f.get("Username") == USER and f.get("Password") == want and f.get("_sessionTOKEN") == FORM_TOKEN:
                sess["logged"] = True
                return self._send(jdump({"login_need_refresh": True}), "application/json")
            return self._send(jdump({"login_need_refresh": False, "loginErrMsg": "Contraseña incorrecta"}),
                              "application/json")
        if t == "loginData" and tag == "logout_entry":
            sess["logged"] = False
            return self._send("{}", "application/json")

        if not sess["logged"]:
            return self._send("<html>login_entry Frm_Password</html>")

        if t == "menuData" and tag == "osp_home_lua.lua":
            if sess.get("wrote"):
                # Comportamiento observado en el ZTE real: tras guardar un cambio, listar en la
                # misma sesión responde SessionTimeout (el cambio sí queda aplicado).
                return self._send("<ajax_response_xml_root><IF_ERRORSTR>SessionTimeout</IF_ERRORSTR></ajax_response_xml_root>",
                                  "text/xml")
            insts = [{k: d[k] for k in ("HostName", "MACAddress", "IPAddress", "Active", "DevType")}
                     for d in STATE["devices"]]
            return self._send(xml_obj("OBJ_ACCESSDEVEXT_ID", insts), "text/xml")
        if t == "menuView" and tag == "vmenu-TimeControl":
            sess["token"] = rand(12)
            return self._send('<div>panel</div><script>_sessionTmpToken = "%s";</script>' % hex_escape(sess["token"]))
        if t == "menuData" and tag == "osp_access_restriction_lua.lua" and method == "GET":
            d = find_dev(q.get("Mac"))
            if d is None:
                return self._send(xml_obj("OBJ_FWTIME_ID", []), "text/xml")
            policy = ("1" if d["Mode"] == 1 else "0") * 168
            inst = {"_InstID": "DEV.FW.TC%d" % (STATE["devices"].index(d) + 1), "DeviceName": d["HostName"],
                    "Enable": "1", "Mode": str(d["Mode"]), "TimePolicy": policy}
            return self._send(xml_obj("OBJ_FWTIME_ID", [inst]), "text/xml")
        if t == "menuData" and tag == "osp_access_restriction_lua.lua" and method == "POST":
            f = {k: v[0] for k, v in parse_qs(body, keep_blank_values=True).items()}
            err = None
            if STATE.pop("fail_next_post", False):
                return self._send("<ajax_response_xml_root><IF_ERRORSTR>SessionTimeout</IF_ERRORSTR></ajax_response_xml_root>",
                                  "text/xml")
            chk = self.headers.get("Check")
            try:
                if chk is None or len(base64.b64decode(chk)) != 512:
                    err = "CheckFail"
            except Exception:
                err = "CheckFail"
            if f.get("_sessionTOKEN") != sess["token"]:
                err = "TokenFail"
            d = find_dev(f.get("Mac"))
            if d is None:
                err = err or "NoDev"
            mode = f.get("Mode")
            pol = f.get("TimePolicy", "")
            if mode not in ("0", "1") or len(pol) != 168 or set(pol) != {"1" if mode == "1" else "0"}:
                err = err or "BadArgs"
            if err:
                return self._send("<ajax_response_xml_root><IF_ERRORSTR>%s</IF_ERRORSTR></ajax_response_xml_root>"
                                  % err, "text/xml")
            d["Mode"] = int(mode)
            sess["wrote"] = True
            return self._send("<ajax_response_xml_root><IF_ERRORSTR>SUCC</IF_ERRORSTR></ajax_response_xml_root>",
                              "text/xml")
        return self._send("not found", code=404)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
