// ==UserScript==
// @name         Pure AMOLED Web - Cromite
// @namespace    https://github.com/latanvillegas/modo-patches
// @version      0.3.0
// @description  Fondo negro AMOLED y texto blanco en superficies opacas; modo forzado configurable.
// @match        http://*/*
// @match        https://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(() => {
  'use strict';

  // 'force' activa AMOLED incluso cuando Cromite no informa prefers-color-scheme: dark.
  // Cambiar a 'system' para respetar el tema del sistema.
  const MODE = 'force';
  const BG_MARK = 'data-pure-amoled-bg';
  const TEXT_MARK = 'data-pure-amoled-text';
  const STYLE_ID = 'pure-amoled-web-style';
  const SKIP = new Set(['IMG', 'VIDEO', 'CANVAS', 'SVG', 'PICTURE', 'IFRAME',
    'OBJECT', 'EMBED', 'INPUT', 'TEXTAREA', 'SELECT', 'OPTION', 'BUTTON',
    'SCRIPT', 'STYLE', 'NOSCRIPT']);
  const darkMode = matchMedia('(prefers-color-scheme: dark)');
  const pending = new Set();
  let scheduled = false;

  const active = () => MODE === 'force' || darkMode.matches;

  function rgb(value) {
    const m = /^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+))?\s*\)$/i.exec(value);
    if (!m) return null;
    const v = [Number(m[1]), Number(m[2]), Number(m[3])];
    const a = m[4] === undefined ? 1 : Number(m[4]);
    if (v.some(n => !Number.isFinite(n) || n < 0 || n > 255) || !Number.isFinite(a)) return null;
    return { v, a };
  }

  function luminance(v) {
    const linear = v.map(n => {
      const c = n / 255;
      return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
  }

  function neutral(v) {
    return Math.max(...v) - Math.min(...v) <= 34;
  }

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent =
      ':root[' + BG_MARK + '],:root [' + BG_MARK + ']{background-color:#000!important}' +
      ':root[' + TEXT_MARK + '],:root [' + TEXT_MARK + ']{color:#fff!important}' +
      'html{color-scheme:dark}';
    (document.head || document.documentElement).appendChild(style);
  }

  function inspect(el) {
    if (!(el instanceof HTMLElement) || SKIP.has(el.tagName) || el.id === STYLE_ID) return;
    // Eliminar nuestras marcas temporalmente permite leer los colores originales.
    el.removeAttribute(BG_MARK);
    el.removeAttribute(TEXT_MARK);
    if (!active()) return;
    const css = getComputedStyle(el);
    const bg = rgb(css.backgroundColor);
    const fg = rgb(css.color);
    const safeBg = css.backgroundImage === 'none' && bg && bg.a >= 0.995 && neutral(bg.v);
    const bgLum = safeBg ? luminance(bg.v) : null;
    // Fondos blancos o grises claros se oscurecen solo si el texto es oscuro.
    // Los fondos ya oscuros se convierten a negro puro.
    const convertLight = safeBg && bgLum >= 0.55 && fg && luminance(fg.v) <= 0.25;
    const convertDark = safeBg && bgLum <= 0.11;
    if (convertLight || convertDark) el.setAttribute(BG_MARK, '');
    if (convertLight || (convertDark && fg && luminance(fg.v) < 0.55)) {
      el.setAttribute(TEXT_MARK, '');
    }
  }

  function scan(root) {
    if (root instanceof HTMLElement) inspect(root);
    root.querySelectorAll?.('*').forEach(inspect);
  }

  function flush() {
    scheduled = false;
    const roots = [...pending];
    pending.clear();
    roots.filter(node => !roots.some(other => other !== node && other.contains(node)))
      .forEach(scan);
  }

  function enqueue(node) {
    if (!(node instanceof Element)) return;
    pending.add(node);
    if (!scheduled) {
      scheduled = true;
      requestAnimationFrame(flush);
    }
  }

  function refresh() {
    if (!active()) {
      document.querySelectorAll('[' + BG_MARK + '],[' + TEXT_MARK + ']')
        .forEach(el => { el.removeAttribute(BG_MARK); el.removeAttribute(TEXT_MARK); });
      return;
    }
    installStyle();
    enqueue(document.documentElement);
  }

  function start() {
    if (!document.documentElement) return;
    refresh();
    new MutationObserver(records => {
      for (const record of records) {
        if (record.type === 'attributes') enqueue(record.target);
        else record.addedNodes.forEach(enqueue);
      }
    }).observe(document.documentElement, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['class', 'style', 'bgcolor']
    });
    darkMode.addEventListener?.('change', refresh);
  }

  if (document.documentElement) start();
  else document.addEventListener('DOMContentLoaded', start, { once: true });
})();
