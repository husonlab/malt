/*
 *  SequenceEncoder.java Copyright (C) 2019. Daniel H. Huson GPL
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

import java.util.Iterator;

/**
 * sequence encoder
 * Daniel Huson, 2014
 */
public class SequenceEncoder implements ISeedExtractor {
    protected final int bitsPerLetter;
    protected final int lettersPerWord;
    protected final long letterMask;
    protected final int unusedBits;
    protected final long[] letter2code;
    protected final byte[] code2letter;
    protected final byte undefinedLetterCode;

    /**
     * constructor
     *
     * @param alphabet
     */
    public SequenceEncoder(final Alphabet alphabet) {
        bitsPerLetter = alphabet.getBitsPerLetter();
        lettersPerWord = 64 / bitsPerLetter;
        letterMask = alphabet.getLetterMask();
        unusedBits = alphabet.getUnusedBits();

        letter2code = alphabet.getLetter2Code();
        code2letter = alphabet.getCode2Letter();
        undefinedLetterCode = alphabet.getUndefinedLetterCode();
    }

    /**
     * encode a sequence
     *
     * @param sequence
     * @param length
     * @param sequenceCode array to use or null
     * @return encoded sequence
     */
    public long[] encode(byte[] sequence, int length, long[] sequenceCode) {
        int numberOfWords = length / lettersPerWord + 1;

        if (sequenceCode == null || sequenceCode.length < numberOfWords)
            sequenceCode = new long[numberOfWords];

        int shift = 64 - bitsPerLetter;
        int word = 0;
        for (int i = 0; i < length; i++) {
            sequenceCode[word] |= letter2code[sequence[i]] << shift;

            shift -= bitsPerLetter;
            if (shift < 0) {
                shift = 64 - bitsPerLetter;
                word++;
            }
        }
        /*
        for (int i = 0; i < numberOfWords; i++) {
            System.err.println(Long.toBinaryString(sequenceCode[i]));
        }
        */

        return sequenceCode;
    }

    /**
     * encode a sequence
     *
     * @param sequence
     * @param length
     * @return encoded sequence
     */
    public long[] encode(byte[] sequence, int length) {
        return encode(sequence, length, new long[length / lettersPerWord + 1]);
    }

    /**
     * encode a sequence
     *
     * @param sequence
     * @return encoded sequence
     */
    public long[] encode(byte[] sequence) {
        return encode(sequence, sequence.length, new long[sequence.length / lettersPerWord + 1]);
    }

    /**
     * encode a single letter
     *
     * @param letter
     * @return code
     */
    public byte encode(byte letter) {
        return (byte) letter2code[letter];
    }

    /**
     * decode a sequenceCode
     *
     * @param sequenceCode
     * @param bytes        sequence
     * @return sequence length
     */
    public int decode(long[] sequenceCode, byte[] bytes) {
        int shift = 64 - bitsPerLetter;
        int word = 0;
        int length = 0;
        while (true) {
            byte bits = (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);
            if (bits == 0)
                break;
            bytes[length++] = code2letter[bits];

            shift -= bitsPerLetter;
            if (shift < 0) {
                if (++word == sequenceCode.length)
                    break;
                shift = 64 - bitsPerLetter;
            }
        }
        return length;
    }

    /**
     * decode a sequence
     *
     * @param sequenceCode
     * @return sequence
     */
    public byte[] decode(long[] sequenceCode) {
        byte[] sequence = new byte[computeLength(sequenceCode)];
        decode(sequenceCode, sequence);
        return sequence;
    }

    /**
     * compute the length of the sequence. It is not stored explicitly.
     *
     * @param sequenceCode
     * @return sequence length
     */
    public int computeLength(long[] sequenceCode) {
        int length = lettersPerWord * (sequenceCode.length - 1); // assume all but last word are full

        int shift = 64 - bitsPerLetter;
        final int word = sequenceCode.length - 1;
        while (shift >= 0) {
            byte bits = (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);
            if (bits == 0)
                break;
            length++;
            shift -= bitsPerLetter;
        }
        return length;
    }

    /**
     * get a letter
     *
     * @param sequenceCode
     * @param pos
     * @return letter
     */
    public byte getLetterCode(long[] sequenceCode, int pos) {
        int word = pos / lettersPerWord;
        int letterInWord = pos - lettersPerWord * word;
        int shift = 64 - (letterInWord + 1) * bitsPerLetter;
        return (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);

    }

    /**
     * gets an getLetterCodeIterator over all letters
     *
     * @param sequenceCode
     * @return getLetterCodeIterator
     */
    public Iterator<Byte> getLetterIterator(final long[] sequenceCode) {
        return getLetterIterator(sequenceCode, 0);
    }

