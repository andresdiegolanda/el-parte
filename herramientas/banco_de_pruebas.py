#!/usr/bin/env python3
"""El banco de pruebas: valida un registro y su revisión, y cuenta una medición.

Lo que se valida está en banco-de-pruebas/formato.md y lo que se cuenta, en
banco-de-pruebas/metricas.md. Si este archivo y esos documentos no dicen lo
mismo, mandan los documentos.

Uso, desde la raíz del repositorio:

    python3 herramientas/banco_de_pruebas.py validar [carpeta]
    python3 herramientas/banco_de_pruebas.py contar [carpeta salida.json [medicion.json]]
    python3 herramientas/banco_de_pruebas.py sortear carpeta salida.json tamaño semilla

La carpeta es la del registro, con su revisión en revision/, y salida.json es
lo que produjeron las corridas en modo banco. Por omisión son los de la
muestra sintética: banco-de-pruebas/muestra/registro/ y
banco-de-pruebas/muestra/salida-de-el-parte.json. "validar" sale con código 1
si hay errores; los avisos no cambian el código. "contar" escribe la medición
en medicion.json, en UTF-8, o si no se le da, en la salida estándar. Conviene
darle el archivo: en Windows PowerShell 5.1, redirigir con ">" lo guarda en
UTF-16.

"sortear" elige al azar, con la semilla, los extras que se juzgan a mano cuando
son demasiados para juzgarlos todos, y los escribe en revision/sorteo.json.
Con la misma salida, la misma revisión y la misma semilla, elige los mismos.

El registro tiene datos personales y es material de otro proyecto (ADR 0007 y
ADR 0008). Nada de lo que este programa escribe copia su contenido: solo
identificadores, fechas, clasificaciones y conteos.

Solo usa la biblioteca estándar de Python.
"""

import datetime
import hashlib
import json
import math
import random
import re
import statistics
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MUESTRA = RAIZ / "banco-de-pruebas" / "muestra"
REGISTRO_DE_MUESTRA = MUESTRA / "registro"
SALIDA_DE_MUESTRA = MUESTRA / "salida-de-el-parte.json"
MEDICION_DE_MUESTRA = MUESTRA / "medicion-esperada.json"

CAMPOS_HALLAZGO = ("id", "noche", "clasificacion", "organismo", "sitio", "tipo_de_fuente",
                   "url", "publicado", "que_dice", "motivo")
CLASIFICACIONES = ("nuevo", "actualizacion", "ya_conocido", "descartado")
HALLAZGOS = ("nuevo", "actualizacion")
TIPOS_DE_FUENTE = ("oficial", "prensa", "otro")
RESULTADOS_DE_BUSQUEDA = ("nada", "fallo", "encontro", None)

# Los identificadores de parte.fuentes y los tipos de hecho de docs/contratos.md.
FUENTES = ("arce", "bid", "banco-mundial", "presidencia", "impo")
TIPOS_DE_HECHO = ("llamado", "adjudicacion", "aclaracion", "cancelacion", "aprobacion", "aviso", "norma")
CLAVE_DE_HECHO = re.compile(r"^(%s):[^:\s]+:(%s)$" % ("|".join(FUENTES), "|".join(TIPOS_DE_HECHO)))

ETIQUETAS = ("publicable", "otra-entidad", "no-nuevo", "sin-decidir")
JUICIOS_DE_CORRESPONDENCIA = ("correcto", "contenido-equivocado")
JUICIOS_DE_EXTRA = ("correcto", "otra-entidad", "no-nuevo", "contenido-equivocado")
# La huella del contenido de un hecho: sha256 de su afirmación en UTF-8, las primeras 16 cifras.
# La escribe la salida de El Parte y la guarda cada juicio de un extra (formato.md).
HUELLA_DE_CONTENIDO = re.compile(r"^sha256:[0-9a-f]{16}$")


def huella_de_contenido(afirmacion):
    return "sha256:" + hashlib.sha256(afirmacion.encode("utf-8")).hexdigest()[:16]
GRADOS = ("PROVEN", "INFERRED", "NOT_MENTIONED")
ESTADOS_DE_CORRIDA = ("completa", "cortada", "fallida")

ENCABEZADO_A = ["fecha", "hallazgos", "estado", "duracion", "costo", "notas"]
ENCABEZADO_B = ["tramo", "noches en el tramo", "noches del tramo con fila propia en a",
                "hallazgos adicionales en el tramo (mas alla de las filas de a)",
                "estado", "duracion", "costo"]

FECHA = re.compile(r"^\d{4}-\d{2}-\d{2}$")
ID = re.compile(r"^(\d{4}-\d{2}-\d{2})-\d+$")
DURACION = re.compile(r"^(\d{2}):(\d{2}):(\d{2})$")
NUMERO = re.compile(r"^\d+(?:[.,]\d+)?$")


class ErrorDeFormato(Exception):
    pass


def fecha(texto):
    """Una fecha AAAA-MM-DD, o None si no lo es."""
    if not isinstance(texto, str) or not FECHA.match(texto):
        return None
    try:
        return datetime.date.fromisoformat(texto)
    except ValueError:
        return None


def dias(desde, hasta):
    """Las fechas de desde a hasta, las dos incluidas."""
    return [desde + datetime.timedelta(days=i) for i in range((hasta - desde).days + 1)]


def huella(carpeta, nombres):
    """sha256 de una lista de archivos: una línea "nombre<TAB>sha256" por archivo, en orden."""
    lineas = []
    for nombre in sorted(nombres):
        lineas.append(f"{nombre}\t{hashlib.sha256((carpeta / nombre).read_bytes()).hexdigest()}\n")
    return "sha256:" + hashlib.sha256("".join(lineas).encode("utf-8")).hexdigest()


