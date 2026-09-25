#!/usr/bin/env python3
"""Regenerate the code-derived docs from tracked files (hard data only).

Run from the repo root:

    python3 docs/generate.py

Rewrites, in place (re-running until a fixed point, so one invocation is idempotent):
  - docs/repository-map.md         (the "### Counts" + "### Every counted file" section)
  - docs/reference/kotlin-files.md
  - docs/reference/non-kotlin-files.md
  - docs/reference/resource-index.md  (every tracked file under app/src/*/res)
  - docs/build-release.md          (Gradle / CI / native / JVM-module facts; needs PyYAML -
                                     `pip install pyyaml` - else it is skipped with a note)
  - the inventory REGIONS of the hand-authored docs docs/app/{README,database,playback,
    preferences-sync-auth,viewmodels}.md, docs/ui/README.md and docs/innertube/README.md
    (see REGION_DOCS): only the text between a `<!-- generated:NAME ... -->` and its
    `<!-- /generated:NAME -->` marker is replaced; the prose around the markers stays
    hand-authored. A region missing from its doc, or a marker with an unknown NAME, is an error.
    Room entity/view tables come from the highest-numbered app/schemas/*/<N>.json.

Everything is derived from `git ls-files` and the file contents. No behaviour is inferred.
Line counts follow `awk 'END{print NR}'` (a final unterminated line still counts). Gitlinks
(submodules) are excluded from repository-map.md and listed as non-file paths in
non-kotlin-files.md.

CI (.github/workflows/docs-regenerate.yml) runs this on every push to main and commits any
change back, so the checked-in docs stay current automatically - do not edit the generated
docs (or the generated regions) by hand.
"""
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET

try:
    import yaml  # PyYAML - parses the release workflow for build-release.md (CI installs it)
    HAVE_YAML = True
except ImportError:  # inventory generation still works without it; build-release.md is skipped
    HAVE_YAML = False

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Project-internal import roots: excluded from "external import roots". com.zemer (the cipher
# composite build) is treated as external, matching the existing docs.
INTERNAL_ROOTS = {"com.jtech", "com.dpi", "com.metrolist"}
# Named declarations, plus destructuring `val (a, b) = ...` (each name captured).
DECL_RE = re.compile(
    r"\b(class|object|interface|fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)"
    r"|\b(val|var)\s*\(\s*([^)]*)\)"
)


def declarations(stripped):
    out = []
    for m in DECL_RE.finditer(stripped):
        if m.group(2):
            out.append(f"{m.group(1)} {m.group(2)}")
        elif m.group(4) is not None:
            for part in m.group(4).split(","):
                nm = re.match(r"\s*([A-Za-z_]\w*)", part)
                if nm:
                    out.append(f"{m.group(3)} {nm.group(1)}")
    return out
KOTLIN_MODULES = ["app", "innertube"]


def sh(*args):
    return subprocess.check_output(args, cwd=ROOT).decode()


def tracked():
    """Return (regular_paths, gitlink_paths) from git, sorted by path (C order)."""
    regular, gitlinks = [], []
    for line in sh("git", "ls-files", "-s").splitlines():
        meta, path = line.split("\t", 1)
        mode = meta.split()[0]
        (gitlinks if mode == "160000" else regular).append(path)
    return sorted(regular), sorted(gitlinks)


def read_bytes(path):
    with open(os.path.join(ROOT, path), "rb") as f:
        return f.read()


def is_binary(data):
    return b"\x00" in data


def nr_lines(data):
    """awk NR: number of lines, counting a final line with no trailing newline."""
    if not data:
        return 0
    n = data.count(b"\n")
    if not data.endswith(b"\n"):
        n += 1
    return n


def size_field(path):
    data = read_bytes(path)
    if is_binary(data):
        return f"{len(data)} bytes", "binary"
    return f"{nr_lines(data)} lines", "text"


def ext_of(path):
    name = os.path.basename(path)
    if name.startswith("."):
        rest = name[1:]
        return "." + rest.rsplit(".", 1)[1] if "." in rest else "[none]"
    return "." + name.rsplit(".", 1)[1] if "." in name else "[none]"


# ---------- Kotlin metadata ----------
def strip_kotlin(text):
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\\n])*'", "''", text)
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.DOTALL)
    text = re.sub(r"//[^\n]*", " ", text)
    return text


def external_roots(imports):
    roots = []
    for imp in imports:
        seg = imp.split(".")
        root = ".".join(seg[:2]) if len(seg) >= 2 else imp
        if root in INTERNAL_ROOTS:
            continue
        roots.append(root)
    return sorted(set(roots))


def kotlin_row(path):
    text = read_bytes(path).decode("utf-8", "replace")
    nr = nr_lines(read_bytes(path))
    pkg_m = re.search(r"(?m)^\s*package\s+([\w.]+)", text)
    pkg = pkg_m.group(1) if pkg_m else ""
    compose = "yes" if "@Composable" in text else "no"
    imports = re.findall(r"(?m)^\s*import\s+([\w.]+)", text)
    decls = declarations(strip_kotlin(text))
    roots = ", ".join(external_roots(imports))
    return f"| `{path}` | {nr} | `{pkg}` | {compose} | {len(imports)} | {len(decls)} | {roots} |"


def gen_kotlin_md():
    regular, _ = tracked()
    kt = [p for p in regular if p.endswith(".kt")]
    out = ["# Kotlin file reference", "",
           "Every tracked Kotlin file is listed with hard metadata extracted from the file text: "
           "line count, package, whether it declares any `@Composable`, import count, top-level "
           "declaration count (`Decls` - a high value flags a god-file), and the external import "
           "roots it depends on. Declaration counting is regex-based (after stripping comments and "
           "string literals). For the actual declaration names, read the file or use your editor's "
           "outline - they are not duplicated here.", ""]
    for mod in KOTLIN_MODULES:
        files = [p for p in kt if p.split("/", 1)[0] == mod]
        if not files:
            continue
        out.append(f"## `{mod}` Kotlin files ({len(files)})")
        out.append("")
        out.append("| File | Lines | Package | Compose | Imports | Decls | External import roots |")
        out.append("| --- | ---: | --- | --- | ---: | ---: | --- |")
        out += [kotlin_row(p) for p in files]
        out.append("")
    return "\n".join(out).rstrip("\n") + "\n"


# ---------- Non-Kotlin metadata ----------
def xml_root(path):
    try:
        return ET.parse(os.path.join(ROOT, path)).getroot().tag.split("}")[-1]
    except Exception:
        return None


def json_keys(path):
    try:
        data = json.loads(read_bytes(path).decode("utf-8"))
        return list(data.keys()) if isinstance(data, dict) else None
    except Exception:
        return None


def gradle_plugins(path):
    text = read_bytes(path).decode("utf-8", "replace")
    m = re.search(r"plugins\s*\{(.*?)\}", text, flags=re.DOTALL)
    if not m:
        return None
    plugins = []
    for line in m.group(1).splitlines():
        a = re.search(r'\bid\s*\(\s*"([^"]+)"', line)
        k = re.search(r'\bkotlin\s*\(\s*"([^"]+)"', line)
        al = re.search(r"\balias\s*\(\s*libs\.plugins\.([\w.]+)", line)
        if a:
            plugins.append(a.group(1))
        elif k:
            plugins.append(k.group(1))
        elif al:
            plugins.append(al.group(1))
    return plugins or None


