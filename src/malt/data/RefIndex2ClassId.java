/*
 *  RefIndex2ClassId.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.data;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;

import java.io.*;

/**
 * maintains a mapping from reference indices to class ids (e.g. taxon ids or KEGG KOs)
 * todo: mappings now start at 0, this breaks old Malt
 * Daniel Huson, 8.2014
 */
public class RefIndex2ClassId {
    private static final byte[] MAGIC_NUMBER = "MAClassV1.1.".getBytes();

    private int maxRefId;
    private int[] refIndex2ClassId;

    public RefIndex2ClassId(int numberOfReferences) {
        refIndex2ClassId = new int[numberOfReferences];
        maxRefId = numberOfReferences;
    }

    /**
     * put, indices start at 0
     *
     * @param refIndex
     * @param classId
     */
    public void put(int refIndex, int classId) {
        refIndex2ClassId[refIndex] = classId;
    }

    /**
     * get, indices start at 0
     *
     * @param refIndex
     * @return class id for given reference id
     */
    public int get(int refIndex) {
        return refIndex2ClassId[refIndex];
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws java.io.IOException
     */
    public void save(File file) throws IOException, CanceledException {
        save(file, MAGIC_NUMBER);
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws java.io.IOException
     */
    public void save(File file, byte[] magicNumber) throws IOException, CanceledException {
        try (BufferedOutputStream outs = new BufferedOutputStream(new FileOutputStream(file)); ProgressPercentage progressListener = new ProgressPercentage("Writing file: " + file, maxRefId)) {
            outs.write(magicNumber);

            // number of entries
            writeInt(outs, maxRefId);

            // write headers and sequences:
            for (int i = 0; i < maxRefId; i++) {
                writeInt(outs, refIndex2ClassId[i]);
                // System.err.println("write: "+i+" "+refIndex2ClassId[i]);
                progressListener.incrementProgress();
            }
        }
    }

    /**
     * constructor from a file
     *
     * @param file
     */
    public RefIndex2ClassId(File file) throws IOException, CanceledException {
        this(file, MAGIC_NUMBER);
    }

    /**
     * constructor from a file
     *
     * @param file
     */
    public RefIndex2ClassId(File file, byte[] magicNumber) throws IOException, CanceledException {
        ProgressPercentage progressListener = null;
        try (BufferedInputStream ins = new BufferedInputStream(new FileInputStream(file))) {
            // check magic number:
            Basic.readAndVerifyMagicNumber(ins, magicNumber);
            maxRefId = readInt(ins);
            progressListener = new ProgressPercentage("Reading file: " + file, maxRefId);
            refIndex2ClassId = new int[maxRefId + 1];
            // write headers and sequences:
            for (int i = 0; i < maxRefId; i++) {
                refIndex2ClassId[i] = readInt(ins);
                // System.err.println("read: "+i+" "+refIndex2ClassId[i]);
                progressListener.incrementProgress();
            }
        } finally {
            if (progressListener != null)
                progressListener.close();
        }
    }

    /**
     * read an int from an input stream
     *
     * @param ins
     * @return long value
     * @throws java.io.IOException
     */
    public static int readInt(InputStream ins) throws IOException {
        return ((ins.read() & 0xFF) << 24)
                + ((ins.read() & 0xFF) << 16)
                + ((ins.read() & 0xFF) << 8)
                + ((ins.read() & 0xFF));
    }

    /**
     * writes an int value
     *
     * @param outs
     * @param value
     * @throws java.io.IOException
     */
    public static void writeInt(OutputStream outs, int value) throws IOException {
        outs.write((byte) (value >> 24));
        outs.write((byte) (value >> 16));
        outs.write((byte) (value >> 8));
        outs.write((byte) value);
    }
}
