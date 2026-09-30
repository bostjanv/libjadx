package probe;

import java.util.List;

/** Owned JVM shapes, compiled with -g:none; names below are not used as identity. */
public class LocalShapes {
    public int sink;
    public int straight(int input) {
        int local = input * 7;
        sink = local;
        return local + 3;
    }
    public int reuse(int input) {
        { int first = input + 11; sink = first; sink += first; }
        { int second = input * 13; sink += second; sink *= second; }
        return sink;
    }
    public int branch(int input) {
        int selected;
        if (input > 0) selected = input * 17; else selected = input - 19;
        sink = selected;
        return selected + 23;
    }
    public int loop(int input) {
        int total = 29;
        for (int index = 0; index < input; index++) total += index * 31;
        sink = total;
        return total;
    }
    public int nested(int input) {
        int outer = input + 37;
        if (input > 0) {
            int inner = outer * 41;
            sink = inner;
            outer += inner;
        }
        return outer;
    }
    public int pressure(int input) {
        int first = Math.abs(input + 43);
        int second = Math.abs(input * 47);
        sink = first + second;
        return first * second + Math.abs(input - 53);
    }
    public int simpleRetarget(int input) {
        int local = Math.abs(input + 43);
        sink = local;
        return local * local + Math.abs(input - 53);
    }
    public int caught(int input) {
        try { return Integer.parseInt("59") / input; }
        catch (RuntimeException failure) { sink = failure.hashCode(); return failure.toString().length(); }
    }
    public int switchFinally(int input) {
        int selected;
        try {
            switch (input) { case 0: selected = 61; break; case 1: selected = 67; break; default: selected = input * 71; }
            sink = selected;
            return selected;
        } finally { sink += input; }
    }
    public long wide(long input, double fraction) {
        long first = input * 73L;
        double second = fraction + 79.5;
        sink = (int) (first + second);
        return first + (long) second;
    }
    public String object(List<String> input) {
        String local = input.get(0).trim();
        sink = local.length();
        return local + input.size();
    }
}
interface LocalContract<T> { T bodyless(T input); }
class LocalBridge implements LocalContract<String> {
    public String bodyless(String input) { String local = input.trim(); return local + local.length(); }
}
class LocalUnrelated { public int cold(int input) { return input * 83; } }
