#!/usr/bin/env python3
"""
Ordbit vocabulary builder  --  FREE / no-API edition.

Builds the full dictionary with NO API key and NO cost. It combines two free
resources:
  - the Swedish Kelly-list  -> the word list + a CEFR level for each word
  - Folkets lexikon (KTH)   -> a human-made English translation, plus an
                               example sentence where the dictionary has one

The result is written to  app/src/main/assets/vocab.json , bundled into the
offline APK.

TRADEOFF vs. the AI builder (build_vocab.py):
  Every word gets a real dictionary translation, but example sentences appear
  only for the words Folkets already has examples for -- not for every word.
  In return: no API key, no cost, and no dependencies to install.

REQUIREMENTS
  None. Pure Python standard library. No pip install, no API key.

USAGE (run from the project root -- the Ordbit folder)
  python tools/build_vocab_free.py                     # full build
  python tools/build_vocab_free.py --limit 200         # quick test
  python tools/build_vocab_free.py --kelly sv.csv --folkets folkets.xml
                                                       # use local files

The Folkets download is a few tens of MB; the build then runs in seconds.
"""

import argparse
import csv
import io
import json
import os
import sys
import urllib.request
import xml.etree.ElementTree as ET

KELLY_URL = "https://raw.githubusercontent.com/codesue/kelly/main/sv.csv"
FOLKETS_URL = ("https://folkets-lexikon.csc.kth.se/folkets/"
               "folkets_sv_en_public.xml")

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "vocab.json")

CEFR = {"A1", "A2", "B1", "B2", "C1", "C2"}
LEVEL_ORDER = ["A1", "A2", "B1", "B2", "C1", "C2"]
SECTION_NAMES = {
    "A1": "Nyb\u00f6rjare", "A2": "Grund", "B1": "Medel",
    "B2": "\u00d6vre medel", "C1": "Avancerad", "C2": "Expert",
}
POS_HINTS = ("noun", "verb", "adj", "adverb", "pron", "prep",
             "conj", "numer", "interj", "particip", "subst")
# Folkets word-class codes -> readable Swedish part of speech
POS_MAP = {
    "nn": "substantiv", "vb": "verb", "jj": "adjektiv", "ab": "adverb",
    "pp": "preposition", "pn": "pronomen", "rg": "r\u00e4kneord",
    "ro": "r\u00e4kneord", "nl": "r\u00e4kneord", "kn": "konjunktion",
    "sn": "konjunktion", "in": "interjektion", "pm": "egennamn",
    "abbrev": "f\u00f6rkortning",
}


# ---------- Kelly word list (the CSV) ----------

def read_csv(path):
    if path:
        with open(path, encoding="utf-8-sig", newline="") as f:
            text = f.read()
    else:
        print(f"Downloading the Swedish Kelly-list from\n  {KELLY_URL}")
        req = urllib.request.Request(KELLY_URL, headers={"User-Agent": "ordbit"})
        with urllib.request.urlopen(req, timeout=90) as resp:
            text = resp.read().decode("utf-8-sig")
    first = text.splitlines()[0] if text else ""
    delim = ";" if first.count(";") > first.count(",") else ","
    return list(csv.DictReader(io.StringIO(text), delimiter=delim))


def detect_columns(rows):
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
        sys.exit("The Kelly CSV is empty.")
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
        lemma = lemma.split(",")[0].split("|")[0].split("(")[0].strip()
        pos = (r.get(pos_col) or "").strip() if pos_col else ""
        key = lemma.lower()
        if level in SECTION_NAMES and lemma and key not in seen:
            seen.add(key)
            out.append({"section": level, "sv": lemma, "pos": pos})
    out.sort(key=lambda w: (LEVEL_ORDER.index(w["section"]), w["sv"]))
    return out[:limit] if limit else out


# ---------- Folkets lexikon (the dictionary XML) ----------

def _tag(elem):
    return elem.tag.split("}")[-1].lower()


def load_folkets(path):
    """Return {swedish_word_lowercase: {en, exSv, exEn, pos}}."""
    if path:
        source = open(path, "rb")
    else:
        print(f"Downloading Folkets lexikon (a few tens of MB) from\n"
              f"  {FOLKETS_URL}")
        req = urllib.request.Request(FOLKETS_URL,
                                     headers={"User-Agent": "ordbit"})
        source = urllib.request.urlopen(req, timeout=240)

    lex = {}
    examples = 0
    try:
        for _event, elem in ET.iterparse(source, events=("end",)):
            if _tag(elem) != "word":
                continue
            sv = (elem.get("value") or "").strip()
            if sv:
                key = sv.lower()
                en = ""
                ex_sv = ex_en = ""
                for child in list(elem):
                    ctag = _tag(child)
                    if ctag == "translation" and not en:
                        en = (child.get("value") or "").strip()
                    elif ctag == "example" and not ex_sv:
                        ev = (child.get("value") or "").strip()
                        etr = next((g for g in list(child)
                                    if _tag(g) == "translation"), None)
                        if ev and etr is not None:
                            ex_sv = ev
                            ex_en = (etr.get("value") or "").strip()
                pos = (elem.get("class") or "").strip()
                rec = lex.get(key)
                if rec is None:
                    lex[key] = {"en": en, "exSv": ex_sv, "exEn": ex_en,
                                "pos": pos}
                    if ex_sv:
                        examples += 1
                else:
                    if not rec["en"] and en:
                        rec["en"] = en
                    if not rec["exSv"] and ex_sv:
                        rec["exSv"], rec["exEn"] = ex_sv, ex_en
                        examples += 1
            elem.clear()
    finally:
        source.close()

    print(f"Folkets: {len(lex)} words parsed, "
          f"{examples} with an example sentence.")
    if not lex:
        sys.exit("Folkets parsing produced nothing -- the file format may "
                 "have changed. Try --folkets with a manually downloaded XML.")
    return lex


# ---------- merge + write ----------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0,
                    help="process only the first N words (for testing)")
    ap.add_argument("--kelly", default="",
                    help="path to a Kelly sv.csv you downloaded yourself")
    ap.add_argument("--folkets", default="",
                    help="path to a folkets_sv_en_public.xml you downloaded")
    args = ap.parse_args()

    kelly = load_kelly(args.kelly, args.limit)
    print(f"Kelly words: {len(kelly)}")
    lex = load_folkets(args.folkets)

    words, missing, with_example = [], 0, 0
    for w in kelly:
        rec = lex.get(w["sv"].lower())
        if not rec or not rec["en"]:
            missing += 1
            continue
        pos = POS_MAP.get(rec["pos"], rec["pos"]) or w["pos"]
        if rec["exSv"]:
            with_example += 1
        words.append({
            "section": w["section"], "sv": w["sv"], "en": rec["en"],
            "pos": pos, "exSv": rec["exSv"], "exEn": rec["exEn"],
        })

    print(f"Matched {len(words)} words "
          f"({with_example} have an example sentence); "
          f"{missing} Kelly words were not in Folkets and were skipped.")
    if not words:
        sys.exit("No words matched -- nothing written.")

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

    cefr_sections = [
        {"id": sid, "name": SECTION_NAMES[sid], "category": False}
        for sid in LEVEL_ORDER
        if any(w["section"] == sid for w in words)
    ]
    out = {
        "sections": cefr_sections + cat_sections,
        "words": words + cat_words,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"\nDone. Wrote {os.path.normpath(OUT)} "
          f"with {len(out['words'])} words in {len(out['sections'])} sections.")
    print("Open the project in Android Studio and Run -- the new words are in.")


if __name__ == "__main__":
    main()
