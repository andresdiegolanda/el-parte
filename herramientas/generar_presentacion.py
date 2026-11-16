#!/usr/bin/env python3
"""Genera los dibujos de la presentación, docs/presentacion/imagenes/*.svg.

Cada dibujo lleva sus textos adentro, para que se lea igual en Marp y en GitHub.
Los datos de la medición son las noches con hallazgos del registro del banco de
pruebas: solo fechas y cantidades (ADR 0007). Se corre desde la raíz del
repositorio: python3 herramientas/generar_presentacion.py
"""
from datetime import date
from pathlib import Path

DESTINO = Path(__file__).resolve().parent.parent / "docs" / "presentacion" / "imagenes"
SANS = 'font-family="Arial, Helvetica, sans-serif"'
SERIF = 'font-family="Georgia, serif"'
MONO = 'font-family="Courier New, monospace"'
TINTA, GRIS, PAPEL, NARANJA = "#17263A", "#4A5361", "#F6F2E9", "#B4491F"


def texto(x, y, s, size=24, color=GRIS, anchor="start", peso="normal", familia=SANS):
    return (f'<text x="{x}" y="{y}" {familia} font-size="{size}" font-weight="{peso}" '
            f'fill="{color}" text-anchor="{anchor}">{s}</text>')


def guardar(nombre, ancho, alto, etiqueta, cuerpo):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho}" height="{alto}" '
           f'viewBox="0 0 {ancho} {alto}" role="img" aria-label="{etiqueta}">\n'
           + "\n".join(cuerpo) + "\n</svg>\n")
    (DESTINO / nombre).write_text(svg, encoding="utf-8")
    print(nombre, len(svg.encode()), "bytes")


# Portada: hojas sueltas de noche que se juntan en un parte.
hoja = ('<rect width="70" height="92" rx="6" fill="#24364D" stroke="#3E5573" stroke-width="2"/>'
        '<rect x="12" y="16" width="46" height="6" rx="3" fill="#4F6A8C"/>'
        '<rect x="12" y="32" width="38" height="6" rx="3" fill="#4F6A8C"/>'
        '<rect x="12" y="48" width="44" height="6" rx="3" fill="#4F6A8C"/>'
        '<rect x="12" y="64" width="30" height="6" rx="3" fill="#4F6A8C"/>')
c = [f'<rect width="660" height="660" rx="28" fill="{TINTA}"/>',
     '<circle cx="560" cy="90" r="36" fill="#F0A27A"/>', f'<circle cx="578" cy="78" r="32" fill="{TINTA}"/>',
     '<circle cx="470" cy="40" r="3" fill="#C4CDD8"/>', '<circle cx="620" cy="170" r="3" fill="#C4CDD8"/>',
     '<circle cx="500" cy="200" r="2.5" fill="#C4CDD8"/>']
for d in ("M 110 420 Q 250 500 368 480", "M 330 290 Q 370 330 368 380", "M 220 430 Q 300 440 368 430"):
    c.append(f'<path d="{d}" fill="none" stroke="#4F6A8C" stroke-width="3" stroke-linecap="round" stroke-dasharray="4 10"/>')
for x, y, a in ((20, 40, -14), (120, 10, 8), (230, 50, -6), (360, 70, 9), (40, 170, 10), (150, 150, -9),
                (270, 180, 5), (10, 300, -5), (130, 290, 12), (250, 320, -11)):
    c.append(f'<g transform="translate({x} {y}) rotate({a} 35 46)">{hoja}</g>')
c += ['<g transform="translate(370 300)">', f'<rect width="260" height="330" rx="14" fill="{PAPEL}"/>',
      f'<rect x="24" y="28" width="150" height="16" rx="8" fill="{TINTA}"/>']
for y, w in ((80, 200), (108, 180), (136, 120), (184, 110), (212, 90)):
    c.append(f'<rect x="24" y="{y}" width="{w}" height="10" rx="5" fill="#8C96A3"/>')
c += [f'<circle cx="200" cy="262" r="40" fill="none" stroke="{NARANJA}" stroke-width="6"/>',
      f'<path d="M 182 262 L 196 276 L 222 246" fill="none" stroke="{NARANJA}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>',
      '</g>']
guardar("portada.svg", 660, 660, "Muchas hojas sueltas, de noche, que se juntan en un solo parte con un sello de verificado", c)

# Qué hace: tres pasos.
def panel(x0, dibujo, titulo, lineas):
    p = [f'<rect x="{x0}" y="0" width="500" height="430" rx="22" fill="#FFFDF7" stroke="#DDD5C3" stroke-width="2"/>',
         f'<g transform="translate({x0 + 40} 36)">{dibujo}</g>',
         texto(x0 + 40, 238, titulo, 26, TINTA, peso="bold")]
    for k, l in enumerate(lineas):
        p.append(texto(x0 + 40, 284 + k * 34, l, 24))
    return p

