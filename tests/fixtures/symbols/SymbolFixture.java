package probe;

public class SymbolFixture {
    static { System.setProperty("libjadx.symbol.fixture", "loaded"); }

    public int count = 3;
    public String[] names = {"one"};

    public SymbolFixture() { }
    public int mix(int value) { return value + count; }
    public String mix(String[] value, int count) { return value[count]; }
    public Runnable anonymous() { return new Runnable() { public void run() { mix(2); } }; }

    public class Inner {
        public long value(long[] input) { return input[0]; }
    }
}
