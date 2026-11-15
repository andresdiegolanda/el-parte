"""El tablero de arquitectura: su modelo, lo que dice el código y la comparación entre los dos.

docs/tablero-de-arquitectura/sistema.json describe el sistema objeto por objeto: nodos (agentes,
tópicos, servicios, tablas, páginas y lo de afuera), flechas entre ellos, recorridos y el historial
de cambios (ADR 0012). Este módulo:

1. Lee y valida el modelo.
2. Lee del código lo que se puede leer sin ejecutarlo: los tópicos, qué emite y qué escucha cada
   clase, qué tablas escribe y lee, de qué otras clases depende y qué rutas web atiende.
3. Compara las dos cosas. Cada relación construida del modelo tiene que estar en el código, y cada
   relación del código tiene que estar en el modelo como construida.
4. Genera docs/tablero-de-arquitectura/index.html desde plantilla.html y el modelo, y una página más
   por cada vista: una parte del sistema con su propia grilla, sacada del mismo modelo.

Lo usan herramientas/generar_tablero_arquitectura.py y herramientas/verificar_docs.py. Solo usa la
biblioteca estándar de Python.
"""

import json
import re
from pathlib import Path

import tablero_md

RAIZ = Path(__file__).resolve().parent.parent
CARPETA = RAIZ / "docs" / "tablero-de-arquitectura"
MODELO = CARPETA / "sistema.json"
PLANTILLA = CARPETA / "plantilla.html"
SALIDA = CARPETA / "index.html"
TABLERO = RAIZ / "tablero.md"

CODIGO = RAIZ / "app" / "src" / "main" / "java"
PLANTILLAS_WEB = RAIZ / "app" / "src" / "main" / "resources" / "templates"
PRIMERA_MIGRACION = RAIZ / "app" / "src" / "main" / "resources" / "db" / "migration" / "V1__base.sql"

MARCA_DATOS = "/*SISTEMA*/"
MARCA_AVISO = "<!--AVISO-->"
AVISO = ("<!-- Generado por herramientas/generar_tablero_arquitectura.py desde sistema.json y plantilla.html. "
         "No se edita a mano: se cambia sistema.json o plantilla.html y se vuelve a generar. -->")

TIPOS_DE_NODO = {"agente", "topico", "servicio", "tabla", "pagina", "externo", "infra", "grupo-de-topicos"}
TIPOS_DE_FLECHA = {"emite", "escucha", "usa", "lee", "escribe", "llama"}
ESTADOS = {"construido", "disenado"}
PAPELES = {"camino", "auxiliar"}

# Relaciones que se leen del código: (tipo de flecha, de dónde sale, a dónde llega).
#   emite:   nodo -> tópico      escucha: tópico -> nodo
#   escribe: nodo -> tabla       lee:     tabla -> nodo
#   usa:     nodo -> nodo (por las dependencias del constructor)
VERIFICABLES = {"emite", "escucha", "escribe", "lee", "usa"}


# ---------------------------------------------------------------- el modelo

def leer_modelo(ruta=MODELO):
    return json.loads(ruta.read_text(encoding="utf-8"))


