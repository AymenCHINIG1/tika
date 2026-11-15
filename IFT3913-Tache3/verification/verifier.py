"""Run harmless local probes against the previously compiled Tika classes.

Requires Java 21 and dependencies already downloaded by the Maven build.
Does not run Git, access external services, or execute arbitrary commands.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
repository = Path.home() / ".m2" / "repository"
artifacts = [
    "org/slf4j/slf4j-api/2.0.18/slf4j-api-2.0.18.jar",
    "org/slf4j/slf4j-simple/2.0.18/slf4j-simple-2.0.18.jar",
    "commons-io/commons-io/2.22.0/commons-io-2.22.0.jar",
    "com/fasterxml/jackson/core/jackson-core/2.22.1/jackson-core-2.22.1.jar",
    "com/fasterxml/jackson/core/jackson-databind/2.22.1/jackson-databind-2.22.1.jar",
    "com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar",
]
dependencies = [repository / artifact for artifact in artifacts]
for path in dependencies:
    if not path.is_file():
        raise RuntimeError(f"Dependency missing: {path}")

with tempfile.TemporaryDirectory(prefix="task3-classes-") as classes:
    classpath = os.pathsep.join(map(str, [
        root / "tika-core/target/classes",
        root / "tika-serialization/target/classes",
        *dependencies,
        Path(classes),
    ]))
    subprocess.run([
        "javac", "-cp", classpath, "-d", classes,
        str(Path(__file__).with_name("VerifyVulnerabilities.java")),
    ], check=True)
    result = subprocess.run([
        "java", "-cp", classpath, "VerifyVulnerabilities",
    ], capture_output=True, text=True)
    output = result.stdout + result.stderr
    (root / "IFT3913-Tache3/preuves/sept-vulnerabilites/verification-comportements.txt").write_text(output)
    print(output, end="")
    result.check_returncode()
