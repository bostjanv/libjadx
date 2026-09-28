package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.search.SearchDtos.Page;
import dev.libjadx.core.search.SearchQuery;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.http.HttpApiServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.scheduler.JobSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchBuildJobTest {
	@TempDir Path dir;

	@Test
	void requireCompleteReusesRunningBuildWithoutEnteringTheEngineQueryGate() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		SearchService service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey(), processed -> {
					if (processed == 1) {
						entered.countDown();
						try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
						catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
					}
				});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime, null, service)) {
			server.start();
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var revision = runtime.projectSnapshot().revisions();
			List<SearchQuery.Domain> domains = List.of(SearchQuery.Domain.MEMBER_NAME, SearchQuery.Domain.SOURCE_TEXT);
			JobSnapshot started = (JobSnapshot) service.buildIndex(domains, false);
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			SearchQuery query = new SearchQuery("mix", domains, SearchQuery.MatchMode.CONTAINS,
					true, 50, null, false, true, revision.sessionId(), revision.logicalRevision());
			assertEquals(started.jobId(), ((JobSnapshot) service.query(query)).jobId());
			HttpResponse<String> reused = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
					"http://127.0.0.1:" + server.localPort() + "/api/v1/search"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"mix\",\"domains\":[\"MEMBER_NAME\",\"SOURCE_TEXT\"],"
							+ "\"requireComplete\":true,\"expectedSessionId\":\"" + revision.sessionId()
							+ "\",\"expectedLogicalRevision\":" + revision.logicalRevision() + "}"))
					.build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(202, reused.statusCode(), reused.body());
			assertEquals(started.jobId().toString(), new ObjectMapper().readTree(reused.body()).path("jobId").asText());
			assertThrows(SearchService.StaleSearchException.class, () -> service.query(new SearchQuery("mix", domains,
					SearchQuery.MatchMode.CONTAINS, true, 50, null, false, true, revision.sessionId(),
					revision.logicalRevision() + 1)));
			assertThrows(dev.libjadx.scheduler.ProjectBusyException.class,
					() -> service.query(new SearchQuery("mix", domains, SearchQuery.MatchMode.CONTAINS,
							true, 50, null, false, false, null, null)));
		} finally { release.countDown(); runtime.close(); }
	}

	@Test
	void rejectedForcedBuildPreservesCompleteIndexAndGeneration() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		var defaults = dev.libjadx.scheduler.JobLimits.defaults();
		var limits = new dev.libjadx.scheduler.JobLimits(1, 1, 16, defaults.maxResultBytes(),
				defaults.maxTotalResultBytes(), defaults.maxDiagnostics(), defaults.maxDiagnosticBytes(),
				defaults.maxEventsPerJob(), defaults.maxEventBytes(), defaults.maxSubscribersPerJob(),
				defaults.maxGlobalSubscribers(), defaults.maxFramesPerSubscriber(), defaults.terminalTtl(),
				defaults.heartbeatInterval());
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir), limits);
		SearchService service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey());
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			List<SearchQuery.Domain> domains = List.of(SearchQuery.Domain.MEMBER_NAME, SearchQuery.Domain.SOURCE_TEXT);
			JobSnapshot build = (JobSnapshot) service.buildIndex(domains, false);
			assertEquals(JobSnapshot.State.SUCCEEDED,
					runtime.jobRegistry().awaitTerminal(build.jobId(), Duration.ofSeconds(10)).state());
			SearchQuery query = new SearchQuery("mix", domains, SearchQuery.MatchMode.CONTAINS,
					true, 1, null, true, false, null, null);
			Page before = (Page) service.query(query);
			assertTrue(before.coverage().stream().allMatch(c -> "COMPLETE".equals(c.state())));
			assertTrue(before.nextCursor() != null);
			var revision = runtime.projectSnapshot().revisions();
			var blocker = runtime.submitJob(new dev.libjadx.scheduler.JobSpec<>("force-test-blocker",
				dev.libjadx.scheduler.OperationRequest.temporaryAnalysis("force-test"),
				new dev.libjadx.scheduler.OperationCoordinator.Admission(revision.sessionId(), revision.logicalRevision()),
				"force-test", Duration.ofSeconds(30), () -> "copied", (context, copied) -> {
					entered.countDown();
					assertTrue(release.await(20, TimeUnit.SECONDS));
					return new dev.libjadx.scheduler.JobSpec.JobResult("{}",
							dev.libjadx.scheduler.JobSpec.Completeness.COMPLETE);
				}));
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			var queued = runtime.submitJob(new dev.libjadx.scheduler.JobSpec<>("force-test-queued",
				dev.libjadx.scheduler.OperationRequest.temporaryAnalysis("queued-test"),
				new dev.libjadx.scheduler.OperationCoordinator.Admission(revision.sessionId(), revision.logicalRevision()),
				"queued-test", Duration.ofSeconds(30), () -> "copied", (context, copied) ->
						new dev.libjadx.scheduler.JobSpec.JobResult("{}",
								dev.libjadx.scheduler.JobSpec.Completeness.COMPLETE)));
			assertEquals(JobSnapshot.State.QUEUED, queued.state());
			assertThrows(dev.libjadx.scheduler.JobRegistry.ResourceLimitException.class,
					() -> service.buildIndex(domains, true));
			Page after = (Page) service.query(query);
			assertEquals(before.indexGeneration(), after.indexGeneration());
			assertEquals(before.coverage(), after.coverage());
			assertEquals(before.hits(), after.hits());
			Page next = (Page) service.query(new SearchQuery("mix", domains, SearchQuery.MatchMode.CONTAINS,
					true, 1, before.nextCursor(), true, false, null, null));
			assertEquals(before.resultSnapshotId(), next.resultSnapshotId());
			release.countDown();
			runtime.jobRegistry().awaitTerminal(blocker.jobId(), Duration.ofSeconds(10));
		} finally { release.countDown(); runtime.close(); }
	}

	@Test
	void queuedSearchFailsStaleAfterNativeEditAndQueueCapacityIsBounded() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		var defaults = dev.libjadx.scheduler.JobLimits.defaults();
		var limits = new dev.libjadx.scheduler.JobLimits(1, 1, 16, defaults.maxResultBytes(),
				defaults.maxTotalResultBytes(), defaults.maxDiagnostics(), defaults.maxDiagnosticBytes(),
				defaults.maxEventsPerJob(), defaults.maxEventBytes(), defaults.maxSubscribersPerJob(),
				defaults.maxGlobalSubscribers(), defaults.maxFramesPerSubscriber(), defaults.terminalTtl(),
				defaults.heartbeatInterval());
		var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir), limits);
		var service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey());
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			runtime.saveProject(dir.resolve("queued.jadx"), runtime.projectSnapshot().revisions().sessionId(),
					runtime.projectSnapshot().revisions().logicalRevision());
			var revision = runtime.projectSnapshot().revisions();
			// An isolated task occupies the only worker after releasing its capture gate.
			var blocker = runtime.submitJob(new dev.libjadx.scheduler.JobSpec<>("isolated-test-blocker",
					dev.libjadx.scheduler.OperationRequest.temporaryAnalysis("owned-test-snapshot"),
					new dev.libjadx.scheduler.OperationCoordinator.Admission(revision.sessionId(), revision.logicalRevision()),
					"owned-test-snapshot", Duration.ofSeconds(30), () -> "copied", (context, copied) -> {
						entered.countDown();
						assertTrue(release.await(20, TimeUnit.SECONDS));
						return new dev.libjadx.scheduler.JobSpec.JobResult("{}",
								dev.libjadx.scheduler.JobSpec.Completeness.COMPLETE);
					}));
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			var queued = (JobSnapshot) service.buildIndex(List.of(SearchQuery.Domain.SOURCE_TEXT), false);
			assertEquals(JobSnapshot.State.QUEUED, queued.state());
			assertThrows(dev.libjadx.scheduler.JobRegistry.ResourceLimitException.class,
					() -> service.buildIndex(List.of(SearchQuery.Domain.MEMBER_NAME), false));
			assertEquals("APPLIED", new EditBatchService(runtime).apply(new dev.libjadx.core.edits.EditDtos.Request(
					revision.sessionId(), revision.logicalRevision(), List.of(new dev.libjadx.core.edits.EditDtos.Operation(
							dev.libjadx.core.edits.EditDtos.Kind.RENAME,
							dev.libjadx.core.symbols.SymbolRef.classRef("Lprobe/SymbolFixture;"),
							"QueuedEditAlias", null, null)))).outcome());
			release.countDown();
			runtime.jobRegistry().awaitTerminal(blocker.jobId(), Duration.ofSeconds(10));
			var terminal = runtime.jobRegistry().awaitTerminal(queued.jobId(), Duration.ofSeconds(10));
			assertEquals(JobSnapshot.State.FAILED, terminal.state());
			assertEquals("STALE_REVISION", terminal.error().code());
			Page cold = (Page) service.query(new SearchQuery("mix", List.of(SearchQuery.Domain.SOURCE_TEXT),
					SearchQuery.MatchMode.CONTAINS, true, 50, null, false, false, null, null));
			assertEquals(0, cold.coverage().get(0).indexed());
		} finally { release.countDown(); runtime.close(); }
	}

	@Test
	void simulatedAdapterFailureStaysPartialAndRequireCompleteDoesNotLoop() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		// Fault injection is deliberate; this fixture does not naturally fail Jadx extraction.
		SearchService service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey(), processed -> { }, (jadx, owner, ref, key) -> {
					throw new IllegalStateException("simulated adapter failure");
				});
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JobSnapshot started = (JobSnapshot) service.buildIndex(List.of(SearchQuery.Domain.SOURCE_TEXT), false);
			JobSnapshot terminal = runtime.jobRegistry().awaitTerminal(started.jobId(), Duration.ofSeconds(10));
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal.state());
			assertEquals(dev.libjadx.scheduler.JobSpec.Completeness.PARTIAL, terminal.completeness());
			SearchQuery partialQuery = new SearchQuery("mix", List.of(SearchQuery.Domain.SOURCE_TEXT),
					SearchQuery.MatchMode.CONTAINS, true, 50, null, false, false, null, null);
			Page partial = (Page) service.query(partialQuery);
			assertEquals(1, partial.coverage().get(0).failed());
			assertThrows(SearchService.IncompleteSearchException.class, () -> service.query(new SearchQuery("mix",
					List.of(SearchQuery.Domain.SOURCE_TEXT), SearchQuery.MatchMode.CONTAINS,
					true, 50, null, true, false, null, null)));
			assertThrows(SearchService.IncompleteSearchException.class, () -> service.query(new SearchQuery("mix",
					List.of(SearchQuery.Domain.SOURCE_TEXT), SearchQuery.MatchMode.CONTAINS,
					true, 50, null, false, true, null, null)));
		} finally { runtime.close(); }
	}

	@Test
	void cancellationAtClassBoundaryLeavesPartialIndexAndReleasesEngine() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		byte[] before = Files.readAllBytes(jar);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		SearchService service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey(), processed -> {
					if (processed == 1) {
						entered.countDown();
						try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
						catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
					}
				});
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JobSnapshot submitted = (JobSnapshot) service.buildIndex(
					List.of(SearchQuery.Domain.MEMBER_NAME, SearchQuery.Domain.SOURCE_TEXT), false);
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			assertEquals(submitted.jobId(), ((JobSnapshot) service.buildIndex(
					List.of(SearchQuery.Domain.MEMBER_NAME, SearchQuery.Domain.SOURCE_TEXT), false)).jobId());
			assertThrows(ProjectRuntime.ShutdownRejectedException.class,
					() -> runtime.requestShutdown(ShutdownPolicy.DISCARD));
			runtime.jobRegistry().cancel(submitted.jobId());
			release.countDown();
			JobSnapshot terminal = runtime.jobRegistry().awaitTerminal(submitted.jobId(), Duration.ofSeconds(10));
			assertEquals(JobSnapshot.State.CANCELLED, terminal.state());
			Page partial = (Page) service.query(new SearchQuery("mix", List.of(SearchQuery.Domain.MEMBER_NAME),
					SearchQuery.MatchMode.CONTAINS, true, 50, null, false, false, null, null));
			assertEquals("PARTIAL", partial.coverage().get(0).state());
			assertArrayEquals(before, Files.readAllBytes(jar));
		} finally { release.countDown(); runtime.close(); }
	}

	@Test
	void verifiedNoCodeSyntheticOwnerIsSkippedDuringCompleteBuild() throws Exception {
		Path jar = SymbolFixtureSupport.syntheticEmptyJar(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		SearchService service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey());
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var page = (Page) service.query(new SearchQuery("EmptySynthetic", List.of(SearchQuery.Domain.CLASS_NAME),
					SearchQuery.MatchMode.CONTAINS, true, 50, null, false, false, null, null));
			assertEquals(1, page.hits().size());
			Object started = service.buildIndex(List.of(SearchQuery.Domain.SOURCE_TEXT), false);
			if (started instanceof JobSnapshot job)
				runtime.jobRegistry().awaitTerminal(job.jobId(), Duration.ofSeconds(10));
			Page source = (Page) service.query(new SearchQuery("EmptySynthetic",
					List.of(SearchQuery.Domain.SOURCE_TEXT), SearchQuery.MatchMode.CONTAINS,
					true, 50, null, false, false, null, null));
			assertEquals("COMPLETE", source.coverage().get(0).state(), source.coverage().toString());
			assertEquals(1, source.coverage().get(0).skipped());
		} finally { runtime.close(); }
	}
}
