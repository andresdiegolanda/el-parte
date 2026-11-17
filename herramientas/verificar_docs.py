#!/usr/bin/env python3
"""Verificador de la documentacion de El Parte.

Controla, sobre todo el repositorio:

1. Que ningun archivo de texto tenga guiones largos (regla 7).
2. Que los enlaces relativos de los Markdown apunten a archivos que existen.
3. Que tablero.md respete su formato y sus reglas (regla 8), y que esté en
   formato canónico: leerlo y volver a escribirlo da el mismo archivo, que es
   lo que necesita la sincronización con el kanban. Ver herramientas/tablero_md.py.
4. Que el kanban se pueda generar desde tablero.md. La página generada no se
   versiona: se publica en GitHub Pages (adrs/0017-forma-de-trabajo-mas-liviana.md).
5. Que cada lámina SVG de docs/laminas/ sea exactamente lo que genera su
   fuente JSON (regla 5).
6. Que toda tarea (T01, T02...) citada en un Markdown exista en el tablero.
7. Que toda decision de arranque (D1, D2...) citada exista en
   arranque/decisiones-iniciales.md.
8. Que cada diagrama Mermaid de los Markdown se pueda dibujar. Usa mermaid-cli
   (el programa mmdc), que se busca en la variable de entorno MMDC o en el PATH.
   Si no está, avisa y no falla, salvo que EXIGIR_MERMAID=1. Los diagramas que
   ya pasaron se guardan por su hash en tmp/mermaid-verificados.txt.
9. Que la primera migración de la base (app/.../db/migration/V1__base.sql) sea,
   aparte de su encabezado de comentarios, el primer bloque SQL de
   docs/base-de-afirmaciones.md: el documento manda.
10. Que el tablero de arquitectura coincida con el código (ADR 0012): cada clase y
    plantilla en un objeto, los tópicos y las tablas del código, quién emite,
    escucha, escribe, lee y usa qué, las rutas de cada página, lo diseñado con
    tarjetas vivas, y que sus páginas se puedan generar desde sistema.json. El
    detalle está en herramientas/tablero_arquitectura.py.
11. Que la muestra sintética del banco de pruebas (banco-de-pruebas/muestra/)
    cumpla el formato de banco-de-pruebas/formato.md sin avisos, y que contada
    dé banco-de-pruebas/muestra/medicion-esperada.json. El detalle está en
    herramientas/banco_de_pruebas.py.
12. Un solo lugar por dato (adrs/0017-forma-de-trabajo-mas-liviana.md): avisa,
    sin fallar, cuando la misma cifra con unidad (USD o %) aparece en más de un
    documento vigente. No cuenta la historia (CHANGELOG.md, los ADRs, las
    decisiones iniciales, obsoletos/, el registro y las tarjetas hechas de
    tablero.md) ni lo que está entre comillas invertidas.

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

import banco_de_pruebas
import generar_kanban
import generar_laminas
import tablero_arquitectura
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
CIFRA = re.compile(r"(?<![\w,.])\d+(?:[.,]\d+)?\s?(?:USD|%)(?!\w)")
REGISTRO = re.compile(r"^- \d{4}-\d{2}-\d{2}:")
HISTORIA = (Path("CHANGELOG.md"), Path("adrs"), Path("arranque/decisiones-iniciales.md"), Path("obsoletos"))


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
    if not de_formato:
        intro, orden = tablero_md.intro_y_columnas(tablero)
        canonico = tablero_md.serializar(intro, orden, datos["columnas"], datos["tipos"], datos["tarjetas"])
        if canonico != tablero.read_text(encoding="utf-8"):
            errores.append("tablero.md: no tiene el formato canónico de las tarjetas (ver herramientas/tablero_md.py, función bloque)")
    return tablero_md.ids(datos)


def verificar_kanban(errores):
    _, problemas = generar_kanban.generar()
    if problemas:
        errores.append("kanban: no se puede generar mientras el tablero tenga problemas")


def verificar_laminas(errores):
    for ruta, contenido in generar_laminas.generar_todas().items():
        relativa = ruta.relative_to(RAIZ)
        if not ruta.exists() or ruta.read_text(encoding="utf-8") != contenido:
            errores.append(f"{relativa}: no coincide con su fuente; corré python3 herramientas/generar_laminas.py")
    for svg in generar_laminas.CARPETA.glob("*.svg"):
        if not svg.with_suffix(".json").exists():
            errores.append(f"{svg.relative_to(RAIZ)}: lámina sin fuente JSON (las láminas no se dibujan a mano)")


def verificar_cifras(markdowns, avisos):
    """Un solo lugar por dato: la misma cifra con unidad en más de un documento vigente es un aviso."""
    donde = {}
    for ruta, relativa in markdowns:
        if any(relativa.is_relative_to(h) for h in HISTORIA):
            continue
        hecho = False
        for linea in leer_texto(ruta).splitlines():
            if relativa == Path("tablero.md") and linea.startswith("## "):
                hecho = linea.strip() == "## Hecho"
            if hecho or REGISTRO.match(linea):
                continue
            for cifra in CIFRA.findall(CODIGO_EN_LINEA.sub("", linea)):
                clave = re.sub(r"\s", " ", cifra).replace(".", ",")
                donde.setdefault(clave, set()).add(relativa.as_posix())
    for cifra, archivos in sorted(donde.items()):
        if len(archivos) > 1:
            avisos.append(f"la cifra {cifra} está en {len(archivos)} documentos ({', '.join(sorted(archivos))}); "
                          "si es el mismo dato, va en uno y los otros lo enlazan (ADR 0017)")


DOC_DE_LA_BASE = Path("docs/base-de-afirmaciones.md")
PRIMERA_MIGRACION = Path("app/src/main/resources/db/migration/V1__base.sql")
BLOQUE_SQL = re.compile(r"^```sql\n(.*?)^```", re.S | re.M)


def verificar_migracion(errores):
    migracion = RAIZ / PRIMERA_MIGRACION
    if not migracion.exists():
        return
    bloques = BLOQUE_SQL.findall(leer_texto(RAIZ / DOC_DE_LA_BASE))
    if not bloques:
        errores.append(f"{DOC_DE_LA_BASE}: no tiene el bloque SQL de las tablas")
        return
    lineas = leer_texto(migracion).splitlines()
    while lineas and (lineas[0].startswith("--") or not lineas[0].strip()):
        lineas.pop(0)
    if "\n".join(lineas).strip() != bloques[0].strip():
        errores.append(f"{PRIMERA_MIGRACION}: no coincide con el primer bloque SQL de {DOC_DE_LA_BASE}; "
                       "se cambia el documento y se vuelve a copiar el bloque")


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
    verificar_cifras(markdowns, avisos)
    verificar_migracion(errores)
    errores.extend(tablero_arquitectura.verificar())
    errores.extend(banco_de_pruebas.verificar_muestra())

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