seguir = ('<rect x="10" y="14" width="180" height="30" rx="15" fill="#E7E0CF"/><circle cx="34" cy="29" r="8" fill="#C9BFA9"/>'
          '<rect x="54" y="25" width="90" height="8" rx="4" fill="#C9BFA9"/>'
          f'<rect x="10" y="55" width="180" height="30" rx="15" fill="{TINTA}"/><circle cx="34" cy="70" r="8" fill="#F0A27A"/>'
          f'<rect x="54" y="66" width="100" height="8" rx="4" fill="{PAPEL}"/>'
          '<rect x="10" y="96" width="180" height="30" rx="15" fill="#E7E0CF"/><circle cx="34" cy="111" r="8" fill="#C9BFA9"/>'
          '<rect x="54" y="107" width="80" height="8" rx="4" fill="#C9BFA9"/>')
hojita = ('<rect width="40" height="54" rx="5" fill="#24364D" stroke="#4F6A8C" stroke-width="2"/>'
          '<rect x="8" y="12" width="24" height="5" rx="2.5" fill="#4F6A8C"/><rect x="8" y="24" width="18" height="5" rx="2.5" fill="#4F6A8C"/>')
noche = (f'<rect width="200" height="140" rx="18" fill="{TINTA}"/><circle cx="156" cy="38" r="20" fill="#F0A27A"/>'
         f'<circle cx="166" cy="31" r="18" fill="{TINTA}"/><circle cx="40" cy="26" r="2.5" fill="#C4CDD8"/>'
         '<circle cx="72" cy="18" r="2.5" fill="#C4CDD8"/><circle cx="110" cy="30" r="2.5" fill="#C4CDD8"/>'
         f'<g transform="translate(24 66) rotate(-8 20 27)">{hojita}</g><g transform="translate(78 62) rotate(6 20 27)">{hojita}</g>'
         f'<g transform="translate(132 70) rotate(-4 20 27)">{hojita}</g>')
rayos = "".join(f'<line x1="{a}" y1="{b}" x2="{c_}" y2="{d}"/>' for a, b, c_, d in (
    (74, 46, 82, 46), (18, 46, 10, 46), (46, 74, 46, 82), (46, 18, 46, 10),
    (65.8, 65.8, 71.5, 71.5), (26.2, 65.8, 20.5, 71.5), (26.2, 26.2, 20.5, 20.5), (65.8, 26.2, 71.5, 20.5)))
manana = (f'<rect width="200" height="140" rx="18" fill="#FBE9DD"/><circle cx="46" cy="46" r="20" fill="{NARANJA}"/>'
          f'<g stroke="{NARANJA}" stroke-width="4" stroke-linecap="round">{rayos}</g>'
          '<rect x="100" y="22" width="80" height="102" rx="8" fill="#FFFDF7" stroke="#DDD5C3" stroke-width="2"/>'
          '<rect x="112" y="38" width="56" height="7" rx="3.5" fill="#8C96A3"/><rect x="112" y="54" width="48" height="7" rx="3.5" fill="#8C96A3"/>'
          '<rect x="112" y="70" width="52" height="7" rx="3.5" fill="#8C96A3"/>'
          '<circle cx="158" cy="104" r="12" fill="none" stroke="#2F6690" stroke-width="3"/>'
          '<path d="M 152 104 L 157 109 L 165 99" fill="none" stroke="#2F6690" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>')
c = (panel(0, seguir, "1. Elegís a quién seguir", ["Un organismo, una empresa,", "un programa o una licitación."])
     + panel(550, noche, "2. De noche, lee las fuentes", ["Lo que publicaron ese día las", "fuentes públicas del Estado y", "de la banca de desarrollo."])
     + panel(1100, manana, "3. A la mañana, te avisa", ["Lo nuevo de lo que seguís, con", "su fuente. Y si no hubo nada,", "lo dice."]))
for x in (507, 1057):
    c.append(f'<path d="M {x} 215 L {x + 30} 215 M {x + 20} 205 L {x + 32} 215 L {x + 20} 225" fill="none" stroke="{NARANJA}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>')
guardar("que-hace.svg", 1600, 430, "Tres pasos: elegís a quién seguir, de noche lee las fuentes, a la mañana te avisa", c)

# Agentes: cinco estaciones en fila y el observador arriba.
centros = (160, 480, 800, 1120, 1440)
cy = 236
c = [f'<rect x="96" y="{cy - 7}" width="1408" height="14" rx="7" fill="#DDD5C3"/>']
for x in centros:
    c.append(f'<line x1="800" y1="92" x2="{x}" y2="{cy - 64}" stroke="#8C96A3" stroke-width="3" stroke-dasharray="8 10" stroke-linecap="round"/>')
