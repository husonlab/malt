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
package malt.data;

import jloda.util.Basic;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * implements a reduced protein alphabet
 * Daniel Huson, 8.2014
 */
public class ReducedAlphabet implements IAlphabet {
    private final String description;
    private final int size;

    private final byte[] normalizedLetters = {
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X',
            'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X', 'X'};

    public static Map<String, String> reductions = new TreeMap<>();

    static {
        // From: Bioinformatics. 2009 June 1; 25(11): 1356–1362. Published online 2009 April 7. doi: 10.1093/bioinformatics/btp164:
        reductions.put("GBMR4", "[ADKERNTSQ] [YFLIVMCWH*X] G P");
        reductions.put("SDM12", "A D [KER] N [STQ] [YF] [LIVM*X] C W H G P");
        reductions.put("HSDM17", "A D [KE] R N T S Q Y F [LIV*X] M C W H G P");
        // Murphy, Lynne Reed and Wallqvist, Anders and Levy, Ronald M., 2000 :
        reductions.put("BLOSUM50_4", "[LVIMC*X] [AGSTP] [FYW] [EDNQKRH]");
        reductions.put("BLOSUM50_8", "[LVIMC*X] [AG] [ST] P [FYW] [EDNQ] [KR] H");
        reductions.put("BLOSUM50_10", "[LVIM*X] C A G [ST] P [FYW] [EDNQ] [KR] H");
        reductions.put("BLOSUM50_11", "[LVIM*X] C A G S T P [FYW] [EDNQ] [KR] H"); // this was produced from BLOSUM50_10 by separating S and T
        reductions.put("BLOSUM50_15", "[LVIM*X] C A G S T P [FY] W E D N Q [KR] H");

        reductions.put("DIAMOND_11", "[KREDQN*X] C G H [ILV] M F Y W P [STA]"); // DIAMOND default

        // produced especially for MALT:
        reductions.put("MALT_10", "[LVIM*X] C [AST] G P [WYF] [DEQ] N [RK] H");

        reductions.put("UNREDUCED", "A D K E R N T S Q Y F [L*] I V M C W H G P");
    }

    /**
     * constructs a reduction protein alphabet mapper
     *
     * @param reduction either name or definition of a reduction
     */
    public ReducedAlphabet(String reduction) throws IOException {
        if (reduction.equalsIgnoreCase("default"))
            reduction = "DIAMOND_11";

        if (Basic.isOneWord(reduction)) {
            if (!reductions.containsKey(reduction))
                throw new IOException("Unknown protein reduction: " + reduction);
            reduction = reductions.get(reduction);
        }

        StringBuilder buffer = new StringBuilder();
        char group = 'A';
        buffer.append("[");

        boolean inWhiteSpace = true;
        for (int i = 0; i < reduction.length(); i++) {
            int ch = Character.toUpperCase(reduction.charAt(i));
            if (Character.isWhitespace(ch)) {
                if (!inWhiteSpace) {
                    group++;
                    buffer.append("] [");
                    inWhiteSpace = true;
                }
            } else {
                if (inWhiteSpace)
                    inWhiteSpace = false;
                if (Character.isLetter(ch) || ch == '*') {
                    normalizedLetters[Character.toLowerCase(ch)] = normalizedLetters[ch] = (byte) group;
                    buffer.append((char) ch);
                }
            }
        }
        buffer.append("]");
        if (normalizedLetters['*'] == 0)
            normalizedLetters['*'] = '*';
        description = buffer.toString();
        size = group - 'A' + 1;
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
     * @return true, if equalOverShorterOfBoth bases
     */
    public boolean equal(byte a, byte b) {
        return normalizedLetters[a] == normalizedLetters[b];
    }

    /**
     * gets human-readable description of reduction
     *
     * @return string
     */
    public String toString() {
        return description;
    }

    /**
     * gets the name of this alphabet
     *
     * @return name
     */
    public String getName() {
        return description;
    }

    /**
     * is this a full protein alphabet?
     *
     * @return true, if protein
     */
    public boolean isProtein() {
        return false;
    }

    /**
     * size of alphabet
     *
     * @return
     */
    public int size() {
        return size;
    }

    /**
     * is this a DNA alphabet?
     *
     * @return true, if DNA
     */
    public boolean isDNA() {
        return false;
    }

    /**
     * a reduced protein seed good if it doesn't contain an X
     *
     * @param word
     * @param length
     * @return
     */
    @Override
    public boolean isGoodSeed(byte[] word, int length) {
        for (int i = 0; i < length; i++) {
            if (word[i] == 'X')
                return false;
        }
        return true;
    }


    public static void main(String[] args) throws IOException {
        Set<Character> firstSet = null;
        for (String name : reductions.keySet()) {
            Set<Character> letters = new HashSet<>();

            String def = reductions.get(name);
            for (int i = 0; i < def.length(); i++) {
                if (Character.isLetter(def.charAt(i)) || def.charAt(i) == '*')
                    letters.add(def.charAt(i));
            }
            System.err.println(name + ": " + letters.size() + ": " + Basic.toString(letters, ","));
            if (firstSet == null)
                firstSet = letters;
            else {
                for (Character ch : letters) {
                    if (!firstSet.contains(ch))
                        System.err.println("Unexpected letter: " + ch);
                }
            }
            ReducedAlphabet alphabet = new ReducedAlphabet(name);
            System.err.println("Alphabet: " + alphabet.toString());
            System.err.println("Size:     " + alphabet.size());
        }
    }
}
