#!/usr/bin/env python3
"""Una sesión con el modelo, sin agentes, sobre las mismas noches que El Parte (tablero, T42).

Es lo que haría una persona en la Claude Console: le da al modelo lo que ARCE publicó del BPS una
noche y le pide el parte de la mañana, en un solo pedido. El resultado se escribe con la forma de la
salida de El Parte (banco-de-pruebas/formato.md), así lo cuenta la misma herramienta,
herramientas/banco_de_pruebas.py, con el mismo registro y la misma revisión.

    correr  carpeta noche...   Pide el parte de cada noche. Lee carpeta/<noche>.json, la lista de
                               publicaciones que El Parte leyó esa noche, ya tapadas, y escribe
                               carpeta/respuestas/<noche>.json y carpeta/salida-de-la-sesion.json.
                               Necesita la clave en ANTHROPIC_API_KEY; no la lee de ningún archivo.
    revisar carpeta            Lo que se puede revisar sin una persona: cuántos hechos, cuántos con
                               una clave que no está en las publicaciones, cuántos con una cifra que
                               no está en la publicación, y el costo y el tiempo. Escribe
                               carpeta/revision-automatica.json.
    elparte salida.json carpeta noche...
                               La salida de una medición de El Parte, acotada a esas noches, en
                               carpeta/salida-de-el-parte.json, para contar los dos lados igual.

Usa solo la biblioteca estándar, así corre en cualquier Python 3.11 o más nuevo.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

MODELO = "claude-sonnet-5-5"
# Dólares por millón de tokens, los de la tabla de precios de la aplicación (ADR 0005).
PRECIO_ENTRADA, PRECIO_SALIDA = 2.00, 10.00
TIPOS = {"llamado", "aclaracion", "adjudicacion", "cancelacion"}
CIFRA = re.compile(r"\d(?:[\d.,/-]*\d)?")

SISTEMA = """Sos quien escribe el parte de la mañana sobre las compras del Banco de Previsión Social (BPS) \
de Uruguay. Recibís, en OCDS, las publicaciones que ARCE, la agencia de compras estatales, publicó la \
noche indicada y el día anterior, con los datos personales tapados como [persona].

Escribí el parte: un hecho por cada novedad que valga contar a alguien que sigue al BPS. Para cada \
hecho:
- "ocid": el ocid de la publicación, tal cual.
- "tipo": llamado, aclaracion, adjudicacion o cancelacion.
- "afirmacion": qué pasó, en una o dos frases, en español rioplatense.
- "grado": PROVEN si todo lo que afirmás está literal en la publicación; INFERRED si se deduce.
- "razonamiento": si es INFERRED, de qué se deduce.
- "campos": los campos de la publicación que lo respaldan, como "tender.title = ...".

