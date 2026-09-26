package probe;

/** Owned reference fixture; no third-party code. MissingDependency is omitted from the analysis JAR. */
public class ReferenceFixture extends ReferenceBase implements ReferenceApi {
    public int value;
    public static int total;
    public ReferenceOther other;
    public void entry() {
        helper(); helper(); helper(3);
        new ReferenceOther().work();
        value = total; total = value; value++;
        MissingDependency.external("input");
    }
    public void helper() { value++; }
    public int helper(int depth) { return depth == 0 ? 0 : helper(depth - 1); }
    public ReferenceOther signature(ReferenceOther argument) { return argument; }
    public void dispatch(ReferenceApi api, ReferenceBase base) { api.invoke(); base.invoke(); }
    @Override public void invoke() { helper(); }
    public Runnable lambda() { return () -> helper(); }
    public Runnable anonymous() { return new Runnable() { public void run() { helper(); } }; }
    public Class<?> reflection() throws Exception { return Class.forName("probe.ReflectiveOnly"); }
}
interface ReferenceApi { void invoke(); }
class ReferenceBase { public void invoke() { } }
class ReferenceOther { public void work() { } }
class ReflectiveOnly { }
class MissingDependency { public static String external(String input) { return input; } }
