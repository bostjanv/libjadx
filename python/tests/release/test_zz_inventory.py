"""Final process-wide release coverage gate: new/stale/missing rows fail closed."""

from release_support import (
    CAPABILITIES,
    ERRORS,
    OPERATIONS,
    ROUTES,
    SURFACES,
    assert_release_capabilities,
)

from libjadx.lowlevel import models


async def test_published_error_inventory_has_real_or_owned_fault_evidence():

    # Aggregate gate intentionally remains red for a published but unqualified code.
    published = {str(code) for code in models.ApiErrorCode}
    missing = {
        code: sorted(set(SURFACES) - set(ERRORS.get(code, {}).get("surfaces", {})))
        for code in published
    }
    missing = {code: surfaces for code, surfaces in missing.items() if surfaces}
    assert not missing, f"NOT QUALIFIED: stable error evidence missing: {missing}"


def test_every_operation_has_all_live_surfaces_and_negative_evidence():
    assert set(ROUTES) == set(OPERATIONS)
    for operation, row in ROUTES.items():
        assert set(row["surfaces"]) == set(row["positive_surfaces"]) == set(SURFACES), (
            operation,
            row,
        )
        assert row["positive_case"] and row["negative_case"] and row["error_checked"]
        assert row["provenance_checked"]


def test_complete_capability_snapshot_has_ordinary_process_evidence():
    assert set(CAPABILITIES) == set(SURFACES)
    for record in CAPABILITIES.values():
        assert record["test_hooks_enabled"] is False
        assert_release_capabilities(record["response"])
