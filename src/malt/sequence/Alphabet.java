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

import malt.data.INormalizer;

/**
 * Alphabet base class
 * <p/>
 * Created by huson on 9/30/14.
 */
public class Alphabet implements INormalizer {
    protected final byte alphabetSize;
    protected final long[] letter2code;
    protected final byte[] letter2normalized;
    protected final byte[] code2letter;
    protected final int bitsPerLetter;
    protected final int unusedBits;
    protected final int lettersPerWord;
    protected final long letterMask;
    protected final byte undefinedLetterCode;
    protected final String definitionString;

    /**
     * constructor
     *
     * @param definitionString
     * @param undefinedLetter
     */
    public Alphabet(String definitionString, byte undefinedLetter) {
        boolean isUndefinedContained = (definitionString.indexOf(undefinedLetter) != -1);
        this.definitionString = definitionString.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("  ", " ");
        String[] letterGroups = this.definitionString.split(" ");
        alphabetSize = (byte) (letterGroups.length + (isUndefinedContained ? 0 : 1));

        {
            int bits = 1;
            while (true) {
                if (Math.pow(2, bits) > alphabetSize) {
                    break;
                }
                bits++;
            }
            bitsPerLetter = bits;
        }
        letterMask = (1l << bitsPerLetter) - 1;
        lettersPerWord = 64 / bitsPerLetter;
        unusedBits = 64 - lettersPerWord * bitsPerLetter;

        System.err.println("Alphabet: " + definitionString + " bits: " + bitsPerLetter);

        code2letter = new byte[alphabetSize + 1];

        undefinedLetterCode = alphabetSize;
        letter2code = new long[127];
        letter2normalized = new byte[127];

        for (int i = 0; i < 127; i++) {
            letter2code[i] = undefinedLetterCode;
            letter2normalized[i] = undefinedLetter;
        }
        code2letter[undefinedLetterCode] = undefinedLetter;

        int bits = 1;
        for (String letterGroup : letterGroups) {
            for (int j = 0; j < letterGroup.length(); j++) {
                int letter = Character.toLowerCase(letterGroup.charAt(j));
                letter2code[letter] = bits;
                letter = Character.toUpperCase(letterGroup.charAt(j));
                letter2code[letter] = bits;
                letter2normalized[letter] = (byte) letterGroup.charAt(0);
                if (j == 0)
                    code2letter[bits] = (byte) letter;
            }
            // System.err.println(letterGroups[i]+" -> "+Integer.toBinaryString(bits)+" -> "+(char)code2letter[bits]);
            bits++;
        }
    }

    /**
     * gets the alphabet size
     *
     * @return alphabet size
     */
    public byte getAlphabetSize() {
        return alphabetSize;
    }

    /**
     * gets the number of bits used to encode a letter
     *
     * @return number of bits
     */
    public int getBitsPerLetter() {
        return bitsPerLetter;
    }

    /**
     * gets the letter to code mapping
     *
     * @return letter to code
     */
    public long[] getLetter2Code() {
        return letter2code;
    }


    /**
     * gets the code to letter mapping
     *
     * @return code to letter
     */
    public byte[] getCode2Letter() {
        return code2letter;
    }

    /**
     * gets the mask used for a single letter
     *
     * @return letter mask
     */
    public long getLetterMask() {
        return letterMask;
    }

    /**
     * gets the number of letters per 64-bit word
     *
     * @return letters per word
     */
    public int getLettersPerWord() {
        return lettersPerWord;
    }

    /**
     * gets the number of unused bits
     *
     * @return number of unused (per 64-bit word)
     */
    public int getUnusedBits() {
        return unusedBits;
    }

    /**
     * gets the code assigned to undefined letter
     *
     * @return code
     */
    public byte getUndefinedLetterCode() {
        return undefinedLetterCode;
    }

    /**
     * gets the definition string
     *
     * @return defintion
     */
    public String getDefinitionString() {
        return definitionString;
    }

    /**
     * returns normalized letter
     *
     * @param letter
     * @return normalized letter
     */
    @Override
    public byte getNormalized(byte letter) {
        return letter2normalized[letter];
    }
}
