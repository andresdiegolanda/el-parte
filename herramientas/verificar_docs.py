#!/usr/bin/env python3
"""Verificador de la documentacion de El Parte.

Controla, sobre todo el repositorio:

1. Que ningun archivo de texto tenga guiones largos (regla 7).
2. Que los enlaces relativos de los Markdown apunten a archivos que existen.
3. Que tablero.md respete su formato y sus reglas (regla 8): ver
   herramientas/tablero_md.py.
4. Que kanban/index.html sea exactamente lo que genera tablero.md
   (adrs/0001-tablero-fuente-unica-kanban-generado.md).
5. Que toda tarea (T01, T02...) citada en un Markdown exista en el tablero.
6. Que toda decision de arranque (D1, D2...) citada exista en
   arranque/decisiones-iniciales.md.

Uso:  python3 herramientas/verificar_docs.py
Sale con codigo 0 si todo esta bien y 1 si encuentra algun problema.
Solo usa la biblioteca estandar de Python.
"""

import re
import sys
from pathlib import Path
from urllib.parse import unquote

import generar_kanban
import tablero_md

RAIZ = Path(__file__).resolve().parent.parent

# Carpetas que no se revisan: la de git, el registro real del banco de
# pruebas (no se versiona) y los archivos de trabajo.
EXCLUIDAS = {".git", "tmp"}
EXCLUIDAS_RUTAS = {Path("banco-de-pruebas/privado")}

# Guion largo (U+2014), semiraya (U+2013) y barra horizontal (U+2015).
# Se arman con chr() para que este archivo no los contenga.
GUIONES = {chr(0x2014): "guion largo", chr(0x2013): "semiraya", chr(0x2015): "barra horizontal"}


ENLACE = re.compile(r"!?\[[^\]]*\]\(\s*([^)\s]+)(?:\s+\"[^\"]*\")?\s*\)")
CODIGO_EN_LINEA = re.compile(r"`[^`]*`")
TAREA = re.compile(r"\bT\d{2}\b")
DECISION = re.compile(r"\bD\d{1,2}\b")


def archivos_del_repo():
    for ruta in sorted(RAIZ.rglob("*")):
        if not ruta.is_file():
            continue
        relativa = ruta.relative_to(RAIZ)
        if relativa.parts and relativa.parts[0] in EXCLUIDAS:
            continue
        if any(relativa.is_relative_to(e) for e in EXCLUIDAS_RUTAS):
            continue
        yield ruta, relativa


def leer_texto(ruta):
    try:
        return ruta.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None


def lineas_fuera_de_bloques(texto):
    """Devuelve (numero, linea) de las lineas que no estan dentro de ``` ```."""
    dentro = False
    for numero, linea in enumerate(texto.splitlines(), start=1):
        if linea.lstrip().startswith("```"):
            dentro = not dentro
            continue
        if not dentro:
            yield numero, linea


def verificar_guiones(archivos, errores):
    for ruta, relativa in archivos:
        texto = leer_texto(ruta)
        if texto is None:
            continue
        for numero, linea in enumerate(texto.splitlines(), start=1):
            for caracter, nombre in GUIONES.items():
                if caracter in linea:
                    errores.append(f"{relativa}:{numero}: tiene {nombre}")


def verificar_enlaces(markdowns, errores):
    for ruta, relativa in markdowns:
        texto = leer_texto(ruta)
        for numero, linea in lineas_fuera_de_bloques(texto):
            linea = CODIGO_EN_LINEA.sub("", linea)
            for destino in ENLACE.findall(linea):
                destino = destino.strip("<>")
                if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", destino) or destino.startswith("#"):
                    continue
                archivo = unquote(destino.split("#", 1)[0])
                if not archivo:
                    continue
                objetivo = (ruta.parent / archivo).resolve()
                if not objetivo.exists():
                    errores.append(f"{relativa}:{numero}: enlace roto a {destino}")


def verificar_tablero(errores):
    tablero = RAIZ / "tablero.md"
    if not tablero.exists():
        errores.append("tablero.md: no existe")
        return set()
    datos, de_formato = tablero_md.leer(tablero)
    errores.extend(de_formato)
    errores.extend(tablero_md.validar(datos))
    return tablero_md.ids(datos)


def verificar_kanban(errores):
    html, problemas = generar_kanban.generar()
    if problemas:
        errores.append("kanban/index.html: no se puede generar mientras el tablero tenga problemas")
        return
    salida = generar_kanban.SALIDA
    if not salida.exists() or salida.read_text(encoding="utf-8") != html:
        errores.append("kanban/index.html: no coincide con tablero.md; corré python3 herramientas/generar_kanban.py")


def decisiones_definidas():
    archivo = RAIZ / "arranque" / "decisiones-iniciales.md"
    if not archivo.exists():
        return set()
    return set(re.findall(r"^## (D\d{1,2})\.", archivo.read_text(encoding="utf-8"), re.MULTILINE))


def verificar_referencias(markdowns, ids, decisiones, errores):
    for ruta, relativa in markdowns:
        texto = leer_texto(ruta)
        for numero, linea in lineas_fuera_de_bloques(texto):
            for tarea in TAREA.findall(linea):
                if tarea not in ids:
                    errores.append(f"{relativa}:{numero}: cita la tarea {tarea}, que no esta en el tablero")
            for decision in DECISION.findall(linea):
                if decision not in decisiones:
                    errores.append(f"{relativa}:{numero}: cita la decision {decision}, que no existe")


def main():
    archivos = list(archivos_del_repo())
    markdowns = [(r, rel) for r, rel in archivos if r.suffix == ".md"]
    errores = []

    verificar_guiones(archivos, errores)
    verificar_enlaces(markdowns, errores)
    ids = verificar_tablero(errores)
    verificar_kanban(errores)
    verificar_referencias(markdowns, ids, decisiones_definidas(), errores)

    if errores:
        print(f"Se encontraron {len(errores)} problemas:")
        for error in errores:
            print(f"  {error}")
        return 1
    print(f"Todo bien: {len(archivos)} archivos revisados, {len(markdowns)} Markdown, {len(ids)} tareas en el tablero.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
