#!/usr/bin/env python3
"""Genera docs/tablero-de-arquitectura/index.html desde sistema.json y plantilla.html.

El tablero de arquitectura se cambia editando sistema.json (lo que dice) o plantilla.html (cómo se ve),
nunca index.html. Después de cambiarlo se corre este script y el verificador, que además compara
sistema.json con el código (ADR 0012).

Uso:  python3 herramientas/generar_tablero_arquitectura.py
"""

import sys

import tablero_arquitectura


def main():
    html, errores = tablero_arquitectura.generar()
    if errores:
        print(f"No se generó el tablero de arquitectura: sistema.json tiene {len(errores)} problemas:")
        for e in errores:
            print(f"  {e}")
        return 1
    tablero_arquitectura.SALIDA.write_text(html, encoding="utf-8", newline="\n")
    print(f"Listo: {tablero_arquitectura.SALIDA.relative_to(tablero_arquitectura.RAIZ)} generado desde sistema.json.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
