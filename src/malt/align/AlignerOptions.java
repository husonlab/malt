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

import jloda.util.Pair;
import megan.parsers.blast.BlastMode;

/**
 * all options required by an aligner
 * Daniel Huson, 8.2014
 */
public class AlignerOptions {
    public enum AlignmentMode {Local, SemiGlobal}

    private AlignmentMode alignmentType = AlignmentMode.Local;

    private int minSeedIdentities = 0;
    private int ungappedXDrop = 0;
    private int ungappedMinRawScore = 0;

    private int gapOpenPenalty = 7;
    private int gapExtensionPenalty = 3;
    private int matchScore = 2;
    private int mismatchScore = -3;
    private int band = 4;

    private boolean referenceIsDNA = true;

    // two values for computing blast statistics:
    private double lambda = 0.625;
    private double lnK = -0.89159811928378356416921953633132;

    private IScoringMatrix scoringMatrix;

    private long referenceDatabaseLength = 100000;

    private boolean samSoftClipping = false;


    public AlignmentMode getAlignmentType() {
        return alignmentType;
    }

    public void setAlignmentType(AlignmentMode alignmentType) {
        this.alignmentType = alignmentType;
    }

    public void setAlignmentType(String alignmentType) {
        setAlignmentType(AlignmentMode.valueOf(alignmentType));
    }

    public int getGapOpenPenalty() {
        return gapOpenPenalty;
    }

    public void setGapOpenPenalty(int gapOpenPenalty) {
        this.gapOpenPenalty = gapOpenPenalty;
    }

    public int getGapExtensionPenalty() {
        return gapExtensionPenalty;
    }

    public void setGapExtensionPenalty(int gapExtensionPenalty) {
        this.gapExtensionPenalty = gapExtensionPenalty;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(int matchScore) {
        this.matchScore = matchScore;
    }

    public int getMismatchScore() {
        return mismatchScore;
    }

    public void setMismatchScore(int mismatchScore) {
        this.mismatchScore = mismatchScore;
    }

    public int getBand() {
        return band;
    }

    public void setBand(int band) {
        this.band = band;
    }

    public long getReferenceDatabaseLength() {
        return referenceDatabaseLength;
    }

    public void setReferenceDatabaseLength(long referenceDatabaseLength) {
        this.referenceDatabaseLength = referenceDatabaseLength;
    }

    public IScoringMatrix getScoringMatrix() {
        return scoringMatrix;
    }

    public void setScoringMatrix(IScoringMatrix scoringMatrix) {
        this.scoringMatrix = scoringMatrix;
    }

    public void setLambdaAndK(Pair<Double, Double> lambdaAndK) {
        System.err.println("BLAST statistics parameters: lambda=" + lambdaAndK.get1() + " k=" + lambdaAndK.get2());
        lambda = lambdaAndK.get1();
        lnK = Math.log(lambdaAndK.get2());
    }

    public void setK(double K) {
        this.lnK = Math.log(K);
    }

    public double getK() {
        return Math.exp(lnK);
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public double getLambda() {
        return lambda;
    }

    public double getLnK() {
        return lnK;
    }

    public boolean isReferenceIsDNA() {
        return referenceIsDNA;
    }

    public void setReferenceIsDNA(boolean referenceIsDNA) {
        this.referenceIsDNA = referenceIsDNA;
    }

    public int getMinSeedIdentities(final BlastMode mode) {
        if (minSeedIdentities == 0) {
            switch (mode) {
                case BlastP:
                case BlastX:
                    return 10;
                case BlastN:
                    return 0; // no need to set this, because BlastN seeds are always completely identical
            }
        }
        return minSeedIdentities;
    }

    public void setMinSeedIdentities(int minSeedIdentities) {
        this.minSeedIdentities = minSeedIdentities;
    }

    public int getUngappedXDrop(final BlastMode mode) {
        if (ungappedXDrop == 0) {
            switch (mode) {
                case BlastP:
                case BlastX:
                    return 20;
                case BlastN:
                    return 8; // todo: need to figure out best default
            }
        }
        return ungappedXDrop;
    }

    public void setUngappedXDrop(int ungappedXDrop) {
        this.ungappedXDrop = ungappedXDrop;
    }

    public int getUngappedMinRawScore(final BlastMode mode) {
        if (ungappedMinRawScore == 0) {
            switch (mode) {
                case BlastP:
                case BlastX:
                    return 60;
                case BlastN:
                    return 60;  // todo: need to figure out best default
            }
        }
        return ungappedMinRawScore;
    }

    public void setUngappedMinRawScore(int ungappedMinRawScore) {
        this.ungappedMinRawScore = ungappedMinRawScore;
    }

    public boolean isSamSoftClipping() {
        return samSoftClipping;
    }

    public void setSamSoftClipping(boolean samSoftClipping) {
        this.samSoftClipping = samSoftClipping;
    }
}
