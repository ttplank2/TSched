/**
 * Place Events: asks which hours free events may go in, then suggests a placement for every unplaced free event.
 * Nothing is saved until the person approves the suggestion.
 */
import { suggest } from './placer.js';
import { placementBlocks, unplacedRows } from './eventrows.js';
import { DAYS } from './event.js';
import { range, clock, length } from './time.js';
import { timePicker } from './clock.js';
import { el, titleBar, dialog } from './ui.js';

const SHORT_DAYS = ['Sun', 'Mon', 'Tues', 'Wed', 'Thurs', 'Fri', 'Sat'];
const DEFAULT_START = 8 * 60;
const DEFAULT_END = 22 * 60;

/** The suggestion in words: each event's name, then a line for each day, with a blank line between events. */
export function describeSuggestion(suggestion) {
  const parts = placementBlocks(suggestion.placed).map(block => [
    `${block[0].name}:`,
    ...block.map(event => `${SHORT_DAYS[DAYS.indexOf(event.day)]}, ${range(event.startMinute, event.startMinute + event.durationMinutes)}`),
  ].join('\n'));
  let text = parts.join('\n\n');
  if (suggestion.unfitted.length > 0) {
    text += `${text ? '\n\n' : ''}Didn’t fit (no open time long enough):\n${suggestion.unfitted.map(event => `• ${event.name} (${length(event.durationMinutes)})`).join('\n')}`;
  }
  return text;
}

export function showPlaceEvents(ctx) {
  titleBar('Place Events', () => ctx.go('/free'));
  let windowStart = DEFAULT_START;
  let windowEnd = DEFAULT_END;

  const error = el('div', { class: 'error', style: 'display:none', role: 'alert' });
  const fromButton = el('button', { class: 'btn choice on pressable', type: 'button', onclick: () => timePicker('No earlier than', windowStart, minute => { windowStart = minute; showWindow(); }) });
  const toButton = el('button', { class: 'btn choice on pressable', type: 'button', onclick: () => timePicker('Done by', windowEnd, minute => { windowEnd = minute; showWindow(); }) });
  const label = text => el('div', { class: 'label' }, text);

  function showWindow() {
    fromButton.textContent = clock(windowStart);
    toButton.textContent = clock(windowEnd);
    error.style.display = 'none';
  }

  function showError(message) {
    error.textContent = message;
    error.style.display = '';
  }

  function propose() {
    let suggestion;
    try {
      suggestion = suggest(ctx.events, windowStart, windowEnd);
    } catch (problem) {
      showError(problem.message);
      return;
    }
    if (suggestion.placed.length === 0 && suggestion.unfitted.length === 0) {
      showError('There are no unplaced free events to place.');
      return;
    }
    error.style.display = 'none';
    const buttons = [{ label: 'Cancel' }];
    if (suggestion.placed.length > 0) {
      buttons.push(
        { label: 'Try another', onclick: propose },
        { label: 'Approve', onclick: () => approve(suggestion) },
      );
    }
    dialog('Suggested Placement', el('p', { class: 'suggestion' }, describeSuggestion(suggestion)), buttons);
  }

  function approve(suggestion) {
    const byId = new Map(suggestion.placed.map(event => [event.id, event]));
    const count = suggestion.placed.length;
    ctx.saveAll(ctx.events.map(event => byId.get(event.id) ?? event), `Placed ${count} ${count === 1 ? 'event' : 'events'} on your calendar`, '/free');
  }

  const waiting = unplacedRows(ctx.events).length;
  document.getElementById('screen').replaceChildren(el('div', { class: 'scroll' }, el('div', { class: 'form' },
    el('p', { class: 'hint' }, 'TSched will fit your unplaced free events into open time around your fixed events, on the days each one allows. Choose the hours they may go in.'),
    label('No earlier than'), fromButton, label('Done by'), toButton, error,
    el('button', { class: 'btn filled save pressable', type: 'button', onclick: propose, disabled: waiting === 0 }, 'Place'))));
  showWindow();
}
