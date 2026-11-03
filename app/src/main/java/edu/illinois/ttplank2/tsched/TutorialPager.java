package edu.illinois.ttplank2.tsched;

/** Which page of the tutorial is showing, and where Back and Next lead. */
public final class TutorialPager {
    private final int count;
    private int index;

    /** A pager over {@code count} pages, starting on {@code startIndex} (kept within the pages). */
    public TutorialPager(int count, int startIndex) {
        if (count < 1) {
            throw new IllegalArgumentException("A tutorial needs at least one page.");
        }
        this.count = count;
        this.index = Math.max(0, Math.min(count - 1, startIndex));
    }

    public int index() {
        return index;
    }

    public int count() {
        return count;
    }

    public boolean isFirst() {
        return index == 0;
    }

    public boolean isLast() {
        return index == count - 1;
    }

    /** Moves to the next page; stays on the last page. */
    public void next() {
        if (!isLast()) {
            index++;
        }
    }

    /** Moves to the previous page; stays on the first page. */
    public void back() {
        if (!isFirst()) {
            index--;
        }
    }
}
