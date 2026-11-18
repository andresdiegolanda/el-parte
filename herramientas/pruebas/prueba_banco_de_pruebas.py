#!/usr/bin/env python3
"""Prueba del formato y del cómputo del banco de pruebas (banco-de-pruebas/formato.md y metricas.md).

Copia la muestra sintética a una carpeta temporal y le hace un cambio por vez: un campo que falta,
un índice que se contradice, una revisión con un id que no existe, un hecho sin juzgar. Cada cambio
tiene que dar el error, el aviso o el resultado que corresponde.

Uso:  python3 herramientas/pruebas/prueba_banco_de_pruebas.py
"""

import atexit
import json
import shutil
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import banco_de_pruebas as bp  # noqa: E402

fallas = []

# Las copias de la muestra se borran al terminar, pase lo que pase (regla 14).
CARPETA = Path(tempfile.mkdtemp(prefix="banco-"))
atexit.register(shutil.rmtree, CARPETA, ignore_errors=True)
copias = 0


def comprobar(condicion, mensaje):
    if not condicion:
        fallas.append(mensaje)
    print(("ok   " if condicion else "MAL  ") + mensaje)


def copia():
    """Una copia de la muestra: (carpeta del registro, ruta de la salida)."""
    global copias
    copias += 1
    destino = CARPETA / str(copias) / "muestra"
    shutil.copytree(bp.MUESTRA, destino)
    return destino / "registro", destino / "salida-de-el-parte.json"


def leer_lineas(ruta):
    return [json.loads(x) for x in ruta.read_text(encoding="utf-8").splitlines() if x.strip()]


def escribir_lineas(ruta, filas):
    ruta.write_text("".join(json.dumps(f, ensure_ascii=False) + "\n" for f in filas), encoding="utf-8")


def cambiar_hallazgo(registro, id_, **campos):
    ruta = registro / "hallazgos.jsonl"
    filas = leer_lineas(ruta)
    for f in filas:
        if f["id"] == id_:
            f.update(campos)
    escribir_lineas(ruta, filas)


def reemplazar(ruta, viejo, nuevo):
    texto = ruta.read_text(encoding="utf-8")
    assert viejo in texto, viejo
    ruta.write_text(texto.replace(viejo, nuevo), encoding="utf-8")


# La muestra tal cual.
comprobar(bp.verificar_muestra() == [], "la muestra cumple el formato sin avisos y da la medición esperada")

# Errores de formato en hallazgos.jsonl.
registro, _ = copia()
filas = leer_lineas(registro / "hallazgos.jsonl")
del filas[0]["motivo"]
escribir_lineas(registro / "hallazgos.jsonl", filas)
errores, _ = bp.validar(registro)
comprobar(any("faltan los campos motivo" in e for e in errores), "un campo que falta es un error")

registro, _ = copia()
cambiar_hallazgo(registro, "2026-01-06-1", clasificacion="importante")
errores, _ = bp.validar(registro)
comprobar(any("clasificacion 'importante'" in e for e in errores), "una clasificación que no existe es un error")

registro, _ = copia()
cambiar_hallazgo(registro, "2026-01-06-1", noche=None)
errores, _ = bp.validar(registro)
comprobar(any("tiene que tener noche" in e for e in errores), "un hallazgo sin noche es un error")

registro, _ = copia()
cambiar_hallazgo(registro, "2026-01-06-3", motivo=None)
errores, _ = bp.validar(registro)
comprobar(any("tiene que tener motivo" in e for e in errores), "un descarte sin motivo es un error")

registro, _ = copia()
cambiar_hallazgo(registro, "2026-01-06-1", noche="2026-01-07")
errores, _ = bp.validar(registro)
comprobar(any("no empieza con su noche" in e for e in errores), "un id que no empieza con su noche es un error")

# Errores de formato en indice.md.
registro, _ = copia()
reemplazar(registro / "indice.md", "| fecha | hallazgos |", "| dia | hallazgos |")
errores, _ = bp.validar(registro)
comprobar(any("sección A: el encabezado" in e for e in errores), "un encabezado de tabla distinto es un error")