def leer_jsonl(ruta, errores):
    filas = []
    for numero, linea in enumerate(ruta.read_text(encoding="utf-8").splitlines(), 1):
        if not linea.strip():
            continue
        try:
            fila = json.loads(linea)
        except json.JSONDecodeError as e:
            errores.append(f"{ruta.name}, línea {numero}: no es JSON ({e.msg}).")
            continue
        if not isinstance(fila, dict):
            errores.append(f"{ruta.name}, línea {numero}: no es un objeto.")
            continue
        filas.append((numero, fila))
    return filas


def texto_o_nulo(valor):
    return valor is None or (isinstance(valor, str) and valor.strip() != "")


# El registro ------------------------------------------------------------------

def leer_hallazgos(ruta, errores):
    entradas = {}
    for numero, fila in leer_jsonl(ruta, errores):
        donde = f"hallazgos.jsonl, línea {numero}"
        faltan = [c for c in CAMPOS_HALLAZGO if c not in fila]
        sobran = [c for c in fila if c not in CAMPOS_HALLAZGO]
        if faltan:
            errores.append(f"{donde}: faltan los campos {', '.join(faltan)}.")
            continue
        if sobran:
            errores.append(f"{donde}: campos que el formato no tiene: {', '.join(sobran)}.")
        m = ID.match(fila["id"]) if isinstance(fila["id"], str) else None
        if not m:
            errores.append(f"{donde}: el id {fila['id']!r} no es AAAA-MM-DD-N.")
            continue
        if fila["id"] in entradas:
            errores.append(f"{donde}: el id {fila['id']} está repetido.")
            continue
        if fila["clasificacion"] not in CLASIFICACIONES:
            errores.append(f"{donde}: clasificacion {fila['clasificacion']!r} no es una de {', '.join(CLASIFICACIONES)}.")
        if fila["tipo_de_fuente"] not in TIPOS_DE_FUENTE:
            errores.append(f"{donde}: tipo_de_fuente {fila['tipo_de_fuente']!r} no es una de {', '.join(TIPOS_DE_FUENTE)}.")
        for campo in ("organismo", "que_dice"):
            if not isinstance(fila[campo], str) or not fila[campo].strip():
                errores.append(f"{donde}: {campo} tiene que ser un texto.")
        for campo in ("sitio", "url", "motivo"):
            if not texto_o_nulo(fila[campo]):
                errores.append(f"{donde}: {campo} tiene que ser un texto o null.")
        if isinstance(fila["url"], str) and not re.match(r"^https?://", fila["url"]):
            errores.append(f"{donde}: la url no empieza con http:// ni https://.")
        for campo in ("noche", "publicado"):
            if fila[campo] is not None and fecha(fila[campo]) is None:
                errores.append(f"{donde}: {campo} {fila[campo]!r} no es una fecha AAAA-MM-DD ni null.")
        if fila["noche"] is not None and fila["noche"] != m.group(1):
            errores.append(f"{donde}: el id {fila['id']} no empieza con su noche, {fila['noche']}.")
        if fila["clasificacion"] in HALLAZGOS and fila["noche"] is None:
            errores.append(f"{donde}: un hallazgo ({fila['clasificacion']}) tiene que tener noche.")
        if fila["clasificacion"] == "descartado" and fila["motivo"] is None:
            errores.append(f"{donde}: un descarte tiene que tener motivo.")
        entradas[fila["id"]] = fila
    return entradas


def filas_de_tabla(lineas, encabezado, seccion, errores):
    """Las filas de la primera tabla de una sección, si su encabezado es el del formato."""
    for i, linea in enumerate(lineas):
        if i + 1 < len(lineas) and linea.startswith("|") and lineas[i + 1].startswith("|---"):
            columnas = [c.strip().lower() for c in linea.strip().strip("|").split("|")]
            if columnas != encabezado:
                errores.append(f"indice.md, sección {seccion}: el encabezado de la tabla no es el del formato.")
                return []
            filas = []
            for fila in lineas[i + 2:]:
                if not fila.startswith("|"):
                    break
                celdas = [c.strip() for c in fila.strip().strip("|").split("|")]
                if len(celdas) != len(encabezado):
                    errores.append(f"indice.md, sección {seccion}: una fila tiene {len(celdas)} columnas y no {len(encabezado)}.")
                    continue
                filas.append(celdas)
            return filas
    errores.append(f"indice.md, sección {seccion}: no tiene tabla.")
    return []


def duracion(texto, donde, errores):
    if texto == "null":
        return None
    m = DURACION.match(texto)
    if not m:
        errores.append(f"{donde}: la duración {texto!r} no es hh:mm:ss ni null.")
        return None
    h, mi, s = (int(x) for x in m.groups())
    return h * 3600 + mi * 60 + s


def costo(texto, donde, errores):
    if texto == "null":
        return None
    if not NUMERO.match(texto):
        errores.append(f"{donde}: el costo {texto!r} no es un número ni null.")
        return None
    return float(texto.replace(",", "."))


def entero(texto, donde, errores):
    if not texto.isdigit():
        errores.append(f"{donde}: {texto!r} no es un número entero.")
        return None
    return int(texto)


