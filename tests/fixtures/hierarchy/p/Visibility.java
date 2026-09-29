package hierarchy.p;

/** Owned visibility and inherited-interface contexts for the independent verifier. */
public class Visibility {
    public static class Base {
        int packageMethod(int n) { return n; }
        protected int protectedMethod(int n) { return n; }
        public final int finalMethod(int n) { return n; }
        public int overload(int n) { return n; }
        public int overload(String n) { return n.length(); }
    }
    public static class Same extends Base {
        int packageMethod(int n) { return n + 1; }
    }
    public static final class FinalLeaf extends Base {
        public int protectedMethod(int n) { return n + 2; }
    }
    public interface Contract { int inherited(int n); }
    public static class ImplementationBase { public int inherited(int n) { return n; } }
    public static class InheritedImplementation extends ImplementationBase implements Contract { }
    public interface Simple { int simple(int n); }
    public static class SimpleImpl implements Simple { public int simple(int n) { return n; } }
    public interface Generic<T> { T generic(T n); }
    public static class GenericImpl implements Generic<String> {
        public String generic(String n) { return n; }
    }
    public static class Synthetic {
        public Runnable lambda() { return () -> System.out.println("owned"); }
    }
    public interface Omitted { int missingInterface(int n); }
    public static class MissingInterface implements Omitted { public int missingInterface(int n) { return n; } }
}