def type_metadata(path):
    ext = ext_of(path)
    kind = f"text `{ext}`"
    if ext == ".xml":
        r = xml_root(path)
        if r:
            kind += f"; XML root `{r}`"
    elif ext == ".json":
        keys = json_keys(path)
        if keys:
            kind += f"; JSON keys `{', '.join(keys)}`"
    elif ext == ".kts":
        pl = gradle_plugins(path)
        if pl:
            kind += f"; plugins `{', '.join(pl)}`"
    return kind


def gen_non_kotlin_md():
    regular, gitlinks = tracked()
    paths = [p for p in regular if not p.endswith(".kt") and not p.startswith("docs/")]
    paths += [p for p in gitlinks if not p.startswith("docs/")]
    paths.sort()
    out = ["# Non-Kotlin file reference", "",
           f"Every tracked non-Kotlin path outside `docs/` is listed. Text files report line "
           f"counts; binary files report byte counts; gitlinks are recorded as non-file tracked "
           f"paths. Total paths: `{len(paths)}`.", "",
           "| Path | Size/status | Type metadata |", "| --- | ---: | --- |"]
    gl = set(gitlinks)
    for p in paths:
        if p in gl:
            out.append(f"| `{p}` | gitlink/non-file | tracked path is not a regular file in this checkout |")
            continue
        data = read_bytes(p)
        if is_binary(data):
            out.append(f"| `{p}` | {len(data)} bytes | binary `{ext_of(p)}` |")
        else:
            out.append(f"| `{p}` | {nr_lines(data)} lines | {type_metadata(p)} |")
    return "\n".join(out) + "\n"


# ---------- reference/resource-index.md ----------
RES_RE = re.compile(r"^app/src/[^/]+/res/(.+)$")
RES_NAMES_CAP = 20


def resource_names(path):
    """Every `name` attribute in document order (deduped), capped - the declared resource /
    item names of a values/xml resource. Empty for files that declare none."""
    try:
        root = ET.parse(os.path.join(ROOT, path)).getroot()
    except Exception:
        return ""
    names = []
    for el in root.iter():
        for k, v in el.attrib.items():
            if k.split("}")[-1] == "name" and v not in names:
                names.append(v)
    if len(names) > RES_NAMES_CAP:
        return ", ".join(names[:RES_NAMES_CAP]) + f", … +{len(names) - RES_NAMES_CAP} more"
    return ", ".join(names)


def gen_resource_index_md():
    regular, _ = tracked()
    groups = {}
    for p in regular:
        m = RES_RE.match(p)
        if m:
            groups.setdefault(m.group(1).split("/", 1)[0], []).append(p)
    total = sum(len(v) for v in groups.values())
    out = ["# Android resource index", "",
           f"Tracked Android resource paths under `app/src/**/res`: `{total}`.", ""]
    for g in sorted(groups):
        out += [f"## `{g}` ({len(groups[g])} paths)", "",
                "| Path | Lines/bytes | XML root | Resource names / metadata |",
                "| --- | ---: | --- | --- |"]
        for p in sorted(groups[g]):
            data = read_bytes(p)
            if is_binary(data):
                out.append(f"| `{p}` | {len(data)} bytes | `` |  |")
                continue
            root = xml_root(p) if p.endswith(".xml") else None
            names = resource_names(p) if root else ""
            out.append(f"| `{p}` | {nr_lines(data)} lines | `{root or ''}` | {_cell(names)} |")
        out.append("")
    return "\n".join(out).rstrip("\n") + "\n"


# ---------- repository-map.md inventory ----------
def gen_repo_inventory():
    regular, _ = tracked()  # gitlinks excluded from this inventory
    counts = {}
    rows = []
    for p in regular:
        ext = ext_of(p)
        counts[ext] = counts.get(ext, 0) + 1
        data = read_bytes(p)
        size = f"{len(data)} bytes" if is_binary(data) else f"{nr_lines(data)} lines"
        rows.append(f"| `{p}` | {size} | `{ext}` |")
    out = ["### Counts", "", f"- Files counted: `{len(regular)}`", "- By extension:"]
    for ext, c in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])):
        out.append(f"  - `{ext}`: `{c}`")
    out += ["", "### Every counted file", "", "| Path | Lines/bytes | Kind |",
            "| --- | ---: | --- |"] + rows
    return "\n".join(out) + "\n"


def rewrite_repo_map():
    path = os.path.join(ROOT, "docs/repository-map.md")
    head = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if line.rstrip("\n") == "### Counts":
                break
            head.append(line)
    # Compute the inventory BEFORE truncating the file, so repository-map.md's own
    # line count (it lists every docs/ file, including itself) is read at its real size.
    inventory = gen_repo_inventory()
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("".join(head) + inventory)


# ---------- build-release.md (derived facts) ----------
def _text(rel):
    return read_bytes(rel).decode("utf-8", "replace")


def _cell(s):
    """Make a string safe inside a Markdown table cell."""
    return str(s).replace("\n", " ").replace("|", "\\|")


def _yval(v):
    """Render a YAML-loaded scalar the way it reads in the file (lowercase booleans)."""
    if isinstance(v, bool):
        return "true" if v else "false"
    return v


def _settings_facts():
    t = _text("settings.gradle.kts")
    name = (re.search(r'rootProject\.name\s*=\s*"([^"]+)"', t) or [None, "?"])[1]
    includes = re.findall(r'include\("(:[^"]+)"\)', t)
    ib = re.search(r'includeBuild\("([^"]+)"\)', t)
    sub = re.search(r'substitute\(module\("([^"]+)"\)\)\.using\(project\("([^"]+)"\)\)', t)
    repos = [r for r, tok in [
        ("mavenLocal", "mavenLocal("), ("Google", "google("),
        ("Gradle Plugin Portal", "gradlePluginPortal("), ("Maven Central", "mavenCentral("),
        ("JitPack", "jitpack.io")] if tok in t]
    facts = f"Root project `{name}`; includes {', '.join('`' + i + '`' for i in includes)}"
    if ib:
        facts += f"; composite build `{ib.group(1)}`"
        if sub:
            facts += f" substitutes `{sub.group(1)}` with `{sub.group(2)}`"
    facts += f"; repositories: {', '.join(repos)}."
    return facts


def _root_build_facts():
    t = _text("build.gradle.kts")
    plugins = re.findall(r'alias\(libs\.plugins\.([\w.]+)\)', t)
    classpaths = [c.strip() for c in re.findall(r'(?m)classpath\((.+)\)\s*$', t)]
    tasks = re.findall(r'tasks\.register<\w+>\("([^"]+)"\)', t)
    facts = f"Root plugins (apply false): {', '.join('`' + p + '`' for p in plugins)}"
    if classpaths:
        facts += f"; buildscript classpath: {', '.join('`' + c + '`' for c in classpaths)}"
    if tasks:
        facts += f"; registers task(s): {', '.join('`' + x + '`' for x in tasks)}"
    if "enableComposeCompilerReports" in t:
        facts += "; subprojects emit Compose compiler reports/metrics when `enableComposeCompilerReports=true`"
    return facts + "."


