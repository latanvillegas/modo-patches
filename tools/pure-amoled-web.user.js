// ==UserScript==
// @name         Pure AMOLED Web - Cromite
// @namespace    https://github.com/latanvillegas/modo-patches
// @version      0.2.0
// @description  AMOLED para fondos oscuros opacos sin filtros sobre medios ni transparencias.
// @match        http://*/*
// @match        https://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(() => {
  'use strict';

  const MARK = 'data-pure-amoled-bg';
  const STYLE_ID = 'pure-amoled-web-style';
  const EXCLUDED = new Set(['IMG', 'VIDEO', 'CANVAS', 'SVG', 'PICTURE', 'IFRAME', 'OBJECT', 'EMBED']);
  const darkMode = matchMedia('(prefers-color-scheme: dark)');
  const queue = new Set();
  let scheduled = false;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const sheet = document.createElement('style');
    sheet.id = STYLE_ID;
    sheet.textContent = ':root[' + MARK + '], :root [' + MARK + '] { background-color: #000 !important; }';
    (document.head || document.documentElement).appendChild(sheet);
  }

  function darkNeutralOpaque(value) {
    // getComputedStyle() normally returns rgb()/rgba(), including the modern space-separated form.
    const m = /^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+))?\s*\)$/i.exec(value);
    if (!m) return false;
    const rgb = [Number(m[1]), Number(m[2]), Number(m[3])];
    const alpha = m[4] === undefined ? 1 : Number(m[4]);
    if (rgb.some(v => !Number.isFinite(v) || v < 0 || v > 255)) return false;
    if (!Number.isFinite(alpha) || alpha < 0.995) return false;
    if (Math.max(...rgb) > 92 || Math.max(...rgb) - Math.min(...rgb) > 34) return false;
    const linear = rgb.map(v => {
      const x = v / 255;
      return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2] <= 0.11;
  }

  function inspect(element) {
    if (!(element instanceof HTMLElement) || EXCLUDED.has(element.tagName)) return;
    if (element.id === STYLE_ID) return;

    // Read the original computed background without our CSS override.
    const had = element.hasAttribute(MARK);
    if (had) element.removeAttribute(MARK);
    if (!darkMode.matches) return;

    const style = getComputedStyle(element);
    if (style.backgroundImage === 'none' && darkNeutralOpaque(style.backgroundColor)) {
      element.setAttribute(MARK, '');
    }
  }

  function scan(root) {
    if (root instanceof HTMLElement) inspect(root);
    if (root.querySelectorAll) root.querySelectorAll('*').forEach(inspect);
  }

  function flush() {
    scheduled = false;
    const roots = [...queue];
    queue.clear();
    // Avoid rescanning descendants when an ancestor is already scheduled.
    const minimal = roots.filter(node => !roots.some(other => other !== node && other.contains?.(node)));
    minimal.forEach(scan);
  }

  function enqueue(node) {
    if (!(node instanceof Element)) return;
    queue.add(node);
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(flush);
  }

  function refresh() {
    if (!darkMode.matches) {
      document.querySelectorAll('[' + MARK + ']').forEach(node => node.removeAttribute(MARK));
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
        if (record.type === 'attributes') {
          enqueue(record.target);
        } else {
          record.addedNodes.forEach(enqueue);
        }
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
