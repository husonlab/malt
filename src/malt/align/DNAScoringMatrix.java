package malt.align;

/**
 * Basic DNA scoring matrix
 * Daniel Huson, 8.2014
 */
public class DNAScoringMatrix implements IScoringMatrix {
    private final int[][] matrix = new int[128][128];

    public DNAScoringMatrix(int matchScore, int mismatchScore) {
        for (int i = 0; i < 128; i++) {
            matrix[i][i] = matchScore;
            for (int j = i + 1; j < 128; j++)
                matrix[i][j] = matrix[j][i] = mismatchScore;
        }
    }

    /**
     * get score for letters a and b
     *
     * @param a
     * @param b
     * @return score
     */
    public int getScore(byte a, byte b) {
        return matrix[a][b];
    }

    @Override
    public int[][] getMatrix() {
        return matrix;
    }
}
