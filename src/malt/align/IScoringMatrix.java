package malt.align;

/**
 * interface for scoring matrix
 * Daniel Huson, 8.2014
 */
public interface IScoringMatrix {
    /**
     * gets the score for aligning letters a and b
     *
     * @param a
     * @param b
     * @return score
     */
    public int getScore(byte a, byte b);

    /**
     * get the scoring matrix
     *
     * @return matrix
     */
    public int[][] getMatrix();
}
