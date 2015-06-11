/**
 * Utilities.java 
 * Copyright (C) 2015 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package malt.util;

import jloda.map.IIntPutter;
import jloda.util.Basic;
import jloda.util.UsageException;
import malt.data.ReadMatch;
import malt.data.Row;
import malt.data.SequenceType;
import megan.classification.IdMapper;
import megan.classification.commandtemplates.LoadMappingFileCommand;
import megan.parsers.blast.BlastMode;

import java.io.*;
import java.util.Random;

/**
 * some utilities
 * Daniel Huson, 8.2014
 */
public class Utilities {
    /**
     * randomize array of longs using (Durstenfeld 1964)
     *
     * @param array
     * @param offset start of numbers to be randomized
     * @param length number of numbers to be randomized
     * @param random
     */
    public static void randomize(long[] array, int offset, int length, Random random) {
        for (int i = offset + length - 1; i >= offset + 1; i--) {
            int j = random.nextInt(i - offset) + offset;
            long tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    /**
     * randomize array of integers using (Durstenfeld 1964) in consecutive pairs
     *
     * @param array
     * @param offset start of numbers to be randomized
     * @param length number of numbers to be randomized. Must be even for this to make sense
     * @param random
     */
    public static void randomizePairs(int[] array, int offset, int length, Random random) {
        int end = offset + length / 2;
        for (int i = end - 1; i >= offset + 1; i--) {
            int j = random.nextInt(i - offset) + offset;
            int i2 = 2 * i - offset;
            int j2 = 2 * j - offset;
            int tmp = array[i2];
            array[i2] = array[j2];
            array[j2] = tmp;
            i2++;
            j2++;
            tmp = array[i2];
            array[i2] = array[j2];
            array[j2] = tmp;
        }
    }

    /**
     * randomize array of integers using (Durstenfeld 1964) in consecutive pairs
     *
     * @param array
     * @param offset start of numbers to be randomized
     * @param length number of numbers to be randomized. Must be even for this to make sense
     * @param random
     */
    public static void randomizePairs(IIntPutter array, long offset, int length, Random random) {
        long end = offset + length / 2;
        for (long i = end - 1; i >= offset + 1; i--) {
            long j = random.nextInt((int) (i - offset)) + offset;
            long i2 = 2 * i - offset;
            long j2 = 2 * j - offset;
            int tmp = array.get(i2);
            array.put(i2, array.get(j2));
            array.put(j2, tmp);
            i2++;
            j2++;
            tmp = array.get(i2);
            array.put(i2, array.get(j2));
            array.put(j2, tmp);
        }
    }


    /**
     * read a long from an input stream
     *
     * @param ins
     * @param bytes
     * @return long value
     * @throws java.io.IOException
     */
    public static long readLong(InputStream ins, byte[] bytes) throws IOException {
        if (ins.read(bytes, 0, 8) != 8)
            throw new IOException("Read long: too few bytes");
        return ((long) (bytes[0]) << 56)
                | ((long) (bytes[1] & 0xFF) << 48)
                | ((long) (bytes[2] & 0xFF) << 40)
                | ((long) (bytes[3] & 0xFF) << 32)
                | ((long) (bytes[4] & 0xFF) << 24)
                | ((long) (bytes[5] & 0xFF) << 16)
                | ((long) (bytes[6] & 0xFF) << 8)
                | (long) (bytes[7] & 0xFF);
    }

    /**
     * writes a long value
     *
     * @param outs
     * @param value
     * @param bytes
     * @throws java.io.IOException
     */
    public static void writeLong(OutputStream outs, long value, byte[] bytes) throws IOException {
        bytes[0] = (byte) ((value >> 56));
        bytes[1] = (byte) (value >> 48);
        bytes[2] = (byte) (value >> 40);
        bytes[3] = (byte) (value >> 32);
        bytes[4] = (byte) (value >> 24);
        bytes[5] = (byte) (value >> 16);
        bytes[6] = (byte) (value >> 8);
        bytes[7] = (byte) value;
        outs.write(bytes, 0, 8);
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
     * writes a 3-byte int value
     *
     * @param outs
     * @param value
     * @param bytes
     * @throws java.io.IOException
     */
    public static void writeInt3(OutputStream outs, int value, byte[] bytes) throws IOException {
        bytes[0] = (byte) (value >> 16);
        bytes[1] = (byte) (value >> 8);
        bytes[2] = (byte) value;
        outs.write(bytes, 0, 3);
    }

    /**
     * read a 3-byte int from an input stream
     *
     * @param ins
     * @param bytes
     * @return long value
     * @throws java.io.IOException
     */
    public static int readInt3(InputStream ins, byte[] bytes) throws IOException {
        if (ins.read(bytes, 0, 3) != 3)
            throw new IOException("Read int3: too few bytes");
        return ((bytes[0] & 0xFF) << 16) | ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
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
     * grow row, always return an odd-length array with length <= maxLength
     *
     * @param row
     * @param maxLengthOdd
     * @return new row four times as big
     */
    public static int[] growOdd(int[] row, int maxLengthOdd) {
        int[] result = new int[(int) Math.max(13L, Math.min(maxLengthOdd, 4L * row.length + 1))];
        System.arraycopy(row, 0, result, 0, row.length);
        return result;
    }

    /**
     * resize array
     *
     * @param array
     * @return new array
     */
    public static int[] resize(int[] array, int newSize) {
        int[] result = new int[newSize];
        System.arraycopy(array, 0, result, 0, Math.min(newSize, array.length));
        return result;
    }


    /**
     * resize array
     *
     * @param array
     * @return new array
     */
    public static Row[] resizeAndConstructEntries(Row[] array, int newSize) {
        Row[] result = new Row[newSize];
        for (int i = array.length; i < newSize; i++)
            result[i] = new Row();
        System.arraycopy(array, 0, result, 0, Math.min(newSize, array.length));
        return result;
    }

    /**
     * resize array
     *
     * @param array
     * @return new array
     */
    public static ReadMatch[] resize(ReadMatch[] array, int newSize) {
        ReadMatch[] result = new ReadMatch[newSize];
        System.arraycopy(array, 0, result, 0, Math.min(newSize, array.length));
        return result;
    }

    /**
     * get first word of header
     *
     * @param header
     * @return first word
     */
    public static byte[] getFirstWord(byte[] header) {
        int length = 0;
        while (length < header.length) {
            if (header[length] == 0 || Character.isWhitespace(header[length])) {
                byte[] result = new byte[length];
                System.arraycopy(header, 0, result, 0, length);
                return result;
            }
            length++;
        }
        return header;
    }

    /**
     * get first word of header
     *
     * @param header
     * @return first word
     */
    public static byte[] getFirstWordSkipLeadingGreaterSign(byte[] header) {
        int start = 0;
        while (start < header.length) {
            byte ch = header[start];
            if (ch != '>' && !Character.isWhitespace(ch))
                break;
            start++;
        }
        int finish = start;
        while (finish < header.length) {
            byte ch = header[finish];
            if (ch == 0 || Character.isWhitespace(ch))
                break;
            finish++;
        }
        byte[] result = new byte[finish - start];
        System.arraycopy(header, start, result, 0, finish - start);
        return result;
    }

    /**
     * get first word of header and make sure it starts with a greater sign
     *
     * @param header
     * @return first word
     */
    public static byte[] getFirstWordEnsureLeadingGreaterSign(byte[] header) {
        int length = 0;
        boolean hasLeadingGreaterSign = (header.length > 0 && header[0] == '>');
        while (length < header.length) {
            if (header[length] == 0 || Character.isWhitespace(header[length])) {
                if (hasLeadingGreaterSign) {
                    byte[] result = new byte[length];
                    System.arraycopy(header, 0, result, 0, length);
                    return result;
                } else {
                    byte[] result = new byte[length + 1];
                    result[0] = '>';
                    System.arraycopy(header, 0, result, 1, length);
                    return result;
                }
            }
            length++;
        }
        return header;

    }


    /**
     * copy a 0-terminated byte array
     *
     * @param bytes
     * @return copy (up to first 0)
     */
    public static byte[] copy0Terminated(byte[] bytes) {
        int length = 0;
        while (length < bytes.length) {
            if (bytes[length] == 0)
                break;
            length++;
        }
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, 0, length);
        return result;
    }

    /**
     * get first word of header and write it to result
     *
     * @param header
     * @return first word
     */
    public static int getFirstWord(byte[] header, byte[] result) {
        int length = 0;

        while (length < header.length) {
            if (header[length] == 0 || Character.isWhitespace(header[length]) || header[length] == 0)
                break;
            length++;
        }
        System.arraycopy(header, 0, result, 0, length);
        return length;
    }

    /**
     * get first word of header and write it to result
     *
     * @param header
     * @return first word
     */
    public static int getFirstWordSkipLeadingGreaterSign(byte[] header, byte[] result) {
        int start = (header.length > 0 && header[0] == '>' ? 1 : 0);

        while (start < header.length && Character.isWhitespace(header[start])) {
            start++;
        }
        int end = start;
        while (end < header.length && !Character.isWhitespace(header[end]) && header[end] != 0) {
            end++;
        }
        int length = end - start;
        if (length > 0)
            System.arraycopy(header, start, result, 0, length);
        return length;
    }

    public static void checkFileExists(File file) throws IOException {
        checkFileExists(file.getPath(), false);
    }

    public static void checkFileExists(String fileName, boolean allowToAddGZSuffix) throws IOException {
        if ((new File(fileName)).exists())
            return; // ok
        if (!allowToAddGZSuffix) {
            throw new IOException("File not found: " + fileName);
        } else if (!(new File(fileName + ".gz")).exists()) {
            throw new IOException("File not found: " + fileName + " nor " + fileName + ".gz");
        }
    }

    /**
     * remove all existing index files
     *
     * @param indexDirectory
     */
    public static void cleanIndexDirectory(File indexDirectory) throws IOException {
        if (!indexDirectory.isDirectory())
            throw new IOException("Not a directory: " + indexDirectory);

        File[] files = indexDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.endsWith(".idx") || s.contains(".idx.");
            }
        });
        if (files != null) {
            System.err.println("Deleting index files: " + files.length);
            for (File file : files)
                if (!file.delete())
                    throw new IOException("Failed to delete file: " + file);
        }
    }

