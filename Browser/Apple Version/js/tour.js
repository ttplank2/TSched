/**
 * Runs a guided tour on the screen: covers the page, lights up each step's real controls, and shows a card with
 * Skip, Back and Next. A step whose controls are not showing is passed over.
 */
import { stepAfter } from './tourkinds.js';
import { el } from './ui.js';

const PAD = 4;

/** The box around every visible element matching the selectors, or null if none shows. */
function boundsOf(selectors) {
  const phone = document.getElementById('phone').getBoundingClientRect();
  let box = null;
  for (const node of document.querySelectorAll(selectors.join(','))) {
    const r = node.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) {
      continue;
    }
    box = box
      ? { left: Math.min(box.left, r.left), top: Math.min(box.top, r.top), right: Math.max(box.right, r.right), bottom: Math.max(box.bottom, r.bottom) }
      : { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
  }
  return box && { left: box.left - phone.left, top: box.top - phone.top, width: box.right - box.left, height: box.bottom - box.top, phoneHeight: phone.height };
}

/** Starts a tour (HOME or ADD from tourkinds.js); calls onFinished(completed) when it ends. Returns a function that ends it silently. */
export function startTour(kind, onFinished) {
  const phone = document.getElementById('phone');
  let index = 0;
  let direction = 1;
  let ended = false;

  const spotlight = el('div', { class: 'tour-spot' });
  const counter = el('div', { class: 'tour-counter' });
  const title = el('h3', {});
  const body = el('p', {});
  const skip = el('button', { class: 'btn pressable', type: 'button', onclick: () => finish(false) }, 'Skip');
  const back = el('button', { class: 'btn pressable', type: 'button', onclick: () => go(-1) }, 'Back');
  const next = el('button', { class: 'btn pressable', type: 'button', onclick: () => (stepAfter(kind, index, 1) === null ? finish(true) : go(1)) });
  const card = el('div', { class: 'tour-card' }, counter, title, body,
    el('div', { class: 'buttons' }, skip, el('span', { style: 'flex:1' }), back, next));
  const cover = el('div', { class: 'tour-cover' }, spotlight, card);

  function finish(completed) {
    if (ended) {
      return;
    }
    dismiss();
    onFinished(completed);
  }

  function dismiss() {
    ended = true;
    cover.remove();
    window.removeEventListener('resize', place);
  }

  function go(by) {
    direction = by;
    index = stepAfter(kind, index, by);
    show();
  }

  function show() {
    const step = kind.steps[index];
    if (step.targets.length > 0 && boundsOf(step.targets) === null) {
      // Its controls are not showing: pass over it in the direction the person was going.
      const further = stepAfter(kind, index, direction);
      if (further === null) {
        direction === 1 ? finish(true) : (direction = 1, go(1));
      } else {
        index = further;
        show();
      }
      return;
    }
    counter.textContent = `${index + 1} of ${kind.steps.length}`;
    title.textContent = step.title;
    body.textContent = step.body;
    back.style.visibility = index === 0 ? 'hidden' : '';
    next.textContent = index === kind.steps.length - 1 ? kind.lastLabel : 'Next';
    place();
  }

  /** Puts the light on the step's controls and the card on whichever side has more room. */
  function place() {
    const step = kind.steps[index];
    const box = step.targets.length > 0 ? boundsOf(step.targets) : null;
    if (!box) {
      spotlight.style.cssText = 'display:none';
      cover.style.background = 'rgba(0, 0, 0, 0.72)';
      card.style.cssText = 'top:50%;transform:translateY(-50%)';
      return;
    }
    cover.style.background = '';
    spotlight.style.cssText = `left:${box.left - PAD}px;top:${box.top - PAD}px;width:${box.width + 2 * PAD}px;height:${box.height + 2 * PAD}px`;
    const roomBelow = box.phoneHeight - (box.top + box.height);
    if (Math.max(roomBelow, box.top) < 230) {
      card.style.cssText = 'bottom:16px';
      return;
    }
    card.style.cssText = roomBelow > box.top ? `top:${box.top + box.height + 16}px` : `bottom:${box.phoneHeight - box.top + 16}px`;
  }

  phone.append(cover);
  window.addEventListener('resize', place);
  show();
  return dismiss;
}
