#!/usr/bin/env python3
"""
Ordbit vocabulary builder  --  the "AI sentence generator", build-time edition.

WHAT IT DOES
  1. Downloads the free Swedish Kelly-list as a plain CSV
     (codesue/kelly on GitHub; ~8,425 CEFR-graded Swedish words;
     original data from Spraakbanken / University of Gothenburg).
  2. For every word, asks Claude (the Anthropic API) for:
       - a concise English translation
       - one short, natural, correct Swedish example sentence using the word
       - the English translation of that sentence
  3. Writes  app/src/main/assets/vocab.json  -- the file the Android app loads.

WHY IT WORKS THIS WAY
  The AI step runs HERE, once, on your machine. The result is baked into the
  APK. The app itself never calls any API and stays 100% offline / standalone.
  (Kelly gives only word + level + part-of-speech, so the English translation
  has to be generated too -- not just the example sentence.)

  Your hand-made thematic categories already in vocab.json (Oregelbundna verb,
  Fraser, Mat, Djur, Tal) are kept as-is; only the CEFR levels are rebuilt.

REQUIREMENTS
  pip install anthropic                 # that's the ONLY dependency
  export ANTHROPIC_API_KEY=sk-ant-...   # Windows PowerShell: $env:ANTHROPIC_API_KEY="sk-ant-..."

USAGE
  python tools/build_vocab.py --limit 60      # quick test: 60 words, costs cents
  python tools/build_vocab.py                 # full run (~8,400 words, resumable)
  python tools/build_vocab.py --csv sv.csv    # use a CSV you downloaded yourself

COST  (one-time, rough)
  ~8,400 words in batches of 20 -> ~420 API calls. A few US dollars with a
  Sonnet model; well under a dollar with a Haiku model. Set MODEL below.

NOTE
  AI output at this scale is good but not perfect -- spot-check the result,
  especially example sentences, before shipping.
"""

import argparse
import csv
import io
import json
import os
import sys
import time
import urllib.request

# Use a current model string -- check https://docs.claude.com for names.
# A Haiku model is much cheaper for bulk work; a Sonnet model is more careful.
MODEL = "claude-sonnet-4-6"
BATCH = 20

CSV_URL = "https://raw.githubusercontent.com/codesue/kelly/main/sv.csv"

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "vocab.json")
CKPT = os.path.join(HERE, "kelly_checkpoint.json")

CEFR = {"A1", "A2", "B1", "B2", "C1", "C2"}
LEVEL_ORDER = ["A1", "A2", "B1", "B2", "C1", "C2"]
SECTION_NAMES = {
    "A1": "Nyb\u00f6rjare", "A2": "Grund", "B1": "Medel",
    "B2": "\u00d6vre medel", "C1": "Avancerad", "C2": "Expert",
}
POS_HINTS = ("noun", "verb", "adj", "adverb", "pron", "prep",
             "conj", "numer", "interj", "particip", "subst")

PROMPT_HEAD = (
    "You are a Swedish lexicographer making vocabulary flashcards for English "
    "speakers learning Swedish.\n"
    "For each numbered Swedish word below, return ONE JSON array. Each element:\n"
    '  {"i": <number>,\n'
    '   "en": "<concise English translation>",\n'
    '   "exSv": "<one short, natural, grammatically correct Swedish sentence '
    'that uses the word>",\n'
    '   "exEn": "<English translation of that sentence>"}\n'
    "Rules: the example sentence MUST contain the word; keep it short and at a "
    "beginner-friendly level; output ONLY the JSON array, no extra text.\n\n"
    "Words:\n"
)


def read_csv(path):
    """Return the Kelly list as a list of dict rows (from disk or GitHub)."""
    if path:
        with open(path, encoding="utf-8-sig", newline="") as f:
            text = f.read()
    else:
        print(f"Downloading the Swedish Kelly-list from\n  {CSV_URL}")
        req = urllib.request.Request(CSV_URL, headers={"User-Agent": "ordbit"})
        with urllib.request.urlopen(req, timeout=90) as resp:
            text = resp.read().decode("utf-8-sig")
    first = text.splitlines()[0] if text else ""
    delim = ";" if first.count(";") > first.count(",") else ","
    return list(csv.DictReader(io.StringIO(text), delimiter=delim))


def detect_columns(rows):
    """Figure out which columns hold the CEFR level, the word and the POS."""
    cols = [c for c in rows[0].keys() if c is not None]
    sample = rows[:600]

    def vals(c):
        return [(r.get(c) or "").strip() for r in sample]

    def is_numeric(c):
        v = [x for x in vals(c) if x]
        if not v:
            return False
        ok = 0
        for x in v:
            try:
                float(x.replace(",", "."))
                ok += 1
            except ValueError:
                pass
        return ok >= 0.9 * len(v)

    def distinct(c):
        return len({x for x in vals(c) if x})

    cefr_col = None
    for c in cols:
        v = [x.upper() for x in vals(c) if x]
        if v and sum(1 for x in v if x in CEFR) >= 0.85 * len(v):
            cefr_col = c
            break

    text_cols = [c for c in cols if c != cefr_col and not is_numeric(c)]
    lemma_col = max(text_cols, key=distinct) if text_cols else None

    pos_col = None
    for c in text_cols:
        if c == lemma_col:
            continue
        v = [x.lower() for x in vals(c) if x]
        if v and sum(1 for x in v if any(h in x for h in POS_HINTS)) >= 0.5 * len(v):
            pos_col = c
            break

    return cefr_col, lemma_col, pos_col


