/** The Add Event, Settings and Unplaced Free Events screens. */
import { DAYS, NO_GROUP, fixed, counted, free, withColor, unplaced, isPlaced, endMinute } from './event.js';
import { membersOf, daysOf, saveFixedGroup, saveFreeGroup } from './groups.js';
import { nameOf, hex } from './colors.js';
import { timePicker } from './clock.js';
import { colorWheel } from './wheel.js';
import { clock, length } from './time.js';
import { el, titleBar, dialog, toast, icons } from './ui.js';
import { unplacedCount } from './calendar.js';
import { numberPicker } from './picker.js';
import { slider } from './slider.js';
import { startTour } from './tour.js';
import { ADD } from './tourkinds.js';

const SHORT_DAYS = ['Sun', 'Mon', 'Tues', 'Wed', 'Thurs', 'Fri', 'Sat'];
const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

function label(text) {
  return el('div', { class: 'label' }, text);
}

/** Add Event: Fixed, Free or Counted, with a name, place, color, days and times. */
export function showAddEvent(ctx) {
  const editing = ctx.editId ? ctx.events.find(event => event.id === ctx.editId) : null;
  ctx.editId = null;
  if (editing === undefined) {
    toast('That event no longer exists.');
  }
  titleBar(editing ? 'Edit Event' : 'Add Event', () => ctx.go('/'));
  const form = {
    kind: 'FIXED',
    color: ctx.settings.accent,
    days: new Set(),
    start: null,
    end: null,
    count: 10,
    hours: 1,
    minutes: 0,
  };
  if (editing) {
    Object.assign(form, {
      kind: editing.kind,
      color: editing.color,
      days: new Set(daysOf(membersOf(ctx.events, editing))),
    });
    if (editing.kind === 'FREE') {
      form.hours = Math.min(12, Math.floor(editing.durationMinutes / 60));
      form.minutes = Math.floor((editing.durationMinutes % 60) / 5) * 5;
    } else {
      form.start = editing.startMinute;
      form.end = endMinute(editing) % (24 * 60);
      form.count = editing.count || 10;
    }
  }
  const loadedCount = editing && editing.kind === 'COUNTED' ? editing.count : -1;

  const name = el('input', { class: 'field', type: 'text', placeholder: 'Name (required)', autocomplete: 'off', autocapitalize: 'sentences', 'aria-label': 'Name', value: editing?.name ?? '' });
  const place = el('input', { class: 'field field-gap', type: 'text', placeholder: 'Location (optional)', autocomplete: 'off', autocapitalize: 'words', 'aria-label': 'Location', value: editing?.location ?? '' });
  const error = el('div', { class: 'error', style: 'display:none', role: 'alert' });

  const kinds = [
    ['FIXED', 'Fixed: Occurs at set time'],
    ['FREE', 'Free: Fits between other events'],
    ['COUNTED', 'Counted: Counts down each pass'],
  ];
  const kindRows = kinds.map(([value, text]) => {
    const input = el('input', { type: 'radio', name: 'kind', value, checked: value === form.kind, onchange: () => { form.kind = value; sync(); } });
    return el('label', { class: 'kind' }, input, el('span', { class: 'dot' }), text);
  });

  const colorButton = el('button', { class: 'btn outlined color-button pressable', type: 'button', onclick: () => colorWheel({ title: 'Color', selected: form.color, accent: ctx.settings.accent, names: ctx.names, renamable: true, onPick: color => { form.color = color; sync(); }, onNamesSaved: sync }) });
  const dayButtons = DAYS.map((day, i) => el('button', {
    class: 'btn pressable', type: 'button', 'aria-pressed': 'false',
    onclick: () => { form.days.has(day) ? form.days.delete(day) : form.days.add(day); sync(); },
  }, SHORT_DAYS[i]));
  const startButton = el('button', { class: 'btn choice pressable', type: 'button', onclick: () => timePicker('Starts', form.start ?? 540, minute => { form.start = minute; sync(); }) });
  const endButton = el('button', { class: 'btn choice pressable', type: 'button', onclick: () => timePicker('Ends', form.end ?? (form.start ?? 480) + 60, minute => { form.end = minute; sync(); }) });
  const countValue = el('b', {}, String(form.count));
  const countSlider = slider({ min: 1, max: 52, value: form.count, label: 'Count', onChange: value => { form.count = value; countValue.textContent = String(value); } });
  const hours = numberPicker({ labels: Array.from({ length: 13 }, (_, h) => String(h)), index: form.hours, label: 'Hours', onChange: h => { form.hours = h; } });
  const minutes = numberPicker({ labels: Array.from({ length: 12 }, (_, i) => String(i * 5).padStart(2, '0')), index: form.minutes / 5, label: 'Minutes', onChange: i => { form.minutes = i * 5; } });

  const fixedSection = el('div', { style: 'margin-top:8px' },
    label('Starts'), startButton, label('Ends'), endButton,
    el('div', { class: 'count-section' }, el('div', { class: 'count-row' }, el('span', { class: 'label', style: 'margin:0' }, 'Count'), countValue), countSlider));
  const freeSection = el('div', { style: 'margin-top:12px' }, label('How long does it take?'),
    el('div', { class: 'length' }, hours, el('span', { class: 'unit' }, 'Hours'), minutes, el('span', {}, 'Minutes')));

  function sync() {
    colorButton.textContent = form.color === ctx.settings.accent ? `${ctx.names.nameOf(form.color)} (Default)` : ctx.names.nameOf(form.color);
    colorButton.style.color = hex(form.color);
    colorButton.style.borderColor = hex(form.color);
    dayButtons.forEach((button, i) => {
      const on = form.days.has(DAYS[i]);
      button.classList.toggle('on', on);
      button.setAttribute('aria-pressed', String(on));
    });
    startButton.textContent = form.start == null ? 'Pick time' : clock(form.start);
    startButton.classList.toggle('on', form.start != null);
    endButton.textContent = form.end == null ? 'Pick time' : clock(form.end) + (form.start != null && form.end <= form.start ? ' (next day)' : '');
    endButton.classList.toggle('on', form.end != null);
    fixedSection.style.display = form.kind === 'FREE' ? 'none' : '';
    fixedSection.querySelector('.count-section').style.display = form.kind === 'COUNTED' ? '' : 'none';
    freeSection.style.display = form.kind === 'FREE' ? '' : 'none';
  }

  function save() {
    try {
      const details = { id: editing?.id, name: name.value, location: place.value };
      const days = DAYS.filter(day => form.days.has(day));
      let saved;
      if (form.kind === 'FREE') {
        saved = saveFreeGroup(ctx.events, withColor(free({ ...details, durationMinutes: form.hours * 60 + form.minutes, allowedDays: days }), form.color), days);
      } else {
        // Checked in the order the form is filled in: name, days, start, end.
        if (!name.value.trim()) {
          throw new Error(`A ${form.kind === 'COUNTED' ? 'counted' : 'fixed'} event needs a name.`);
        }
        if (days.length === 0) {
          throw new Error('Pick a day for the event.');
        }
        if (form.start == null) {
          throw new Error('Pick a start time.');
        }
        if (form.end == null) {
          throw new Error('Pick an end time.');
        }
        const duration = form.end > form.start ? form.end - form.start : form.end + 24 * 60 - form.start;
        if (duration < 15) {
          throw new Error('Events must be at least 15 minutes long.');
        }
        const make = form.kind === 'COUNTED' ? counted : fixed;
        const one = withColor(make({ ...details, day: days[0], startMinute: form.start, durationMinutes: duration, count: form.count }), form.color);
        saved = saveFixedGroup(ctx.events, one, days, loadedCount === form.count);
      }
      ctx.saveAll(saved, `Saved “${name.value.trim() || 'Free time'}”`, '/');
    } catch (problem) {
      error.textContent = problem.message;
      error.style.display = '';
    }
  }

  const root = el('div', { class: 'scroll' }, el('div', { class: 'form' },
    label('Type'), ...kindRows, name, place,
    label('Color'), colorButton,
    el('div', { style: 'margin-top:12px' }, label('Day(s)'), el('div', { class: 'choice-row' }, dayButtons)),
    fixedSection, freeSection, error,
    el('button', { class: 'btn filled save pressable', type: 'button', onclick: save }, 'Save Event')));
  document.getElementById('screen').replaceChildren(root);
  sync();
  if (ctx.addTourWanted) {
    ctx.addTourWanted = false;
    setTimeout(() => { ctx.endTour = startTour(ADD, () => ctx.go('/')); }, 100);
  }
}

