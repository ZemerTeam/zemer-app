#!/usr/bin/env python3
"""Multi-line-aware detector for hardcoded user-facing strings under ui/ (standards.md Rule 5).

The line-based grep in ui-audit.sh cannot see a string literal that sits on a different line
from its `Text(` / `text =` / Toast call, nor `section =`-style assignments — exactly the shapes
that have slipped through before. This scanner reads each file with a small amount of context so
those multi-line cases are caught too.

Output: one `<path>\tR5-hardcoded` line per offending source line (same shape the bash audit's
other rules emit), so it plugs straight into the ratchet aggregation.

It is deliberately conservative — it flags a literal only when it is BOTH (a) phrase-like user
text (a letter-word with a space, or a multi-word capitalized phrase, after stripping ${...}
interpolation) AND (b) in a UI-bearing context — and skips technical literals (ids, routes,
CONSTANTS, format-only strings, animation `label=` tags) and internal strings
(error()/throw/require/check/Log/Timber). Run standalone to list offenders with context:

    python3 scripts/ui-strings-scan.py [--verbose]
"""
import os
import re
import sys

UI = "app/src/main/kotlin/com/jtech/zemer/ui"

STR = re.compile(r'"((?:[^"\\]|\\.)*)"')
INTERP = re.compile(r'\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*')

# A UI-bearing context keyword on the literal's line or a couple of lines above it.
UI_CTX = ('Text(', 'text =', 'title =', 'description =', 'label =', 'placeholder =',
          'contentDescription =', 'section =', 'subtitle =', 'message =', 'supportingContent',
          'headlineContent', 'overlineContent', 'Toast.makeText', 'setContentTitle', 'setContentText')

# Contexts that mean the literal is NOT user-facing (internal error / log / nav / lint).
INTERNAL = ('stringResource', 'getString', 'error(', 'throw ', 'require(', 'requireNotNull(',
            'check(', 'checkNotNull(', 'Log.', 'Timber', 'navigate(', 'IllegalStateException',
            'IllegalArgumentException', 'Exception(', 'println(', '@')


def phrase_like(s: str) -> bool:
    """A literal that reads like user-facing prose: a letter-word followed by a space and more
    letters, once interpolation/escapes are stripped. Single tokens, ids and format-only strings
    return False."""
    bare = INTERP.sub('', s).replace('\\n', ' ').replace('\\t', ' ').strip()
    if not re.search(r'[A-Za-z]', bare):
        return False
    if '/' in bare or '://' in s:
        return False
    if re.fullmatch(r'[A-Za-z0-9_]+', bare):          # single identifier token (e.g. animation label)
        return False
    if re.fullmatch(r'[A-Z0-9_]{2,}', bare):           # CONSTANT
        return False
    # require two letter-runs separated by a space (a real phrase) OR an obvious sentence
    return bool(re.search(r'[A-Za-z]\s+[A-Za-z]', bare))


def scan():
    offenders = []
    for root, _dirs, files in os.walk(UI):
        if '/theme/' in root.replace(os.sep, '/'):
            continue
        for fn in files:
            if not fn.endswith('.kt'):
                continue
            path = os.path.join(root, fn)
            with open(path, encoding='utf-8') as f:
                lines = f.readlines()
            for i, line in enumerate(lines):
                if any(k in line for k in INTERNAL):
                    continue
                if 'import ' in line or line.lstrip().startswith('//'):
                    continue
                if not STR.search(line):
                    continue
                window = ' '.join(lines[max(0, i - 2):i + 1])
                if not any(k in window for k in UI_CTX):
                    continue
                for m in STR.finditer(line):
                    if phrase_like(m.group(1)):
                        offenders.append((path, i + 1, m.group(1), line.strip()))
                        break
    return offenders


def main():
    verbose = '--verbose' in sys.argv
    offenders = scan()
    for path, ln, lit, src in offenders:
        if verbose:
            print(f"{path}:{ln}\t«{lit}»\t{src[:100]}", file=sys.stderr)
        print(f"{path}\tR5-hardcoded")
    return 0


if __name__ == '__main__':
    sys.exit(main())
