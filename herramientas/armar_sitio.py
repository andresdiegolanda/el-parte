#!/usr/bin/env python3
"""Arma el sitio de GitHub Pages: el kanban y, si está, el tablero de arquitectura (ADR 0017).

Genera las dos páginas desde sus fuentes, tablero.md y docs/tablero-de-arquitectura/sistema.json,
y las deja en la carpeta de destino con las mismas rutas que en el repositorio, más una portada:

  index.html                              la portada
  kanban/index.html                       el kanban, de solo lectura
  docs/tablero-de-arquitectura/*.html     el tablero de arquitectura, una página por vista
  marca/                                  el logo, si el proyecto tiene docs/marca/

Si la variable de entorno URL_DE_LA_APLICACION tiene una dirección, la portada suma un enlace a la
aplicación publicada. La dirección no va en el repositorio: en GitHub Actions es una variable del
repositorio con el mismo nombre (ADR 0016).

Lo corre .github/workflows/publicar.yml en cada push a main. En una sesión se puede correr igual,
para ver el sitio entero antes de subir.

Uso:  python3 herramientas/armar_sitio.py [carpeta]      (por omisión, tmp/sitio)
Sale con 1, sin escribir nada, si alguna de las fuentes tiene errores.
"""

import html
import os
import shutil
import sys
from pathlib import Path

import generar_kanban
import tablero_md

# El tablero de arquitectura es propio de El Parte: sin él, el sitio lleva solo el kanban.
try:
    import tablero_arquitectura
except ImportError:
    tablero_arquitectura = None

RAIZ = Path(__file__).resolve().parent.parent

# El logo del proyecto, si tiene (docs/marca/README.md): la portada lo pone arriba, el kanban en su
# encabezado, y cada página lo usa de ícono. Sin esta carpeta, el sitio sale sin logo.
MARCA = RAIZ / "docs" / "marca"
ICONO = "el-parte-icon-color-sin-fondo.svg"
LOGO = "el-parte-lockup-color-sin-fondo.svg"

# Lo único que cambia al copiar este archivo a otro proyecto (metodologia/README.md). El nombre
# sale del campo "Proyecto" del encabezado de tablero.md.
DESCRIPCION = ("Un proyecto didáctico de agentes orquestados que leen fuentes públicas del Estado uruguayo "
               "cada noche y publican hechos con su grado de evidencia.")

