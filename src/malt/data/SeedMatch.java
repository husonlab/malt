/*
 *  SeedMatch.java Copyright (C) 2019. Daniel H. Huson GPL
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

/**
 * A seed match as used in the inner loop of the alignment engine
 * Daniel Huson, 8.2014
 */

import java.util.Comparator;

/**
 * a seed match, consisting of a location in a query and in a reference
 */
public class SeedMatch {
    private int queryOffset;
    private int referenceOffset;
    private int rank;     // rank of frame. Frame is given by frame[rank]
    private int seedLength;

    static private Comparator<SeedMatch> comparator = new Comparator<SeedMatch>() {
        public int compare(SeedMatch a, SeedMatch b) {
            if (a.queryOffset < b.queryOffset)
                return -1;
            else if (a.queryOffset > b.queryOffset)
                return 1;
            else if (a.referenceOffset < b.referenceOffset)
                return -1;
            else if (a.referenceOffset > b.referenceOffset)
                return 1;
            else if (a.rank < b.rank)
                return -1;
            else if (a.rank > b.rank)
                return 1;
            else if (a.seedLength < b.seedLength)
                return -1;
            else if (a.seedLength > b.seedLength)
                return 1;
            else
                return 0;
        }
    };

    /**
     * constructor
     */
    SeedMatch() {
    }

    /**
     * set the seed match
     *
     * @param queryOffset
     * @param referenceOffset
     * @param rank
     * @return this
     */
    public SeedMatch set(int queryOffset, int referenceOffset, int rank, int seedLength) {
        this.queryOffset = queryOffset;
        this.referenceOffset = referenceOffset;
        this.rank = rank;
        this.seedLength = seedLength;
        return this;
    }

    public int getRank() {
        return rank;
    }

    public int getQueryOffset() {
        return queryOffset;
    }

    public int getReferenceOffset() {
        return referenceOffset;
    }

    public int getSeedLength() {
        return seedLength;
    }

    public String toString() {
        return queryOffset + "/" + referenceOffset;
    }

    /**
     * compare first by query position and then by reference position
     *
     * @return comparator
     */
    static public Comparator<SeedMatch> getComparator() {
        return comparator;
    }

    /**
     * determines whether this seed follows the previous one. It is deemed to follow, if on the same diagonal +-3
     *
     * @param prev
     * @return true if prev not null and in same frame and similar coordinates
     */
    public boolean follows(SeedMatch prev) {
        return prev != null && prev.rank == rank && Math.abs((referenceOffset - queryOffset) - (prev.referenceOffset - prev.queryOffset)) < 3;
    }

    /**
     * resize array
     *
     * @param array
     * @return new array
     */
    public static SeedMatch[] resizeAndConstructEntries(SeedMatch[] array, int newSize) {
        SeedMatch[] result = new SeedMatch[newSize];
        if (array == null) {
            for (int i = 0; i < newSize; i++)
                result[i] = new SeedMatch();
        } else {
            for (int i = array.length; i < newSize; i++)
                result[i] = new SeedMatch();
            System.arraycopy(array, 0, result, 0, Math.min(newSize, array.length));
        }
        return result;
    }
}
