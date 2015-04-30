package malt.data;

/**
 * a row of numbers that are stored in a larger array.
 * Daniel Huson, 8.2014
 */

public class BuildRow {
    private int size;
    private int offset;
    private int[] containingArray;
    private final int[] pair = new int[]{0, 0};

    /**
     * constructor
     */
    public BuildRow() {
    }

    /**
     * Set the row. array[offset] must contain size, i.e. the number of integers to be used
     * array[offset+1]... array[offset+size-1] are the numbers
     *
     * @param array  array containing size followed by entries
     * @param offset location of of size entry in array
     */
    public void set(int[] array, int offset) {
        this.size = array[offset];
        this.containingArray = array;
        this.offset = offset + 1;
    }

    /**
     * setting a single pair of numbers
     *
     * @param firstNumber
     * @param secondNumber
     */
    public void setPair(int firstNumber, int secondNumber) {
        size = 2;
        offset = 0;
        pair[0] = firstNumber;
        pair[1] = secondNumber;
        containingArray = pair;
    }

    /**
     * set to empty
     */
    public void setEmpty() {
        size = 0;
    }

    /**
     * gets the number of int in this row
     *
     * @return size
     */
    public int size() {
        return size;
    }

    /**
     * use this to access numbers 0,..,size-1
     *
     * @param index
     * @return item
     */
    public int get(int index) {
        return containingArray[offset + index];
    }

    /**
     * get array that contains numbers
     *
     * @return full row
     */
    public int[] getContainingArray() {
        return containingArray;
    }

    /**
     * get offset at which numbers start (position of size entry plus 1)
     *
     * @return offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * get string representation
     *
     * @return
     */
    public String toString() {
        if (size > 0) {
            StringBuilder buf = new StringBuilder();
            buf.append("(").append(size()).append("): ");
            for (int i = 0; i < size(); i++)
                buf.append(" ").append(get(i));
            return buf.toString();
        } else
            return "null";
    }
}
