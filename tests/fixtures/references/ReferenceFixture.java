package probe;

/** Owned reference fixture; no third-party code. MissingDependency is omitted from the analysis JAR. */
public class ReferenceFixture extends ReferenceBase implements ReferenceApi {
    public int value;
    public static int total;
    public ReferenceOther other;
    public void entry() {
        helper(); helper(); helper(); helper();
        helper(); helper(); helper(); helper(); helper(3);
        new ReferenceOther().work();
        value = total; total = value; value++;
        MissingDependency.external("input");
    }
    public void helper() { value++; }
    public int helper(int depth) { return depth == 0 ? 0 : helper(depth - 1); }
    public void fanout() {
        fan0(); fan1(); fan2(); fan3(); fan4();
        fan5(); fan6(); fan7(); fan8(); fan9();
    }
    public void fan0() { }
    public void fan1() { }
    public void fan2() { }
    public void fan3() { }
    public void fan4() { }
    public void fan5() { }
    public void fan6() { }
    public void fan7() { }
    public void fan8() { }
    public void fan9() { }
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
class ReferenceLate {
    public void invoke(ReferenceFixture fixture) { bridge(fixture); }
    private static void bridge(ReferenceFixture fixture) { fixture.helper(); }
}