def _gradle_properties_facts():
    pairs = [ln.strip() for ln in _text("gradle.properties").splitlines()
             if ln.strip() and not ln.strip().startswith("#") and "=" in ln]
    return "; ".join("`" + p + "`" for p in pairs) + "." if pairs else "No properties set."


def _workflow_section():
    d = yaml.safe_load(_text(".github/workflows/release-build.yml"))
    on = d.get("on", d.get(True, {})) or {}  # PyYAML parses bare `on:` as the boolean key True
    push = on.get("push") or {}
    pr = on.get("pull_request") or {}
    paths_ignore = push.get("paths-ignore") or pr.get("paths-ignore") or []
    env = d.get("env", {}) or {}
    jobs = d.get("jobs", {}) or {}
    job_name = next(iter(jobs), "?")
    job = jobs.get(job_name, {}) or {}
    perms = job.get("permissions", {})
    out = [f"`.github/workflows/release-build.yml` defines workflow `{d.get('name', '?')}`.", "",
           "| Workflow fact | Value |", "| --- | --- |",
           f"| Triggers | {', '.join('`' + str(k) + '`' for k in on.keys()) or 'none'} |",
           f"| Path filters (`paths-ignore`) | {', '.join('`' + str(p) + '`' for p in paths_ignore) or 'none'} |",
           f"| Environment | {', '.join(f'`{k}: {_yval(v)}`' for k, v in env.items()) or 'none'} |",
           f"| Job | `{job_name}` on `{job.get('runs-on', '?')}` |",
           f"| Permissions | {', '.join(f'`{k}: {v}`' for k, v in perms.items()) if isinstance(perms, dict) and perms else 'default'} |",
           "", "| Step | Action / command |", "| --- | --- |"]
    for st in job.get("steps", []) or []:
        if "uses" in st:
            action = f"`{st['uses']}`"
            w = st.get("with", {})
            extras = ", ".join(f"{k}=`{v}`" for k, v in w.items()
                               if k in ("java-version", "distribution", "submodules", "path", "key", "name")) if isinstance(w, dict) else ""
            if extras:
                action += f" ({extras})"
        elif "run" in st:
            action = "run: `" + st["run"].strip().splitlines()[0][:90] + "`"
        else:
            action = " - "
        out.append(f"| {_cell(st.get('name', '(unnamed)'))} | {_cell(action)} |")
    return "\n".join(out)


def _native_section():
    subs = re.findall(r'\[submodule\s+"([^"]+)"\]\s*\n\s*path\s*=\s*(\S+)\s*\n\s*url\s*=\s*(\S+)', _text(".gitmodules"))
    if not os.path.exists("app/src/main/cpp/CMakeLists.txt"):
        # No first-party native code (the cover-art embedder is pure Kotlin now); only
        # the submodule inventory remains meaningful.
        # H3s: the caller (gen_build_release_md) already emits the "## Native code and
        # submodules" H2 this section sits under.
        lines = ["### Native code", "", "None - metadata embedding is pure Kotlin",
                 "(`utils/mp4/`, `utils/ogg/`); no NDK/CMake build.", "", "### Git submodules", ""]
        if subs:
            # Header directly before the rows (after the blank line) so the table renders.
            lines += ["| Submodule | Path | URL |", "| --- | --- | --- |"]
            lines += [f"| `{name}` | `{path}` | {url} |" for name, path, url in subs]
        else:
            lines.append("None.")
        return "\n".join(lines)
    cmake = _text("app/src/main/cpp/CMakeLists.txt")
    cmin = (re.search(r'cmake_minimum_required\(VERSION\s+([\d.]+)\)', cmake) or [None, "?"])[1]
    proj = (re.search(r'project\(([^)\s]+)', cmake) or [None, "?"])[1]
    subdir = re.findall(r'add_subdirectory\(([^)\s]+)', cmake)
    appg = _text("app/build.gradle.kts")
    cver = (re.search(r'cmake\s*\{[^}]*?version\s*=\s*"([^"]+)"', appg, re.DOTALL) or [None, "?"])[1]
    ndk = (re.search(r'ndkVersion\s*=\s*"([^"]+)"', appg) or [None, "?"])[1]
    cpp = (re.search(r'cppFlags\s*\+?=\s*"([^"]+)"', appg) or [None, "?"])[1]
    sub_facts = "; ".join(f"submodule `{n}` → `{u}` (path `{p}`)" for n, p, u in subs)
    return "\n".join([
        "| Path | Hard facts |", "| --- | --- |",
        f"| `.gitmodules` | {_cell(sub_facts)}. |",
        f"| `app/src/main/cpp/CMakeLists.txt` | cmake_minimum_required `{cmin}`; project `{proj}`; add_subdirectory {', '.join('`' + s + '`' for s in subdir)}. |",
        f"| `app/build.gradle.kts` (native) | CMake version `{cver}`; NDK `{ndk}`; cppFlags `{_cell(cpp)}` (used when `USE_PREBUILT_NATIVE` != `true`). |",
    ])


def _modules_section():
    regular, _ = tracked()
    rows = ["| Module | Package | Plugins | Dependencies (`libs.*`) | Kotlin files |",
            "| --- | --- | --- | --- | --- |"]
    for mod in ["innertube"]:
        g = _text(f"{mod}/build.gradle.kts")
        plugins = re.findall(r'alias\(libs\.plugins\.([\w.]+)\)', g) + re.findall(r'kotlin\("([\w-]+)"\)', g)
        tc = re.search(r'jvmToolchain\((\d+)\)', g)
        plugins_str = ", ".join("`" + p + "`" for p in plugins) + (f"; jvmToolchain `{tc.group(1)}`" if tc else "")
        deps = re.findall(r'(?:implementation|testImplementation)\(libs\.([\w.]+)\)', g)
        kts = [p for p in regular if p.startswith(mod + "/") and p.endswith(".kt")]
        pkg, ktcell = "?", "none"
        if kts:
            basedir = os.path.dirname(min(kts, key=lambda p: p.count("/")))
            pkg = basedir.split("src/main/kotlin/", 1)[-1].replace("/", ".")
            rels = sorted(os.path.relpath(p, basedir) for p in kts)
            ktcell = ", ".join("`" + r + "`" for r in rels) if len(rels) <= 6 else f"{len(rels)} files (see `reference/kotlin-files.md`)"
        rows.append(f"| `:{mod}` | `{pkg}` | {plugins_str} | {', '.join('`' + d + '`' for d in deps)} | {_cell(ktcell)} |")
    return "\n".join(rows)


def gen_build_release_md():
    parts = [
        "# Build, CI, native, and auxiliary modules documentation", "",
        "> Generated by `docs/generate.py` from tracked source files - do not edit by hand.", "",
        "## Root Gradle and settings facts", "",
        "| File | Hard facts visible in file |", "| --- | --- |",
        f"| `settings.gradle.kts` | {_cell(_settings_facts())} |",
        f"| `build.gradle.kts` | {_cell(_root_build_facts())} |",
        f"| `gradle.properties` | {_cell(_gradle_properties_facts())} |",
        "| `gradle/libs.versions.toml` | Central version catalog for plugins and dependencies; see `reference/non-kotlin-files.md`. |", "",
        "## GitHub Actions release workflow", "",
        _workflow_section(), "",
        "## Native code and submodules", "",
        _native_section(), "",
        "## Auxiliary JVM modules", "",
        _modules_section(),
    ]
    return "\n".join(parts).rstrip("\n") + "\n"


