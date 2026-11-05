#!/usr/bin/env python3
"""
Genera las ilustraciones de la guía (app/src/main/res/drawable/guia_N.xml) a partir de
formas sencillas. También escribe la misma ilustración en SVG para poder revisarla.
Uso: python3 herramientas/ilustraciones.py <carpeta_svg>
"""
import pathlib
import sys

W, H = 240, 180
DARK, ACC, GREEN, RED, MID, LIGHT, WHITE, CARD = "#1F1D1A", "#E8590C", "#2E7D4F", "#C0392B", "#6B665E", "#E6E1D8", "#FFFFFF", "#FBF8F3"


def rr(x, y, w, h, r):
    return (f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} "
            f"H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z")


def circle(cx, cy, r):
    return f"M{cx-r},{cy} A{r},{r} 0 1 0 {cx+r},{cy} A{r},{r} 0 1 0 {cx-r},{cy} Z"


def fill(d, color):
    return ("fill", d, color, 0)


def line(d, color, w=6):
    return ("stroke", d, color, w)


def bg():
    return [fill(circle(120, 92, 78), LIGHT)]


def router(x, y, s=1.0):
    w, h = 110 * s, 46 * s
    out = [line(f"M{x+22*s},{y} L{x+12*s},{y-30*s}", DARK, 6 * s), line(f"M{x+w-22*s},{y} L{x+w-12*s},{y-30*s}", DARK, 6 * s),
           fill(rr(x, y, w, h, 12 * s), DARK)]
    for i in range(4):
        out.append(fill(circle(x + 24 * s + i * 18 * s, y + h / 2, 4.5 * s), GREEN if i < 3 else ACC))
    return out


def phone(x, y, w=74, h=128):
    return [fill(rr(x, y, w, h, 14), DARK), fill(rr(x + 6, y + 14, w - 12, h - 30, 6), CARD),
            fill(rr(x + w / 2 - 10, y + h - 11, 20, 4, 2), MID)]


def check(cx, cy, r, color=GREEN):
    return [fill(circle(cx, cy, r), color),
            line(f"M{cx-r*0.45},{cy+r*0.02} L{cx-r*0.1},{cy+r*0.38} L{cx+r*0.5},{cy-r*0.34}", WHITE, r * 0.26)]


def cross(cx, cy, r):
    return [fill(circle(cx, cy, r), RED), line(f"M{cx-r*.38},{cy-r*.38} L{cx+r*.38},{cy+r*.38}", WHITE, r * .26),
            line(f"M{cx+r*.38},{cy-r*.38} L{cx-r*.38},{cy+r*.38}", WHITE, r * .26)]


def sparkle(cx, cy, r, color):
    k = r * 0.28
    return [fill(f"M{cx},{cy-r} Q{cx+k},{cy-k} {cx+r},{cy} Q{cx+k},{cy+k} {cx},{cy+r} "
                 f"Q{cx-k},{cy+k} {cx-r},{cy} Q{cx-k},{cy-k} {cx},{cy-r} Z", color)]


CARDS = {
    # 1 · ¿Tu router no está?
    1: bg() + router(52, 96) + [fill(circle(178, 62, 30), ACC),
        line("M168,54 A10,10 0 1 1 182,63 Q178,66 178,73", WHITE, 7), fill(circle(178, 84, 4.5), WHITE)],
    # 2 · Mira su ficha
    2: bg() + [fill(rr(70, 26, 100, 132, 12), WHITE), fill(rr(70, 26, 100, 26, 12), DARK), fill("M70,40 H170 V52 H70 Z", DARK),
        line("M86,72 H150", LIGHT, 7), line("M86,92 H140", LIGHT, 7), line("M86,112 H146", LIGHT, 7), line("M86,132 H124", LIGHT, 7)]
        + check(160, 128, 20) + cross(76, 128, 14),
    # 3 · Graba su panel
    3: bg() + phone(83, 24) + [line("M98,62 H140", LIGHT, 7), line("M98,80 H132", LIGHT, 7), line("M98,98 H138", LIGHT, 7),
        line("M98,116 H124", LIGHT, 7), fill(circle(170, 46, 18), RED), fill(circle(170, 46, 7), WHITE)],
    # 4 · Pásalo a una IA
    4: bg() + phone(26, 34, 62, 110) + [fill("M42,62 H64 L72,70 V104 H42 Z", LIGHT), fill("M64,62 V70 H72 Z", MID),
        line("M49,80 H64", MID, 4), line("M49,90 H62", MID, 4), line("M100,90 H128", ACC, 7), fill("M124,78 L142,90 L124,102 Z", ACC),
        fill(rr(148, 48, 72, 62, 16), DARK), fill("M162,108 L160,128 L180,108 Z", DARK)]
        + sparkle(184, 79, 17, ACC) + sparkle(204, 60, 7, WHITE),
    # 5 · Pruébala
    5: bg() + [fill(rr(46, 46, 148, 40, 20), LIGHT), fill(rr(46, 46, 148, 40, 20), RED), fill(circle(66, 66, 15), WHITE),
        fill(rr(46, 100, 148, 40, 20), GREEN), fill(circle(174, 120, 15), WHITE)] + check(196, 34, 16),
    # 6 · Compártela
    6: bg() + [fill(rr(96, 58, 48, 62, 8), WHITE), line("M106,76 H134", LIGHT, 6), line("M106,90 H130", LIGHT, 6),
        line("M106,104 H134", LIGHT, 6),
        line("M120,58 L120,36", ACC, 5), line("M96,100 L64,124", ACC, 5), line("M144,100 L176,124", ACC, 5)]
        + [fill(circle(120, 28, 14), DARK), fill(circle(56, 132, 14), DARK), fill(circle(184, 132, 14), DARK)]
        + sparkle(176, 44, 10, ACC),
}


def svg(shapes):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W*2}" height="{H*2}">']
    for kind, d, color, w in shapes:
        if kind == "fill":
            parts.append(f'<path d="{d}" fill="{color}"/>')
        else:
            parts.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')
    return "\n".join(parts + ["</svg>"])


def vector(shapes):
    parts = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Generado por herramientas/ilustraciones.py: no editar a mano. -->',
             f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{W}dp" '
             f'android:height="{H}dp" android:viewportWidth="{W}" android:viewportHeight="{H}">']
    for kind, d, color, w in shapes:
        if kind == "fill":
            parts.append(f'    <path android:fillColor="{color}" android:pathData="{d}" />')
        else:
            parts.append(f'    <path android:strokeColor="{color}" android:strokeWidth="{w:g}" android:strokeLineCap="round" '
                         f'android:strokeLineJoin="round" android:pathData="{d}" />')
    return "\n".join(parts + ["</vector>"]) + "\n"


if __name__ == "__main__":
    raiz = pathlib.Path(__file__).resolve().parents[1]
    res = raiz / "app" / "src" / "main" / "res" / "drawable"
    out_svg = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
    for n, shapes in CARDS.items():
        (res / f"guia_{n}.xml").write_text(vector(shapes), encoding="utf-8")
        if out_svg:
            out_svg.mkdir(parents=True, exist_ok=True)
            (out_svg / f"guia_{n}.svg").write_text(svg(shapes), encoding="utf-8")
    print("ilustraciones:", len(CARDS))
