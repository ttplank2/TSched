/** Small helpers the screens share: building elements, the top bar, popups and the toast. */
import { clock } from './time.js';
import { hex } from './colors.js';

/** Makes an element: el('div', { class: 'x', onclick: fn }, child, 'text'). */
export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) {
      continue;
    }
    if (key.startsWith('on')) {
      node.addEventListener(key.slice(2), value);
    } else if (key === 'class') {
      node.className = value;
    } else if (key === 'style') {
      node.style.cssText = value;
    } else {
      node.setAttribute(key, value === true ? '' : value);
    }
  }
  for (const child of children.flat()) {
    if (child != null && child !== false) {
      node.append(child);
    }
  }
  return node;
}

const BACK_ARROW = '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>';
const GEAR = '<svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z"/></svg>';
const PLUS = '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>';
const BELL_ON = '<svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/></svg>';
const BELL_OFF = '<svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor"><path d="M20 18.69 7.84 6.14 5.27 3.49 4 4.76l2.8 2.8v.01c-.52.99-.8 2.16-.8 3.42v5l-2 2v1h13.73l2 2L21 21.72 20 18.69zM12 22c1.11 0 2-.89 2-2h-4c0 1.11.89 2 2 2zm6-7.32V11c0-3.08-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68c-.15.03-.29.08-.42.12-.1.03-.2.07-.3.11h-.01c-.01 0-.01 0-.02.01-.23.09-.46.2-.68.31 0 0-.01 0-.01.01L18 14.68z"/></svg>';

export const icons = { PLUS, BELL_ON, BELL_OFF };

const bar = () => document.getElementById('bar');

/** The calendar's top bar: the TSched logo, Filter, Snap to Day, and the gear. */
export function calendarBar({ onFilter, onSnap, onSettings, filterName }) {
  bar().replaceChildren(
    el('span', { class: 'logo' }, 'TSched'),
    el('button', {
      class: 'bar-button pressable',
      id: 'filter-button',
      style: filterName ? `border-color:${filterName.color}` : '',
      onclick: onFilter,
    }, filterName ? filterName.name : 'Filter'),
    el('button', { class: 'bar-button pressable', id: 'snap-button', onclick: onSnap }, 'Snap to Day'),
    (() => {
      const gear = el('button', { class: 'gear pressable', 'aria-label': 'Settings', onclick: onSettings });
      gear.innerHTML = GEAR;
      return gear;
    })(),
  );
}

/** A screen's top bar: a back arrow and the title. */
export function titleBar(title, onBack) {
  const back = el('button', { class: 'back pressable', 'aria-label': 'Back', onclick: onBack });
  back.innerHTML = BACK_ARROW;
  bar().replaceChildren(back, el('h1', { class: 'title' }, title));
}

let toastTimer = null;

export function toast(message) {
  const node = document.getElementById('toast');
  node.textContent = message;
  node.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => node.classList.remove('show'), 2600);
}

/**
 * Shows a popup with a title (none if empty), a body, and buttons [{ label, onclick, keepOpen, left }]; a `left`
 * button sits at the left edge (like Edit Names). Returns a function that closes it; the function also has
 * `.setTitle(text)` and `.buttons` (the button elements, in order) so a popup can change as it is used.
 */
export function dialog(title, body, buttons) {
  const root = document.getElementById('dialogs');
  // Only one popup at a time: opening another (a quick second tap, say) replaces the first.
  root.replaceChildren();
  const close = () => backdrop.remove();
  const heading = title ? el('h3', {}, title) : null;
  const buttonElements = buttons.map(button => el('button', {
    class: 'btn pressable' + (button.left ? ' left' : ''),
    type: 'button',
    onclick: () => {
      if (!button.keepOpen) {
        close();
      }
      button.onclick?.();
    },
  }, button.label));
  const box = el('div', { class: 'dialog', role: 'dialog', 'aria-label': title || 'Popup' },
    heading, body, el('div', { class: 'buttons' }, buttonElements));
  const backdrop = el('div', { class: 'backdrop', onclick: event => event.target === backdrop && close() }, box);
  root.append(backdrop);
  close.setTitle = text => { if (heading) heading.textContent = text; };
  close.buttons = buttonElements;
  close.box = box;
  return close;
}

/** A popup to pick one of several options (a radio list). Calls onPick with the option's value. */
export function chooser(title, options, chosenValue, onPick, extraButtons = []) {
  let close;
  const list = el('div', {}, options.map(option => el('button', {
    class: 'option pressable' + (option.value === chosenValue ? ' chosen' : ''),
    onclick: () => {
      close();
      onPick(option.value);
    },
  }, option.color == null ? el('span', { class: 'tick' }) : el('span', { class: 'swatch', style: `background:${hex(option.color)}` }),
  option.label)));
  close = dialog(title, list, [...extraButtons, { label: 'Cancel' }]);
  return close;
}

export { clock };