def leer_indice(ruta, errores):
    secciones = {}
    actual = None
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        if linea.startswith("## "):
            actual = linea[3:].strip()
            secciones[actual] = []
        elif actual is not None:
            secciones[actual].append(linea)

    def seccion(prefijo):
        for nombre, lineas in secciones.items():
            if nombre.lower().startswith(prefijo):
                return lineas
        errores.append(f"indice.md: falta la sección que empieza con \"## {prefijo.capitalize()}\".")
        return None

    indice = {"noches": {}, "tramos": [], "primera": None, "ultima": None, "total": None}

    a = seccion("a.")
    for celdas in filas_de_tabla(a or [], ENCABEZADO_A, "A", errores) if a is not None else []:
        donde = f"indice.md, fila {celdas[0]} de la sección A"
        dia = fecha(celdas[0])
        if dia is None:
            errores.append(f"{donde}: la fecha no es AAAA-MM-DD.")
            continue
        if dia in indice["noches"]:
            errores.append(f"{donde}: la fecha está repetida.")
            continue
        indice["noches"][dia] = {
            "hallazgos": entero(celdas[1], donde, errores),
            "estado": celdas[2],
            "duracion": duracion(celdas[3], donde, errores),
            "costo": costo(celdas[4], donde, errores),
        }

    b = seccion("b.")
    for celdas in filas_de_tabla(b or [], ENCABEZADO_B, "B", errores) if b is not None else []:
        donde = f"indice.md, tramo {celdas[0]} de la sección B"
        partes = celdas[0].split(" a ")
        desde, hasta = (fecha(partes[0]), fecha(partes[1])) if len(partes) == 2 else (None, None)
        if desde is None or hasta is None or hasta < desde:
            errores.append(f"{donde}: el tramo no es \"AAAA-MM-DD a AAAA-MM-DD\".")
            continue
        conA = []
        if celdas[2] != "(ninguna)":
            for parte in celdas[2].split(","):
                dia = fecha(parte.strip())
                if dia is None:
                    errores.append(f"{donde}: {parte.strip()!r} no es una fecha.")
                else:
                    conA.append(dia)
        indice["tramos"].append({
            "desde": desde, "hasta": hasta,
            "noches": entero(celdas[1], donde, errores),
            "conFilaEnA": conA,
            "adicionales": entero(celdas[3], donde, errores),
            "duracion": duracion(celdas[5], donde, errores),
            "costo": costo(celdas[6], donde, errores),
        })

    totales = seccion("totales")
    for linea in totales or []:
        m = re.match(r"^- (?:Primera) noche: (\S+)", linea)
        if m:
            indice["primera"] = fecha(m.group(1))
        m = re.match(r"^- (?:Ultima|Última) noche: (\S+)", linea)
        if m:
            indice["ultima"] = fecha(m.group(1))
        m = re.match(r"^- Total de noches en el periodo: (\d+)", linea)
        if m:
            indice["total"] = int(m.group(1))
    if totales is not None and (indice["primera"] is None or indice["ultima"] is None):
        errores.append("indice.md: la sección de totales tiene que decir la primera y la última noche.")
    return indice


def leer_busquedas(ruta, errores):
    busquedas = []
    for numero, fila in leer_jsonl(ruta, errores):
        donde = f"busquedas.jsonl, línea {numero}"
        if set(fila) != {"noche", "consulta_o_url", "resultado"}:
            errores.append(f"{donde}: los campos tienen que ser noche, consulta_o_url y resultado.")
            continue
        partes = fila["noche"].split("/") if isinstance(fila["noche"], str) else []
        if not partes or len(partes) > 2 or any(fecha(p) is None for p in partes):
            errores.append(f"{donde}: noche {fila['noche']!r} no es AAAA-MM-DD ni AAAA-MM-DD/AAAA-MM-DD.")
        if not isinstance(fila["consulta_o_url"], str) or not fila["consulta_o_url"].strip():
            errores.append(f"{donde}: consulta_o_url tiene que ser un texto.")
        if fila["resultado"] not in RESULTADOS_DE_BUSQUEDA:
            errores.append(f"{donde}: resultado {fila['resultado']!r} no es nada, fallo, encontro ni null.")
        busquedas.append(fila)
    return busquedas


def cotejar_registro(entradas, indice, avisos):
    """Lo que el registro dice de sí mismo y no coincide: son avisos, no errores."""
    primera, ultima = indice["primera"], indice["ultima"]
    if primera is None or ultima is None:
        return
    periodo = dias(primera, ultima)
    if indice["total"] is not None and indice["total"] != len(periodo):
        avisos.append(f"indice.md: dice {indice['total']} noches y del {primera} al {ultima} hay {len(periodo)}.")
    cubiertas = set(indice["noches"])
    for tramo in indice["tramos"]:
        noches = dias(tramo["desde"], tramo["hasta"])
        cubiertas |= set(noches)
        if tramo["noches"] is not None and tramo["noches"] != len(noches):
            avisos.append(f"indice.md: el tramo {tramo['desde']} a {tramo['hasta']} dice {tramo['noches']} noches y tiene {len(noches)}.")
        for dia in tramo["conFilaEnA"]:
            if dia not in indice["noches"] or not tramo["desde"] <= dia <= tramo["hasta"]:
                avisos.append(f"indice.md: el tramo {tramo['desde']} a {tramo['hasta']} nombra {dia}, que no es una fila de A dentro del tramo.")
        enA = set(tramo["conFilaEnA"])
        reales = [e for e in entradas.values() if e["clasificacion"] in HALLAZGOS
                  and tramo["desde"] <= fecha(e["noche"]) <= tramo["hasta"] and fecha(e["noche"]) not in enA]
        if tramo["adicionales"] is not None and tramo["adicionales"] != len(reales):
            avisos.append(f"indice.md: el tramo {tramo['desde']} a {tramo['hasta']} dice {tramo['adicionales']} hallazgos adicionales y hallazgos.jsonl tiene {len(reales)}.")
    faltan = [d for d in periodo if d not in cubiertas]
    if faltan:
        avisos.append(f"indice.md: {len(faltan)} noches del período no están en A ni en un tramo, desde {faltan[0]}.")
    fuera = sorted(d for d in cubiertas if not primera <= d <= ultima)
    if fuera:
        avisos.append(f"indice.md: {len(fuera)} noches de las tablas caen fuera del período, desde {fuera[0]}.")
    for dia, noche in sorted(indice["noches"].items()):
        reales = sum(1 for e in entradas.values() if e["clasificacion"] in HALLAZGOS and e["noche"] == dia.isoformat())
        if noche["hallazgos"] is not None and noche["hallazgos"] != reales:
            avisos.append(f"indice.md: la noche {dia} dice {noche['hallazgos']} hallazgos y hallazgos.jsonl tiene {reales}.")
    for e in entradas.values():
        if e["noche"] and not primera <= fecha(e["noche"]) <= ultima:
            avisos.append(f"hallazgos.jsonl: {e['id']} tiene una noche fuera del período.")


