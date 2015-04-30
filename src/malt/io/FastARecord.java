package malt.io;

import jloda.util.Basic;

/**
 * A simple fastA record. Note that header and sequence are 0-terminated
 * Daniel Huson, 8.2014
 */

public class FastARecord {
    int id;
    int headerLength;
    byte[] header;
    int sequenceLength;
    byte[] sequence;

    byte[] qualityValues;

    /**
     * constructor
     */
    public FastARecord() {
    }

    /**
     * constructor
     *
     * @param initialLength
     */
    public FastARecord(int initialLength, boolean wantQualityValuesIfAvailable) {
        header = new byte[1000];
        sequence = new byte[initialLength];
        if (wantQualityValuesIfAvailable)
            qualityValues = new byte[initialLength];
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * get header length
     *
     * @return length
     */
    public int getHeaderLength() {
        return headerLength;
    }

    /**
     * get the header
     *
     * @return header  (0-terminated)
     */
    public byte[] getHeader() {
        return header;
    }

    public String getHeaderString() {
        return Basic.toString(header, headerLength);
    }

    /**
     * get the sequence length
     *
     * @return length
     */
    public int getSequenceLength() {
        return sequenceLength;
    }

    /**
     * get the sequence
     *
     * @return sequence (0-terminated)
     */
    public byte[] getSequence() {
        return sequence;
    }

    /**
     * set the sequence
     *
     * @param sequence
     * @param length
     */
    public void setSequence(byte[] sequence, int length) {
        this.sequence = sequence;
        this.sequence[length] = 0;
    }

    public String getSequenceString() {
        return Basic.toString(sequence, sequenceLength);
    }

    public byte[] getQualityValues() {
        return qualityValues;
    }

    public String getQualityValuesString() {
        return Basic.toString(qualityValues, sequenceLength);
    }

    /**
     * get as string
     *
     * @return
     */
    public String toString() {
        return (new StringBuilder()).append(Basic.toString(header, headerLength)).append("\n").append(Basic.toString(sequence, sequenceLength)).append("\n").toString();
    }

    public boolean isWantQualityValues() {
        return qualityValues != null;
    }
}
