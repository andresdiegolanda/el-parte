"""Lectura y validacion de tablero.md.

tablero.md es la unica fuente del tablero de El Parte. Este modulo lo lee y
devuelve sus datos (columnas, tipos, dueños, tarjetas) para que los usen el
generador del kanban y el verificador. No escribe nada.

Formato, en resumen (el detalle esta en el propio tablero.md):

- Antes del primer "##", una lista "- Clave: valor" con los datos generales
  (Actualizado, Nota, Calendario, Repositorio).
- Una tabla "Columna | Qué quiere decir", otra "Tipo | Etapas" y otra
  "Dueño | Quién es" definen columnas, tipos y dueños.
- Cada "##" cuyo nombre es una columna agrupa tarjetas. Cada tarjeta empieza
  con "### Txx · Título", sigue con sus campos "- Clave: valor" y despues
  tiene notas y los bloques "Etapas:", "Lista:" y "Registro:".
"""

import re
import unicodedata
from pathlib import Path

ENCABEZADO_TARJETA = re.compile(r"^### (T\d{2}) · (.+?)\s*$")
ID_TAREA = re.compile(r"\bT\d{2}\b")
RANGO = re.compile(r"\b(T\d{2}) a (T\d{2})\b")
CAMPO = re.compile(r"^- ([^:]+):\s*(.*)$")
CASILLA = re.compile(r"^- \[( |x|X)\] (.+)$")
ENTRADA_REGISTRO = re.compile(r"^- (\d{4}-\d{2}-\d{2}): (.+)$")
FECHA = re.compile(r"^\d{4}-\d{2}-\d{2}$")
ENLACE = re.compile(r"\[([^\]]+)\]\(([^)\s]+)\)")

CAMPOS_TARJETA = {"Tipo", "Dueño", "Depende de", "Espera a", "Fecha", "Detalle"}
BLOQUES = {"Etapas:": "etapas", "Lista:": "lista", "Registro:": "registro"}


def clave(texto):
    """Normaliza un nombre para usarlo como identificador: sin tildes, minusculas, guiones."""
    sin_tildes = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", sin_tildes.lower()).strip("-")


def _celdas(linea):
    return [c.strip() for c in linea.strip().strip("|").split("|")]


def _tablas(lineas):
    """Devuelve {primer encabezado: [filas como dict]} de cada tabla del archivo."""
    tablas = {}
    encabezado = None
    for linea in lineas:
        if not linea.startswith("|"):
            encabezado = None
            continue
        celdas = _celdas(linea)
        if encabezado is None:
            encabezado = celdas
            tablas.setdefault(encabezado[0], [])
            continue
        if all(re.fullmatch(r":?-{3,}:?", c) for c in celdas):
            continue
        tablas[encabezado[0]].append(dict(zip(encabezado, celdas)))
    return tablas


def expandir_dependencias(texto):
    """'T02 a T04, T10' -> ['T02', 'T03', 'T04', 'T10']."""
    ids = []
    for desde, hasta in RANGO.findall(texto):
        for n in range(int(desde[1:]), int(hasta[1:]) + 1):
            ids.append(f"T{n:02d}")
    resto = RANGO.sub("", texto)
    ids.extend(ID_TAREA.findall(resto))
    vistos = []
    for i in ids:
        if i not in vistos:
            vistos.append(i)
    return vistos


