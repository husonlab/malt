/*
 *  ProteinSequenceEncoder.java Copyright (C) 2019. Daniel H. Huson GPL
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

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.data.SeedShape;

import java.io.IOException;
import java.util.Iterator;

/**
 * test protein sequence encoder
 * Created by huson on 9/30/14.
 */
public class ProteinSequenceEncoder {

    /**
     * test program
     *
     * @param args
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        SequenceEncoder encoder = new SequenceEncoder(ProteinAlphabet.getInstance());
        ReducedAlphabet reducedAlphabet = new ReducedAlphabet(ProteinAlphabet.getInstance(), "DIAMOND_11");

        byte[] sequence1 = "MKTKSSNNIKKIYYISSILVGIYLCWQIIIQIIFLMDNSIAILEAIGMVVFISVYSLAVAINGWILVGRMKKSSKKAQYE".getBytes();

        System.err.println("set: " + Basic.toString(sequence1));
        long[] encoded = encoder.encode(sequence1, sequence1.length, null);

        byte[] sequence2 = encoder.decode(encoded);
        System.err.println("get: " + Basic.toString(sequence2));

        System.err.println("SAME: " + Basic.equal(sequence1, sequence2));

        for (int i = 0; i < sequence2.length; i++) {
            if (sequence2[i] != encoder.decode(encoder.getLetterCode(encoded, i)))
                System.err.println((char) sequence2[i] + " != " + (char) encoder.getLetterCode(encoded, i));
        }

        System.err.print("It.: ");
        for (Iterator<Byte> it = encoder.getLetterIterator(encoded); it.hasNext(); ) {
            System.err.print((char) (byte) it.next());
        }
        System.err.println();
        int skip = 30;
        System.err.print("It.: ");
        for (int i = 0; i < skip; i++)
            System.err.print(" ");
        for (Iterator<Byte> it = encoder.getLetterIterator(encoded, skip); it.hasNext(); ) {
            System.err.print((char) (byte) it.next());
        }
        System.err.println();
        int rev = 30;
        System.err.print("It.: ");
        for (Iterator<Byte> it = encoder.getLetterReverseIterator(encoded, rev); it.hasNext(); ) {
            System.err.print((char) (byte) it.next());
        }
        System.err.println(" reverse " + rev);


        SeedShape2 seedShape = new SeedShape2(SeedShape.SINGLE_PROTEIN_SEED);

        System.err.println("SeedShape: " + seedShape);

        for (int i = 0; i < 5; i++) {
            System.err.print("Span at " + i + ": ");
            long[] span = encoder.getSeedSpanCode(seedShape.getLength(), encoded, i, null);
            System.err.print(Basic.toString(encoder.decode(span)));
            System.err.println();

            long seedCode = encoder.getSeedCode(seedShape.getMask(), seedShape.getWeight(), encoded, i);
            System.err.println("Full seed at " + i + ":    " + Basic.toString(encoder.decodeSeed(seedCode, seedShape.getWeight()))
                    + "      " + Basic.toBinaryString(seedCode));

            long reducedSeedCode = reducedAlphabet.getSeedCode(seedShape.getMask(), seedShape.getWeight(), encoded, i);
            System.err.println("Reduced seed at " + i + ": " +
                    Basic.toString(reducedAlphabet.decodeSeed(reducedSeedCode, seedShape.getWeight()))
                    + "      " + Basic.toBinaryString(reducedSeedCode));
        }

        int limit = sequence1.length - seedShape.getLength();
        long[] seeds = new long[2 * limit];
        for (int i = 0; i < limit; i++) {
            seeds[2 * i] = reducedAlphabet.getSeedCode(seedShape.getMask(), seedShape.getWeight(), encoded, i);
            seeds[2 * i + 1] = i;
        }

        seeds = ProteinSequenceEncoder.radixSort2(seeds, seeds.length, 64 - reducedAlphabet.unusedBits, reducedAlphabet.bitsPerLetter, new ProgressPercentage("Sorting..."));

        for (int i = 0; i < seeds.length; i += 2) {
            System.err.println(String.format("i=%3d pos=%3d seed=%s", i, seeds[i + 1], Basic.toString(reducedAlphabet.decodeSeed(seeds[i], seedShape.getWeight()))));
        }
    }

    /**
     * radix sort list of longs, using entries with even index as keys and entries with odd indices as associated values
     *
     * @param array
     * @param length
     * @param w      number of bits to use (64 to sort full numbers)
     * @param d      number of bits to consider at a time - in the case of 4-bit encoded letters: 4
     * @return sorted array
     */
    public static long[] radixSort2(long[] array, int length, int w, int d, final ProgressPercentage progress) {
        if (length % 2 != 0)
            throw new RuntimeException("radixSort2(length=" + length + "): length must be even");

        final int steps = w / d;
        long[] a = array;
        long[] b = new long[length];

        if (progress != null) {
            progress.setMaximum(steps);
            progress.setProgress(0);
        }

        for (int p = 0; p < steps; p++) {
            final int[] c = new int[1 << d];
            // the next three for loops implement counting-sort
            for (int i = 0; i < length; i += 2) {
                c[(int) ((a[i] >> d * p) & ((1 << d) - 1))]++;
            }
            for (int i = 1; i < 1 << d; i++)
                c[i] += c[i - 1];
            for (int i = length - 2; i >= 0; i -= 2) {
                final int index = (--c[(int) ((a[i] >> d * p) & ((1 << d) - 1))]) << 1;
                b[index] = a[i];
                b[index + 1] = a[i + 1];
            }
            // swap arrays
            final long[] tmp = b;
            b = a;
            a = tmp;
            if (progress != null)
                progress.setProgress(p);
        }
        return a;
    }

}
