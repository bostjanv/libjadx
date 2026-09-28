package related;

/** Independently owned PR #14 hierarchy census; no upstream fixture code. */
public class Hierarchy {
    public abstract static class Base {
        public abstract int work(int value);
        public int work(String value) { return value.length(); }
        private int hidden(int value) { return value; }
        public static int hiding(int value) { return value; }
    }
    public static class Middle extends Base {
        public int work(int value) { return value + 1; }
        private int hidden(int value) { return value + 1; }
        public static int hiding(int value) { return value + 1; }
    }
    public static class Leaf extends Middle {
        public int work(int value) { return value + 2; }
    }
    public static class Sibling extends Base {
        public int work(int value) { return value + 3; }
        public int collision(int value) { return value; }
    }
    public static class Unrelated {
        public int work(int value) { return value; }
    }
    public interface Root { int call(int value); }
    public interface Left extends Root { int call(int value); }
    public interface Right extends Root { int call(int value); }
    public interface Diamond extends Left, Right { int call(int value); }
    public static class Implementation implements Diamond {
        public int call(int value) { return value; }
    }
    public interface DefaultRoot { default int run(int value) { return value; } }
    public static class DefaultImplementation implements DefaultRoot {
        public int run(int value) { return value + 1; }
    }
    public interface SeparateLeft { int joined(int value); }
    public interface SeparateRight { int joined(int value); }
    public interface ExtendedLeft extends SeparateLeft { int joined(int value); }
    public static class Joined implements ExtendedLeft, SeparateRight {
        public int joined(int value) { return value; }
    }
    public static class CovariantBase { public Object value() { return "base"; } }
    public static class CovariantLeaf extends CovariantBase {
        public String value() { return "leaf"; }
    }
    public class Inner extends Middle {
        public int work(int value) { return value + 4; }
    }
    public static class MissingParent extends MissingRoot {
        public int lost(int value) { return value; }
    }
    public static class External implements Runnable { public void run() { } }
}
class MissingRoot { public int lost(int value) { return value; } }
class UnrelatedCold { public int work(int value) { return value; } }
