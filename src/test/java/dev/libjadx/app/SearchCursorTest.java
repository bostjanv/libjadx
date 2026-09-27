package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import dev.libjadx.core.search.SearchDtos.Page;
import dev.libjadx.core.search.SearchQuery;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.scheduler.JobSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchCursorTest {
	@TempDir Path dir;

	@Test
	void expiryAndLruEvictionAreTypedAndPagesKeepTheirImmutableCoverage() throws Exception {
		var runtime = new ProjectRuntime(null, List.of(SymbolFixtureSupport.compileFixture(dir)), List.of(dir));
		AtomicLong clock = new AtomicLong();
		var service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey(), processed -> { }, SearchService::extractSource, clock::get);
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			Page first = (Page) service.query(query(null));
			assertNotNull(first.nextCursor());
			assertTrue(first.nextCursor().length() < 600);
			Page next = (Page) service.query(query(first.nextCursor()));
			assertEquals(first.coverage(), next.coverage());
			assertEquals(first.resultSnapshotId(), next.resultSnapshotId());
			assertNotEquals(first.hits(), next.hits());
			clock.set(Duration.ofMinutes(11).toNanos());
			assertEquals("SEARCH_SNAPSHOT_EVICTED", assertThrows(SearchService.StaleSearchException.class,
					() -> service.query(query(first.nextCursor()))).getMessage());
			Page lru = (Page) service.query(query(null));
			for (int i = 0; i < 256; i++) service.query(query(null));
			assertEquals("SEARCH_SNAPSHOT_EVICTED", assertThrows(SearchService.StaleSearchException.class,
					() -> service.query(query(lru.nextCursor()))).getMessage());
		} finally { runtime.close(); }
	}

	@Test
	void incrementalGenerationInvalidatesAuthenticatedCursorAndTamperingStillReturnsInvalid() throws Exception {
		var runtime = new ProjectRuntime(null, List.of(SymbolFixtureSupport.compileFixture(dir)), List.of(dir));
		var service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey());
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			Page first = (Page) service.query(query(null));
			var job = (JobSnapshot) service.buildIndex(List.of(SearchQuery.Domain.MEMBER_NAME), false);
			assertEquals(JobSnapshot.State.SUCCEEDED,
					runtime.jobRegistry().awaitTerminal(job.jobId(), Duration.ofSeconds(20)).state());
			assertThrows(SearchService.StaleSearchException.class, () -> service.query(query(first.nextCursor())));
			String tampered = "!" + first.nextCursor().substring(1);
			assertThrows(IllegalArgumentException.class, () -> service.query(query(tampered)));
		} finally { runtime.close(); }
	}

	private static SearchQuery query(String cursor) {
		return new SearchQuery("SymbolFixture", List.of(SearchQuery.Domain.CLASS_NAME),
				SearchQuery.MatchMode.CONTAINS, true, 1, cursor, false, false, null, null);
	}
}
