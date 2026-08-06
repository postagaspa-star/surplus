# Surplus

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-2.2.0-14532D)
![Privacy](https://img.shields.io/badge/data-on%20device%20only-blue)

**Android app for tracking food and training through a calorie surplus** (lean bulking). Built to be fast and usable every day rather than a nutritionist-grade tracker: consistency matters more than being accurate to the gram.

No account, no ads, no subscription. Your data stays on the phone.

## What it does

- **Food diary** organised by moments of the day (breakfast, snacks, lunch, post-workout, dinner), with quantities in grams/ml required and nutritional values optional.
- **Automatic advice**, deterministic and always explainable: it compares what you ate against your plan and suggests what to add rather than what to "avoid", since no food is ever labelled as bad.
- **Barcode scanner**: point at the product and the values fill themselves in, querying two public food databases (Open Food Facts and USDA FoodData Central) in sequence.
- **Product card** in the style of Yuka after every entry: energy, protein, fibre, fat, sugar, salt and degree of processing, with judgements calibrated for bulking (a calorie-dense food counts in its favour here).
- **Training section**: Push/Pull sessions, a "last time" history for every exercise, and a progression prompt when it's time to add load.
- **Reminders** for snacks only (never for main meals), on times and days you pick, with a dynamic mode tied to the end of the workout.
- **Weekly weigh-in** with one rule: if the weight hasn't moved after 3-4 weeks, the app tells you.
- **Backup** to a file, whenever you want, wherever you want.

## Download

Go to the **[Releases](../../releases)** section of this repository and download the latest `.apk`.

Since it isn't distributed through the Play Store, Android will ask you to **allow installation from this app** (usually the browser): that's normal for apps outside the official stores, and you only confirm it once.

Updates install over the previous version without losing your data.

## Privacy

Surplus runs entirely offline. The only exception is the optional product lookup (by name or barcode): in that case a request goes to the public food databases named above, containing only the search text or the product code, with **no personal data and nothing that identifies the user**. Everything else (diary, weigh-ins, workouts, settings) is stored on the phone alone.

## Requirements

Android 8.0 (API 26) or later.

## Disclaimer

Surplus is a personal diary, not a medical service, and it doesn't replace advice from a doctor or a dietitian. The targets it proposes are estimates from standard formulas and don't account for medical history, allergies or ongoing treatment. If you're under 18, talk to a responsible adult and to your doctor before following a hypercaloric diet.

## Source code

This repository holds only the landing page and the compiled releases: the source code is not published. For reports or questions, open an [Issue](../../issues).

## Licence

Personal project, all rights reserved. The app is distributed free of charge for personal use; redistributing it with modifications, or using it as the basis for other projects, is not permitted without authorisation.
