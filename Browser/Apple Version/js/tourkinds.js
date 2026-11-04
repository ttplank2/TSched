/**
 * The guided tours, with the words from the Android app. Each step lights up the controls that match its `targets`
 * (CSS selectors; none means the card sits in the middle with nothing lit).
 */

export const HOME = {
  lastLabel: 'Try It Out',
  steps: [
    {
      title: 'Welcome to TSched',
      body: 'TSched is a weekly schedule. It shows the days of a repeating week, Sunday to Saturday, not calendar dates.\n\nThis quick tour lights up the things you will use. Tap Next to go on, or Skip to leave.',
      targets: [],
    },
    {
      title: 'Your Week',
      body: 'Each card is a day. Swipe left or right, or hold the mouse and drag, to move between days. Days go around forever.',
      targets: ['.pager'],
    },
    {
      title: 'Add Event',
      body: 'Tap here to put something on your week. In a moment we will look at the form it opens.',
      targets: ['.bottom .add'],
    },
    {
      title: 'Unplaced Free Events',
      body: 'Free events do not have a time yet. They wait here until you place them: open this, tap Place Events, and TSched suggests times around your fixed events. The number is how many are waiting.',
      targets: ['.bottom .grow'],
    },
    {
      title: 'Filter',
      body: 'Show only one color of event, such as just school or just work. Double-tap it to clear the filter. In its popup, tap Edit Names to rename colors.',
      targets: ['#filter-button'],
    },
    {
      title: 'Snap to Day',
      body: 'Jump straight to any day. Double-tap it to jump to today.',
      targets: ['#snap-button'],
    },
    {
      title: 'Settings',
      body: 'This is where all the general settings are. For example, you can choose when each day’s view starts, delete many events at once, or replay this tour.',
      targets: ['.gear'],
    },
    {
      title: 'Your Events',
      body: 'Tap any event to see its details and delete it. Deleting always asks you first. An event that repeats on several days is deleted on all of its days together.',
      targets: ['.pager'],
    },
  ],
};

export const ADD = {
  lastLabel: 'Done',
  steps: [
    {
      title: 'Pick the Type',
      body: 'Fixed happens at a set time, like a class or a shift.\n\nFree needs time somewhere, like studying. You give it a length and TSched finds a spot.\n\nCounted is fixed, but it counts down, like 10 more lectures, and goes away at zero.',
      targets: ['.kind'],
    },
    {
      title: 'Name it',
      body: 'Every event needs a name. The location under it is optional.',
      targets: ['input.field'],
    },
    {
      title: 'Choose a Color',
      body: 'Colors help you tell events apart, and you can filter the calendar by color. Tap this and turn the wheel to pick one.',
      targets: ['.color-button'],
    },
    {
      title: 'Pick the Days',
      body: 'Tap each day it happens on. Choose several to make one event on every one of those days; they are edited and deleted together.',
      targets: ['.choice-row'],
    },
    {
      title: 'Set the Times',
      body: 'Fixed and Counted events need a start and an end time. Tap each button to open a clock. Free events ask for a length instead.',
      targets: ['.btn.choice', '.length'],
    },
    {
      title: 'Save it',
      body: 'Tap Save Event to put it on your calendar. Nothing is saved during this tour. Tap Done to go back to the calendar.',
      targets: ['.save'],
    },
  ],
};

/** The step after `index` going `direction` (1 or -1), or null if the tour has no more steps that way. */
export const stepAfter = (kind, index, direction) => {
  const next = index + direction;
  return next >= 0 && next < kind.steps.length ? next : null;
};
