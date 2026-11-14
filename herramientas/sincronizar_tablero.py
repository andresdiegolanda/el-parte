#!/usr/bin/env python3
"""Sincroniza el kanban publicado con tablero.md (adrs/0009-kanban-editable-sincronizado.md).

La página publicada guarda sus tarjetas en su base. Una sesión de desarrollo
vuelca esa base a un archivo JSON, corre este script y escribe en la base las
operaciones que el script deja en otro archivo. El script:

1. Aplica a tablero.md los cambios hechos en el kanban, salvo los que chocan
   con un cambio del repositorio (gana el repositorio) o rompen sus reglas.
2. Si tablero.md cambió, lo reescribe, regenera kanban/index.html y suma una
   línea al CHANGELOG.
3. Deja en el archivo de operaciones el estado del tablero para la base.

Uso:
  python3 herramientas/sincronizar_tablero.py VOLCADO.json [--operaciones OPS.json]
                                              [--remoto origin/main] [--sin-fetch]

VOLCADO.json tiene la forma:
  {"tarjetas": [{"id": "T02", "version": 3, "data": {...}}, ...],
   "meta": {"version": 1} o null}

De las tarjetas que no están marcadas como editadas en el kanban alcanza con
copiar id, version y, de data, editadaEnKanban, huellaRepo, huellaContenido y
orden: el script solo lee el resto en las marcadas. Las operaciones solo
reescriben las tarjetas que cambiaron, más el documento meta. Si de una tarjeta
solo cambió el lugar dentro de su columna, la operación lleva solo el orden.

Sale con 0 si tablero.md no cambió, con 2 si cambió (hay que hacer commit y
push del tablero y volver a correrlo) y con 1 si hubo un error.
"""

import argparse
import copy
import datetime
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import generar_kanban
import tablero_md

RAIZ = Path(__file__).resolve().parent.parent
TABLERO = RAIZ / "tablero.md"
CHANGELOG = RAIZ / "CHANGELOG.md"
COLECCION = "tarjetas"
META = "meta/tablero"
EDITABLES = ("titulo", "columna", "dueno", "dependeDe", "esperaA", "fecha", "etapas", "lista", "notas", "registro")
PROHIBIDOS = (chr(0x2014), chr(0x2013), chr(0x2015))
LINEAS_RESERVADAS = {"Etapas:", "Lista:", "Registro:"}


def git(*args):
    return subprocess.run(["git", *args], cwd=RAIZ, capture_output=True, text=True)


def tablero_remoto(remoto):
    """Lee tablero.md tal como está en GitHub. Devuelve (datos, hash_del_commit)."""
    rama = remoto.split("/", 1)[1] if "/" in remoto else remoto
    commit = git("rev-parse", "--short", remoto).stdout.strip()
    texto = git("show", f"{remoto}:tablero.md")
    if texto.returncode != 0:
        raise SystemExit(f"No se pudo leer tablero.md de {remoto} (rama {rama}): {texto.stderr.strip()}")
    with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as f:
        f.write(texto.stdout)
        temporal = f.name
    datos, errores = tablero_md.leer(temporal)
    Path(temporal).unlink()
    if errores:
        raise SystemExit("El tablero.md de GitHub no se puede leer:\n  " + "\n  ".join(errores))
    return datos, commit


def de_la_base(doc, local):
    """Convierte un documento de la base en una tarjeta, con tipo y detalle del repositorio."""
    d = doc["data"]
    t = {
        "id": doc["id"], "titulo": d.get("titulo", "").strip(), "columna": d.get("columna", "para-empezar"),
        "orden": float(d.get("orden", 0)), "tipo": d.get("tipo", ""), "dueno": d.get("dueno", ""),
        "dependeDe": d.get("dependeDe", "").strip(), "esperaA": d.get("esperaA", "").strip(),
        "fecha": d.get("fecha", "").strip(),
        "detalle": [dict(x) for x in d.get("detalle", [])],
        "etapas": [{"texto": e["texto"], "hecho": bool(e["hecho"])} for e in d.get("etapas", [])],
        "lista": [{"texto": e["texto"].strip(), "hecho": bool(e["hecho"])} for e in d.get("lista", [])],
        "notas": [" ".join(n.split()) for n in d.get("notas", []) if n.strip()],
        "registro": [{"fecha": r["fecha"], "texto": " ".join(r["texto"].split())} for r in d.get("registro", [])],
        "linea": 0,
    }
    if local is not None:
        t["tipo"], t["detalle"] = local["tipo"], copy.deepcopy(local["detalle"])
    t["dependencias"] = tablero_md.expandir_dependencias(t["dependeDe"])
    return t