def leer(ruta):
    """Lee tablero.md. Devuelve (datos, errores_de_formato)."""
    ruta = Path(ruta)
    lineas = ruta.read_text(encoding="utf-8").splitlines()
    errores = []
    tablas = _tablas(lineas)

    columnas = [{"id": clave(f["Columna"]), "nombre": f["Columna"], "pista": f.get("Qué quiere decir", "")}
                for f in tablas.get("Columna", [])]
    tipos = {}
    for f in tablas.get("Tipo", []):
        etapas = [e.strip() for e in f.get("Etapas", "").split(";") if e.strip() and e.strip() != "(sin etapas)"]
        tipos[clave(f["Tipo"])] = {"nombre": f["Tipo"], "etapas": etapas}
    duenos = [{"nombre": f["Dueño"], "descripcion": f.get("Quién es", "")} for f in tablas.get("Dueño", [])]
    if not columnas:
        errores.append("tablero.md: falta la tabla de columnas (Columna | Qué quiere decir)")
    if not tipos:
        errores.append("tablero.md: falta la tabla de tipos (Tipo | Etapas)")
    if not duenos:
        errores.append("tablero.md: falta la tabla de dueños (Dueño | Quién es)")
    nombres_columna = {c["nombre"]: c["id"] for c in columnas}

    meta = {}
    tarjetas = []
    columna_actual = None
    tarjeta = None
    bloque = None
    en_codigo = False
    parrafo = []
    vio_seccion = False

    def cerrar_parrafo():
        if tarjeta is not None and parrafo:
            tarjeta["notas"].append(" ".join(parrafo))
        parrafo.clear()

    for numero, linea in enumerate(lineas, start=1):
        if linea.lstrip().startswith("```"):
            en_codigo = not en_codigo
            continue
        if en_codigo:
            continue

        if linea.startswith("## "):
            cerrar_parrafo()
            vio_seccion = True
            tarjeta, bloque = None, None
            columna_actual = nombres_columna.get(linea[3:].strip())
            continue

        if not vio_seccion:
            m = CAMPO.match(linea)
            if m:
                meta[m.group(1).strip()] = m.group(2).strip()
            continue

        if linea.startswith("### "):
            cerrar_parrafo()
            bloque = None
            if columna_actual is None:
                tarjeta = None
                continue
            m = ENCABEZADO_TARJETA.match(linea)
            if not m:
                errores.append(f"tablero.md:{numero}: encabezado de tarjeta invalido, se espera '### Txx · Título'")
                tarjeta = None
                continue
            tarjeta = {
                "id": m.group(1), "titulo": m.group(2), "columna": columna_actual, "linea": numero,
                "campos": {}, "notas": [], "etapas": [], "lista": [], "registro": [], "en_campos": True,
            }
            tarjetas.append(tarjeta)
            continue

        if tarjeta is None:
            continue

        if not linea.strip():
            cerrar_parrafo()
            tarjeta["en_campos"] = False
            continue

        if tarjeta["en_campos"]:
            m = CAMPO.match(linea)
            if m and m.group(1).strip() in CAMPOS_TARJETA:
                tarjeta["campos"][m.group(1).strip()] = m.group(2).strip()
            else:
                errores.append(f"tablero.md:{numero}: {tarjeta['id']}: campo desconocido o mal escrito: {linea.strip()}")
            continue

        if linea.strip() in BLOQUES:
            cerrar_parrafo()
            bloque = BLOQUES[linea.strip()]
            continue

        if bloque in ("etapas", "lista"):
            m = CASILLA.match(linea)
            if m:
                tarjeta[bloque].append({"texto": m.group(2).strip(), "hecho": m.group(1) != " "})
                continue
            errores.append(f"tablero.md:{numero}: {tarjeta['id']}: en '{bloque}' se espera '- [ ] texto' o '- [x] texto'")
            continue
        if bloque == "registro":
            m = ENTRADA_REGISTRO.match(linea)
            if m:
                tarjeta["registro"].append({"fecha": m.group(1), "texto": m.group(2).strip()})
                continue
            errores.append(f"tablero.md:{numero}: {tarjeta['id']}: en el registro se espera '- AAAA-MM-DD: texto'")
            continue

        parrafo.append(linea.strip())

    cerrar_parrafo()

    for t in tarjetas:
        t.pop("en_campos", None)
        c = t.pop("campos")
        t["tipo"] = clave(c.get("Tipo", ""))
        t["dueno"] = c.get("Dueño", "")
        t["dependeDe"] = c.get("Depende de", "")
        t["dependencias"] = expandir_dependencias(t["dependeDe"])
        t["esperaA"] = c.get("Espera a", "")
        t["fecha"] = c.get("Fecha", "")
        t["detalle"] = [{"texto": texto, "ruta": destino} for texto, destino in ENLACE.findall(c.get("Detalle", ""))]
        t["_tipo_original"] = c.get("Tipo", "")

    orden = {}
    for t in tarjetas:
        orden[t["columna"]] = orden.get(t["columna"], 0) + 1
        t["orden"] = orden[t["columna"]]

    calendario = [d.strip() for d in meta.get("Calendario", "").split(",") if d.strip()]
    datos = {
        "meta": {
            "actualizado": meta.get("Actualizado", ""),
            "nota": meta.get("Nota", ""),
            "calendario": calendario,
            "repositorio": meta.get("Repositorio", ""),
        },
        "columnas": columnas,
        "tipos": tipos,
        "duenos": duenos,
        "tarjetas": tarjetas,
    }
    return datos, errores


