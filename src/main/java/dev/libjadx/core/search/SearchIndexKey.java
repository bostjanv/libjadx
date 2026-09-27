package dev.libjadx.core.search;

/** Complete identity of one published primary Jadx engine. */
public record SearchIndexKey(String sessionId, long logicalRevision, long publicationEpoch,
		String settingsFingerprint) {
	public String snapshotId() {
		return sessionId + ":" + logicalRevision + ":" + publicationEpoch + ":" + settingsFingerprint;
	}
}