def problemas_de_texto(t):
    """Textos que romperían tablero.md o la regla de los guiones largos."""
    textos = [t["titulo"], t["dueno"], t["dependeDe"], t["esperaA"], t["fecha"]]
    textos += t["notas"] + [i["texto"] for i in t["lista"]] + [r["texto"] for r in t["registro"]]
    problemas = []
    if any(c in x for x in textos for c in PROHIBIDOS):
        problemas.append("tiene guiones largos, que el proyecto no usa")
    if any(x.strip() in LINEAS_RESERVADAS or x.startswith("#") for x in t["notas"]):
        problemas.append("tiene una nota que se confundiría con un bloque de la tarjeta")
    if not t["titulo"]:
        problemas.append("no tiene título")
    return problemas


def siguiente_id(usados):
    numeros = [int(i[1:]) for i in usados if re.fullmatch(r"T\d{2}", i)]
    return f"T{max(numeros, default=0) + 1:02d}"


def renumerar(tarjetas):
    for col in {t["columna"] for t in tarjetas}:
        de_la_columna = sorted((t for t in tarjetas if t["columna"] == col), key=lambda t: (t["orden"], t["id"]))
        for i, t in enumerate(de_la_columna, start=1):
            t["orden"] = i


def errores_por_tarjeta(datos):
    errores = tablero_md.validar(datos)
    culpables = {}
    for e in errores:
        m = re.search(r": (T\d{2})(:| )", e)
        culpables.setdefault(m.group(1) if m else "?", []).append(e)
    return errores, culpables


def sincronizar(volcado, remoto, hoy):
    local, errores = tablero_md.leer(TABLERO)
    if errores:
        raise SystemExit("tablero.md tiene errores de formato; arreglalos antes de sincronizar:\n  " + "\n  ".join(errores))
    base, commit = tablero_remoto(remoto)
    tipos = local["tipos"]
    original = copy.deepcopy(local["tarjetas"])
    por_id = {t["id"]: t for t in local["tarjetas"]}
    base_por_id = {t["id"]: t for t in base["tarjetas"]}
    huella = lambda t: tablero_md.huella(t, tipos) if t is not None else None

    informe = {"aplicadas": [], "nuevas": {}, "conflictos": [], "rechazadas": [], "ordenes": []}
    aplicadas = {}

    usados = set(por_id) | {d["id"] for d in volcado.get("tarjetas", [])}
    for doc in sorted(volcado.get("tarjetas", []), key=lambda d: d["id"]):
        d = doc["data"]
        if not d.get("editadaEnKanban"):
            continue
        local_t = por_id.get(doc["id"])
        nueva = de_la_base(doc, local_t)
        problemas = problemas_de_texto(nueva)
        if problemas:
            informe["rechazadas"].append((doc["id"], "; ".join(problemas)))
            continue
        if local_t is None:
            if not re.fullmatch(r"T\d{2}", doc["id"]):
                nuevo_id = siguiente_id(usados)
                usados.add(nuevo_id)
                informe["nuevas"][doc["id"]] = nuevo_id
                nueva["id"] = nuevo_id
            if nueva["tipo"] not in tipos:
                informe["rechazadas"].append((doc["id"], f"tipo desconocido '{nueva['tipo']}'"))
                continue
            nueva["_tipo_original"] = tipos[nueva["tipo"]]["nombre"]
            local["tarjetas"].append(nueva)
            por_id[nueva["id"]] = nueva
            aplicadas[nueva["id"]] = None
            continue
        if huella(nueva) == huella(local_t):
            if nueva["columna"] == local_t["columna"] and nueva["orden"] != local_t["orden"]:
                local_t["orden"] = nueva["orden"]
                informe["ordenes"].append(local_t["id"])
            continue
        base_t = base_por_id.get(doc["id"])
        sin_cambios_en_github = (base_t is None and d.get("huellaRepo") is None) or \
                                (base_t is not None and huella(base_t) == d.get("huellaRepo"))
        cambios_locales = base_t is not None and huella(local_t) != huella(base_t)
        if sin_cambios_en_github and not cambios_locales:
            anterior = copy.deepcopy(local_t)
            for campo in EDITABLES:
                local_t[campo] = nueva[campo]
            local_t["orden"] = nueva["orden"]
            local_t["dependencias"] = nueva["dependencias"]
            aplicadas[local_t["id"]] = anterior
        else:
            aviso = "El cambio hecho en el kanban no se aplicó porque la tarjeta también cambió en el repositorio."
            if not local_t["registro"] or local_t["registro"][-1]["texto"] != aviso:
                local_t["registro"].append({"fecha": hoy, "texto": aviso})
            informe["conflictos"].append(local_t["id"])

    # Revertir lo aplicado que rompe reglas, hasta que el tablero valide.
    while True:
        renumerar(local["tarjetas"])
        errores, culpables = errores_por_tarjeta(local)
        if not errores:
            break
        revertir = [i for i in culpables if i in aplicadas] or list(aplicadas)
        if not revertir:
            raise SystemExit("El tablero no valida y no es por cambios del kanban:\n  " + "\n  ".join(errores))
        for i in revertir:
            motivo = "; ".join(culpables.get(i, ["rompía otra regla del tablero"]))
            informe["rechazadas"].append((i, motivo))
            if aplicadas[i] is None:
                local["tarjetas"] = [t for t in local["tarjetas"] if t["id"] != i]
                informe["nuevas"] = {k: v for k, v in informe["nuevas"].items() if v != i}
            else:
                por_id[i].update(aplicadas[i])
            del aplicadas[i]
    informe["aplicadas"] = [i for i in aplicadas if i not in informe["nuevas"].values()]

    intro, orden = tablero_md.intro_y_columnas(TABLERO)
    texto = tablero_md.serializar(intro, orden, local["columnas"], tipos, local["tarjetas"])
    cambio = texto != TABLERO.read_text(encoding="utf-8")
    if cambio:
        TABLERO.write_text(texto, encoding="utf-8", newline="\n")
        anotar_changelog(informe, original, local["tarjetas"], hoy)
        html, problemas = generar_kanban.generar()
        if problemas:
            raise SystemExit("No se pudo regenerar el kanban:\n  " + "\n  ".join(problemas))
        generar_kanban.SALIDA.write_text(html, encoding="utf-8", newline="\n")
    return local, base_por_id, commit, informe, cambio