c += [f'<path d="M 740 58 Q 800 10 860 58 Q 800 106 740 58 Z" fill="{PAPEL}" stroke="{GRIS}" stroke-width="5" stroke-linejoin="round"/>',
      f'<circle cx="800" cy="58" r="14" fill="{GRIS}"/>',
      texto(880, 46, "<tspan font-weight=\"bold\" fill=\"#17263A\">Observador</tspan>: mide el costo, el tiempo", 26),
      texto(880, 80, "y los aciertos de cada uno.", 26)]
for x in (320, 640, 960, 1280):
    c.append(f'<path d="M {x - 7} {cy - 12} L {x + 7} {cy} L {x - 7} {cy + 12}" fill="none" stroke="#8C96A3" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>')
for i, x in enumerate(centros):
    c.append(f'<circle cx="{x}" cy="{cy}" r="64" fill="{NARANJA if i == 2 else TINTA}"/>')
g = [f'<circle cx="{centros[0] - 10}" cy="{cy - 10}" r="22"/>',
     f'<line x1="{centros[0] + 6}" y1="{cy + 6}" x2="{centros[0] + 26}" y2="{cy + 26}"/>',
     f'<rect x="{centros[1] - 32}" y="{cy - 26}" width="64" height="52" rx="6"/>',
     f'<line x1="{centros[1] - 32}" y1="{cy - 6}" x2="{centros[1] + 32}" y2="{cy - 6}"/>',
     f'<path d="M {centros[2]} {cy - 36} L {centros[2] + 30} {cy - 24} L {centros[2] + 26} {cy + 10} Q {centros[2] + 18} {cy + 30} {centros[2]} {cy + 38} Q {centros[2] - 18} {cy + 30} {centros[2] - 26} {cy + 10} L {centros[2] - 30} {cy - 24} Z"/>',
     f'<path d="M {centros[2] - 14} {cy} L {centros[2] - 3} {cy + 11} L {centros[2] + 15} {cy - 10}"/>',
     f'<circle cx="{centros[3]}" cy="{cy - 12}" r="22"/>',
     f'<line x1="{centros[3] - 10}" y1="{cy + 20}" x2="{centros[3] + 10}" y2="{cy + 20}"/>',
     f'<line x1="{centros[3] - 7}" y1="{cy + 30}" x2="{centros[3] + 7}" y2="{cy + 30}"/>',
     f'<rect x="{centros[4] - 24}" y="{cy - 32}" width="48" height="64" rx="6"/>']
for dy, fin in ((-14, 14), (0, 14), (14, 4)):
    g.append(f'<line x1="{centros[4] - 14}" y1="{cy + dy}" x2="{centros[4] + fin}" y2="{cy + dy}"/>')
c.append(f'<g fill="none" stroke="{PAPEL}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round">{"".join(g)}</g>')
c.append(f'<rect x="{centros[1] - 10}" y="{cy + 4}" width="20" height="6" rx="3" fill="{PAPEL}"/>')
roles = (("Explorador", "busca lo publicado esa", "noche en una fuente"),
         ("Archivista", "se acuerda de lo ya visto:", "¿es nuevo?"),
         ("Refutador", "intenta demostrar", "que es falso"),
         ("Analista", "explica qué dice y", "por qué importa"),
         ("Editor", "arma la página", "de la mañana"))
for x, (nombre, l1, l2) in zip(centros, roles):
    c += [texto(x, 350, nombre, 28, TINTA, "middle", "bold"), texto(x, 388, l1, 24, anchor="middle"),
          texto(x, 420, l2, 24, anchor="middle")]
guardar("agentes.svg", 1600, 440, "Cinco agentes en fila, del explorador al editor, y un observador arriba que mide a todos", c)

# Grados: tres sellos.
c = [f'<rect width="1600" height="230" rx="24" fill="{TINTA}"/>']
for x, color, extra, nombre in (
        (267, "#7FB2DB", '<path d="M 245 82 L 261 98 L 291 64" fill="none" stroke="#7FB2DB" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>', "PROVEN"),
        (800, "#F0A27A", "".join(f'<circle cx="{800 + d}" cy="80" r="7" fill="#F0A27A"/>' for d in (-22, 0, 22)), "INFERRED"),
        (1333, "#A5AEBB", '<line x1="1297.6" y1="115.4" x2="1368.4" y2="44.6" stroke="#A5AEBB" stroke-width="7" stroke-linecap="round"/>', "NOT_MENTIONED")):
    trazo = ' stroke-dasharray="14 10"' if nombre == "INFERRED" else ""
    c += [f'<circle cx="{x}" cy="80" r="50" fill="none" stroke="{color}" stroke-width="7"{trazo}/>', extra,
          texto(x, 192, nombre, 30, color, "middle", "bold", MONO)]
