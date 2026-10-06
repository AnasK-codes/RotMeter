# RotMeter

RotMeter is a college Android Lab and DBMS Lab app (`com.rotmeter.app`). It reads today's foreground time for the apps seeded in its database through Android's `UsageStatsManager`, stores whole minutes locally, and computes a Rot Score with SQLite. Positive weights increase the score; productive apps have negative weights that reduce it. The rounded daily score never goes below zero.

The app uses Kotlin, XML layouts, ViewBinding, one activity, and four fragments:

- **Home:** animated score ring and roast, player level and XP, clean-day streaks, daily quests, badges, productive credits, top three rot apps, week comparison, manual sync, and demo loading.
- **Stats:** animated seven-day gradient bar chart with today highlighted and a dashed average, worst-app accent card, days below the recorded-day average, and weekly app totals with share bars.
- **Limits:** app initials, category chips, limit pills, and today’s usage bars (green below 70%, amber from 70% through 100%, red above 100%). Tap an app to save a daily limit of 5–600 minutes or remove its limit.
- **Alerts:** stored roasts, newest first, in flame-marked chat bubbles with app chips and relative creation times. SQL triggers create roasts when usage exceeds a limit, and pending roasts can appear as notifications.

App data uses raw `SQLiteOpenHelper` with foreign keys enabled. All application SQL lives in [`Sql.kt`](../app/src/main/java/com/rotmeter/app/db/Sql.kt); database work runs on a shared background executor. The app makes no network calls. A unique WorkManager task is configured for a 15-minute interval; actual background execution is controlled by Android.

## Build and install

Open the existing project in Android Studio and let Gradle sync. Install Android SDK platform 37 and use the project's configured Java 25 Gradle daemon toolchain. The existing wrapper uses Gradle 9.5.0; `compileSdk` and `targetSdk` remain 37 and `minSdk` is 26. Do not replace the project or change its build versions.

From the project root on macOS/Linux:

```sh
./gradlew assembleDebug
./gradlew lint
```

For a clean build, use `./gradlew clean assembleDebug`. On Windows, use `gradlew.bat` instead of `./gradlew`.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Run the app from Android Studio on an Android 8.0/API 26 or newer device, or install that APK. If Android platform-tools is on your PATH, installation over USB is:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug startup runs a background trigger self-test. Filter Logcat by `RotSelfTest` to see `PASS: limit insert trigger created an alert; test changes rolled back.` The test leaves existing limits and usage unchanged and is excluded from release builds.

## Grant usage access

1. Open **Home** and tap **Sync now**.
2. In the explanation dialog, tap **Open settings**.
3. Select **RotMeter** on Android's usage-access screen and enable usage access. The setting's wording varies by phone.
4. Return to RotMeter; Home rechecks access and syncs after a grant. Later, tap **Sync now** to refresh manually.

Sync reads today's usage in the device's time zone, combines duplicate package records, and rounds accumulated milliseconds down to whole minutes. Only the 12 packages seeded in `apps` are eligible; other installed apps are ignored. The success toast reports how many tracked app records were updated, not the total number of installed apps. With access denied, the app shows the explanation instead of crashing.

On Android 13/API 33 or newer, allow notifications when prompted if you want roast notifications. The permission is requested once; if it was denied, enable it later in Android's app notification settings. Denial does not prevent sync or the Alerts list, and alerts remain pending until they can be posted.

## Load demo data

1. Open **Home**, scroll to the buttons if needed, and tap **Load demo data**. Usage access is not required.
2. Wait for loading to finish. The fixed random seed (`42`) inserts repeatable data for the last 14 days, including today, across 10 tracked apps.
3. Before a real usage sync changes today's rows, expect a score of **97**, level **Mild Rot**, and **58 min** of productive credits. The top three rot apps are Instagram, YouTube, and X.
4. Open **Stats**, **Limits**, and **Alerts** to inspect the generated data, limits, and SQL-created roasts.

