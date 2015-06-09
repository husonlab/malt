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
 * implements a protein alphabet
 * Daniel Huson, 8.2014
 */
public class ProteinAlphabet implements IAlphabet {

    final static private byte[] normalizedLetters = {
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', '*', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'A',
            'X', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'X', 'K', 'L', 'M', 'N', 'X', 'P', 'Q', 'R', 'S', 'T', 'X', 'V', 'W',
            'X', 'Y', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'A', 'X', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'X', 'K', 'L', 'M',
            'N', 'X', 'P', 'Q', 'R', 'S', 'T', 'X', 'V', 'W', 'X', 'Y', 'X', 'X', 'X', 'X', 'X', 'X'};

    private static ProteinAlphabet instance = new ProteinAlphabet();

    /**
     * return an instance
     *
     * @return instance
     */
    public static ProteinAlphabet getInstance() {
        return instance;
    }

    /**
     * maps letter to normalized base or amino acid
     *
     * @param letter
     * @return
     */
    public byte getNormalized(byte letter) {
        return normalizedLetters[letter];
    }

    /**
     * do letters a and b correspond to the same base or amino acid?
     *
     * @param a
     * @param b
     * @return true, if equal bases
     */
    public boolean equal(byte a, byte b) {
        return normalizedLetters[a] == normalizedLetters[b];
    }

    @Override
    public String getName() {
        return "PROTEIN";
    }

    /**
     * returns the used alphabet
     *
     * @return alphabet
     */
    public String toString() {
        return "A C D E F G H I K [L*] M N P Q R S T V W X Y";
    }

    /**
     * is this a protein alphabet?
     *
     * @return true, if protein
     */
    public boolean isProtein() {
        return true;
    }

    /**
     * is this a DNA alphabet?
     *
     * @return true, if DNA
     */
    public boolean isDNA() {
        return false;
    }

    public int size() {
        return 20;
    }

    /**
     * a protein seed is a good seed if it contains more than 2 different letters and no unknown
     *
     * @param word
     * @param length
     * @return
     */
    @Override
    public boolean isGoodSeed(byte[] word, int length) {
        final byte a = word[0];
        byte b = 0;
        byte c = 0;

        for (int i = 0; i < length; i++) {
            final byte z = word[i];
            if (z == 'X')
                return false;
            if (z != a) {
                if (b == 0)
                    b = z;
                else if (c == 0 && z != b)
                    c = z;
            }
        }
        return b != 0 && c != 0;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 128; i++) {
            char ch = Character.toUpperCase((char) i);
            if ("ACDEFGHIKLMNPQRSTVWXY*".contains("" + ch))
                System.err.print(" '" + ch + "',");
            else
                System.err.print(" 'X',");

        }
    }
}
