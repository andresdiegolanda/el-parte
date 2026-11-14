#!/usr/bin/env python3
"""Prueba de herramientas/sincronizar_tablero.py en una copia del repositorio.

Copia el repositorio a una carpeta temporal, simula que lo local ya está en GitHub
y arma volcados de la base del kanban para cinco casos: sin cambios, cambios del
kanban con una tarjeta nueva, la segunda pasada después del push, un conflicto y
un cambio que rompe las reglas. No toca el repositorio original ni la red.

Uso:  python3 herramientas/pruebas/prueba_sincronizacion.py
"""

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ORIGEN = Path(__file__).resolve().parent.parent.parent
fallas = []


def comprobar(condicion, mensaje):
    print(("ok   " if condicion else "MAL  ") + mensaje)
    if not condicion:
        fallas.append(mensaje)


def correr(raiz, *args):
    return subprocess.run([sys.executable, *args], cwd=raiz, capture_output=True, text=True)


def git(raiz, *args):
    return subprocess.run(["git", *args], cwd=raiz, capture_output=True, text=True, check=True).stdout.strip()


def publicar(raiz):
    """Simula commit y push: lo local pasa a ser lo que hay en GitHub."""
    git(raiz, "add", "-A")
    git(raiz, "-c", "user.name=prueba", "-c", "user.email=prueba@example.org", "commit", "-q", "--allow-empty", "-m", "prueba")
    git(raiz, "update-ref", "refs/remotes/origin/main", "HEAD")


def volcado_desde_ops(ops_llamadas, previo):
    """Aplica las operaciones a un volcado, como lo haría la base."""
    docs = {f"tarjetas/{d['id']}": {"version": d["version"], "data": d["data"]} for d in previo["tarjetas"]}
    if previo.get("meta"):
        docs["meta/tablero"] = dict(previo["meta"])
    for llamada in ops_llamadas:
        for op in llamada:
            actual = docs.get(op["path"])
            if op["op"] == "delete":
                docs.pop(op["path"], None)
            elif op["op"] == "set":
                docs[op["path"]] = {"version": (actual["version"] + 1) if actual else 1, "data": op["data"]}
            elif op["op"] == "update":
                docs[op["path"]] = {"version": actual["version"] + 1, "data": {**actual["data"], **op["data"]}}
    return {
        "tarjetas": [{"id": p.split("/")[1], **v} for p, v in docs.items() if p.startswith("tarjetas/")],
        "meta": docs.get("meta/tablero"),
    }


def sincronizar(raiz, volcado):
    archivo = Path(raiz) / "tmp" / "volcado.json"
    archivo.parent.mkdir(exist_ok=True)
    archivo.write_text(json.dumps(volcado, ensure_ascii=False), encoding="utf-8")
    r = correr(raiz, "herramientas/sincronizar_tablero.py", str(archivo), "--sin-fetch")
    ops = json.loads((Path(raiz) / "tmp" / "kanban-operaciones.json").read_text(encoding="utf-8"))
    return r, ops


