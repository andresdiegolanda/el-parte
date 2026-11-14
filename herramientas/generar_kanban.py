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


CAMPOS_TARJETA = ("titulo", "columna", "orden", "tipo", "dueno", "dependeDe", "esperaA", "fecha",
                  "detalle", "etapas", "lista", "notas", "registro")


def datos_para_la_pagina(datos):
    """Los datos con la misma forma que la base del kanban publicado (adrs/0009).

    Los enlaces quedan relativos al repositorio: la página los completa.
    """
    repo = datos["meta"]["repositorio"]
    tarjetas = [dict({"id": t["id"]}, **{c: t[c] for c in CAMPOS_TARJETA}) for t in datos["tarjetas"]]
    meta = dict(datos["meta"], tablero=url_del_repo(repo, "tablero.md"),
                columnas=datos["columnas"], tipos=datos["tipos"], duenos=datos["duenos"])
    return {"meta": meta, "tarjetas": tarjetas}


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
