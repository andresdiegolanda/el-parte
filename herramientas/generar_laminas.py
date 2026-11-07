#!/usr/bin/env python3
"""Genera las láminas de arquitectura en SVG desde su fuente en JSON.

Cada lámina tiene su fuente en docs/laminas/<nombre>.json y su salida en
docs/laminas/<nombre>.svg. El SVG no se edita a mano (regla 5): se cambia el
JSON y se vuelve a generar. El verificador regenera cada lámina en memoria y
falla si no coincide con el archivo.

Formato de la fuente (claves sin tildes):
  titulo, ancho, alto
  grupos:  [{id, texto, x, y, ancho, alto}]
  cajas:   [{id, texto: [lineas], x, y, ancho, alto, tipo}]
           tipo: fuente | agente | web | dato | externo
  flechas: [{desde, hasta, texto?, via?: [[x, y]], desde_xy?, hasta_xy?,
             texto_xy?, doble?, trazo?: lleno | punteado}]

Uso:  python3 herramientas/generar_laminas.py
La salida no depende de la fecha ni de la máquina.
"""

import json
import sys
from pathlib import Path
from xml.sax.saxutils import escape

RAIZ = Path(__file__).resolve().parent.parent
CARPETA = RAIZ / "docs" / "laminas"

COLORES = {
    "fuente": ("#f6e7d6", "#8a4b12"),
    "agente": ("#e1e8f7", "#1f4fae"),
    "web": ("#ebe5fa", "#5b3fb0"),
    "dato": ("#dcf1f2", "#0f6e7a"),
    "externo": ("#eceeed", "#5a666c"),
}
TINTA = "#1b2226"
TENUE = "#5a666c"
FUENTE_TEXTO = "Segoe UI, Helvetica, Arial, sans-serif"


def numero(v):
    """Formatea un número sin decimales innecesarios, siempre igual."""
    v = round(float(v), 1)
    return str(int(v)) if v == int(v) else f"{v:.1f}"


def centro(caja):
    return (caja["x"] + caja["ancho"] / 2, caja["y"] + caja["alto"] / 2)


def borde(caja, hacia):
    """Punto donde la recta del centro de la caja hacia `hacia` corta su borde."""
    cx, cy = centro(caja)
    dx, dy = hacia[0] - cx, hacia[1] - cy
    if dx == 0 and dy == 0:
        return (cx, cy)
    tx = (caja["ancho"] / 2) / abs(dx) if dx else float("inf")
    ty = (caja["alto"] / 2) / abs(dy) if dy else float("inf")
    t = min(tx, ty)
    return (cx + dx * t, cy + dy * t)


def puntos_de(flecha, elementos):
    via = [tuple(p) for p in flecha.get("via", [])]
    origen = elementos[flecha["desde"]]
    destino = elementos[flecha["hasta"]]
    primero = via[0] if via else (tuple(flecha["hasta_xy"]) if "hasta_xy" in flecha else centro(destino))
    ultimo = via[-1] if via else (tuple(flecha["desde_xy"]) if "desde_xy" in flecha else centro(origen))
    inicio = tuple(flecha["desde_xy"]) if "desde_xy" in flecha else borde(origen, primero)
    fin = tuple(flecha["hasta_xy"]) if "hasta_xy" in flecha else borde(destino, ultimo)
    return [inicio] + via + [fin]


