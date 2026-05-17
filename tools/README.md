# Ordbit — vocabulary builders

Two scripts grow the dictionary to full size (~8,000 words). Both write
`../app/src/main/assets/vocab.json` — the file the app loads at startup.
**Either way the app stays 100% offline**: the work happens here, once, and the
finished `vocab.json` is bundled into the APK.

Pick one:

## Option A — `build_vocab_free.py`  (no API key, no cost)

Combines two free resources:
- the **Swedish Kelly-list** — the word list + a CEFR level per word
- **Folkets lexikon** (KTH) — a human-made English translation, plus an example
  sentence where the dictionary has one

Tradeoff: every word gets a real translation, but example sentences appear only
for the words Folkets already has examples for — not for every word.

**No dependencies, no key, no cost** — pure Python standard library.

```bash
# run from the project root (the Ordbit folder)
python tools/build_vocab_free.py --limit 200     # quick test
python tools/build_vocab_free.py                 # full build
```

If a download is blocked on your network, fetch the files yourself and pass
them in — Kelly `sv.csv` from https://github.com/codesue/kelly and Folkets
`folkets_sv_en_public.xml` from https://folkets-lexikon.csc.kth.se/folkets/ :

```bash
python tools/build_vocab_free.py --kelly sv.csv --folkets folkets_sv_en_public.xml
```

## Option B — `build_vocab.py`  (AI sentence generator, needs an API key)

Pulls the Kelly word list and uses the Anthropic API to generate, for **every**
word, an English translation and a fresh Swedish example sentence with its
translation. So every card has an example.

Needs an Anthropic API key (from console.anthropic.com — this is separate from
a claude.ai subscription) and costs a small one-time amount: a few dollars with
a Sonnet model, under a dollar with a Haiku model.

```bash
pip install anthropic
export ANTHROPIC_API_KEY=sk-ant-...          # PowerShell: $env:ANTHROPIC_API_KEY="sk-ant-..."
python tools/build_vocab.py --limit 60       # quick test
python tools/build_vocab.py                  # full run (resumable)
```

## Either way

- After it finishes, open the project in Android Studio and press **Run** —
  the new `vocab.json` is already in place.
- Your hand-made thematic categories (Oregelbundna verb, Fraser & uttryck,
  Mat & dryck, Djur & natur, Tal & tid) are kept; only the CEFR levels A1–C2
  are rebuilt.
- **Check the output** before shipping — skim `vocab.json` for anything odd.
- Sources are free but licensed: the Swedish Kelly-list and Folkets lexikon are
  both Creative Commons (Univ. of Gothenburg / KTH). Keep the attribution if you
  publish the app.