registro, _ = copia()
reemplazar(registro / "indice.md", "- Primera noche: 2026-01-05\n", "")
errores, _ = bp.validar(registro)
comprobar(any("la primera y la última noche" in e for e in errores), "un índice sin la primera noche es un error")

registro, _ = copia()
reemplazar(registro / "indice.md", "| 00:15:00 |", "| 15 minutos |")
errores, _ = bp.validar(registro)
comprobar(any("no es hh:mm:ss" in e for e in errores), "una duración con otra forma es un error")

# Avisos: el registro se contradice.
registro, _ = copia()
reemplazar(registro / "indice.md", "| 2026-01-06 | 2 |", "| 2026-01-06 | 5 |")
errores, avisos = bp.validar(registro)
comprobar(not errores and any("la noche 2026-01-06 dice 5 hallazgos" in a for a in avisos),
          "una noche de A con otra cantidad de hallazgos es un aviso")

registro, _ = copia()
reemplazar(registro / "indice.md", "| 3 | 2026-01-12 | 1 |", "| 3 | 2026-01-12 | 0 |")
errores, avisos = bp.validar(registro)
comprobar(not errores and any("dice 0 hallazgos adicionales" in a for a in avisos),
          "un tramo con otros hallazgos adicionales es un aviso, como en el registro real")

registro, _ = copia()
reemplazar(registro / "indice.md", "| 2026-01-10 a 2026-01-12 | 3 |", "| 2026-01-11 a 2026-01-12 | 2 |")
errores, avisos = bp.validar(registro)
comprobar(any("no están en A ni en un tramo, desde 2026-01-10" in a for a in avisos), "un hueco en el período es un aviso")

# La revisión.
registro, _ = copia()
filas = leer_lineas(registro / "revision" / "entradas.jsonl")
escribir_lineas(registro / "revision" / "entradas.jsonl", filas + [{"id": "2026-02-01-1", "fuente": "arce"}])
errores, _ = bp.validar(registro)
comprobar(any("no es un id del registro" in e for e in errores), "una revisión de un id que no existe es un error")

registro, _ = copia()
filas = leer_lineas(registro / "revision" / "entradas.jsonl")
filas[0]["etiqueta"] = "publicable"
escribir_lineas(registro / "revision" / "entradas.jsonl", filas)
errores, _ = bp.validar(registro)
comprobar(any("es un hallazgo y no lleva etiqueta" in e for e in errores), "un hallazgo con etiqueta es un error")

registro, _ = copia()
escribir_lineas(registro / "revision" / "correspondencias.jsonl",
                leer_lineas(registro / "revision" / "correspondencias.jsonl") + [{"registro": "2026-01-12-3", "hecho": "arce:ocds-x:fusion"}])
errores, _ = bp.validar(registro)
comprobar(any("no es una clave de hecho" in e for e in errores), "una clave de hecho con un tipo que no existe es un error")

registro, salida = copia()
filas = [f for f in leer_lineas(registro / "revision" / "entradas.jsonl") if f["id"] != "2026-01-12-1"]
escribir_lineas(registro / "revision" / "entradas.jsonl", filas)
errores, avisos = bp.validar(registro)
medicion = bp.medir(registro, salida)
comprobar(not errores and any("1 entradas del registro sin revisar" in a for a in avisos)
          and medicion["hallazgos"]["sinRevisar"] == 1 and medicion["hallazgos"]["noEncontrados"] == 0,
          "una entrada sin revisar es un aviso y cuenta como sin revisar, no como no encontrada")

registro, salida = copia()
shutil.rmtree(registro / "revision")
errores, avisos = bp.validar(registro)
medicion = bp.medir(registro, salida)
comprobar(not errores and any("No hay revisión" in a for a in avisos) and medicion["hallazgos"]["sinRevisar"] == 9
          and medicion["hechos"]["todos"]["sinJuzgar"] == 9 and medicion["hechos"]["todos"]["precision"] is None,
          "sin revisión todo queda sin revisar o sin juzgar, y la precisión no se puede medir")

