/** Formats minutes-after-midnight and lengths for display (the same wording as the Android app). */

/** 0 becomes "12:00 AM", 870 becomes "2:30 PM", 1440 becomes "12:00 AM". */
export function clock(minuteOfDay) {
  const hour24 = Math.floor(minuteOfDay / 60) % 24;
  const minute = minuteOfDay % 60;
  const hour12 = hour24 % 12 === 0 ? 12 : hour24 % 12;
  return `${hour12}:${String(minute).padStart(2, '0')} ${hour24 < 12 ? 'AM' : 'PM'}`;
}

export function range(startMinute, endMinute) {
  return `${clock(startMinute)} – ${clock(endMinute)}`;
}

/** "12 AM", "1 AM", and so on up to "11 PM": the labels down the side of a day. */
export function hourLabels() {
  return Array.from({ length: 24 }, (_, hour) => `${hour % 12 === 0 ? 12 : hour % 12} ${hour < 12 ? 'AM' : 'PM'}`);
}

/** 90 becomes "1h 30m", 120 becomes "2h", 45 becomes "45m". */
export function length(minutes) {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest}m`;
  }
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

const MAX_SHOWN = 9;

/** The count on the Unplaced Free Events button, which stops at 9+ so the button stays short. */
export function freeCountLabel(count) {
  return count > MAX_SHOWN ? `${MAX_SHOWN}+` : String(count);
}
