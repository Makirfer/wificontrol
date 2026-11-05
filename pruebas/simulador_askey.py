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
logged = {"ok": False}
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
        self.end_headers()
        self.wfile.write(b)

    def do_POST(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        f = {k: v[0] for k, v in parse_qs(self.rfile.read(n).decode(), keep_blank_values=True).items()}
        p = self.path.split("?")[0]
        if p == "/cgi-bin/cbCheckLogin.xml":
            ok = f.get("rqUsername") == xor(USER) and f.get("rqPasswd") == xor(PASS)
            logged["ok"] = ok
            return self.send("<cbCheckLogin><Result>%d</Result><SessionID>NotUsed</SessionID><BruteTime>0</BruteTime>"
                             "</cbCheckLogin>" % (0 if ok else 1))
        if p == "/cgi-bin/cbCheckLogout.xml":
            logged["ok"] = False
            return self.send("<cbCheckLogout><Result>0</Result></cbCheckLogout>")
        if p == "/cgi-bin/cbGetLanHostList.xml":
            if not logged["ok"]:
                return self.send("<cbGetLanHostList><Result>1</Result></cbGetLanHostList>")
            items = "".join("<rtm_cfg_host><hostname>%s</hostname><mac_addr>%s</mac_addr><ipv4_addr>%s</ipv4_addr>"
                            "<intf_type>%s</intf_type><is_active>%s</is_active></rtm_cfg_host>" % h for h in HOSTS)
            return self.send("<cbGetLanHostList><Result>0</Result>" + items + "</cbGetLanHostList>")
        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 8766), H).serve_forever()
