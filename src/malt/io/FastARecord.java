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