    /**
     * gets an iterator over all letters.
     * No check is performed to see whether pos is in range
     *
     * @param sequenceCode
     * @return iterator
     */
    public Iterator<Byte> getLetterIterator(final long[] sequenceCode, final int pos) {
        return new Iterator<Byte>() {
            private int word = pos / lettersPerWord;
            private int shift = 64 - ((pos - lettersPerWord * word) + 1) * bitsPerLetter;
            byte bits = (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);

            public boolean hasNext() {
                return bits > 0;
            }

            public Byte next() {
                byte result = decode(bits);
                // get next bits:
                shift -= bitsPerLetter;
                if (shift < 0) {
                    word++;
                    shift = 64 - bitsPerLetter;
                }
                if (word < sequenceCode.length)
                    bits = (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);
                else
                    bits = 0;
                // else done
                return result;
            }

            public void remove() {
            }
        };
    }

    /**
     * gets a reverse iterator over all letters.
     * No check is performed to see whether pos is in range
     *
     * @param sequenceCode
     * @return iterator
     */
    public Iterator<Byte> getLetterReverseIterator(final long[] sequenceCode, final int pos) {
        return new Iterator<Byte>() {
            private int word = pos / lettersPerWord;
            private int shift = 64 - ((pos - lettersPerWord * word) + 1) * bitsPerLetter;

            byte bits = (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);

            public boolean hasNext() {
                return bits > 0;
            }

            public Byte next() {
                byte result = decode(bits);
                shift += bitsPerLetter;
                if (shift >= 64) {
                    shift = unusedBits;
                    word--;
                }
                if (word >= 0)
                    bits = (byte) ((sequenceCode[word] & (letterMask << shift)) >>> shift);
                else
                    bits = 0;
                return result;
            }

            public void remove() {
            }
        };
    }

    /**
     * gets a seed code using the reduced alphabet
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos          @return reduced code
     */
    public long getSeedCode(final boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos) {
        return getSeedCode(seedShape, seedWeight, sequenceCode, pos, 0);
    }

    /**
     * get the code for a given seed
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos
     * @param failValue    @return code
     */
    public long getSeedCode(boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos, int failValue) {
        long seed = 0;
        int seedShift = (seedWeight - 1) * bitsPerLetter;

        int word = pos / lettersPerWord;
        int letterInWord = pos - lettersPerWord * word;
        int shift = 64 - (letterInWord + 1) * bitsPerLetter;
        for (boolean aSeedShape : seedShape) {
            if (aSeedShape) {
                long bits = (sequenceCode[word] & (letterMask << shift)) >>> shift;
                if (bits == 0)
                    return failValue;
                // System.err.println(Long.toBinaryString(bits));
                seed |= (bits << seedShift);
                seedShift -= bitsPerLetter;
            }
            shift -= bitsPerLetter;
            if (shift < 0) {
                shift = 64 - bitsPerLetter;
                word++;
                if (word == sequenceCode.length)
                    return failValue;
            }
        }
        return seed;
    }

    /**
     * decodes a seed code. For debugging only
     *
     * @param seedCode
     * @return bytes for seed
     */
    public byte[] decodeSeed(long seedCode, int seedWeight) {
        return decode(new long[]{seedCode << (64 - seedWeight * bitsPerLetter)});
    }

    /**
     * decodes a letter code
     *
     * @param letterCode
     * @return letter
     */
    public byte decode(byte letterCode) {
        return code2letter[letterCode];
    }

    /**
     * get the code for sequence spanned by a seed
     *
     * @param seedLength
     * @param sequenceCode
     * @param pos
     * @param seedWords
     * @return
     */
    public long[] getSeedSpanCode(int seedLength, long[] sequenceCode, int pos, long[] seedWords) {
        if (seedWords == null)
            seedWords = new long[1 + seedLength / lettersPerWord];
        int seedWord = 0;

        long seed = 0;
        int seedShift = 64 - bitsPerLetter;
        int word = pos / lettersPerWord;
        int letterInWord = pos - lettersPerWord * word;
        int shift = 64 - (letterInWord + 1) * bitsPerLetter;
        for (int i = 0; i < seedLength; i++) {
            long bits = (sequenceCode[word] & (letterMask << shift)) >>> shift;
            seed |= (bits << seedShift);
            seedShift -= bitsPerLetter;

            shift -= bitsPerLetter;
            if (shift < 0) {
                shift = 64 - bitsPerLetter;
                word++;
            }
            if (seedShift < 0) {
                seedShift = 64 - bitsPerLetter;
                seedWords[seedWord++] = seed;
                seed = 0;
            }
        }
        seedWords[seedWord] = seed;
        return seedWords;
    }

    /**
     * is this a good seed? Yes, if it contains at least two different letters and none is undefined
     *
     * @param seedCode
     * @return true, if good
     */
    public boolean isGoodSeed(long seedCode, int seedWeight) {
        int shift = 0;
        byte a = 0;
        byte b = 0;

        while (shift < 64) {
            byte bits = (byte) ((seedCode & (letterMask << shift)) >>> shift);
            if (bits == 0)
                break;
            else if (bits == undefinedLetterCode)
                return false;
            if (a == 0)
                a = bits;
            else if (bits != a && b == 0)
                b = bits;
            shift += bitsPerLetter;
        }
        return b != 0;
    }

    public int getBitsPerLetter() {
        return bitsPerLetter;
    }
}
