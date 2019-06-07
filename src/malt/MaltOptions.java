/*
 *  MaltOptions.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt;

import jloda.util.BlastMode;
import malt.data.IAlphabet;

/**
 * maintains the set of Malt options
 * Daniel Huson, 8.2014
 */
public class MaltOptions {
    private String commandLine;

    public enum MatchOutputFormat {
        SAM, Tab, Text;

        public static MatchOutputFormat valueOfIgnoreCase(String label) {
            for (MatchOutputFormat type : values())
                if (label.equalsIgnoreCase(type.toString()))
                    return type;
            return null;
        }
    }

    public enum MemoryMode {load, page, map} // load data into memory, load data in pages on demand, use memory mapping

    private boolean saveUnalignedToRMA;

    private int maxAlignmentsPerQuery = 25;
    private int maxAlignmentsPerReference = 1;

    private double minBitScore = 50;
    private double maxExpected = 1;
    private double minProportionIdentity = 0;

    private boolean doForward = true;
    private boolean doReverse = true;

    private BlastMode mode;

    private MatchOutputFormat matchOutputFormat = MatchOutputFormat.SAM;

    private boolean sparseSAM = false;

    private boolean gzipMatches = true;
    private boolean gzipOrganisms = true;
    private boolean gzipAlignedReads = true;
    private boolean gzipUnalignedReads = true;

    private boolean useWeightedLCA = false;
    private float lcaCoveragePercent = 80.0f;

    private float topPercentLCA = 10;
    private float minSupportPercentLCA = 0.001f;
    private int minSupportLCA = 1;
    private float minPercentIdentityLCA = 0.0f;

    private boolean usePercentIdentityFilterLCA = false;

    private int maxSeedsPerReference = 20;
    private int maxSeedsPerOffsetPerFrame = 100;

    private int shift = 1;
    private int numberOfThreads = 8;

    private IAlphabet queryAlphabet;

    private boolean useReplicateQueryCaching = false;

    private boolean pairedReads = false;

    private String contaminantsFile = "";

    private boolean parseHeaders;


    /**
     * get seed shift step
     *
     * @return shift
     */
    public int getShift() {
        return shift;
    }

    /**
     * set seed shift step
     *
     * @param shift
     */
    public void setShift(int shift) {
        this.shift = shift;
    }

    /**
     * get number of worker threads
     *
     * @return threads
     */
    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    /**
     * set number of worker threads
     *
     * @param numberOfThreads
     */
    public void setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    public int getMaxAlignmentsPerQuery() {
        return maxAlignmentsPerQuery;
    }

    public void setMaxAlignmentsPerQuery(int maxAlignmentsPerQuery) {
        this.maxAlignmentsPerQuery = maxAlignmentsPerQuery;
    }

    public int getMaxAlignmentsPerReference() {
        return maxAlignmentsPerReference;
    }

    public void setMaxAlignmentsPerReference(int maxAlignmentsPerReference) {
        this.maxAlignmentsPerReference = maxAlignmentsPerReference;
    }

    public double getMinBitScore() {
        return minBitScore;
    }

    public void setMinBitScore(double minBitScore) {
        this.minBitScore = minBitScore;
    }

    public double getMaxExpected() {
        return maxExpected;
    }

    public void setMaxExpected(double maxExpected) {
        this.maxExpected = maxExpected;
    }

    public double getMinProportionIdentity() {
        return minProportionIdentity;
    }

    public void setMinProportionIdentity(double minProportionIdentity) {
        this.minProportionIdentity = minProportionIdentity;
    }

    public boolean isDoForward() {
        return doForward;
    }

    public void setDoForward(boolean doForward) {
        this.doForward = doForward;
    }

    public boolean isDoReverse() {
        return doReverse;
    }

    public void setDoReverse(boolean doReverse) {
        this.doReverse = doReverse;
    }

    public int getMaxSeedsPerReference() {
        return maxSeedsPerReference;
    }

    public void setMaxSeedsPerReference(int maxSeedsPerReference) {
        this.maxSeedsPerReference = maxSeedsPerReference;
    }

    public int getMaxSeedsPerOffsetPerFrame() {
        return maxSeedsPerOffsetPerFrame;
    }

    public void setMaxSeedsPerOffsetPerFrame(int maxSeedsPerOffsetPerFrame) {
        this.maxSeedsPerOffsetPerFrame = maxSeedsPerOffsetPerFrame;
    }

    public void setSaveUnalignedToRMA(boolean saveUnalignedToRMA) {
        this.saveUnalignedToRMA = saveUnalignedToRMA;
    }

    public boolean isSaveUnalignedToRMA() {
        return saveUnalignedToRMA;
    }

