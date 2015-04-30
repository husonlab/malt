package malt.data;

import jloda.map.ByteFileGetter;
import jloda.map.LongFileGetter;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.FileInputIterator;

import java.io.File;
import java.io.IOException;

/**
 * accesses the references DB
 * Daniel Huson, 3.2015
 */
public class ReferencesDBAccess {
    private byte[][] headers;
    private byte[][] sequences;

    private int numberOfSequences;
    private long numberOfLetters;

    private static final int SYNC_BITMASK = 1023;//  length of vector must be SYNC_BITMASK+1
    private final Object[] syncObjects;

    private final LongFileGetter refIndex;
    private final ByteFileGetter refDB;


    /**
     * construct from an input file
     *
     * @param refIndexFile
     * @throws java.io.IOException
     */
    public ReferencesDBAccess(File refIndexFile, File refDBFile, File refInfFile) throws IOException, CanceledException {
        syncObjects = new Object[SYNC_BITMASK + 1];
        for (int i = 0; i < syncObjects.length; i++) {
            syncObjects[i] = new Object();
        }

        System.err.println("Opening file: " + refIndexFile);
        System.err.println("Opening file: " + refDBFile);
        refIndex = new LongFileGetter(refIndexFile);
        refDB = new ByteFileGetter(refDBFile);

        FileInputIterator it = new FileInputIterator(refInfFile);
        while (it.hasNext()) {
            String aLine = it.next();
            if (aLine.startsWith("sequences")) {
                numberOfSequences = Integer.parseInt(Basic.getTokenFromTabSeparatedLine(aLine, 1));
            } else if (aLine.startsWith("letters")) {
                numberOfLetters = Long.parseLong(Basic.getTokenFromTabSeparatedLine(aLine, 1));
            }
        }
        it.close();
        System.err.println(String.format("Number of sequences:%,13d", numberOfSequences));
        System.err.println(String.format("Number of letters:%,15d", numberOfLetters));

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
    public byte[] getHeader(int index) {
        if (headers[index] == null) {
            synchronized (syncObjects[index & SYNC_BITMASK]) {
                if (headers[index] == null) {
                    long dbIndex = refIndex.get(index);
                    dbIndex += 4 + refDB.getInt(dbIndex); // increment dbIndex by 4 plus length of sequence (to skip over sequence)
                    int headerLength = refDB.getInt(dbIndex);
                    dbIndex += 4;
                    headers[index] = new byte[headerLength];
                    refDB.get(dbIndex, headers[index], 0, headerLength);
                }
            }
        }
        return headers[index];
    }

    /**
     * Get sequence. Index starts at 0
     *
     * @param index
     * @return sequence
     */
    public byte[] getSequence(int index) {
        if (sequences[index] == null) {
            synchronized (syncObjects[index & SYNC_BITMASK]) {
                if (sequences[index] == null) {
                    long dbIndex = refIndex.get(index);
                    int sequenceLength = refDB.getInt(dbIndex);
                    dbIndex += 4;
                    sequences[index] = new byte[sequenceLength];
                    refDB.get(dbIndex, sequences[index], 0, sequenceLength);
                }
            }
        }
        return sequences[index];
    }

    /**
     * Get sequence length
     *
     * @param index
     * @return sequence length
     */
    public int getSequenceLength(int index) {
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
