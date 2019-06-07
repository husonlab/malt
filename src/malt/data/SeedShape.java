/*
 *  SeedShape.java Copyright (C) 2019. Daniel H. Huson GPL
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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * seed shape
 * Daniel Huson, 8.2014
 */
public class SeedShape {
    private final String shape;
    private final int[] map;
    private final int length;
    private final int weight;
    private final IAlphabet alphabet;
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
     * @throws IOException
     */
    public SeedShape(IAlphabet alphabet, String shape) throws IOException {
        this(alphabet, shape.getBytes());
    }

    /**
     * constructor
     *
     * @param shapeBytes
     * @throws IOException
     */
    public SeedShape(IAlphabet alphabet, byte[] shapeBytes) throws IOException {
        this.alphabet = alphabet;
        StringBuilder buf = new StringBuilder();
        for (byte a : shapeBytes) buf.append((char) a);
        this.shape = buf.toString();
        int pos = 0;
        List<Integer> list = new LinkedList<>();
        for (int i = 0; i < shapeBytes.length; i++) {
            byte a = shapeBytes[i];
            switch (a) {
                case '0':
                    if (jumpToFirstZero == -1)
                        jumpToFirstZero = i;
                    break;
                case '1':
                    list.add(pos);
                    break;
                default:
                    throw new IOException("Illegal character '" + (char) a + "' in shape: " + this.shape);
            }
            pos++;
        }
        length = shapeBytes.length;
        weight = list.size();
        map = new int[weight];
        int i = 0;
        for (Integer value : list) {
            map[i++] = value;
        }
        // System.err.println("Seed='" + toString()+"', length: " + getMaxIndex() + ", weight: " + getWeight());
        // System.err.println("Map: " + Basic.toString(map, ","));
    }

    /**
     * gets a spaced seed from the given sequence starting at the given offset
     *
     * @param sequence
     * @param offset
     * @param result   if non-null, is used for result
     * @return spaced seed
     */
    public byte[] getSeed(byte[] sequence, int offset, byte[] result) {
        if (result == null)
            result = new byte[weight];
        for (int i = 0; i < weight; i++) {
            result[i] = alphabet.getNormalized(sequence[offset + map[i]]);
        }

        // String seq=new String(sequence).substring(offset,offset+length);
        // System.err.println("Sequence: "+seq+": seed: "+new String(result));
        return result;
    }

    /**
     * are query and reference equalOverShorterOfBoth sequences at given offset for the given seed shape?
     *
     * @param query
     * @param qOffset
     * @param reference
     * @param rOffset
     * @return true if equalOverShorterOfBoth for seed shape
     */
    public boolean equalSequences(byte[] query, int qOffset, byte[] reference, int rOffset) {
        for (int i = 0; i < weight; i++) {
            if (!alphabet.equal(query[qOffset + map[i]], reference[rOffset + map[i]])) // sequences are normalized, so ok to compare directly
                return false;
        }
        return true;
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
        return length;
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

    public IAlphabet getAlphabet() {
        return alphabet;
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
    public long getEstimatedSeedCount(int numberOfSequences, long numberOfLetters, int numberOfJobs) {
        return Math.max(1, numberOfLetters - numberOfSequences * (weight - 1)) / numberOfJobs;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean[] getMask() {
        boolean[] mask = new boolean[shape.length()];
        for (int i = 0; i < shape.length(); i++)
            if (shape.charAt(i) == '1')
                mask[i] = true;
        return mask;
    }
}
