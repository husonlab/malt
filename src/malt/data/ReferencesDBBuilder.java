/*
 *  ReferencesDBBuilder.java Copyright (C) 2019. Daniel H. Huson GPL
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
import malt.io.FastAFileIteratorBytes;
import megan.io.OutputWriter;

import java.io.*;
import java.util.Iterator;
import java.util.List;

/**
 * builds the reference sequences database
 * Daniel Huson, 8.2014
 */
public class ReferencesDBBuilder implements ISequenceAccessor {
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
     * gets an iterable over all ref names as strings
     *
     * @return iterable
     */
    public Iterable<String> refNames() {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    private int i = 0;

                    @Override
                    public boolean hasNext() {
                        return i < numberOfSequences;
                    }

                    @Override
                    public String next() {
                        return Basic.getAccessionWord(headers[i++]);
                    }

                    @Override
                    public void remove() {

                    }
                };
            }
        };
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
    public void loadFastAFiles(final List<String> fileNames, final IAlphabet alphabet) throws IOException {
        long totalSize = 0;
        for (String fileName : fileNames) {
            totalSize += (new File(fileName)).length();
        }
        int guessNumberOfSequences = (int) Math.min(Integer.MAX_VALUE, totalSize / 1000L);
        grow(guessNumberOfSequences);

        try (ProgressPercentage progress = new ProgressPercentage("Loading FastA files:", fileNames.size())) {
            for (String fileName : fileNames) {
                loadFastAFile(fileName, alphabet);
                progress.incrementProgress();
            }
        }
    }

    /**
     * load data from a fastA file
     *
     * @param fileName
     * @throws FileNotFoundException
     */
    private void loadFastAFile(final String fileName, final IAlphabet alphabet) throws IOException {
        try (FastAFileIteratorBytes it = new FastAFileIteratorBytes(fileName, alphabet)) {
            while (it.hasNext()) {
                byte[] header = it.next();
                if (it.hasNext()) {
                    byte[] sequence = it.next();
                    add(header, sequence);
                }
            }
        }
    }

    /**
     * save sequences in fastA format
     *
     * @param fileName
     * @throws IOException
     * @throws CanceledException
     */
    public void saveFastAFile(String fileName) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(fileName), 8192)) {
            for (int i = 0; i < numberOfSequences; i++) {
                w.write(Basic.toString(headers[i]) + "\n");
                w.write(Basic.toString(sequences[i]) + "\n");
            }
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
        System.err.println("Writing file: " + refDBFile);

        try (ProgressPercentage progress = new ProgressPercentage("Writing file: " + refIndexFile, numberOfLetters);
             final OutputWriter refDBOutputStream = new OutputWriter(refDBFile); OutputWriter refIndexOutputStream = new OutputWriter(refIndexFile)) {
            long dbFilePos = 0;

            for (int i = 0; i < numberOfSequences; i++) {
                refIndexOutputStream.writeLong(dbFilePos);

                final byte[] sequence = sequences[i];
                refDBOutputStream.writeInt(sequence.length);
                refDBOutputStream.write(sequence);
                dbFilePos += 4 + sequence.length;

                final byte[] header = (saveFirstWordOnly ? getFirstWord(headers[i]) : headers[i]);
                refDBOutputStream.writeInt(header.length);
                refDBOutputStream.write(header);
                dbFilePos += 4 + header.length;

                progress.incrementProgress();
            }
        }

        try (BufferedWriter w = new BufferedWriter(new FileWriter(refInfFile))) {
            w.write("sequences\t" + numberOfSequences + "\n");
            w.write("letters\t" + numberOfLetters + "\n");
        }
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
        while (Character.isWhitespace(header[pos]) && pos < header.length - 1) // skip leading white space
            pos++;
        while (!Character.isWhitespace(header[pos]) && pos < header.length - 1) // go to next white space or end
            pos++;
        byte[] add;
        if (header[pos - 1] == '|')
            add = String.format("%s%d", tag, id).getBytes();
        else
            add = String.format("|%s%d", tag, id).getBytes();

        byte[] newHeader = new byte[header.length + add.length];
        System.arraycopy(header, 0, newHeader, 0, pos);
        System.arraycopy(add, 0, newHeader, pos, add.length);
        if (pos < header.length - 1) {
            System.arraycopy(header, pos, newHeader, add.length + pos, header.length - pos);
        }
        headers[index] = newHeader;
        //System.err.println("Header="+Basic.toString(headers[index]));
    }
}
