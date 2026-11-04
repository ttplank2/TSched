/**
 * A slider like the Android app's: a thin track with the part up to the thumb in the accent color, a dot for every
 * step along it, and a round thumb that glows while held. Drag it, tap the track, or use the arrow keys.
 */
import { el } from './ui.js';

/** @param min,max the first and last value, in steps of 1. @returns the element, with `.get()`. */
export function slider({ min, max, value, label, onChange }) {
  let current = value;
  const count = max - min;

  const active = el('div', { class: 'sl-active' });
  const ticks = el('div', { class: 'sl-ticks' }, Array.from({ length: count + 1 }, (_, i) => el('span', { 'data-step': i, style: `left:calc(8px + (100% - 16px) * ${i / count})` })));
  const thumb = el('div', { class: 'sl-thumb' });
  const track = el('div', { class: 'sl-track' }, el('div', { class: 'sl-inactive' }), active, ticks, thumb);
  const box = el('div', { class: 'sl', tabindex: '0', role: 'slider', 'aria-label': label, 'aria-valuemin': String(min), 'aria-valuemax': String(max) }, track);

  function show() {
    box.style.setProperty('--f', String((current - min) / count));
    for (const tick of ticks.children) {
      const step = Number(tick.dataset.step);
      tick.classList.toggle('on', step < current - min);
      tick.classList.toggle('under', step === current - min);
    }
    box.setAttribute('aria-valuenow', String(current));
  }

  function set(next) {
    const clamped = Math.max(min, Math.min(max, Math.round(next)));
    if (clamped !== current) {
      current = clamped;
      show();
      onChange?.(current);
    }
  }

  const fromPointer = event => {
    const bounds = track.getBoundingClientRect();
    set(min + ((event.clientX - bounds.left) / bounds.width) * count);
  };
  box.addEventListener('pointerdown', event => {
    box.setPointerCapture(event.pointerId);
    box.classList.add('held');
    box.focus({ preventScroll: true });
    fromPointer(event);
  });
  box.addEventListener('pointermove', event => box.classList.contains('held') && fromPointer(event));
  const release = () => box.classList.remove('held');
  box.addEventListener('pointerup', release);
  box.addEventListener('pointercancel', release);
  box.addEventListener('keydown', event => {
    const by = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 }[event.key];
    if (by) {
      event.preventDefault();
      set(current + by);
    }
  });

  show();
  box.get = () => current;
  return box;
}