# ======================================================================================
# Inventory regions inside the hand-authored docs (docs/app/*.md, docs/ui/README.md,
# docs/innertube/README.md). Each table lives between
#     <!-- generated:NAME ... -->  and  <!-- /generated:NAME -->
# markers; only the text between a pair is rewritten, the prose around it stays hand-authored.
# ======================================================================================
APP_KT = "app/src/main/kotlin/com/jtech/zemer/"
UI_KT = APP_KT + "ui/"
IT_KT = "innertube/src/main/kotlin/com/metrolist/innertube/"
DECL_CAP = 25          # declarations shown per file in the app/*.md file tables
KEY_DECL_CAP = 12      # "key declarations" in the ui/ and innertube/ file tables
REGION_RE = re.compile(r"(<!-- generated:([\w-]+)[^\n]*?-->\n)(.*?)(<!-- /generated:\2 -->)", re.DOTALL)


def _kt_files(prefix):
    regular, _ = tracked()
    return [p for p in regular if p.startswith(prefix) and p.endswith(".kt")]


_KT_INFO = {}


def kt_info(path):
    """Lines, package, raw text and the reference/kotlin-files.md declaration list of a file."""
    if path not in _KT_INFO:
        data = read_bytes(path)
        text = data.decode("utf-8", "replace")
        pm = re.search(r"(?m)^\s*package\s+([\w.]+)", text)
        _KT_INFO[path] = dict(lines=nr_lines(data), pkg=pm.group(1) if pm else "", text=text,
                              decls=declarations(strip_kotlin(text)))
    return _KT_INFO[path]


def decl_cell(decls, cap):
    s = ", ".join(decls[:cap])
    if len(decls) > cap:
        s += f", … (+{len(decls) - cap} more)"
    return s


def file_decl_table(paths, cap=DECL_CAP):
    out = ["| File | Lines | Package | Declarations |", "| --- | ---: | --- | --- |"]
    for p in paths:
        i = kt_info(p)
        out.append(f"| `{p}` | {i['lines']} | `{i['pkg']}` | {decl_cell(i['decls'], cap)} |")
    return "\n".join(out)


def key_decl_table(paths, cap=KEY_DECL_CAP):
    out = ["| File | Lines | Key declarations |", "| --- | ---: | --- |"]
    for p in paths:
        i = kt_info(p)
        out.append(f"| `{p}` | {i['lines']} | {decl_cell(i['decls'], cap)} |")
    return "\n".join(out)


