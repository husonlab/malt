package malt;

import malt.data.IAlphabet;
import megan.parsers.blast.BlastMode;

/**
 * maintains the set of Malt options
 * Daniel Huson, 8.2014
 */
public class MaltOptions implements IMaltOptions {
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

    private double topPercentLCA = 10;
    private float minSupportPercentLCA = 0.001f;
    private int minSupportLCA = 1;

    private int maxSeedsPerReference = 20;
    private int maxSeedsPerOffsetPerFrame = 100;

    private int shift = 1;
    private int numberOfThreads = 8;

    private IAlphabet queryAlphabet;

    private boolean useReplicateQueryCaching = false;

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

    @Override
    public double getMinBitScore() {
        return minBitScore;
    }

    public void setMinBitScore(double minBitScore) {
        this.minBitScore = minBitScore;
    }

    @Override
    public double getMaxExpected() {
        return maxExpected;
    }

    public void setMaxExpected(double maxExpected) {
        this.maxExpected = maxExpected;
    }

    @Override
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

    @Override
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

    @Override
    public double getTopPercentLCA() {
        return topPercentLCA;
    }

    public void setTopPercentLCA(double topPercentLCA) {
        this.topPercentLCA = topPercentLCA;
    }

    @Override
    public int getMinSupportLCA() {
        return minSupportLCA;
    }

    public void setMinSupportLCA(int minSupportLCA) {
        this.minSupportLCA = minSupportLCA;
    }

    @Override
    public float getMinSupportPercentLCA() {
        return minSupportPercentLCA;
    }

    public void setMinSupportPercentLCA(float minSupportPercentLCA) {
        this.minSupportPercentLCA = minSupportPercentLCA;
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

}
