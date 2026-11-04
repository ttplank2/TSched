/** The calendar screen: a card for each day of the week, side by side, with the day's events as blocks. */
import { DAYS } from './event.js';
import { endMinute, crossesMidnight, isPlaced, unplaced } from './event.js';
import { membersOf } from './groups.js';
import { segmentsForDay, placeSegments } from './layout.js';
import { fit, wrap } from './blocktext.js';
import { clock, range, hourLabels, freeCountLabel } from './time.js';
import { hex, nameOf } from './colors.js';
import { el, icons, calendarBar, chooser, dialog, toast } from './ui.js';
import { colorWheel } from './wheel.js';
import { TapOrDoubleTap } from './taps.js';
import { startTour } from './tour.js';
import { HOME } from './tourkinds.js';
import { dayOfPage, nearestPage, swipeIntent, settlePage } from './pager.js';

const HOUR_HEIGHT = 60;
const GUTTER = 52;
const TOP_PADDING = 10;
const BLOCK_GAP = 2;
const TEXT_PADDING = 5;
const HOLD_MILLIS = 500;
const TITLE_LINE = 16;
const DETAILS_LINE = 14;
const SIDE_SHADE = 0.6;
/** When today's view starts at the current time, it opens this many minutes earlier. */
const TODAY_LEAD_MINUTES = 180;
/** The distance between neighbouring day cards: a card's width and the gap between cards. */
const PAGE_STRIDE = 329 + 12;
/** How far a finger or the mouse moves before it counts as a swipe or a scroll. */
const TOUCH_SLOP = 8;
const SNAP_MILLIS = 260;
/** The page showing now, kept so redrawing the calendar (a filter, say) stays on the same day. */
let currentPage = null;
/** The tour opens by itself once each time the app is opened, if there are no events yet. */
let tourOffered = false;
const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

const yFor = minute => TOP_PADDING + (minute / 60) * HOUR_HEIGHT;

let measureContext = null;

/** Measures how wide text is in the app's bold font, for wrapping names inside blocks. */
function measurer(size) {
  measureContext ??= document.createElement('canvas').getContext('2d');
  measureContext.font = `700 ${size}px Sora`;
  return text => measureContext.measureText(text).width;
}

/** How many unplaced free events are waiting; events made together on several days count once. */
export function unplacedCount(events) {
  const groups = new Set();
  for (const event of events) {
    if (event.kind === 'FREE' && event.day == null) {
      groups.add(event.groupId || event.id);
    }
  }
  return groups.size;
}

function nowMinute() {
  const now = new Date();
  return now.getHours() * 60 + now.getMinutes();
}

function todayName() {
  return DAYS[new Date().getDay()];
}

