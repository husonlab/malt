/*
 *  ReadMatch.java Copyright (C) 2019. Daniel H. Huson GPL
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
    private byte[] rma6Text;

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
        return new ReadMatch(bitScore, expected, percentIdentity, referenceId, text, rma6Text, startRef, endRef);
    }

    /**
     * constructor
     *
     * @param bitScore
     * @param referenceId
     * @param text
     */
    public ReadMatch(float bitScore, float expected, int percentIdentity, int referenceId, byte[] text, byte[] rma6Text, int startRef, int endRef) {
        this.bitScore = bitScore;
        this.expected = expected;
        this.percentIdentity = percentIdentity;
        this.referenceId = referenceId;
        this.entryNumber = ++numberOfEntries;
        this.text = text;
        this.rma6Text = rma6Text;
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
        this.rma6Text = rma3Text;
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

    public byte[] getRMA6Text() {
        return rma6Text;
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
