/*
 *  ReducedAlphabet.java Copyright (C) 2019. Daniel H. Huson GPL
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
import java.util.Map;
import java.util.TreeMap;

/**
 * Reduced protein alphabet
 * Daniel Huson, 9.2014
 */
public class ReducedAlphabet extends Alphabet implements ISeedExtractor {
    private final Alphabet proteinAlphabet;
    private final long[] proteinCode2ReducedCode;

    private static Map<String, String> reductions;

    /**
     * constructor
     *
     * @param proteinAlphabet
     * @param reductionDefinition
     */
    public ReducedAlphabet(final Alphabet proteinAlphabet, String reductionDefinition) throws IOException {
        super(getReductionDefinition(reductionDefinition), (byte) 'X');
        this.proteinAlphabet = proteinAlphabet;
        proteinCode2ReducedCode = new long[proteinAlphabet.getCode2Letter().length];
        for (int i = 1; i < proteinCode2ReducedCode.length; i++) {
            proteinCode2ReducedCode[i] = getLetter2Code()[proteinAlphabet.getCode2Letter()[i]];
            /*
            System.err.println(Integer.toBinaryString(i)+" -> "+(char)proteinAlphabet.getCode2Letter()[i]+" -> "
            +Integer.toBinaryString((int)proteinCode2ReducedCode[i]));
            */
        }
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
     * @param seedCode
     * @param seedWeight
     * @return seed sequence
     */
    @Override
    public byte[] decodeSeed(long seedCode, int seedWeight) {
        byte[] sequence = new byte[seedWeight];
        decode(new long[]{seedCode << (64 - seedWeight * bitsPerLetter)}, sequence);
        return sequence;
    }

    /**
     * gets a seed code using the reduced alphabet
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos
     * @return reduced code
     */
    public long getSeedCode(final boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos) {
        return getSeedCode(seedShape, seedWeight, sequenceCode, pos, 0);
    }

    /**
     * gets a seed code using the reduced alphabet
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos
     * @param failValue    value to return if seed extraction fails due to sequence being too short   @return reduced code
     */
    public long getSeedCode(final boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos, int failValue) {
        // this code is a bit tricky:
        // we need to use protein alphabet encoding parameters to step through the sequence code
        // we need to use reduced protein alphabet encoding parameters to compute seed code
        long seed = 0;
        int seedShift = (seedWeight - 1) * bitsPerLetter;

        int word = pos / proteinAlphabet.lettersPerWord;
        int letterInWord = pos - proteinAlphabet.lettersPerWord * word;
        int shift = 64 - (letterInWord + 1) * proteinAlphabet.bitsPerLetter;

        for (boolean aSeedShape : seedShape) {
            if (aSeedShape) {
                long bits = (sequenceCode[word] & (proteinAlphabet.letterMask << shift)) >>> shift;
                if (bits == 0)
                    return failValue;
                bits = proteinCode2ReducedCode[(int) bits];
                seed |= (bits << seedShift);
                seedShift -= bitsPerLetter;
            }
            shift -= proteinAlphabet.bitsPerLetter;
            if (shift < 0) {
                shift = 64 - proteinAlphabet.bitsPerLetter;
                word++;
                if (word == sequenceCode.length)
                    return failValue;
            }
        }
        return seed;
    }

    /**
     * get a reduction by name
     *
     * @param name
     * @return reduction definition string or null
     */
    public static String getReductionDefinition(String name) throws IOException {
        if (reductions == null)
            reductions = initReductions();
        String value = reductions.get(name);
        if (value == null) {
            if (name.split(" ").length > 1)
                return name;
            else
                throw new IOException("Unknown reduction: " + name);
        }
        return value;
    }

    /**
     * is this a good seed? Yes, if it contains at least three different letters and none is undefined
     *
     * @param seedCode
     * @return true, if good
     */
    public boolean isGoodSeed(long seedCode, int seedWeight) {
        int shift = 0;
        byte a = 0;
        byte b = 0;
        byte c = 0;

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
            else if (bits != a && bits != b && c == 0)
                c = bits;
            shift += bitsPerLetter;
        }
        return c != 0;
    }

    /**
     * setup the set of all known reductions
     *
     * @return reductions
     */
    private static Map<String, String> initReductions() {
        Map<String, String> reductions = new TreeMap<>();
        // From: Bioinformatics. 2009 June 1; 25(11): 1356–1362. Published online 2009 April 7. doi: 10.1093/bioinformatics/btp164:
        reductions.put("GBMR4", "[ADKERNTSQ] [YFLIVMCWH*X] G P");
        reductions.put("SDM12", "A D [KER] N [STQ] [YF] [LIVM*X] C W H G P");
        reductions.put("HSDM17", "A D [KE] R N T S Q Y F [LIV*X] M C W H G P");
        // Murphy, Lynne Reed and Wallqvist, Anders and Levy, Ronald M., 2000 :
        reductions.put("BLOSUM50_4", "[LVIMC*] [AGSTP] [FYW] [EDNQKRH]");
        reductions.put("BLOSUM50_8", "[LVIMC*] [AG] [ST] P [FYW] [EDNQ] [KR] H");
        reductions.put("BLOSUM50_10", "[LVIM*] C A G [ST] P [FYW] [EDNQ] [KR] H");
        reductions.put("BLOSUM50_11", "[LVIM*] C A G S T P [FYW] [EDNQ] [KR] H"); // this was produced from BLOSUM50_10 by separating S and T
        reductions.put("BLOSUM50_15", "[LVIM*] C A G S T P [FY] W E D N Q [KR] H");

        reductions.put("DIAMOND_11", "[KREDQN*] C G H [ILV] M F Y W P [STA]"); // DIAMOND default

        // produced especially for MALT:
        reductions.put("MALT_10", "[LVIM*X] C [AST] G P [WYF] [DEQ] N [RK] H");

        // use these together to get good sensitivity:
        reductions.put("MALT_12A", " [LVMI*WYF] C [AST] G P D E Q N R K H");
        reductions.put("MALT_12B", " [LVM*I] W Y F C A S T G P [DEQNRK] H");
        reductions.put("MALT_12C", "[LVIM*] C [AST] G P [FY] [DE] W N Q [KR] H");

        reductions.put("UNREDUCED", "A D K E R N T S Q Y F L I V M C W H G P *");
        return reductions;
    }
}
