"""Restart, shared-client revision policy, native conflicts and real input formats."""

import json

import pytest
from release_support import SURFACES, Surface, assert_fidelity
from test_routes import CLASS, pre

from libjadx import AsyncClient, Client, SymbolRef
from libjadx.errors import StaleRevisionError


@pytest.mark.parametrize("kind", ("jar", "class", "dex", "native"))
async def test_real_input_analysis_save_restart(factory, kind):
    with factory(kind=kind) as service:
        api = Surface(service, "high_level_async")
        try:
            page = await api.call("listClasses", expected=200)
            assert page["items"]
            ref = page["items"][0]["ref"]
            assert (await api.call("resolveSymbol", body={"ref": ref}, expected=200))[
                "outcome"
            ] == "RESOLVED"
            code = await api.call("decompileJava", body={"ref": ref}, expected=200)
            assert code["source"] and code["sourceSnapshotId"]
            search = await api.call(
                "search",
                body={
                    "query": ref["originalClassDescriptor"],
                    "domains": ["CLASS_NAME"],
                },
                expected=200,
            )
            assert search["hits"]
            await api.call(
                "saveProject",
                body={
                    "targetPath": str(
                        service.root
                        / ("sample.jar.jadx" if kind == "native" else "saved.jadx")
                    ),
                    **pre(service),
                },
                expected=200,
            )
            snapshot = await api.call("getProject", expected=200)
            root = service.root
        finally:
            await api.close()
    with factory(
        kind="saved",
        root=root,
        saved_name="sample.jar.jadx" if kind == "native" else "saved.jadx",
    ) as restarted:
        api = Surface(restarted, "generated_async")
        try:
            assert (await api.call("listClasses"))["items"]
            assert (await api.call("resolveSymbol", body={"ref": ref}))[
                "outcome"
            ] == "RESOLVED"
            restored = await api.call("decompileJava", body={"ref": ref})
            assert restored["source"] == code["source"]
            assert restored["sessionId"] != snapshot["revisions"]["sessionId"]
            assert (
                await api.call(
                    "search",
                    body={
                        "query": ref["originalClassDescriptor"],
                        "domains": ["CLASS_NAME"],
                    },
                )
            )["hits"]
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
@pytest.mark.parametrize("dirty", (False, True))
@pytest.mark.parametrize("policy", ("discard", "save", "refuse_if_dirty"))
async def test_shutdown_policies_and_disk_truth(factory, surface, dirty, policy):
    with factory() as service:
        api = Surface(service, surface)
        try:
            await api.call(
                "saveProject",
                body={"targetPath": str(service.root / "saved.jadx")},
                expected=200,
            )
            native = service.root / "saved.jadx"
            original = native.read_bytes()
            if dirty:
                await api.call(
                    "applyEditBatch",
                    body={
                        "items": [
                            {
                                "kind": "RENAME",
                                "target": CLASS,
                                "newName": "ShutdownSaved",
                            }
                        ],
                        **pre(service),
                    },
                    expected=200,
                )
                assert native.read_bytes() == original
            accepted = not dirty or policy != "refuse_if_dirty"
            await api.call(
                "shutdownService",
                body={"policy": policy},
                expected=202 if accepted else 409,
            )
            if not accepted:
                assert (
                    service.process.poll() is None and native.read_bytes() == original
                )
                await api.call("getProject", expected=200)
                await api.call(
                    "shutdownService", body={"policy": "discard"}, expected=202
                )
            assert service.process.wait(timeout=10) == 0
            root = service.root
        finally:
            await api.close()
    with factory(kind="saved", root=root) as restarted:
        result = restarted.http.post("/api/v1/decompile", json={"ref": CLASS}).json()
        assert ("ShutdownSaved" in result["source"]) == (dirty and policy == "save")
        assert not restarted.http.get("/api/v1/project").json()["dirty"]


async def test_mixed_client_stale_mutation_refresh_is_explicit(factory):
    with factory() as service, Client(service.url) as a:
        async with AsyncClient(service.url) as b:
            a.project.snapshot()
            await b.project.snapshot()
            observed = b.project.logical_revision
            a.project.rename(SymbolRef.class_("Lprobe/Variables;"), "MixedA")
            with pytest.raises(StaleRevisionError) as stale:
                await b.project.rename(SymbolRef.class_("Lprobe/Variables;"), "MixedB")
            assert stale.value.http_status == 409
            assert b.project.logical_revision == observed
            assert len(a.project.pending_edits().code_data.to_dict()["renames"]) == 1
            assert not (service.root / "saved.jadx").exists()
            await b.project.refresh()
            await b.project.rename(SymbolRef.class_("Lprobe/Variables;"), "MixedB")
            assert a.project.snapshot().revisions.logical_revision == observed + 2


@pytest.mark.parametrize("surface", ("raw_http", "high_level_sync", "high_level_async"))
@pytest.mark.parametrize("file", ("sample.jar", "sample.tiny"))
async def test_external_input_and_mapping_edits_fail_closed(factory, surface, file):
    with factory(kind="native") as service:
        api = Surface(service, surface)
        try:
            ref = {"kind": "CLASS", "originalClassDescriptor": "Lprobe/Sample;"}
            before = service.http.get("/api/v1/project").json()
            path = service.root / file
            external = path.read_bytes() + b"\n"
            path.write_bytes(external)
            result = await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {"kind": "RENAME", "target": ref, "newName": "MustNotAdopt"}
                    ],
                    **pre(service),
                },
                expected=409,
            )
            assert result["error"]["code"] == "EXTERNAL_MODIFICATION_CONFLICT"
            assert_fidelity(before, service.http.get("/api/v1/project").json())
            assert path.read_bytes() == external
        finally:
            await api.close()


async def test_opaque_native_json_and_settings_attachment(factory):
    with factory(kind="native") as service:
        native = service.root / "sample.jar.jadx"
        root = service.root
    document = json.loads(native.read_text())
    document["codeData"]["futureNative"] = {"opaque": [None, True, 2**60, "Ž😀"]}
    native.write_text(json.dumps(document))
    saved = root / "saved.jadx"
    document["files"] = ["sample.jar", "second.jar"]
    saved.write_text(json.dumps(document))
    with factory(kind="saved", root=root) as service:
        for name in SURFACES:
            api = Surface(service, name)
            try:
                pending = await api.call("exportPendingEdits", expected=200)
                assert (
                    pending["codeData"]["futureNative"]
                    == document["codeData"]["futureNative"]
                )
                settings = await api.call(
                    "updateProjectSettings",
                    body={"mappingsPath": None, **pre(service)},
                    expected=200,
                )
                assert settings["mappingsPath"] is None
                await api.call(
                    "updateProjectSettings",
                    body={"mappingsPath": str(root / "sample.tiny"), **pre(service)},
                    expected=200,
                )
            finally:
                await api.close()