    /**
     * gets the query sequence type from the alignment program mode
     *
     * @param mode
     * @return query type
     * @throws UsageException
     */
    public static SequenceType getQuerySequenceTypeFromMode(BlastMode mode) throws IOException {
        switch (mode) {
            case BlastN:
            case BlastX:
                return SequenceType.DNA;
            case BlastP:
                return SequenceType.Protein;
            default:
                throw new IOException("Unsupported mode: " + mode);
        }

    }

    /**
     * gets the reference sequence type from the alignment program mode
     *
     * @param mode
     * @return query type
     * @throws UsageException
     */
    public static SequenceType getReferenceSequenceTypeFromMode(BlastMode mode) throws IOException {
        switch (mode) {
            case BlastN:
                return SequenceType.DNA;
            case BlastX:
            case BlastP:
                return SequenceType.Protein;
            default:
                throw new IOException("Unsupported mode: " + mode);
        }

    }

    /**
     * determines how many different frames are possible for a given query
     *
     * @param mode
     * @param dnaDoForward
     * @param dnaDoReverse
     * @return number of frames
     * @throws IOException
     */
    public static int getMaxFramesPerQuery(BlastMode mode, boolean dnaDoForward, boolean dnaDoReverse) throws IOException {
        switch (mode) {
            case BlastN:
                return (dnaDoForward ? 1 : 0) + (dnaDoReverse ? 1 : 0);
            case BlastX:
                return 3 * ((dnaDoForward ? 1 : 0) + (dnaDoReverse ? 1 : 0));
            case BlastP:
                return 1;
            default:
                throw new IOException("Unsupported mode: " + mode);
        }
    }

