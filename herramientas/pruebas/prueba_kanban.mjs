// Prueba del kanban editable con una base simulada, en un Chrome sin ventana.
//
// Abre kanban/index.html con un window.claude falso que imita la base de la página publicada,
// hace cambios como los haría una persona y comprueba que se respeten las reglas del tablero.
// Al final deja la base simulada como volcado, para probar la sincronización con
// herramientas/sincronizar_tablero.py.
//
// Uso:  NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_kanban.mjs [volcado.json]
// Chrome: se toma de PUPPETEER_EXECUTABLE_PATH o de /usr/bin/google-chrome.

import { createRequire } from "node:module";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const require = createRequire(import.meta.url);
const puppeteer = require("puppeteer");
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const html = path.join(raiz, "kanban", "index.html");
const salida = process.argv[2] || "/tmp/volcado-prueba.json";

const texto = readFileSync(html, "utf8");
const datos = JSON.parse(texto.match(/<script type="application\/json" id="datos">([\s\S]*?)<\/script>/)[1].replace(/<\\\//g, "</"));
const semilla = { "meta/tablero": { version: 1, data: datos.meta } };
for (const t of datos.tarjetas) {
  const { id, ...resto } = t;
  semilla["tarjetas/" + id] = { version: 1, data: { ...resto, huellaRepo: null, editadaEnKanban: false } };
}

const fallas = [];
const comprobar = (condicion, mensaje) => { if (!condicion) fallas.push(mensaje); console.log((condicion ? "ok   " : "MAL  ") + mensaje); };

const navegador = await puppeteer.launch({
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || "/usr/bin/google-chrome",
  args: ["--no-sandbox", "--disable-gpu"],
});
const pagina = await navegador.newPage();
await pagina.evaluateOnNewDocument((semilla) => {
  const docs = JSON.parse(JSON.stringify(semilla));
  const oyentes = [];
  window.__docs = docs;
  window.__escrituras = [];
  const avisar = () => oyentes.forEach((f) => f());
  const fotoDoc = (p) => ({ exists: !!docs[p], id: p.split("/").pop(), data: () => JSON.parse(JSON.stringify(docs[p].data)) });
  const db = {
    doc: (p) => ({
      onSnapshot: (cb) => { const f = () => cb(fotoDoc(p)); oyentes.push(f); setTimeout(f, 0); return () => {}; },
      update: async (cuerpo) => {
        if (!docs[p]) throw Object.assign(new Error("no existe"), { code: "not_found" });
        docs[p] = { version: docs[p].version + 1, data: { ...docs[p].data, ...cuerpo } };
        window.__escrituras.push({ op: "update", p, cuerpo }); setTimeout(avisar, 0);
      },
      set: async (cuerpo) => {
        docs[p] = { version: docs[p] ? docs[p].version + 1 : 1, data: cuerpo };
        window.__escrituras.push({ op: "set", p, cuerpo }); setTimeout(avisar, 0);
      },
    }),
    collection: (c) => ({
      onSnapshot: (cb) => {
        const f = () => cb({ docs: Object.keys(docs).filter((p) => p.startsWith(c + "/")).sort().map(fotoDoc) });
        oyentes.push(f); setTimeout(f, 0); return () => {};
      },
    }),
  };
  window.claude = { use: async (nombre) => nombre === "db" ? db : nombre === "user" ? { can: async () => true } : null };
}, semilla);

await pagina.goto("file://" + html);
await pagina.waitForFunction(() => document.getElementById("rw").textContent.includes("En vivo"), { timeout: 10000 });
comprobar((await pagina.$eval("#rw", (e) => e.textContent)) === "En vivo · podés editar", "con base y permiso, el kanban se puede editar");

const escrituras = () => pagina.evaluate(() => window.__escrituras.length);
const abrir = (id) => pagina.evaluate((id) => { location.hash = id; }, id).then(() => pagina.waitForSelector("#p-col"));
const cambiarSelect = (sel, valor) => pagina.evaluate((sel, valor) => { const e = document.querySelector(sel); e.value = valor; e.dispatchEvent(new Event("change")); }, sel, valor);
const toast = () => pagina.$eval("#toast", (e) => e.textContent);
const columna = (id) => pagina.evaluate((id) => window.__docs["tarjetas/" + id].data.columna, id);
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

// 1. Una tarjeta con dependencias abiertas no pasa a Para empezar.
let antes = await escrituras();
await abrir("T08");
await cambiarSelect("#p-col", "para-empezar");
await espera(200);
comprobar((await toast()).startsWith("No puede pasar a Para empezar"), "T08 no pasa a Para empezar con dependencias abiertas");
comprobar((await escrituras()) === antes && (await columna("T08")) === "pendiente", "y no se escribe nada en la base");

// 2. T19: se tilda toda la lista y pasa a Hecho.
await abrir("T19");
const items = await pagina.$$eval("input[id^='p-lista-']", (l) => l.filter((e) => e.type === "checkbox").map((e) => e.id));
for (const id of items) { await pagina.click("#" + id); await espera(80); }
await cambiarSelect("#p-col", "hecho");
await espera(300);
comprobar((await columna("T19")) === "hecho", "T19 pasa a Hecho después de tildar su lista");
comprobar(await pagina.evaluate(() => window.__docs["tarjetas/T19"].data.editadaEnKanban === true), "y queda marcada para sincronizar");

// 3. T05 no pasa a Hecho con una etapa abierta; con todo tildado sí, y T14 pasa sola a Para empezar.
await abrir("T05");
await cambiarSelect("#p-col", "hecho");
await espera(200);
comprobar((await toast()).startsWith("Para pasar a Hecho"), "T05 no pasa a Hecho con la etapa Aceptado abierta");
await pagina.click("#p-etapas-3");
await espera(150);
await cambiarSelect("#p-col", "hecho");
await espera(300);
comprobar((await columna("T05")) === "hecho", "T05 pasa a Hecho con todas sus etapas tildadas");
comprobar((await columna("T14")) === "para-empezar", "T14, que solo esperaba a T05, pasa sola a Para empezar");

// 4. Las notas no aceptan guiones largos.
antes = await escrituras();
await abrir("T12");
await pagina.evaluate(() => { const a = document.getElementById("p-notas"); a.value = "Una nota " + String.fromCharCode(0x2014) + " con guion largo"; a.dispatchEvent(new Event("change")); });
await espera(200);
comprobar((await toast()).includes("guiones largos") && (await escrituras()) === antes, "una nota con guion largo se rechaza");

// 5. Se suma una tarjeta nueva en Para empezar.
await pagina.evaluate(() => { location.hash = ""; });
await pagina.keyboard.press("Escape");
await espera(150);
await pagina.click(".column[data-col='para-empezar'] .add");
await pagina.type("#nueva-titulo", "Probar la sincronización del kanban");
await pagina.click(".addform .btn.primary");
await espera(300);
const nuevas = await pagina.evaluate(() => Object.keys(window.__docs).filter((p) => p.startsWith("tarjetas/N-")));
comprobar(nuevas.length === 1, "la tarjeta nueva queda en la base con un identificador temporal");

// Volcado para la prueba de sincronización.
const volcado = await pagina.evaluate(() => {
  const d = window.__docs;
  return {
    tarjetas: Object.keys(d).filter((p) => p.startsWith("tarjetas/")).map((p) => ({ id: p.split("/")[1], version: d[p].version, data: d[p].data })),
    meta: { version: d["meta/tablero"].version, data: d["meta/tablero"].data },
  };
});
writeFileSync(salida, JSON.stringify(volcado, null, 1));
console.log("Volcado de la base simulada en " + salida);
await navegador.close();
if (fallas.length) { console.log(fallas.length + " comprobaciones fallaron."); process.exit(1); }
console.log("Todas las comprobaciones pasaron.");
