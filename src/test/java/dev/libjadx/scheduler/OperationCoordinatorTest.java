package dev.libjadx.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class OperationCoordinatorTest {
	private static final OperationCoordinator.Admission REV = new OperationCoordinator.Admission("session-1", 7);

	@Test void exclusiveIsFailFastAndIdempotent() {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicInteger released = new AtomicInteger();
		var save = coordinator.tryAdmit(OperationRequest.projectExclusive("save"), REV, released::incrementAndGet);
		assertNotNull(UUID.fromString(save.id().toString()));
		assertEquals(REV, save.admission());
		assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
				OperationRequest.projectExclusive("reload"), REV, () -> { }));
		assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
				OperationRequest.classRead("Lsample/A;"), REV, () -> { }));
		assertEquals(1, coordinator.inFlightCount());
		save.close();
		save.close();
		assertEquals(1, released.get());
		assertEquals(0, coordinator.inFlightCount());
		try (var retry = coordinator.tryAdmit(OperationRequest.projectExclusive("reload"), REV, () -> { })) {
			assertNotEquals(save.id(), retry.id());
		}
	}

	@Test void classReadsRemainSerializedAcrossKeysAndDoNotLeakAfterFailure() {
		OperationCoordinator coordinator = new OperationCoordinator();
		assertThrows(IllegalStateException.class, () -> {
			try (var first = coordinator.tryAdmit(OperationRequest.classRead("A"), REV, () -> { })) {
				assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
						OperationRequest.classRead("A"), REV, () -> { }));
				assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
						OperationRequest.classRead("B"), REV, () -> { }));
				throw new IllegalStateException("synthetic failure");
			}
		});
		assertEquals(0, coordinator.inFlightCount());
	}

	@Test void immutableQueriesShareOnlyAnExactSnapshot() {
		OperationCoordinator coordinator = new OperationCoordinator();
		try (var index = coordinator.tryAdmit(OperationRequest.indexRead("snapshot-a"), REV, () -> { });
				var query = coordinator.tryAdmit(OperationRequest.queryRead("snapshot-a"), REV, () -> { })) {
			assertEquals(2, coordinator.inFlightCount());
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.queryRead("snapshot-b"), REV, () -> { }));
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.queryRead("snapshot-a"),
					new OperationCoordinator.Admission("other-session", 7), () -> { }));
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.indexRead("snapshot-a"),
					new OperationCoordinator.Admission("session-1", 8), () -> { }));
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.projectExclusive("edit"), REV, () -> { }));
		}
	}

	@Test void indexAndQueryBothRejectMismatchedAdmissionMetadata() {
		OperationCoordinator coordinator = new OperationCoordinator();
		var otherSession = new OperationCoordinator.Admission("other-session", REV.logicalRevision());
		var otherRevision = new OperationCoordinator.Admission(REV.sessionId(), REV.logicalRevision() + 1);
		try (var index = coordinator.tryAdmit(OperationRequest.indexRead("same-key"), REV, () -> { })) {
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.queryRead("same-key"), otherSession, () -> { }));
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.queryRead("same-key"), otherRevision, () -> { }));
		}
		try (var query = coordinator.tryAdmit(OperationRequest.queryRead("same-key"), REV, () -> { })) {
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.indexRead("same-key"), otherSession, () -> { }));
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.indexRead("same-key"), otherRevision, () -> { }));
		}
	}

	@Test void temporarySnapshotGateEndsBeforeIsolatedWorkOwnership() {
		OperationCoordinator coordinator = new OperationCoordinator();
		try (var temporary = coordinator.tryAdmit(OperationRequest.temporaryAnalysis("snapshot-a"), REV, () -> { })) {
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.projectExclusive("edit"), REV, () -> { }));
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.classRead("A"), REV, () -> { }));
			temporary.finishSnapshotCapture();
			try (var edit = coordinator.tryAdmit(OperationRequest.projectExclusive("edit"), REV, () -> { })) {
				assertEquals(2, coordinator.inFlightCount());
			}
			assertThrows(ProjectBusyException.class, () -> coordinator.tryAdmit(
					OperationRequest.temporaryAnalysis("snapshot-b"), REV, () -> { }));
		}
		assertEquals(0, coordinator.inFlightCount());
	}

	@Test void shutdownStopsAdmissionButWaitsForActualRelease() {
		OperationCoordinator coordinator = new OperationCoordinator();
		var active = coordinator.tryAdmit(OperationRequest.projectExclusive("save"), REV, () -> { });
		coordinator.stopAdmissions();
		assertThrows(ServiceShuttingDownException.class, () -> coordinator.tryAdmit(
				OperationRequest.projectExclusive("edit"), REV, () -> { }));
		assertEquals(1, coordinator.inFlightCount());
		active.close();
		assertEquals(0, coordinator.inFlightCount());
	}
}
