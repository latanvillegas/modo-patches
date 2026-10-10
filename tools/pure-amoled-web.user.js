// ==UserScript==
// @name         Pure AMOLED Web - Cromite
// @namespace    https://github.com/latanvillegas/modo-patches
// @version      0.4.0
// @description  Negro puro en modo oscuro y blanco puro en modo claro, según el sistema.
// @match        http://*/*
// @match        https://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(() => {
  'use strict';

  // Seguir automáticamente el tema comunicado por el navegador/sistema.
  const MODE = 'system';
  const BG_MARK = 'data-pure-amoled-bg';
  const TEXT_MARK = 'data-pure-amoled-text';
  const STYLE_ID = 'pure-amoled-web-style';
  const SKIP = new Set(['IMG', 'VIDEO', 'CANVAS', 'SVG', 'PICTURE', 'IFRAME',
    'OBJECT', 'EMBED', 'INPUT', 'TEXTAREA', 'SELECT', 'OPTION', 'BUTTON',
    'SCRIPT', 'STYLE', 'NOSCRIPT']);
  const darkMode = matchMedia('(prefers-color-scheme: dark)');
  const pending = new Set();
  let scheduled = false;

  const isDark = () => darkMode.matches;

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
      ':root[' + BG_MARK + '="dark"],:root [' + BG_MARK + '="dark"]{background-color:#000!important}' +
      ':root[' + BG_MARK + '="light"],:root [' + BG_MARK + '="light"]{background-color:#fff!important}' +
      ':root[' + TEXT_MARK + '],:root [' + TEXT_MARK + ']{color:#fff!important}';
    (document.head || document.documentElement).appendChild(style);
  }

  function inspect(el) {
    if (!(el instanceof HTMLElement) || SKIP.has(el.tagName) || el.id === STYLE_ID) return;
    // Leer los colores originales sin nuestras reglas.
    el.removeAttribute(BG_MARK);
    el.removeAttribute(TEXT_MARK);
    const css = getComputedStyle(el);
    const bg = rgb(css.backgroundColor);
    const fg = rgb(css.color);
    const safeBg = css.backgroundImage === 'none' && bg && bg.a >= 0.995 && neutral(bg.v);
    if (!safeBg) return;

    const bgLum = luminance(bg.v);
    if (isDark()) {
      const convertLight = bgLum >= 0.55 && fg && luminance(fg.v) <= 0.25;
      const convertDark = bgLum <= 0.11;
      if (convertLight || convertDark) el.setAttribute(BG_MARK, 'dark');
      if (convertLight || (convertDark && fg && luminance(fg.v) < 0.55)) {
        el.setAttribute(TEXT_MARK, '');
      }
    } else {
      // En modo claro, conservar el texto original y blanquear solo fondos claros.
      // No blanquear superficies oscuras (p. ej., reproductores y paneles).
      if (bgLum >= 0.70) el.setAttribute(BG_MARK, 'light');
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
    document.querySelectorAll('[' + BG_MARK + '],[' + TEXT_MARK + ']')
      .forEach(el => { el.removeAttribute(BG_MARK); el.removeAttribute(TEXT_MARK); });
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