No agregues nada que no esté en las publicaciones. Respondé solo con JSON: {"hechos": [...]}."""


def huella(texto):
    return "sha256:" + hashlib.sha256(texto.encode("utf-8")).hexdigest()[:16]


def pedir(clave, usuario):
    cuerpo = json.dumps({"model": MODELO, "max_tokens": 8000, "system": SISTEMA,
                         "messages": [{"role": "user", "content": usuario}]}).encode("utf-8")
    pedido = urllib.request.Request("https://api.anthropic.com/v1/messages", data=cuerpo, method="POST", headers={
        "x-api-key": clave, "anthropic-version": "2023-06-01", "content-type": "application/json"})
    with urllib.request.urlopen(pedido, timeout=600) as respuesta:
        return json.loads(respuesta.read())


def hechos_de(texto):
    """El JSON de la respuesta, aunque venga entre comillas invertidas o con algo antes."""
    inicio, fin = texto.find("{"), texto.rfind("}")
    return json.loads(texto[inicio:fin + 1]).get("hechos", [])


def correr(carpeta, noches):
    clave = os.environ.get("ANTHROPIC_API_KEY", "").strip().strip('"').strip("'")
    if not clave:
        sys.exit("Falta ANTHROPIC_API_KEY en el entorno.")
    (carpeta / "respuestas").mkdir(exist_ok=True)
    for noche in noches:
        destino = carpeta / "respuestas" / f"{noche}.json"
        if destino.exists():
            print(f"{noche}: ya estaba")
            continue
        publicaciones = json.loads((carpeta / f"{noche}.json").read_text(encoding="utf-8-sig"))
        usuario = f"Noche: {noche}\n\nPublicaciones ({len(publicaciones)}):\n" + json.dumps(
            [p["publicacion"] for p in publicaciones], ensure_ascii=False)
        empezo = time.time()
        respuesta = pedir(clave, usuario)
        segundos = round(time.time() - empezo, 1)
        uso = respuesta.get("usage", {})
        costo = round((uso.get("input_tokens", 0) * PRECIO_ENTRADA + uso.get("output_tokens", 0) * PRECIO_SALIDA) / 1e6, 6)
        texto = "".join(b.get("text", "") for b in respuesta.get("content", []))
        destino.write_text(json.dumps({"noche": noche, "segundos": segundos, "costoUsd": costo, "uso": uso,
                                       "texto": texto}, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"{noche}: {len(publicaciones)} publicaciones, {segundos} s, {costo} USD")
    escribir_salida(carpeta, noches)


def escribir_salida(carpeta, noches):
    corridas, hechos, vistas = [], [], set()
    for noche in noches:
        r = json.loads((carpeta / "respuestas" / f"{noche}.json").read_text(encoding="utf-8"))
        corridas.append({"noche": noche, "corrida": f"sesion-{noche}", "estado": "completa", "noRepetibles": [],
                         "costoUsd": r["costoUsd"], "duracionSegundos": round(r["segundos"]), "lecturasFallidas": 0})
        for h in hechos_de(r["texto"]):
            clave = f"arce:{h.get('ocid', '')}:{h.get('tipo', '')}"
            if clave in vistas:
                continue
            vistas.add(clave)
            grado = h.get("grado") if h.get("grado") in ("PROVEN", "INFERRED") else "INFERRED"
            hechos.append({"clave": clave, "noche": noche, "grado": grado, "contenido": huella(h.get("afirmacion", ""))})
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    salida = {"commit": commit or "sin-git", "entidad": "organismo:bps", "fuentes": ["arce"],
              "modelos": {"sesion": MODELO}, "corridas": corridas, "hechos": hechos}
    (carpeta / "salida-de-la-sesion.json").write_text(json.dumps(salida, ensure_ascii=False, indent=1) + "\n",
                                                       encoding="utf-8")


def revisar(carpeta):
    resumen = {"noches": 0, "hechos": 0, "conClaveQueNoEsta": 0, "conTipoQueNoExiste": 0, "conCifraQueNoEsta": 0,
               "sinCampos": 0, "inferred": 0, "costoUsd": 0.0, "segundos": 0.0, "tokensEntrada": 0, "tokensSalida": 0,
               "ejemplosDeCifras": []}
    for archivo in sorted((carpeta / "respuestas").glob("*.json")):
        r = json.loads(archivo.read_text(encoding="utf-8"))
        publicaciones = json.loads((carpeta / f"{r['noche']}.json").read_text(encoding="utf-8-sig"))
        por_ocid = {}
        for p in publicaciones:
            por_ocid.setdefault(p["publicacion"].get("ocid", ""), []).append(json.dumps(p["publicacion"], ensure_ascii=False))
        resumen["noches"] += 1
        resumen["costoUsd"] += r["costoUsd"]
        resumen["segundos"] += r["segundos"]
        resumen["tokensEntrada"] += r["uso"].get("input_tokens", 0)
        resumen["tokensSalida"] += r["uso"].get("output_tokens", 0)
        for h in hechos_de(r["texto"]):
            resumen["hechos"] += 1
            resumen["inferred"] += h.get("grado") == "INFERRED"
            resumen["sinCampos"] += not h.get("campos")
            resumen["conTipoQueNoExiste"] += h.get("tipo") not in TIPOS
            fuente = por_ocid.get(h.get("ocid", ""))
            if not fuente:
                resumen["conClaveQueNoEsta"] += 1
                continue
            permitidas = cifras_permitidas(" ".join(fuente) + " " + r["noche"])
            sobran = cifras(h.get("afirmacion", "")) - permitidas
            if sobran:
                resumen["conCifraQueNoEsta"] += 1
                if len(resumen["ejemplosDeCifras"]) < 5:
                    resumen["ejemplosDeCifras"].append({"clave": f"arce:{h.get('ocid')}:{h.get('tipo')}",
                                                        "cifras": sorted(sobran)})
    resumen["costoUsd"] = round(resumen["costoUsd"], 4)
    resumen["segundos"] = round(resumen["segundos"], 1)
    (carpeta / "revision-automatica.json").write_text(json.dumps(resumen, ensure_ascii=False, indent=1) + "\n",
                                                      encoding="utf-8")
    print(json.dumps(resumen, ensure_ascii=False, indent=1))


def cifras(texto):
    """Como el analista (app/.../analista/Analista.java): sin separadores ni ceros a la izquierda."""
    return {re.sub(r"^0+(?=\d)", "", re.sub(r"[.,/-]", "", c)) for c in CIFRA.findall(texto)}


def cifras_permitidas(texto):
    permitidas = cifras(texto)
    for c in CIFRA.findall(texto):
        permitidas |= {re.sub(r"^0+(?=\d)", "", p) for p in re.split(r"[.,/-]", c) if p}
    return permitidas


def elparte(salida, carpeta, noches):
    s = json.loads(Path(salida).read_text(encoding="utf-8"))
    s["corridas"] = [c for c in s["corridas"] if c["noche"] in noches]
    s["hechos"] = [h for h in s["hechos"] if h["noche"] in noches]
    (carpeta / "salida-de-el-parte.json").write_text(json.dumps(s, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(s['corridas'])} noches, {len(s['hechos'])} hechos")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    orden = sys.argv[1]
    if orden == "correr":
        correr(Path(sys.argv[2]), sys.argv[3:])
    elif orden == "revisar":
        revisar(Path(sys.argv[2]))
    elif orden == "elparte":
        elparte(sys.argv[2], Path(sys.argv[3]), sys.argv[4:])
    else:
        sys.exit(__doc__)
