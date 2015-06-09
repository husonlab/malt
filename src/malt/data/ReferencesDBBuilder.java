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

package malt.data;

import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import malt.io.FastAFileIteratorBytes;

import java.io.*;
import java.util.List;

/**
 * builds the reference sequences database
 * Daniel Huson, 8.2014
 */
public class ReferencesDBBuilder implements ISequenceAccessor {
    public static final String TAXON_TAG = "tax";
    public static final String KEGG_TAG = "kegg";
    public static final String SEED_TAG = "seed";
    public static final String COG_TAG = "cog";

    private byte[][] headers;
    private byte[][] sequences;
    private int numberOfSequences;
    private long numberOfLetters;

    /**
     * constructor
     */
    public ReferencesDBBuilder() {
        headers = new byte[10000][];
        sequences = new byte[10000][];
        numberOfSequences = 0;
        numberOfLetters = 0;
    }

    /**
     * resize
     *
     * @param newSize
     */
    public void grow(int newSize) {
        if (newSize > headers.length) {
            byte[][] newHeaders = new byte[newSize][];
            int top = Math.min(newSize, headers.length);
            System.arraycopy(headers, 0, newHeaders, 0, top);
            headers = newHeaders;
            byte[][] newSequences = new byte[newSize][];
            top = Math.min(newSize, sequences.length);
            System.arraycopy(sequences, 0, newSequences, 0, top);
            sequences = newSequences;
        }
    }

    /**
     * add a header and sequence to the list of sequences
     *
     * @param header
     * @param sequence
     */
    public void add(byte[] header, byte[] sequence) {
        if (numberOfSequences == sequences.length) {
            headers = grow(headers);
            sequences = grow(sequences);
        }
        headers[numberOfSequences] = header;
        sequences[numberOfSequences] = sequence;
        numberOfSequences++;
        numberOfLetters += sequence.length;
    }

    /**
     * grow an array
     *
     * @param array
     * @return bigger array
     */
    private byte[][] grow(byte[][] array) {
        byte[][] result = new byte[Math.min(Integer.MAX_VALUE, 2 * Math.max(1, array.length))][];
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    /**
     * Get header string. Index starts at 0
     *
     * @param index
     * @return header
     */
    public byte[] getHeader(int index) {
        return headers[index];
    }

    /**
     * Get sequence. Index starts at 0
     *
     * @param index
     * @return sequence
     */
    public byte[] getSequence(int index) {
        return sequences[index];
    }

    /**
     * load a collection of fastA files
     *
     * @param fileNames
     * @throws IOException
     * @throws CanceledException
     */
    public void loadFastAFiles(final List<String> fileNames, final IAlphabet alphabet) throws IOException, CanceledException {
        long totalSize = 0;
        for (String fileName : fileNames) {
            totalSize += (new File(fileName)).length();
        }
        int guessNumberOfSequences = (int) Math.min(Integer.MAX_VALUE, totalSize / 1000L);
        grow(guessNumberOfSequences);

        for (String fileName : fileNames) {
            loadFastAFile(fileName, new ProgressPercentage("Reading file: " + fileName), alphabet);
        }
    }

    /**
     * load data from a fastA file
     *
     * @param fileName
     * @throws FileNotFoundException
     */
    public void loadFastAFile(final String fileName, final ProgressListener progressListener, final IAlphabet alphabet) throws IOException, CanceledException {
        FastAFileIteratorBytes it = new FastAFileIteratorBytes(fileName, alphabet);
        progressListener.setMaximum(it.getMaximumProgress());
        progressListener.setProgress(0);

        try {
            while (it.hasNext()) {
                byte[] header = it.next();
                if (it.hasNext()) {
                    byte[] sequence = it.next();
                    add(header, sequence);
                    progressListener.setProgress(it.getProgress());
                }
            }
        } finally {
            if (progressListener instanceof ProgressPercentage)
                progressListener.close();
            it.close();
        }
    }

    /**
     * save sequences in fastA format
     *
     * @param fileName
     * @param progressListener
     * @throws IOException
     * @throws CanceledException
     */
    public void saveFastAFile(String fileName, ProgressListener progressListener) throws IOException, CanceledException {
        progressListener.setMaximum(numberOfSequences);
        progressListener.setProgress(0);

        try (BufferedWriter w = new BufferedWriter(new FileWriter(fileName), 8192)) {
            for (int i = 0; i < numberOfSequences; i++) {
                w.write(headers[i] + "\n");
                w.write(sequences[i] + "\n");
                progressListener.incrementProgress();
            }
        } finally {
            if (progressListener instanceof ProgressPercentage)
                progressListener.close();

        }
    }

    /**
     * Save the reference data as an index file and a datafile
     * @param refIndexFile
     * @param refDBFile
     * @throws IOException
     * @throws CanceledException
     */
    public void save(File refIndexFile, File refDBFile, File refInfFile, boolean saveFirstWordOnly) throws IOException, CanceledException {
        final ProgressPercentage progress = new ProgressPercentage("Writing file: " + refIndexFile, numberOfLetters);
        System.err.println("Writing file: " + refDBFile);

        try (final DataOutputStream refDBOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(refDBFile), 8192));
             DataOutputStream refIndexOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(refIndexFile), 8192))) {
            long dbFilePos = 0;

            for (int i = 0; i < numberOfSequences; i++) {
                refIndexOutputStream.writeLong(dbFilePos);

                final byte[] sequence = sequences[i];
                refDBOutputStream.writeInt(sequence.length);
                refDBOutputStream.write(sequence, 0, sequence.length);
                dbFilePos += 4 + sequence.length;

                final byte[] header = (saveFirstWordOnly ? getFirstWord(headers[i]) : headers[i]);
                refDBOutputStream.writeInt(header.length);
                refDBOutputStream.write(header, 0, header.length);
                dbFilePos += 4 + header.length;

                progress.incrementProgress();
            }
        } finally {
            progress.close();
        }
        final Writer writer = new FileWriter(refInfFile);
        writer.write("sequences\t" + numberOfSequences + "\n");
        writer.write("letters\t" + numberOfLetters + "\n");
        writer.close();
    }

    /**
     * get string consisting of first word
     *
     * @param str
     * @return first word
     */
    static public byte[] getFirstWord(byte[] str) {
        for (int i = 0; i < str.length; i++) {
            if (Character.isWhitespace(str[i])) {
                byte[] result = new byte[i];
                System.arraycopy(str, 0, result, 0, i);
                return result;
            }
        }
        return str;
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
     * extend the header by the given tag. We use this to write the taxon id into a reference sequence
     *
     * @param index
     * @param tag
     * @param id
     */
    public void extendHeader(int index, String tag, Integer id) {
        byte[] header = headers[index];
        int pos = 0;
        while (Character.isWhitespace(header[pos]) && pos < header.length) // skip leading white space
            pos++;
        while (!Character.isWhitespace(header[pos]) && pos < header.length) // go to next white space or end
            pos++;
        byte[] add;
        if (header[pos - 1] == '|')
            add = String.format("%s|%d|", tag, id).getBytes();
        else
            add = String.format("|%s|%d|", tag, id).getBytes();

        byte[] newHeader = new byte[header.length + add.length];
        System.arraycopy(header, 0, newHeader, 0, pos);
        System.arraycopy(add, 0, newHeader, pos, add.length);
        if (pos < header.length) {
            System.arraycopy(header, pos, newHeader, add.length + pos, header.length - pos);
        }
        headers[index] = newHeader;
        //System.err.println("Header="+Basic.toString(headers[index]));
    }
}