function dayBlocks(schedule, placements, filterColor, onTap) {
  const width = schedule.clientWidth || 327;
  const columnArea = width - GUTTER - 4;
  const titleMeasure = measurer(14);
  const timeMeasure = measurer(12);
  for (const placement of placements) {
    const event = placement.event;
    const columnWidth = columnArea / placement.columnCount;
    const left = GUTTER + placement.column * columnWidth + BLOCK_GAP;
    const top = yFor(placement.startMinute) + BLOCK_GAP;
    const blockWidth = columnWidth - 2 * BLOCK_GAP;
    const height = yFor(placement.endMinute) - BLOCK_GAP - top;
    const counted = event.kind === 'COUNTED';
    const outlined = event.kind === 'FREE' || counted;
    const dimmed = filterColor != null && event.color !== filterColor;
    const textWidth = Math.max(0, blockWidth - 2 * TEXT_PADDING - (outlined ? 4 : 0));
    const arrowRoom = placement.continuesAfter || placement.continuesFrom ? Math.max(0, 7.2 + 2 - TEXT_PADDING) : 0;
    const topInset = placement.continuesFrom ? arrowRoom : 0;
    const bottomInset = placement.continuesAfter ? arrowRoom : 0;
    const textHeight = height - (outlined ? 4 : 0) - 2 * TEXT_PADDING - topInset - bottomInset;
    const duration = placement.endMinute - placement.startMinute;
    const lines = fit(duration, textHeight, TITLE_LINE, DETAILS_LINE);

    let held = false;
    let holdTimer = null;
    let pressedAt = null;
    const block = el('div', {
      class: ['block', outlined ? 'outlined' : 'solid', counted ? 'counted' : '', dimmed ? 'dimmed' : ''].filter(Boolean).join(' '),
      style: `left:${left}px;top:${top}px;width:${blockWidth}px;height:${height}px;--block-color:${hex(event.color)}`,
      role: 'button',
      tabindex: '0',
      'aria-label': `${event.name}, ${range(event.startMinute, endMinute(event))}`,
      onclick: () => {
        // A hold already opened the popup; the click that ends it must not open it a second time.
        if (held) {
          held = false;
          return;
        }
        onTap(event);
      },
      onkeydown: key => (key.key === 'Enter' || key.key === ' ') && onTap(event),
    });
    // Holding a click or a finger on an event opens the same popup as tapping it.
    const cancelHold = () => clearTimeout(holdTimer);
    block.addEventListener('pointerdown', press => {
      held = false;
      pressedAt = [press.clientX, press.clientY];
      holdTimer = setTimeout(() => { held = true; onTap(event); }, HOLD_MILLIS);
    });
    block.addEventListener('pointermove', move => {
      if (pressedAt && Math.hypot(move.clientX - pressedAt[0], move.clientY - pressedAt[1]) > 8) {
        cancelHold();
      }
    });
    for (const type of ['pointerup', 'pointercancel', 'pointerleave']) {
      block.addEventListener(type, cancelHold);
    }
    block.addEventListener('contextmenu', menu => menu.preventDefault());
    let y = TEXT_PADDING + topInset - (outlined ? 2 : 0);
    if (lines.nameLines > 0) {
      for (const line of wrap(event.name, textWidth, lines.nameLines, titleMeasure)) {
        block.append(el('div', { class: 'name', style: `top:${y}px` }, line));
        y += TITLE_LINE;
      }
    }
    if (lines.showTime) {
      let details = range(event.startMinute, endMinute(event));
      if (event.location) {
        details += ` · ${event.location}`;
      }
      block.append(el('div', { class: 'time', style: `top:${y}px` }, details));
    }
    if (counted) {
      block.append(el('div', { class: 'count' }, String(event.count)));
    }
    if ((placement.continuesAfter || placement.continuesFrom) && height >= 14.4) {
      block.append(el('div', { class: `arrow ${placement.continuesAfter ? 'down' : 'up'}` }));
    }
    schedule.append(block);
  }
}

function buildDay(day, events, ctx) {
  const isToday = day === todayName();
  const segments = segmentsForDay(events, day);
  const title = el('h2', {}, isToday ? `${DAY_NAMES[DAYS.indexOf(day)]} · Today` : DAY_NAMES[DAYS.indexOf(day)]);
  const subtitle = el('div', { class: 'subtitle' },
    segments.length === 0 ? 'Nothing scheduled' : `${segments.length} ${segments.length === 1 ? 'Event' : 'Events'}`);
  const schedule = el('div', { class: 'schedule', style: `height:${yFor(24 * 60) + TOP_PADDING}px` });
  const labels = hourLabels();
  for (let hour = 0; hour <= 24; hour++) {
    const y = yFor(hour * 60);
    schedule.append(el('div', { class: 'hour-line', style: `top:${y}px` }));
    if (hour < 24) {
      schedule.append(el('div', { class: 'hour-label', style: `top:${y - 5}px` }, labels[hour]));
    }
  }
  const scroller = el('div', { class: 'scroller' }, schedule);
  const card = el('section', { class: 'day', 'data-day': day }, title, subtitle, scroller, el('div', { class: 'shade' }));
  card.draw = () => {
    for (const old of schedule.querySelectorAll('.block, .now-line, .now-arrow')) {
      old.remove();
    }
    dayBlocks(schedule, placeSegments(segments), ctx.filterColor, ctx.onBlockTap);
    if (isToday) {
      const y = yFor(nowMinute());
      schedule.append(el('div', { class: 'now-arrow', style: `top:${y}px` }), el('div', { class: 'now-line', style: `top:${y}px` }));
    }
  };
  card.scrollToStart = () => {
    const minute = isToday && ctx.settings.todayAtNow
      ? Math.max(0, nowMinute() - TODAY_LEAD_MINUTES)
      : Math.max(0, ctx.settings.startMinute - 5);
    scroller.scrollTop = minute === 0 ? 0 : yFor(minute);
  };
  return card;
}

