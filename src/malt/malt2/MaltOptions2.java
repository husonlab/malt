package malt.malt2;

import malt.IMaltOptions;
import megan.parsers.blast.BlastMode;

/**
 * maintains the set of Malt options
 * <p/>
 * Daniel Huson, 8.2014
 */
public class MaltOptions2 implements IMaltOptions {
    public enum MatchOutputFormat {
        BlastTab, BlastText, RMA, SAM;

        public static MatchOutputFormat valueOfIgnoreCase(String label) {
            for (MatchOutputFormat type : values())
                if (label.equalsIgnoreCase(type.toString()))
                    return type;
            return null;
        }
    }

    public enum ProfileOutputFormat {
        MEGAN, TSV;

        public static ProfileOutputFormat valueOfIgnoreCase(String label) {
            for (ProfileOutputFormat type : values())
                if (label.equalsIgnoreCase(type.toString()))
                    return type;
            return null;
        }
    }

    public enum TaxonomyAlgorithms {
        NaiveLCA, WeightedLCA;

        public static TaxonomyAlgorithms valueOfIgnoreCase(String label) {
            for (TaxonomyAlgorithms type : values())
                if (label.equalsIgnoreCase(type.toString()))
                    return type;
            return null;
        }
    }

    private int maxAlignmentsPerQuery = 25;
    private int maxAlignmentsPerReference = 1;

    private double minBitScore = 50;
    private double maxExpected = 1;
    private double minProportionIdentity = 0;
    private float minComplexity = 0.0f;

    private boolean doForward = true;
    private boolean doReverse = true;

    private BlastMode mode;

    private MatchOutputFormat matchOutputFormat = MatchOutputFormat.RMA;

    private boolean generateSamSQLines;

    private boolean gzipMatches = true;
    private boolean gzipOrganisms = true;
    private boolean gzipAlignedReads = true;
    private boolean gzipUnalignedReads = true;

    private double topPercentLCA = 10;
    private float minSupportPercentLCA = 0.01f;
    private int minSupportLCA = 0;

    private int numberOfThreads = 8;
    private int numberOfChunks = 1;
    private int queryBatchSize = 1000000;

    private boolean useReplicateQueryCaching = false;

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

    public int getNumberOfChunks() {
        return numberOfChunks;
    }

    public void setNumberOfChunks(int numberOfChunks) {
        this.numberOfChunks = numberOfChunks;
    }

    @Override
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

    public double getTopPercentLCA() {
        return topPercentLCA;
    }

    public void setTopPercentLCA(double topPercentLCA) {
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

    public boolean isUseReplicateQueryCaching() {
        return useReplicateQueryCaching;
    }

    public void setUseReplicateQueryCaching(boolean useReplicateQueryCaching) {
        this.useReplicateQueryCaching = useReplicateQueryCaching;
    }

    public float getMinComplexity() {
        return minComplexity;
    }

    public void setMinComplexity(float minComplexity) {
        this.minComplexity = minComplexity;
    }

    /**
     * gets the number of queries to be processed per batch
     *
     * @return batch size
     */
    public int getQueryBatchSize() {
        return queryBatchSize;
    }

    /**
     * sets the query batch size
     *
     * @param queryBatchSize
     */
    public void setQueryBatchSize(int queryBatchSize) {
        this.queryBatchSize = queryBatchSize;
    }

    /**
     * get the appropriate suffix for a matches output file
     *
     * @return suffix
     */
    public String getMatchesOutputSuffix() {
        if (matchOutputFormat == MatchOutputFormat.RMA)
            return "." + mode.name().toLowerCase() + ".rma";
        if (matchOutputFormat == MatchOutputFormat.SAM)
            return "." + mode.name().toLowerCase() + ".sam";
        else if (matchOutputFormat == MatchOutputFormat.BlastTab)
            return "." + mode.name().toLowerCase() + ".tab";
        else return "." + mode.name().toLowerCase();
    }

    public boolean isGenerateSamSQLines() {
        return generateSamSQLines;
    }

    public void setGenerateSamSQLines(boolean generateSamSQLines) {
        this.generateSamSQLines = generateSamSQLines;
    }
}
