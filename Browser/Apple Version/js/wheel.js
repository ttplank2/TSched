/**
 * The color wheel: a drum of the ten colors that repeats as you turn it. Drag it, flick it (it coasts and then
 * settles on a row), scroll over it, or use the arrow keys; tap a color to pick it. With `renamable`, an Edit
 * Names button lets the person give the colors names of their own. The look and the drum math follow the Android app.
 */
import { COLORS, hex, nameOf as originalName } from './colors.js';
import { ROW_HEIGHT, shapeRow, wrapIndex, nearestRow, startRowFor } from './wheelmath.js';
import { el, dialog, toast } from './ui.js';

const SHOWN_ROWS = 5;
const POOL_ROWS = 10;
const FRAME_HEIGHT = ROW_HEIGHT * SHOWN_ROWS;
/** Pixels of dragging that still count as a tap. */
const TAP_SLOP = 6;
const FRICTION_PER_SECOND = 3.2;
const STOP_SPEED = 1.2;

/**
 * Shows the wheel in a popup. Options: title, selected (a color or null), accent (opens here if nothing is
 * selected), names (a ColorNames), renamable, rightLabel and onRight (the button at the right; Cancel by
 * default), onPick(color), and onNamesSaved().
 */
export function colorWheel({ title, selected, accent, names, renamable, rightLabel = 'Cancel', onRight, onPick, onNamesSaved }) {
  const list = COLORS;
  let position = startRowFor(selected, accent);
  let velocity = 0;
  let animation = null;
  let editing = false;
  let pending = {};
  let closeDialog = null;
  let leftButton = null;
  let rightButton = null;

  const frame = el('div', { class: 'wheel', tabindex: '0', role: 'listbox', 'aria-label': title });
  frame.append(el('div', { class: 'wheel-line', style: `top:${ROW_HEIGHT * 2}px` }), el('div', { class: 'wheel-line', style: `top:${ROW_HEIGHT * 3}px` }));

  const rows = Array.from({ length: POOL_ROWS }, () => {
    const swatch = el('span', { class: 'wswatch' });
    const name = el('span', { class: 'wname' });
    const input = el('input', { class: 'wfield', type: 'text', autocomplete: 'off', autocapitalize: 'words', style: 'display:none' });
    const row = el('div', { class: 'wrow', role: 'option' }, swatch, name, input);
    input.addEventListener('input', () => { pending[row.dataset.color] = input.value; });
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        input.blur();
      }
    });
    frame.append(row);
    return { row, swatch, name, input };
  });

  function render() {
    const base = Math.floor(position);
    rows.forEach((parts, i) => {
      const index = base - 4 + i;
      const fromMiddle = index - position;
      const color = list[wrapIndex(index, list.length)];
      const shape = shapeRow(fromMiddle);
      const { row, swatch, name, input } = parts;
      row.dataset.index = String(index);
      row.dataset.color = String(color);
      row.style.top = `${ROW_HEIGHT * 2 + fromMiddle * ROW_HEIGHT}px`;
      row.style.transform = `translateY(${shape.translateY}px) scale(${shape.scaleX}, ${shape.scaleY})`;
      row.style.opacity = String(shape.opacity);
      row.style.pointerEvents = shape.opacity < 0.05 ? 'none' : '';
      swatch.style.background = hex(color);
      const custom = names.hasCustomName(color);
      name.textContent = names.nameOf(color);
      // A color still on its original name is dim on a wheel that can be renamed, like a hint.
      name.classList.toggle('dim', renamable && !custom);
      name.style.display = editing ? 'none' : '';
      input.style.display = editing ? '' : 'none';
      if (editing) {
        input.placeholder = originalName(color);
        const typed = pending[color];
        if (input !== document.activeElement || typed === undefined) {
          input.value = typed ?? '';
        }
        // Only the field in the middle takes typing; a tap on the others brings that row to the middle.
        input.disabled = Math.abs(fromMiddle) >= 0.5;
      }
    });
  }

  function stop() {
    if (animation !== null) {
      cancelAnimationFrame(animation);
      animation = null;
    }
    velocity = 0;
  }

  /** Eases the wheel to a row (any whole number) over a short time, then calls `done`. */
  function animateTo(target, ms = 200, done) {
    stop();
    const from = position;
    const start = performance.now();
    const step = now => {
      const t = Math.min(1, (now - start) / ms);
      position = from + (target - from) * (1 - (1 - t) * (1 - t));
      render();
      if (t < 1) {
        animation = requestAnimationFrame(step);
      } else {
        animation = null;
        done?.();
      }
    };
    animation = requestAnimationFrame(step);
  }

  /** Lets the wheel coast with the speed it was let go at, then settle on a row. */
  function coast(initialVelocity) {
    stop();
    velocity = initialVelocity;
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
        animateTo(nearestRow(position), 160);
      }
    };
    animation = requestAnimationFrame(step);
  }

  // ---- touching, dragging and scrolling
  let drag = null;
  frame.addEventListener('pointerdown', event => {
    if (event.target.closest('.wfield:not(:disabled)')) {
      return;
    }
    if (document.activeElement?.classList?.contains('wfield')) {
      document.activeElement.blur();
    }
    stop();
    drag = { y: event.clientY, position, moved: 0, samples: [{ t: performance.now(), y: event.clientY }], target: event.target };
    frame.setPointerCapture(event.pointerId);
  });
  frame.addEventListener('pointermove', event => {
    if (!drag) {
      return;
    }
    const dy = event.clientY - drag.y;
    drag.moved = Math.max(drag.moved, Math.abs(dy));
    if (drag.moved > TAP_SLOP) {
      position = drag.position - dy / ROW_HEIGHT;
      render();
    }
    drag.samples.push({ t: performance.now(), y: event.clientY });
    drag.samples = drag.samples.filter(s => s.t > performance.now() - 120);
  });
  const release = event => {
    if (!drag) {
      return;
    }
    const finished = drag;
    drag = null;
    if (finished.moved <= TAP_SLOP) {
      const row = finished.target.closest?.('.wrow');
      if (row) {
        tapRow(row);
      }
      return;
    }
    const first = finished.samples[0];
    const seconds = first ? (performance.now() - first.t) / 1000 : 0;
    const speed = seconds > 0.01 && first ? (first.y - event.clientY) / ROW_HEIGHT / seconds : 0;
    if (Math.abs(speed) > 2) {
      coast(Math.max(-40, Math.min(40, speed)));
    } else {
      animateTo(nearestRow(position), 160);
    }
  };
  frame.addEventListener('pointerup', release);
  frame.addEventListener('pointercancel', release);

  let settleTimer = null;
  frame.addEventListener('wheel', event => {
    event.preventDefault();
    stop();
    position += (event.deltaMode === 1 ? event.deltaY * 16 : event.deltaY) / ROW_HEIGHT / 1.6;
    render();
    clearTimeout(settleTimer);
    settleTimer = setTimeout(() => animateTo(nearestRow(position), 140), 120);
  }, { passive: false });

  frame.addEventListener('keydown', event => {
    if (event.target.closest('.wfield')) {
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      animateTo(nearestRow(position) + (event.key === 'ArrowDown' ? 1 : -1), 140);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      pick(list[wrapIndex(nearestRow(position), list.length)]);
    }
  });

  function pick(color) {
    // On a touch screen the click that follows the tap would land on the page behind once the popup is gone.
    const swallow = event => {
      event.stopPropagation();
      event.preventDefault();
    };
    document.addEventListener('click', swallow, true);
    setTimeout(() => document.removeEventListener('click', swallow, true), 500);
    closeDialog();
    onPick?.(color);
  }

  function tapRow(row) {
    const index = Number(row.dataset.index);
    if (editing) {
      // Bring that row to the middle, then let the person type in it.
      animateTo(index, 220, () => {
        const field = rows.find(parts => parts.row === row)?.input;
        field?.focus();
      });
    } else {
      pick(list[wrapIndex(index, list.length)]);
    }
  }

  // ---- renaming
  function startEditing() {
    editing = true;
    pending = {};
    for (const color of COLORS) {
      pending[color] = names.hasCustomName(color) ? names.nameOf(color) : '';
    }
    closeDialog.setTitle('Edit Names');
    leftButton.textContent = 'Save';
    rightButton.textContent = 'Cancel';
    render();
  }

  function stopEditing() {
    editing = false;
    closeDialog.setTitle(title);
    leftButton.textContent = 'Edit Names';
    rightButton.textContent = rightLabel;
    render();
  }

  function saveNames() {
    if (!names.renameAll(pending)) {
      toast('Couldn’t save. Please try again.');
      return;
    }
    stopEditing();
    onNamesSaved?.();
  }

  const buttons = [];
  if (renamable) {
    buttons.push({ label: 'Edit Names', left: true, keepOpen: true, onclick: () => (editing ? saveNames() : startEditing()) });
  }
  buttons.push({
    label: rightLabel,
    keepOpen: true,
    onclick: () => {
      if (editing) {
        stopEditing();
      } else {
        closeDialog();
        onRight?.();
      }
    },
  });
  const popup = dialog(title, frame, buttons);
  leftButton = renamable ? popup.buttons[0] : null;
  rightButton = popup.buttons[renamable ? 1 : 0];
  closeDialog = () => {
    stop();
    popup();
  };
  closeDialog.setTitle = popup.setTitle;
  render();
  frame.focus({ preventScroll: true });
  return closeDialog;
}