def validar_modelo(modelo):
    """Errores de forma del modelo: ids, referencias, tipos, posiciones."""
    errores = []
    donde = "docs/tablero-de-arquitectura/sistema.json"
    nodos = modelo.get("nodos", [])
    flechas = modelo.get("flechas", [])
    ids_nodos = [n.get("id") for n in nodos]
    ids_flechas = [f.get("id") for f in flechas]
    for repetido in {i for i in ids_nodos + ids_flechas if (ids_nodos + ids_flechas).count(i) > 1}:
        errores.append(f"{donde}: el id {repetido} está repetido")
    por_id = {n["id"]: n for n in nodos if "id" in n}
    celdas = {}
    for n in nodos:
        nid = n.get("id", "?")
        for campo in ("id", "tipo", "titulo", "estado", "col", "fila", "que_es", "para_que"):
            if campo not in n or n[campo] in ("", None):
                errores.append(f"{donde}: al nodo {nid} le falta '{campo}'")
        if n.get("tipo") not in TIPOS_DE_NODO:
            errores.append(f"{donde}: el nodo {nid} tiene un tipo desconocido: {n.get('tipo')}")
        if n.get("estado") not in ESTADOS:
            errores.append(f"{donde}: el nodo {nid} tiene un estado desconocido: {n.get('estado')}")
        celda = (n.get("col"), n.get("fila"))
        if celda in celdas:
            errores.append(f"{donde}: los nodos {celdas[celda]} y {nid} están en la misma celda {celda}")
        celdas[celda] = nid
        if n.get("tipo") == "topico" and not n.get("topico"):
            errores.append(f"{donde}: el tópico {nid} no dice su nombre en 'topico'")
        if n.get("tipo") == "tabla" and not n.get("tabla"):
            errores.append(f"{donde}: la tabla {nid} no dice su nombre en 'tabla'")
        for ruta in n.get("codigo", []):
            if not (RAIZ / ruta).exists():
                errores.append(f"{donde}: el nodo {nid} cita {ruta}, que no existe")
        for ruta in n.get("docs", []):
            if not (RAIZ / ruta.split("#")[0]).exists():
                errores.append(f"{donde}: el nodo {nid} cita {ruta}, que no existe")
    for f in flechas:
        fid = f.get("id", "?")
        for campo in ("id", "de", "a", "tipo", "estado", "papel", "etiqueta", "detalle"):
            if campo not in f or f[campo] in ("", None):
                errores.append(f"{donde}: a la flecha {fid} le falta '{campo}'")
        for extremo in ("de", "a"):
            if f.get(extremo) not in por_id:
                errores.append(f"{donde}: la flecha {fid} va {'desde' if extremo == 'de' else 'hacia'} {f.get(extremo)}, que no es un nodo")
        if f.get("tipo") not in TIPOS_DE_FLECHA:
            errores.append(f"{donde}: la flecha {fid} tiene un tipo desconocido: {f.get('tipo')}")
        if f.get("estado") not in ESTADOS:
            errores.append(f"{donde}: la flecha {fid} tiene un estado desconocido: {f.get('estado')}")
        if f.get("papel") not in PAPELES:
            errores.append(f"{donde}: la flecha {fid} tiene un papel desconocido: {f.get('papel')}")
        if f.get("estado") == "construido" and (por_id.get(f.get("de"), {}).get("estado") == "disenado"
                                                or por_id.get(f.get("a"), {}).get("estado") == "disenado"):
            errores.append(f"{donde}: la flecha {fid} está construida pero uno de sus extremos está diseñado")
    todos = set(ids_nodos) | set(ids_flechas)
    for r in modelo.get("recorridos", []):
        if not r.get("pasos"):
            errores.append(f"{donde}: el recorrido {r.get('id')} no tiene pasos")
        for i, paso in enumerate(r.get("pasos", []), start=1):
            if paso.get("en") not in todos:
                errores.append(f"{donde}: el paso {i} del recorrido {r.get('id')} va a {paso.get('en')}, que no existe")
            if not paso.get("texto"):
                errores.append(f"{donde}: el paso {i} del recorrido {r.get('id')} no tiene texto")
    for h in modelo.get("historial", []):
        for objeto in h.get("objetos", []):
            if objeto not in todos:
                errores.append(f"{donde}: el historial del {h.get('fecha')} cita {objeto}, que no existe")
    errores += _validar_vistas(modelo, por_id, donde)
    return errores


def _validar_vistas(modelo, por_id, donde):
    errores, archivos = [], set()
    flechas = {f["id"]: f for f in modelo.get("flechas", [])}
    recorridos = {r["id"]: r for r in modelo.get("recorridos", [])}
    for v in modelo.get("vistas", []):
        vid = v.get("id", "?")
        if not v.get("boton") or not v.get("titulo") or not v.get("sub"):
            errores.append(f"{donde}: a la vista {vid} le falta 'boton', 'titulo' o 'sub'")
        archivo = v.get("archivo", "")
        if not re.fullmatch(r"[a-z0-9-]+\.html", archivo) or archivo == "index.html" or archivo in archivos:
            errores.append(f"{donde}: la vista {vid} tiene un archivo inválido o repetido: {archivo}")
        archivos.add(archivo)
        posiciones = v.get("posiciones", {})
        celdas = {}
        for nid, celda in posiciones.items():
            if nid not in por_id:
                errores.append(f"{donde}: la vista {vid} ubica a {nid}, que no es un nodo")
            if tuple(celda) in celdas:
                errores.append(f"{donde}: en la vista {vid}, {celdas[tuple(celda)]} y {nid} están en la misma celda {celda}")
            celdas[tuple(celda)] = nid
        for rid in v.get("recorridos", []):
            if rid not in recorridos:
                errores.append(f"{donde}: la vista {vid} cita el recorrido {rid}, que no existe")
                continue
            for i, paso in enumerate(recorridos[rid]["pasos"], start=1):
                en = paso.get("en")
                f = flechas.get(en)
                adentro = en in posiciones or (f is not None and f["de"] in posiciones and f["a"] in posiciones)
                if not adentro:
                    errores.append(f"{donde}: el paso {i} del recorrido {rid} va a {en}, que no está en la vista {vid}")
    return errores


