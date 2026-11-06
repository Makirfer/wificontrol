#!/usr/bin/env python3
"""
Simulador mínimo del panel avanzado del Askey RTF8316VW (Movistar), solo para pruebas.
Reproduce lo observado en una grabación real: login con usuario y contraseña ofuscados
(XOR 0x1f), sesión por cliente y lista de dispositivos en XML.
Uso: python3 simulador_askey.py [puerto]   (usuario 1234, contraseña "prueba-askey")
"""
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

USER, PASS = "1234", "prueba-askey"
logged = {"ok": False, "basic": False}
xor = lambda s: "".join(chr(ord(c) ^ 0x1F) for c in s)
HOSTS = [("Mac", "02:00:00:00:00:35", "192.168.1.35", "3", "1"),
         ("iPhone", "02:00:00:00:00:33", "192.168.1.33", "3", "1"),
         ("", "02:00:00:00:00:34", "192.168.1.34", "3", "1"),
         ("Pc-Familia", "02:00:00:00:00:36", "192.168.1.36", "3", "0")]


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def send(self, body):
        b = ('<?xml version="1.0" encoding="UTF-8" ?>\n<Response>' + body + "</Response>").encode()
        self.send_response(200)
        self.send_header("Content-Type", 'text/xml; charset="utf-8"')
        self.send_header("Content-Length", str(len(b)))
        for k, v in getattr(self, "extra", []):
            self.send_header(k, v)
        self.extra = []
        self.end_headers()
        self.wfile.write(b)

    def do_POST(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        f = {k: v[0] for k, v in parse_qs(self.rfile.read(n).decode(), keep_blank_values=True).items()}
        p = self.path.split("?")[0]
        if p == "/cgi-bin/te_acceso_router.cgi":
            # Panel básico de Movistar (grabación real del 06/11): sin esto, la sesión avanzada no vale.
            logged["basic"] = f.get("loginUsername") == xor(USER) and f.get("loginPassword") == xor(PASS)
            self.send_response(302)
            self.send_header("Location", f.get("curWebPage", "/te_wifi.asp") if logged["basic"] else "/")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if p == "/cgi-bin/cbCheckLogin.xml":
            ok = f.get("rqUsername") == xor(USER) and f.get("rqPasswd") == xor(PASS)
            logged["ok"] = ok
            return self.send("<cbCheckLogin><Result>%d</Result><SessionID>NotUsed</SessionID>"
                             "<LoginTime>20261106095900</LoginTime><LoginRole>admin</LoginRole><BruteTime>0</BruteTime>"
                             "</cbCheckLogin>" % (0 if ok else 1))
        if p == "/cgi-bin/cbCheckLogout.xml":
            logged["ok"] = False
            return self.send("<cbCheckLogout><Result>0</Result></cbCheckLogout>")
        if p == "/cgi-bin/cbCheckSession.xml":
            # La sesión se valida con las cookies que crea el JavaScript y exige el acceso al panel básico.: la sesión se valida con las cookies
            # que el JavaScript crea a partir de la respuesta del login (fecha y hora de entrada).
            ck = self.headers.get("Cookie", "")
            ok = logged["ok"] and logged["basic"] and "LoginTime=09%3A59" in ck and "LoginDate=11%2F06" in ck and "SessionID=NotUsed" in ck
            return self.send("<cbCheckSession><Result>%d</Result><LoginRole>admin</LoginRole></cbCheckSession>"
                             % (0 if ok else 1))
        if p == "/cgi-bin/cbGetLanHostList.xml":
            # Hipótesis tras el fallo real de la receta 0.1.0 (HTTP 302): el panel exige las cookies
            # que crea su JavaScript al entrar; sin ellas, redirige a la página de acceso.
            ck = self.headers.get("Cookie", "")
            if "LoginName=" not in ck or "TimeOut=" not in ck:
                self.send_response(302)
                self.send_header("Location", "/me_configuracion_avanzada.asp")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            if not logged["ok"] or not logged["basic"]:
                return self.send("<cbGetLanHostList><Result>1</Result></cbGetLanHostList>")
            items = "".join("<rtm_cfg_host><hostname>%s</hostname><mac_addr>%s</mac_addr><ipv4_addr>%s</ipv4_addr>"
                            "<intf_type>%s</intf_type><is_active>%s</is_active></rtm_cfg_host>" % h for h in HOSTS)
            return self.send("<cbGetLanHostList><Result>0</Result>" + items + "</cbGetLanHostList>")
        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        if self.path.split("?")[0] in ("/", "/te_wifi.asp", "/avanzada.asp", "/me_configuracion_avanzada.asp"):
            b = b"<html><head><title>RTF8316VW</title></head><body>avanzada</body></html>"
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(b)))
            self.end_headers()
            self.wfile.write(b)
            return
        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 8766), H).serve_forever()
