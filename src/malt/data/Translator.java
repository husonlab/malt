package malt.data;

import jloda.util.SequenceUtils;

/**
 * translate DNA sequences into protein sequences
 * Daniel Huson, 8.2014
 */
public class Translator {
    /**
     * translate a given DNA sequence into protein sequences
     *
     * @param doForward
     * @param doReverse
     * @param dnaSequence
     * @param length
     * @param frame
     * @param proteinSequences
     * @param proteinLengths
     * @return number of sequences returned
     */
    public static int getBestFrames(boolean doForward, boolean doReverse, byte[] dnaSequence, int length, int[] frame, byte[][] proteinSequences, int[] proteinLengths) {
        int numberOfResults = 0;
        for (int shift = 0; shift <= 2; shift++) {
            if (doForward) {
                int posProteins = 0;
                for (int pos = shift; pos < length - 2; pos += 3) {
                    proteinSequences[numberOfResults][posProteins++] = SequenceUtils.getAminoAcid(dnaSequence, pos);
                }
                if (isPossibleCodingSequence(proteinSequences[numberOfResults], posProteins)) {
                    proteinLengths[numberOfResults] = posProteins;
                    frame[numberOfResults] = shift + 1;
                    numberOfResults++;
                }
            }
            if (doReverse) {
                int posProteins = 0;
                for (int pos = length - 3 - shift; pos >= 0; pos -= 3) {
                    proteinSequences[numberOfResults][posProteins++] = SequenceUtils.getAminoAcidReverse(dnaSequence, pos);
                }
                if (isPossibleCodingSequence(proteinSequences[numberOfResults], posProteins)) {
                    proteinLengths[numberOfResults] = posProteins;
                    frame[numberOfResults] = -(shift + 1);
                    numberOfResults++;
                }
            }
        }
        return numberOfResults;
    }

    /**
     * heuristically determine whether this looks like real coding sequence
     *
     * @param sequence
     * @param length
     * @return true, if there is a stop-free run of at least 20 amino acids or if whole sequence is stop-free
     */
    private static boolean isPossibleCodingSequence(byte[] sequence, int length) {
        int nonStopRun = 0;
        for (int i = 0; i < length; i++) {
            if (sequence[i] == '*')
                nonStopRun = 0;
            else {
                nonStopRun++;
                if (nonStopRun == 20)
                    return true;
            }
        }
        return nonStopRun == length;
    }
}