# ---------------------------------------------------------------- el código

def _literales(java):
    """Los textos entre comillas de un archivo Java: los bloques de texto y las cadenas comunes."""
    literales = re.findall(r'"""(.*?)"""', java, re.S)
    sin_bloques = re.sub(r'"""(.*?)"""', " ", java, flags=re.S)
    literales += re.findall(r'"((?:[^"\\\n]|\\.)*)"', sin_bloques)
    return literales


def _sin_comentarios(java):
    java = re.sub(r"/\*.*?\*/", " ", java, flags=re.S)
    return re.sub(r"//[^\n]*", " ", java)


def _constructor(java, clase):
    """Los tipos de los parámetros del constructor público de la clase, o [] si no tiene."""
    m = re.search(r"public\s+" + re.escape(clase) + r"\s*\(", java)
    if not m:
        return []
    i, nivel, inicio = m.end(), 1, m.end()
    while i < len(java) and nivel:
        nivel += {"(": 1, ")": -1}.get(java[i], 0)
        i += 1
    parametros, actual, nivel = [], "", 0
    for c in java[inicio:i - 1]:
        if c in "(<":
            nivel += 1
        elif c in ")>":
            nivel -= 1
        if c == "," and nivel == 0:
            parametros.append(actual)
            actual = ""
        else:
            actual += c
    if actual.strip():
        parametros.append(actual)
    tipos = []
    for p in parametros:
        p = re.sub(r"@\w+(\((?:[^()]|\([^()]*\))*\))?", " ", p).split()
        if len(p) >= 2:
            tipos.append(re.sub(r"<.*", "", p[-2]).split(".")[0])
    return tipos


def hechos_del_codigo():
    """Lo que dice el código, por archivo. Devuelve un dict con:

    - topicos: {constante: nombre} de Tipos.java, y todos: la lista TODOS en nombres.
    - tablas: los nombres esquema.tabla de la primera migración.
    - archivos: {ruta relativa: {emite, escucha, escucha_todos, escribe, lee, usa, rutas}}.
    - clases: {nombre simple: ruta relativa}.
    - plantillas: las rutas relativas de las plantillas web.
    - problemas: lo que no se pudo leer.
    """
    tipos_java = next(CODIGO.rglob("Tipos.java")).read_text(encoding="utf-8")
    topicos = dict(re.findall(r'public static final String (\w+) = "([^"]+)";', tipos_java))
    todos_m = re.search(r"TODOS\s*=\s*List\.of\((.*?)\);", tipos_java, re.S)
    todos = [topicos[c] for c in re.findall(r"\b([A-Z_]+)\b", todos_m.group(1)) if c in topicos] if todos_m else []
    tablas = set(re.findall(r"^create table (\w+\.\w+)", PRIMERA_MIGRACION.read_text(encoding="utf-8"), re.M))

    java = {f: f.read_text(encoding="utf-8") for f in sorted(CODIGO.rglob("*.java"))}
    clases = {f.stem: f.relative_to(RAIZ).as_posix() for f in java}
    archivos, problemas = {}, []
    for f, fuente in java.items():
        rel = f.relative_to(RAIZ).as_posix()
        codigo = _sin_comentarios(fuente)
        h = {"emite": set(), "escucha": set(), "escucha_todos": False, "escribe": set(), "lee": set(),
             "usa": set(), "rutas": set()}
        for llamada in re.finditer(r"emisor\.emitir\(\s*[^,]+,\s*([^,]+),", codigo):
            constante = re.fullmatch(r"\s*Tipos\.(\w+)\s*", llamada.group(1))
            if constante and constante.group(1) in topicos:
                h["emite"].add(topicos[constante.group(1)])
            else:
                problemas.append(f"{rel}: emite a un tópico que no es una constante de Tipos ({llamada.group(1).strip()})")
        for escucha in re.finditer(r"@KafkaListener\(((?:[^()]|\([^()]*\))*)\)", codigo):
            parte = re.search(r"topics\s*=\s*(\{[^}]*\}|[^,)]+)", escucha.group(1))
            nombres = {topicos[c] for c in re.findall(r"Tipos\.(\w+)", parte.group(1)) if c in topicos} if parte else set()
            if not nombres:
                problemas.append(f"{rel}: un @KafkaListener sin tópicos de Tipos")
            if todos and nombres == set(todos):
                h["escucha_todos"] = True
            else:
                h["escucha"] |= nombres
        for texto in _literales(codigo):
            mencionadas = set(re.findall(r"\b((?:parte|cuentas)\.\w+)", texto))
            escritas = set(re.findall(r"(?:insert\s+into|update|delete\s+from)\s+((?:parte|cuentas)\.\w+)", texto, re.I))
            h["escribe"] |= escritas & tablas
            h["lee"] |= (mencionadas - escritas) & tablas
        h["lee"] -= h["escribe"]
        h["usa"] = {t for t in _constructor(codigo, f.stem) if t in clases and t != f.stem}
        for verbo, ruta in re.findall(r'@(Get|Post|Put|Delete)Mapping\("([^"]+)"\)', codigo):
            h["rutas"].add(f"{verbo.upper()} {ruta}")
        archivos[rel] = h
    plantillas = sorted(f.relative_to(RAIZ).as_posix() for f in PLANTILLAS_WEB.glob("*.html"))
    return {"topicos": topicos, "todos": todos, "tablas": tablas, "archivos": archivos, "clases": clases,
            "plantillas": plantillas, "problemas": problemas}


