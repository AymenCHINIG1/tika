
#!/usr/bin/env python3
"""Comparaison differentielle de rapports Semgrep."""

import argparse
import json
import sys
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path


def load_report(path):
    """Charger et verifier un rapport Semgrep."""
    data = json.loads(
        Path(path).read_text(encoding="utf-8-sig")
    )

    if not isinstance(data, dict):
        raise ValueError(f"Rapport invalide : {path}")

    if not isinstance(data.get("results"), list):
        raise ValueError(f"Resultats absents : {path}")

    if data.get("errors"):
        raise ValueError(
            f"Erreurs Semgrep dans {path}: "
            f"{data['errors'][:2]}"
        )

    return data["results"]


def norm(path):
    """Uniformiser les chemins Windows et Linux."""
    return str(path).replace("\\", "/").removeprefix("./")


def normaliser_regle(check_id):
    """
    Retirer les prefixes de configuration Semgrep.

    La comparaison utilise l'identifiant de regle Java,
    et non le chemin local du fichier de configuration.
    """
    marqueurs = (
        "java.lang.",
        "javascript.",
        "python.",
        "generic.",
    )

    for marqueur in marqueurs:
        position = check_id.find(marqueur)
        if position != -1:
            return check_id[position:]

    return check_id


def stable_line_map(before, after):
    """
    Associer les lignes inchangees du code candidat
    aux lignes correspondantes du code de reference.
    """
    before_lines = before.splitlines(keepends=True)
    after_lines = after.splitlines(keepends=True)

    mapping = {}

    matcher = SequenceMatcher(
        a=before_lines,
        b=after_lines,
        autojunk=False
    )

    for kind, a1, a2, b1, b2 in matcher.get_opcodes():
        if kind == "equal":
            for offset in range(b2 - b1):
                mapping[b1 + offset + 1] = a1 + offset + 1

    return mapping


def key(result, line=None):
    """Construire l'identite d'une detection."""
    start = result["start"]

    return (
        normaliser_regle(result["check_id"]),
        norm(result["path"]),
        int(start["line"] if line is None else line),
        int(start["col"])
    )


def compare(base_results, new_results, base_root, new_root):
    """Identifier les nouvelles detections Semgrep."""

    base_counts = Counter(
        key(result) for result in base_results
    )

    maps = {}
    nouvelles = []

    for result in new_results:
        rel = norm(result["path"])

        if rel not in maps:
            old_file = base_root / rel
            new_file = new_root / rel

            if old_file.is_file() and new_file.is_file():
                before = old_file.read_text(
                    encoding="utf-8",
                    errors="replace"
                )

                after = new_file.read_text(
                    encoding="utf-8",
                    errors="replace"
                )

                maps[rel] = stable_line_map(before, after)
            else:
                maps[rel] = {}

        mapped = maps[rel].get(
            int(result["start"]["line"])
        )

        matched = False

        if mapped is not None:
            candidate_key = key(result, mapped)

            if base_counts[candidate_key] > 0:
                base_counts[candidate_key] -= 1
                matched = True

        if not matched:
            nouvelles.append(result)

    return nouvelles


def main():
    parser = argparse.ArgumentParser(
        description="Comparaison differentielle Semgrep"
    )

    parser.add_argument("--baseline", required=True)
    parser.add_argument("--apres", required=True)
    parser.add_argument("--base-root", required=True)
    parser.add_argument("--candidate-root", required=True)
    parser.add_argument("--delta-output", default=None)

    args = parser.parse_args()

    before = load_report(args.baseline)
    after = load_report(args.apres)

    nouvelles = compare(
        before,
        after,
        Path(args.base_root),
        Path(args.candidate_root)
    )

    summary = {
        "baseline_count": len(before),
        "candidate_count": len(after),
        "new_count": len(nouvelles),
        "new_findings": []
    }

    for result in nouvelles:
        summary["new_findings"].append({
            "rule": normaliser_regle(result["check_id"]),
            "file": norm(result["path"]),
            "line": result["start"]["line"],
            "column": result["start"]["col"],
            "severity": result.get(
                "extra", {}
            ).get("severity", "UNKNOWN")
        })

    if args.delta_output:
        output = Path(args.delta_output)
        output.parent.mkdir(parents=True, exist_ok=True)

        output.write_text(
            json.dumps(summary, indent=2),
            encoding="utf-8"
        )

    print(
        f"Reference: {len(before)} | "
        f"Candidat: {len(after)} | "
        f"Nouvelles: {len(nouvelles)}"
    )

    for result in summary["new_findings"]:
        print(
            f"NOUVELLE: {result['rule']} | "
            f"{result['file']}:"
            f"{result['line']}:"
            f"{result['column']} | "
            f"{result['severity']}"
        )

    if nouvelles:
        print("ECHEC: nouvelles detections")
        return 1

    print("SUCCES: aucune nouvelle detection")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())

    except (
            OSError,
            ValueError,
            KeyError,
            TypeError
    ) as exc:
        print(
            f"ERREUR TECHNIQUE: {exc}",
            file=sys.stderr
        )
        sys.exit(2)
