package dev.libjadx.app;

/** Persistence choice made by an explicit HTTP shutdown request. */
public enum ShutdownPolicy {
	DISCARD("discard"), SAVE("save"), REFUSE_IF_DIRTY("refuse_if_dirty");

	private final String wireName;
	ShutdownPolicy(String wireName) { this.wireName = wireName; }
	public String wireName() { return wireName; }

	public static ShutdownPolicy fromWireName(String value) {
		for (ShutdownPolicy policy : values()) if (policy.wireName.equals(value)) return policy;
		throw new IllegalArgumentException("Unknown shutdown policy");
	}
}
