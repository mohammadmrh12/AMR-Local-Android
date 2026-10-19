# AMR Local — Al-Habbubi Hospital

Private, offline-first Android app for local antimicrobial-resistance reporting.

## Current features
- Android app built with Kotlin, Jetpack Compose and Material 3.
- Year and month dropdowns; values and population inputs are saved locally.
- Searchable antibiotic catalogue with ATC code, strength/dose and dosage-form badges.
- Favorites and quantity entry for each antibiotic.
- Creates a report copy from the bundled original Excel template, fills the selected year, population values and entered quantities, saves it in app storage, then opens Android's share sheet.
- Arabic in-app instructions; burgundy/orange interface.
- No account or backend required.

## Project layout
- `app/src/main/java/com/medstock/amr/MainActivity.kt` — Android UI and report generation.
- `app/src/main/assets/products.json` — searchable antibiotic catalogue.
- `app/src/main/assets/amr_template.xlsx` — bundled Excel workbook template.

## Build
- JDK 17
- Android SDK (compile SDK 35)
- Gradle 8.9

From the project root:

```sh
JAVA_HOME=/path/to/jdk-17 /path/to/gradle-8.9/bin/gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Status
Debug APK build succeeded on the Mac development machine. The app has not yet been confirmed through an on-device install and runtime test. The report workbook output should be validated against the expected AMR reporting workflow before production use.
