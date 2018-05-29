/*
 *  Copyright (C) 2018 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package malt.util;

/**
 * compute an approximation of the factorial function
 */
public class PoissionDistribution {
    /**
     * Poission probablity
     *
     * @param lambda mean
     * @param k      number of events
     * @return probability
     */
    public static double computePoisson(double lambda, int k) {
        double value = Math.exp(-lambda);

        for (int i = 2; i <= k; i++) {
            value *= (lambda / i);
        }
        return value;
    }

    public static void main(String[] args) {
        {
            for (int N = 1000; N < 60000; N += 1000) {
                double H = 1000;

                double r = N / H;

                System.err.println("N=" + N + ", H=" + 1000 + ", r=" + r);

                int threshold = 0;
                for (int k = 1; k < 200; k++) {
                    double value = computePoisson(r, k);
                    double adjusted = (N / H) * value;
                    System.err.println("P(" + k + ")=" + value + " adjusted: " + adjusted);
                    if (adjusted >= 0.0001)
                        threshold = k;
                }
                System.err.println("Threshold=" + threshold);
            }
        }
    }
}