def strip_kotlin_keep_lines(src):
    """Blank out comments and string contents (quotes kept) with a single scanner, so a `//` or
    `/*` inside a string never starts a comment. Used for the DAO method signatures."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            out.append(" ")
            continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            out.append('""')
            continue
        if c in "\"'":
            j = i + 1
            while j < n and src[j] != c and src[j] != "\n":
                if src[j] == "\\":
                    j += 1
                j += 1
            out.append(c + c)
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


FUN_SIG_RE = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(")


def fun_signatures(src):
    """Every `fun` in source order: name, parameter text and declared return type
    (`(inferred)` for an expression body without one, `Unit` for a block body without one)."""
    s = strip_kotlin_keep_lines(src)
    res = []
    for m in FUN_SIG_RE.finditer(s):
        i = j = m.end()
        depth = 1
        while j < len(s) and depth:
            if s[j] == "(":
                depth += 1
            elif s[j] == ")":
                depth -= 1
            j += 1
        params = " ".join(s[i:j - 1].split()).rstrip(",").strip()
        rest = s[j:j + 400]
        mm = re.match(r"\s*:\s*([^{=\n]+)", rest)
        if mm:
            ret = " ".join(mm.group(1).split()).strip()
        else:
            ret = "(inferred)" if re.match(r"\s*=", rest) else "Unit"
        res.append(dict(name=m.group(1), params=params, ret=re.sub(r"\s+where\s.*$", "", ret)))
    return res


def _balanced(s, i, open_c, close_c):
    """Index just past the bracket closing the one opened right before `i`."""
    depth, j = 1, i
    while depth and j < len(s):
        if s[j] == open_c:
            depth += 1
        elif s[j] == close_c:
            depth -= 1
        j += 1
    return j


def _strip_comments_only(src):
    """Drop comments but keep string literals verbatim (string-aware, so `"*/*"` or a URL's
    `//` inside a string never starts a comment). For extractors that read string values."""
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            out.append(" ")
            continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(src[i:j])
            i = j
            continue
        c = src[i]
        if c in "\"'":
            j = i + 1
            while j < n and src[j] != c and src[j] != "\n":
                if src[j] == "\\":
                    j += 1
                j += 1
            out.append(src[i:j + 1])
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def _and_join(items):
    return items[0] if len(items) == 1 else ", ".join(items[:-1]) + " and " + items[-1]


# ---------- docs/app/README.md ----------
def _gradle_str(g, key):
    m = re.search(rf'\b{key}\s*=\s*"?([\w.]+)"?', g)
    return m.group(1) if m else "?"


def gen_app_module_facts():
    regular, _ = tracked()
    g = _text("app/build.gradle.kts")
    app = [p for p in regular if p.startswith("app/")]
    kts = [p for p in app if p.endswith(".kt")]
    by_set = {}
    for p in kts:
        by_set[p.split("/")[2]] = by_set.get(p.split("/")[2], 0) + 1
    set_order = ["main", "test", "androidTest"] + sorted(k for k in by_set if k not in ("main", "test", "androidTest"))
    kt_split = ", ".join(f"`{by_set[k]}` in `src/{k}`" for k in set_order if k in by_set)
    res = [p for p in app if RES_RE.match(p)]
    main_assets = [p for p in app if p.startswith("app/src/main/assets/")]
    other_assets = {}
    for p in app:
        m = re.match(r"app/src/([^/]+)/assets/", p)
        if m and m.group(1) != "main":
            other_assets[m.group(1)] = other_assets.get(m.group(1), 0) + 1
    assets = f"`{len(main_assets)}` in `src/main`"
    if other_assets:
        assets += " (" + ", ".join(f"`{c}` test fixtures under `src/{s}/assets`" for s, c in sorted(other_assets.items())) + ")"
    schemas = [p for p in app if p.startswith("app/schemas/")]
    jvm = re.search(r"JvmTarget\.JVM_(\d+)", g)
    fields = re.findall(r'buildConfigField\(\s*"[^"]+"\s*,\s*"(\w+)"', g)
    schema_dir = re.search(r'schemaDirectory\(\s*"\$projectDir/([^"]+)"', g)
    native = not any(p.startswith("app/src/main/cpp/") for p in app) and "externalNativeBuild" not in _strip_comments_only(g)
    rows = [
        ("Gradle module", "`:app`"),
        ("Namespace / application ID", f"`{_gradle_str(g, 'namespace')}` / `{_gradle_str(g, 'applicationId')}`"),
        ("Compile / min / target SDK", f"`{_gradle_str(g, 'compileSdk')}` / `{_gradle_str(g, 'minSdk')}` / `{_gradle_str(g, 'targetSdk')}`"),
        ("Version code/name", f"`{_gradle_str(g, 'versionCode')}` / `{_gradle_str(g, 'versionName')}`"),
        ("Kotlin/JVM target", f"JVM {jvm.group(1)}" if jvm else "?"),
        ("Compose", "Enabled" if re.search(r"\bcompose\s*=\s*true", g) else "Disabled"),
        ("BuildConfig fields visible in Gradle", ", ".join(f"`{f}`" for f in fields)),
        ("Room schema directory", f"`app/{schema_dir.group(1)}`" if schema_dir else "`app/schemas`"),
        ("Native build", "None (no `app/src/main/cpp`, no `externalNativeBuild`)" if native else "Yes (see `build-release.md`)"),
        ("Tracked app paths", f"`{len(app)}`"),
        ("Tracked app Kotlin files", f"`{len(kts)}` ({kt_split})"),
        ("Tracked app resource paths", f"`{len(res)}`"),
        ("Tracked app asset paths", assets),
        ("Tracked app Room schema files", f"`{len(schemas)}`"),
    ]
    return "\n".join(["| Fact | Value |", "| --- | --- |"] + [f"| {k} | {v} |" for k, v in rows])


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
COMPONENT_TAGS = ["activity", "service", "provider", "receiver"]


def _manifest_components():
    app_el = ET.parse(os.path.join(ROOT, "app/src/main/AndroidManifest.xml")).getroot().find("application")
    return [el for el in app_el if el.tag in COMPONENT_TAGS]


def gen_app_manifest_components():
    comps = _manifest_components()
    out = ["| Type | `android:name` | `android:exported` |", "| --- | --- | --- |"]
    for tag in COMPONENT_TAGS:
        for el in comps:
            if el.tag == tag:
                out.append(f"| `{tag}` | `{el.get(ANDROID_NS + 'name')}` | `{el.get(ANDROID_NS + 'exported', '')}` |")
    return "\n".join(out)


def _app_main_kotlin():
    return _kt_files("app/src/main/kotlin/")


def gen_app_packages():
    counts = {}
    for p in _app_main_kotlin():
        m = re.search(r"(?m)^package\s+([\w.]+)", kt_info(p)["text"])
        k = m.group(1) if m else "(none)"
        counts[k] = counts.get(k, 0) + 1
    return "\n".join(["| Package | File count |", "| --- | ---: |"] + [f"| `{k}` | {counts[k]} |" for k in sorted(counts)])


def gen_app_directories():
    counts = {}
    for p in _app_main_kotlin():
        d = p[len("app/src/main/kotlin/"):].rsplit("/", 1)[0]
        counts[d] = counts.get(d, 0) + 1
    return "\n".join(["| Directory | Kotlin files |", "| --- | ---: |"] + [f"| `{k}` | {counts[k]} |" for k in sorted(counts)])


# ---------- docs/app/database.md ----------
DB_KT = APP_KT + "db/MusicDatabase.kt"
DAO_KT = APP_KT + "db/DatabaseDao.kt"
DAO_ANNOTATIONS = {"Query", "RawQuery", "Insert", "Update", "Upsert", "Delete", "Transaction",
                   "RewriteQueriesToDropUnusedColumns"}


def _schemas():
    regular, _ = tracked()
    files = [p for p in regular if re.match(r"app/schemas/[^/]+/\d+\.json$", p)]
    latest = max(files, key=lambda p: int(os.path.basename(p)[:-5]))
    return files, latest, json.loads(read_bytes(latest).decode("utf-8"))["database"]


def gen_db_facts():
    files, latest, db = _schemas()
    src = _text(DB_KT)
    db_class = re.search(r"abstract class (\w+)\s*:\s*RoomDatabase", src)
    wrapper = re.search(r"class (\w+)\([^)]*\)\s*:\s*(\w+)\s+by\s+([\w.]+)", src, re.DOTALL)
    funs = fun_signatures(_text(DAO_KT))
    v = db["version"]
    rows = [
        ("Database class", f"`{db_class.group(1)}` (top-level `abstract class` in `db/MusicDatabase.kt`)"),
        ("Wrapper class", f"`{wrapper.group(1)}` delegates `{wrapper.group(2)}` to `{wrapper.group(3)}`"),
        ("Schema version", f"`{v}`"),
        ("Identity hash", f"`{db['identityHash']}`"),
        (f"Entity count in schema {v}", f"`{len(db['entities'])}`"),
        (f"View count in schema {v}", f"`{len(db.get('views', []))}`"),
        ("Schema files tracked", f"`{len(files)}`"),
        ("DAO file", f"`{DAO_KT}`"),
        ("DAO `fun` declarations found by parser", f"`{len(funs)}` (`{len({f['name'] for f in funs})}` distinct names)"),
    ]
    return "\n".join(["| Fact | Value |", "| --- | --- |"] + [f"| {k} | {val} |" for k, val in rows])


def gen_dao_annotations():
    counts = {}
    for a in re.findall(r"@(\w+)", strip_kotlin(_text(DAO_KT))):
        if a in DAO_ANNOTATIONS:
            counts[a] = counts.get(a, 0) + 1
    ordered = sorted(counts.items(), key=lambda kv: -kv[1])  # stable: ties keep first-use order
    return "\n".join(["| Annotation | Count |", "| --- | ---: |"] + [f"| `@{a}` | {c} |" for a, c in ordered])


def gen_db_auto_migrations():
    src = _strip_comments_only(_text(DB_KT))
    out = ["| From | To | Spec |", "| ---: | ---: | --- |"]
    for args in re.findall(r"\bAutoMigration\s*\(([^)]*)\)", src):
        f = re.search(r"from\s*=\s*(\d+)", args)
        t = re.search(r"to\s*=\s*(\d+)", args)
        spec = re.search(r"spec\s*=\s*(\w+)::class", args)
        if f and t:
            out.append(f"| {f.group(1)} | {t.group(1)} | `{spec.group(1) if spec else 'none'}` |")
    return "\n".join(out)


def gen_db_manual_migrations():
    src = _strip_comments_only(_text(DB_KT))
    out = ["| Name | From | To |", "| --- | ---: | ---: |"]
    for name, f, t in re.findall(r"\bval\s+(\w+)\s*=\s*object\s*:\s*Migration\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)", src):
        out.append(f"| `{name}` | {f} | {t} |")
    return "\n".join(out)


def gen_db_schema_entities():
    _, _, db = _schemas()
    out = [f"## Schema {db['version']} entities"]
    for e in db["entities"]:
        out += ["", f"### `{e['tableName']}`", "",
                f"- Primary key columns: {', '.join('`' + c + '`' for c in e['primaryKey']['columnNames'])}",
                f"- Field count: `{len(e['fields'])}`",
                f"- Indices: `{len(e.get('indices', []))}`", "",
                "| Field path | Column | Affinity | Not null | Default |",
                "| --- | --- | --- | --- | --- |"]
        for f in e["fields"]:
            out.append(f"| `{f['fieldPath']}` | `{f['columnName']}` | `{f['affinity']}` | "
                       f"`{bool(f.get('notNull', False))}` | `{f.get('defaultValue')}` |")
    return "\n".join(out)


def gen_db_schema_views():
    _, latest, db = _schemas()
    src = _strip_comments_only(_text(DB_KT))
    vm = re.search(r"\bviews\s*=\s*\[([^\]]*)\]", src)
    views = [v.strip() for v in vm.group(1).split(",") if v.strip()] if vm else []
    out = [f"## Schema {db['version']} views", "",
           f"Declared in `@Database(views = [{', '.join(views)}])`.", "",
           f"| View | Create SQL (from `{os.path.basename(latest)}`) |", "| --- | --- |"]
    for v in db.get("views", []):
        out.append(f"| `{v['viewName']}` | `` {_cell(v['createSql'])} `` |")
    return "\n".join(out)


def gen_dao_methods():
    out = ["| Method | Parameters | Return/type text |", "| --- | --- | --- |"]
    for f in fun_signatures(_text(DAO_KT)):
        p, r = f["params"].replace("|", "\\|"), f["ret"].replace("|", "\\|")
        out.append(f"| `{f['name']}` | {('`' + p + '`') if p else ''} | `{r}` |")
    return "\n".join(out)


# ---------- docs/app/playback.md ----------
PLAYBACK_FILES = [APP_KT + "constants/MediaSessionConstants.kt", APP_KT + "constants/PlaybackMode.kt"]
PLAYBACK_DIRS = [APP_KT + "playback/", UI_KT + "player/"]


def gen_playback_files():
    paths = sorted(set(p for p in _kt_files(APP_KT) if p in PLAYBACK_FILES or p.startswith(tuple(PLAYBACK_DIRS))))
    subdirs = sorted({p[len(APP_KT + "playback/"):].split("/")[0] for p in paths
                      if p.startswith(APP_KT + "playback/") and p.count("/") > (APP_KT + "playback/").count("/")})
    intro = (f"Scope: {', '.join('`' + p[len(APP_KT):] + '`' for p in PLAYBACK_FILES)}, every tracked Kotlin file "
             f"under `playback/` (including {', '.join('`' + d + '/`' for d in subdirs)}) and under `ui/player/`, all "
             f"relative to `{APP_KT}` ({len(paths)} files). Declarations are every "
             f"`class`/`object`/`interface`/`fun`/`val`/`var` name in source order (the same extraction as "
             f"`reference/kotlin-files.md`), capped at {DECL_CAP} per file.")
    return intro + "\n\n" + file_decl_table(paths)


def gen_playback_manifest():
    out = ["| Component | Exported | Foreground type / actions visible in manifest |", "| --- | --- | --- |"]
    for el in _manifest_components():
        name = el.get(ANDROID_NS + "name")
        actions = [a.get(ANDROID_NS + "name") for f in el.findall("intent-filter") for a in f.findall("action")]
        if not (name.startswith(".playback.") or "android.intent.action.MEDIA_BUTTON" in actions):
            continue
        fgs = el.get(ANDROID_NS + "foregroundServiceType")
        what = (f"`{fgs}`" if fgs else "none") + "; " + (f"actions `{', '.join(actions)}`" if actions else "no intent-filter")
        out.append(f"| `{name}` | `{el.get(ANDROID_NS + 'exported', '')}` | {what} |")
    return "\n".join(out)


# ---------- docs/app/preferences-sync-auth.md ----------
PREF_KEYS_KT = APP_KT + "constants/PreferenceKeys.kt"
PREF_KEY_RE = re.compile(r'\b(?:val|var)\s+(\w+)\s*(?::[^=\n]+)?=\s*(\w+PreferencesKey)\s*\(\s*"([^"]*)"\s*\)')
PREFS_FILES = [APP_KT + "App.kt", PREF_KEYS_KT, APP_KT + "utils/ContentFilterConfig.kt",
               APP_KT + "utils/DataStore.kt", APP_KT + "utils/SyncUtils.kt"]
PREFS_DIRS = [APP_KT + "auth/", APP_KT + "sync/"]


def _pref_keys(path):
    return PREF_KEY_RE.findall(_strip_comments_only(_text(path)))


def gen_pref_keys():
    keys = _pref_keys(PREF_KEYS_KT)
    out = [f"Preference keys extracted from `PreferenceKeys.kt`: `{len(keys)}`.", "",
           "| Kotlin val | Key type | Stored name |", "| --- | --- | --- |"]
    out += [f"| `{v}` | `{t}` | `{n}` |" for v, t, n in keys]
    return "\n".join(out)


def gen_pref_keys_elsewhere():
    out = ["| File | Kotlin val | Key type | Stored name |", "| --- | --- | --- | --- |"]
    for p in _app_main_kotlin():
        if p == PREF_KEYS_KT:
            continue
        rel = p[len(APP_KT):] if p.startswith(APP_KT) else p
        out += [f"| `{rel}` | `{v}` | `{t}` | `{n}` |" for v, t, n in _pref_keys(p)]
    return "\n".join(out)


def gen_prefs_files():
    paths = sorted(p for p in _kt_files(APP_KT) if p in PREFS_FILES or p.startswith(tuple(PREFS_DIRS)))
    return file_decl_table(paths)


# ---------- docs/app/viewmodels.md ----------
def _viewmodel_classes(path):
    return re.findall(r"\bclass\s+(\w+ViewModel)\b", strip_kotlin(kt_info(path)["text"]))


def gen_viewmodels_intro():
    paths = _kt_files(APP_KT + "viewmodels/")
    n = sum(len(_viewmodel_classes(p)) for p in paths)
    return (f"Tracked Kotlin files in `viewmodels/`: `{len(paths)}`; `class *ViewModel` declarations in them: `{n}`. "
            f"Metadata below is extracted from source text. Declarations are capped at {DECL_CAP} per file (same "
            f"extraction as `reference/kotlin-files.md`); the imports column lists every `com.jtech.zemer.*` and "
            f"`com.metrolist.innertube.*` import.")


def gen_viewmodel_classes():
    rows = sorted((c, p[len(APP_KT):]) for p in _kt_files(APP_KT + "viewmodels/") for c in _viewmodel_classes(p))
    return "\n".join(["| Class | File |", "| --- | --- |"] + [f"| `{c}` | `{f}` |" for c, f in rows])


def gen_viewmodel_files():
    out = ["| File | Lines | Declarations | Referenced app/InnerTube imports |", "| --- | ---: | --- | --- |"]
    for p in _kt_files(APP_KT + "viewmodels/"):
        i = kt_info(p)
        imports = [m for m in re.findall(r"(?m)^\s*import\s+([\w.]+)", i["text"])
                   if m.startswith(("com.jtech.zemer.", "com.metrolist.innertube."))]
        out.append(f"| `{p}` | {i['lines']} | {decl_cell(i['decls'], DECL_CAP)} | {', '.join(imports)} |")
    return "\n".join(out)


# ---------- docs/innertube/README.md ----------
CONSUMER_AREAS = [  # (path prefix relative to APP_KT, area label); "" = files at the package root
    ("", "App/session initialization"),
    ("db/entities/", "Database entities"),
    ("db/", "Database"),
    ("extensions/", "Extensions"),
    ("lyrics/", "Lyrics providers"),
    ("playback/", "Playback, queues, SABR, downloads"),
    ("recognition/", "Music recognition"),
    ("ui/", "UI (components, menus, screens, utils)"),
    ("utils/", "Utilities"),
    ("viewmodels/", "View models"),
]


def _consumer_area(rel):
    if "/" not in rel:
        return CONSUMER_AREAS[0][1]
    for prefix, label in CONSUMER_AREAS[1:]:
        if rel.startswith(prefix):
            return label
    return f"`{rel.split('/')[0]}/`"


def gen_innertube_consumers():
    main = [p for p in _app_main_kotlin()]
    any_import = [p for p in main if re.search(r"(?m)^\s*import\s+com\.metrolist\.innertube\.", kt_info(p)["text"])]
    facade = [p for p in main if re.search(r"(?m)^\s*import\s+com\.metrolist\.innertube\.YouTube\s*$", kt_info(p)["text"])]
    groups = {}
    for p in facade:
        rel = p[len(APP_KT):]
        groups.setdefault(_consumer_area(rel), []).append(rel)
    order = [label for _, label in CONSUMER_AREAS] + sorted(k for k in groups if k not in {l for _, l in CONSUMER_AREAS})
    out = [f"`{len(any_import)}` Kotlin files under `app/src/main` import something from `com.metrolist.innertube` "
           f"(mostly models/pages). The `{len(facade)}` that import the `YouTube` facade itself "
           f"(`import com.metrolist.innertube.YouTube`, from `git grep`):", "",
           f"| Area | Files (relative to `{APP_KT}`) |", "| --- | --- |"]
    for label in order:
        if label in groups:
            out.append(f"| {label} | {', '.join('`' + r + '`' for r in sorted(groups[label]))} |")
    return "\n".join(out)


def gen_innertube_pages():
    return key_decl_table(_kt_files(IT_KT + "pages/"))


def gen_innertube_models():
    return key_decl_table([p for p in _kt_files(IT_KT) if not p.startswith(IT_KT + "pages/")])


# ---------- docs/ui/README.md ----------
SCREENS_KT = UI_KT + "screens/Screens.kt"
NAV_KT = UI_KT + "screens/NavigationBuilder.kt"
# Screen-group rows: (sub-directory of ui/, group label, hand-written responsibility). A new
# directory under ui/screens/ that is missing here still gets a row, flagged as undescribed.
SCREEN_GROUPS = [
    ("screens", "Root screens (`screens/`)",
     "Top-level and feature screens wired from navigation or startup/auth flows, plus the Home tab/row pieces, the "
     "navigation graph (`NavigationBuilder`), the screen model (`Screens`), and pure helpers (`HomeContentTab`, "
     "`KidZoneTab`, `LoginGateRedirect`)."),
    ("screens/onboarding", "Onboarding (`screens/onboarding/`)",
     "Per-step onboarding screens, the pure step-flow logic, and the shared connectivity poll; hosted by "
     "`screens/OnboardingScreen.kt`. The radio-choice card is `ui/component/OnboardingChoiceCard.kt`."),
    ("screens/artist", "Artist (`screens/artist/`)", "Artist / podcast-host channel page and the per-section see-all."),
    ("screens/library", "Library (`screens/library/`)", "Library tabs and media groupings."),
    ("screens/playlist", "Playlist (`screens/playlist/`)",
     "Playlist, cache, downloaded, online, curated, and ranked media views, plus shared header pieces."),
    ("screens/podcast", "Podcast (`screens/podcast/`)", "Podcast show page."),
    ("screens/recognition", "Recognition (`screens/recognition/`)", "Music recognition dialog activity and its history screen."),
    ("screens/search", "Search (`screens/search/`)", "Search input, result presentation, and the filter policy."),
    ("screens/settings", "Settings (`screens/settings/`)", "Settings screens and the lyrics provider dialogs."),
    ("screens/statuses", "Music Status (`screens/statuses/`)", "Status see-all, live story viewer, saved-status library and viewer."),
    ("player", "Player UI (`player/`)",
     "Now-playing surface, mini player, lyrics, queue, thumbnails, video mode, casting, and controls."),
    (None, "Reusable components",
     "Cards, list/grid rows, dialogs, menus, app bars, layout utilities, shimmer placeholders, and support components."),
    ("theme", "Theme (`theme/`)",
     "Compose colors, palettes, typography/theme wrapper, slider colors, and player color extraction."),
]
REUSABLE_DIRS = ("component", "menu", "utils")
COMPOSABLE_RE = re.compile(
    r"@Composable\b(?:\s*@\w+(?:\([^)]*\))?|\s+(?:private|internal|public|inline|override|suspend|actual|expect))*"
    r"\s+fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+?\.)?(\w+)\s*\(")


def _ui_dirs(files):
    return sorted({f[len(UI_KT):].rsplit("/", 1)[0] for f in files if "/" in f[len(UI_KT):]})


def _screen_objects():
    """`object X : Screens(...)` -> dict(route/titleId/iconIdInactive/iconIdActive), in source order."""
    src = _text(SCREENS_KT)
    objs = {}
    for name, body in re.findall(r"object (\w+) : Screens\((.*?)\n    \)", src, re.DOTALL):
        objs[name] = {k: (re.search(k + r'\s*=\s*([\w."]+)', body) or [None, "?"])[1].strip('"')
                      for k in ("route", "titleId", "iconIdInactive", "iconIdActive")}
    return objs, src


def gen_ui_stack_facts():
    files = _kt_files(UI_KT)
    objs, src = _screen_objects()
    main = re.search(r"\bval\s+MainScreens\s*=\s*listOf\(([^)]*)\)", src)
    pinned = re.search(r"\bval\s+PinnedTopBarRoutes\s*=\s*setOf\(([^)]*)\)", src)
    main_names = [x.strip() for x in main.group(1).split(",") if x.strip()] if main else []
    pinned_routes = [objs.get(x.strip().split(".")[0], {}).get("route", x.strip())
                     for x in (pinned.group(1).split(",") if pinned else []) if x.strip()]
    out = [f"- UI source is under `{UI_KT.rstrip('/')}` ({len(files)} tracked Kotlin files).",
           f"- The package directories are {', '.join('`' + d + '`' for d in _ui_dirs(files))}.",
           f"- `Screens.MainScreens` is {', '.join('`' + n + '`' for n in main_names)} (`ui/screens/Screens.kt`)."
           + (f" `Screens.PinnedTopBarRoutes` is the {_and_join(['`' + r + '`' for r in pinned_routes])} routes."
              if pinned_routes else "")]
    return "\n".join(out)


def gen_ui_main_screens():
    objs, _ = _screen_objects()
    out = ["| Screen object | Route | Title resource | Inactive icon | Active icon |", "| --- | --- | --- | --- | --- |"]
    for name, o in objs.items():
        out.append(f"| `Screens.{name}` | `{o['route']}` | `{o['titleId']}` | `{o['iconIdInactive']}` | `{o['iconIdActive']}` |")
    return "\n".join(out)


NOT_DESTINATIONS = {"LaunchedEffect", "Box", "Column", "Text", "Modifier"}


def _nav_routes():
    """(route, destination composable, debug-only) for every `composable(...)` in NavigationBuilder.kt.
    The destination is the last capitalised call in the lambda body (after redirect/effect wrappers)."""
    s = _strip_comments_only(_text(NAV_KT))
    objs, _ = _screen_objects()
    debug_spans = []
    for m in re.finditer(r"\bif\s*\(\s*BuildConfig\.DEBUG\s*\)\s*\{", s):
        debug_spans.append((m.end(), _balanced(s, m.end(), "{", "}")))
    rows = []
    for m in re.finditer(r"\bcomposable\(", s):
        j = _balanced(s, m.end(), "(", ")")
        args = s[m.end():j - 1]
        k = s.index("{", j)
        body = s[k + 1:_balanced(s, k + 1, "{", "}") - 1]
        r = (re.search(r'route\s*=\s*"([^"]+)"', args) or re.match(r'\s*"([^"]+)"', args))
        if r:
            route = r.group(1)
        else:
            so = re.match(r"\s*Screens\.(\w+)\.route", args)
            route = objs[so.group(1)]["route"] if so else "?"
        calls = [c for c in re.findall(r"\b([A-Z]\w*)\s*\(", body) if c not in NOT_DESTINATIONS]
        rows.append((route, calls[-1] if calls else "?", any(a <= m.start() < b for a, b in debug_spans)))
    return rows


def gen_ui_routes():
    rows = _nav_routes()
    out = [f"{len(rows)} `composable(...)` destinations, in declaration order. Optional query arguments are part of "
           f"the declared route string.", "", "| Route | Destination function |", "| --- | --- |"]
    for route, dest, dbg in rows:
        out.append(f"| `{route}` | `{dest}`{' (only registered when `BuildConfig.DEBUG`)' if dbg else ''} |")
    return "\n".join(out)


def gen_ui_screen_groups():
    files = _kt_files(UI_KT)

    def names(d):
        pre = UI_KT + d + "/"
        return ", ".join(f"`{f[len(pre):-3]}`" for f in files if f.startswith(pre) and "/" not in f[len(pre):])

    known = {d for d, _, _ in SCREEN_GROUPS if d}
    rows = list(SCREEN_GROUPS)
    for d in _ui_dirs(files):
        if d.startswith("screens/") and d not in known:
            rows.insert(-3, (d, f"`{d}/`", "Not yet described - add it to `SCREEN_GROUPS` in `docs/generate.py`."))
    out = ["| Group | Files | Observable responsibility |", "| --- | --- | --- |"]
    for d, label, resp in rows:
        if d is None:
            globs = [f"`{x}/*.kt`" for x in _ui_dirs(files) if x.split("/")[0] in REUSABLE_DIRS]
            out.append(f"| {label} | {', '.join(globs)} | {resp} |")
        elif names(d):
            out.append(f"| {label} | {names(d)} | {resp} |")
    return "\n".join(out)


def gen_ui_composables():
    out = ["| File | Lines | Composable declarations found |", "| --- | ---: | --- |"]
    for f in _kt_files(UI_KT):
        i = kt_info(f)
        c = COMPOSABLE_RE.findall(strip_kotlin(i["text"]))
        if c:
            out.append(f"| `{f}` | {i['lines']} | {', '.join(c)} |")
    return "\n".join(out)


def gen_ui_files():
    return key_decl_table(_kt_files(UI_KT))


# doc -> {region name -> generator}. Every listed region must exist in its doc (and vice versa).
REGION_DOCS = {
    "docs/app/README.md": {
        "app-module-facts": gen_app_module_facts,
        "app-manifest-components": gen_app_manifest_components,
        "app-packages": gen_app_packages,
        "app-directories": gen_app_directories,
    },
    "docs/app/database.md": {
        "db-facts": gen_db_facts,
        "dao-annotations": gen_dao_annotations,
        "db-auto-migrations": gen_db_auto_migrations,
        "db-manual-migrations": gen_db_manual_migrations,
        "db-schema-entities": gen_db_schema_entities,
        "db-schema-views": gen_db_schema_views,
        "dao-methods": gen_dao_methods,
    },
    "docs/app/playback.md": {
        "playback-files": gen_playback_files,
        "playback-manifest": gen_playback_manifest,
    },
    "docs/app/preferences-sync-auth.md": {
        "pref-keys": gen_pref_keys,
        "pref-keys-elsewhere": gen_pref_keys_elsewhere,
        "prefs-files": gen_prefs_files,
    },
    "docs/app/viewmodels.md": {
        "viewmodels-intro": gen_viewmodels_intro,
        "viewmodel-classes": gen_viewmodel_classes,
        "viewmodel-files": gen_viewmodel_files,
    },
    "docs/innertube/README.md": {
        "innertube-consumers": gen_innertube_consumers,
        "innertube-pages": gen_innertube_pages,
        "innertube-models": gen_innertube_models,
    },
    "docs/ui/README.md": {
        "ui-stack-facts": gen_ui_stack_facts,
        "ui-main-screens": gen_ui_main_screens,
        "ui-routes": gen_ui_routes,
        "ui-screen-groups": gen_ui_screen_groups,
        "ui-composables": gen_ui_composables,
        "ui-files": gen_ui_files,
    },
}


def rewrite_regions(rel, generators):
    """Replace the body of every `<!-- generated:NAME -->` region in `rel`; prose outside stays."""
    text = read_text(rel)
    found = set()

    def repl(m):
        name = m.group(2)
        if name not in generators:
            raise SystemExit(f"{rel}: unknown generated region '{name}' (not in REGION_DOCS)")
        found.add(name)
        return m.group(1) + "\n" + generators[name]().strip("\n") + "\n\n" + m.group(4)

    new = REGION_RE.sub(repl, text)
    missing = set(generators) - found
    if missing:
        raise SystemExit(f"{rel}: missing generated region marker(s): {', '.join(sorted(missing))}")
    if new != text:
        write_text(rel, new)


def write_text(rel, content):
    with open(os.path.join(ROOT, rel), "w", encoding="utf-8") as fh:
        fh.write(content)


def read_text(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as fh:
        return fh.read()


def generate_pass():
    """One full generation. Order matters: write the leaf docs first so repository-map.md
    (which inventories every file, including those) records their post-write sizes."""
    for rel, generators in REGION_DOCS.items():
        rewrite_regions(rel, generators)
    write_text("docs/reference/kotlin-files.md", gen_kotlin_md())
    write_text("docs/reference/non-kotlin-files.md", gen_non_kotlin_md())
    write_text("docs/reference/resource-index.md", gen_resource_index_md())
    if HAVE_YAML:
        write_text("docs/build-release.md", gen_build_release_md())
    rewrite_repo_map()


# Files generate_pass() rewrites - used to detect convergence.
GENERATED = list(REGION_DOCS) + [
    "docs/reference/kotlin-files.md",
    "docs/reference/non-kotlin-files.md",
    "docs/reference/resource-index.md",
    "docs/build-release.md",
    "docs/repository-map.md",
]


def main():
    # repository-map.md lists its own (and the other generated files') line counts, so a single
    # pass leaves the self-counts one generation stale. Re-run until two consecutive passes are
    # byte-identical - i.e. a true fixed point - so one invocation is idempotent (required for the
    # docs-check CI to pass on a clean tree, and to avoid phantom auto-commits).
    prev = None
    for _ in range(6):
        generate_pass()
        cur = {t: read_text(t) for t in GENERATED}
        if cur == prev:
            break
        prev = cur
    else:
        raise SystemExit("generate.py did not converge after 6 passes")
    note = "" if HAVE_YAML else "  (build-release.md skipped: PyYAML not installed - `pip install pyyaml`)"
    print("Regenerated docs (converged): repository-map.md, build-release.md, reference/*.md, and the "
          "generated regions of " + ", ".join(r[len("docs/"):] for r in REGION_DOCS) + note)


if __name__ == "__main__":
    main()
