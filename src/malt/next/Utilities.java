package malt.next;

import jloda.util.FileInputIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * some utilities
 * <p/>
 * Created by huson on 8/17/14.
 */
public class Utilities {
    public final static int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static boolean verbose = false;


    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static byte[][][] grow(byte[][][] array) {
        final int newLength = (int) Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, array.length));
        if (verbose)
            System.err.print("[GrowA: " + array.length + " -> " + newLength + "]");
        byte[][][] result = new byte[newLength][][]; // min size is 16 entries
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static long[][][] grow(long[][][] array) {
        final int newLength = (int) Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, array.length));
        if (verbose)
            System.err.print("[GrowA: " + array.length + " -> " + newLength + "]");
        long[][][] result = new long[newLength][][]; // min size is 16 entries
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static byte[][] grow(byte[][] array) {
        final int newLength = (int) Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, array.length));
        if (verbose)
            System.err.print("[GrowB: " + array.length + " -> " + newLength + "]");
        byte[][] result = new byte[newLength][]; // min size is 16 entries
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static long[][] grow(long[][] array) {
        final int newLength = (int) Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, array.length));
        if (verbose)
            System.err.print("[GrowB: " + array.length + " -> " + newLength + "]");
        long[][] result = new long[newLength][]; // min size is 16 entries
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static int[] grow(int[] array) {
        final int newLength = (int) (Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, array.length)));
        if (verbose)
            System.err.print("[GrowC: " + array.length + " -> " + newLength + "]");
        int[] result = new int[newLength]; // min size is 16 entries
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static Mask[] grow(Mask[] array) {
        final int newLength = (int) (Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, array.length)));
        if (verbose)
            System.err.print("[GrowM: " + array.length + " -> " + newLength + "]");
        Mask[] result = new Mask[newLength]; // min size is 16 entries
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }


    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    public static long[] growEven(long[] array) {
        int newLength = (int) Math.min(MAX_ARRAY_SIZE, Math.max(16, 2l * array.length));  // min size is 16 entries
        if ((newLength % 2) == 1)
            newLength--; // even!
        if (verbose)
            System.err.print("[GrowD: " + array.length + " -> " + newLength + "]");
        long[] result = new long[newLength];
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * gets the position of the most significant bit in the given number
     *
     * @param number
     * @return position of highest set bit
     */
    public static int getMostSignificantBit(int number) {
        int mask = (1 << 31);
        for (int bitIndex = 31; bitIndex >= 0; bitIndex--) {
            if ((number & mask) != 0) {
                return bitIndex;
            }
            mask >>>= 1;
        }
        return -1;
    }

    /**
     * grow, if necessary
     *
     * @param seedStores
     * @param newSize
     * @return new array
     */
    public static SeedStore[] grow(SeedStore[] seedStores, int newSize) {
        if (newSize > seedStores.length) {
            SeedStore[] result = new SeedStore[newSize];
            System.arraycopy(seedStores, 0, result, 0, seedStores.length);
            return result;
        } else
            return seedStores;
    }

    /**
     * returns all trimmed lines in a file, excluding empty lines or lines that start with #
     *
     * @param fileName
     * @return lines
     * @throws java.io.IOException
     */
    public static List<String> getAllLines(String fileName) throws IOException {
        final List<String> list = new ArrayList<String>();
        FileInputIterator it = new FileInputIterator(fileName);
        while (it.hasNext()) {
            String aLine = it.next().trim();
            if (aLine.length() > 0 && !aLine.startsWith("#"))
                list.add(aLine);
        }
        it.close();
        return list;
    }
}
