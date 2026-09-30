"""Real archive and Git regressions for release evidence; no product mocks."""

import io
import subprocess
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import qualify


class ArchiveEvidenceTest(unittest.TestCase):
    def test_outer_tar_with_embedded_jar_is_not_counted_as_the_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = io.BytesIO()
            with zipfile.ZipFile(jar, "w") as archive:
                for name in ("A.class", "B.class", "C.class"):
                    archive.writestr(name, b"owned")
            path = root / "candidate.tar"
            with tarfile.open(path, "w") as archive:
                for name, content in (
                    ("LICENSE", b"owned notice"),
                    ("lib/owned.jar", jar.getvalue()),
                ):
                    entry = tarfile.TarInfo(name)
                    entry.size = len(content)
                    archive.addfile(entry, io.BytesIO(content))
            self.assertTrue(zipfile.is_zipfile(path))  # The original misclassification.
            self.assertEqual(2, qualify.artifact(path)["file_count"])
            self.assertEqual(qualify.sha(path), qualify.artifact(path)["sha256"])
            with zipfile.ZipFile(root / "candidate.zip", "w") as archive:
                archive.writestr("folder/", b"")
                archive.writestr("folder/LICENSE", b"owned")
            self.assertEqual(1, qualify.artifact(root / "candidate.zip")["file_count"])


class GitEvidenceTest(unittest.TestCase):
    def test_evidence_commit_preserves_identity_and_source_change_rejects_it(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(qualify, "ROOT", Path(directory)),
        ):
            root = Path(directory)

            def git(*args):
                return subprocess.check_output(
                    ["git", *args], cwd=root, text=True, stderr=subprocess.STDOUT
                ).strip()

            git("init", "-q")
            git("config", "user.name", "Owned release fixture")
            git("config", "user.email", "owned@example.invalid")
            source = root / "owned.java"
            source.write_text("owned input\n")
            git("add", ".")
            git("commit", "-qm", "owned source")
            record = {"head": qualify.head(), "source_sha256": qualify.source_hash()}
            qualify.validate_evidence_identity(record)
            evidence = root / "docs/pr-22-owned.json"
            evidence.parent.mkdir()
            evidence.write_text('{"owned": true}\n')
            git("add", ".")
            git("commit", "-qm", "owned evidence only")
            self.assertNotEqual(record["head"], qualify.head())
            qualify.validate_evidence_identity(record)
            source.write_text("unqualified source change\n")
            with self.assertRaisesRegex(AssertionError, "Superseded-source"):
                qualify.validate_evidence_identity(record)
            git("add", ".")
            git("commit", "-qm", "unqualified change")
            with self.assertRaisesRegex(AssertionError, "Superseded-source"):
                qualify.validate_evidence_identity(record)


if __name__ == "__main__":
    unittest.main()