guardar("grados.svg", 1600, 230, "Tres sellos: PROVEN con una marca de verificado, INFERRED punteado y NOT_MENTIONED tachado", c)

# Medición: 78 noches de la vigilancia anterior y de El Parte.
inicio = date(2026, 8, 29)
hallazgos = {"2026-09-17": 1, "2026-09-23": 2, "2026-09-25": 1, "2026-09-29": 1, "2026-10-01": 1, "2026-10-08": 3,
             "2026-10-13": 2, "2026-10-15": 2, "2026-11-03": 1, "2026-11-10": 1, "2026-11-11": 2, "2026-11-12": 2}
assert sum(hallazgos.values()) == 19 and (date(2026, 11, 14) - inicio).days == 77
cuenta = {(date.fromisoformat(k) - inicio).days: v for k, v in hallazgos.items()}
x = lambda i: round(20 + i * 20.2, 1)
c = [texto(20, 36, "Vigilancia anterior, con un solo agente.", 28, TINTA),
     texto(20, 72, "En naranja, las noches con hallazgos: 19 en total.", 28)]
for i in range(78):
    h = 28 + 28 * cuenta[i] if i in cuenta else 24
    c.append(f'<rect x="{x(i)}" y="{200 - h}" width="12" height="{h}" rx="3" fill="{NARANJA if i in cuenta else "#D3CAB6"}"/>')
c += ['<rect x="0" y="200" width="1600" height="2" fill="#8C96A3"/>',
      texto(20, 252, "El Parte: las mismas 78 noches, una por una, con varios agentes.", 28, TINTA)]
for i in range(78):
    c.append(f'<rect x="{x(i)}" y="270" width="12" height="60" rx="3" fill="#2F6690"/>')
c.append('<rect x="0" y="330" width="1600" height="2" fill="#8C96A3"/>')
for i, mes in ((3, "Septiembre"), (33, "Octubre"), (64, "Noviembre")):
    c += [f'<rect x="{x(i) - 4}" y="334" width="2" height="14" fill="#8C96A3"/>', texto(x(i) + 4, 378, mes, 26)]
guardar("medicion.svg", 1600, 400, "Dos filas de 78 noches: arriba la vigilancia anterior, con sus noches de hallazgos en naranja; abajo El Parte, que repite cada noche", c)

# Dónde estamos: cuatro fines de semana.
hitos = (200, 600, 1000, 1400)
c = [f'<line x1="200" y1="130" x2="800" y2="130" stroke="{TINTA}" stroke-width="6" stroke-linecap="round"/>',
     '<line x1="800" y1="130" x2="1400" y2="130" stroke="#8C96A3" stroke-width="6" stroke-linecap="round" stroke-dasharray="14 12"/>',
     f'<line x1="800" y1="52" x2="800" y2="110" stroke="{NARANJA}" stroke-width="3" stroke-dasharray="6 6"/>',
     texto(800, 36, "Hoy, 16 de noviembre", 26, "#8A3415", "middle", "bold")]
for x in hitos[:2]:
    c += [f'<circle cx="{x}" cy="130" r="40" fill="{TINTA}"/>',
          f'<path d="M {x - 18} 132 L {x - 5} 145 L {x + 20} 116" fill="none" stroke="{PAPEL}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>']
c += [f'<circle cx="1000" cy="130" r="40" fill="#FBE9DD" stroke="{NARANJA}" stroke-width="6"/>',
      f'<path d="M 1000 93 A 37 37 0 0 0 1000 167 Z" fill="{NARANJA}"/>',
      f'<circle cx="1400" cy="130" r="40" fill="{PAPEL}" stroke="#8C96A3" stroke-width="6"/>']
etapas = (("7 y 8 de noviembre", ("Documentación: el plan,", "las decisiones y cómo", "se hablan los agentes"), ("Hecho", TINTA)),
          ("14 y 15 de noviembre", ("La primera rebanada:", "una fuente, de la red", "a la página de la mañana"), ("Hecho", TINTA)),
          ("21 y 22 de noviembre", ("La medición contra la", "vigilancia anterior y", "el analista completo"), ("Empezado: medición corrida", "#8A3415")),
          ("28 y 29 de noviembre", ("Las otras cuatro fuentes,", "los usuarios y la", "demostración"), ("Pendiente", GRIS)))
for x, (fecha, lineas, (estado, color)) in zip(hitos, etapas):
    c.append(texto(x, 215, fecha, 26, TINTA, "middle", "bold"))
    c += [texto(x, 252 + k * 32, l, 24, anchor="middle") for k, l in enumerate(lineas)]
    c.append(texto(x, 352, estado, 24, color, "middle", "bold"))
guardar("donde.svg", 1600, 370, "El plan en cuatro fines de semana: los dos primeros hechos, el tercero empezado y el cuarto pendiente, con la marca de hoy entre el segundo y el tercero", c)