def validar(datos):
    """Reglas de contenido del tablero. Devuelve una lista de errores."""
    errores = []
    meta = datos["meta"]
    if not FECHA.match(meta["actualizado"]):
        errores.append("tablero.md: 'Actualizado' tiene que ser una fecha AAAA-MM-DD")
    for d in meta["calendario"]:
        if not FECHA.match(d):
            errores.append(f"tablero.md: fecha invalida en el calendario: {d}")
    if not meta["repositorio"].startswith("https://"):
        errores.append("tablero.md: falta 'Repositorio' con la URL del repositorio")

    duenos = {d["nombre"] for d in datos["duenos"]}
    por_id = {}
    for t in datos["tarjetas"]:
        donde = f"tablero.md:{t['linea']}: {t['id']}"
        if t["id"] in por_id:
            errores.append(f"{donde}: ID repetido")
        por_id[t["id"]] = t
        if t["tipo"] not in datos["tipos"]:
            errores.append(f"{donde}: tipo desconocido '{t['_tipo_original']}'")
        if t["dueno"] not in duenos:
            errores.append(f"{donde}: dueño invalido '{t['dueno']}'")
        if t["fecha"] and not FECHA.match(t["fecha"]):
            errores.append(f"{donde}: 'Fecha' tiene que ser AAAA-MM-DD")
        if not t["registro"]:
            errores.append(f"{donde}: le falta el registro (al menos la fecha en que se creó)")
        if t["tipo"] in datos["tipos"]:
            esperadas = datos["tipos"][t["tipo"]]["etapas"]
            tiene = [e["texto"] for e in t["etapas"]]
            if tiene != esperadas:
                if esperadas:
                    errores.append(f"{donde}: las etapas tienen que ser, en orden: {'; '.join(esperadas)}")
                else:
                    errores.append(f"{donde}: el tipo '{t['_tipo_original']}' no tiene etapas")

    hechas = {t["id"] for t in datos["tarjetas"] if t["columna"] == "hecho"}
    for t in datos["tarjetas"]:
        donde = f"tablero.md:{t['linea']}: {t['id']}"
        for dep in t["dependencias"]:
            if dep == t["id"]:
                errores.append(f"{donde}: depende de sí misma")
            elif dep not in por_id:
                errores.append(f"{donde}: depende de {dep}, que no existe")
        faltan = [d for d in t["dependencias"] if d not in hechas]
        if t["columna"] in ("para-empezar", "en-curso") and faltan:
            errores.append(f"{donde}: está en '{t['columna']}' pero le falta {', '.join(faltan)}")
        if t["columna"] == "pendiente" and not faltan:
            errores.append(f"{donde}: está en 'pendiente' sin dependencias abiertas; va en 'para-empezar'")
        if t["columna"] == "esperando" and not t["esperaA"]:
            errores.append(f"{donde}: está en 'esperando' sin 'Espera a'")
        if t["columna"] == "hecho":
            abiertas = [e["texto"] for e in t["etapas"] + t["lista"] if not e["hecho"]]
            if abiertas:
                errores.append(f"{donde}: está en 'hecho' con ítems sin tildar: {'; '.join(abiertas)}")
    return errores


def ids(datos):
    return {t["id"] for t in datos["tarjetas"]}