# ---------------------------------------------------------------- la comparación

def _relaciones_del_codigo(modelo, hechos, errores):
    """Las relaciones del código, en ids de nodos del modelo: {(tipo, de, a): por qué}."""
    nodo_de_archivo = {}
    for n in modelo["nodos"]:
        for ruta in n.get("codigo", []):
            if ruta in nodo_de_archivo:
                errores.append(f"tablero de arquitectura: {ruta} está en dos nodos, {nodo_de_archivo[ruta]} y {n['id']}")
            nodo_de_archivo[ruta] = n["id"]
    for ruta in list(hechos["archivos"]) + hechos["plantillas"]:
        if ruta not in nodo_de_archivo:
            errores.append(f"tablero de arquitectura: {ruta} no está en ningún nodo; sumalo al nodo que corresponda o creá uno")
    topico_a_nodo = {n["topico"]: n["id"] for n in modelo["nodos"] if n["tipo"] == "topico"}
    tabla_a_nodo = {n["tabla"]: n["id"] for n in modelo["nodos"] if n["tipo"] == "tabla"}
    comunes = {n["id"] for n in modelo["nodos"] if n.get("comun")}
    todos = next((n["id"] for n in modelo["nodos"] if n["tipo"] == "grupo-de-topicos"), None)

    relaciones = {}
    for ruta, h in hechos["archivos"].items():
        nodo = nodo_de_archivo.get(ruta)
        if not nodo:
            continue
        archivo = ruta.rsplit("/", 1)[-1]
        for t in h["emite"]:
            if t in topico_a_nodo:
                relaciones[("emite", nodo, topico_a_nodo[t])] = f"{archivo} emite a {t}"
        for t in h["escucha"]:
            if t in topico_a_nodo:
                relaciones[("escucha", topico_a_nodo[t], nodo)] = f"{archivo} escucha {t}"
        if h["escucha_todos"]:
            if todos:
                relaciones[("escucha", todos, nodo)] = f"{archivo} escucha todos los tópicos"
            else:
                errores.append(f"tablero de arquitectura: {archivo} escucha todos los tópicos y no hay un nodo grupo-de-topicos")
        for t in h["escribe"]:
            if t in tabla_a_nodo:
                relaciones[("escribe", nodo, tabla_a_nodo[t])] = f"{archivo} escribe en {t}"
        for t in h["lee"]:
            if t in tabla_a_nodo:
                relaciones[("lee", tabla_a_nodo[t], nodo)] = f"{archivo} lee {t}"
        for clase in h["usa"]:
            otro = nodo_de_archivo.get(hechos["clases"][clase])
            if otro and otro != nodo and otro not in comunes:
                relaciones[("usa", nodo, otro)] = f"{archivo} recibe un {clase} en su constructor"
    return relaciones


