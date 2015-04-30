package malt.malt2;

import jloda.util.SequenceUtils;

/**
 * translates a DNA sequence into a amino acid sequence
 * <p/>
 * Created by huson on 8/17/14.
 */
public class TranslatorNew {
    public static boolean acceptAllSequences = false; // if this set, accept all sequences, otherwise use heuristic to tell whether sequence looks good

    /**
     * translate a given DNA sequence into a protein sequence
     *
     * @param dnaSequence
     * @param frame       one of: +1,+2,+3,-1,-2,-3
     * @return amino acid sequence or null
     */
    public static byte[] getTranslation(byte[] dnaSequence, int frame) {
        if (frame > 0) {
            int posProtein = 0;
            final byte[] protein = new byte[(dnaSequence.length - (frame - 1)) / 3];
            int pos = frame - 1;
            while (pos < dnaSequence.length - 2) {
                protein[posProtein++] = SequenceUtils.getAminoAcid(dnaSequence[pos++], dnaSequence[pos++], dnaSequence[pos++]);
            }
            if (acceptAllSequences || isPossibleCodingSequence(protein))
                return protein;
            else
                return null;

        } else if (frame < 0) {
            int posProtein = 0;
            final byte[] protein = new byte[(dnaSequence.length + frame + 1) / 3];
            int pos = dnaSequence.length + frame;
            while (pos >= 2) {
                protein[posProtein++] = SequenceUtils.getAminoAcidReverse(dnaSequence[pos--], dnaSequence[pos--], dnaSequence[pos--]);
            }
            if (acceptAllSequences || isPossibleCodingSequence(protein))
                return protein;
            else
                return null;
        } else
            throw new RuntimeException("Illegal frame: " + frame);
    }

    /**
     * heuristically determine whether this looks like real coding sequence
     *
     * @param sequence
     * @return true, if there is a stop-free run of at least 20 amino acids or if whole sequence is stop-free
     */
    public static boolean isPossibleCodingSequence(byte[] sequence) {
        int nonStopRun = 0;
        for (int i = 0; i < sequence.length; i++) {
            if (sequence[i] == '*')
                nonStopRun = 0;
            else {
                nonStopRun++;
                if (nonStopRun == 20)
                    return true;
            }
        }
        return nonStopRun == sequence.length;
    }

}
