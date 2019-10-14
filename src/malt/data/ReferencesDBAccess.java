/*
 *  ReferencesDBAccess.java Copyright (C) 2019. Daniel H. Huson GPL
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
import jloda.util.FileLineIterator;
import malt.MaltOptions;
import megan.io.*;
import megan.io.experimental.ByteFileGetterPagedMemory;
import megan.io.experimental.LongFileGetterPagedMemory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * accesses the references DB
 * Daniel Huson, 3.2015
 */
public class ReferencesDBAccess implements Closeable {
    private byte[][] headers;
    private byte[][] sequences;

    private int numberOfSequences;
    private long numberOfLetters;

    private static final int SYNC_BITMASK = 1023;//  length of vector must be SYNC_BITMASK+1
    private final Object[] syncObjects;

    private final ILongGetter refIndex;
    private final IByteGetter refDB;

    /**
     * construct from an input file
     *
     * @param refIndexFile
     * @throws java.io.IOException
     */
    public ReferencesDBAccess(MaltOptions.MemoryMode memoryMode, File refIndexFile, File refDBFile, File refInfFile) throws IOException, CanceledException {
        syncObjects = new Object[SYNC_BITMASK + 1];
        for (int i = 0; i < syncObjects.length; i++) {
            syncObjects[i] = new Object();
        }

        switch (memoryMode) {
            default:
            case load:
                refIndex = new LongFileGetterInMemory(refIndexFile);
                refDB = new ByteFileGetterInMemory(refDBFile);
                break;
            case page:
                refIndex = new LongFileGetterPagedMemory(refIndexFile);
                refDB = new ByteFileGetterPagedMemory(refDBFile);
                break;
            case map:
                refIndex = new LongFileGetterMappedMemory(refIndexFile);
                refDB = new ByteFileGetterMappedMemory(refDBFile);
                break;
        }

        try (FileLineIterator it = new FileLineIterator(refInfFile)) {
            while (it.hasNext()) {
                String aLine = it.next();
                if (aLine.startsWith("sequences")) {
                    numberOfSequences = Integer.parseInt(Basic.getTokenFromTabSeparatedLine(aLine, 1));
                } else if (aLine.startsWith("letters")) {
                    numberOfLetters = Long.parseLong(Basic.getTokenFromTabSeparatedLine(aLine, 1));
                }
            }
        }
        System.err.println(String.format("Number of sequences:%,14d", numberOfSequences));
        System.err.println(String.format("Number of letters:%,16d", numberOfLetters));

        if (numberOfSequences != refIndex.limit())
            throw new IOException("Expected " + numberOfSequences + "sequences , index contains: " + refIndex.limit());

        headers = new byte[numberOfSequences][];
        sequences = new byte[numberOfSequences][];
    }

    /**
     * Get header string. Index starts at 0
     *
     * @param index
     * @return header
     */
    public byte[] getHeader(int index) throws IOException {
        byte[] array = headers[index];
        if (array == null) {
            synchronized (syncObjects[index & SYNC_BITMASK]) {
                if (headers[index] == null) {
                    long dbIndex = refIndex.get(index);
                    dbIndex += 4 + refDB.getInt(dbIndex); // increment dbIndex by 4 plus length of sequence (to skip over sequence)
                    int headerLength = refDB.getInt(dbIndex);
                    dbIndex += 4;
                    array = new byte[headerLength];
                    refDB.get(dbIndex, array, 0, headerLength);
                    headers[index] = array;
                } else
                    array = headers[index];
            }
        }
        return array;
    }

    /**
     * Get sequence. Index starts at 0
     *
     * @param index
     * @return sequence
     */
    public byte[] getSequence(int index) throws IOException {
        byte[] array = sequences[index];
        if (array == null) {
            synchronized (syncObjects[index & SYNC_BITMASK]) {
                if (sequences[index] == null) {
                    long dbIndex = refIndex.get(index);
                    int sequenceLength = refDB.getInt(dbIndex);
                    dbIndex += 4;
                    array = new byte[sequenceLength];
                    refDB.get(dbIndex, array, 0, sequenceLength);
                    sequences[index] = array;
                } else
                    array = sequences[index];
            }
        }
        return array;
    }

    /**
     * Get sequence length
     *
     * @param index
     * @return sequence length
     */
    public int getSequenceLength(int index) throws IOException {
        if (sequences[index] != null)
            return sequences[index].length;
        else
            return refDB.getInt(refIndex.get(index));
    }

    /**
     * number of sequences
     *
     * @return number of sequences
     */
    public int getNumberOfSequences() {
        return numberOfSequences;
    }

    /**
     * total number of letters
     *
     * @return number of letters
     */
    public long getNumberOfLetters() {
        return numberOfLetters;
    }

    /**
     * close
     */
    public void close() {
        refIndex.close();
        refDB.close();
    }
}
