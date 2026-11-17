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

SHELL = comparison.TARGETED_SHELL_RULE
AUDIT = comparison.PROCESS_BUILDER_AUDIT_RULE
OTHER = "java.lang.security.other"
EXTERNAL = comparison.KNOWN_SAFE_PROCESS_BUILDER[0]


def finding(rule, line, path="Example.java"):
    return {"check_id": rule, "path": path,
            "start": {"line": line, "col": 1}}


class GateTests(unittest.TestCase):
    def check_delta(self, before, after, old, new, expected):
        with tempfile.TemporaryDirectory() as tmp:
            base, candidate = Path(tmp) / "base", Path(tmp) / "candidate"
            base.mkdir()
            candidate.mkdir()
            path = Path((new or old)[0]["path"])
            (base / path).parent.mkdir(parents=True, exist_ok=True)
            (candidate / path).parent.mkdir(parents=True, exist_ok=True)
            (base / path).write_text(before)
            (candidate / path).write_text(after)
            result = comparison.compare(old, new, base, candidate)
            self.assertEqual([r["check_id"] for r in result], expected)

    def test_shell_introduced_even_with_existing_audit(self):
        self.check_delta("direct\n", "shell\n", [finding(AUDIT, 1)],
                         [finding(AUDIT, 1), finding(SHELL, 1)], [SHELL])

    def test_shell_removed_does_not_block(self):
        old = [finding(AUDIT, 1, EXTERNAL), finding(SHELL, 1, EXTERNAL)]
        new = [finding(AUDIT, 1, EXTERNAL)]
        self.check_delta('new ProcessBuilder("sh", "-c", command),\n',
                         "new ProcessBuilder(thisCommandLine),\n",
                         old, new, [])

    def test_audit_rule_elsewhere_is_blocking(self):
        self.check_delta("safe\n", "changed\n", [], [finding(AUDIT, 1)],
                         [AUDIT])

    def test_audit_rule_non_safe_call_in_same_file_is_blocking(self):
        finding_audit = finding(AUDIT, 1, EXTERNAL)
        self.check_delta("safe\n", "new ProcessBuilder(userInput),\n", [],
                         [finding_audit], [AUDIT])

    def test_existing_shell_shifted_by_comment(self):
        self.check_delta("shell\n", "// comment\nshell\n",
                         [finding(SHELL, 1)], [finding(SHELL, 2)], [])

    def test_additional_shell_is_new(self):
        self.check_delta("shell\n", "shell\nshell2\n",
                         [finding(SHELL, 1)],
                         [finding(SHELL, 1), finding(SHELL, 2)], [SHELL])

    def test_existing_alert_reindented_inside_condition(self):
        old = finding(SHELL, 1)
        old['start']['col'] = 5
        new = finding(SHELL, 2)
        new['start']['col'] = 9
        self.check_delta("    shell\n", "if (enabled) {\n        shell\n}\n",
                         [old], [new], [])

    def test_changed_command_still_blocks_after_reindent(self):
        old = finding(SHELL, 1)
        old['start']['col'] = 5
        new = finding(SHELL, 2)
        new['start']['col'] = 9
        self.check_delta("    shell(old)\n", "if (enabled) {\n        shell(new)\n}\n",
                         [old], [new], [SHELL])

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