/** The accent wheel shows every color under its own original name, whatever names were given elsewhere. */
const originalNames = { nameOf, hasCustomName: () => true };

const SETTING_NOT_YET = 'That setting isn’t in the web version yet.';

/** Settings: the same buttons as the Android app; the ones that work here are marked in the code. */
export function showSettings(ctx) {
  titleBar('Settings', () => ctx.go('/'));
  const settings = ctx.settings;
  const notYet = () => toast(SETTING_NOT_YET);

  // These three are fixed in the web version: they show their setting but a press only explains.
  const lockedToggle = (text, key) => {
    const button = el('button', { class: 'btn setting toggle pressable', type: 'button', onclick: notYet }, text);
    button.classList.toggle('on', Boolean(settings[key]));
    return button;
  };
  const setting = (text, onclick) => el('button', { class: 'btn setting pressable', type: 'button', onclick }, text);
  const toggle = (text, key) => {
    const button = el('button', { class: 'btn setting toggle pressable', type: 'button' }, text);
    const show = () => button.classList.toggle('on', Boolean(settings[key]));
    button.addEventListener('click', () => { settings[key] = !settings[key]; ctx.saveSettings(); show(); });
    show();
    return button;
  };

  const startButton = setting('', () => timePicker('Start Calendar View at', settings.startMinute, minute => {
    settings.startMinute = minute;
    ctx.saveSettings();
    startButton.textContent = `Start Calendar View at: ${clock(minute)}`;
  }));
  startButton.textContent = `Start Calendar View at: ${clock(settings.startMinute)}`;

  const half = (text, key) => { const b = lockedToggle(text, key); b.style.width = 'auto'; b.style.fontSize = '13px'; return b; };
  const bell = el('button', { class: 'btn toggle bell pressable', type: 'button', 'aria-label': 'Notifications', onclick: notYet });
  bell.innerHTML = icons.BELL_OFF;

  document.getElementById('screen').replaceChildren(el('div', { class: 'scroll' }, el('div', { class: 'stack' },
    setting('Delete Many Events', () => ctx.go('/delete')),
    setting('Replay Tutorial', () => { ctx.tourWanted = true; ctx.go('/'); }),
    setting('Accent Color', () => colorWheel({
      title: 'Accent Color',
      selected: settings.accent,
      accent: settings.accent,
      names: originalNames,
      renamable: false,
      onPick: color => {
        settings.accent = color;
        ctx.saveSettings();
        ctx.applyAccent();
      },
    })),
    lockedToggle('Lock Vertical Calendar', 'lockVertical'),
    startButton,
    toggle('Start Today’s View at Current Time', 'todayAtNow'),
    el('div', { class: 'row-pair' }, half('Vibration', 'vibration'), half('Sound', 'sound')),
    el('div', { class: 'row-pair' }, bell, setting('Notify 10 Minutes Before', notYet)),
    setting('Notification Colors', notYet))));
}

