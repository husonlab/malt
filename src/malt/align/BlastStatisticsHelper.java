/*
 *  BlastStatisticsHelper.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.align;

import jloda.util.Basic;
import jloda.util.Pair;

import java.io.IOException;

/**
 * blast statistics helper
 * Daniel Huson, 8.2014
 */
public class BlastStatisticsHelper {
    private long referenceLength;
    private final double lnK;
    private final double k;
    private final double lambda;
    private final double LN2 = (float) Math.log(2);


    /**
     * lookup table, source: Blast book, appendix C
     */
    private static String[] table = new String[]
            {
                    "Matrix open extension lambda K H",
                    "BLOSUM80	32767	32767	0.343	0.177	0.657",
                    "BLOSUM80	25	2	0.342	0.170	0.660",
                    "BLOSUM80	13	2	0.336	0.150	0.570",
                    "BLOSUM80	9	2	0.319	0.110	0.420",
                    "BLOSUM80	8	2	0.308	0.0900	0.350",
                    "BLOSUM80	7	2	0.293	0.0700	0.270",
                    "BLOSUM80	6	2	0.268	0.0450	0.190",
                    "BLOSUM80	11	1	0.314	0.0950	0.350",
                    "BLOSUM80	10	1	0.299	0.0710	0.270",
                    "BLOSUM80	9	1	0.279	0.0480	0.200",
                    "BLOSUM62	32767	32767	0.318	0.134	0.401",
                    "BLOSUM62	11	2	0.297	0.0820	0.270",
                    "BLOSUM62	10	2	0.291	0.0750	0.230",
                    "BLOSUM62	9	2	0.279	0.0580	0.190",
                    "BLOSUM62	8	2	0.264	0.0450	0.150",
                    "BLOSUM62	7	2	0.239	0.0270	0.100",
                    "BLOSUM62	6	2	0.201	0.0120	0.0610",
                    "BLOSUM62	13	1	0.292	0.0710	0.230",
                    "BLOSUM62	12	1	0.283	0.0590	0.190",
                    "BLOSUM62	11	1	0.267	0.0410	0.140",
                    "BLOSUM62	10	1	0.243	0.0240	0.100",
                    "BLOSUM62	9	1	0.206	0.0100	0.0520",
                    "BLOSUM50	32767	32767	0.232	0.112	0.336",
                    "BLOSUM50	13	3	0.212	0.0630	0.190",
                    "BLOSUM50	12	3	0.206	0.0550	0.170",
                    "BLOSUM50	11	3	0.197	0.0420	0.140",
                    "BLOSUM50	10	3	0.186	0.0310	0.110",
                    "BLOSUM50	9	3	0.172	0.0220	0.0820",
                    "BLOSUM50	16	2	0.215	0.0660	0.200",
                    "BLOSUM50	15	2	0.210	0.0580	0.170",
                    "BLOSUM50	14	2	0.202	0.0450	0.140",
                    "BLOSUM50	13	2	0.193	0.0350	0.120",
                    "BLOSUM50	12	2	0.181	0.0250	0.0950",
                    "BLOSUM50	19	1	0.212	0.0570	0.180",
                    "BLOSUM50	18	1	0.207	0.0500	0.150",
                    "BLOSUM50	17	1	0.198	0.0370	0.120",
                    "BLOSUM50	16	1	0.186	0.0250	0.100",
                    "BLOSUM50	15	1	0.171	0.0150	0.0630",
                    "BLOSUM45	32767	32767	0.229	0.0924	0.251",
                    "BLOSUM45	13	3	0.207	0.0490	0.140",
                    "BLOSUM45	12	3	0.199	0.0390	0.110",
                    "BLOSUM45	11	3	0.190	0.0310	0.0950",
                    "BLOSUM45	10	3	0.179	0.0230	0.0750",
                    "BLOSUM45	16	2	0.210	0.0510	0.140",
                    "BLOSUM45	15	2	0.203	0.0410	0.120",
                    "BLOSUM45	14	2	0.195	0.0320	0.100",
                    "BLOSUM45	13	2	0.185	0.0240	0.0840",
                    "BLOSUM45	12	2	0.171	0.0160	0.0610",
                    "BLOSUM45	19	1	0.205	0.0400	0.110",
                    "BLOSUM45	18	1	0.198	0.0320	0.100",
                    "BLOSUM45	17	1	0.189	0.0240	0.0790",
                    "BLOSUM45	16	1	0.176	0.0160	0.0630",
                    "BLOSUM90	32767	32767	0.335	0.190	0.755",
                    "BLOSUM90	9	2	0.310	0.120	0.460",
                    "BLOSUM90	8	2	0.300	0.0990	0.390",
                    "BLOSUM90	7	2	0.283	0.0720	0.300",
                    "BLOSUM90	6	2	0.259	0.0480	0.220",
                    "BLOSUM90	11	1	0.302	0.0930	0.390",
                    "BLOSUM90	10	1	0.290	0.0750	0.280",
                    "BLOSUM90	9	1	0.265	0.0440	0.200"
            };


