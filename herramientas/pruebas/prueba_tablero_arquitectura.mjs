// Prueba de la página del tablero de arquitectura, en un Chrome sin ventana.
//
// Genera las páginas desde sistema.json, abre docs/tablero-de-arquitectura/index.html y comprueba que
// dibuje todo el modelo, que las fichas, el menú, los enlaces con ?objeto= y los recorridos anden, y
// que no haya errores en la consola. En cada página, además, mide el dibujo: falla si dos cajas se
// pisan, si una caja se sale de su franja, si un texto se sale de su caja o si un rótulo de los que se
// ven sin pasar el mouse pisa una caja u otro rótulo, y avisa de los textos cortados. Deja una captura de cada vista para mirarla
// (ADR 0017), en la carpeta de la variable CAPTURAS o en tmp/capturas/.
//
// Uso:  NODE_PATH=<carpeta con puppeteer> node herramientas/pruebas/prueba_tablero_arquitectura.mjs
// Chrome: se toma de PUPPETEER_EXECUTABLE_PATH o de /usr/bin/google-chrome.

import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const require = createRequire(import.meta.url);
const puppeteer = require("puppeteer");
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const archivo = path.join(raiz, "docs", "tablero-de-arquitectura", "index.html");
const url = pathToFileURL(archivo).href;
const sistema = JSON.parse(readFileSync(path.join(raiz, "docs", "tablero-de-arquitectura", "sistema.json"), "utf8"));
const capturas = process.env.CAPTURAS || path.join(raiz, "tmp", "capturas");
execFileSync("python3", [path.join(raiz, "herramientas", "generar_tablero_arquitectura.py")], { stdio: "ignore" });

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

// 8. La vista del usuario: el mismo modelo, con sus objetos, sus recorridos y lo que le queda afuera.
for (const v of sistema.vistas) {
  const urlVista = pathToFileURL(path.join(raiz, "docs", "tablero-de-arquitectura", v.archivo)).href;
  await pagina.goto(urlVista);
  comprobar((await pagina.$$("g.nodo")).length === Object.keys(v.posiciones).length, `la vista ${v.id} dibuja sus ${Object.keys(v.posiciones).length} objetos`);
  comprobar((await texto("#titulo")).startsWith(v.titulo), `la vista ${v.id} lleva su título`);
  comprobar((await pagina.$eval("#vistas a.activa", (a) => a.getAttribute("href"))) === v.archivo, `el botón de la vista ${v.id} queda marcado`);
  for (const rid of v.recorridos) {
    const n = sistema.recorridos.find((r) => r.id === rid).pasos.length;
    const ok = await pagina.evaluate((rid, n) => { empezar(rid, 0); for (let i = 1; i < n; i++) { siguiente(); }
      return document.getElementById("pie").textContent.includes("parada " + n + " de " + n); }, rid, n);
    comprobar(ok, `en la vista ${v.id}, el recorrido ${rid} llega a su última parada`);
  }
  await pagina.keyboard.press("Escape");
  await pagina.$eval('g.nodo[data-id="inicio"]', (g) => g.dispatchEvent(new MouseEvent("click", { bubbles: true })));
  comprobar((await texto("#panel-cuerpo")).includes("Fuera de esta vista"), `en la vista ${v.id}, la ficha dice lo que queda afuera`);
}

// 9. El dibujo de cada página: nada se pisa, y una captura para mirarla.
mkdirSync(capturas, { recursive: true });
const paginas = [{ archivo: "index.html", titulo: "todo el sistema" }].concat(sistema.vistas.map((v) => ({ archivo: v.archivo, titulo: "la vista " + v.id })));
for (const pg of paginas) {
  await pagina.setViewport({ width: 2400, height: 1400 });
  await pagina.goto(pathToFileURL(path.join(raiz, "docs", "tablero-de-arquitectura", pg.archivo)).href);
  const d = await pagina.evaluate(() => {
    const pisa = (a, b) => a.x < b.x + b.w - 1 && b.x < a.x + a.w - 1 && a.y < b.y + b.h - 1 && b.y < a.y + a.h - 1;
    const rect = (r) => ({ x: +r.getAttribute("x"), y: +r.getAttribute("y"), w: +r.getAttribute("width"), h: +r.getAttribute("height") });
    const cajas = S.nodos.map((n) => ({ id: n.id, x: n.x, y: n.y, w: NW, h: NH }));
    const franjas = [...document.querySelectorAll("g.franja > rect")].map(rect);
    const rotulos = [...document.querySelectorAll("g.flecha")]
      .filter((g) => getComputedStyle(g).display !== "none" && getComputedStyle(g.querySelector("g.rotulo")).display !== "none")
      .map((g) => Object.assign({ id: g.dataset.id }, rect(g.querySelector("g.rotulo rect"))));
    const r = { cajas: [], fuera: [], sobreCaja: [], sobreRotulo: [], cortados: [], salen: [] };
    document.querySelectorAll("g.nodo text.titulo, g.nodo text.sub").forEach((t) => {
      if (+t.getAttribute("x") + t.getComputedTextLength() > NW - 6) { r.salen.push(t.closest("g.nodo").dataset.id + ": " + t.textContent); }
    });
    cajas.forEach((a, i) => cajas.slice(i + 1).forEach((b) => { if (pisa(a, b)) { r.cajas.push(a.id + " y " + b.id); } }));
    cajas.forEach((c) => { if (!franjas.some((f) => c.x >= f.x && c.y >= f.y && c.x + c.w <= f.x + f.w && c.y + c.h <= f.y + f.h)) { r.fuera.push(c.id); } });
    rotulos.forEach((o) => cajas.forEach((c) => { if (pisa(o, c)) { r.sobreCaja.push(o.id + " sobre " + c.id); } }));
    rotulos.forEach((a, i) => rotulos.slice(i + 1).forEach((b) => { if (pisa(a, b)) { r.sobreRotulo.push(a.id + " y " + b.id); } }));
    r.cortados = [...document.querySelectorAll("g.nodo text")].filter((t) => t.textContent.endsWith("…")).map((t) => t.textContent);
    return r;
  });
  const lista = (xs) => (xs.length ? ": " + xs.join("; ") : "");
  comprobar(d.cajas.length === 0, `en ${pg.titulo}, ninguna caja pisa otra` + lista(d.cajas));
  comprobar(d.fuera.length === 0, `en ${pg.titulo}, ninguna caja se sale de su franja` + lista(d.fuera));
  comprobar(d.salen.length === 0, `en ${pg.titulo}, ningún texto se sale de su caja` + lista(d.salen));
  comprobar(d.sobreCaja.length === 0, `en ${pg.titulo}, ningún rótulo pisa una caja` + lista(d.sobreCaja));
  comprobar(d.sobreRotulo.length === 0, `en ${pg.titulo}, ningún rótulo pisa otro` + lista(d.sobreRotulo));
  if (d.cortados.length) { console.log(`aviso: en ${pg.titulo}, ${d.cortados.length} textos de cajas quedan cortados: ${d.cortados.join("; ")}`); }
  await pagina.click("#btn-ajustar");
  await espera(400);
  const destino = path.join(capturas, pg.archivo.replace(/\.html$/, ".png"));
  await pagina.screenshot({ path: destino });
  console.log(`captura de ${pg.titulo}: ${path.relative(raiz, destino)}`);
}

comprobar(errores.length === 0, "sin errores en la consola" + (errores.length ? ": " + errores.join(" | ") : ""));
await navegador.close();
if (fallas.length) { console.log(`\n${fallas.length} fallas`); process.exit(1); }
console.log("\nTodo bien.");
