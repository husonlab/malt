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

/**
 * DNA characters
 * Daniel Huson, 8.2014
 */
public class DNA5 implements IAlphabet {

    final static private byte[] normalizedLetters = {
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', '-', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'A',
            'N', 'C', 'N', 'N', 'N', 'G', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'T', 'T', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'A', 'N', 'C', 'N', 'N', 'N', 'G', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'T', 'T', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N'
    };

    final static private byte[] normalizedComplement = {
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', '-', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'T',
            'N', 'G', 'N', 'N', 'N', 'C', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'A', 'A', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'T', 'N', 'G', 'N', 'N', 'N', 'C', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'A', 'A', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N'
    };

    private static DNA5 instance = new DNA5();

    /**
     * return an instance
     *
     * @return instance
     */
    public static DNA5 getInstance() {
        return instance;
    }

    /**
     * maps letter to 'A', 'C', 'G', 'T' or 'N'
     *
     * @param letter
     * @return
     */
    public byte getNormalized(byte letter) {
        return DNA5.normalizedLetters[letter];
    }

    /**
     * get complement of base
     *
     * @param letter
     * @return
     */
    public byte getBaseComplement(byte letter) {
        return normalizedComplement[letter];
    }

    /**
     * do letters a and b correspond to the same base?
     *
     * @param a
     * @param b
     * @return true, if equal bases
     */
    public boolean equal(byte a, byte b) {
        return normalizedLetters[a] == normalizedLetters[b];
    }

    /**
     * do strings a and b correspond to the same DNA sequences?
     *
     * @param a
     * @param b
     * @return true, if equal DNA sequences
     */
    public boolean equal(byte[] a, byte[] b) {
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++)
            if (normalizedLetters[a[i]] != normalizedLetters[b[i]])
                return false;
        return true;
    }


    /**
     * gets reverse complement of a DNA sequence
     *
     * @param sequence
     * @return reverse complement
     */
    public byte[] getReverseComplement(byte[] sequence) {
        byte[] result = new byte[sequence.length];
        for (int i = 0; i < sequence.length; i++) {
            result[i] = normalizedComplement[sequence[sequence.length - 1 - i]];
        }
        return result;
    }

    /**
     * gets reverse complement of a DNA sequence
     *
     * @param sequence
     * @param length
     * @param reverseComplement
     */
    public void getReverseComplement(byte[] sequence, int length, byte[] reverseComplement) {
        for (int i = 0; i < length; i++) {
            reverseComplement[i] = normalizedComplement[sequence[length - 1 - i]];
        }
    }

    /**
     * gets reverse, but not complement, of a DNA sequence
     *
     * @param sequence
     * @param length
     * @param reverse
     */
    public void getReverseNotComplement(byte[] sequence, int length, byte[] reverse) {
        for (int i = 0; i < length; i++) {
            reverse[i] = sequence[length - 1 - i];
        }
    }

    /**
     * is this a protein alphabet?
     *
     * @return true, if protein
     */
    public boolean isProtein() {
        return false;
    }

    /**
     * is this a DNA alphabet?
     *
     * @return true, if DNA
     */
    public boolean isDNA() {
        return true;
    }

    @Override
    public String getName() {
        return "DNA";
    }

    /**
     * reverse complement in place
     *
     * @param bytes
     */
    public void reverseComplement(byte[] bytes) {
        int top = (bytes.length + 1) / 2;
        for (int i = 0; i < top; i++) {
            int j = bytes.length - (i + 1);
            byte tmp = bytes[i];
            bytes[i] = getBaseComplement(bytes[j]);
            bytes[j] = getBaseComplement(tmp);
        }
    }

    /**
     * reverse (but no complement) in place
     *
     * @param bytes
     */
    public void reverse(byte[] bytes) {
        int top = bytes.length / 2;
        for (int i = 0; i < top; i++) {
            int j = bytes.length - (i + 1);
            byte tmp = bytes[i];
            bytes[i] = bytes[j];
            bytes[j] = tmp;
        }
    }

    /**
     * size
     *
     * @return size
     */
    public int size() {
        return 5;
    }

    /**
     * a DNA seed is good it does not contain an N and contains at least two different letters
     *
     * @param word
     * @param length
     * @return
     */
    @Override
    public boolean isGoodSeed(byte[] word, int length) {
        byte a = word[0];
        byte b = 0;
        for (int i = 0; i < length; i++) {
            if (word[i] == 'N')
                return false;
            else if (b == 0 && word[i] != a)
                b = word[i];
        }
        return b != 0;
    }
}
