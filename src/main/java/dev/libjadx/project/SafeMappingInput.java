package dev.libjadx.project;

import static dev.libjadx.core.mappings.MappingExportDtos.MAX_BYTES;
import static dev.libjadx.core.mappings.MappingImportDtos.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Owned bounded read, no writes. Optimistic identity/content checks do not lock external writers. */
public final class SafeMappingInput {
	private record Identity(Path path, Object key) { }
	private final Path path;
	private final List<Path> roots;
	private final List<Path> protectedPaths;
	private final List<Identity> parents;
	private final BasicFileAttributes captured;
	private final byte[] bytes;
	private final String digest;

	public SafeMappingInput(Path path, List<Path> roots, List<Path> protectedPaths) throws IOException {
		this.path = path; this.roots = List.copyOf(roots); this.protectedPaths = List.copyOf(protectedPaths);
		this.parents = validate();
		this.captured = attributes(path);
		if (captured.fileKey() == null) throw unsupported("FILESYSTEM_IDENTITY_UNAVAILABLE", null);
		if (captured.size() > MAX_BYTES) throw limit();
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			bytes = input.readNBytes(MAX_BYTES + 1);
		}
		if (bytes.length > MAX_BYTES) throw limit();
		digest = "sha256:" + HexFormat.of().formatHex(FileFingerprint.sha256Digest().digest(bytes));
		checkIdentity();
	}
	public Path path() { return path; }
	/** Borrow only within one admitted operation; this owned buffer must never be mutated. */
	public byte[] bytes() { return bytes; }
	public String sha256() { return digest; }

	/** Final read is streamed and bounded; verify both content and file/directory identities. */
	public void verifyUnchanged() throws IOException {
		checkIdentity();
		var hash = FileFingerprint.sha256Digest();
		long count = 0;
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0;) {
				count += read;
				if (count > MAX_BYTES) throw changed();
				hash.update(buffer, 0, read);
			}
		}
		if (count != bytes.length || !digest.equals("sha256:" + HexFormat.of().formatHex(hash.digest()))) throw changed();
		checkIdentity();
	}
	private List<Identity> validate() throws IOException {
		if (roots.stream().noneMatch(path::startsWith)) throw new SecurityException("Mapping source is outside configured allowed roots");
		List<Identity> identities = new ArrayList<>();
		Path current = path.getRoot();
		for (Path component : path) {
			current = current.resolve(component);
			var attrs = attributes(current);
			if (attrs.isSymbolicLink()) throw new SecurityException("Mapping source may not traverse a symbolic link");
			if (current.equals(path)) {
				if (!attrs.isRegularFile()) throw new SecurityException("Mapping source must be a regular file");
			} else {
				if (!attrs.isDirectory()) throw new SecurityException("Mapping source parent must be a directory");
				if (attrs.fileKey() == null) throw unsupported("FILESYSTEM_IDENTITY_UNAVAILABLE", null);
				identities.add(new Identity(current, attrs.fileKey()));
			}
		}
		if (!path.toRealPath().equals(path)) throw new SecurityException("Mapping source must be canonical");
		for (Path protectedPath : protectedPaths) {
			if (path.equals(protectedPath) || (Files.exists(protectedPath) && Files.isSameFile(path, protectedPath))) {
				throw new SecurityException("Mapping source may not be a project or input file");
			}
		}
		return List.copyOf(identities);
	}
	private void checkIdentity() throws IOException {
		try {
			List<Identity> observed = validate();
			var attrs = attributes(path);
			if (!parents.equals(observed) || !attrs.isRegularFile() || !Objects.equals(captured.fileKey(), attrs.fileKey())
					|| captured.size() != attrs.size() || !captured.lastModifiedTime().equals(attrs.lastModifiedTime())
					|| !captured.creationTime().equals(attrs.creationTime())) throw changed();
		} catch (NoSuchFileException disappeared) { throw changed(); }
	}
	private static BasicFileAttributes attributes(Path path) throws IOException {
		return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
	}
	private static NativeProjectRepository.ExternalModificationException changed() {
		return new NativeProjectRepository.ExternalModificationException("Mapping source changed since capture; retry against a fresh source");
	}
}
