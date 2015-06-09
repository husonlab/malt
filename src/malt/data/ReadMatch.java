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

import jloda.util.Basic;

import java.util.Comparator;

/**
 * A match for a given read
 * Daniel Huson, 8.2014
 */
public
/**
 * a read match, consisting of a score, reference ID and the match text
 */
class ReadMatch {
    private static long numberOfEntries = 0;
    private long entryNumber;  // used to make all matches unique

    private float bitScore;
    private float expected;
    private int percentIdentity;
    private int referenceId;
    private byte[] text;      // match text
    private byte[] rma3Text;

    private int startRef; // start position of match in reference sequence
    private int endRef;  // end position of match in reference sequence

    /**
     * constructor
     */
    public ReadMatch() {

    }

    /**
     * returns a copy
     *
     * @return copy
     */
    public ReadMatch getCopy() {
        return new ReadMatch(bitScore, expected, percentIdentity, referenceId, text, rma3Text, startRef, endRef);
    }

    /**
     * constructor
     *
     * @param bitScore
     * @param referenceId
     * @param text
     */
    public ReadMatch(float bitScore, float expected, int percentIdentity, int referenceId, byte[] text, byte[] rma3Text, int startRef, int endRef) {
        this.bitScore = bitScore;
        this.expected = expected;
        this.percentIdentity = percentIdentity;
        this.referenceId = referenceId;
        this.entryNumber = ++numberOfEntries;
        this.text = text;
        this.rma3Text = rma3Text;
        this.startRef = startRef;
        this.endRef = endRef;
    }

    /**
     * reuse this object
     *
     * @param score
     * @param referenceId
     * @param text
     */
    public void set(float score, int referenceId, byte[] text, byte[] rma3Text, int startRef, int endRef) {
        this.bitScore = score;
        this.referenceId = referenceId;
        this.entryNumber = ++numberOfEntries;
        this.text = text;
        this.rma3Text = rma3Text;
        this.startRef = startRef;
        this.endRef = endRef;
    }

    public float getBitScore() {
        return bitScore;
    }

    public float getExpected() {
        return expected;
    }

    public int getPercentIdentity() {
        return percentIdentity;
    }

    public int getReferenceId() {
        return referenceId;
    }

    public byte[] getText() {
        return text;
    }

    public byte[] getRMA3Text() {
        return rma3Text;
    }

    public int getStartRef() {
        return startRef;
    }

    public int getEndRef() {
        return endRef;
    }

    public String toString() {
        return "RefId=" + referenceId + " bitScore=" + bitScore + " start=" + startRef + " end=" + endRef + " text=" + (text == null ? "null" : Basic.toString(text));
    }

    /**
     * get comparator
     */
    static public Comparator<ReadMatch> createComparator() {
        return new Comparator<ReadMatch>() {
            public int compare(ReadMatch a, ReadMatch b) {
                if (a.bitScore < b.bitScore)
                    return -1;
                else if (a.bitScore > b.bitScore)
                    return 1;
                else if (a.referenceId < b.referenceId)
                    return 1;
                else if (a.referenceId > b.referenceId)
                    return -1;
                else if (a.entryNumber < b.entryNumber)
                    return -1;
                else if (a.entryNumber > b.entryNumber)
                    return 1;
                else
                    return 0;
            }
        };
    }

    /**
     * does this overlap the given reference coordinates?
     *
     * @param start
     * @param end
     * @return overlaps the given coordinates?
     */
    public boolean overlap(int start, int end) {
        return !(Math.min(startRef, endRef) >= Math.max(start, end) || Math.max(startRef, endRef) <= Math.min(start, end));
    }
}
