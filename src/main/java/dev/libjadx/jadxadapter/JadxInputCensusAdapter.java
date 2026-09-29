package dev.libjadx.jadxadapter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.libjadx.core.hierarchy.CensusLimits;
import dev.libjadx.core.hierarchy.HierarchyGraph;
import dev.libjadx.core.hierarchy.IndependentHierarchyVerifier;
import dev.libjadx.core.hierarchy.InputCensus;
import dev.libjadx.core.hierarchy.InputCensus.ClassRecord;
import dev.libjadx.core.hierarchy.InputCensus.InputOrigin;
import dev.libjadx.core.hierarchy.InputCensus.MethodRecord;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Verification;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.FileFingerprint;
import jadx.api.JadxDecompiler;
import jadx.api.plugins.input.data.IClassData;
import jadx.api.plugins.input.data.IMethodData;
import jadx.api.plugins.input.data.ISeqConsumer;
import jadx.core.dex.instructions.args.ArgType;
import jadx.plugins.input.dex.DexFileLoader;
import jadx.plugins.input.dex.DexInputOptions;
import jadx.plugins.input.java.JavaClassReader;
import jadx.zip.ZipReader;

/** Pinned 1.5.6 input-reader adapter. Never uses post-collapse ClassNode metadata for the census.
 * Bulk JavaLoadResult/collectFiles swallow exceptions, so strict bounded individual readers are used.
 * The declarations are copied before primary load; fingerprints bracket that load and every use. */
public final class JadxInputCensusAdapter {
	private JadxInputCensusAdapter() { }

	public static Capture capture(List<Path> inputs, CensusLimits limits) {
		try { return new Builder(limits).capture(inputs); }
		catch (CensusFailure failure) { return new Capture(null, Map.of(), limits, failure.status, failure.getMessage()); }
		catch (FileFingerprint.LimitExceededException failure) { return new Capture(null, Map.of(), limits, Status.RESOURCE_LIMIT, "Fingerprint byte limit"); }
		catch (IOException | RuntimeException failure) { return new Capture(null, Map.of(), limits, Status.FAILED, "Raw input parse/read failed"); }
	}

	public record Capture(InputCensus census, Map<Path, FileFingerprint> fingerprints,
			CensusLimits limits, Status status, String reason) {
		public Capture { fingerprints = Map.copyOf(fingerprints); }
		public boolean unchanged() {
			try {
				for (var entry : fingerprints.entrySet()) {
					Path path = entry.getKey();
					if (!path.toRealPath().equals(path) || Files.size(path) > limits.fileBytes()
						|| !entry.getValue().equals(FileFingerprint.of(path, limits.fileBytes()))) return false;
				}
				return true;
			} catch (IOException failure) { return false; }
		}
		/** Immutable classpath boundary facts only. No mutable nodes or primary engine escape. */
		public RelatedHierarchyVerifier bind(JadxDecompiler primary) {
			if (status != Status.COMPLETE) return (seed, budget) -> Verification.incomplete(status, seed, null, reason);
			try {
				var loaded = new ArrayList<String>();
				for (var file : primary.getArgs().getInputFiles()) loaded.add(file.toPath().toRealPath().toString());
				if (primary.getArgs().isUseDxInput() || !loaded.equals(census.inputs().stream().map(InputOrigin::canonicalPath).toList()))
					return (seed, budget) -> Verification.incomplete(Status.UNSUPPORTED_INPUT, seed, null, "Primary input configuration differs from raw census");
			} catch (IOException failure) {
				return (seed, budget) -> Verification.incomplete(Status.INPUT_CHANGED, seed, null, "Primary inputs no longer resolve");
			}
			if (!unchanged()) return (seed, budget) -> Verification.incomplete(Status.INPUT_CHANGED, seed, null, "Input changed during primary load");
			Set<String> external = new HashSet<>(), objectSignatures = new HashSet<>();
			for (var definitions : census.classesByDescriptor().values()) for (var cls : definitions) {
				for (String parent : cls.parents()) {
					if (census.classesByDescriptor().containsKey(parent)) continue;
					var details = primary.getRoot().getClsp().getClsDetails(ArgType.object(dotted(parent)));
					if (details != null) {
						external.add(parent);
						if (parent.equals("Ljava/lang/Object;")) {
							for (String key : details.getMethodsMap().keySet())
								objectSignatures.add(key.substring(0, key.indexOf(')') + 1));
						}
					}
				}
			}
			RelatedHierarchyVerifier verifier = new IndependentHierarchyVerifier(new HierarchyGraph(census, external, objectSignatures));
			return (seed, budget) -> {
				if (!unchanged()) return Verification.incomplete(Status.INPUT_CHANGED, seed, null, "Input baseline changed; explicit reload required");
				var result = verifier.verify(seed, budget);
				return unchanged() ? result : Verification.incomplete(Status.INPUT_CHANGED, seed, null, "Input changed during verification");
			};
		}
	}
	private static String dotted(String descriptor) { return descriptor.substring(1, descriptor.length() - 1).replace('/', '.'); }

