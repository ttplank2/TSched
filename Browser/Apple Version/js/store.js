/** Saves the person's events in the browser, on their own device (nothing is sent anywhere). */
import { encode, decode } from './codec.js';

const KEY = 'tsched.events';

export class EventStore {
  /** `storage` is the browser's localStorage, or a stand-in for tests. */
  constructor(storage) {
    this.storage = storage;
  }

  /** The saved events, or none if nothing has been saved yet or it can't be read. */
  load() {
    try {
      return decode(this.storage.getItem(KEY) ?? '');
    } catch {
      return [];
    }
  }

  /** Saves the events; false if the browser refused (storage full or switched off). */
  save(events) {
    try {
      this.storage.setItem(KEY, encode(events));
      return true;
    } catch {
      return false;
    }
  }

  /** Replaces the saved event with the same id, or adds it if there is none. */
  put(event) {
    const events = this.load().filter(saved => saved.id !== event.id);
    events.push(event);
    return this.save(events);
  }
}