/** Details of one event, with a Delete button. */
function showDetails(event, events, ctx) {
  const days = event.kind === 'FREE' ? (event.day ? [event.day] : event.allowedDays) : [event.day];
  const dayText = days.length === 0 ? 'Any Day' : days.map(d => DAY_NAMES[DAYS.indexOf(d)]).join(', ');
  const lines = [];
  if (event.kind === 'FREE') {
    lines.push(event.day ? `Placed on ${DAY_NAMES[DAYS.indexOf(event.day)]}, ${range(event.startMinute, endMinute(event))}` : `Takes ${event.durationMinutes} minutes · ${dayText}`);
  } else {
    lines.push(`Every ${dayText}, ${range(event.startMinute, endMinute(event))}${crossesMidnight(event) ? ' (ends the next day)' : ''}`);
    if (event.kind === 'COUNTED') {
      lines.push(`${event.count} ${event.count === 1 ? 'time' : 'times'} left`);
    }
  }
  if (event.location) {
    lines.push(`At ${event.location}`);
  }
  const body = el('div', {}, lines.map(line => el('p', {}, line)));
  const group = membersOf(events, event);
  const buttons = [
    { label: 'Delete', left: true, onclick: () => confirmDelete(event, group, events, ctx) },
    { label: 'Edit', onclick: () => { ctx.editId = event.id; ctx.go('/add'); } },
    { label: 'OK' },
  ];
  if (event.kind === 'FREE' && event.day != null) {
    buttons.unshift({ label: 'Recall', left: true, onclick: () => confirmRecall(event, group, events, ctx) });
  }
  dialog(event.name, body, buttons);
}

/** Asks before deleting an event and, if it repeats on several days, all of its days. */
function confirmDelete(event, group, events, ctx) {
  const message = group.length > 1 ? 'This event repeats on several days. It will be deleted on all of them.' : null;
  dialog(`Delete “${event.name}”?`, message ? el('p', {}, message) : null, [
    { label: 'Cancel' },
    {
      label: 'Delete',
      onclick: () => {
        const doomed = new Set(group.map(member => member.id));
        ctx.saveAll(events.filter(saved => !doomed.has(saved.id)), 'Event deleted');
      },
    },
  ]);
}

/** Asks before taking a placed free event (and the rest of its group) back to Unplaced Free Events. */
function confirmRecall(event, group, events, ctx) {
  dialog(`Recall “${event.name}”?`, el('p', {}, group.length > 1
    ? 'All instances will come off the calendar and go back to Unplaced Free Events.'
    : 'It will come off the calendar and go back to Unplaced Free Events.'), [
    { label: 'Cancel' },
    {
      label: 'Recall',
      onclick: () => {
        const recalled = new Set(group.map(member => member.id));
        ctx.saveAll(events.map(saved => (recalled.has(saved.id) && isPlaced(saved) ? unplaced(saved) : saved)), `“${event.name}” is back in Unplaced Free Events`);
      },
    },
  ]);
}

/**
 * Shows the calendar. ctx has: events, settings, filterColor, setFilter(color), saveAll(events, message),
 * go(route). Returns nothing; the screen redraws itself when the events change.
 */
