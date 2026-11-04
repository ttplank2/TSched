/**
 * The numbers behind the calendar's endless week: pages wrap Sunday to Saturday and around again forever, so swiping
 * past Saturday comes back to Sunday. Also the rule for telling a sideways swipe from a vertical scroll. Follows
 * WeekPages.java and DayScrollView.java in the Android app.
 */

export const DAYS_IN_WEEK = 7;

/** Which day of the week (0 = Sunday) a page shows, for any whole page number, negative too. */
export const dayOfPage = page => ((page % DAYS_IN_WEEK) + DAYS_IN_WEEK) % DAYS_IN_WEEK;

/** The page that shows `dayIndex` needing the fewest swipes from `current`. */
export function nearestPage(current, dayIndex) {
  let swipes = dayOfPage(dayIndex - current);
  if (swipes > 3) {
    swipes -= DAYS_IN_WEEK;
  }
  return current + swipes;
}

/**
 * What a finger or mouse drag is doing once it has moved: 'sideways' to swipe between days, 'vertical' to scroll the
 * day, or 'undecided' while it has moved less than the slop. It is sideways only when it is more sideways than
 * vertical (steeper than 45 degrees the other way counts as vertical), so a diagonal swipe that is mostly up or down
 * scrolls.
 */
export function swipeIntent(dx, dy, slop) {
  const sideways = Math.abs(dx);
  const vertical = Math.abs(dy);
  if (sideways <= slop && vertical <= slop) {
    return 'undecided';
  }
  return sideways > slop && sideways > vertical ? 'sideways' : 'vertical';
}

/**
 * The page to settle on after a drag is let go: the nearest page, nudged one further by a quick flick in the
 * direction it was going, but never more than one page from where the drag began. `position` is in pages (3.4 is
 * 40% of the way from page 3 to page 4); `velocity` is pages a second.
 */
export function settlePage(position, velocity, start) {
  const flick = 1.5;
  let target = Math.round(position);
  if (velocity > flick) {
    target = Math.max(target, Math.floor(position) + 1);
  } else if (velocity < -flick) {
    target = Math.min(target, Math.ceil(position) - 1);
  }
  return Math.max(start - 1, Math.min(start + 1, target));
}
