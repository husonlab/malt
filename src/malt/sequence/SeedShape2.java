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
