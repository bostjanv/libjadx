package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.jadxadapter.OwnedDexAnalysis.AcquisitionStage.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.JadxCfgProbeTest;
import dev.libjadx.probes.JadxCfgProbeTest.Graph;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.project.NativeProjectRepository.AcceptedInputs;
import jadx.api.data.impl.JadxCodeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Retained security counterexamples, not a passing hostile-filesystem containment claim. */
public class OwnedDexContainmentTest {
	@TempDir Path dir;

	@BeforeEach void linuxSecureProvider() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").equals("Linux"));
		try (var stream = Files.newDirectoryStream(dir)) {
			org.junit.jupiter.api.Assumptions.assumeTrue(stream instanceof SecureDirectoryStream<?>);
			System.out.println("PLATFORM " + Runtime.version() + " " + dir.getFileSystem().provider().getClass().getName()
					+ " " + Files.getFileStore(dir).type() + " " + stream.getClass().getName());
		}
	}

	Path fixture(Path root) throws Exception {
		return HierarchyFixture.dex(root, "input.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")));
	}

	static Graph graph(OwnedDexAnalysis owned) throws Exception {
		owned.load(JadxEngineFactory.arguments(List.of(), null, new JadxCodeData(), EffectiveAnalysisConfig.defaults()));
		var jadx = owned.decompiler();
		var cls = jadx.searchJavaClassByOrigFullName("probe.CfgCases").getClassNode();
		var method = cls.getMethods().stream().filter(m -> m.getMethodInfo().getName().equals("branchLoop")).findFirst().orElseThrow();
		assertTrue(jadx.getRoot().getProcessClasses().processMethodUntilVisitor(method, "BlockFinisher", true));
		var copied = JadxCfgProbeTest.graph(method, JadxCfgProbeTest.Limits.DEFAULT);
		assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().isEmpty());
		return copied;
	}

	static Graph healthy(AcceptedInputs accepted) throws Exception {
		try (var owned = OwnedDexAnalysis.acquire(accepted)) { return graph(owned); }
	}

	static void loop(Graph graph, String operator) {
		String exit = graph.nodes().stream().filter(n -> n.synthetic() && n.start() == 13).findFirst().orElseThrow().id();
		assertEquals(List.of("1:" + operator + ":" + exit + ":3", "3:EQ:8:5"), graph.branches());
		assertTrue(graph.edges().contains("10>1"), "The back edge must survive copying");
	}

	@ParameterizedTest
	@CsvSource({"false,false,DIRECTORY_OPENED", "false,true,DIRECTORY_OPENED", "true,false,DIRECTORY_OPENED",
			"true,true,DIRECTORY_OPENED", "false,false,FILE_OPENED", "false,true,FILE_OPENED",
			"true,false,FILE_OPENED", "true,true,FILE_OPENED"})
	void counterexampleMovedAncestorStillSuppliesAcceptedBytes(boolean moveRoot, boolean replaceName,
			OwnedDexAnalysis.AcquisitionStage pause) throws Exception {
		Path root = Files.createDirectory(dir.resolve("allowed"));
		Path nested = Files.createDirectory(root.resolve("nested"));
		Path input = fixture(nested);
		byte[] bytes = Files.readAllBytes(input);
		Path oppositeInput = HierarchyFixture.dex(dir, "opposite.dex",
				Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")).replace("if-le", "if-gt"));
		byte[] opposite = Files.readAllBytes(oppositeInput);
		loop(healthy(NativeProjectRepository.fromInputs(List.of(oppositeInput), List.of(dir)).acceptedInputs()), "GT");
		var repository = NativeProjectRepository.fromInputs(List.of(input), List.of(root));
		var accepted = repository.acceptedInputs();
		var before = repository.snapshot();
		Graph expected = healthy(accepted); loop(expected, "LE");
		Path moving = moveRoot ? root : nested, destination = dir.resolve("outside");
		Path movedInput = destination.resolve(moving.relativize(input));
		var fired = new AtomicBoolean(); var read = new AtomicLong();
		try {
			Graph result;
			try (var owned = OwnedDexAnalysis.acquire(accepted, (stage, path, count) -> {
				if (stage == pause && path.equals(pause == DIRECTORY_OPENED ? moving : input) && fired.compareAndSet(false, true)) {
					Files.move(moving, destination);
					assertFalse(movedInput.toRealPath().startsWith(root));
					assertArrayEquals(bytes, Files.readAllBytes(movedInput));
					if (replaceName) Files.write(Files.createDirectories(input.getParent()).resolve(input.getFileName()), opposite);
					System.out.println("MOVED " + pause + " root=" + moveRoot + " replacement=" + replaceName);
				}
				if (stage == READ) {
					assertTrue(fired.get()); assertTrue(Files.isRegularFile(movedInput));
					assertFalse(movedInput.toRealPath().startsWith(root)); read.set(count);
				}
			})) { result = graph(owned); }
			assertTrue(fired.get()); assertEquals(bytes.length, read.get());
			assertEquals(expected, result); loop(result, "LE");
			assertThrows(UnsupportedOperationException.class, () -> result.nodes().clear());
			System.out.println("OUTSIDE_READ " + read + " accepted=true graph=LE");
		} finally {
			if (fired.get()) {
				if (replaceName) {
					Files.delete(input); if (moveRoot) Files.delete(nested); Files.delete(moving);
				}
				Files.move(destination, moving);
			}
		}
		assertEquals(accepted, repository.acceptedInputs());
		assertEquals(before.revisions().logicalRevision(), repository.snapshot().revisions().logicalRevision());
		assertEquals(before.dirty(), repository.snapshot().dirty());
		assertEquals(expected, healthy(accepted));
		assertArrayEquals(bytes, Files.readAllBytes(input));
		noDescriptors(root);
	}

	@Test void movingWithinAllowedRootAndDeletingPathsAfterCaptureRetainsOwnedGraph() throws Exception {
		Path root = Files.createDirectory(dir.resolve("allowed"));
		Path nested = Files.createDirectory(root.resolve("nested")), input = fixture(nested);
		var accepted = NativeProjectRepository.fromInputs(List.of(input), List.of(root)).acceptedInputs();
		var fired = new AtomicBoolean();
		try (var owned = OwnedDexAnalysis.acquire(accepted, (stage, path, count) -> {
			if (stage == DIRECTORY_OPENED && path.equals(nested)) { Files.move(nested, root.resolve("sibling")); fired.set(true); }
		})) {
			assertTrue(fired.get());
			assertTrue(root.resolve("sibling/input.dex").toRealPath().startsWith(root));
			Files.delete(root.resolve("sibling/input.dex"));
			loop(graph(owned), "LE");
		}
		noDescriptors(root);
	}

	@ParameterizedTest @ValueSource(strings = {"replace", "growth", "inPlace"})
	void realAcquisitionRejectsPreOpenAndDuringReadContentChanges(String change) throws Exception {
		Path root = Files.createDirectory(dir.resolve("allowed")), input = fixture(root);
		byte[] bytes = Files.readAllBytes(input);
		var accepted = NativeProjectRepository.fromInputs(List.of(input), List.of(root)).acceptedInputs();
		var fired = new AtomicBoolean(); var read = new AtomicLong();
		try {
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> {
				try (var owned = OwnedDexAnalysis.acquire(accepted, (stage, path, count) -> {
					if (stage == READ) read.set(count);
					if (stage == (change.equals("replace") ? BEFORE_FILE_OPEN : READ) && fired.compareAndSet(false, true)) {
						byte[] changed = java.util.Arrays.copyOf(bytes, bytes.length + (change.equals("growth") ? 257 : 0));
						changed[changed.length - 1] ^= 1; Files.write(input, changed);
					}
				})) { fail("Changed content must not publish a census"); }
			});
			assertTrue(fired.get()); assertEquals(bytes.length + (change.equals("growth") ? 257 : 0), read.get());
		} finally { Files.write(input, bytes); }
		loop(healthy(accepted), "LE"); noDescriptors(root);
	}

	@ParameterizedTest @CsvSource({"true,DIRECTORY", "true,SYMLINK", "false,DIRECTORY", "false,SYMLINK"})
	void leafTypeRefusalsAndRecovery(boolean beforeAttributes, String kind) throws Exception {
		Path root = Files.createDirectory(dir.resolve("allowed")); Path input = fixture(root);
		byte[] bytes = Files.readAllBytes(input);
		var accepted = NativeProjectRepository.fromInputs(List.of(input), List.of(root)).acceptedInputs();
		Graph expected = healthy(accepted);
		var fired = new AtomicBoolean(); var opened = new AtomicBoolean(); var read = new AtomicLong();
		Path target = Files.write(dir.resolve("outside.dex"), bytes);
		try {
			var failure = assertThrows(Exception.class, () -> {
				try (var owned = OwnedDexAnalysis.acquire(accepted, (stage, path, count) -> {
					if (stage == (beforeAttributes ? BEFORE_ATTRIBUTES : BEFORE_FILE_OPEN) && fired.compareAndSet(false, true)) {
						Files.delete(input);
						switch (kind) {
							case "DIRECTORY" -> Files.createDirectory(input);
							case "SYMLINK" -> Files.createSymbolicLink(input, target);
							default -> throw new AssertionError(kind);
						}
					}
					if (stage == FILE_OPENED) opened.set(true);
					if (stage == READ) read.set(count);
				})) { fail("Type replacement must not publish a census"); }
			});
			assertTrue(fired.get()); assertEquals(0, read.get());
			assertEquals(!beforeAttributes && kind.equals("DIRECTORY"), opened.get());
			if (beforeAttributes) assertInstanceOf(SecurityException.class, failure);
			else assertInstanceOf(IOException.class, failure);
			System.out.println("TYPE " + kind + " beforeAttributes=" + beforeAttributes + " opened=" + opened + " " + failure);
		} finally { Files.deleteIfExists(input); Files.write(input, bytes); }
		assertEquals(expected, healthy(accepted)); noDescriptors(root);
	}

	@Test void laterRepeatedSlotRefusesAtomicallyAndAllHandlesClose() throws Exception {
		Path root = Files.createDirectory(dir.resolve("allowed")); Path input = fixture(root);
		byte[] bytes = Files.readAllBytes(input);
		var repository = NativeProjectRepository.fromInputs(List.of(input, input), List.of(root));
		var accepted = repository.acceptedInputs(); var before = repository.snapshot();
		int[] opens = {0}; var read = new AtomicLong();
		try {
			assertThrows(SecurityException.class, () -> {
				try (var owned = OwnedDexAnalysis.acquire(accepted, (stage, path, count) -> {
					if (stage == BEFORE_ATTRIBUTES && ++opens[0] == 2) { Files.delete(input); Files.createDirectory(input); }
					if (stage == READ) read.set(count);
				})) { fail("No partial census"); }
			});
			assertEquals(2, opens[0]); assertEquals(bytes.length, read.get());
		} finally { Files.delete(input); Files.write(input, bytes); }
		assertEquals(accepted, repository.acceptedInputs());
		assertEquals(before.revisions().logicalRevision(), repository.snapshot().revisions().logicalRevision());
		assertEquals(before.dirty(), repository.snapshot().dirty());
		loop(healthy(accepted), "LE");
		noDescriptors(root);
	}

	static void noDescriptors(Path root) throws IOException {
		// Linux can unlink open files; inspect descriptors as well instead of treating deletion as proof of close.
		try (var fds = Files.list(Path.of("/proc/self/fd"))) {
			for (Path fd : fds.toList()) {
				try { assertFalse(Files.readSymbolicLink(fd).toString().startsWith(root.toString()), "Leaked " + fd); }
				catch (java.nio.file.NoSuchFileException closed) { /* The listing's own descriptor has closed. */ }
			}
		}
	}

	static void fifo(Path path) throws IOException {
		Process process = new ProcessBuilder("mkfifo", path.toString()).redirectErrorStream(true).start();
		try {
			if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IOException("mkfifo failed: " + path);
		} catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
		finally { if (process.isAlive()) { process.destroyForcibly(); process.onExit().join(); } }
	}

	@ParameterizedTest @ValueSource(strings = {"leaf", "directory", "beforeAttributes"})
	void fifoControlsUseExternalWatchdogRatherThanInterruptTimeout(String kind) throws Exception {
		Path root = Files.createDirectory(dir.resolve("allowed")); Path nested = Files.createDirectory(root.resolve("nested"));
		Path input = fixture(nested); byte[] bytes = Files.readAllBytes(input);
		Path log = dir.resolve("child.log");
		Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
				System.getProperty("java.class.path"), getClass().getName(), kind, root.toString(), input.toString())
				.redirectErrorStream(true).start();
		var gate = new CompletableFuture<Void>(); var transcript = new StringBuilder();
		var reader = Thread.ofPlatform().start(() -> {
			try (var output = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
				String line;
				while ((line = output.readLine()) != null) {
					synchronized (transcript) { transcript.append(line).append('\n'); }
					if (line.equals("GATE " + kind)) gate.complete(null);
				}
				gate.completeExceptionally(new IOException("Child exited before gate"));
			} catch (IOException failure) { gate.completeExceptionally(failure); }
		});
		try {
			gate.get(30, TimeUnit.SECONDS);
			if (kind.equals("beforeAttributes")) {
				assertTrue(child.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, child.exitValue());
			} else {
				assertFalse(child.waitFor(2, TimeUnit.SECONDS), "Current acquisition unexpectedly escaped FIFO open");
				// Capture the actual native open, rather than inferring it from elapsed time.
				Process dump = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/jcmd").toString(),
						Long.toString(child.pid()), "Thread.print").redirectErrorStream(true).redirectOutput(dir.resolve("threads.log").toFile()).start();
				try {
					assertTrue(dump.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, dump.exitValue());
					String stack = Files.readString(dir.resolve("threads.log")); System.out.print(stack);
					assertTrue(stack.contains("UnixNativeDispatcher.openat0"), stack);
					assertTrue(stack.contains(kind.equals("leaf") ? "UnixChannelFactory.open" : "UnixSecureDirectoryStream.newDirectoryStream"), stack);
					System.out.println("BLOCKED_STACK " + kind + " openat0 confirmed; watchdog terminates pid=" + child.pid());
				} finally { if (dump.isAlive()) { dump.destroyForcibly(); dump.onExit().join(); } }
			}
		} finally {
			child.destroyForcibly(); boolean exited = child.waitFor(10, TimeUnit.SECONDS); reader.join(10000);
			synchronized (transcript) { Files.writeString(log, transcript); System.out.print(transcript); }
			if (Files.exists(root.resolve("held"))) { Files.delete(nested); Files.move(root.resolve("held"), nested); }
			else if (!Files.isRegularFile(input)) { Files.deleteIfExists(input); Files.write(input, bytes); }
			assertTrue(exited); assertFalse(reader.isAlive());
			assertFalse(Files.exists(Path.of("/proc", Long.toString(child.pid()))));
			assertFalse(Files.readString(log).contains("FILE_OPENED"));
			assertFalse(Files.readString(log).contains("READ")); assertFalse(Files.readString(log).contains("ACQUIRED"));
			if (kind.equals("beforeAttributes")) assertTrue(Files.readString(log).contains("REFUSED Regular input required"));
		}
		loop(healthy(NativeProjectRepository.fromInputs(List.of(input), List.of(root)).acceptedInputs()), "LE");
	}

	/** Disposable probe JVM only; baseline acquisition finishes before installing the special file. */
	public static void main(String[] arguments) throws Exception {
		String kind = arguments[0]; Path root = Path.of(arguments[1]), input = Path.of(arguments[2]);
		var accepted = NativeProjectRepository.fromInputs(List.of(input), List.of(root)).acceptedInputs();
		try (var owned = OwnedDexAnalysis.acquire(accepted, (stage, path, count) -> {
			if (kind.equals("beforeAttributes") && stage == BEFORE_ATTRIBUTES) {
				Files.delete(input); fifo(input); System.out.println("GATE beforeAttributes");
			}
			if (kind.equals("leaf") && stage == BEFORE_FILE_OPEN) { Files.delete(input); fifo(input); System.out.println("GATE leaf"); }
			if (kind.equals("directory") && stage == BEFORE_DIRECTORY && path.equals(input.getParent())) {
				Files.move(path, root.resolve("held")); fifo(path); System.out.println("GATE directory");
			}
			if (stage == FILE_OPENED || stage == READ) System.out.println(stage + " " + count);
		})) { System.out.println("ACQUIRED"); }
		catch (SecurityException refusal) {
			if (!kind.equals("beforeAttributes")) throw refusal;
			System.out.println("REFUSED " + refusal.getMessage());
		}
	}
}