# El cómputo.
registro, salida = copia()
escribir_lineas(registro / "revision" / "extras.jsonl",
                leer_lineas(registro / "revision" / "extras.jsonl") + [{"hecho": "arce:ocds-ejemplo-0008:llamado", "juicio": "correcto"}])
medicion = bp.medir(registro, salida)
comprobar(medicion["hechos"]["INFERRED"]["sinJuzgar"] == 0 and medicion["hechos"]["INFERRED"]["precision"] == 0.5,
          "juzgar un hecho sin juzgar cambia la precisión de su grado, sin volver a correr")

# La huella del contenido juzgado (formato.md, metricas.md).
medicion = bp.medir(*copia())
viejo = [f for f in medicion["detalle"]["hechos"] if f.get("juicioViejo")]
comprobar(medicion["hechos"]["juiciosViejos"] == 1 and [f["clave"] for f in viejo] == ["arce:ocds-ejemplo-0006:adjudicacion"]
          and viejo[0]["resultado"] == "sin-juzgar",
          "un juicio con la huella de otro contenido es viejo y cuenta como sin juzgar")
fila_0007 = next(f for f in medicion["detalle"]["hechos"] if f["clave"] == "arce:ocds-ejemplo-0007:llamado")
comprobar(fila_0007["resultado"] == "falso-positivo-otra-entidad" and "juicioViejo" not in fila_0007,
          "un juicio con la misma huella que la salida vale")
registro, salida = copia()
reemplazar(salida, ', "contenido": "sha256:44b21400e539814c"', "")
comprobar(bp.medir(registro, salida)["hechos"]["juiciosViejos"] == 0,
          "si la salida no trae la huella, el juicio vale como antes")
registro, salida = copia()
reemplazar(registro / "revision" / "extras.jsonl", '"contenido": "sha256:86b815045c2d2d50"', '"contenido": "86b815045c2d2d50"')
comprobar(any("no es una huella sha256:" in e for e in bp.validar(registro)[0]), "una huella de contenido con otra forma es un error")

registro, salida = copia()
datos = json.loads(salida.read_text(encoding="utf-8"))
datos["fuentes"] = ["arce"]
salida.write_text(json.dumps(datos), encoding="utf-8")
medicion = bp.medir(registro, salida)
comprobar(medicion["hallazgos"]["fueraDeEstaMedicion"] == 3 and medicion["hallazgos"]["cobertura"] == 0.3333,
          "con solo ARCE, lo de IMPO queda fuera de esta medición")

registro, salida = copia()
datos = json.loads(salida.read_text(encoding="utf-8"))
datos["fuentes"] = ["impo"]
salida.write_text(json.dumps(datos), encoding="utf-8")
medicion = bp.medir(registro, salida)
comprobar(medicion["publicables"]["cobertura"] is None, "sin nada a su alcance, la cobertura no se puede medir")

registro, salida = copia()
datos = json.loads(salida.read_text(encoding="utf-8"))
datos["corridas"] = [c for c in datos["corridas"] if c["noche"] != "2026-01-11"]
datos["hechos"] = [h for h in datos["hechos"] if h["noche"] != "2026-01-11"]
salida.write_text(json.dumps(datos), encoding="utf-8")
medicion = bp.medir(registro, salida)
comprobar(medicion["hallazgos"]["fueraDeLasNochesMedidas"] == 2 and medicion["hallazgos"]["noEncontrados"] == 0,
          "una noche que no se corrió deja sus publicaciones fuera de las noches medidas")

registro, salida = copia()
datos = json.loads(salida.read_text(encoding="utf-8"))
datos["hechos"].append({"clave": "arce:ocds-ejemplo-0001:adjudicacion", "noche": "2026-01-06", "grado": "PROVEN"})
salida.write_text(json.dumps(datos), encoding="utf-8")
try:
    bp.medir(registro, salida)
    comprobar(False, "un hecho repetido en la salida es un error")
except bp.ErrorDeFormato as e:
    comprobar("está repetido" in str(e), "un hecho repetido en la salida es un error")

