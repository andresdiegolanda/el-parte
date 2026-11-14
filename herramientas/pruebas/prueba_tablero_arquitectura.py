#!/usr/bin/env python3
"""Prueba del control del tablero de arquitectura contra el código (ADR 0012).

Parte del modelo y del código reales, que tienen que coincidir, y les hace un cambio por vez, como
los que pasan al trabajar: una flecha que falta, un tópico nuevo, una clase sin nodo, una flecha
diseñada que ya se construyó. Cada cambio tiene que dar el error que corresponde, y solo ese.

Uso:  python3 herramientas/pruebas/prueba_tablero_arquitectura.py
"""

import copy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import tablero_arquitectura as ta  # noqa: E402

MODELO = ta.leer_modelo()
HECHOS = ta.hechos_del_codigo()
TARJETAS = ta.tarjetas_del_tablero()
fallas = []


def comprobar(condicion, mensaje):
    if not condicion:
        fallas.append(mensaje)
    print(("ok   " if condicion else "MAL  ") + mensaje)


def errores(modelo=None, hechos=None, tarjetas=None):
    modelo = modelo or MODELO
    return ta.validar_modelo(modelo) + ta.verificar_contra_el_codigo(modelo, hechos or HECHOS, tarjetas or TARJETAS)


def con_error(texto, modelo=None, hechos=None, tarjetas=None):
    e = errores(modelo, hechos, tarjetas)
    return len(e) == 1 and texto in e[0], e


def sin(modelo, id_flecha):
    m = copy.deepcopy(modelo)
    m["flechas"] = [f for f in m["flechas"] if f["id"] != id_flecha]
    return m


def archivo(nombre):
    return next(r for r in HECHOS["archivos"] if r.endswith("/" + nombre))


comprobar(errores() == [], "el modelo real coincide con el código real")

# Una relación del código que falta en el modelo.
ok, e = con_error("falta la flecha emite de editor a kafka/revision.pendiente", modelo=sin(MODELO, "editor>kafka/revision.pendiente"))
comprobar(ok, f"una emisión del código sin flecha se detecta {e if not ok else ''}")

# Una flecha construida que el código no tiene.
h = copy.deepcopy(HECHOS)
h["archivos"][archivo("Archivista.java")]["lee"].discard("parte.evidencias")
ok, e = con_error("la flecha base/evidencias>archivista está construida y el código no la tiene", hechos=h)
comprobar(ok, f"una lectura que el código dejó de hacer se detecta {e if not ok else ''}")

# Un tópico nuevo en Tipos.java.
h = copy.deepcopy(HECHOS)
h["topicos"]["NUEVO"] = "nuevo.topico"
ok, e = con_error("el tópico nuevo.topico está en Tipos.java y no en el tablero", hechos=h)
comprobar(ok, f"un tópico nuevo en el código se detecta {e if not ok else ''}")

# Una tabla nueva en la migración.
h = copy.deepcopy(HECHOS)
h["tablas"].add("parte.nueva")
ok, e = con_error("la tabla parte.nueva está en la primera migración y no en el tablero", hechos=h)
comprobar(ok, f"una tabla nueva en la migración se detecta {e if not ok else ''}")

# Una clase nueva que no está en ningún nodo.
h = copy.deepcopy(HECHOS)
ruta = "app/src/main/java/io/github/andresdiegolanda/elparte/analista/Resumidor.java"
h["archivos"][ruta] = {"emite": set(), "escucha": set(), "escucha_todos": False, "escribe": set(), "lee": set(), "usa": set(), "rutas": set()}
h["clases"]["Resumidor"] = ruta
ok, e = con_error("Resumidor.java no está en ningún nodo", hechos=h)
comprobar(ok, f"una clase sin nodo se detecta {e if not ok else ''}")

# Lo diseñado que se construye: la flecha diseñada tiene que pasar a construida.
h = copy.deepcopy(HECHOS)
h["archivos"][archivo("Analista.java")]["usa"].add("Modelo")
ok, e = con_error("la flecha analista>modelo está diseñada pero ya está en el código", hechos=h)
comprobar(ok, f"una flecha diseñada que ya se construyó se detecta {e if not ok else ''}")

# Una dependencia hacia un objeto común no pide flecha.
h = copy.deepcopy(HECHOS)
h["archivos"][archivo("Analista.java")]["usa"].add("RegistroDeMensajes")
comprobar(errores(hechos=h) == [], "usar un objeto común no pide flecha")

# Una ruta web nueva.
h = copy.deepcopy(HECHOS)
h["archivos"][archivo("MananaControlador.java")]["rutas"].add("GET /manana/{fecha}")
ok, e = con_error("la página web/manana dice", hechos=h)
comprobar(ok, f"una ruta nueva en un controlador se detecta {e if not ok else ''}")

# Lo diseñado con sus tarjetas ya hechas.
t = dict(TARJETAS)
t["T22"] = "hecho"
e = errores(tarjetas=t)
comprobar(e and all("sus tarjetas (T22) ya están hechas" in x for x in e), "lo diseñado con su tarjeta hecha se detecta")

# Errores de forma: una flecha hacia un nodo que no existe, un paso de recorrido roto.
m = copy.deepcopy(MODELO)
m["flechas"][0]["a"] = "no-existe"
comprobar(any("no es un nodo" in x for x in ta.validar_modelo(m)), "una flecha hacia un nodo que no existe se detecta")
m = copy.deepcopy(MODELO)
m["recorridos"][0]["pasos"][0]["en"] = "no-existe"
comprobar(any("va a no-existe" in x for x in ta.validar_modelo(m)), "un paso de recorrido roto se detecta")
m = copy.deepcopy(MODELO)
m["nodos"][1]["col"], m["nodos"][1]["fila"] = m["nodos"][0]["col"], m["nodos"][0]["fila"]
comprobar(any("en la misma celda" in x for x in ta.validar_modelo(m)), "dos nodos en la misma celda se detectan")

# Las vistas: lo que ubican tiene que existir, sin dos objetos en una celda, y sus recorridos tienen que caber.
m = copy.deepcopy(MODELO)
m["vistas"][0]["posiciones"]["no-existe"] = [9, 9]
comprobar(any("ubica a no-existe" in x for x in ta.validar_modelo(m)), "una vista que ubica un nodo que no existe se detecta")
m = copy.deepcopy(MODELO)
m["vistas"][0]["posiciones"]["correo"] = m["vistas"][0]["posiciones"]["web/login"]
comprobar(any("en la misma celda" in x and "vista" in x for x in ta.validar_modelo(m)), "dos objetos en la misma celda de una vista se detectan")
m = copy.deepcopy(MODELO)
del m["vistas"][0]["posiciones"]["base/envios"]
comprobar(any("no está en la vista usuarios" in x for x in ta.validar_modelo(m)), "un recorrido que se sale de su vista se detecta")
vista = ta.datos_de_vista(MODELO, MODELO["vistas"][0])
comprobar(all(f["de"] in MODELO["vistas"][0]["posiciones"] and f["a"] in MODELO["vistas"][0]["posiciones"] for f in vista["flechas"]),
          "la vista solo dibuja las flechas entre sus objetos")
comprobar(any(c["id"] == "inicio>kafka/corrida.iniciada" for c in vista["fuera"].get("inicio", [])),
          "la vista guarda las conexiones que le quedan afuera")

if fallas:
    print(f"\n{len(fallas)} fallas")
    sys.exit(1)
print("\nTodo bien.")
