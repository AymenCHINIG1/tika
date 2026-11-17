#!/usr/bin/env python3
"""Run paired Semgrep scans with a single snapshot of the auto rules."""
import argparse
from contextlib import contextmanager
import gzip
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.request import Request, urlopen

MODULES = [
    "tika-core",
    "tika-serialization",
    "tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-pdf-module",
    "tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-ocr-module",
    "tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-microsoft-module",
]
RULES_URL = "https://semgrep.dev/c/auto"


def run(cmd, cwd=None):
    print("EXEC:", " ".join(map(str, cmd)), flush=True)
    return subprocess.run(cmd, cwd=cwd, check=True)


@contextmanager
def reference_worktree(repo, ref, tmpdir):
    root = Path(tmpdir) / "reference"
    run(["git", "worktree", "add", "--detach", str(root), ref], cwd=repo)
    try:
        yield root
    finally:
        run(["git", "worktree", "remove", "--force", str(root)], cwd=repo)


def scan(source, rules, local_rules, report):
    for module in MODULES:
        if not (source / module).is_dir():
            raise RuntimeError(f"Module manquant : {source / module}")
    run(["semgrep", "scan", "--config", str(rules), "--config", str(local_rules), "--json", "--output", str(report), *MODULES], cwd=source)
    if not report.is_file():
        raise RuntimeError(f"Rapport non genere : {report}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-ref", required=True, help="SHA ou branche de reference, ex. origin/tache3")
    parser.add_argument("--repo", default=".")
    parser.add_argument("--output", default="IFT3913-Tache3/rapports-semgrep/ci")
    parser.add_argument(
        "--rules-snapshot",
        help="Snapshot auto YAML ou YAML.gz a reutiliser au lieu de le telecharger"
    )
    parser.add_argument(
        "--rules-sha256",
        help="SHA-256 attendu du YAML auto decompresse"
    )
    args = parser.parse_args()
    repo = Path(args.repo).resolve()
    output = (repo / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    run(["git", "rev-parse", "--verify", f"{args.base_ref}^{{commit}}"], cwd=repo)
    rules = output / "auto-regles.yml"
    local_rules = output / "regles-locales.yml"
    shutil.copyfile(repo / "IFT3913-Tache3" / "regles-semgrep.yml", local_rules)
    if args.rules_snapshot:
        snapshot = Path(args.rules_snapshot)
        if not snapshot.is_absolute():
            snapshot = repo / snapshot
        if snapshot.suffix == ".gz":
            with gzip.open(snapshot, "rb") as stream:
                raw_rules = stream.read()
        else:
            raw_rules = snapshot.read_bytes()
        print(f"Reutilisation du snapshot {snapshot}", flush=True)
    else:
        print("Telechargement unique des regles auto ...", flush=True)
        request = Request(RULES_URL, headers={"User-Agent": "IFT3913-Semgrep/1.0", "Accept": "text/yaml, application/yaml, */*"})
        with urlopen(request, timeout=90) as response:
            raw_rules = response.read()

    digest = hashlib.sha256(raw_rules).hexdigest()
    if args.rules_sha256 and digest.lower() != args.rules_sha256.lower():
        raise RuntimeError(
            f"SHA-256 du snapshot invalide : {digest} != {args.rules_sha256}"
        )
    print(f"SHA-256 des regles auto : {digest}", flush=True)
    content = raw_rules.decode("utf-8-sig")
    if not re.search(r"(?m)^rules\s*:\s*$", content) or not re.search(r"(?m)^\s*-\s*id\s*:", content):
        raise RuntimeError("Le registre n'a pas retourne de regles YAML valides")
    rules.write_bytes(raw_rules)
    run(["semgrep", "scan", "--validate", "--config", str(rules), "--config", str(local_rules)], cwd=repo)
    with tempfile.TemporaryDirectory(prefix="ift3913-semgrep-") as tmp:
        with reference_worktree(repo, args.base_ref, tmp) as base:
            baseline = output / "baseline.json"
            candidate = output / "apres.json"
            scan(base, rules, local_rules, baseline)
            scan(repo, rules, local_rules, candidate)
            comparator = repo / "IFT3913-Tache3" / "script-comparaison.py"
            return subprocess.run([
                sys.executable, str(comparator),
                "--baseline", str(baseline), "--apres", str(candidate),
                "--base-root", str(base), "--candidate-root", str(repo),
                "--delta-output", str(output / "delta.json")
            ], check=False).returncode


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (subprocess.CalledProcessError, OSError, RuntimeError, UnicodeError) as error:
        print(f"ERREUR TECHNIQUE: {error}", file=sys.stderr)
        sys.exit(2)