def tarjeta(volcado, id_):
    return next(d for d in volcado["tarjetas"] if d["id"] == id_)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        raiz = Path(tmp) / "el-parte"
        shutil.copytree(ORIGEN, raiz, ignore=shutil.ignore_patterns("tmp", "__pycache__"))
        publicar(raiz)
        tablero = raiz / "tablero.md"

        # 1. Base vacía: la primera sincronización carga todo y no cambia el tablero.
        r, ops = sincronizar(raiz, {"tarjetas": [], "meta": None})
        comprobar(r.returncode == 0, "con la base vacía, tablero.md no cambia")
        v = volcado_desde_ops(ops, {"tarjetas": [], "meta": None})
        comprobar(len(v["tarjetas"]) > 20 and v["meta"]["data"]["commitGithub"], "y la base queda con todas las tarjetas y el commit de GitHub")

        # 2. Cambios del kanban: T19 a Hecho con su lista tildada, y una tarjeta nueva.
        t19 = tarjeta(v, "T19")["data"]
        t19["lista"] = [dict(i, hecho=True) for i in t19["lista"]]
        t19.update(columna="hecho", editadaEnKanban=True)
        t19["registro"].append({"fecha": "2026-11-14", "texto": "Pasa a Hecho (kanban)"})
        v["tarjetas"].append({"id": "N-prueba", "version": 1, "data": {
            "titulo": "Tarjeta creada en el kanban", "columna": "para-empezar", "orden": 99, "tipo": "gestion",
            "dueno": "Andrés", "dependeDe": "", "esperaA": "", "fecha": "", "detalle": [], "etapas": [], "lista": [],
            "notas": [], "registro": [{"fecha": "2026-11-14", "texto": "Creada (kanban)"}], "huellaRepo": None,
            "editadaEnKanban": True}})
        r, ops = sincronizar(raiz, v)
        texto = tablero.read_text(encoding="utf-8")
        comprobar(r.returncode == 2, "con cambios del kanban, tablero.md cambia")
        hecho = texto.index("## Hecho")
        comprobar(texto.index("### T19 ·") > hecho, "T19 queda en Hecho en tablero.md")
        nuevas = [l for l in texto.splitlines() if l.startswith("### ") and "Tarjeta creada en el kanban" in l]
        comprobar(len(nuevas) == 1 and nuevas[0].startswith("### T"), f"la tarjeta nueva recibe número: {nuevas[0] if nuevas else 'ninguna'}")
        changelog = (raiz / "CHANGELOG.md").read_text(encoding="utf-8")
        comprobar("Sincronización con el kanban: " in changelog and "T19 pasa a Hecho" in changelog, "el CHANGELOG anota la sincronización")
        verif = correr(raiz, "herramientas/verificar_docs.py")
        comprobar(verif.returncode == 0, "el verificador pasa después de sincronizar")
        borrados = [op for llamada in ops for op in llamada if op["op"] == "delete"]
        comprobar(any(op["path"] == "tarjetas/N-prueba" for op in borrados), "el documento temporal de la tarjeta nueva se borra de la base")
        marcadas = [op["path"] for llamada in ops for op in llamada if op["op"] == "set" and op["data"].get("editadaEnKanban")]
        comprobar("tarjetas/T19" in marcadas, "antes del push, T19 sigue marcada como pendiente de subir")

        # 3. Después del push, la segunda pasada no cambia nada y limpia las marcas.
        publicar(raiz)
        v = volcado_desde_ops(ops, v)
        r, ops = sincronizar(raiz, v)
        comprobar(r.returncode == 0, "después del push, la segunda pasada no cambia tablero.md")
        marcadas = [op["path"] for llamada in ops for op in llamada if op["op"] == "set" and op["data"].get("editadaEnKanban")]
        comprobar(not marcadas, "y ninguna tarjeta queda marcada")
        v = volcado_desde_ops(ops, v)
        r, ops = sincronizar(raiz, v)
        escritas = [op["path"] for llamada in ops for op in llamada]
        comprobar(escritas == ["meta/tablero"], f"sin cambios, solo se reescribe el documento meta ({len(escritas)} operaciones)")
        v = volcado_desde_ops(ops, v)

        # 3b. Si en tablero.md una tarjeta cambia de lugar en su columna, la base recibe solo el orden nuevo.
        texto = tablero.read_text(encoding="utf-8")
        inicio = texto.index("## Hecho\n\n") + len("## Hecho\n\n")
        fin = texto.index("\n### ", inicio) + 1
        primera = texto[inicio:fin]
        texto = texto[:inicio] + texto[fin:].rstrip("\n") + "\n\n" + primera.rstrip("\n") + "\n"
        tablero.write_text(texto, encoding="utf-8")
        correr(raiz, "herramientas/generar_kanban.py")
        verif = correr(raiz, "herramientas/verificar_docs.py")
        comprobar(verif.returncode == 0, "el tablero reordenado sigue en formato canónico")
        publicar(raiz)
        r, ops = sincronizar(raiz, v)
        tarjetas_ops = [op for llamada in ops for op in llamada if op["path"].startswith("tarjetas/")]
        comprobar(r.returncode == 0 and tarjetas_ops and all(op["op"] == "update" and set(op["data"]) == {"orden", "sincronizadaEn"} for op in tarjetas_ops),
                  f"un cambio de orden en tablero.md se escribe como orden solo ({len(tarjetas_ops)} operaciones)")
        v = volcado_desde_ops(ops, v)
        comprobar([d["id"] for d in sorted((d for d in v["tarjetas"] if d["data"]["columna"] == "hecho"), key=lambda d: (d["data"]["orden"], d["id"]))][-1]
                  == primera.split(" ")[1], "y en la base la tarjeta movida queda última en Hecho")

        # 4. Conflicto: T11 cambia en el repositorio y en el kanban. Gana el repositorio.
        texto = tablero.read_text(encoding="utf-8")
        texto = texto.replace("El seguimiento acumulado y las bitácoras", "Cambio del repositorio. El seguimiento acumulado y las bitácoras", 1)
        tablero.write_text(texto, encoding="utf-8")
        publicar(raiz)
        t11 = tarjeta(v, "T11")["data"]
        t11["notas"] = ["Cambio hecho en el kanban."]
        t11["editadaEnKanban"] = True
        r, ops = sincronizar(raiz, v)
        texto = tablero.read_text(encoding="utf-8")
        comprobar("Conflictos (ganó el repositorio): T11" in r.stdout, "el conflicto en T11 se informa")
        comprobar("Cambio del repositorio." in texto and "Cambio hecho en el kanban." not in texto, "gana el repositorio")
        comprobar("no se aplicó porque la tarjeta también cambió en el repositorio" in texto, "y el registro de T11 lo anota")
        publicar(raiz)
        v = volcado_desde_ops(ops, v)

        # 5. Un cambio que rompe una regla se rechaza y queda en el kanban para corregirlo.
        t08 = tarjeta(v, "T08")["data"]
        t08.update(columna="hecho", editadaEnKanban=True)
        r, ops = sincronizar(raiz, v)
        texto = tablero.read_text(encoding="utf-8")
        comprobar("Rechazada T08" in r.stdout, "pasar T08 a Hecho con etapas abiertas se rechaza")
        comprobar(texto.index("### T08 ·") < texto.index("## Hecho"), "y T08 sigue fuera de Hecho en tablero.md")
        avisos = [op for llamada in ops for op in llamada if op["path"] == "tarjetas/T08"]
        comprobar(len(avisos) == 1 and avisos[0]["op"] == "update" and avisos[0]["data"].get("rechazoSincronizacion"),
                  "en la base, T08 conserva el cambio y recibe el motivo del rechazo")

    if fallas:
        print(f"{len(fallas)} comprobaciones fallaron.")
        return 1
    print("Todas las comprobaciones pasaron.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