    /**
     * constructor
     *
     * @param referenceLength
     * @param blosumName
     * @param gapOpenPenalty
     * @param gapExtensionPenalty
     */
    public BlastStatisticsHelper(long referenceLength, String blosumName, int gapOpenPenalty, int gapExtensionPenalty) throws IOException {
        this.referenceLength = referenceLength;
        Pair<Double, Double> pair = lookupLambdaAndK(blosumName, gapOpenPenalty, gapExtensionPenalty);
        this.lambda = pair.get1();
        this.k = pair.get2();
        this.lnK = (float) Math.log(k);
        System.err.println("Blast-stats: matrix=" + blosumName + " gapOpen=" + gapOpenPenalty + " gapExtend=" + gapExtensionPenalty + " lambda=" + getLambda() + " k=" + getK());
    }

    /**
     * constructor
     *
     * @param referenceLength
     * @param k
     * @param lambda
     */
    public BlastStatisticsHelper(long referenceLength, float k, float lambda) {
        this.referenceLength = referenceLength;
        this.k = k;
        this.lnK = (float) Math.log(k);
        this.lambda = lambda;
    }

    /**
     * set the reference length
     *
     * @param referenceLength
     */
    public void setReferenceLength(long referenceLength) {
        this.referenceLength = referenceLength;
    }

    /**
     * get the bit score
     *
     * @param alignmentScore
     * @return bit score
     */
    public double getBitScore(int alignmentScore) {
        return (lambda * alignmentScore - lnK) / LN2;
    }

    /**
     * get the e-value
     *
     * @param queryLength
     * @param alignmentScore
     * @return e-evalue
     */
    public double getExpect(int queryLength, int alignmentScore) {
        return k * referenceLength * queryLength * Math.exp(-lambda * alignmentScore);
    }

    /**
     * get  blast's k value
     *
     * @return k
     */
    public double getK() {
        return k;
    }

    /**
     * get blast's lambda value
     *
     * @return lambda
     */
    public double getLambda() {
        return lambda;
    }

    /**
     * lookup the blast K and Lambda values for a given setting
     *
     * @param blosumName
     * @param gapOpen
     * @param gapExtend
     * @return k and lambda
     * @throws IOException
     */
    public static Pair<Double, Double> lookupLambdaAndK(String blosumName, int gapOpen, int gapExtend) throws IOException {
        blosumName = blosumName.toUpperCase();
        for (String line : table) {
            if (line.startsWith(blosumName)) {
                String[] tokens = line.split("\t");
                if (tokens.length == 6) {
                    int gop = Integer.parseInt(tokens[1]);
                    int gep = Integer.parseInt(tokens[2]);
                    if (gop == gapOpen && gep == gapExtend) {
                        return new Pair<>(Double.parseDouble(tokens[3]), Double.parseDouble(tokens[4]));

                    }

                }
            }
        }
        System.err.println("Known combinations of BLOSUM matrices and gap penalties:");
        System.err.println(Basic.toString(table, "\n"));
        throw new IOException("Can't determine BLAST statistics for given combination of BLOSUM matrix and gap penalties");
    }
}
