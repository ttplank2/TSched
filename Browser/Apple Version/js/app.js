/** Starts TSched in the browser: loads the saved events and settings and shows the screen for the address. */
import { EventStore } from './store.js';
import { showCalendar } from './calendar.js';
import { showPlaceEvents } from './place.js';
import { showDeleteMany } from './deletemany.js';
import { showAddEvent, showSettings, showFreeEvents } from './forms.js';
import { toast } from './ui.js';
import { loadSettings, saveSettings } from './settings.js';
import { hex } from './colors.js';
import { ColorNames } from './names.js';
import { dragScroll } from './dragscroll.js';

const store = new EventStore(window.localStorage);

const ctx = {
  events: store.load(),
  settings: loadSettings(window.localStorage),
  names: new ColorNames(window.localStorage),
  filterColor: null,
  go(route) {
    if (location.hash === `#${route}` || (route === '/' && location.hash === '')) {
      render();
    } else {
      location.hash = route;
    }
  },
  setFilter(color) {
    ctx.filterColor = color;
    render();
  },
  /** Saves a new list of events, shows a message, and goes to a screen (or redraws this one). */
  saveAll(events, message, route) {
    if (!store.save(events)) {
      toast('Couldn’t save. Please try again.');
      return;
    }
    ctx.events = events;
    if (message) {
      toast(message);
    }
    if (route) {
      ctx.go(route);
    } else {
      render();
    }
  },
  saveSettings() {
    if (!saveSettings(window.localStorage, ctx.settings)) {
      toast('Couldn’t save. Please try again.');
    }
  },
  /** Makes the whole page wear the accent color chosen in Settings. */
  applyAccent() {
    const root = document.documentElement.style;
    root.setProperty('--accent', hex(ctx.settings.accent));
    root.setProperty('--accent-fill', hex(ctx.settings.accent));
  },
};

const routes = {
  '/': showCalendar,
  '/add': showAddEvent,
  '/settings': showSettings,
  '/free': showFreeEvents,
  '/place': showPlaceEvents,
  '/delete': showDeleteMany,
};

function render() {
  ctx.endTour?.();
  ctx.endTour = null;
  ctx.applyAccent();
  const route = location.hash.replace(/^#/, '') || '/';
  (routes[route] ?? showCalendar)(ctx);
  document.querySelectorAll('#screen .scroll').forEach(dragScroll);
}

window.addEventListener('hashchange', render);
// Safari on iPhones starts its own pinch zoom with these events, whatever the page's style says.
for (const type of ['gesturestart', 'gesturechange', 'gestureend']) {
  document.addEventListener(type, event => event.preventDefault());
}
// The "now" line moves as the minutes go by.
setInterval(() => ctx.refreshNow?.(), 60_000);

// The app's fonts must be ready before blocks are measured and drawn.
Promise.all([
  document.fonts.load('700 14px Sora'),
  document.fonts.load('400 20px Sora'),
  document.fonts.load('400 22px Pacifico'),
]).catch(() => {}).then(render);
