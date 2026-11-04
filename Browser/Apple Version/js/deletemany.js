/** Delete Many Events: every event with a tick box, so several can be deleted at once. */
import { buildRows } from './eventrows.js';
import { describeRow } from './describe.js';
import { hex } from './colors.js';
import { el, titleBar, dialog } from './ui.js';

const CHECK = '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';

/** The events that go when these rows are deleted: every member of every ticked row. */
export function doomedEvents(rows, tickedIds) {
  return rows.filter(row => tickedIds.has(row.first.id)).flatMap(row => row.members);
}

export function showDeleteMany(ctx) {
  titleBar('Delete Many Events', () => ctx.go('/settings'));
  const rows = buildRows(ctx.events);
  const ticked = new Set();

  const deleteButton = el('button', { class: 'btn filled pill pressable delete-many', type: 'button', onclick: confirmDelete });

  function updateButton() {
    deleteButton.disabled = ticked.size === 0;
    deleteButton.textContent = ticked.size === 0 ? 'Delete' : `Delete (${ticked.size})`;
  }

  function confirmDelete() {
    const count = ticked.size;
    dialog(`Delete ${count} ${count === 1 ? 'event' : 'events'}?`, el('p', {}, 'This can’t be undone.'), [
      { label: 'Cancel' },
      {
        label: 'Delete',
        onclick: () => {
          const doomedIds = new Set(doomedEvents(rows, ticked).map(event => event.id));
          ctx.saveAll(ctx.events.filter(event => !doomedIds.has(event.id)), `Deleted ${count} ${count === 1 ? 'event' : 'events'}`);
        },
      },
    ]);
  }

  const cards = rows.map(row => {
    const event = row.first;
    const box = el('span', { class: 'dcheck' });
    box.innerHTML = CHECK;
    const card = el('button', {
      class: `dcard pressable ${event.kind.toLowerCase()}`,
      type: 'button',
      role: 'checkbox',
      'aria-checked': 'false',
      style: `--block-color:${hex(event.color)}`,
      onclick: () => {
        const on = !ticked.has(event.id);
        on ? ticked.add(event.id) : ticked.delete(event.id);
        card.classList.toggle('ticked', on);
        card.setAttribute('aria-checked', String(on));
        updateButton();
      },
    }, el('span', { class: 'dtext' }, el('b', {}, event.name), el('span', {}, describeRow(row))), box);
    return card;
  });

  document.getElementById('screen').replaceChildren(
    el('div', { class: 'scroll' },
      el('p', { class: 'hint' }, 'Tap the events you want to delete, then tap Delete. Events that repeat on several days are one line and are deleted on all of their days.'),
      rows.length === 0 ? el('p', { class: 'empty' }, 'There are no events to delete.') : null,
      ...cards),
    el('div', { class: 'bottom' }, deleteButton),
  );
  updateButton();
}
