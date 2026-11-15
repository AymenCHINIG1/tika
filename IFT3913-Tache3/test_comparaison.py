"""Regressions du controle de nouvelles detections (sans commande Git)."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "comparaison", Path(__file__).with_name("script-comparaison.py")
)
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)

SHELL = "java.lang.security.ift3913.dynamic-shell-command"
AUDIT = next(iter(comparison.ADVISORY_RULES))
OTHER = "java.lang.security.other"


def finding(rule, line):
    return {"check_id": rule, "path": "Example.java",
            "start": {"line": line, "col": 1}}


class GateTests(unittest.TestCase):
    def check_delta(self, before, after, old, new, expected):
        with tempfile.TemporaryDirectory() as tmp:
            base, candidate = Path(tmp) / "base", Path(tmp) / "candidate"
            base.mkdir()
            candidate.mkdir()
            (base / "Example.java").write_text(before)
            (candidate / "Example.java").write_text(after)
            result = comparison.compare(old, new, base, candidate)
            self.assertEqual([r["check_id"] for r in result], expected)

    def test_shell_introduced_even_with_existing_audit(self):
        self.check_delta("direct\n", "shell\n", [finding(AUDIT, 1)],
                         [finding(AUDIT, 1), finding(SHELL, 1)], [SHELL])

    def test_shell_removed_does_not_block(self):
        self.check_delta("shell\n", "direct\n",
                         [finding(AUDIT, 1), finding(SHELL, 1)],
                         [finding(AUDIT, 1)], [])

    def test_existing_shell_shifted_by_comment(self):
        self.check_delta("shell\n", "// comment\nshell\n",
                         [finding(SHELL, 1)], [finding(SHELL, 2)], [])

    def test_additional_shell_is_new(self):
        self.check_delta("shell\n", "shell\nshell2\n",
                         [finding(SHELL, 1)],
                         [finding(SHELL, 1), finding(SHELL, 2)], [SHELL])

    def test_same_total_different_rules_still_blocks(self):
        self.check_delta("old\n", "shell\n", [finding(OTHER, 1)],
                         [finding(SHELL, 1)], [SHELL])

    def test_other_rule_on_modified_line_still_blocks(self):
        self.check_delta("old\n", "changed\n", [finding(OTHER, 1)],
                         [finding(OTHER, 1)], [OTHER])

    def test_analysis_errors_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "report.json"
            report.write_text('{"results": [], "errors": ["parse error"]}')
            with self.assertRaises(ValueError):
                comparison.load_report(report)


if __name__ == "__main__":
    unittest.main()
