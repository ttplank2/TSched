/** The numbers behind the round clock: angles, hours and minutes, and 12 hour time. */

/** The angle in degrees, clockwise from 12 at the top (0 to 360), of a point (x, y) from the middle (y grows down). */
export function angleOf(x, y) {
  const degrees = (Math.atan2(x, -y) * 180) / Math.PI;
  return (degrees + 360) % 360;
}

/** The hour, 1 to 12, nearest an angle. */
export function hourFromAngle(angle) {
  const hour = Math.round(angle / 30) % 12;
  return hour === 0 ? 12 : hour;
}

/** The minute, 0 to 59, at an angle: six degrees a minute. */
export function minuteFromAngle(angle) {
  return Math.round(angle / 6) % 60;
}

/** Where a spot at `angle` degrees and `radius` from the middle is, as x and y from the middle. */
export function dialPosition(angle, radius) {
  const radians = (angle * Math.PI) / 180;
  return { x: Math.sin(radians) * radius, y: -Math.cos(radians) * radius };
}

/** An hour 1 to 12 and 'AM' or 'PM' as the hour of the day, 0 to 23. */
export function to24(hour, period) {
  return (hour % 12) + (period === 'PM' ? 12 : 0);
}

/** The hour 1 to 12 and the period for an hour of the day. */
export function from24(hour24) {
  return { hour: hour24 % 12 === 0 ? 12 : hour24 % 12, period: hour24 >= 12 ? 'PM' : 'AM' };
}

/** Typed hour and minute text as minutes after midnight, or null if either is not a valid time. */
export function parseTyped(hourText, minuteText, period) {
  if (!/^\d{1,2}$/.test(hourText) || !/^\d{1,2}$/.test(minuteText)) {
    return null;
  }
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (hour < 1 || hour > 12 || minute > 59) {
    return null;
  }
  return to24(hour, period) * 60 + minute;
}