Demo loading replaces all existing usage, alerts, and credits. It preserves app/category records and existing limits. Only when no app has a limit does it install the defaults: Instagram 60, YouTube 60, Snapchat 30, X 30, Reddit 45, and Netflix 60 minutes. Triggers generate credits and alerts using whichever limits are present; older demo alerts are marked notified so only today's roasts are eligible for notification delivery. Loading again recreates the same minute values relative to the current date. A later manual or background usage sync can overwrite today's demo values for packages Android reports.

## Home gamification

Gamification reads the existing tables and views; the database schema and version remain unchanged. For each recorded usage day, XP is weighted productive minutes times two, plus 30 when no alerts exist for that day, plus 50 when its score is strictly below the average score of all recorded days. Fractional XP is accumulated before the total is rounded down to whole XP. Each 200 XP adds a player level; the progress bar shows XP within the current level out of 200.

A clean day has a score strictly below that same average and zero alerts. Consecutive calendar dates count toward streaks; missing days break them. Yesterday's streak stays current until today's first usage row arrives. Quests and the Bookworm badge use weighted productive minutes from the daily view; the existing productive-credits label still shows raw minutes from `credits`.

With the default demo limits, Home shows **Lv. 12**, **Brain Monk**, **XP 125 / 200**, a **1 day streak** with **Best: 1**, and three green completed quests (49.2 weighted productive minutes, Instagram 56 / 60 min, zero roasts today). First Blood and Bookworm are unlocked. Scroll horizontally through the badge grid and tap a badge for its description and status. Custom existing limits can change XP, streaks, quests, and badge status. A level increase after a refresh displays an animated trophy dialog; loading the same data again at the same level does not repeat it.

## Custom roast sound and optional soft blocking

Roast notifications use the bundled `res/raw/fahhhhh.mp3` through the **Roast alerts · Fahhhhh sound** notification channel. The versioned channel applies the new default to existing installations; notification mute and explicit sound choices from the old channel are preserved. Sound remains subject to Android's notification volume, channel settings, and Do Not Disturb. No audio plays merely from opening the app.

Soft blocking is **off by default**. Open **Limits**, save a daily limit, and turn on **Block this app at limit** for each app you want to restrict. Enable the **Soft blocking** master switch. **Blocking setup** explains the access used and opens Android settings: grant Usage Access, then enable **RotMeter Soft Blocking** under Accessibility. Return to Limits to see whether both permissions are ready.

The service uses window package metadata and real UsageStats foreground history, including ongoing sessions, to check selected apps. It returns to the phone's Home screen when real whole minutes reach or exceed the selected app's limit, and shows which app reached it. Checks run when a selected app opens and approximately every five seconds while it stays active. Blocking does not use demo rows, write screen-time data, or change the SQLite schema/version. Existing roast triggers still fire only when logged minutes strictly exceed a limit.

Turn the master switch **off** to allow all apps immediately. Per-app selections are retained for your next enable. Deselect an app or remove its limit to allow that app. RotMeter stays accessible so you can change these controls; pending checks re-read switches before acting and are cancelled on disabling. Without both required permissions, on screen lock, or after a failed reading, the service allows access. This is a soft restriction that can also be disabled from Android Accessibility settings.

## DBMS submission files

- [`schema.sql`](schema.sql): executable SQLite tables, views, triggers, and category/app seeds copied from `Sql.kt`. Run once against an empty database; it does not contain the 14-day demo usage or default demo limits.
- [`queries.md`](queries.md): important SQL, parameter order, two-line explanations, and the screens that use it.
- [`er_diagram.md`](er_diagram.md): Mermaid diagram with keys, relationships, and normalization notes.

For a desktop SQLite viva demonstration, run this from the project root with SQLite installed, choosing a new database file:

```sh
sqlite3 rotmeter-viva.db < docs/schema.sql
```

The exported schema includes `PRAGMA foreign_keys=ON` for that connection. Enable it again in each new desktop SQLite connection; the Android helper enables it through `onConfigure`.
