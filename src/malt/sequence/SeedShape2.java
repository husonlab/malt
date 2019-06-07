/*
 *  SeedShape2.java Copyright (C) 2019. Daniel H. Huson GPL
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

import java.io.IOException;

/**
 * seed shape
 * Daniel Huson, 8.2014
 */
public class SeedShape2 {
    private final boolean[] mask;
    private final String shape;
    private final int weight;
    private int jumpToFirstZero = -1;

    // Source for all seed patterns: Ilie et al. BMC Genomics 2011, 12:280 http://www.biomedcentral.com/1471-2164/12/280
    public static final String SINGLE_DNA_SEED = "111110111011110110111111";
    public static final String SINGLE_PROTEIN_SEED = "111101101110111";
    public static final String[] PROTEIN_SEEDS = new String[]{"111101101110111", "1111000101011001111", "11101001001000100101111", "11101001000010100010100111"};
    private int id; // id is 0,..,number of seed shapes-1

    /**
     * constructor
     *
     * @param shape
     * @throws java.io.IOException
     */
    public SeedShape2(String shape) throws IOException {
        this.shape = shape;
        mask = new boolean[shape.length()];
        int ones = 0;
        for (int i = 0; i < shape.length(); i++) {
            if (shape.charAt(i) != '0') {
                mask[i] = true;
                ones++;
            } else {
                if (jumpToFirstZero == -1)
                    jumpToFirstZero = i;
            }
        }
        weight = ones;
    }

    /**
     * string representation of shaped seed
     *
     * @return string
     */
    public String toString() {
        return shape;
    }

    /**
     * get bytes
     *
     * @return string as bytes
     */
    public byte[] getBytes() {
        return shape.getBytes();
    }

    /**
     * length of spaced seed
     *
     * @return length
     */
    public int getLength() {
        return mask.length;
    }

    /**
     * weight of spaced seed
     *
     * @return weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * create correct size byte array for holding seed results
     *
     * @return bytes
     */
    public byte[] createBuffer() {
        return new byte[getWeight()];
    }

    /**
     * compute the number of positions to jump over to get to first 0
     *
     * @return number of ones before first zero
     */
    public int getJumpToFirstZero() {
        return jumpToFirstZero;
    }

    /**
     * gets the expected number of seeds
     *
     * @param numberOfSequences
     * @param numberOfLetters
     * @return expected number of seeds
     */
    public long getMaxSeedCount(int numberOfSequences, long numberOfLetters, int numberOfJobs) {
        return Math.max(1, numberOfLetters - numberOfSequences * (weight - 1)) / numberOfJobs;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean[] getMask() {
        return mask;
    }
}
