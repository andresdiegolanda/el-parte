#!/usr/bin/env python3
"""Genera kanban/index.html desde tablero.md.

tablero.md es la unica fuente (adrs/0001-tablero-fuente-unica-kanban-generado.md).
Este script lee el tablero, lo valida, convierte los enlaces relativos en
enlaces al repositorio y mete los datos en kanban/plantilla.html. El resultado,
kanban/index.html, no se edita a mano.

Uso:  python3 herramientas/generar_kanban.py
Sale con codigo 1, sin escribir nada, si el tablero tiene errores.
La salida no depende de la fecha ni de la maquina: generar dos veces da lo mismo.
"""

import json
import re
import sys
from pathlib import Path

import tablero_md

RAIZ = Path(__file__).resolve().parent.parent
TABLERO = RAIZ / "tablero.md"
PLANTILLA = RAIZ / "kanban" / "plantilla.html"
SALIDA = RAIZ / "kanban" / "index.html"

MARCA_DATOS = "__DATOS__"
MARCA_AVISO = "<!--GENERADO-->"
AVISO = ("<!-- Generado por herramientas/generar_kanban.py desde tablero.md. "
         "No se edita a mano: se edita tablero.md y se vuelve a generar. -->")

ENLACE_MD = re.compile(r"\[([^\]]+)\]\(([^)\s]+)\)")


def url_del_repo(repositorio, ruta):
    """Convierte una ruta relativa a la raiz en un enlace al repositorio."""
    if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", ruta):
        return ruta
    limpia, _, ancla = ruta.partition("#")
    while limpia.startswith("./"):
        limpia = limpia[2:]
    tipo = "tree" if (RAIZ / limpia).is_dir() else "blob"
    url = f"{repositorio}/{tipo}/main/{limpia}"
    return url + ("#" + ancla if ancla else "")


def enlaces_absolutos(texto, repositorio):
    return ENLACE_MD.sub(lambda m: f"[{m.group(1)}]({url_del_repo(repositorio, m.group(2))})", texto)


def datos_para_la_pagina(datos):
    repo = datos["meta"]["repositorio"]
    tarjetas = []
    for t in datos["tarjetas"]:
        tarjetas.append({
            "id": t["id"],
            "titulo": t["titulo"],
            "columna": t["columna"],
            "orden": t["orden"],
            "tipo": t["tipo"],
            "dueno": t["dueno"],
            "dependeDe": t["dependeDe"],
            "dependencias": t["dependencias"],
            "esperaA": t["esperaA"],
            "fecha": t["fecha"],
            "detalle": [{"texto": d["texto"], "url": url_del_repo(repo, d["ruta"])} for d in t["detalle"]],
            "etapas": t["etapas"],
            "lista": [{"texto": enlaces_absolutos(i["texto"], repo), "hecho": i["hecho"]} for i in t["lista"]],
            "notas": [enlaces_absolutos(n, repo) for n in t["notas"]],
            "registro": [{"fecha": r["fecha"], "texto": enlaces_absolutos(r["texto"], repo)} for r in t["registro"]],
        })
    return {
        "meta": dict(datos["meta"], nota=enlaces_absolutos(datos["meta"]["nota"], repo),
                     tablero=url_del_repo(repo, "tablero.md")),
        "columnas": datos["columnas"],
        "tipos": datos["tipos"],
        "duenos": datos["duenos"],
        "tarjetas": tarjetas,
    }


def generar():
    """Devuelve (html, errores). Si hay errores, html es None."""
    datos, errores = tablero_md.leer(TABLERO)
    errores += tablero_md.validar(datos)
    if errores:
        return None, errores
    plantilla = PLANTILLA.read_text(encoding="utf-8")
    if plantilla.count(MARCA_DATOS) != 1 or plantilla.count(MARCA_AVISO) != 1:
        return None, [f"{PLANTILLA.relative_to(RAIZ)}: tiene que tener una vez {MARCA_DATOS} y una vez {MARCA_AVISO}"]
    contenido = json.dumps(datos_para_la_pagina(datos), ensure_ascii=False, indent=1)
    contenido = contenido.replace("</", "<\\/")
    html = plantilla.replace(MARCA_AVISO, AVISO).replace(MARCA_DATOS, contenido)
    return html, []


def main():
    html, errores = generar()
    if errores:
        print(f"No se generó el kanban: el tablero tiene {len(errores)} problemas:")
        for e in errores:
            print(f"  {e}")
        return 1
    SALIDA.write_text(html, encoding="utf-8", newline="\n")
    print(f"Listo: {SALIDA.relative_to(RAIZ)} generado desde tablero.md.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
