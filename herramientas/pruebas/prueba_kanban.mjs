// Prueba del kanban de solo lectura, en un Chrome sin ventana (ADR 0017).
//
// Genera kanban/index.html desde tablero.md, lo abre y comprueba que cada columna tenga sus tarjetas,
// que las fichas y los enlaces con #T.. anden, que no haya nada para editar y que no haya errores
// en la consola.
//
// Uso:  NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_kanban.mjs
// Chrome: se toma de PUPPETEER_EXECUTABLE_PATH o de /usr/bin/google-chrome.

import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const require = createRequire(import.meta.url);
const puppeteer = require("puppeteer");
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const archivo = path.join(raiz, "kanban", "index.html");

execFileSync("python3", [path.join(raiz, "herramientas", "generar_kanban.py")], { stdio: "ignore" });
const texto = readFileSync(archivo, "utf8");
const datos = JSON.parse(texto.match(/<script type="application\/json" id="datos">([\s\S]*?)<\/script>/)[1].replace(/<\\\//g, "</"));

const fallas = [];
const comprobar = (condicion, mensaje) => { if (!condicion) fallas.push(mensaje); console.log((condicion ? "ok   " : "MAL  ") + mensaje); };

const navegador = await puppeteer.launch({
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || "/usr/bin/google-chrome",
  args: ["--no-sandbox", "--disable-gpu"],
});
const pagina = await navegador.newPage();
await pagina.setViewport({ width: 1500, height: 950 });
const errores = [];
pagina.on("pageerror", (e) => errores.push(e.message));
pagina.on("console", (m) => { if (m.type() === "error") errores.push(m.text()); });
await pagina.goto(pathToFileURL(archivo).href, { waitUntil: "load" });
const texto$ = (sel) => pagina.$eval(sel, (e) => e.textContent);

// Las columnas y sus tarjetas, como en tablero.md.
for (const col of datos.meta.columnas) {
  const esperadas = datos.tarjetas.filter((t) => t.columna === col.id).length;
  const dibujadas = await pagina.$$eval(`.column[data-col="${col.id}"] .card`, (cs) => cs.length);
  comprobar(dibujadas === esperadas, `${col.nombre} dibuja sus tarjetas de tablero.md (${esperadas})`);
}
comprobar((await texto$("#rw")).includes("solo lectura"), "el encabezado dice que es de solo lectura");

// Nada para editar.
comprobar((await pagina.$$("[draggable=true]")).length === 0, "ninguna tarjeta se puede arrastrar");
comprobar((await pagina.$$("input, textarea, select")).length === 0, "no hay campos para escribir");
const primera = datos.tarjetas.find((t) => t.columna === "hecho" && (t.registro || []).length);
await pagina.click(`.card[data-id="${primera.id}"]`);
comprobar((await texto$("#p-title")) === primera.titulo, `un clic en ${primera.id} abre su ficha`);
comprobar((await pagina.evaluate(() => location.hash)) === "#" + primera.id, `y la dirección termina en #${primera.id}`);
comprobar((await pagina.$$("#panel input, #panel textarea, #panel select")).length === 0, "la ficha no tiene campos para editar");
comprobar((await texto$("#panel")).includes(primera.registro[primera.registro.length - 1].texto.slice(0, 30)), "la ficha muestra el registro");
await pagina.keyboard.press("Escape");
comprobar(await pagina.$eval("#overlay", (o) => o.hidden), "Escape cierra la ficha");

// Un enlace directo a una tarjeta.
const conDependencias = datos.tarjetas.find((t) => t.dependeDe);
await pagina.goto(pathToFileURL(archivo).href + "#" + conDependencias.id, { waitUntil: "load" });
comprobar((await texto$("#p-title")) === conDependencias.titulo, `#${conDependencias.id} abre esa tarjeta`);
comprobar((await texto$("#panel")).includes("Depende de"), "y muestra de qué depende");

// Los pasos del crédito de la API: el botón es propio de El Parte, y en otro proyecto puede no estar.
await pagina.keyboard.press("Escape");
if (await pagina.$("#btn-credito")) {
  await pagina.click("#btn-credito");
  comprobar((await texto$("#panel")).includes("Buy credits"), "el botón del crédito muestra los pasos");
}

comprobar(errores.length === 0, "sin errores en la consola" + (errores.length ? ": " + errores.join(" | ") : ""));
await navegador.close();
if (fallas.length) { console.log(`\n${fallas.length} fallas`); process.exit(1); }
console.log("\nTodo bien.");
