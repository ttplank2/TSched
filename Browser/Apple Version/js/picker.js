/**
 * A number picker like Android's NumberPicker: a small drum with the chosen value between two lines and the ones
 * before and after it fading above and below. Drag it and let go to coast, scroll over it, or use the arrow keys; it
 * settles on a value and wraps around at the ends.
 */
import { el } from './ui.js';

const ROW = 40;
const HEIGHT = 120;
const FRICTION_PER_SECOND = 3.2;
const STOP_SPEED = 1.2;
const TAP_SLOP = 5;

/** @param labels the text of each choice, in order. @returns the element, with `.get()`. */
export function numberPicker({ labels, index, label, onChange }) {
  let position = index;
  let shown = index;
  let animation = null;
  let drag = null;

  const wrap = n => ((n % labels.length) + labels.length) % labels.length;
  const rows = Array.from({ length: 5 }, () => el('div', { class: 'np-row' }));
  const box = el('div', { class: 'np', tabindex: '0', role: 'spinbutton', 'aria-label': label }, ...rows);

  function render() {
    const base = Math.round(position);
    rows.forEach((row, i) => {
      const at = base - 2 + i;
      const away = at - position;
      row.textContent = labels[wrap(at)];
      row.style.transform = `translateY(${HEIGHT / 2 - ROW / 2 + away * ROW}px)`;
      row.style.opacity = String(Math.max(0, 1 - Math.abs(away) * 0.55));
      row.classList.toggle('dim', Math.abs(away) > 0.5);
    });
    const value = wrap(base);
    if (value !== shown) {
      shown = value;
      box.setAttribute('aria-valuetext', labels[value]);
      onChange?.(value);
    }
  }

  function stop() {
    if (animation !== null) {
      cancelAnimationFrame(animation);
      animation = null;
    }
  }

  function animateTo(target, ms = 180) {
    stop();
    const from = position;
    const start = performance.now();
    const step = now => {
      const t = Math.min(1, (now - start) / ms);
      position = from + (target - from) * (1 - (1 - t) * (1 - t));
      render();
      animation = t < 1 ? requestAnimationFrame(step) : null;
    };
    animation = requestAnimationFrame(step);
  }

  function coast(speed) {
    stop();
    let velocity = speed;
    let last = performance.now();
    const step = now => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      position += velocity * dt;
      velocity *= Math.exp(-FRICTION_PER_SECOND * dt);
      render();
      if (Math.abs(velocity) > STOP_SPEED) {
        animation = requestAnimationFrame(step);
      } else {
        animation = null;
        animateTo(Math.round(position), 160);
      }
    };
    animation = requestAnimationFrame(step);
  }

  box.addEventListener('pointerdown', event => {
    stop();
    drag = { y: event.clientY, start: position, moved: 0, samples: [{ t: performance.now(), y: event.clientY }] };
    box.setPointerCapture(event.pointerId);
  });
  box.addEventListener('pointermove', event => {
    if (!drag) {
      return;
    }
    const dy = event.clientY - drag.y;
    drag.moved = Math.max(drag.moved, Math.abs(dy));
    if (drag.moved > TAP_SLOP) {
      position = drag.start - dy / ROW;
      render();
    }
    drag.samples.push({ t: performance.now(), y: event.clientY });
    drag.samples = drag.samples.filter(sample => sample.t > performance.now() - 120);
  });
  const release = event => {
    if (!drag) {
      return;
    }
    const finished = drag;
    drag = null;
    if (finished.moved <= TAP_SLOP) {
      // A tap on the row above or below steps to it.
      const box2 = box.getBoundingClientRect();
      const offset = event.clientY - (box2.top + HEIGHT / 2);
      animateTo(Math.round(position) + (Math.abs(offset) < ROW / 2 ? 0 : Math.sign(offset)));
      return;
    }
    const first = finished.samples[0];
    const seconds = first ? (performance.now() - first.t) / 1000 : 0;
    const speed = seconds > 0.01 ? (first.y - event.clientY) / ROW / seconds : 0;
    if (Math.abs(speed) > 2) {
      coast(Math.max(-30, Math.min(30, speed)));
    } else {
      animateTo(Math.round(position));
    }
  };
  box.addEventListener('pointerup', release);
  box.addEventListener('pointercancel', release);

  box.addEventListener('wheel', event => {
    event.preventDefault();
    animateTo(Math.round(position) + (event.deltaY > 0 ? 1 : -1), 140);
  }, { passive: false });
  box.addEventListener('keydown', event => {
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      animateTo(Math.round(position) + (event.key === 'ArrowDown' ? 1 : -1), 140);
    }
  });

  render();
  box.get = () => shown;
  return box;
}
