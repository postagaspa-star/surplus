# Surplus

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-2.6.0-14532D)
![Privacy](https://img.shields.io/badge/data-on%20device%20only-blue)

**Android app for tracking food and training through a calorie surplus** (lean bulking). Built to be fast and usable every day rather than a nutritionist-grade tracker: consistency matters more than being accurate to the gram.

No account, no ads, no subscription. Your data stays on the phone.

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

## Download

Go to the **[Releases](../../releases)** section of this repository and download the latest `.apk`.

Since it isn't distributed through the Play Store, Android will ask you to **allow installation from this app** (usually the browser): that's normal for apps outside the official stores, and you only confirm it once.

Updates install over the previous version without losing your data.

## Privacy

Surplus runs **offline**. The diary, weigh-ins, workouts and settings live in a file belonging to the app, on the phone. There is no Surplus server and no account.

Something leaves the phone in three cases only, each one started by you:

| When | What goes out | Where to |
|---|---|---|
| You search a food by name or scan a barcode | the search text or the product code, nothing else | Open Food Facts, then USDA FoodData Central |
| You use photo recognition | only the photos you picked | the AI service you configured yourself |
| An exercise name is one the app can't recognise | only the exercise name | the AI service you configured yourself |

**The app ships with no AI key of its own.** Anyone who wants the AI features puts in their own, in Settings (Google Gemini has a free tier, Claude is pay-as-you-go). That way no key can be pulled out of the APK to spend someone else's money, and whoever doesn't set one simply doesn't see those features. The key lives in the app's private storage and is excluded from Android's automatic backup.

## Source code

The code is here, in this repository: a native Android project in **Kotlin + Jetpack Compose**, with no database (data goes into a JSON file written safely) and 156 automated tests on the logic.

```
app/src/main/kotlin/it/gaspa/surplus/
  data/    models, file storage, food databases, AI calls
  logic/   advice, diet quality, training rules  (pure logic, all tested)
  ui/      Compose screens
  notify/  reminders through AlarmManager
app/src/test/   the tests
```

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