    public BlastMode getMode() {
        return mode;
    }

    public void setMode(BlastMode mode) {
        this.mode = mode;
    }

    public MatchOutputFormat getMatchOutputFormat() {
        return matchOutputFormat;
    }

    public void setMatchOutputFormat(MatchOutputFormat matchOutputFormat) {
        this.matchOutputFormat = matchOutputFormat;
    }

    public void setMatchOutputFormat(String matchOutputFormat) {
        this.matchOutputFormat = MatchOutputFormat.valueOfIgnoreCase(matchOutputFormat);
    }

    public boolean isGzipMatches() {
        return gzipMatches;
    }

    public void setGzipMatches(boolean gzipMatches) {
        this.gzipMatches = gzipMatches;
    }

    public boolean isGzipOrganisms() {
        return gzipOrganisms;
    }

    public void setGzipOrganisms(boolean gzipOrganisms) {
        this.gzipOrganisms = gzipOrganisms;
    }

    public boolean isGzipAlignedReads() {
        return gzipAlignedReads;
    }

    public void setGzipAlignedReads(boolean gzipAlignedReads) {
        this.gzipAlignedReads = gzipAlignedReads;
    }

    public boolean isGzipUnalignedReads() {
        return gzipUnalignedReads;
    }

    public void setGzipUnalignedReads(boolean gzipUnalignedReads) {
        this.gzipUnalignedReads = gzipUnalignedReads;
    }

    public float getTopPercentLCA() {
        return topPercentLCA;
    }

    public void setTopPercentLCA(float topPercentLCA) {
        this.topPercentLCA = topPercentLCA;
    }

    public int getMinSupportLCA() {
        return minSupportLCA;
    }

    public void setMinSupportLCA(int minSupportLCA) {
        this.minSupportLCA = minSupportLCA;
    }

    public float getMinSupportPercentLCA() {
        return minSupportPercentLCA;
    }

    public void setMinSupportPercentLCA(float minSupportPercentLCA) {
        this.minSupportPercentLCA = minSupportPercentLCA;
    }

    public float getMinPercentIdentityLCA() {
        return minPercentIdentityLCA;
    }

    public void setMinPercentIdentityLCA(float minPercentIdentityLCA) {
        this.minPercentIdentityLCA = minPercentIdentityLCA;
    }

    public IAlphabet getQueryAlphabet() {
        return queryAlphabet;
    }

    public void setQueryAlphabet(IAlphabet queryAlphabet) {
        this.queryAlphabet = queryAlphabet;
    }

    public boolean isUseReplicateQueryCaching() {
        return useReplicateQueryCaching;
    }

    public void setUseReplicateQueryCaching(boolean useReplicateQueryCaching) {
        this.useReplicateQueryCaching = useReplicateQueryCaching;
    }

    public boolean isUseWeightedLCA() {
        return useWeightedLCA;
    }

    public void setUseWeightedLCA(boolean useWeightedLCA) {
        this.useWeightedLCA = useWeightedLCA;
    }

    public float getLcaCoveragePercent() {
        return lcaCoveragePercent;
    }

    public void setLcaCoveragePercent(float lcaCoveragePercent) {
        this.lcaCoveragePercent = lcaCoveragePercent;
    }

    public boolean isPairedReads() {
        return pairedReads;
    }

    public void setPairedReads(boolean pairedReads) {
        this.pairedReads = pairedReads;
    }

    public boolean isUsePercentIdentityFilterLCA() {
        return usePercentIdentityFilterLCA;
    }

    public void setUsePercentIdentityFilterLCA(boolean usePercentIdentityFilterLCA) {
        this.usePercentIdentityFilterLCA = usePercentIdentityFilterLCA;
    }

    /**
     * get the appropriate suffix for a matches output file
     *
     * @return suffix
     */
    public String getMatchesOutputSuffix() {
        if (matchOutputFormat == MatchOutputFormat.SAM)
            return "." + mode.name().toLowerCase() + ".sam";
        else if (matchOutputFormat == MatchOutputFormat.Tab)
            return "." + mode.name().toLowerCase() + ".tab";
        else return "." + mode.name().toLowerCase();
    }

    public boolean isSparseSAM() {
        return sparseSAM;
    }

    public void setSparseSAM(boolean sparseSAM) {
        this.sparseSAM = sparseSAM;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public String getContaminantsFile() {
        return contaminantsFile;
    }

    public void setContaminantsFile(String contaminantsFile) {
        this.contaminantsFile = contaminantsFile;
    }


    public boolean isParseHeaders() {
        return parseHeaders;
    }

    public void setParseHeaders(boolean parseHeaders) {
        this.parseHeaders = parseHeaders;
    }
}
