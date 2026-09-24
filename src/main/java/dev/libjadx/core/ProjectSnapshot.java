package dev.libjadx.core;

import java.nio.file.Path;
import java.util.List;

/** Immutable public view of the one fixed project. */
public record ProjectSnapshot(Path projectPath, List<Path> inputs, boolean dirty, RevisionState revisions) {
	public ProjectSnapshot {
		inputs = List.copyOf(inputs);
	}
}