/** One row per unplaced free event; events made together on several days are one row. */
function unplacedRows(events) {
  const seen = new Set();
  const rows = [];
  for (const event of events) {
    if (event.kind !== 'FREE' || isPlaced(event)) {
      continue;
    }
    const key = event.groupId || event.id;
    if (!seen.has(key)) {
      seen.add(key);
      rows.push(event);
    }
  }
  return rows;
}

/** Unplaced Free Events: the free events that are waiting for a time. */
export function showFreeEvents(ctx) {
  titleBar('Unplaced Free Events', () => ctx.go('/'));
  const events = ctx.events;
  const rows = unplacedRows(events);
  const placed = events.filter(isPlaced);

  const place = el('button', { class: 'btn filled pill pressable', type: 'button', style: 'width:100%;margin-bottom:8px', disabled: rows.length === 0, onclick: () => ctx.go('/place') }, 'Place Events');
  const recall = el('button', {
    class: 'btn outlined pill pressable', type: 'button', style: 'width:100%;margin-bottom:12px', disabled: placed.length === 0,
    onclick: () => dialog('Recall all events?', el('p', {}, 'They will come off the calendar and go back to Unplaced Free Events. Fixed events stay where they are.'), [
      { label: 'Cancel' },
      { label: 'Recall All', onclick: () => ctx.saveAll(events.map(event => (isPlaced(event) ? unplaced(event) : event)), 'Recalled all events') },
    ]),
  }, 'Recall All Events');

  const list = rows.map(event => el('button', { class: 'list-row pressable', type: 'button', onclick: () => dialog(event.name, el('div', {}, [
    el('p', {}, `Takes ${length(event.durationMinutes)}`),
    el('p', {}, event.allowedDays.length === 0 ? 'Any day' : event.allowedDays.map(d => DAY_NAMES[DAYS.indexOf(d)]).join(', ')),
    event.location ? el('p', {}, `At ${event.location}`) : null,
  ]), [
    { label: 'Delete', onclick: () => ctx.saveAll(events.filter(e => e.id !== event.id && (!event.groupId || e.groupId !== event.groupId)), `Deleted “${event.name}”`) },
    { label: 'Edit', onclick: () => { ctx.editId = event.id; ctx.go('/add'); } },
    { label: 'Close' },
  ]) }, event.name, el('small', {}, `${length(event.durationMinutes)} · ${event.allowedDays.length === 0 ? 'Any day' : event.allowedDays.map(d => d.slice(0, 3)).join(', ')}`)));

  document.getElementById('screen').replaceChildren(el('div', { class: 'scroll' },
    place, recall,
    el('p', { class: 'hint' }, 'Tap an event to see its details, edit it, or delete it.'),
    rows.length === 0 ? el('p', { class: 'empty' }, 'No unplaced free events. Add one with “Add Event” and choose Free.') : null,
    ...list));
}

export { NO_GROUP, unplacedCount };