	private static final class Builder {
		private final CensusLimits limits;
		private final Map<String, List<ClassRecord>> records = new TreeMap<>();
		private int classes, methods, edges, entries;
		private long bytes, characters;
		Builder(CensusLimits limits) { this.limits = limits; }
		Capture capture(List<Path> configured) throws IOException {
			if (configured.size() > limits.inputs()) limit("Configured input limit");
			if (configured.isEmpty()) fail(Status.UNSUPPORTED_INPUT, "No configured inputs");
			var origins = new ArrayList<InputOrigin>();
			var fingerprints = new java.util.LinkedHashMap<Path, FileFingerprint>();
			for (int i = 0; i < configured.size(); i++) {
				checkInterrupted();
				Path path = configured.get(i).toRealPath();
				// Production callers supply already validated canonical paths. Do not adopt a new
				// target if a path component was replaced by a symlink after that validation.
				if (!path.equals(configured.get(i).toAbsolutePath().normalize()))
					fail(Status.INPUT_CHANGED, "Canonical input identity changed since validation");
				if (!Files.isRegularFile(path)) fail(Status.UNSUPPORTED_INPUT, "Only individual class/JAR/DEX inputs supported");
				long size = Files.size(path);
				if (size > limits.fileBytes()) limit("Input byte limit");
				chargeBytes(size);
				var baseline = FileFingerprint.of(path, limits.fileBytes());
				var origin = new InputOrigin(i, path.toString()); text(origin.canonicalPath()); origins.add(origin);
				String name = path.getFileName().toString();
				int before = classes;
				if (name.endsWith(".jar")) jar(path, origin);
				else if (name.endsWith(".class") || name.endsWith(".dex")) {
					try (var in = Files.newInputStream(path)) { readCode(read(in), name, origin); }
				} else fail(Status.UNSUPPORTED_INPUT, "Input format outside class/JAR/DEX census subset");
				if (classes == before) fail(Status.UNSUPPORTED_INPUT, "Configured input supplies no understood declarations");
				if (!path.toRealPath().equals(path) || !baseline.equals(FileFingerprint.of(path, limits.fileBytes())))
					fail(Status.INPUT_CHANGED, "Input changed during census");
				var previous = fingerprints.putIfAbsent(path, baseline);
				if (previous != null && !previous.equals(baseline))
					fail(Status.INPUT_CHANGED, "Repeated configured input changed between reads");
			}
			return new Capture(new InputCensus(origins, records), fingerprints, limits, Status.COMPLETE, null);
		}
		void jar(Path path, InputOrigin origin) throws IOException {
			// Match Jadx's ZIP security and multi-release exclusion. Nested archives fail closed.
			try (var zip = new ZipReader().open(path.toFile())) {
				for (var entry : zip.getEntries()) {
					checkInterrupted();
					if (++entries > limits.archiveEntries()) limit("Archive entry limit");
					if (entry.isDirectory()) continue;
					String name = entry.getName();
					try (var in = entry.getInputStream()) {
						byte[] prefix = in.readNBytes(4);
						boolean java = magic(prefix, 0xca, 0xfe, 0xba, 0xbe) || name.endsWith(".class");
						boolean dex = magic(prefix, 'd', 'e', 'x', '\n') || name.endsWith(".dex");
						if (java && name.startsWith("META-INF/versions/")) continue;
						if (magic(prefix, 'P', 'K', 3, 4) || name.endsWith(".jar") || name.endsWith(".zip"))
							fail(Status.UNSUPPORTED_INPUT, "Nested archive outside census subset");
						if (!java && !dex) continue;
						byte[] remaining = in.readNBytes(limits.fileBytes() + 1);
						if ((long) prefix.length + remaining.length > limits.fileBytes()) limit("Expanded code entry byte limit");
						byte[] content = new byte[prefix.length + remaining.length];
						System.arraycopy(prefix, 0, content, 0, prefix.length);
						System.arraycopy(remaining, 0, content, prefix.length, remaining.length);
						chargeBytes(content.length); readCode(content, name, origin);
					}
				}
			}
		}
		byte[] read(InputStream in) throws IOException {
			byte[] data = in.readNBytes(limits.fileBytes() + 1);
			if (data.length > limits.fileBytes()) limit("Input byte limit");
			return data;
		}
		void readCode(byte[] content, String label, InputOrigin origin) {
			if (magic(content, 0xca, 0xfe, 0xba, 0xbe)) {
				add(new JavaClassReader(1, label, content).loadClassData(), origin);
			} else if (magic(content, 'd', 'e', 'x', '\n')) {
				// Strict reader throws instead of silently skipping broken class data. No instructions are requested.
				var options = new DexInputOptions(); options.setOptions(Map.of());
				var readers = new DexFileLoader(options).loadDexReaders(label, content);
				for (var reader : readers) {
					if ((long) classes + reader.getHeader().getClassDefsSize() > limits.classes()) limit("Raw class definition limit");
					reader.visitClasses(cls -> add(cls, origin));
				}
			} else fail(Status.FAILED, "Invalid class/DEX magic");
		}
		void add(IClassData data, InputOrigin origin) {
			checkInterrupted();
			if (++classes > limits.classes()) limit("Raw class definition limit");
			String owner = data.getType(); text(owner);
			var definitions = records.computeIfAbsent(owner, ignored -> new ArrayList<>());
			if (definitions.size() >= limits.duplicates()) limit("Duplicate definition limit");
			String superclass = data.getSuperType(); text(superclass);
			var interfaces = data.getInterfacesTypes();
			if ((long) edges + interfaces.size() + (superclass == null ? 0 : 1) > limits.edges()) limit("Hierarchy edge limit");
			edges += interfaces.size() + (superclass == null ? 0 : 1); interfaces.forEach(this::text);
			var declarations = new ArrayList<MethodRecord>();
			data.visitFieldsAndMethods(f -> { }, new ISeqConsumer<IMethodData>() {
				@Override public void init(int count) {
					if (count < 0) fail(Status.FAILED, "Invalid declaration count");
					if (count > limits.methodsPerClass() || (long) methods + count > limits.methods()) limit("Method declaration limit");
				}
				@Override public void accept(IMethodData m) {
					checkInterrupted();
					if (declarations.size() >= limits.methodsPerClass() || ++methods > limits.methods()) limit("Method declaration limit");
					var ref = m.getMethodRef(); ref.load();
					if (!owner.equals(ref.getParentClassType())) fail(Status.FAILED, "Inconsistent raw method owner");
					String descriptor = "(" + String.join("", ref.getArgTypes()) + ")" + ref.getReturnType();
					text(ref.getName()); text(descriptor);
					declarations.add(new MethodRecord(new SymbolRef(SymbolRef.Kind.METHOD, owner, null, ref.getName(), descriptor), m.getAccessFlags()));
				}
			});
			definitions.add(new ClassRecord(owner, origin, superclass, interfaces, data.getAccessFlags(), declarations));
		}
		void text(String value) { if (value != null && (characters += value.length()) > limits.characters()) limit("Metadata character limit"); }
		void chargeBytes(long count) { if ((bytes += count) > limits.totalBytes()) limit("Total census byte limit"); }
	}
	private static boolean magic(byte[] bytes, int a, int b, int c, int d) {
		return bytes.length >= 4 && (bytes[0] & 255) == a && (bytes[1] & 255) == b && (bytes[2] & 255) == c && (bytes[3] & 255) == d;
	}
	private static void checkInterrupted() { if (Thread.currentThread().isInterrupted()) fail(Status.FAILED, "Census interrupted cooperatively"); }
	private static void limit(String reason) { fail(Status.RESOURCE_LIMIT, reason); }
	private static void fail(Status status, String reason) { throw new CensusFailure(status, reason); }
	private static final class CensusFailure extends RuntimeException {
		final Status status;
		CensusFailure(Status status, String reason) { super(reason); this.status = status; }
	}
}
