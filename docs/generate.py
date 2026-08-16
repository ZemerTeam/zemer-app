#!/usr/bin/env python3
"""Regenerate the code-derived docs from tracked files (hard data only).

Run from the repo root:

    python3 docs/generate.py

Rewrites, in place (re-running until a fixed point, so one invocation is idempotent):
  - docs/repository-map.md         (the "### Counts" + "### Every counted file" section)
  - docs/reference/kotlin-files.md
  - docs/reference/non-kotlin-files.md
  - docs/build-release.md          (Gradle / CI / native / JVM-module facts; needs PyYAML -                                     `pip install pyyaml` - else it is skipped with a note)

Everything is derived from `git ls-files` and the file contents. No behaviour is inferred.
Line counts follow `awk 'END{print NR}'` (a final unterminated line still counts). Gitlinks
(submodules) are excluded from repository-map.md and listed as non-file paths in
non-kotlin-files.md.

CI (.github/workflows/docs-regenerate.yml) runs this on every push to main and commits any
change back, so the checked-in docs stay current automatically - do not edit the generated
docs by hand.
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
KOTLIN_MODULES = ["app", "innertube", "lrclib", "simpmusic"]


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
    for mod in ["innertube", "lrclib", "simpmusic"]:
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


def _solver_section():
    regular, _ = tracked()
    solver = [p for p in regular if p.startswith("app/src/main/assets/solver/")]
    rows = ["| File | Hard fact |", "| --- | --- |"]
    for p in solver:
        rows.append(f"| `{os.path.basename(p)}` | Tracked JavaScript asset under Android assets. |")
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
        _modules_section(), "",
        "## Solver assets", "",
        "Tracked solver assets under `app/src/main/assets/solver`:", "",
        _solver_section(),
    ]
    return "\n".join(parts).rstrip("\n") + "\n"


def write_text(rel, content):
    with open(os.path.join(ROOT, rel), "w", encoding="utf-8") as fh:
        fh.write(content)


def read_text(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as fh:
        return fh.read()


def generate_pass():
    """One full generation. Order matters: write the leaf docs first so repository-map.md
    (which inventories every file, including those) records their post-write sizes."""
    write_text("docs/reference/kotlin-files.md", gen_kotlin_md())
    write_text("docs/reference/non-kotlin-files.md", gen_non_kotlin_md())
    if HAVE_YAML:
        write_text("docs/build-release.md", gen_build_release_md())
    rewrite_repo_map()


# Files generate_pass() rewrites - used to detect convergence.
GENERATED = [
    "docs/reference/kotlin-files.md",
    "docs/reference/non-kotlin-files.md",
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
    print("Regenerated docs (converged): repository-map.md, build-release.md, reference/*.md" + note)


if __name__ == "__main__":
    main()
