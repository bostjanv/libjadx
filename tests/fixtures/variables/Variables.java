package probe;

/** Owned optimized JVM fixture; compiled without parameter names or debug locals. */
public class Variables {
    public int field;
    public int instance(int first, long wide, double fraction, String text) {
        int sum = first + (int) wide;
        int other = text.length() + (int) fraction;
        if (sum > other) { int branch = sum * 2; field = branch; }
        else { int branch = other * 3; field = branch; }
        for (int i = 0; i < first; i++) sum += i;
        return sum + other + field;
    }
    public static long statik(long wide, int count, double fraction) { return wide + count + (long) fraction; }
    public int single(int value) { return value + field; }
    public String single(String value) { return value + field; }
    @Deprecated public int annotated(@Deprecated int value) { return value + field; }
    public <T> T generic(T value) { return value; }
    public int zero() { return field; }
    public int merged(int value) {
        int total;
        if (value > 0) total = value + 1; else total = value - 1;
        for (int i = 0; i < 3; i++) total += i;
        return total;
    }
    public int temporary(int value) { return (value + 2) * (value + 3); }
    public int usedCatch(int value) {
        try { return Integer.parseInt("42") + value; }
        catch (NumberFormatException failure) { return value + failure.getMessage().length(); }
    }
    public int catchText(int value) { return value + "catch (Exception unused)".length(); }
}
class VariableUnrelated { public int cold(int value) { return value * 2; } }

interface VariableContract { int absentBody(int value); }