export function showCalendar(ctx) {
  const screen = document.getElementById('screen');
  const events = ctx.events;
  ctx.onBlockTap = event => showDetails(event, events, ctx);

  const pager = el('div', { class: 'pager' });
  const cards = DAYS.map(day => buildDay(day, events, ctx));
  pager.append(...cards);
  const days = createDays(pager, cards, ctx);

  const free = el('button', { class: 'btn outlined pill grow pressable', onclick: () => ctx.go('/free') },
    `Unplaced Free Events (${freeCountLabel(unplacedCount(events))})`);
  const add = el('button', { class: 'btn filled pill add pressable', onclick: () => ctx.go('/add') });
  add.innerHTML = icons.PLUS;
  add.append(' Add Event');
  screen.replaceChildren(pager, el('div', { class: 'bottom' }, free, add));

  const filterName = ctx.filterColor == null ? null : { name: ctx.names.nameOf(ctx.filterColor), color: hex(ctx.filterColor) };
  // One tap opens the picker; a double tap clears the filter (Filter) or jumps to today (Snap to Day).
  const filterTaps = new TapOrDoubleTap(() => colorWheel({
    title: 'Show only one color.',
    selected: ctx.filterColor,
    accent: ctx.settings.accent,
    names: ctx.names,
    renamable: true,
    rightLabel: ctx.filterColor == null ? 'Cancel' : 'Clear Filter',
    onRight: () => ctx.filterColor != null && ctx.setFilter(null),
    onPick: ctx.setFilter,
    onNamesSaved: () => ctx.go('/'),
  }), () => ctx.setFilter(null));
  const snapTaps = new TapOrDoubleTap(() => chooser('Snap to Day', DAYS.map((day, i) => ({
    value: day,
    label: day === todayName() ? `${DAY_NAMES[i]} · Today` : DAY_NAMES[i],
  })), DAYS[days.shownDay()], day => days.snapTo(day)),
  () => days.snapTo(todayName()));
  calendarBar({
    filterName,
    onFilter: () => filterTaps.tap(),
    onSnap: () => snapTaps.tap(),
    onSettings: () => ctx.go('/settings'),
  });

  for (const card of cards) {
    card.draw();
  }
  for (const card of cards) {
    card.scrollToStart();
  }
  days.show(currentPage ?? nearestPage(7 * 1000, DAYS.indexOf(todayName())));

  // Draw the blocks again once the cards have their real width (the first draw can come before layout).
  requestAnimationFrame(() => {
    for (const card of cards) {
      card.draw();
    }
  });
  const offered = tourOffered;
  tourOffered = true;
  if (ctx.tourWanted || (!offered && events.length === 0)) {
    ctx.tourWanted = false;
    // Wait a moment so every control has its place on the screen.
    setTimeout(() => {
      ctx.endTour = startTour(HOME, completed => {
        if (completed) {
          ctx.addTourWanted = true;
          ctx.go('/add');
        }
      });
    }, 100);
  }
  ctx.refreshNow = () => cards.forEach(card => card.draw());
}

/**
 * The endless week of day cards. Only the cards near the shown page are visible; the card for a page is the one for
 * its day of the week, so after Saturday comes Sunday again. A drag more sideways than vertical (the mouse or a
 * finger) moves the cards; a drag more vertical than sideways scrolls the day.
 */
