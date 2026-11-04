/**
 * The round clock for picking a time, like the Android app's: big hour and minute at the top with AM and PM beside
 * them, a dial to tap or drag (the hour first, then the minute), and a keyboard button to type the time instead.
 */
import { angleOf, hourFromAngle, minuteFromAngle, dialPosition, to24, from24, parseTyped } from './dial.js';
import { el, dialog } from './ui.js';

const DIAL = 218;
const MIDDLE = DIAL / 2;
const NUMBER_RADIUS = 88;
const SELECTOR = 39;

const KEYBOARD = '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M20 5H4c-1.1 0-1.99.9-1.99 2L2 17c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0-3H5V8h2v2zm9 7H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2zm0-3h-2V8h2v2z"/></svg>';

/** Shows the clock for a time (minutes after midnight); calls onPick with the chosen minutes after midnight. */
export function timePicker(title, minute, onPick) {
  const start = from24(Math.floor(minute / 60));
  const state = { hour: start.hour, minute: minute % 60, period: start.period, mode: 'hour', typing: false };

  const hourBox = el('button', { class: 'tbox pressable', type: 'button', 'aria-label': 'Hour', onclick: () => setMode('hour') });
  const minuteBox = el('button', { class: 'tbox pressable', type: 'button', 'aria-label': 'Minute', onclick: () => setMode('minute') });
  const am = el('button', { class: 'tperiod pressable', type: 'button', onclick: () => setPeriod('AM') }, 'AM');
  const pm = el('button', { class: 'tperiod pressable', type: 'button', onclick: () => setPeriod('PM') }, 'PM');
  const head = el('div', { class: 'thead' }, hourBox, el('span', { class: 'tcolon' }, ':'), minuteBox, el('div', { class: 'tperiods' }, am, pm));

  const dial = el('div', { class: 'tdial', style: `width:${DIAL}px;height:${DIAL}px` });
  const hand = el('div', { class: 'thand' });
  const selector = el('div', { class: 'tselector', style: `width:${SELECTOR}px;height:${SELECTOR}px` });
  const center = el('div', { class: 'tcenter' });
  const labels = el('div', {});
  dial.append(hand, center, selector, labels);

  const hourInput = el('input', { class: 'tfield', type: 'text', inputmode: 'numeric', maxlength: '2', 'aria-label': 'Hour' });
  const minuteInput = el('input', { class: 'tfield', type: 'text', inputmode: 'numeric', maxlength: '2', 'aria-label': 'Minute' });
  const typedError = el('div', { class: 'terror', style: 'visibility:hidden' }, 'Enter a valid time.');
  const typedBox = el('div', { class: 'ttyped', style: 'display:none' },
    el('div', { class: 'ttyped-row' }, hourInput, el('span', { class: 'tcolon' }, ':'), minuteInput), typedError);

  function angleNow() {
    return state.mode === 'hour' ? (state.hour % 12) * 30 : state.minute * 6;
  }

  function render() {
    hourBox.textContent = String(state.hour);
    minuteBox.textContent = String(state.minute).padStart(2, '0');
    hourBox.classList.toggle('on', state.mode === 'hour' && !state.typing);
    minuteBox.classList.toggle('on', state.mode === 'minute' && !state.typing);
    am.classList.toggle('on', state.period === 'AM');
    pm.classList.toggle('on', state.period === 'PM');
    const angle = angleNow();
    const spot = dialPosition(angle, NUMBER_RADIUS);
    selector.style.left = `${MIDDLE + spot.x - SELECTOR / 2}px`;
    selector.style.top = `${MIDDLE + spot.y - SELECTOR / 2}px`;
    // The hand runs from the middle to the edge of the selector circle.
    hand.style.height = `${NUMBER_RADIUS - SELECTOR / 2}px`;
    hand.style.transform = `rotate(${angle}deg)`;
    labels.replaceChildren(...numbers());
    dial.style.display = state.typing ? 'none' : '';
    typedBox.style.display = state.typing ? '' : 'none';
  }

  /** The numbers around the dial: 1 to 12 for the hour, 00 to 55 by fives for the minute. */
  function numbers() {
    const list = state.mode === 'hour'
      ? Array.from({ length: 12 }, (_, i) => ({ text: String(i === 0 ? 12 : i), angle: i * 30, chosen: (i === 0 ? 12 : i) === state.hour }))
      : Array.from({ length: 12 }, (_, i) => ({ text: String(i * 5).padStart(2, '0'), angle: i * 30, chosen: i * 5 === state.minute }));
    return list.map(({ text, angle, chosen }) => {
      const spot = dialPosition(angle, NUMBER_RADIUS);
      return el('span', { class: 'tnumber' + (chosen ? ' chosen' : ''), style: `left:${MIDDLE + spot.x}px;top:${MIDDLE + spot.y}px` }, text);
    });
  }

  function setMode(mode) {
    state.mode = mode;
    state.typing = false;
    render();
  }

  function setPeriod(period) {
    state.period = period;
    render();
  }

  function fromPointer(event, finished) {
    const box = dial.getBoundingClientRect();
    const angle = angleOf(event.clientX - (box.left + box.width / 2), event.clientY - (box.top + box.height / 2));
    if (state.mode === 'hour') {
      state.hour = hourFromAngle(angle);
      render();
      if (finished) {
        setMode('minute');
      }
    } else {
      state.minute = minuteFromAngle(angle);
      render();
    }
  }

  let dragging = false;
  dial.addEventListener('pointerdown', event => {
    dragging = true;
    dial.setPointerCapture(event.pointerId);
    fromPointer(event, false);
  });
  dial.addEventListener('pointermove', event => dragging && fromPointer(event, false));
  dial.addEventListener('pointerup', event => {
    if (dragging) {
      dragging = false;
      fromPointer(event, true);
    }
  });

  function toggleTyping() {
    if (state.typing) {
      state.typing = false;
    } else {
      state.typing = true;
      hourInput.value = String(state.hour);
      minuteInput.value = String(state.minute).padStart(2, '0');
      typedError.style.visibility = 'hidden';
      setTimeout(() => hourInput.select(), 0);
    }
    render();
  }

  const keyboardButton = el('button', { class: 'tkeyboard pressable', type: 'button', 'aria-label': 'Switch to typing the time', onclick: toggleTyping });
  keyboardButton.innerHTML = KEYBOARD;

  function ok() {
    if (state.typing) {
      const typed = parseTyped(hourInput.value.trim(), minuteInput.value.trim(), state.period);
      if (typed === null) {
        typedError.style.visibility = 'visible';
        return false;
      }
      onPick(typed);
      return true;
    }
    onPick(to24(state.hour, state.period) * 60 + state.minute);
    return true;
  }

  const body = el('div', { class: 'clock' }, head, el('div', { class: 'tdial-wrap' }, dial, typedBox));
  const popup = dialog('', body, [
    { label: 'Cancel' },
    { label: 'OK', keepOpen: true, onclick: () => { if (ok()) { popup(); } } },
  ]);
  // The keyboard button sits at the left of the buttons row.
  popup.box.querySelector('.buttons').prepend(keyboardButton);
  keyboardButton.classList.add('left');
  popup.box.classList.add('clock-dialog');
  render();
  return popup;
}