def anotar_changelog(informe, antes, despues, hoy):
    previas = {t["id"]: t for t in antes}
    nombre = {"pendiente": "Pendiente", "para-empezar": "Para empezar", "en-curso": "En curso",
              "esperando": "Esperando", "hecho": "Hecho"}
    partes = []
    for t in despues:
        p = previas.get(t["id"])
        if p is None:
            partes.append(f"{t['id']} nueva")
        elif p["columna"] != t["columna"]:
            partes.append(f"{t['id']} pasa a {nombre.get(t['columna'], t['columna'])}")
        elif t["id"] in informe["aplicadas"]:
            partes.append(f"{t['id']} actualizada")
    if informe["ordenes"] and not partes:
        partes.append("cambia el orden de prioridad")
    if not partes:
        partes.append("se anotan conflictos en el registro de las tarjetas")
    linea = f"- Sincronización con el kanban: {', '.join(partes)}. Por qué: [tablero.md](tablero.md).\n"
    texto = CHANGELOG.read_text(encoding="utf-8")
    encabezado = f"## {hoy}\n\n"
    if encabezado in texto:
        texto = texto.replace(encabezado, encabezado + linea, 1)
    else:
        i = texto.index("\n## ") + 1 if "\n## " in texto else len(texto)
        texto = texto[:i] + encabezado + linea + "\n" + texto[i:]
    CHANGELOG.write_text(texto, encoding="utf-8", newline="\n")


