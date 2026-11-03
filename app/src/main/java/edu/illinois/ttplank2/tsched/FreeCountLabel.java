package edu.illinois.ttplank2.tsched;

/** The count on the Unplaced Free Events button, which stops at 9+ so the button stays short. */
public final class FreeCountLabel {
    private static final int MAX_SHOWN = 9;

    private FreeCountLabel() {
    }

    public static String of(int count) {
        return count > MAX_SHOWN ? MAX_SHOWN + "+" : String.valueOf(count);
    }
}
