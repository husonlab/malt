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
