package malt.malt2;

/**
 * list of pairs of numbers
 * Created by huson on 8/11/14.
 */
public class ListOfPairs {
    private final int[] data;
    private int length = 0;
    private boolean overflowed = false;

    /**
     * constructor
     *
     * @param maxNumberOfPairs
     */
    public ListOfPairs(int maxNumberOfPairs) {
        data = new int[2 * maxNumberOfPairs];
    }

    /**
     * erase all pairs
     */
    public void clear() {
        length = 0;
        overflowed = false;
    }

    /**
     * add a pair
     *
     * @param a
     * @param b
     */
    public void add(int a, int b) {
        if (length < data.length) {
            data[length++] = a;
            data[length++] = b;
        } else overflowed = true;
    }

    /**
     * put a pair at the given index
     *
     * @param index
     * @param a
     * @param b
     */
    public void put(int index, int a, int b) {
        index <<= 1;
        data[index] = a;
        data[index + 1] = b;
    }

    /**
     * get first value of i-th pair
     *
     * @param index
     * @return first value
     */
    public int getFirstValue(int index) {
        return data[index << 1];
    }

    /**
     * get second value of i-th pair
     *
     * @param i
     * @return
     */
    public int getSecondValue(int i) {
        return data[(i << 1) + 1];
    }

    /**
     * get number of pairs
     *
     * @return size
     */
    public int size() {
        return length / 2;
    }

    /**
     * did we attempt to add more than the maximum number of entries
     *
     * @return true, if overflowed
     */
    public boolean isOverflowed() {
        return overflowed;
    }
}
