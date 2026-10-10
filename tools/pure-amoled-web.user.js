// ==UserScript==
// @name         Pure AMOLED Web - Cromite
// @namespace    https://github.com/latanvillegas/modo-patches
// @version      0.1.0
// @description  Convierte fondos oscuros opacos en negro AMOLED conservando fondos claros, imágenes y transparencias.
// @match        http://*/*
// @match        https://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(() => {
  'use strict';

  const ATTRIBUTE = 'data-pure-amoled-bg';
  const STYLE_ID = 'pure-amoled-web-style';
  const DARK_LUMINANCE_MAX = 0.11;
  const MAX_CHANNEL = 92;
  const MAX_CHROMA = 34;
  const EXCLUDED = new Set(['IMG', 'VIDEO', 'CANVAS', 'SVG', 'PICTURE', 'IFRAME', 'OBJECT', 'EMBED']);
  const pending = new Set();
  let scheduled = false;

  // Only operate when the page/browser explicitly prefers dark colors.
  const darkQuery = matchMedia('(prefers-color-scheme: dark)');
  const css = `
    :root[${ATTRIBUTE}], :root [${ATTRIBUTE}] {
      background-color: #000 !important;
    }
  `;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = css;
    (document.head || document.documentElement)?.appendChild(style);
  }

  function isDarkNeutral(color) {
    const match = color.match(/^rgba?\\(\\s*([\\d.]+)[, ]+([\\d.]+)[, ]+([\\d.]+)(?:[, /]+([\\d.]+))?\\s*\\)$/i);
    if (!match) return false;
    const rgb = match.slice(1, 4).map(Number);
    const alpha = match[4] === undefined ? 1 : Number(match[4]);
    if (rgb.some(v => !Number.isFinite(v) || v < 0 || v > 255)) return false;
    if (alpha < 0.995) return false; // Preserve transparency.
    if (Math.max(...rgb) > MAX_CHANNEL || Math.max(...rgb) - Math.min(...rgb) > MAX_CHROMA) return false;
    const linear = rgb.map(v => {
      const s = v / 255;
      return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    });
    return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722 <= DARK_LUMINANCE_MAX;
  }

  function eligible(element) {
    if (!(element instanceof HTMLElement) || EXCLUDED.has(element.tagName)) return false;
    if (element.id === STYLE_ID) return false;
    const style = getComputedStyle(element);
    if (style.backgroundImage !== 'none') return false;
    return isDarkNeutral(style.backgroundColor);
  }

  function inspect(element) {
    if (!(element instanceof HTMLElement)) return;
    // Temporarily remove our override before measuring the original color.
    const had = element.hasAttribute(ATTRIBUTE);
    if (had) element.removeAttribute(ATTRIBUTE);
    const shouldApply = darkQuery.matches && eligible(element);
    if (shouldApply) element.setAttribute(ATTRIBUTE, '');
  }

  function scan(root) {
    if (root instanceof HTMLElement) inspect(root);
    if (!root.querySelectorAll) return;
    root.querySelectorAll('*').forEach(inspect);
  }

  function flush() {
    scheduled = false;
    const nodes = [...pending];
    pending.clear();
    nodes.forEach(scan);
  }

  function queue(root) {
    if (!root) return;
    pending.add(root);
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(flush);
  }

  function clear() {
    document.querySelectorAll('[' + ATTRIBUTE + ']').forEach(el => el.removeAttribute(ATTRIBUTE));
  }

  function refresh() {
    if (!darkQuery.matches) {
      clear();
      return;
    }
    installStyle();
    queue(document.documentElement);
  }

  function start() {
    if (!document.documentElement) return;
    refresh();
    new MutationObserver(records => {
      for (const record of records) {
        if (record.type === 'attributes') {
          if (record.attributeName === ATTRIBUTE) continue;
          queue(record.target);
        } else {
          record.addedNodes.forEach(node => {
            if (node.nodeType === Node.ELEMENT_NODE) queue(node);
          });
        }
      }
    }).observe(document.documentElement, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['class', 'style', 'bgcolor', ATTRIBUTE],
    });
    darkQuery.addEventListener?.('change', refresh);
  }

  if (document.documentElement) start();
  else document.addEventListener('DOMContentLoaded', start, { once: true });
})();
