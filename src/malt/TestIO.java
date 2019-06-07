/*
 *  TestIO.java Copyright (C) 2019. Daniel H. Huson GPL
 *
 *   (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package malt;

import jloda.util.Basic;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

/**
 * Test IO
 * Daniel Huson, 8.2014
 */
public class TestIO {
    public static final byte[] MAGIC_NUMBER = "HEAT-IDX".getBytes();

    public static void main(String[] args) throws IOException {

        String choice = (args.length == 0 ? "wfrf wnrn" : Basic.toString(args, " "));

        // create a test buffer
        int[][] arrays = createArrays();

        long start = System.currentTimeMillis();
        if (choice.contains("wn")) {
            // do the first test (the normal way of writing files)
            normalToFile("/Users/huson/tmp/heat/first.idx", arrays);
        }
        long one = System.currentTimeMillis();
        if (choice.contains("wn"))
            System.out.println("normal write: " + (one - start));

        if (choice.contains("wf")) {
            // use the faster nio stuff
            fasterToFile("/Users/huson/tmp/heat/second.idx", arrays);
        }
        long two = System.currentTimeMillis();

        // print the result
        if (choice.contains("wf"))
            System.out.println("faster write: " + (two - one));

        System.out.println();

        long a = System.currentTimeMillis();
        int[][] normalResults = null;
        if (choice.contains("rn")) {
            normalResults = normalFromFile("/Users/huson/tmp/heat/first.idx");
        }

        long b = System.currentTimeMillis();
        if (choice.contains("rn"))
            System.out.println("normal read: " + (b - a));

        if (normalResults != null) {
            if (arrays.length != normalResults.length)
                throw new IOException("arrays.length=" + arrays.length + "!= normalResults.length: " + normalResults.length);
            for (int i = 0; i < arrays.length; i++) {
                if (arrays[i].length != normalResults[i].length) {
                    throw new IOException("array[" + i + "].length=" + arrays[i].length + "!= normalResults[" + i + "].length: " + normalResults[i].length);
                }
            }
            System.err.println("normalResults ok");
        }

        int[][] fasterResults = null;
        if (choice.contains("rf")) {
            fasterResults = fasterFromFile("/Users/huson/tmp/heat/second.idx");
        }
        long c = System.currentTimeMillis();
        if (choice.contains("rf"))
            System.out.println("faster read: " + (c - b));

        if (fasterResults != null) {
            if (arrays.length != fasterResults.length)
                throw new IOException("arrays.length=" + arrays.length + "!= fasterResults.length: " + fasterResults.length);
            for (int i = 0; i < arrays.length; i++) {
                if (arrays[i].length != fasterResults[i].length) {
                    throw new IOException("array[" + i + "].length=" + arrays[i].length + "!= fasterResults[" + i + "].length: " + fasterResults[i].length);
                }
            }
            System.err.println("fasterResults ok");
        }

    }

