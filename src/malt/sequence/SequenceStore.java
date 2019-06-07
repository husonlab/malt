/*
 *  SequenceStore.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.sequence;


import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import megan.io.OutputWriter;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Sequence store using encoded sequenceCodes
 * Created by huson on 10/1/14.
 */
public class SequenceStore {
    public final static int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private final SequenceEncoder sequenceEncoder;
    private int numberOfSequences;
    private byte[][] headers;
    private long[][] sequenceCodes;

    /**
     * constructor
     *
     * @param sequenceEncoder
     * @param size
     */
    public SequenceStore(final SequenceEncoder sequenceEncoder, final int size) {
        this.sequenceEncoder = sequenceEncoder;
        headers = new byte[size][];
        sequenceCodes = new long[size][];
    }

    /**
     * read sequences from a fastA or fastQ file
     *
     * @param it
     * @param numberToRead
     * @return number of sequences read
     */
    public int readFromFastA(FastAFileIteratorCode it, int numberToRead, ProgressListener progress) throws CanceledException {
        progress.setMaximum(numberToRead);
        progress.setProgress(0);
        numberOfSequences = 0;
        while (it.hasNext()) {
            if (numberOfSequences >= headers.length) {
                grow();
            }
            headers[numberOfSequences] = it.nextHeader();
            sequenceCodes[numberOfSequences] = it.nextSequenceCode();
            numberOfSequences++;
            if (numberOfSequences >= numberToRead)
                break;
            progress.incrementProgress();
        }
        return numberOfSequences;
    }

    /**
     * write to a file in binary format
     *
     * @param fileName
     */
    public void write(final String fileName) throws IOException {
        try (OutputWriter outs = new OutputWriter(new File(fileName)); ProgressPercentage progress = new ProgressPercentage("Writing file: " + fileName, numberOfSequences)) {
            outs.writeInt(numberOfSequences);
            for (int i = 0; i < numberOfSequences; i++) {
                {
                    int length = headers[i].length;
                    outs.writeInt(length);
                    outs.write(headers[i], 0, length);
                }
                {
                    int length = sequenceCodes[i].length;
                    outs.writeInt(length);
                    for (int j = 0; j < length; j++)
                        outs.writeLong(sequenceCodes[i][j]);
                }
                progress.incrementProgress();
            }
            progress.close();
        }
    }

    /**
     * read a file in binary format
     *
     * @param fileName
     * @return number of sequences read
     * @throws IOException
     */
    public int read(final String fileName) throws IOException {
        DataInputStream ins = new DataInputStream(new FileInputStream(fileName));
        numberOfSequences = ins.readInt();
        if (headers.length < numberOfSequences) { // resize
            headers = new byte[numberOfSequences][];
            sequenceCodes = new long[numberOfSequences][];
        }

        ProgressPercentage progress = new ProgressPercentage("Reading file: " + fileName, numberOfSequences);
        for (int i = 0; i < numberOfSequences; i++) {
            {
                int length = ins.readInt();
                for (int j = 0; j < length; j++)
                    headers[i][j] = (byte) ins.read();
            }
            {
                int length = ins.readInt();
                for (int j = 0; j < length; j++)
                    sequenceCodes[i][j] = ins.readLong();
            }
            progress.incrementProgress();
        }
        ins.close();
        progress.close();
        return numberOfSequences;
    }

    /**
     * get the number of sequences
     *
     * @return
     */
    public int getNumberOfSequences() {
        return numberOfSequences;
    }

    /**
     * gets the i-th header
     *
     * @param i
     * @return header
     */
    public byte[] getHeader(int i) {
        return headers[i];
    }

    /**
     * gets the i-th sequence
     *
     * @param i
     * @return sequence
     */
    public byte[] getSequence(int i) {
        return sequenceEncoder.decode(sequenceCodes[i]);
    }

    /**
     * gets the i-th sequence code
     *
     * @param i
     * @return sequence code
     */
    public long[] getSequenceCode(int i) {
        return sequenceCodes[i];
    }

    /**
     * gets the sequence encoder
     *
     * @return
     */
    public SequenceEncoder getSequenceEncoder() {
        return sequenceEncoder;
    }

    /**
     * grow the data arrays
     */
    private void grow() {
        final int newLength = (int) Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, headers.length));
        System.err.print("[Grow: " + headers.length + " -> " + newLength + "]");

        {
            byte[][] tmp = new byte[newLength][];
            System.arraycopy(headers, 0, tmp, 0, headers.length);
            headers = tmp;
        }
        {
            long[][] tmp = new long[newLength][];
            System.arraycopy(sequenceCodes, 0, tmp, 0, sequenceCodes.length);
            sequenceCodes = tmp;
        }
    }

    public static void main(String[] args) throws IOException, CanceledException {
        String fileName = "/Users/huson/data/megan/ecoli/x.fna";

        SequenceStore sequenceStore = new SequenceStore(new SequenceEncoder(DNA5Alphabet.getInstance()), 2000);
        FastAFileIteratorCode fastAFileIteratorCode = new FastAFileIteratorCode(fileName, sequenceStore.getSequenceEncoder());
        ProgressPercentage progress = new ProgressPercentage("Reading file: " + fileName);
        sequenceStore.readFromFastA(fastAFileIteratorCode, 2000, progress);
        progress.close();

        System.err.println("Got:");
        for (int i = 0; i < Math.min(5, sequenceStore.getNumberOfSequences()); i++) {
            System.err.println(Basic.toString(sequenceStore.getHeader(i)));
            System.err.println(Basic.toString(sequenceStore.getSequence(i)));
        }

        String binFile = "/Users/huson/tmp/x.idx";
        sequenceStore.write(binFile);

        sequenceStore.read(binFile);
        System.err.println("Read: " + sequenceStore.numberOfSequences);
        System.err.println("Got:");
        for (int i = 0; i < Math.min(5, sequenceStore.getNumberOfSequences()); i++) {
            System.err.println(Basic.toString(sequenceStore.getHeader(i)));
            System.err.println(Basic.toString(sequenceStore.getSequence(i)));
        }
    }
}