def verificar_contra_el_codigo(modelo, hechos, tarjetas):
    """Errores de la comparación entre el modelo y el código. tarjetas: {id: columna} de tablero.md."""
    errores = list(f"tablero de arquitectura: {p}" for p in hechos["problemas"])
    nodos = {n["id"]: n for n in modelo["nodos"]}

    # Tópicos y tablas: los construidos del modelo son exactamente los del código.
    del_modelo = {n["topico"] for n in modelo["nodos"] if n["tipo"] == "topico" and n["estado"] == "construido"}
    del_codigo = set(hechos["topicos"].values())
    for t in sorted(del_codigo - del_modelo):
        errores.append(f"tablero de arquitectura: el tópico {t} está en Tipos.java y no en el tablero")
    for t in sorted(del_modelo - del_codigo):
        errores.append(f"tablero de arquitectura: el tópico {t} está construido en el tablero y no está en Tipos.java")
    del_modelo = {n["tabla"] for n in modelo["nodos"] if n["tipo"] == "tabla" and n["estado"] == "construido"}
    for t in sorted(hechos["tablas"] - del_modelo):
        errores.append(f"tablero de arquitectura: la tabla {t} está en la primera migración y no en el tablero")
    for t in sorted(del_modelo - hechos["tablas"]):
        errores.append(f"tablero de arquitectura: la tabla {t} está construida en el tablero y no en la primera migración")

    # Relaciones: las del código, construidas en el modelo; las construidas del modelo, en el código.
    del_codigo = _relaciones_del_codigo(modelo, hechos, errores)
    construidas, disenadas = {}, {}
    for f in modelo["flechas"]:
        clave = (f["tipo"], f["de"], f["a"])
        (construidas if f["estado"] == "construido" else disenadas)[clave] = f["id"]
    for clave, motivo in sorted(del_codigo.items()):
        if clave in construidas:
            continue
        if clave in disenadas:
            errores.append(f"tablero de arquitectura: la flecha {disenadas[clave]} está diseñada pero ya está en el código ({motivo}); pasala a construida")
        else:
            errores.append(f"tablero de arquitectura: falta la flecha {clave[0]} de {clave[1]} a {clave[2]} ({motivo})")
    for clave, fid in sorted(construidas.items()):
        if clave[0] in VERIFICABLES and clave not in del_codigo:
            errores.append(f"tablero de arquitectura: la flecha {fid} está construida y el código no la tiene")

    # Rutas web: las de cada página, exactamente las de sus controladores.
    for n in modelo["nodos"]:
        if n["tipo"] != "pagina" or n["estado"] != "construido":
            continue
        rutas = set()
        for ruta in n.get("codigo", []):
            rutas |= hechos["archivos"].get(ruta, {}).get("rutas", set())
        if rutas != set(n.get("rutas", [])):
            errores.append(f"tablero de arquitectura: la página {n['id']} dice {sorted(n.get('rutas', []))} y su código atiende {sorted(rutas)}")

    # Lo construido que no es de afuera tiene código; lo diseñado tiene tarjetas vivas.
    for n in modelo["nodos"]:
        if n["estado"] == "construido" and n["tipo"] not in {"externo", "infra", "topico", "tabla", "grupo-de-topicos"} and not n.get("codigo"):
            errores.append(f"tablero de arquitectura: el nodo {n['id']} está construido y no cita su código")
    for objeto in modelo["nodos"] + modelo["flechas"]:
        if objeto["estado"] != "disenado":
            continue
        citadas = objeto.get("tarjetas", [])
        if not citadas:
            errores.append(f"tablero de arquitectura: {objeto['id']} está diseñado y no cita la tarjeta que lo construye")
            continue
        for t in citadas:
            if t not in tarjetas:
                errores.append(f"tablero de arquitectura: {objeto['id']} cita {t}, que no está en tablero.md")
        if all(tarjetas.get(t) == "hecho" for t in citadas):
            errores.append(f"tablero de arquitectura: {objeto['id']} está diseñado y sus tarjetas ({', '.join(citadas)}) ya están hechas; si se construyó, pasalo a construido")
    for n in nodos.values():
        if n["estado"] == "disenado":
            continue
        for t in n.get("tarjetas", []):
            if t not in tarjetas:
                errores.append(f"tablero de arquitectura: {n['id']} cita {t}, que no está en tablero.md")
    return errores


