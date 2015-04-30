package malt.util;

import jloda.util.Basic;

import java.util.Iterator;

/**
 * store a collection of integer arrays
 * Daniel Huson, 8.2014
 */
public class ListOfIntArrays {
    private static final int MAX_NUMBER_OF_SEGMENTS = 100000;
    private static final int SEGMENT_SIZE = 100000;
    private int[][] data;
    private int[] currentSegment;
    private int currentSegmentId;
    private int currentPosition;
    private int numberOfEntries;

    /**
     * constructor
     */
    public ListOfIntArrays() {
        currentSegmentId = 0;
        data = new int[MAX_NUMBER_OF_SEGMENTS][];
        currentSegment = data[currentSegmentId] = new int[SEGMENT_SIZE];
        currentPosition = 1;
        currentSegment[currentPosition] = -1; // indicates empty
    }

    /**
     * add an array
     *
     * @param array
     * @param length
     */
    public void add(int[] array, int length) {
        if (currentPosition + length + 2 >= currentSegment.length) {
            currentSegment = data[++currentSegmentId] = new int[SEGMENT_SIZE];
            currentPosition = 0;
        }
        currentSegment[currentPosition++] = length;
        if (length > 0)
            System.arraycopy(array, 0, currentSegment, currentPosition, length);
        currentPosition += length;
        currentSegment[currentPosition] = -1; // end of array, if we add more stuff to current segment, this will be overwritten
        numberOfEntries++;
    }

    /**
     * gets the number of arrays stored in this list
     *
     * @return number of arrays
     */
    public int size() {
        return numberOfEntries;
    }

    /**
     * get an getLetterCodeIterator over the arrays. Each array of length n is returned as an array of length n+1, where the first component contains the length of the array
     *
     * @param buffer use this to return arrays
     * @return getLetterCodeIterator
     */
    public Iterator<int[]> iterator(final int[] buffer) {
        return new Iterator<int[]>() {
            private int segmentId = 0;
            private int position = 1;
            private int[] segment = data[0];

            public boolean hasNext() {
                if (segment[position] == -1) // at end of array
                {
                    segmentId++;
                    if (segmentId > currentSegmentId) {
                        return false;
                    }
                    segment = data[segmentId];
                    position = 0;
                }
                return true;
            }

            public int[] next() {
                int length = segment[position];
                int[] result;
                if (buffer == null)
                    result = new int[length + 1];
                else
                    result = buffer;
                System.arraycopy(data[segmentId], position, result, 0, length + 1);
                position += length + 1;
                return result;
            }

            public void remove() {
            }
        };
    }

    /**
     * get an getLetterCodeIterator over the arrays. Each array of length n is returned as an array of length n+1, where the first component contains the length of the array
     *
     * @return getLetterCodeIterator
     */
    public Iterator<int[]> iterator() {
        return iterator(null);
    }

    /**
     * test this construct
     *
     * @param args
     */
    public static void main(String[] args) {
        ListOfIntArrays list = new ListOfIntArrays();

        System.err.println("Adding:");
        for (int i = 0; i < 10; i++) {
            int[] array = new int[i];
            for (int k = 0; k < i; k++)
                array[k] = k;
            System.err.println("array: " + Basic.toString(array, 0, i, " "));
            list.add(array, i);
        }
        for (int i = 9; i >= 0; i--) {
            int[] array = new int[i];
            for (int k = 0; k < i; k++)
                array[k] = k;
            System.err.println("array: " + Basic.toString(array, 0, i, " "));
            list.add(array, i);
        }

        System.err.println("Iterating:");

        for (Iterator<int[]> it = list.iterator(); it.hasNext(); ) {
            int[] array = it.next();
            System.err.println("array: " + Basic.toString(array, 1, array[0], " "));
        }
    }
}
