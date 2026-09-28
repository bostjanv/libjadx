package dev.libjadx.project;

import static dev.libjadx.core.mappings.MappingExportDtos.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;

/** New operational output only. Hard-link publication never replaces a destination. */
public final class SafeMappingOutput implements AutoCloseable {
	@FunctionalInterface public interface Hook { void at(String stage) throws Exception; }
	private final Path target;
	private final List<Path> roots;
	private final List<Path> protectedPaths;
	private final Object parentKey;
	private final Hook hook;
	private final DirectoryStream<Path> directory;
	private final SecureDirectoryStream<Path> secureDirectory;
	private Path staged;
	private Object stagedKey;
	private boolean published;

	public SafeMappingOutput(Path target, List<Path> roots, List<Path> protectedPaths, Hook hook) throws IOException {
		this.target = target; this.roots = roots; this.protectedPaths = protectedPaths; this.hook = hook;
		validate();
		this.parentKey = attributes(target.getParent()).fileKey();
		if (parentKey == null) throw unsupported("FILESYSTEM_DIRECTORY_IDENTITY_UNAVAILABLE");
		this.directory = Files.newDirectoryStream(target.getParent());
		this.secureDirectory = directory instanceof SecureDirectoryStream<Path> secure ? secure : null;
	}
	public void stage(byte[] bytes) throws Exception {
		recheck();
		if (Files.getFileStore(target.getParent()).supportsFileAttributeView("posix")) {
			staged = Files.createTempFile(target.getParent(), ".libjadx-mapping-", ".tmp",
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		} else staged = Files.createTempFile(target.getParent(), ".libjadx-mapping-", ".tmp");
		stagedKey = attributes(staged).fileKey();
		if (stagedKey == null) throw unsupported("FILESYSTEM_FILE_IDENTITY_UNAVAILABLE");
		hook.at("WRITE");
		try (FileChannel output = FileChannel.open(staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) output.write(buffer);
			output.force(true);
		}
		// Exercise the filesystem's existing-name behavior on our own inode.
		try {
			Files.createLink(staged, staged);
			throw unsupported("FILESYSTEM_NO_CLOBBER_NOT_PROVEN");
		} catch (FileAlreadyExistsException proven) { /* expected, no replacement */ }
	}
	public byte[] readStaged() throws IOException {
		checkOwnedStage();
		try (FileChannel input = FileChannel.open(staged, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
			long size = input.size();
			if (size > MAX_BYTES) throw limit();
			ByteBuffer bytes = ByteBuffer.allocate((int) size);
			while (bytes.hasRemaining()) { if (input.read(bytes) < 0) throw new IOException("Staged output changed"); }
			if (input.size() != size) throw new IOException("Staged output changed");
			return bytes.array();
		}
	}
	public void publish(byte[] verified) throws Exception {
		hook.at("BEFORE_PUBLISH");
		recheck(); checkOwnedStage();
		hook.at("PUBLICATION");
		recheck(); checkOwnedStage();
		if (!java.util.Arrays.equals(verified, readStaged())) throw new IOException("Staged bytes changed after verification");
		// createLink is a single no-clobber directory operation, unlike ATOMIC_MOVE.
		try { Files.createLink(target, staged); }
		catch (FileAlreadyExistsException race) { throw conflict(); }
		catch (UnsupportedOperationException unsupportedFs) { throw unsupported("FILESYSTEM_HARD_LINK_UNAVAILABLE"); }
		published = true;
		hook.at("AFTER_PUBLISH");
		if (!Objects.equals(parentKey, attributes(target.getParent()).fileKey())) {
			throw new IOException("Output parent changed during publication");
		}
		checkOwnedStage();
		Files.delete(staged); staged = null;
	}
	public boolean published() { return published; }
	private void validate() throws IOException {
		if (protectedPaths.stream().anyMatch(path -> path.toAbsolutePath().normalize().equals(target))) {
			throw new SecurityException("Output may not target a project, input or attached mapping path");
		}
		Path parent = target.getParent();
		Path current = parent.getRoot();
		for (Path component : parent) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current)) throw new SecurityException("Output parent may not traverse a symbolic link");
			if (!attributes(current).isDirectory()) throw new SecurityException("Output parent must be an existing directory");
		}
		Path realParent = parent.toRealPath();
		if (!realParent.equals(parent) || roots.stream().noneMatch(realParent::startsWith)) {
			throw new SecurityException("Output parent is outside configured allowed roots");
		}
		if (Files.isSymbolicLink(target)) throw new SecurityException("Output may not be a symbolic link");
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw conflict();
	}
	private void recheck() throws IOException {
		validate();
		if (!Objects.equals(parentKey, attributes(target.getParent()).fileKey())) {
			throw new SecurityException("Output parent changed since validation");
		}
	}
	private void checkOwnedStage() throws IOException {
		if (staged == null || !attributes(staged).isRegularFile()
				|| !Objects.equals(stagedKey, attributes(staged).fileKey())) throw new IOException("Owned staging file changed");
	}
	private static BasicFileAttributes attributes(Path path) throws IOException {
		return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
	}
	private static NativeProjectRepository.ExternalModificationException conflict() {
		return new NativeProjectRepository.ExternalModificationException("Mapping export target already exists; choose a new path");
	}
	@Override public void close() throws IOException {
		try {
			if (staged != null && secureDirectory != null) {
				var attrs = secureDirectory.getFileAttributeView(staged.getFileName(), java.nio.file.attribute.BasicFileAttributeView.class,
						LinkOption.NOFOLLOW_LINKS).readAttributes();
				if (attrs.isRegularFile() && Objects.equals(stagedKey, attrs.fileKey())) {
					secureDirectory.deleteFile(staged.getFileName()); staged = null;
				}
			} else if (staged != null && Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
				// Only remove our regular staging inode, never the destination or a replacement.
				if (Objects.equals(parentKey, attributes(target.getParent()).fileKey())
						&& attributes(staged).isRegularFile() && Objects.equals(stagedKey, attributes(staged).fileKey())) {
					Files.delete(staged); staged = null;
				}
			}
		} finally { directory.close(); }
	}
}