def operaciones(local, base_por_id, commit, volcado, ahora, informe):
    tipos = local["tipos"]
    versiones = {d["id"]: d["version"] for d in volcado.get("tarjetas", [])}
    marcadas = {d["id"] for d in volcado.get("tarjetas", []) if d["data"].get("editadaEnKanban")}
    ops = []
    pendiente_de_subir = False
    rechazadas = {}
    for i, motivo in informe["rechazadas"]:
        rechazadas[i] = motivo
    for i, motivo in rechazadas.items():
        if i in versiones:
            # Se deja la edición del kanban como está, para que se pueda corregir, y se avisa el motivo.
            ops.append({"op": "update", "path": f"{COLECCION}/{i}", "ifVersion": versiones[i],
                        "data": {"rechazoSincronizacion": motivo, "sincronizadaEn": ahora}})
    previos = {d["id"]: d["data"] for d in volcado.get("tarjetas", [])}
    for t in sorted(local["tarjetas"], key=lambda t: t["id"]):
        if t["id"] in rechazadas:
            continue
        base_t = base_por_id.get(t["id"])
        h_base = tablero_md.huella(base_t, tipos) if base_t else None
        h_local = tablero_md.huella(t, tipos)
        en_github = h_base == h_local
        pendiente_de_subir |= not en_github
        previo = previos.get(t["id"])
        sin_cambios = (previo is not None and t["id"] not in marcadas and previo.get("huellaContenido") == h_local
                       and previo.get("huellaRepo") == h_base and not previo.get("rechazoSincronizacion"))
        if sin_cambios:
            # La huella no incluye el orden: si la tarjeta cambió de lugar en su columna, solo se escribe eso.
            if previo.get("orden") is None or float(previo["orden"]) != float(t["orden"]):
                ops.append({"op": "update", "path": f"{COLECCION}/{t['id']}", "ifVersion": versiones[t["id"]],
                            "data": {"orden": t["orden"], "sincronizadaEn": ahora}})
            continue
        data = {
            "titulo": t["titulo"], "columna": t["columna"], "orden": t["orden"], "tipo": t["tipo"],
            "dueno": t["dueno"], "dependeDe": t["dependeDe"], "esperaA": t["esperaA"], "fecha": t["fecha"],
            "detalle": t["detalle"], "etapas": t["etapas"], "lista": t["lista"], "notas": t["notas"],
            "registro": t["registro"], "huellaRepo": h_base, "huellaContenido": h_local,
            "editadaEnKanban": (t["id"] in marcadas) and not en_github,
            "sincronizadaEn": ahora,
        }
        op = {"op": "set", "path": f"{COLECCION}/{t['id']}", "data": data}
        if t["id"] in versiones:
            op["ifVersion"] = versiones[t["id"]]
        ops.append(op)
    for temporal in informe["nuevas"]:
        # La tarjeta nueva ya tiene su número: el documento con el identificador temporal sobra.
        ops.append({"op": "delete", "path": f"{COLECCION}/{temporal}", "ifVersion": versiones[temporal]})
    datos_pagina = generar_kanban.datos_para_la_pagina(local)["meta"]
    meta = {
        "actualizado": local["meta"]["actualizado"], "nota": local["meta"]["nota"],
        "calendario": local["meta"]["calendario"], "repositorio": local["meta"]["repositorio"],
        "tablero": datos_pagina["tablero"], "columnas": local["columnas"], "tipos": local["tipos"],
        "duenos": local["duenos"], "sincronizadoEn": ahora, "commitGithub": commit,
        "pendienteDeSubir": pendiente_de_subir,
    }
    op = {"op": "set", "path": META, "data": meta}
    if volcado.get("meta"):
        op["ifVersion"] = volcado["meta"]["version"]
    ops.append(op)
    return [ops[i:i + 40] for i in range(0, len(ops), 40)]


def main():
    p = argparse.ArgumentParser(description="Sincroniza el kanban publicado con tablero.md.")
    p.add_argument("volcado")
    p.add_argument("--operaciones", default=str(RAIZ / "tmp" / "kanban-operaciones.json"))
    p.add_argument("--remoto", default="origin/main")
    p.add_argument("--sin-fetch", action="store_true")
    a = p.parse_args()
    if not a.sin_fetch:
        remoto, rama = a.remoto.split("/", 1)
        r = git("fetch", "-q", remoto, f"+refs/heads/{rama}:refs/remotes/{remoto}/{rama}")
        if r.returncode != 0:
            print(f"No se pudo actualizar desde GitHub: {r.stderr.strip()}")
            return 1
    volcado = json.loads(Path(a.volcado).read_text(encoding="utf-8"))
    ahora_dt = datetime.datetime.now(datetime.timezone.utc)
    hoy = ahora_dt.date().isoformat()
    local, base_por_id, commit, informe, cambio = sincronizar(volcado, a.remoto, hoy)
    llamadas = operaciones(local, base_por_id, commit, volcado, ahora_dt.strftime("%Y-%m-%dT%H:%M:%SZ"), informe)
    salida = Path(a.operaciones)
    salida.parent.mkdir(exist_ok=True)
    salida.write_text(json.dumps(llamadas, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"Tarjetas aplicadas desde el kanban: {', '.join(informe['aplicadas']) or 'ninguna'}")
    if informe["nuevas"]:
        print("Tarjetas nuevas: " + ", ".join(f"{k} pasa a ser {v}" for k, v in informe["nuevas"].items()))
    if informe["ordenes"]:
        print(f"Cambios de orden: {', '.join(informe['ordenes'])}")
    if informe["conflictos"]:
        print(f"Conflictos (ganó el repositorio): {', '.join(informe['conflictos'])}")
    for i, motivo in informe["rechazadas"]:
        print(f"Rechazada {i}: {motivo}")
    print(f"Operaciones para la base: {sum(len(c) for c in llamadas)} en {len(llamadas)} llamadas, en {salida.relative_to(RAIZ) if salida.is_relative_to(RAIZ) else salida}")
    if cambio:
        print("tablero.md cambió: hacé commit y push del tablero y volvé a correr la sincronización.")
        return 2
    print("tablero.md no cambió.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
