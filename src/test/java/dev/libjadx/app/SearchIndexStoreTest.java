package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.search.SearchIndexKey;
import dev.libjadx.core.search.SearchIndexStore;
import dev.libjadx.core.search.SearchQuery;
import dev.libjadx.core.source.DecompileResult;
import dev.libjadx.core.symbols.ClassInfo;
import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolRef;
import org.junit.jupiter.api.Test;

class SearchIndexStoreTest {
	@Test
	void latchControlledLateFragmentIsRejectedAfterPublicationChange() throws Exception {
		SearchIndexKey old = new SearchIndexKey("session", 1, 1, "settings");
		SearchIndexKey fresh = new SearchIndexKey("session", 1, 2, "settings");
		AtomicReference<SearchIndexKey> published = new AtomicReference<>(old);
		SearchIndexStore store = new SearchIndexStore(published::get, 4096);
		List<ClassInfo> catalog = List.of(cls("La/A;"));
		assertTrue(store.initialize(old, catalog, Map.of("La/A;", "La/A;")));
		CountDownLatch candidateReady = new CountDownLatch(1);
		CountDownLatch allowPublish = new CountDownLatch(1);
		CompletableFuture<Boolean> worker = CompletableFuture.supplyAsync(() -> {
			var candidate = new SearchIndexStore.SourceFragment("La/A;", "old-source", "old text",
					DecompileResult.Status.COMPLETE);
			candidateReady.countDown();
			try { if (!allowPublish.await(5, TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
			catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
			return store.publishSource(old, candidate);
		});
		assertTrue(candidateReady.await(5, TimeUnit.SECONDS));
		published.set(fresh);
		assertTrue(store.initialize(fresh, catalog, Map.of("La/A;", "La/A;")));
		allowPublish.countDown();
		assertFalse(worker.get(5, TimeUnit.SECONDS));
		assertTrue(store.view(fresh).sources().isEmpty());
	}

	@Test
	void lateWorkerCannotRepopulateNewPublicationAndEvictionDegradesCoverage() {
		SearchIndexKey first = new SearchIndexKey("session", 0, 1, "settings");
		SearchIndexKey second = new SearchIndexKey("session", 0, 2, "settings");
		AtomicReference<SearchIndexKey> published = new AtomicReference<>(first);
		SearchIndexStore store = new SearchIndexStore(published::get, 4096);
		List<ClassInfo> classes = List.of(cls("La/A;"), cls("La/B;"));
		assertTrue(store.initialize(first, classes, Map.of("La/A;", "La/A;", "La/B;", "La/B;")));
		assertEquals("PARTIAL", store.view(first).coverage(List.of(SearchQuery.Domain.SOURCE_TEXT)).get(0).state());
		long initial = store.generation(first);
		assertTrue(store.publishSource(first, new SearchIndexStore.SourceFragment("La/A;", "source-a",
				"a".repeat(300), DecompileResult.Status.COMPLETE)));
		assertTrue(store.publishSource(first, new SearchIndexStore.SourceFragment("La/B;", "source-b",
				"b".repeat(300), DecompileResult.Status.COMPLETE)));
		var afterEviction = store.view(first);
		assertTrue(afterEviction.generation() > initial + 2);
		assertEquals(1, afterEviction.coverage(List.of(SearchQuery.Domain.SOURCE_TEXT)).get(0).evicted());
		assertEquals("PARTIAL", afterEviction.coverage(List.of(SearchQuery.Domain.SOURCE_TEXT)).get(0).state());
		published.set(second);
		assertFalse(store.publishSource(first, new SearchIndexStore.SourceFragment("La/A;", "stale",
				"late", DecompileResult.Status.COMPLETE)));
		assertThrows(SearchIndexStore.StaleIndexException.class, () -> store.view(first));
		assertTrue(store.initialize(second, classes, Map.of("La/A;", "La/A;", "La/B;", "La/B;")));
		assertEquals(0, store.view(second).sources().size());
	}

	@Test
	void catalogBudgetRejectsOversizedIdentityAndCancelledBuildCannotClaimComplete() {
		SearchIndexKey key = new SearchIndexKey("session", 0, 1, "settings");
		SearchIndexStore tiny = new SearchIndexStore(() -> key, 1024);
		assertThrows(SearchIndexStore.IndexLimitException.class,
				() -> tiny.initialize(key, List.of(cls("L" + "A".repeat(900) + ";")), Map.of()));
		SearchIndexStore store = new SearchIndexStore(() -> key, 4096);
		store.initialize(key, List.of(cls("La/A;")), Map.of("La/A;", "La/A;"));
		var domains = List.of(SearchQuery.Domain.SOURCE_TEXT);
		store.beginBuild(key, domains);
		store.publishSource(key, new SearchIndexStore.SourceFragment("La/A;", "snapshot", "class A {}",
				DecompileResult.Status.COMPLETE));
		assertEquals("PARTIAL", store.view(key).coverage(domains).get(0).state());
		long pendingGeneration = store.generation(key);
		store.finishBuild(key, domains);
		assertTrue(store.generation(key) > pendingGeneration);
		assertEquals("COMPLETE", store.view(key).coverage(domains).get(0).state());
	}

	private static ClassInfo cls(String descriptor) {
		String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
		return new ClassInfo(SymbolRef.classRef(descriptor), name, name.substring(name.lastIndexOf('.') + 1),
				name, false, true, null, SymbolInfo.Provenance.UNAVAILABLE);
	}
}