PORTADA = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{nombre}</title>
<!-- Generado por herramientas/armar_sitio.py. No se edita a mano. -->
<style>
  body {{ font: 16px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif; color: #1b2226; background: #eceeed; margin: 0; }}
  main {{ max-width: 720px; margin: 0 auto; padding: 48px 20px; }}
  h1 {{ font-size: 28px; margin: 0 0 8px; }}
  h1 img {{ height: 64px; width: auto; display: block; margin-left: -6px; }}
  p {{ margin: 0 0 16px; color: #3a454b; }}
  ul {{ list-style: none; padding: 0; margin: 24px 0; display: grid; gap: 12px; }}
  a.tarjeta {{ display: block; background: #fff; border: 1px solid #cdd3d2; border-radius: 10px; padding: 16px 18px; color: inherit; text-decoration: none; }}
  a.tarjeta:hover {{ border-color: #1f4fae; }}
  a.tarjeta b {{ display: block; font-size: 18px; color: #1f4fae; margin-bottom: 4px; }}
  footer {{ font-size: 13px; color: #5a666c; }}
</style>
</head>
<body>
<main>
  <h1>{nombre}</h1>
  <p>{descripcion}</p>
  <ul>
{enlaces}
  </ul>
  <footer>Generado desde el repositorio, <a href="{repo}">{repo_corto}</a>, en cada push a <code>main</code>. Lo que manda es el repositorio.</footer>
</main>
</body>
</html>
"""

ENLACE = """    <li><a class="tarjeta" href="{href}"><b>{titulo}</b>{texto}</a></li>"""


def armar():
    """Devuelve ({ruta relativa: html}, errores)."""
    kanban, errores = generar_kanban.generar()
    paginas = {}
    if tablero_arquitectura is not None:
        paginas, problemas = tablero_arquitectura.generar()
        errores += problemas
    if errores:
        return {}, errores
    sitio = {"kanban/index.html": kanban}
    enlaces = []
    aplicacion = os.environ.get("URL_DE_LA_APLICACION", "").strip()
    if aplicacion.startswith("https://"):
        enlaces.append(ENLACE.format(href=html.escape(aplicacion), titulo="La aplicación",
                                     texto="El proyecto funcionando. Se entra con un enlace que llega por correo, solo con invitación."))
    enlaces += [
        ENLACE.format(href="kanban/", titulo="El tablero del proyecto",
                      texto="Lo que está en curso, lo que falta y lo que ya se hizo, tarjeta por tarjeta. De solo lectura: se cambia en tablero.md."),
    ]
    if paginas:
        carpeta = tablero_arquitectura.CARPETA.relative_to(RAIZ).as_posix()
        sitio.update({f"{carpeta}/{archivo}": contenido for archivo, contenido in paginas.items()})
        enlaces.append(ENLACE.format(href=f"{carpeta}/", titulo="El tablero de arquitectura",
                                     texto="El sistema objeto por objeto, construido y diseñado, con recorridos paso a paso."))
        for v in tablero_arquitectura.leer_modelo().get("vistas", []):
            enlaces.append(ENLACE.format(href=f"{carpeta}/{v['archivo']}", titulo=html.escape(v["titulo"]),
                                         texto="Una vista del tablero de arquitectura."))
    meta = tablero_md.leer(generar_kanban.TABLERO)[0]["meta"]
    repo = meta["repositorio"]
    nombre = meta["proyecto"] or repo.rstrip("/").rsplit("/", 1)[-1]
    sitio["index.html"] = PORTADA.format(nombre=html.escape(nombre), descripcion=html.escape(DESCRIPCION),
                                         enlaces="\n".join(enlaces), repo=repo,
                                         repo_corto=repo.removeprefix("https://"))
    if (MARCA / ICONO).exists():
        sitio = {ruta: con_marca(ruta, contenido, nombre) for ruta, contenido in sitio.items()}
    return sitio, []


def con_marca(ruta, contenido, nombre):
    """La página con el logo: el ícono en la pestaña, el logo en la portada y el ícono en el kanban."""
    prefijo = "../" * ruta.count("/") + "marca/"
    icono = (f'<link rel="icon" href="{prefijo}favicon.ico" sizes="any">\n'
             f'<link rel="icon" href="{prefijo}{ICONO}" type="image/svg+xml">\n')
    if 'rel="icon"' not in contenido:
        contenido = contenido.replace("</title>\n", "</title>\n" + icono, 1)
    if ruta == "index.html" and (MARCA / LOGO).exists():
        contenido = contenido.replace(f"<h1>{html.escape(nombre)}</h1>",
                                      f'<h1><img src="{prefijo}{LOGO}" alt="{html.escape(nombre)}"></h1>', 1)
    if ruta == "kanban/index.html":
        contenido = contenido.replace('<div class="id">\n', f'<div class="id">\n      <img src="{prefijo}{ICONO}" alt="" '
                                      'width="36" height="36" style="display:block;margin:0 0 8px -4px">\n', 1)
    return contenido


def main():
    destino = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "tmp" / "sitio"
    sitio, errores = armar()
    if errores:
        print(f"No se armó el sitio: las fuentes tienen {len(errores)} problemas:")
        for e in errores:
            print(f"  {e}")
        return 1
    if destino.exists() and any(destino.iterdir()):
        # Solo se vacía una carpeta de tmp/ (regla 14); cualquier otra tiene que estar vacía.
        if (RAIZ / "tmp").resolve() not in destino.resolve().parents:
            print(f"No se armó el sitio: {destino} no está vacía.")
            return 1
        shutil.rmtree(destino)
    for ruta, contenido in sitio.items():
        archivo = destino / ruta
        archivo.parent.mkdir(parents=True, exist_ok=True)
        archivo.write_text(contenido, encoding="utf-8", newline="\n")
    if (MARCA / ICONO).exists():
        shutil.copytree(MARCA, destino / "marca", ignore=shutil.ignore_patterns("*.md"))
    print(f"Listo: {len(sitio)} páginas en {destino}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
