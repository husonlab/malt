/**
 * Copyright 2015, Daniel Huson
 * <p/>
 * (Some files contain contributions from other authors, who are then mentioned separately)
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package malt.sequence;


import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;

import java.io.*;

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
        DataOutputStream outs = new DataOutputStream(new FileOutputStream(fileName));
        outs.writeInt(numberOfSequences);
        ProgressPercentage progress = new ProgressPercentage("Writing file: " + fileName, numberOfSequences);
        for (int i = 0; i < numberOfSequences; i++) {
            {
                int length = headers[i].length;
                outs.writeInt(length);
                for (int j = 0; j < length; j++)
                    outs.writeByte(headers[i][j]);
            }
            {
                int length = sequenceCodes[i].length;
                outs.writeInt(length);
                for (int j = 0; j < length; j++)
                    outs.writeLong(sequenceCodes[i][j]);
            }
            progress.incrementProgress();
        }
        outs.close();
        progress.close();
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
