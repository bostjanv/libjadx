package probe;

public abstract class SourceFixture {
    public int count = 2;
    static { System.setProperty("libjadx.source.fixture", "ready"); }

    public SourceFixture() { }
    public abstract int missing(int value);

    public int tricky(int value) {
        String braces = "} { /* text */";
        String block = """
                } { // text block
                """;
        Runnable lambda = () -> System.out.println(braces + block);
        lambda.run();
        return value + this.count;
    }

    public interface Api { void invoke(); }
}
