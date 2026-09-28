package probe;

/** Independent classes keep the cold-edit probe separate from Jadx's owner dependencies. */
public class EditOwner {
    public int count = 3;
    public int otherCount = 4;
    public int work() { return count; }
    public int otherWork() { return otherCount; }
}

class UnrelatedA {
    public int value() { return 11; }
}

class UnrelatedB {
    public int value() { return 22; }
}