def svg(lamina, nombre):
    ancho, alto = lamina["ancho"], lamina["alto"]
    elementos = {g["id"]: g for g in lamina.get("grupos", [])}
    elementos.update({c["id"]: c for c in lamina.get("cajas", [])})
    salida = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho}" height="{alto}" viewBox="0 0 {ancho} {alto}" '
        f'font-family="{FUENTE_TEXTO}" role="img" aria-label="{escape(lamina["titulo"])}">',
        f"<!-- Generada por herramientas/generar_laminas.py desde docs/laminas/{nombre}.json. No se edita a mano. -->",
        f"<title>{escape(lamina['titulo'])}</title>",
        "<defs>",
        f'<marker id="punta" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">'
        f'<path d="M0,0 L10,5 L0,10 z" fill="{TENUE}"/></marker>',
        "</defs>",
        f'<rect x="0" y="0" width="{ancho}" height="{alto}" fill="#ffffff"/>',
        f'<text x="20" y="34" font-size="18" font-weight="600" fill="{TINTA}">{escape(lamina["titulo"])}</text>',
    ]
    for g in lamina.get("grupos", []):
        salida.append(
            f'<rect x="{numero(g["x"])}" y="{numero(g["y"])}" width="{numero(g["ancho"])}" height="{numero(g["alto"])}" '
            f'rx="10" fill="#f7f8f8" stroke="{TENUE}" stroke-dasharray="6 4"/>'
        )
        salida.append(
            f'<text x="{numero(g["x"] + 12)}" y="{numero(g["y"] + 20)}" font-size="12" font-weight="600" fill="{TENUE}">'
            f'{escape(g["texto"])}</text>'
        )
    for f in lamina.get("flechas", []):
        pts = puntos_de(f, elementos)
        d = "M" + " L".join(f"{numero(x)},{numero(y)}" for x, y in pts)
        punteado = ' stroke-dasharray="5 4"' if f.get("trazo") == "punteado" else ""
        inicio = ' marker-start="url(#punta)"' if f.get("doble") else ""
        salida.append(f'<path d="{d}" fill="none" stroke="{TENUE}" stroke-width="1.6"{punteado}{inicio} marker-end="url(#punta)"/>')
    for c in lamina.get("cajas", []):
        relleno, trazo = COLORES[c.get("tipo", "externo")]
        salida.append(
            f'<rect x="{numero(c["x"])}" y="{numero(c["y"])}" width="{numero(c["ancho"])}" height="{numero(c["alto"])}" '
            f'rx="8" fill="{relleno}" stroke="{trazo}" stroke-width="1.4"/>'
        )
        lineas = c["texto"]
        cx, cy = centro(c)
        primera = cy - (len(lineas) - 1) * 8 + 4.5
        for i, linea in enumerate(lineas):
            peso = ' font-weight="600"' if i == 0 else ""
            tam = 13 if i == 0 else 11.5
            salida.append(
                f'<text x="{numero(cx)}" y="{numero(primera + i * 16)}" font-size="{numero(tam)}"{peso} '
                f'text-anchor="middle" fill="{TINTA}">{escape(linea)}</text>'
            )
    for f in lamina.get("flechas", []):
        if not f.get("texto"):
            continue
        if "texto_xy" in f:
            tx, ty = f["texto_xy"]
        else:
            pts = puntos_de(f, elementos)
            a, b = pts[len(pts) // 2 - 1], pts[len(pts) // 2]
            tx, ty = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 - 6
        salida.append(
            f'<text x="{numero(tx)}" y="{numero(ty)}" font-size="11.5" text-anchor="middle" fill="{TENUE}" '
            f'stroke="#ffffff" stroke-width="4" paint-order="stroke">{escape(f["texto"])}</text>'
        )
    salida.append("</svg>")
    return "\n".join(salida) + "\n"


def generar_todas():
    """Devuelve {ruta_svg: contenido} para cada fuente de lámina."""
    resultado = {}
    for fuente in sorted(CARPETA.glob("*.json")):
        lamina = json.loads(fuente.read_text(encoding="utf-8"))
        resultado[fuente.with_suffix(".svg")] = svg(lamina, fuente.stem)
    return resultado


def main():
    laminas = generar_todas()
    for ruta, contenido in laminas.items():
        ruta.write_text(contenido, encoding="utf-8", newline="\n")
        print(f"Listo: {ruta.relative_to(RAIZ)}")
    if not laminas:
        print("No hay fuentes de láminas en docs/laminas/.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
