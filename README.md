# Ordbit — Svenska

A **standalone, fully offline Android app** for learning Swedish vocabulary
with English translations. Built like WordBit: a native Android app in Kotlin
+ Jetpack Compose. There is **no web app, no website and no server**.

## Why it is genuinely standalone

- **No network.** The `INTERNET` permission is deliberately left out of
  `AndroidManifest.xml`. The app cannot make a single network request.
- **All progress stays on the device.** Word marks, favourites, theme and
  badges are saved by Android's Jetpack DataStore in the app's private
  storage. Nothing leaves the phone; there is no account.
- **Audio is on-device** — Android's built-in Swedish TextToSpeech engine.
- **The dictionary is bundled** in `assets/vocab.json` inside the APK.

## What's inside

- A bundled dictionary in `app/src/main/assets/vocab.json`. It ships with a
  curated **250-word starter set** (5 CEFR levels + 5 thematic categories,
  each with an example sentence) and can be regenerated to full size — see
  *Growing the dictionary* below.
- **Three-state marking**, like WordBit — every word is **Kan inte** (don't
  know), **Osäker** (unsure) or **Kan** (know). Stored on the device only.
- **4 study modes:** Lås (one detailed card), Kort (flashcards), Test
  (multiple choice), Lista (list — tap a row to cycle the mark).
- **6 colour themes** and a **Statistik / Märken** panel (the ◐ button).

## Growing the dictionary to full size

`tools/` has two scripts that rebuild `assets/vocab.json` to full size
(~8,000 words) from the free **Swedish Kelly-list**. Either way the AI/lookup
step runs **once, on your machine** — the finished file is baked into the APK,
so the app itself never calls anything and stays fully offline.

- `build_vocab_free.py` — **no API key, no cost.** Uses Folkets lexikon (a free
  human-made Sw-En dictionary) for translations and example sentences. Pure
  Python, no dependencies:

  ```bash
  python tools/build_vocab_free.py --limit 200     # quick test
  python tools/build_vocab_free.py                 # full build
  ```

- `build_vocab.py` — AI sentence generator. Uses the Anthropic API to write a
  fresh example sentence for *every* word (needs an API key, small one-time
  cost).

See `tools/README.md` for details.

## How to build the APK

You need **Android Studio** (Ladybug / 2024.2 or newer).

1. Open Android Studio → **Open** → select this `Ordbit` folder.
2. Let Gradle finish syncing (it downloads the SDK components automatically).
3. To install on a phone: connect a device with USB debugging on (or start an
   emulator), then press **Run ▶**.
4. For a shareable APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   The debug APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

## Note on Swedish audio

The first time you tap **Uttala**, the phone needs a Swedish TextToSpeech
voice. If you hear nothing, install it via **Settings → System → Languages &
input → Text-to-speech output → install voice data → Swedish**. Still fully
on-device.

## Project structure

```
Ordbit/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── tools/
│   ├── build_vocab_free.py          (free builder — Kelly + Folkets lexikon)
│   ├── build_vocab.py               (AI builder — Anthropic API)
│   └── README.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml      (no INTERNET permission)
        ├── assets/vocab.json        (the bundled dictionary)
        ├── res/                     (icon, theme, strings)
        └── java/com/ordbit/svenska/
            ├── MainActivity.kt      (entry point + TextToSpeech)
            ├── Vocab.kt             (loads assets/vocab.json)
            ├── Store.kt             (on-device DataStore persistence)
            └── ui/
                ├── Theme.kt         (6 palettes + status colors)
                └── OrdbitApp.kt     (ViewModel + all UI / modes)
```

App id: `com.ordbit.svenska` · min Android 7.0 (API 24) · offline-only.
