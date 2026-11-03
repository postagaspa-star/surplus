# Surplus

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-2.7.3-14532D)
![Privacy](https://img.shields.io/badge/data-only%20on%20your%20own%20devices-blue)

**Android app for tracking food and training through a calorie surplus** (lean bulking). Built to be fast and usable every day rather than a nutritionist-grade tracker: consistency matters more than being accurate to the gram.

No account, no ads, no subscription. Your data stays on devices you own: the phone, and if you want, your computer and your own NAS at home. Never in someone else's cloud.

## What it does

### Food

- **Food diary** organised by moments of the day (breakfast, snacks, lunch, post-workout, dinner, before bed, plus drinks with no time slot), with quantities in grams/ml required and nutritional values optional.
- **Automatic advice**, deterministic and always explainable: it compares what you ate against the pace of the day and suggests what to add, with the grams, rather than what to "avoid". No food is ever labelled as bad.
- **Barcode scanner**: point at the product and the values fill themselves in, querying two public food databases (Open Food Facts and USDA FoodData Central) in sequence. The product is kept in your pantry with its code, so the second time is instant and works offline.
- **Product card** after every entry: energy, protein, fibre, fat, sugar, salt and degree of processing, with judgements calibrated for bulking — a calorie-dense food counts in its favour here.
- **Photo recognition**, optional and on your own key (see below): photograph a plate or a label and the app proposes the foods with estimated quantities. Several shots of the same food can be grouped together, say the package and its nutrition table. Nothing reaches the diary without your confirmation.
- **Water counter** that also counts the drinks you logged, and a **weekly weigh-in** with the 3-4 week rule.
- **Full history** of every day you recorded, with the weekly average and the overall average for calories and protein.

### Training

- **Your own routines**: create them, rename them, pick the exercises and their order. One tap on a routine starts the session.
- **One exercise open at a time**: weight and reps come pre-filled and you adjust them with − and +, no keyboard. Usually a single tap on "Segna" is all it takes.
- **Automatic progression**: if last time you closed every set at the top of the rep range, the app offers one more plate and restarts from the bottom of the range.
- **Exercises filled in from their name**: type "squat con bilanciere" and the muscle, sets, reps and weight step fill themselves in, offline. Unusual names can go to the AI instead, if you set a key.
- **Per-exercise history**, plus the time of the first and last set of each session.

### Everything else

- **Reminders** for snacks only, never for main meals, on times and days you pick, with a dynamic mode tied to the end of the workout.
- **Backup** to a file, whenever you want, wherever you want.
- **Guided first run** that works your targets out from your own numbers with standard formulas; they stay editable by hand.

## On the computer too

There is a Windows version of the same app, in `pc/`: same screens, same colours, same diary file. Its window is shaped like a phone — the app was built for a tall, narrow screen, and squeezing it into a desktop layout would have made it a different app.

It is a native .NET program hosting the interface in a WebView2 (the same architecture as my other desktop apps), with no account: the diary lives in a file on the computer, in the very format the phone writes, so a backup moves between the two.

It also bundles Roboto, the font the phone uses, so the text looks the same on a computer that doesn't have it.

## Why the diary no longer lives only on the phone

Surplus started out strictly local-first: one file, on the phone, and nowhere else. That was the whole privacy story, and it was a good one — until my phone started failing. It crashed and rebooted on its own, the USB connection stopped working, and for a while three months of diary were locked inside a device I could not use. I got them back only because a backup I had exported that same morning happened to exist.

That changed how I thought about it. Keeping the data in one place is not what protects your privacy; keeping it in places **you** control is. A single copy on a single phone is private, but it is also one broken screen away from being gone. So the diary now lives in several places, all of them mine: the phone, the computer, and a small server on the NAS at home. If one of them dies, the others still have everything, and nothing ever leaves the house.

Nothing was given up for it. There is still no account and no cloud, no company in the middle and no data on anyone else's machine. Each app still works on its own with the NAS switched off — the local file is still the truth for that device — and pairing with the NAS is a choice you make, not a requirement.

## Keeping the two in step, at home

Phone and computer can share one diary through a small server you run on your own NAS (`server/`): FastAPI in a Docker container, on the home network, pairing a device with an 8-digit code. No account, nothing on anyone else's machine.

The server does not take sides. It remembers the diary each device last agreed on (the *base*) and merges three versions — base, the device's, its own:

- unchanged on both sides → stays as it is
- changed on one side only → that side wins
- changed on both → the device syncing right now wins, and the clash is counted
- new on one side → added
- in the base and now missing from one side → **it was deleted, so it goes**

That last line is what makes deletions work. Without the base, "missing" and "never existed" are the same thing, and a meal you deleted on the computer would climb back up from the phone forever. On the very first sync the base is empty: nothing is new, nothing is deleted, and two diaries that have never met are simply added together.

Each device tells the server which merged diary it actually kept (its *base*), instead of the server assuming the last reply arrived. If the Wi-Fi drops mid-reply, or you log a meal while the reply is on its way, the device keeps its own copy, sends again shortly, and nothing the other side wrote looks "deleted". Two devices syncing at the same moment are handled one at a time, so neither overwrites the other.

While an app is open it also listens to the NAS, so the two stay in step in real time. It asks `GET /api/novita` with the revision it has; the server holds the request open (up to 20 seconds) and answers the moment another device writes something, and the app then syncs. A meal logged on the phone shows up on the computer in about two seconds, and the other way round. When the app goes to the background the listening stops, so it costs no battery. If the NAS doesn't answer, the apps retry every few seconds (at most every fifteen), and as soon as it is back they send whatever you wrote meanwhile.

Both apps keep working with the NAS switched off — the local file is still the truth for that device — and send what you wrote at the first opportunity. A merged diary that would drop more than half the entries is refused rather than saved.

Both apps also update themselves from that server, so you install them by hand once. The Windows package is only installed if it carries a valid signature (ECDSA P-256, private key never in this repository); the Android one is protected by Android itself, which refuses an update signed with a different key than the installed app.

## Download

Go to the **[Releases](../../releases)** section of this repository and download the latest `.apk` for the phone, or `surplus-pc.zip` for Windows (unzip it anywhere and run `Surplus.exe` — no installer, no administrator).

Since it isn't distributed through the Play Store, Android will ask you to **allow installation from this app** (usually the browser): that's normal for apps outside the official stores, and you only confirm it once.

Updates install over the previous version without losing your data.

## Privacy

Surplus runs **offline**. The diary, weigh-ins, workouts and settings live in a file belonging to the app, on the phone — and, if you pair them, on your computer and on your own NAS. There is no Surplus service and no account: the only server that can ever exist is the one you run on your own NAS, on your own network, and only if you pair a device with it.

Something leaves the phone in four cases only, each one started by you:

| When | What goes out | Where to |
|---|---|---|
| You paired the phone with your NAS | the diary | your own NAS, on your home network, nowhere else |
| You search a food by name or scan a barcode | the search text or the product code, nothing else | Open Food Facts, then USDA FoodData Central |
| You use photo recognition | only the photos you picked | the AI service you configured yourself |
| An exercise name is one the app can't recognise | only the exercise name | the AI service you configured yourself |

**The app ships with no AI key of its own.** Anyone who wants the AI features puts in their own, in Settings (Google Gemini has a free tier, Claude is pay-as-you-go). That way no key can be pulled out of the APK to spend someone else's money, and whoever doesn't set one simply doesn't see those features. The key lives in the app's private storage and is excluded from Android's automatic backup.

## Source code

The code is here, in this repository: a native Android project in **Kotlin + Jetpack Compose**, with no database (data goes into a JSON file written safely), plus the Windows program and the NAS server. 488 automated tests in all — 164 on Android, 202 on the shared interface logic, 79 on the Windows program, 43 on the server.

```
app/src/main/kotlin/it/gaspa/surplus/
  data/           models, file storage, food databases, AI calls, NAS sync
  logic/          advice, diet quality, training rules  (pure logic, all tested)
  ui/             Compose screens
  notify/         reminders through AlarmManager
  aggiornamento/  updating itself from the NAS
app/src/test/     the Android tests
pc/               the Windows program (.NET 8, WinForms + WebView2)
server/           the NAS server (FastAPI in Docker): merging, pairing, packages
```

Comments and identifiers are in Italian, like the app itself.

### Building it

You need JDK 17 and the Android SDK (API 35). From Android Studio just open the folder; from the command line:

```
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:testDebugUnitTest # the tests
```

A signed release needs a key of your own: put `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in a `keystore.properties` file and point to it from `local.properties` with `surplus.keystore.props=/path/to/that/file` (or through the `SURPLUS_KEYSTORE_PROPS` environment variable). Without it the release still builds, simply unsigned.

The APKs published under Releases are signed with the author's key, and only those install over an earlier version without wiping the data.

## Requirements

Android 8.0 (API 26) or later.

## Disclaimer

Surplus is a personal diary, not a medical service, and it doesn't replace advice from a doctor or a dietitian. The targets it proposes are estimates from standard formulas and don't account for medical history, allergies or ongoing treatment. If you're under 18, talk to a responsible adult and to your doctor before following a hypercaloric diet.

## Licence

Personal project, all rights reserved. The code is published so that anyone can read it and check what the app actually does; redistributing it, with or without modifications, or using it as the basis for other projects, is not permitted without authorisation.