def tarjetas_del_tablero():
    datos, _ = tablero_md.leer(TABLERO)
    return {t["id"]: t["columna"] for t in datos["tarjetas"]}


# ---------------------------------------------------------------- la página

def datos_de_vista(modelo, vista):
    """Lo que la página de una vista necesita: sus nodos en su grilla, las flechas entre ellos, sus
    recorridos y, por cada nodo, las conexiones que quedan fuera de la vista."""
    posiciones = vista["posiciones"]
    titulos = {n["id"]: n["titulo"] for n in modelo["nodos"]}
    nodos = []
    for n in modelo["nodos"]:
        if n["id"] in posiciones:
            copia = dict(n)
            copia["col"], copia["fila"] = posiciones[n["id"]]
            nodos.append(copia)
    flechas = [f for f in modelo["flechas"] if f["de"] in posiciones and f["a"] in posiciones]
    adentro = {f["id"] for f in flechas}
    fuera = {}
    for f in modelo["flechas"]:
        if f["id"] in adentro:
            continue
        for extremo, otro, lado in (("de", "a", "sale"), ("a", "de", "entra")):
            if f[extremo] in posiciones:
                fuera.setdefault(f[extremo], []).append({"id": f["id"], "lado": lado, "otro": titulos[f[otro]],
                                                        "etiqueta": f["etiqueta"], "estado": f["estado"]})
    datos = {k: modelo[k] for k in ("actualizado", "repositorio", "tablero", "kanban")}
    datos.update({
        "titulo": vista["titulo"], "franjas": vista["franjas"], "columnas": vista["columnas"], "grupos": vista["grupos"],
        "nodos": nodos, "flechas": flechas,
        "recorridos": [r for r in modelo["recorridos"] if r["id"] in vista["recorridos"]],
        "historial": [dict(h, objetos=[o for o in h["objetos"] if o in posiciones or o in adentro]) for h in modelo["historial"]],
        "fuera": fuera, "vista": {"id": vista["id"], "titulo": vista["titulo"], "sub": vista["sub"], "archivo": vista["archivo"]},
        "vistas": [{"id": v["id"], "boton": v["boton"], "archivo": v["archivo"]} for v in modelo.get("vistas", [])],
    })
    return datos


def _pagina(plantilla, datos):
    contenido = json.dumps(datos, ensure_ascii=False, indent=1).replace("</", "<\\/")
    return plantilla.replace(MARCA_AVISO, AVISO).replace(MARCA_DATOS, contenido)


def generar(modelo=None):
    """Devuelve ({archivo: html}, errores): index.html y una página por vista. Con errores, {}."""
    modelo = modelo or leer_modelo()
    errores = validar_modelo(modelo)
    if errores:
        return {}, errores
    plantilla = PLANTILLA.read_text(encoding="utf-8")
    if plantilla.count(MARCA_DATOS) != 1 or plantilla.count(MARCA_AVISO) != 1:
        return {}, [f"{PLANTILLA.relative_to(RAIZ)}: tiene que tener una vez {MARCA_DATOS} y una vez {MARCA_AVISO}"]
    paginas = {"index.html": _pagina(plantilla, modelo)}
    for v in modelo.get("vistas", []):
        paginas[v["archivo"]] = _pagina(plantilla, datos_de_vista(modelo, v))
    return paginas, []


def verificar():
    """Todo lo que controla el verificador sobre el tablero de arquitectura."""
    modelo = leer_modelo()
    errores = validar_modelo(modelo)
    if errores:
        return errores
    errores += verificar_contra_el_codigo(modelo, hechos_del_codigo(), tarjetas_del_tablero())
    paginas, problemas = generar(modelo)
    errores += problemas
    for archivo, html in paginas.items():
        ruta = CARPETA / archivo
        if not ruta.exists() or ruta.read_text(encoding="utf-8") != html:
            errores.append(f"{ruta.relative_to(RAIZ)}: no coincide con sistema.json; corré python3 herramientas/generar_tablero_arquitectura.py")
    generadas = set(paginas) | {"plantilla.html"}
    for sobrante in sorted(CARPETA.glob("*.html")):
        if paginas and sobrante.name not in generadas:
            errores.append(f"{sobrante.relative_to(RAIZ)}: no lo genera ninguna vista de sistema.json; si la vista se quitó, el archivo va a obsoletos/")
    return errores
