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
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        patcher = patch.object(qualify, "ROOT", self.root)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.git("init", "-q")
        self.git("config", "user.name", "Owned release fixture")
        self.git("config", "user.email", "owned@example.invalid")
        for name in (
            "README.md",
            "LICENSE",
            "python/pyproject.toml",
            "src/main/java/Owned.java",
        ):
            self.write(name, "owned input\n")
        self.commit("owned source")
        self.record = {"head": qualify.head(), "source_sha256": qualify.source_hash()}

    def git(self, *args):
        return subprocess.check_output(
            ["git", *args], cwd=self.root, text=True, stderr=subprocess.STDOUT
        ).strip()

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "-qm", message)

    def test_exact_head(self):
        qualify.validate_evidence_identity(self.record)

    def test_versioned_evidence_and_changelog_descendants(self):
        for name in (
            "docs/release-evidence/0.1.0-alpha.1/owned.json",
            "docs/changelog.md",
        ):
            with self.subTest(path=name):
                self.write(name, "owned evidence\n")
                self.commit("owned evidence only")
                self.assertNotEqual(self.record["head"], qualify.head())
                qualify.validate_evidence_identity(self.record)

    def test_changelog_only_descendant(self):
        self.write("docs/changelog.md", "owned result\n")
        self.commit("owned changelog only")
        qualify.validate_evidence_identity(self.record)

    def test_unqualified_inputs_fail_uncommitted_and_committed(self):
        for name in (
            "README.md",
            "LICENSE",
            "python/pyproject.toml",
            "src/main/java/Owned.java",
            "docs/arbitrary-evidence/owned.json",
            "docs/releases/0.1.0-alpha.1.md",
        ):
            with self.subTest(path=name):
                self.git("reset", "--hard", self.record["head"])
                self.git("clean", "-fd")
                self.write(name, "unqualified input\n")
                with self.assertRaisesRegex(AssertionError, "Superseded-source"):
                    qualify.validate_evidence_identity(self.record)
                self.commit("unqualified source change")
                with self.assertRaisesRegex(AssertionError, "Superseded-source"):
                    qualify.validate_evidence_identity(self.record)

    def test_source_change_then_revert_does_not_bypass_path_allowlist(self):
        self.write("LICENSE", "unqualified license\n")
        self.commit("unqualified license")
        self.write("LICENSE", "owned input\n")
        self.commit("restore license")
        # All post-qualification paths must be evidence, even if later reverted.
        with self.assertRaisesRegex(AssertionError, "Only evidence commits"):
            qualify.validate_evidence_identity(self.record)
        forged = dict(self.record, source_sha256=qualify.source_hash())
        self.write("README.md", "unqualified docs\n")
        self.commit("unqualified docs")
        forged["source_sha256"] = qualify.source_hash()
        with self.assertRaisesRegex(AssertionError, "Only evidence commits"):
            qualify.validate_evidence_identity(forged)

    def test_unrelated_qualification_head_fails(self):
        self.git("checkout", "--orphan", "unrelated")
        self.commit("same tree unrelated history")
        with self.assertRaisesRegex(AssertionError, "not an ancestor"):
            qualify.validate_evidence_identity(self.record)


if __name__ == "__main__":
    unittest.main()
