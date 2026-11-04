# TSched for the browser (and iPhone)

A weekly schedule app that runs in a web browser, so it works on a computer, an iPhone, an iPad, or any phone. Your week repeats, so you add each event once.

**Open it:** https://ttplank2.github.io/TSched/

> **This is not a fully tested version, and it is lacking a few features.** It is the browser version of [TSched for Android](../../Android%20Version), made quickly so people on Apple devices can try the app. Expect rough edges. Some things that are in the Android app are missing or work differently here, such as notifications, vibration and sound, and some of the polish. If something looks wrong, it is probably a bug in this version and not something you did.

- **Fixed events** have a name, days and a time (and optionally a place). Pick several days and one event is made on each.
- **Free events** have only a length, and optionally the days they may happen on. They wait in *Unplaced Free Events* until you tap *Place Events*, which suggests times around your fixed events. Nothing moves until you approve it.
- **Counted events** repeat a set number of times and then go away.
- Swipe (or hold the mouse button and drag) to move between days. The days go around forever. Tap or hold an event to see it, edit it, or delete it.
- Everything is saved in your own browser, on your own device. There is no account, and nothing is sent anywhere. Clearing your browser's website data erases your events.

## Put it on an iPhone or iPad

You do not download anything from the App Store. You open the link and add it to your Home Screen, and it then opens full screen like an app.

1. On your iPhone, open **Safari** (it must be Safari for this to work).
2. Go to **https://ttplank2.github.io/TSched/**
3. Tap the **Share** button (the square with an arrow pointing up, at the bottom of the screen).
4. Scroll down and tap **Add to Home Screen**.
5. Tap **Add** in the top right corner.
6. Open **TSched** from your Home Screen.

Your events are kept on that device, in Safari's storage for this website. If you clear Safari's website data, they are erased, and events on your iPhone are separate from events on a computer or on the Android app.

## Use it on a computer

Open https://ttplank2.github.io/TSched/ in any current browser. It is laid out like a phone screen, in a column in the middle of the page.

## Run it yourself

It is plain HTML, CSS and JavaScript with no build step. From this folder, start any static web server, for example `python -m http.server`, and open `http://localhost:8000`.

To run the tests you need [Node.js](https://nodejs.org): `npm test`.

## Fonts

Sora and Pacifico, both under the [SIL Open Font License 1.1](https://openfontlicense.org).

## About this code

TSched is a student project, made with Claude Code for a University of Illinois course. You are welcome to read it and learn from it. There is no license, so it is not open source: please do not copy it into your own work without asking.