# Con un archivo de destino, contar lo escribe en UTF-8 y da lo mismo que medir.
registro, salida = copia()
destino = salida.parent / "mediciones" / "prueba" / "medicion.json"
codigo = bp.main(["contar", str(registro), str(salida), str(destino)])
escrito = destino.read_bytes()
comprobar(codigo == 0 and json.loads(escrito.decode("utf-8")) == bp.medir(registro, salida) and not escrito.startswith(b"\xff\xfe"),
          "contar con destino escribe la medición en UTF-8")

# El sorteo de extras: el mismo con la misma semilla, y la estimación con su intervalo.
registro, salida = copia()
primero = bp.sortear(registro, salida, 3, 7)
comprobar(primero == bp.sortear(registro, salida, 3, 7) and primero["poblacion"] == 3 and primero["tamano"] == 3,
          "el sorteo da los mismos extras con la misma semilla")
comprobar(len({tuple(bp.sortear(registro, salida, 2, semilla)["claves"]) for semilla in range(10)}) > 1,
          "otra semilla puede dar otros extras")
codigo = bp.main(["sortear", str(registro), str(salida), "3", "7"])
sorteo = json.loads((registro / "revision" / "sorteo.json").read_text(encoding="utf-8"))
comprobar(codigo == 0 and sorteo == primero, "sortear escribe revision/sorteo.json")
escribir_lineas(registro / "revision" / "extras.jsonl",
                [{"hecho": k, "juicio": j} for k, j in zip(sorteo["claves"], ("correcto", "correcto", "no-nuevo"))])
errores, avisos = bp.validar(registro)
estimacion = bp.medir(registro, salida)["hechos"]["sorteoDeExtras"]
comprobar(not errores and not avisos and estimacion["juzgados"] == 3 and estimacion["correctos"] == 2
          and estimacion["precision"] == 0.6667 and estimacion["falsos"]["noNuevo"] == 1
          and estimacion["intervalo95"] == [0.2077, 0.9385],
          "con el sorteo juzgado, la medición estima la precisión de los extras con su intervalo de Wilson")
comprobar(estimacion["intervaloDeTodo95"][0] <= estimacion["precisionDeTodo"] <= estimacion["intervaloDeTodo95"][1],
          "y estima la de todo lo publicado, dentro de su intervalo")
escribir_lineas(registro / "revision" / "extras.jsonl",
                [{"hecho": k, "juicio": "correcto"} for k in sorteo["claves"]]
                + [{"hecho": "arce:ocds-ejemplo-9999:llamado", "juicio": "correcto"}])
comprobar(any("fuera del sorteo" in a for a in bp.validar(registro)[1]), "un extra juzgado fuera del sorteo es un aviso")
reemplazar(salida, '"entidad"', ' "entidad"')
otra = bp.medir(registro, salida)["hechos"]
comprobar("sorteoDeExtras" not in otra and otra.get("sorteoDeOtraSalida") is True,
          "un sorteo de otra salida no estima nada: la medición sale sin estimación y lo dice")

# La huella depende de cada byte de los archivos del registro, y no de la revisión.
registro, _ = copia()
antes = bp.leer_registro(registro)[0]["huella"]
reemplazar(registro / "revision" / "extras.jsonl", "correcto", "correcto")
(registro / "revision" / "nota.txt").write_text("otra cosa", encoding="utf-8")
comprobar(bp.leer_registro(registro)[0]["huella"] == antes, "la huella del registro no cambia con la revisión")
reemplazar(registro / "indice.md", "\n", "\r\n")
comprobar(bp.leer_registro(registro)[0]["huella"] != antes, "la huella cambia con los saltos de línea")

# Lo que se versiona no lleva texto del registro.
texto = json.dumps(bp.medir(*copia()), ensure_ascii=False)
registro_muestra = leer_lineas(bp.REGISTRO_DE_MUESTRA / "hallazgos.jsonl")
campos_libres = {f[c] for f in registro_muestra for c in ("organismo", "que_dice", "motivo", "url", "sitio") if f[c]}
comprobar(not any(valor in texto for valor in campos_libres), "la medición no copia ningún texto libre del registro")

if fallas:
    print(f"\n{len(fallas)} fallas")
    sys.exit(1)
print("\nTodo bien.")