function createDays(pager, cards, ctx) {
  let position = 0;
  let animation = null;
  let gesture = null;
  let justSwiped = false;
  let coastFrame = null;

  const show = page => {
    position = page;
    currentPage = Math.round(page);
    for (const card of cards) {
      card.style.visibility = 'hidden';
    }
    for (let page = Math.floor(position) - 1; page <= Math.ceil(position) + 1; page++) {
      const card = cards[dayOfPage(page)];
      const away = page - position;
      card.style.visibility = '';
      card.style.transform = `translateX(${away * PAGE_STRIDE}px)`;
      card.querySelector('.shade').style.opacity = String(Math.min(1, Math.abs(away)) * SIDE_SHADE);
    }
  };

  /** Lets a day keep scrolling after a mouse drag is let go, slowing down like the phone's own scrolling does. */
  const coastScroll = (scroller, speed) => {
    let last = performance.now();
    let velocity = speed;
    const step = now => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const before = scroller.scrollTop;
      scroller.scrollTop = before - velocity * dt;
      velocity *= Math.exp(-3 * dt);
      const stuck = scroller.scrollTop === before;
      coastFrame = Math.abs(velocity) > 20 && !stuck ? requestAnimationFrame(step) : null;
    };
    coastFrame = requestAnimationFrame(step);
  };

  const stop = () => {
    if (coastFrame !== null) {
      cancelAnimationFrame(coastFrame);
      coastFrame = null;
    }
    if (animation !== null) {
      cancelAnimationFrame(animation);
      animation = null;
    }
  };

  const animateTo = target => {
    stop();
    const from = position;
    const start = performance.now();
    const step = now => {
      const t = Math.min(1, (now - start) / SNAP_MILLIS);
      show(from + (target - from) * (1 - (1 - t) * (1 - t) * (1 - t)));
      animation = t < 1 ? requestAnimationFrame(step) : null;
    };
    animation = requestAnimationFrame(step);
  };

  pager.addEventListener('pointerdown', event => {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    // A press stops any coasting up or down at once.
    stop();
    const card = cards[dayOfPage(Math.round(position))];
    const scroller = card.querySelector('.scroller');
    gesture = {
      x: event.clientX,
      y: event.clientY,
      start: position,
      startPage: Math.round(position),
      mode: 'undecided',
      scroller,
      scrollTop: scroller.scrollTop,
      baseX: event.clientX,
      basePosition: position,
      samples: [],
    };
  });

  pager.addEventListener('pointermove', event => {
    if (!gesture) {
      return;
    }
    const dx = event.clientX - gesture.x;
    const dy = event.clientY - gesture.y;
    // The 45 degree rule, checked all through the drag like the Android app: as soon as the drag has gone more
    // sideways than up or down it becomes a swipe between days, even if it began as a scroll.
    if (gesture.mode !== 'sideways') {
      const intent = swipeIntent(dx, dy, TOUCH_SLOP);
      if (intent === 'sideways') {
        const fromScroll = gesture.mode === 'vertical';
        stop();
        gesture.mode = 'sideways';
        gesture.baseX = fromScroll ? event.clientX : gesture.x;
        gesture.basePosition = position;
        gesture.samples = [];
        pager.setPointerCapture(event.pointerId);
      } else if (intent === 'vertical' && gesture.mode === 'undecided') {
        gesture.mode = 'vertical';
        pager.setPointerCapture(event.pointerId);
      }
    }
    const now = performance.now();
    if (gesture.mode === 'sideways') {
      // Locked to left and right: nothing moves up or down any more.
      show(gesture.basePosition - (event.clientX - gesture.baseX) / PAGE_STRIDE);
      gesture.samples.push({ t: now, x: event.clientX });
    } else if (gesture.mode === 'vertical') {
      gesture.scroller.scrollTop = gesture.scrollTop - dy;
      gesture.samples.push({ t: now, y: event.clientY });
    }
    gesture.samples = gesture.samples.filter(sample => sample.t > now - 120);
  });

  const release = event => {
    if (!gesture) {
      return;
    }
    const finished = gesture;
    gesture = null;
    if (finished.mode === 'undecided') {
      return;
    }
    // The click that ends a drag must not also press whatever is under the pointer.
    justSwiped = true;
    setTimeout(() => { justSwiped = false; }, 0);
    const first = finished.samples[0];
    const seconds = first ? (performance.now() - first.t) / 1000 : 0;
    if (finished.mode === 'vertical') {
      const speed = seconds > 0.01 ? (event.clientY - first.y) / seconds : 0;
      if (Math.abs(speed) > 150) {
        coastScroll(finished.scroller, Math.max(-4000, Math.min(4000, speed)));
      }
      return;
    }
    const velocity = seconds > 0.01 ? -(event.clientX - first.x) / PAGE_STRIDE / seconds : 0;
    animateTo(event.type === 'pointercancel' ? Math.round(position) : settlePage(position, velocity, finished.startPage));
  };
  pager.addEventListener('pointerup', release);
  pager.addEventListener('pointercancel', release);
  // A swipe that ends over an event block must not also open that event.
  pager.addEventListener('click', event => {
    if (justSwiped) {
      event.stopPropagation();
      event.preventDefault();
    }
  }, true);
  pager.addEventListener('keydown', event => {
    if (event.target === pager && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) {
      animateTo(Math.round(position) + (event.key === 'ArrowRight' ? 1 : -1));
    }
  });

  return {
    show,
    shownDay: () => dayOfPage(Math.round(position)),
    snapTo: day => animateTo(nearestPage(Math.round(position), DAYS.indexOf(day))),
  };
}

export { toast, clock };
