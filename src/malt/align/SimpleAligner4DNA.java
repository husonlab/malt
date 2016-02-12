/*
 *  Copyright (C) 2015 Daniel H. Huson
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

package malt.align;

import jloda.util.Basic;
import megan.parsers.blast.BlastMode;
import megan.util.BoyerMoore;

import java.util.Iterator;

/**
 * convenience class for aligning a DNA query into a DNA reference
 * Created by huson on 2/9/16.
 */
public class SimpleAligner4DNA {
    private final AlignerOptions alignerOptions;
    private final BandedAligner bandedAligner;
    private int minRawScore = 1;
    private float minPercentIdentity = 0;


    public SimpleAligner4DNA() {
        alignerOptions = new AlignerOptions();
        alignerOptions.setAlignmentType(AlignerOptions.AlignmentMode.SemiGlobal);
        alignerOptions.setScoringMatrix(new DNAScoringMatrix(alignerOptions.getMatchScore(), alignerOptions.getMismatchScore()));
        bandedAligner = new BandedAligner(alignerOptions, BlastMode.BlastN);
    }

    /**
     * compute a semi-global alignment between the query and the reference
     *
     * @param query
     * @param reference
     * @param queryPos
     * @param refPos
     * @param seedLength
     * @return true, if alignment found
     */
    public boolean computeAlignment(byte[] query, byte[] reference, int queryPos, int refPos, int seedLength) {
        bandedAligner.computeAlignment(query, query.length, reference, reference.length, queryPos, refPos, seedLength);
        return bandedAligner.getRawScore() >= minRawScore && (minPercentIdentity == 0 || bandedAligner.getPercentIdentity() >= minPercentIdentity);
    }

    /**
     * set the parameters
     *
     * @param matchScore
     * @param mismatchScore
     * @param gapOpenPenality
     * @param gapExtensionPenality
     */
    public void setAlignmentParameters(int matchScore, int mismatchScore, int gapOpenPenality, int gapExtensionPenality) {
        alignerOptions.setScoringMatrix(new DNAScoringMatrix(matchScore, mismatchScore));
        alignerOptions.setGapOpenPenalty(gapOpenPenality);
        alignerOptions.setGapExtensionPenalty(gapExtensionPenality);
    }

    /**
     * get the min score to be attained
     *
     * @return
     */
    public int getMinRawScore() {
        return minRawScore;
    }

    /**
     * set the min raw score
     *
     * @param minRawScore
     */
    public void setMinRawScore(int minRawScore) {
        this.minRawScore = minRawScore;
    }

    /**
     * get the min percent identity
     *
     * @return
     */
    public float getMinPercentIdentity() {
        return minPercentIdentity;
    }

    /**
     * set the min identity
     *
     * @param minPercentIdentity
     */
    public void setMinPercentIdentity(float minPercentIdentity) {
        this.minPercentIdentity = minPercentIdentity;
    }

    /**
     * get simple alignment text
     *
     * @return as string
     */
    public String getAlignmentString() {
        return Basic.toString(bandedAligner.getAlignmentSimpleText());
    }

    /**
     * gets a position of the query in the reference, or reference.length if not contained
     *
     * @param query
     * @param reference
     * @param queryMustBeContained
     * @return pos or reference.length
     */
    public int getPositionInReference(byte[] query, byte[] reference, boolean queryMustBeContained) {

        if (getMinPercentIdentity() >= 100) {
            return (new BoyerMoore(query, 127)).search(reference);
        }

        final int k = Math.max(10, (int) (100.0 / (100.0 - minPercentIdentity + 1))); // determine smallest exact match that must be present

        for (int i = 0; i < query.length - k + 1; i++) {
            byte[] queryWord = new byte[k];
            System.arraycopy(query, i, queryWord, 0, k);
            BoyerMoore boyerMoore = new BoyerMoore(queryWord, 127);
            for (Iterator<Integer> it = boyerMoore.iterator(reference); it.hasNext(); ) {
                int refPos = it.next();
                if ((!queryMustBeContained && computeAlignment(query, reference, 0, refPos, k))
                        || (refPos <= reference.length - query.length && computeAlignment(query, reference, 0, refPos, k) && bandedAligner.getAlignmentLength() >= query.length)) {
                    return refPos;
                }
            }
        }
        return reference.length;
    }

    /**
     * check whether the given query is contained with in the given reference at the set level of percent identity
     *
     * @param query
     * @param reference
     * @return true, if query has significant alignment into reference
     */
    public boolean isContained(byte[] query, byte[] reference) {
        return getPositionInReference(query, reference, true) != reference.length;
    }

    /**
     * get the percent identity of the last alignment
     *
     * @return percent identity
     */
    public float getPercentIdentity() {
        return bandedAligner.getPercentIdentity();
    }

    /**
     * test this class
     *
     * @param args
     */
    public static void main(String[] args) {
        final SimpleAligner4DNA simpleAligner4DNA = new SimpleAligner4DNA();

        byte[] reference = "acttgcatcacgactacactgacacggctctttacatcggtatatcgctacacagtcacagactacacgtcacagcat".getBytes();

        //byte[] query="gactgtgtagcgatattaccgatgtaaagagcc".getBytes();
        byte[] query = "ggctctttacatcggtaatatcgctacacagtc".getBytes();

        simpleAligner4DNA.setMinPercentIdentity(90);


        if (simpleAligner4DNA.getPositionInReference(query, reference, false) != reference.length) {
            System.err.println(simpleAligner4DNA.getAlignmentString());
        } else System.err.println("No alignment found");
    }

}
