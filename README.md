# TSched

A weekly schedule app for Android. Your week repeats, so you add each event once.

- **Fixed events** have a name, a day and a time (and optionally a place).
- **Free events** have only a length, and optionally a window of allowed times. TSched suggests where they fit around your fixed events, and nothing moves until you approve it.
- **Counted events** repeat a set number of times and then go away.
- **Notifications** before your events, for the colors you choose.
- **Accent colors,** including some secret ones. The home-screen icon follows your accent.
- Everything is saved on your phone only. TSched has no account and sends nothing anywhere.

TSched is a student project, made with Claude Code for a University of Illinois course. It runs on Android 8.0 or newer. There is no iPhone version.

## Install it on an Android phone

1. On your phone, open the [latest release](../../releases/latest).
2. Under **Assets**, tap **TSched.apk** to download it.
3. When the download finishes, tap **Open** (or open it from your Downloads or Files app).
4. Android will say it blocks installs from this source. Tap **Settings**, switch on **Allow from this source**, and go back.
5. Tap **Install**. If Google Play Protect shows a warning because the app is not from the Play Store, tap **More details**, then **Install anyway**.
6. Open **TSched** from your app drawer. Android asks to allow notifications only when you turn them on in Settings.

To update, install a newer TSched.apk over the old one. Your events stay.

To remove it, uninstall it like any app. That deletes its saved events.

TSched.apk is a test build signed with a debug key, so Android treats it as coming from an unknown source. Only install apps like this from people you trust. You can check the code here, and build the same app yourself (below).

## Build it yourself

1. Install a JDK 17 and set `JAVA_HOME` to it.
2. Install the Android SDK command-line tools, then:
   ```
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```
3. Create `local.properties` in the project folder, pointing at the SDK (this file is not committed):
   ```
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```
4. Run the tests and build:
   ```
   gradlew test assembleDebug
   ```
   The app is written to `app/build/outputs/apk/debug/app-debug.apk`.

To put it on a phone plugged in with USB debugging on, run `gradlew installDebug`.

## About this code

You are welcome to read it and learn from it. There is no license, so it is not open source: please do not copy it into your own work without asking.
