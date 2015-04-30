package malt.sequence;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by huson on 9/30/14.
 */
public class DNA5SequenceEncoder {
    /**
     * test program
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        SequenceEncoder encoder = new SequenceEncoder(DNA5Alphabet.getInstance());

        byte[] sequence1 = "ACCATGAACCATGAACCATGANACCATGAACCATGAACCATGANACCATGAACCATGAACCATGAN".getBytes();

        System.err.println("set: " + Basic.toString(sequence1));
        long[] encoded = encoder.encode(sequence1, sequence1.length, null);

        byte[] sequence2 = encoder.decode(encoded);
        System.err.println("get: " + Basic.toString(sequence2));

        System.err.println("SAME: " + Basic.equal(sequence1, sequence2));

        for (int i = 0; i < sequence2.length; i++) {
            if (sequence2[i] != encoder.decode((byte) encoder.getLetterCode(encoded, i)))
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


        SeedShape2 seedShape = new SeedShape2(SeedShape2.SINGLE_DNA_SEED);

        System.err.println("SeedShape: " + seedShape);

        for (int i = 0; i < 5; i++) {
            System.err.print("Span at " + i + ": ");
            long[] span = encoder.getSeedSpanCode(seedShape.getLength(), encoded, i, null);
            System.err.print(Basic.toString(encoder.decode(span)));
            System.err.println();

            long seedCode = encoder.getSeedCode(seedShape.getMask(), seedShape.getWeight(), encoded, i);
            System.err.println("Seed at " + i + ": " + Basic.toString(encoder.decodeSeed(seedCode, seedShape.getWeight()))
                    + "      " + Basic.toBinaryString(seedCode));
        }

        int limit = sequence1.length - seedShape.getLength();
        long[] seeds = new long[2 * limit];
        for (int i = 0; i < limit; i++) {
            seeds[2 * i] = encoder.getSeedCode(seedShape.getMask(), seedShape.getWeight(), encoded, i);
            seeds[2 * i + 1] = i;
        }

        seeds = ProteinSequenceEncoder.radixSort2(seeds, seeds.length, 64 - encoder.unusedBits, encoder.bitsPerLetter, new ProgressPercentage("Sorting..."));

        for (int i = 0; i < seeds.length; i += 2) {
            System.err.println(String.format("i=%3d pos=%3d seed=%s", i, seeds[i + 1], Basic.toString(encoder.decodeSeed(seeds[i], seedShape.getWeight()))));
        }

    }
}
