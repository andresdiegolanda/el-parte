// Prueba de la página del tablero de arquitectura, en un Chrome sin ventana.
//
// Abre docs/tablero-de-arquitectura/index.html y comprueba que dibuje todo el modelo, que las fichas,
// el menú, los enlaces con ?objeto= y los recorridos anden, y que no haya errores en la consola.
//
// Uso:  NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_tablero_arquitectura.mjs
// Chrome: se toma de PUPPETEER_EXECUTABLE_PATH o de /usr/bin/google-chrome.

import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const require = createRequire(import.meta.url);
const puppeteer = require("puppeteer");
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const archivo = path.join(raiz, "docs", "tablero-de-arquitectura", "index.html");
const url = pathToFileURL(archivo).href;
const sistema = JSON.parse(readFileSync(path.join(raiz, "docs", "tablero-de-arquitectura", "sistema.json"), "utf8"));

const fallas = [];
const comprobar = (condicion, mensaje) => { if (!condicion) fallas.push(mensaje); console.log((condicion ? "ok   " : "MAL  ") + mensaje); };

const navegador = await puppeteer.launch({
  executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || "/usr/bin/google-chrome",
  args: ["--no-sandbox", "--disable-gpu"],
});
const pagina = await navegador.newPage();
await pagina.setViewport({ width: 1600, height: 950 });
const errores = [];
pagina.on("pageerror", (e) => errores.push(e.message));
pagina.on("console", (m) => { if (m.type() === "error") errores.push(m.text()); });
const espera = (ms) => new Promise((r) => setTimeout(r, ms));
const texto = (sel) => pagina.$eval(sel, (e) => e.textContent);

// 1. Dibuja todo el modelo, sin coordenadas rotas.
await pagina.goto(url);
comprobar((await pagina.$$("g.nodo")).length === sistema.nodos.length, `dibuja los ${sistema.nodos.length} nodos`);
comprobar((await pagina.$$("g.flecha")).length === sistema.flechas.length, `dibuja las ${sistema.flechas.length} flechas`);
const rotas = await pagina.$$eval("g.flecha path.trazo", (ps) => ps.filter((p) => /NaN|undefined/.test(p.getAttribute("d"))).length);
comprobar(rotas === 0, "ninguna flecha tiene coordenadas rotas");
comprobar((await texto("#panel-titulo")) === "Cómo se usa", "arranca con la ayuda en el panel");
comprobar((await pagina.$$eval("g.flecha.lee", (fs) => fs.filter((f) => getComputedStyle(f).display !== "none").length)) === 0, "las lecturas arrancan ocultas");

// 2. Un clic abre la ficha y deja el objeto en la dirección.
await pagina.$eval('g.nodo[data-id="refutador"]', (g) => g.dispatchEvent(new MouseEvent("click", { bubbles: true })));
comprobar((await texto("#panel-titulo")) === "Refutador", "un clic en el refutador abre su ficha");
comprobar((await pagina.evaluate(() => location.search)).includes("objeto=refutador"), "la dirección lleva el objeto elegido");
comprobar((await texto("#panel-cuerpo")).includes("arquitectura: refutador"), "la ficha da la referencia para citarlo");
await pagina.$eval('g.nodo[data-id="archivista"]', (g) => g.dispatchEvent(new MouseEvent("click", { bubbles: true })));
const visibles = await pagina.$$eval("g.flecha.lee.foco", (fs) => fs.filter((f) => getComputedStyle(f).display !== "none").length);
comprobar(visibles === 4, "al elegir el archivista se ven sus cuatro lecturas");

// 3. El botón derecho abre el menú.
await pagina.$eval('g.nodo[data-id="kafka/candidatos"]', (g) => {
  const r = g.getBoundingClientRect();
  g.dispatchEvent(new MouseEvent("contextmenu", { bubbles: true, clientX: r.x + 10, clientY: r.y + 10 }));
});
comprobar(!(await pagina.$eval("#menu", (m) => m.classList.contains("oculto"))), "el botón derecho abre el menú");
comprobar((await texto("#menu")).includes("Copiar la referencia"), "el menú ofrece copiar la referencia");

// 4. Una flecha abre su ficha.
await pagina.$eval('g.flecha[data-id="refutador>modelo"]', (g) => g.dispatchEvent(new MouseEvent("click", { bubbles: true })));
comprobar((await texto("#panel-titulo")) === "Refutador → Servicio del modelo", "un clic en una flecha abre su ficha");

// 5. ?objeto= abre la página en ese objeto.
await pagina.goto(url + "?objeto=" + encodeURIComponent("base/hechos"));
comprobar((await texto("#panel-titulo")) === "hechos", "?objeto=base/hechos abre la ficha de la tabla");

// 6. Los recorridos: cada parada de cada recorrido, sin errores; las flechas del teclado avanzan.
await pagina.goto(url + "?recorrido=noche&paso=3");
comprobar((await texto("#pie")).includes("parada 3 de"), "?recorrido=noche&paso=3 abre esa parada");
await pagina.keyboard.press("ArrowRight");
await espera(50);
comprobar((await texto("#pie")).includes("parada 4 de"), "la flecha derecha avanza una parada");
await espera(900);
comprobar((await pagina.evaluate(() => cam.k)) >= 0.89, "con Seguir encendido, la cámara se acerca a la parada");
for (const r of sistema.recorridos) {
  const ok = await pagina.evaluate((rid, n) => {
    empezar(rid, 0);
    for (let i = 1; i < n; i++) { siguiente(); }
    return document.getElementById("pie").textContent.includes("parada " + n + " de " + n);
  }, r.id, r.pasos.length);
  comprobar(ok, `el recorrido ${r.id} llega a su última parada`);
}
await pagina.keyboard.press("Escape");
comprobar((await texto("#pie")).includes("Sin recorrido"), "Esc sale del recorrido");

// 7. Los botones del encabezado.
await pagina.click("#btn-lecturas");
comprobar((await pagina.$$eval("g.flecha.lee", (fs) => fs.filter((f) => getComputedStyle(f).display !== "none").length)) > 0, "Lecturas muestra las lecturas");
await pagina.click("#btn-disenado");
comprobar((await pagina.$$eval("g.nodo.disenado", (ns) => ns.filter((n) => getComputedStyle(n).display !== "none").length)) === 0, "Diseñado oculta lo diseñado");
await pagina.click("#btn-cambios");
comprobar((await pagina.$$("g.nodo.cambio")).length > 0, "Cambios resalta lo último que cambió");
await pagina.click("#btn-leyenda");
comprobar((await texto("#panel-titulo")) === "Leyenda", "Leyenda abre la leyenda");

comprobar(errores.length === 0, "sin errores en la consola" + (errores.length ? ": " + errores.join(" | ") : ""));
await navegador.close();
if (fallas.length) { console.log(`\n${fallas.length} fallas`); process.exit(1); }
console.log("\nTodo bien.");