    public static void fasterToFile(String fileName, int[][] arrays) throws IOException {
        // final long maxNumberOfInts = Integer.MAX_VALUE / 4 - MAGIC_NUMBER.length;
        final long maxNumberOfInts = 100 * arrays.length;   // about 10 files

        int start = 0;
        int fileCount = 0;
        while (start < arrays.length) {

            int numberOfInts = (fileCount == 0 ? 2 : 1);

            int end = start;
            while (end < arrays.length && numberOfInts + arrays[end].length < maxNumberOfInts) {
                numberOfInts += arrays[end++].length;
            }

            final File file = new File(replaceFileSuffix(fileName, "-" + fileCount + ".idx"));
            if (file.exists() && !file.delete())
                throw new IOException("Failed to delete existing file: " + file);

            final RandomAccessFile out = new RandomAccessFile(file, "rw");
            final FileChannel fc = out.getChannel();

            final int size = 4 * numberOfInts + MAGIC_NUMBER.length;

            final ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, 0, size);

            buf.put(MAGIC_NUMBER); // magic number comes first
            buf.putInt(fileCount);   // file number comes second
            if (fileCount == 0)
                buf.putInt(arrays.length); // first file additionally contains total number of arrays

            for (int i = start; i < end; i++) {
                final int[] array = arrays[i];
                final int length = array.length;
                buf.putInt(length);
                for (int j = 1; j < length; j++)
                    buf.putInt(array[j]);
            }
            out.close();
            start = end;
            fileCount++;
        }
    }

    public static int[][] fasterFromFile(String fileName) throws IOException {

        int[][] arrays = null;
        int theArraysLength = Integer.MAX_VALUE;

        int fileCount = 0;

        int arrayNumber = 0; // which array are we reading

        while (arrayNumber < theArraysLength) {
            final File file = new File(replaceFileSuffix(fileName, "-" + fileCount + ".idx"));

            final FileInputStream ins = new FileInputStream(file);
            final FileChannel fc = ins.getChannel();

            final ByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            byte[] theMagicNumber = new byte[MAGIC_NUMBER.length];
            buf.get(theMagicNumber, 0, theMagicNumber.length); // magic number comes first
            int theFileCount = buf.getInt();
            if (theFileCount != fileCount)
                throw new IOException("Incorrect file count=" + theFileCount + ", expected: " + fileCount);

            if (fileCount == 0) {
                theArraysLength = buf.getInt();
                arrays = new int[theArraysLength][];
            }

            while (buf.hasRemaining()) {
                int length = buf.getInt();
                int[] array = new int[length];
                arrays[arrayNumber++] = array;
                array[0] = length;
                for (int i = 1; i < length; i++)
                    array[i] = buf.getInt();
                // System.err.println("Got: "+Basic.toString(array,","));
            }
            ins.close();
            fileCount++;
        }
        return arrays;
    }

    private static void normalToFile(String fileName, int[][] arrays) throws IOException {
        try (OutputStream outs = new BufferedOutputStream(new FileOutputStream(fileName))) {
            outs.write(MAGIC_NUMBER, 0, MAGIC_NUMBER.length);
            byte[] buffer = new byte[8];

            writeInt(outs, arrays.length, buffer);
            for (int[] array : arrays) {
                int length = array.length;
                writeInt(outs, length, buffer);
                for (int i = 1; i < length; i++)
                    writeInt(outs, array[i], buffer);
            }

        }
    }

    private static int[][] normalFromFile(String fileName) throws IOException {
        InputStream ins = new BufferedInputStream(new FileInputStream(fileName));
        byte[] theMagicNumber = new byte[MAGIC_NUMBER.length];
        ins.read(theMagicNumber);
        byte[] buffer = new byte[8];
        int theArraysLength = readInt(ins, buffer);
        int[][] arrays = new int[theArraysLength][];
        for (int i = 0; i < theArraysLength; i++) {
            int length = readInt(ins, buffer);
            int[] array = new int[length];
            arrays[i] = array;
            for (int j = 1; j < length; j++)
                array[j] = readInt(ins, buffer);
        }

        ins.close();
        return arrays;
    }


    private static int[][] createArrays() {
        if (true) {
            Random random = new Random(666);
            int[][] arrays = new int[50000][];
            for (int i = 0; i < arrays.length; i++) {
                arrays[i] = new int[random.nextInt(1000) + 1];
                arrays[i][0] = arrays[i].length;
                for (int j = 1; j < arrays[i].length; j++)
                    arrays[i][j] = random.nextInt(100);
            }
            return arrays;
        } else {
            int[][] arrays = new int[10][];
            for (int i = 0; i < arrays.length; i++) {
                int length = i + 1;
                int[] array = new int[length];
                arrays[i] = array;
                array[0] = length;
                for (int j = 1; j < length; j++)
                    array[j] = j;
            }
            return arrays;
        }
    }

    /**
     * writes an int value
     *
     * @param outs
     * @param value
     * @param bytes
     * @throws java.io.IOException
     */
    public static void writeInt(OutputStream outs, int value, byte[] bytes) throws IOException {
        bytes[0] = (byte) (value >> 24);
        bytes[1] = (byte) (value >> 16);
        bytes[2] = (byte) (value >> 8);
        bytes[3] = (byte) value;
        outs.write(bytes, 0, 4);
    }

    /**
     * read an int from an input stream
     *
     * @param ins
     * @param bytes
     * @return long value
     * @throws java.io.IOException
     */
    public static int readInt(InputStream ins, byte[] bytes) throws IOException {
        if (ins.read(bytes, 0, 4) != 4)
            throw new IOException("Read int: too few bytes");
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    /**
     * replace the suffix of a file
     *
     * @param fileName
     * @param newSuffix
     * @return new file name
     */
    public static String replaceFileSuffix(String fileName, String newSuffix) {
        return replaceFileSuffix(new File(fileName), newSuffix).getPath();
    }

    /**
     * replace the suffix of a file
     *
     * @param file
     * @param newSuffix
     * @return new file
     */
    public static File replaceFileSuffix(File file, String newSuffix) {
        String name = getFileBaseName(file.getName());
        if (!name.endsWith(newSuffix))
            name = name + (newSuffix != null ? newSuffix : "");
        return new File(file.getParent(), name);

    }

    /**
     * returns name without any .suffix removed
     *
     * @param name
     * @return name without .suffix
     */
    public static String getFileBaseName(String name) {
        {
            if (name != null) {
                int pos = name.lastIndexOf(".");
                if (pos > 0)
                    name = name.substring(0, pos);
            }
        }
        return name;
    }
}
