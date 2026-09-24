package dev.libjadx.core;

/** Revision tokens are scoped to one process session. */
public record RevisionState(String sessionId, long logicalRevision, long indexRevision,
		String persistedIdentity, String persistedIdentityState) { }
