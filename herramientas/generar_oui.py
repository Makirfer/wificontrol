#!/usr/bin/env python3
"""
Genera app/src/main/assets/oui.txt: la marca de cada aparato a partir de su MAC, sin conexión.

Fuente: registro público de fabricantes del IEEE (MA-L), tal como lo publica el proyecto
oui-data (https://github.com/silverwind/oui-data, licencia BSD-2-Clause). Solo se guardan
las marcas de consumo que interesan en una casa, con un nombre corto y, si la marca lo deja
claro, una pista del tipo de aparato.

Uso: python3 herramientas/generar_oui.py <ruta a oui-data/index.json>
"""
import json, re, sys, os

# (expresión sobre el nombre del IEEE, marca que se muestra, pista de tipo o "")
BRANDS = [
    (r"^APPLE,? INC", "Apple", ""),
    (r"^SAMSUNG ELECTRO", "Samsung", ""),
    (r"XIAOMI", "Xiaomi", ""),
    (r"^HUAWEI (TECHNOLOGIES|DEVICE)", "Huawei", ""),
    (r"^HONOR DEVICE", "Honor", "phone"),
    (r"OPPO", "OPPO", "phone"),
    (r"^VIVO MOBILE", "vivo", "phone"),
    (r"ONEPLUS", "OnePlus", "phone"),
    (r"REALME", "realme", "phone"),
    (r"^MOTOROLA MOBILITY", "Motorola", "phone"),
    (r"^NOTHING TECHNOLOGY", "Nothing", "phone"),
    (r"^GOOGLE,? INC|^GOOGLE LLC", "Google", ""),
    (r"^AMAZON TECHNOLOGIES|^AMAZON\.COM", "Amazon", ""),
    (r"^SONY INTERACTIVE ENTERTAINMENT", "Sony PlayStation", "console"),
    (r"^SONY ", "Sony", ""),
    (r"^NINTENDO", "Nintendo", "console"),
    (r"^VALVE CORPORATION", "Valve", "console"),
    (r"^OCULUS VR|^META PLATFORMS TECHNOLOGIES", "Meta Quest", "console"),
    (r"^MICROSOFT", "Microsoft", ""),
    (r"^LG ELECTRONICS|^LG INNOTEK", "LG", ""),
    (r"^LENOVO", "Lenovo", ""),
    (r"^HEWLETT PACKARD|^HP INC", "HP", ""),
    (r"^DELL ", "Dell", "computer"),
    (r"^ASUSTEK", "ASUS", ""),
    (r"^ACER ", "Acer", "computer"),
    (r"^INTEL CORPORATE", "Intel", "computer"),
    (r"^RASPBERRY PI", "Raspberry Pi", "computer"),
    (r"^TP-LINK", "TP-Link", ""),
    (r"^ESPRESSIF", "Espressif", "smart"),
    (r"^TUYA", "Tuya", "smart"),
    (r"^SHELLY|^ALLTERCO", "Shelly", "smart"),
    (r"^SIGNIFY|^PHILIPS LIGHTING", "Philips Hue", "smart"),
    (r"^IROBOT", "iRobot", "smart"),
    (r"ECOVACS", "Ecovacs", "smart"),
    (r"ROBOROCK", "Roborock", "smart"),
    (r"^DYSON", "Dyson", "smart"),
    (r"^ROKU", "Roku", "tv"),
    (r"^SONOS", "Sonos", "speaker"),
    (r"^BOSE ", "Bose", "speaker"),
    (r"^CANON ", "Canon", "printer"),
    (r"^SEIKO EPSON", "Epson", "printer"),
    (r"^BROTHER INDUSTRIES", "Brother", "printer"),
    (r"^HISENSE", "Hisense", "tv"),
    (r"^TCL ", "TCL", ""),
    (r"^PANASONIC", "Panasonic", ""),
    (r"^SHARP ", "Sharp", ""),
    (r"^VESTEL", "Vestel", "tv"),
    (r"^GARMIN", "Garmin", "watch"),
    (r"^FITBIT", "Fitbit", "watch"),
    (r"^WITHINGS", "Withings", "watch"),
    (r"HIKVISION", "Hikvision", "camera"),
    (r"EZVIZ", "EZVIZ", "camera"),
    (r"^RING LLC", "Ring", "camera"),
    (r"^ARLO", "Arlo", "camera"),
    (r"^NOKIA (CORPORATION|SOLUTIONS|SHANGHAI)", "Nokia", ""),
    (r"^ZTE CORPORATION", "ZTE", ""),
    (r"^SAGEMCOM", "Sagemcom", "network"),
    (r"^ARCADYAN", "Arcadyan", "network"),
    (r"^ASKEY", "Askey", "network"),
    (r"^MITRASTAR", "MitraStar", "network"),
    (r"^SERCOMM", "Sercomm", "network"),
    (r"^TECHNICOLOR", "Technicolor", "network"),
    (r"^AVM ", "AVM FRITZ!", "network"),
    (r"^UBIQUITI", "Ubiquiti", "network"),
    (r"^ZYXEL", "Zyxel", "network"),
    (r"^NETGEAR", "Netgear", ""),
    (r"^D-LINK", "D-Link", ""),
    (r"TENDA TECHNOLOGY", "Tenda", ""),
    (r"^LOGITECH", "Logitech", ""),
    (r"^TESLA", "Tesla", ""),
    (r"^MUNDO READER", "BQ", ""),
    (r"^TCT MOBILE", "Alcatel", "phone"),
    (r"^WIKO", "Wiko", "phone"),
]


def main(src, dst):
    data = json.load(open(src, encoding="utf-8"))
    rules = [(re.compile(r, re.I), b, h) for r, b, h in BRANDS]
    out = []
    for prefix, text in data.items():
        if len(prefix) != 6:
            continue
        name = text.split("\n", 1)[0].strip()
        for rx, brand, hint in rules:
            if rx.search(name):
                out.append(f"{prefix.upper()}\t{brand}\t{hint}")
                break
    out.sort()
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "w", encoding="utf-8") as f:
        f.write("# Marca por MAC. Datos: registro MA-L del IEEE vía oui-data (BSD-2-Clause, (c) silverwind).\n")
        f.write("# Generado con herramientas/generar_oui.py. Formato: PREFIJO<tab>marca<tab>pista de tipo\n")
        f.write("\n".join(out) + "\n")
    print(len(out), "prefijos")


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    main(sys.argv[1], os.path.join(here, "..", "app", "src", "main", "assets", "oui.txt"))