def leer_registro(carpeta):
    """(registro, errores, avisos). Si faltan los archivos obligatorios, ErrorDeFormato."""
    errores, avisos = [], []
    for nombre in ("hallazgos.jsonl", "indice.md"):
        if not (carpeta / nombre).is_file():
            raise ErrorDeFormato(f"{carpeta}: falta {nombre}.")
    entradas = leer_hallazgos(carpeta / "hallazgos.jsonl", errores)
    indice = leer_indice(carpeta / "indice.md", errores)
    busquedas = leer_busquedas(carpeta / "busquedas.jsonl", errores) if (carpeta / "busquedas.jsonl").is_file() else []
    if not errores:
        cotejar_registro(entradas, indice, avisos)
    archivos = [p.name for p in carpeta.iterdir() if p.is_file()]
    registro = {"entradas": entradas, "indice": indice, "busquedas": busquedas,
                "huella": huella(carpeta, archivos), "archivos": sorted(archivos)}
    return registro, errores, avisos


# La revisión ------------------------------------------------------------------

def leer_revision(carpeta, registro):
    """(revision, errores, avisos). Sin carpeta revision/, una revisión vacía y un aviso."""
    errores, avisos = [], []
    revision = {"entradas": {}, "correspondencias": [], "extras": {}, "sorteo": None, "huella": None}
    if not carpeta.is_dir():
        avisos.append("No hay revisión (revision/): todas las entradas quedan sin revisar.")
        return revision, errores, avisos
    entradas = registro["entradas"]

    ruta = carpeta / "entradas.jsonl"
    for numero, fila in leer_jsonl(ruta, errores) if ruta.is_file() else []:
        donde = f"revision/entradas.jsonl, línea {numero}"
        id_ = fila.get("id")
        if id_ not in entradas:
            errores.append(f"{donde}: {id_!r} no es un id del registro.")
            continue
        if id_ in revision["entradas"]:
            errores.append(f"{donde}: {id_} ya está revisada.")
            continue
        sobran = set(fila) - {"id", "fuente", "etiqueta", "nota"}
        if sobran or "fuente" not in fila:
            errores.append(f"{donde}: los campos son id, fuente, etiqueta (solo en descartes y ya conocidos) y nota, que es opcional.")
            continue
        if fila["fuente"] is not None and fila["fuente"] not in FUENTES:
            errores.append(f"{donde}: fuente {fila['fuente']!r} no es una de {', '.join(FUENTES)} ni null.")
        es_hallazgo = entradas[id_]["clasificacion"] in HALLAZGOS
        if es_hallazgo and "etiqueta" in fila:
            errores.append(f"{donde}: {id_} es un hallazgo y no lleva etiqueta.")
        if not es_hallazgo and fila.get("etiqueta") not in ETIQUETAS:
            errores.append(f"{donde}: {id_} lleva una etiqueta: {', '.join(ETIQUETAS)}.")
        revision["entradas"][id_] = fila
    sin_revisar = len(set(entradas) - set(revision["entradas"]))
    if sin_revisar:
        avisos.append(f"revision/entradas.jsonl: {sin_revisar} entradas del registro sin revisar.")

    pares = set()
    ruta = carpeta / "correspondencias.jsonl"
    for numero, fila in leer_jsonl(ruta, errores) if ruta.is_file() else []:
        donde = f"revision/correspondencias.jsonl, línea {numero}"
        if not set(fila) <= {"registro", "hecho", "juicio"} or "registro" not in fila or "hecho" not in fila:
            errores.append(f"{donde}: los campos son registro, hecho y juicio, que es opcional.")
            continue
        if fila["registro"] not in entradas:
            errores.append(f"{donde}: {fila['registro']!r} no es un id del registro.")
            continue
        if not isinstance(fila["hecho"], str) or not CLAVE_DE_HECHO.match(fila["hecho"]):
            errores.append(f"{donde}: {fila['hecho']!r} no es una clave de hecho fuente:identificador:tipo.")
            continue
        if fila.get("juicio", "correcto") not in JUICIOS_DE_CORRESPONDENCIA:
            errores.append(f"{donde}: juicio {fila['juicio']!r} no es correcto ni contenido-equivocado.")
        par = (fila["registro"], fila["hecho"])
        if par in pares:
            errores.append(f"{donde}: la correspondencia está repetida.")
            continue
        pares.add(par)
        revisada = revision["entradas"].get(fila["registro"])
        if revisada is not None and revisada["fuente"] != fila["hecho"].split(":")[0]:
            avisos.append(f"{donde}: {fila['registro']} es de la fuente {revisada['fuente']} y el hecho, de {fila['hecho'].split(':')[0]}.")
        revision["correspondencias"].append(fila)
    con_registro = {c["hecho"] for c in revision["correspondencias"]}

    ruta = carpeta / "extras.jsonl"
    for numero, fila in leer_jsonl(ruta, errores) if ruta.is_file() else []:
        donde = f"revision/extras.jsonl, línea {numero}"
        if not {"hecho", "juicio"} <= set(fila) <= {"hecho", "juicio", "contenido"}:
            errores.append(f"{donde}: los campos son hecho, juicio y contenido, que es opcional.")
            continue
        if "contenido" in fila and (not isinstance(fila["contenido"], str) or not HUELLA_DE_CONTENIDO.match(fila["contenido"])):
            errores.append(f"{donde}: contenido {fila['contenido']!r} no es una huella sha256: de 16 cifras hexadecimales.")
            continue
        if not isinstance(fila["hecho"], str) or not CLAVE_DE_HECHO.match(fila["hecho"]):
            errores.append(f"{donde}: {fila['hecho']!r} no es una clave de hecho fuente:identificador:tipo.")
            continue
        if fila["juicio"] not in JUICIOS_DE_EXTRA:
            errores.append(f"{donde}: juicio {fila['juicio']!r} no es uno de {', '.join(JUICIOS_DE_EXTRA)}.")
        if fila["hecho"] in revision["extras"]:
            errores.append(f"{donde}: {fila['hecho']} ya está juzgado.")
            continue
        if fila["hecho"] in con_registro:
            avisos.append(f"{donde}: {fila['hecho']} tiene una correspondencia con el registro y no es un extra.")
        revision["extras"][fila["hecho"]] = fila

    ruta = carpeta / "sorteo.json"
    if ruta.is_file():
        try:
            sorteo = json.loads(ruta.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            errores.append(f"revision/sorteo.json: no es JSON ({e.msg}).")
            sorteo = None
        if sorteo is not None:
            claves = sorteo.get("claves") if isinstance(sorteo, dict) else None
            if (not isinstance(sorteo, dict) or set(sorteo) != {"salida", "semilla", "tamano", "poblacion", "claves"}
                    or not isinstance(claves, list) or len(claves) != sorteo["tamano"] or len(set(claves)) != len(claves)
                    or any(not isinstance(k, str) or not CLAVE_DE_HECHO.match(k) for k in claves)
                    or not isinstance(sorteo["poblacion"], int) or sorteo["poblacion"] < sorteo["tamano"]):
                errores.append("revision/sorteo.json: los campos son salida, semilla, tamano, poblacion y claves, "
                               "con tantas claves de hecho distintas como dice tamano.")
            else:
                revision["sorteo"] = sorteo
                fuera = [k for k in revision["extras"] if k not in set(claves)]
                if fuera:
                    avisos.append(f"revision/extras.jsonl: {len(fuera)} extras juzgados fuera del sorteo; "
                                  "cuentan en la precisión pero no en la estimación del sorteo.")

    archivos = [p.name for p in carpeta.iterdir() if p.is_file()]
    revision["huella"] = huella(carpeta, archivos)
    return revision, errores, avisos


# La salida de El Parte ----------------------------------------------------------

def leer_salida(ruta):
    """La salida de las corridas en modo banco. ErrorDeFormato si no tiene la forma."""
    try:
        salida = json.loads(ruta.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise ErrorDeFormato(f"{ruta.name}: no es JSON ({e.msg}).")
    problemas = []
    if not isinstance(salida.get("fuentes"), list) or any(f not in FUENTES for f in salida["fuentes"]):
        problemas.append(f"fuentes tiene que ser una lista de {', '.join(FUENTES)}.")
    if not isinstance(salida.get("entidad"), str):
        problemas.append("falta la entidad medida.")
    noches = set()
    for corrida in salida.get("corridas", []):
        if fecha(corrida.get("noche")) is None or corrida["noche"] in noches:
            problemas.append(f"corrida con noche {corrida.get('noche')!r}: no es una fecha o está repetida.")
            continue
        noches.add(corrida["noche"])
        if corrida.get("estado") not in ESTADOS_DE_CORRIDA:
            problemas.append(f"corrida {corrida['noche']}: estado {corrida.get('estado')!r} no es uno de {', '.join(ESTADOS_DE_CORRIDA)}.")
        if any(f not in FUENTES for f in corrida.get("noRepetibles", [])):
            problemas.append(f"corrida {corrida['noche']}: noRepetibles tiene fuentes que no existen.")
    if not noches:
        problemas.append("no tiene corridas.")
    claves = set()
    for hecho in salida.get("hechos", []):
        clave = hecho.get("clave")
        if not isinstance(clave, str) or not CLAVE_DE_HECHO.match(clave) or clave in claves:
            problemas.append(f"hecho {clave!r}: no es una clave de hecho o está repetido.")
            continue
        claves.add(clave)
        if hecho.get("noche") not in noches:
            problemas.append(f"hecho {clave}: su noche no es la de una corrida.")
        if hecho.get("grado") not in GRADOS:
            problemas.append(f"hecho {clave}: grado {hecho.get('grado')!r} no es uno de {', '.join(GRADOS)}.")
        if "contenido" in hecho and (not isinstance(hecho["contenido"], str) or not HUELLA_DE_CONTENIDO.match(hecho["contenido"])):
            problemas.append(f"hecho {clave}: contenido {hecho['contenido']!r} no es una huella sha256: de 16 cifras hexadecimales.")
    if problemas:
        raise ErrorDeFormato(f"{ruta.name}: " + " ".join(problemas))
    salida["huella"] = "sha256:" + hashlib.sha256(ruta.read_bytes()).hexdigest()
    return salida


# La medición ------------------------------------------------------------------

def proporcion(a, b):
    return round(a / b, 4) if b else None


def wilson(aciertos, total, z=1.96):
    """El intervalo de Wilson al 95 % de una proporción, o None si no hay nada juzgado."""
    if not total:
        return None
    p = aciertos / total
    centro = (p + z * z / (2 * total)) / (1 + z * z / total)
    margen = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / (1 + z * z / total)
    return [round(max(0.0, centro - margen), 4), round(min(1.0, centro + margen), 4)]


def poblacion_de_extras(revision, salida):
    """Las claves de los hechos PROVEN o INFERRED de la salida que no corresponden a nada del registro."""
    con_registro = {c["hecho"] for c in revision["correspondencias"]}
    return sorted(h["clave"] for h in salida["hechos"]
                  if h["grado"] in ("PROVEN", "INFERRED") and h["clave"] not in con_registro)


def sortear(carpeta, ruta_salida, tamano, semilla):
    """El sorteo de extras para juzgar a mano. ErrorDeFormato si el registro, la revisión o la salida tienen errores."""
    errores, _ = validar(carpeta)
    if errores:
        raise ErrorDeFormato(" ".join(errores))
    registro, _, _ = leer_registro(carpeta)
    revision, _, _ = leer_revision(carpeta / "revision", registro)
    salida = leer_salida(ruta_salida)
    poblacion = poblacion_de_extras(revision, salida)
    if not 0 < tamano <= len(poblacion):
        raise ErrorDeFormato(f"el tamaño tiene que estar entre 1 y {len(poblacion)}, los extras de la salida.")
    claves = sorted(random.Random(semilla).sample(poblacion, tamano))
    return {"salida": salida["huella"], "semilla": semilla, "tamano": tamano, "poblacion": len(poblacion), "claves": claves}


def contar(registro, revision, salida):
    """La medición, con la forma de banco-de-pruebas/metricas.md."""
    entradas = registro["entradas"]
    corridas = {c["noche"]: c for c in salida["corridas"]}
    hechos = {h["clave"]: h for h in salida["hechos"]}
    a_la_vista = ("PROVEN", "INFERRED")

    # Un juicio con la huella de otro contenido es viejo: el hecho cambió después de juzgarlo, y
    # cuenta como sin juzgar. Sin huella de un lado o del otro, el juicio vale (metricas.md).
    viejos = set()

    def extra_vigente(clave):
        extra = revision["extras"].get(clave)
        if extra is None:
            return None
        actual = hechos.get(clave, {}).get("contenido")
        if extra.get("contenido") and actual and extra["contenido"] != actual:
            viejos.add(clave)
            return None
        return extra
    por_registro, por_hecho = {}, {}
    for c in revision["correspondencias"]:
        por_registro.setdefault(c["registro"], []).append(c)
        por_hecho.setdefault(c["hecho"], []).append(c)

    def resultado_de_entrada(e):
        r = revision["entradas"].get(e["id"])
        if r is None:
            return "sin-revisar", [], None
        if r["fuente"] is None:
            return "fuera-de-el-parte", [], None
        if r["fuente"] not in salida["fuentes"]:
            return "fuera-de-esta-medicion", [], None
        vistos = [hechos[c["hecho"]] for c in por_registro.get(e["id"], []) if c["hecho"] in hechos]
        surgidos = [h for h in vistos if h["grado"] in a_la_vista]
        if surgidos:
            primero = min(fecha(h["noche"]) for h in surgidos)
            adelanto = (fecha(e["noche"]) - primero).days if e["noche"] else None
            tipo = "encontrado-publicado" if any(h["grado"] == "PROVEN" for h in surgidos) else "encontrado-a-revision"
            return tipo, sorted(h["clave"] for h in surgidos), adelanto
        if vistos:
            return "visto-y-descartado", sorted(h["clave"] for h in vistos), None
        dia = e["publicado"] or e["noche"]
        if dia is None or dia not in corridas:
            return "fuera-de-las-noches-medidas", [], None
        if r["fuente"] in corridas[dia].get("noRepetibles", []):
            return "no-repetible", [], None
        return "no-encontrado", [], None

    def bloque(ids):
        detalle = []
        cuenta = {k: 0 for k in ("encontrado-publicado", "encontrado-a-revision", "visto-y-descartado",
                                 "no-encontrado", "no-repetible", "fuera-de-las-noches-medidas",
                                 "fuera-de-esta-medicion", "fuera-de-el-parte", "sin-revisar")}
        adelantos = []
        for id_ in sorted(ids):
            e = entradas[id_]
            res, claves, adelanto = resultado_de_entrada(e)
            cuenta[res] += 1
            fila = {"id": id_, "clasificacion": e["clasificacion"], "resultado": res}
            if claves:
                fila["hechos"] = claves
            if adelanto is not None:
                fila["diasDeAdelanto"] = adelanto
                adelantos.append(adelanto)
            detalle.append(fila)
        encontrados = cuenta["encontrado-publicado"] + cuenta["encontrado-a-revision"]
        resumen = {
            "total": len(ids),
            "encontrados": {"publicados": cuenta["encontrado-publicado"], "aRevision": cuenta["encontrado-a-revision"]},
            "vistosYDescartados": cuenta["visto-y-descartado"],
            "noEncontrados": cuenta["no-encontrado"],
            "noRepetibles": cuenta["no-repetible"],
            "fueraDeLasNochesMedidas": cuenta["fuera-de-las-noches-medidas"],
            "fueraDeEstaMedicion": cuenta["fuera-de-esta-medicion"],
            "fueraDeElParte": cuenta["fuera-de-el-parte"],
            "sinRevisar": cuenta["sin-revisar"],
            "cobertura": proporcion(encontrados, encontrados + cuenta["visto-y-descartado"] + cuenta["no-encontrado"]),
            "diasDeAdelanto": statistics.median(adelantos) if adelantos else None,
        }
        return resumen, detalle

    hallazgos = [i for i, e in entradas.items() if e["clasificacion"] in HALLAZGOS]
    publicables = [i for i, e in entradas.items() if e["clasificacion"] not in HALLAZGOS
                   and revision["entradas"].get(i, {}).get("etiqueta") == "publicable"]
    resumen_hallazgos, detalle_hallazgos = bloque(hallazgos)
    resumen_publicables, detalle_publicables = bloque(publicables)

    def juicio_de_hecho(clave):
        correspondencias = por_hecho.get(clave, [])
        if correspondencias:
            c = correspondencias[0]
            e = entradas[c["registro"]]
            if c.get("juicio") == "contenido-equivocado":
                return "falso-positivo-contenido", c["registro"]
            if e["clasificacion"] in HALLAZGOS:
                return "coincide-con-hallazgo", c["registro"]
            etiqueta = revision["entradas"].get(c["registro"], {}).get("etiqueta")
            return {"publicable": "coincide-con-publicable", "otra-entidad": "falso-positivo-otra-entidad",
                    "no-nuevo": "falso-positivo-no-nuevo"}.get(etiqueta, "sin-juzgar"), c["registro"]
        extra = extra_vigente(clave)
        if extra is None:
            return "sin-juzgar", None
        return {"correcto": "extra-correcto", "otra-entidad": "falso-positivo-otra-entidad",
                "no-nuevo": "falso-positivo-no-nuevo", "contenido-equivocado": "falso-positivo-contenido"}[extra["juicio"]], None

    verdaderos = ("coincide-con-hallazgo", "coincide-con-publicable", "extra-correcto")
    falsos = {"falso-positivo-otra-entidad": "otraEntidad", "falso-positivo-no-nuevo": "noNuevo",
              "falso-positivo-contenido": "contenidoEquivocado"}

    def precision(lista):
        v = sum(1 for x in lista if x in verdaderos)
        f = {nombre: sum(1 for x in lista if x == clave) for clave, nombre in falsos.items()}
        total_f = sum(f.values())
        return {"total": len(lista), "verdaderos": v, "falsos": f, "sinJuzgar": sum(1 for x in lista if x == "sin-juzgar"),
                "precision": proporcion(v, v + total_f)}

    detalle_hechos, por_grado = [], {g: [] for g in a_la_vista}
    for clave in sorted(hechos):
        h = hechos[clave]
        if h["grado"] not in a_la_vista:
            continue
        res, con = juicio_de_hecho(clave)
        por_grado[h["grado"]].append(res)
        fila = {"clave": clave, "grado": h["grado"], "noche": h["noche"], "resultado": res}
        if con:
            fila["coincideCon"] = con
        if clave in viejos:
            fila["juicioViejo"] = True
        detalle_hechos.append(fila)

    detalle_noches = []
    for noche in sorted(corridas):
        c = corridas[noche]
        fila = {"noche": noche, "estado": c["estado"], "costoUsd": c.get("costoUsd"),
                "duracionSegundos": c.get("duracionSegundos"), "lecturasFallidas": c.get("lecturasFallidas", 0),
                "noRepetibles": c.get("noRepetibles", [])}
        de_la_vigilancia = registro["indice"]["noches"].get(fecha(noche))
        if de_la_vigilancia and de_la_vigilancia["duracion"] is not None:
            fila["duracionDeLaVigilancia"] = de_la_vigilancia["duracion"]
        detalle_noches.append(fila)
    sorteo_de_extras = None
    sorteo = revision.get("sorteo")
    if sorteo is not None:
        if sorteo["salida"] != salida.get("huella"):
            raise ErrorDeFormato("revision/sorteo.json es de otra salida: hay que sortear de nuevo.")
        juicios = [extra_vigente(k)["juicio"] for k in sorteo["claves"] if extra_vigente(k) is not None]
        juzgados, correctos = len(juicios), juicios.count("correcto")
        de_registro = [f["resultado"] for f in detalle_hechos if "coincideCon" in f]
        v_registro = sum(1 for r in de_registro if r in verdaderos)
        j_registro = v_registro + sum(1 for r in de_registro if r in falsos)
        n = sorteo["poblacion"]

        def de_todo(p):
            return round((v_registro + p * n) / (j_registro + n), 4) if p is not None and j_registro + n else None

        intervalo = wilson(correctos, juzgados)
        sorteo_de_extras = {
            "poblacion": n, "tamano": sorteo["tamano"], "semilla": sorteo["semilla"],
            "juzgados": juzgados, "correctos": correctos,
            "falsos": {nombre: juicios.count(juicio) for juicio, nombre in
                       (("otra-entidad", "otraEntidad"), ("no-nuevo", "noNuevo"), ("contenido-equivocado", "contenidoEquivocado"))},
            "precision": proporcion(correctos, juzgados),
            "intervalo95": intervalo,
            "precisionDeTodo": de_todo(correctos / juzgados if juzgados else None),
            "intervaloDeTodo95": [de_todo(intervalo[0]), de_todo(intervalo[1])] if intervalo else None,
        }

    costos = [c["costoUsd"] for c in corridas.values() if c.get("costoUsd") is not None]
    duraciones = [c["duracionSegundos"] for c in corridas.values() if c.get("duracionSegundos") is not None]
    indice = registro["indice"]

    return {
        "registro": {
            "huella": registro["huella"],
            "primeraNoche": indice["primera"].isoformat() if indice["primera"] else None,
            "ultimaNoche": indice["ultima"].isoformat() if indice["ultima"] else None,
            "entradas": len(entradas),
        },
        "revision": {"huella": revision["huella"], "sinRevisar": len(set(entradas) - set(revision["entradas"]))},
        "elParte": {k: salida[k] for k in ("commit", "entidad", "fuentes", "modelos") if k in salida},
        "noches": {
            "medidas": len(corridas),
            "completas": sum(1 for c in corridas.values() if c["estado"] == "completa"),
            "cortadas": sum(1 for c in corridas.values() if c["estado"] == "cortada"),
            "fallidas": sum(1 for c in corridas.values() if c["estado"] == "fallida"),
            "conFuentesNoRepetibles": sum(1 for c in corridas.values() if c.get("noRepetibles")),
            "costoUsd": round(sum(costos), 4) if costos else None,
            "costoUsdPorNoche": round(sum(costos) / len(costos), 4) if costos else None,
            "duracionSegundosPorNoche": round(sum(duraciones) / len(duraciones), 1) if duraciones else None,
        },
        "hallazgos": resumen_hallazgos,
        "publicables": resumen_publicables,
        "hechos": {
            "PROVEN": precision(por_grado["PROVEN"]),
            "INFERRED": precision(por_grado["INFERRED"]),
            "todos": precision(por_grado["PROVEN"] + por_grado["INFERRED"]),
            "descartadosPorElParte": sum(1 for h in hechos.values() if h["grado"] == "NOT_MENTIONED"),
            "juiciosViejos": len(viejos),
            **({"sorteoDeExtras": sorteo_de_extras} if sorteo_de_extras is not None else {}),
        },
        "detalle": {
            "hallazgos": detalle_hallazgos,
            "publicables": detalle_publicables,
            "hechos": detalle_hechos,
            "noches": detalle_noches,
        },
    }


# La línea de comandos --------------------------------------------------------------

def validar(carpeta):
    """(errores, avisos) del registro y de su revisión."""
    try:
        registro, errores, avisos = leer_registro(carpeta)
    except ErrorDeFormato as e:
        return [str(e)], []
    if not errores:
        _, e2, a2 = leer_revision(carpeta / "revision", registro)
        errores += e2
        avisos += a2
    return errores, avisos


def medir(carpeta, ruta_salida):
    """La medición de un registro con su revisión y una salida. ErrorDeFormato si alguno tiene errores."""
    errores, _ = validar(carpeta)
    if errores:
        raise ErrorDeFormato(" ".join(errores))
    registro, _, _ = leer_registro(carpeta)
    revision, _, _ = leer_revision(carpeta / "revision", registro)
    return contar(registro, revision, leer_salida(ruta_salida))


def verificar_muestra():
    """Los problemas de la muestra sintética: tiene que cumplir el formato sin avisos y dar la medición esperada."""
    errores, avisos = validar(REGISTRO_DE_MUESTRA)
    problemas = [f"muestra del banco de pruebas: {p}" for p in errores + avisos]
    if problemas:
        return problemas
    try:
        medicion = medir(REGISTRO_DE_MUESTRA, SALIDA_DE_MUESTRA)
    except ErrorDeFormato as e:
        return [f"muestra del banco de pruebas: {e}"]
    esperada = json.loads(MEDICION_DE_MUESTRA.read_text(encoding="utf-8"))
    if medicion != esperada:
        distintos = sorted(k for k in set(medicion) | set(esperada) if medicion.get(k) != esperada.get(k))
        return [f"muestra del banco de pruebas: la medición no es la de {MEDICION_DE_MUESTRA.relative_to(RAIZ)} en {', '.join(distintos)}."]
    return []


def main(argumentos):
    if not argumentos or argumentos[0] not in ("validar", "contar", "sortear"):
        print(__doc__)
        return 2
    if argumentos[0] == "sortear":
        if len(argumentos) != 5 or not argumentos[3].isdigit() or not argumentos[4].isdigit():
            print(__doc__)
            return 2
        carpeta = Path(argumentos[1])
        try:
            sorteo = sortear(carpeta, Path(argumentos[2]), int(argumentos[3]), int(argumentos[4]))
        except ErrorDeFormato as e:
            print(f"Error: {e}", file=sys.stderr)
            return 1
        destino = carpeta / "revision" / "sorteo.json"
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_text(json.dumps(sorteo, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
        print(f"Sorteo de {sorteo['tamano']} de {sorteo['poblacion']} extras, con la semilla {sorteo['semilla']}, en {destino}:")
        for clave in sorteo["claves"]:
            print(clave)
        return 0
    if argumentos[0] == "validar":
        carpeta = Path(argumentos[1]) if len(argumentos) > 1 else REGISTRO_DE_MUESTRA
        errores, avisos = validar(carpeta)
        for aviso in avisos:
            print(f"Aviso: {aviso}")
        for error in errores:
            print(f"Error: {error}")
        if errores:
            return 1
        registro, _, _ = leer_registro(carpeta)
        cuenta = {c: sum(1 for e in registro["entradas"].values() if e["clasificacion"] == c) for c in CLASIFICACIONES}
        print(f"Bien: {len(registro['entradas'])} entradas ({', '.join(f'{v} {k}' for k, v in cuenta.items())}), "
              f"{len(registro['indice']['noches'])} noches en A, {len(registro['indice']['tramos'])} tramos en B, "
              f"{len(registro['busquedas'])} búsquedas, {len(avisos)} avisos. Huella {registro['huella']}.")
        return 0
    destino = None
    if len(argumentos) == 1:
        carpeta, ruta_salida = REGISTRO_DE_MUESTRA, SALIDA_DE_MUESTRA
    elif len(argumentos) in (3, 4):
        carpeta, ruta_salida = Path(argumentos[1]), Path(argumentos[2])
        destino = Path(argumentos[3]) if len(argumentos) == 4 else None
    else:
        print(__doc__)
        return 2
    try:
        medicion = medir(carpeta, ruta_salida)
    except ErrorDeFormato as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1
    texto = json.dumps(medicion, ensure_ascii=False, indent=1) + "\n"
    if destino is None:
        sys.stdout.write(texto)
    else:
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_text(texto, encoding="utf-8", newline="\n")
        print(f"Medición escrita en {destino}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