    /**
     * count the number of gaps ('-') in a sequence
     *
     * @param sequence
     * @return number of gaps
     */
    public static int countGaps(byte[] sequence, int offset, int length) {
        int count = 0;
        for (int i = 0; i < length; i++) {
            int a = sequence[offset + i];
            if (a == '-')
                count++;
        }
        return count;
    }

    /**
     * is the given sequence a homopolymer sequence?
     *
     * @param seq
     * @return True if all letters are the same
     */
    public static boolean isHomoPolymer(byte[] seq) {
        byte a = seq[0];

        for (int i = 1; i < seq.length; i++) {
            if (a != seq[i])
                return false;
        }
        return true;
    }


    /**
     * does this contain only at most two different letters
     *
     * @param seq
     * @return True at most two different letters occur
     */
    public static boolean hasAtMostTwoLetters(byte[] seq) {
        byte a = seq[0];
        byte b = 0;

        int pos = 1;
        while (pos < seq.length) {
            if (seq[pos] != a) {
                b = seq[pos];
                break;
            }
            pos++;
        }
        while (pos < seq.length) {
            if (seq[pos] != a && seq[pos] != b)
                return false;
            pos++;
        }
        return true;
    }

    public static int getNextPowerOf2(int value) {
        long i = 1;
        for (; i < Integer.MAX_VALUE; i <<= 1) {
            if (i > value)
                return (int) i;
        }
        return Integer.MAX_VALUE;
    }

    public static int getNextPowerOf2Mask(int value) {
        if (value >= Integer.MAX_VALUE >>> 1)
            return -1;
        else
            return getNextPowerOf2(value) - 1;
    }

    /**
     * gets a file for a given directory with a given name, if it exists. If gzippedOk, also tries adding .gz or replacing the suffix by .gz
     *
     * @param directory
     * @param name
     * @param gzippedOk
     * @return file or null
     */
    public static File getFile(String directory, String name, boolean gzippedOk) {
        File file = new File(directory, name);
        if (file.exists())
            return file;
        if (gzippedOk) {
            file = new File(directory, name + ".gz");
            if (file.exists())
                return file;
            file = new File(directory, Basic.replaceFileSuffix(name, ".gz"));
            if (file.exists())
                return file;
        }
        return null;
    }

    /**
     * is one of the three names non null and of length>0?
     *
     * @param fileA
     * @param fileB
     * @param fileC
     * @return true, if a file is specified
     */
    public static boolean hasAMapping(String fileA, String fileB, String fileC) {
        return fileA != null && fileA.length() > 0 || fileB != null && fileB.length() > 0 || fileC != null && fileC.length() > 0;
    }

    /**
     * load a mapping file
     *
     * @param fileName
     * @param mapType
     * @param cName
     * @throws Exception
     */
    public static void loadMapping(String fileName, IdMapper.MapType mapType, String cName) throws Exception {
        if (fileName.length() > 0)
            (new LoadMappingFileCommand()).apply("load mapFile='" + fileName + "' mapType=" + mapType.toString() + " cName=" + cName + ";");
    }
}
