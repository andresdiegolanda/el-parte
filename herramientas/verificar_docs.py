#!/usr/bin/env python3
"""Verificador de la documentacion de El Parte.

Controla, sobre todo el repositorio:

1. Que ningun archivo de texto tenga guiones largos (regla 7).
2. Que los enlaces relativos de los Markdown apunten a archivos que existen.
3. Que tablero.md respete su formato y sus reglas (regla 8): ver
   herramientas/tablero_md.py.
4. Que kanban/index.html sea exactamente lo que genera tablero.md
   (adrs/0001-tablero-fuente-unica-kanban-generado.md).
5. Que cada lámina SVG de docs/laminas/ sea exactamente lo que genera su
   fuente JSON (regla 5).
6. Que toda tarea (T01, T02...) citada en un Markdown exista en el tablero.
7. Que toda decision de arranque (D1, D2...) citada exista en
   arranque/decisiones-iniciales.md.
8. Que cada diagrama Mermaid de los Markdown se pueda dibujar. Usa mermaid-cli
   (el programa mmdc), que se busca en la variable de entorno MMDC o en el PATH.
   Si no está, avisa y no falla, salvo que EXIGIR_MERMAID=1. Los diagramas que
   ya pasaron se guardan por su hash en tmp/mermaid-verificados.txt.

Uso:  python3 herramientas/verificar_docs.py
Sale con codigo 0 si todo esta bien y 1 si encuentra algun problema.
Solo usa la biblioteca estandar de Python.
"""

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from urllib.parse import unquote

import generar_kanban
import generar_laminas
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


def verificar_laminas(errores):
    for ruta, contenido in generar_laminas.generar_todas().items():
        relativa = ruta.relative_to(RAIZ)
        if not ruta.exists() or ruta.read_text(encoding="utf-8") != contenido:
            errores.append(f"{relativa}: no coincide con su fuente; corré python3 herramientas/generar_laminas.py")
    for svg in generar_laminas.CARPETA.glob("*.svg"):
        if not svg.with_suffix(".json").exists():
            errores.append(f"{svg.relative_to(RAIZ)}: lámina sin fuente JSON (las láminas no se dibujan a mano)")


def bloques_mermaid(texto):
    """Devuelve (linea, contenido) de cada bloque ```mermaid del texto."""
    bloques, actual, inicio = [], None, 0
    for numero, linea in enumerate(texto.splitlines(), start=1):
        if actual is None and linea.strip() == "```mermaid":
            actual, inicio = [], numero
        elif actual is not None and linea.strip() == "```":
            bloques.append((inicio, "\n".join(actual) + "\n"))
            actual = None
        elif actual is not None:
            actual.append(linea)
    return bloques


def verificar_mermaid(markdowns, errores, avisos):
    mmdc = os.environ.get("MMDC") or shutil.which("mmdc")
    if not mmdc:
        mensaje = "Mermaid: no se comprobó si los diagramas se dibujan (falta mmdc; ver arranque/como-se-trabaja.md)"
        (errores if os.environ.get("EXIGIR_MERMAID") == "1" else avisos).append(mensaje)
        return
    cache = RAIZ / "tmp" / "mermaid-verificados.txt"
    vistos = set(cache.read_text(encoding="utf-8").split()) if cache.exists() else set()
    nuevos = set()
    configuracion = {"args": ["--no-sandbox", "--disable-gpu"]}
    if os.environ.get("PUPPETEER_EXECUTABLE_PATH"):
        configuracion["executablePath"] = os.environ["PUPPETEER_EXECUTABLE_PATH"]
    with tempfile.TemporaryDirectory() as carpeta:
        cfg = Path(carpeta) / "puppeteer.json"
        cfg.write_text(json.dumps(configuracion), encoding="utf-8")
        for ruta, relativa in markdowns:
            for linea, bloque in bloques_mermaid(leer_texto(ruta)):
                huella = hashlib.sha256(bloque.encode("utf-8")).hexdigest()
                if huella in vistos:
                    nuevos.add(huella)
                    continue
                entrada = Path(carpeta) / "diagrama.mmd"
                entrada.write_text(bloque, encoding="utf-8")
                proceso = subprocess.run(
                    [mmdc, "-q", "-p", str(cfg), "-i", str(entrada), "-o", str(Path(carpeta) / "diagrama.svg")],
                    capture_output=True, text=True, timeout=120,
                )
                if proceso.returncode == 0:
                    nuevos.add(huella)
                else:
                    detalle = (proceso.stderr or proceso.stdout).strip().splitlines()
                    errores.append(f"{relativa}:{linea}: el diagrama Mermaid no se dibuja: {detalle[0] if detalle else 'sin detalle'}")
    cache.parent.mkdir(exist_ok=True)
    cache.write_text("\n".join(sorted(nuevos)) + "\n", encoding="utf-8")


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
    avisos = []

    verificar_guiones(archivos, errores)
    verificar_enlaces(markdowns, errores)
    ids = verificar_tablero(errores)
    verificar_kanban(errores)
    verificar_laminas(errores)
    verificar_mermaid(markdowns, errores, avisos)
    verificar_referencias(markdowns, ids, decisiones_definidas(), errores)

    for aviso in avisos:
        print(f"Aviso: {aviso}")
    if errores:
        print(f"Se encontraron {len(errores)} problemas:")
        for error in errores:
            print(f"  {error}")
        return 1
    print(f"Todo bien: {len(archivos)} archivos revisados, {len(markdowns)} Markdown, {len(ids)} tareas en el tablero.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