def load_kelly(path, limit):
    rows = read_csv(path)
    if not rows:
        sys.exit("The CSV is empty.")
    cefr_col, lemma_col, pos_col = detect_columns(rows)
    print(f"Detected columns -> level: {cefr_col!r}  word: {lemma_col!r}  "
          f"pos: {pos_col!r}")
    if not cefr_col or not lemma_col:
        sys.exit("Could not detect the needed columns. CSV header is:\n  "
                 + ", ".join(repr(c) for c in rows[0].keys()))

    out, seen = [], set()
    for r in rows:
        level = (r.get(cefr_col) or "").strip().upper()
        lemma = (r.get(lemma_col) or "").strip()
        # keep the main form only, drop stylistic variants / comments
        lemma = lemma.split(",")[0].split("|")[0].split("(")[0].strip()
        pos = (r.get(pos_col) or "").strip() if pos_col else ""
        key = lemma.lower()
        if level in SECTION_NAMES and lemma and key not in seen:
            seen.add(key)
            out.append({"section": level, "sv": lemma, "pos": pos})
    out.sort(key=lambda w: (LEVEL_ORDER.index(w["section"]), w["sv"]))
    return out[:limit] if limit else out


def call_api(client, batch):
    prompt = PROMPT_HEAD + "\n".join(
        f'{i + 1}. {w["sv"]}' for i, w in enumerate(batch)
    )
    msg = client.messages.create(
        model=MODEL,
        max_tokens=4000,
        messages=[{"role": "user", "content": prompt}],
    )
    text = "".join(b.text for b in msg.content if b.type == "text").strip()
    lo, hi = text.find("["), text.rfind("]")
    if lo == -1 or hi == -1:
        raise ValueError("no JSON array in the model response")
    return json.loads(text[lo:hi + 1])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0,
                    help="process only the first N words (for testing)")
    ap.add_argument("--csv", default="",
                    help="path to a Kelly sv.csv you downloaded yourself")
    args = ap.parse_args()

    key = os.environ.get("ANTHROPIC_API_KEY")
    if not key:
        sys.exit("Set your key first:  export ANTHROPIC_API_KEY=sk-ant-...")
    try:
        import anthropic
    except ImportError:
        sys.exit("Missing dependency. Run:  pip install anthropic")
    client = anthropic.Anthropic(api_key=key)

    rows = load_kelly(args.csv, args.limit)
    print(f"Kelly words to process: {len(rows)}  (model: {MODEL})")

    done = {}
    if os.path.exists(CKPT):
        with open(CKPT, encoding="utf-8") as f:
            done = json.load(f)
        print(f"Resuming -- {len(done)} words already generated.")

    for start in range(0, len(rows), BATCH):
        batch = rows[start:start + BATCH]
        if all(w["sv"] in done for w in batch):
            continue
        for attempt in range(4):
            try:
                result = call_api(client, batch)
                for item in result:
                    idx = int(item.get("i", 0)) - 1
                    if 0 <= idx < len(batch):
                        w = batch[idx]
                        done[w["sv"]] = {
                            "section": w["section"],
                            "sv": w["sv"],
                            "en": str(item.get("en", "")).strip(),
                            "pos": w["pos"],
                            "exSv": str(item.get("exSv", "")).strip(),
                            "exEn": str(item.get("exEn", "")).strip(),
                        }
                break
            except Exception as e:
                wait = 3 * (attempt + 1)
                print(f"  batch at {start} attempt {attempt + 1} failed: {e} "
                      f"-- retrying in {wait}s")
                time.sleep(wait)
        with open(CKPT, "w", encoding="utf-8") as f:
            json.dump(done, f, ensure_ascii=False)
        print(f"  progress: {len(done)}/{len(rows)}")

    # Keep hand-made thematic categories from the existing vocab.json.
    cat_sections, cat_words = [], []
    if os.path.exists(OUT):
        try:
            with open(OUT, encoding="utf-8") as f:
                seed = json.load(f)
            cat_ids = {s["id"] for s in seed.get("sections", [])
                       if s.get("category")}
            cat_sections = [s for s in seed.get("sections", [])
                            if s.get("category")]
            cat_words = [w for w in seed.get("words", [])
                         if w.get("section") in cat_ids]
            if cat_sections:
                print(f"Keeping {len(cat_sections)} thematic categories "
                      f"({len(cat_words)} words) from the existing file.")
        except Exception:
            pass

    generated = list(done.values())
    cefr_sections = [
        {"id": sid, "name": SECTION_NAMES[sid], "category": False}
        for sid in LEVEL_ORDER
        if any(w["section"] == sid for w in generated)
    ]

    out = {
        "sections": cefr_sections + cat_sections,
        "words": generated + cat_words,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"\nDone. Wrote {os.path.normpath(OUT)} "
          f"with {len(out['words'])} words in {len(out['sections'])} sections.")
    print("Open the project in Android Studio and Run -- the new words are in.")


if __name__ == "__main__":
    main()
