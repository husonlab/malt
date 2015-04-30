package malt.malt2;

import malt.ITextProducer;
import malt.align.AlignerOptions;
import malt.data.SeedShape;

/**
 * The alignment engine
 * <p/>
 * Daniel Huson, 8.2014
 */
public class AlignmentEngine2 {
    private final int[][] scoringMatrix;
    private final QueryStore queryStore;
    private final SeedStore querySeeds;
    private final ReferenceStore refStore;
    private final SeedStore refSeeds;
    private final SeedShape[] allSeedShapes;
    private final int seedId;
    private final int seedLength;
    private final MatchStore matchStore;

    private final BandedAligner2 aligner;

    private final MaltOptions2.MatchOutputFormat matchOutputFormat;
    private final double minBitScore;
    private final double maxExpected;
    private final double minProportionIdentity;
    private final int minRawScore;

    private final int minSeedIdentities;
    private final int xDrop;
    private final int minUngappedRawScore;

    final int maxOccurrencesRefSeed;

    private final AlignerStats.JobStats jobStats;

    /**
     * constructor
     *
     * @param alignerOptions
     */
    public AlignmentEngine2(final AlignerOptions alignerOptions, final MaltOptions2 maltOptions, int maxRefOccurrencesPerSeed, final QueryStore queryStore, final SeedStore querySeeds,
                            final ReferenceStore refStore, final SeedStore refSeeds, final SeedShape seedShape, final SeedShape[] allSeedShapes, final MatchStore matchStore, final AlignerStats.JobStats jobStats) {
        this.scoringMatrix = alignerOptions.getScoringMatrix().getMatrix();
        this.queryStore = queryStore;
        this.querySeeds = querySeeds;
        this.refStore = refStore;
        this.refSeeds = refSeeds;
        this.allSeedShapes = allSeedShapes;
        this.matchStore = matchStore;
        this.seedLength = seedShape.getLength();
        this.seedId = seedShape.getId();
        this.jobStats = jobStats;

        this.maxOccurrencesRefSeed = maxRefOccurrencesPerSeed;

        aligner = new BandedAligner2(alignerOptions, maltOptions.getMode());

        matchOutputFormat = maltOptions.getMatchOutputFormat();
        minBitScore = maltOptions.getMinBitScore();
        maxExpected = maltOptions.getMaxExpected();
        minProportionIdentity = maltOptions.getMinProportionIdentity();
        minRawScore = aligner.getRawScoreForBitScore(maltOptions.getMinBitScore());

        // ungapped alignment parameters:
        minSeedIdentities = alignerOptions.getMinSeedIdentities(maltOptions.getMode());
        xDrop = alignerOptions.getUngappedXDrop(maltOptions.getMode());
        minUngappedRawScore = alignerOptions.getUngappedMinRawScore(maltOptions.getMode());
        // todo: try using a percentage of the minRawScore

    }

    /**
     * run the alignment engine
     */
    public void run() {
        final int numberOfQuerySeeds = querySeeds.getLength();
        final int numberOfRefSeeds = refSeeds.getLength();

        if (numberOfQuerySeeds == 0 || numberOfRefSeeds == 0)
            return;

        final long[] queryArray = querySeeds.getDataArray();
        final long[] refArray = refSeeds.getDataArray();

        int q = 0;
        int r = 0;

        while (true) {
            long refCode = refArray[r];
            while (queryArray[q] < refCode) { // while query seed < ref seed, catch up
                q += 2;
                if (q >= numberOfQuerySeeds)
                    return; // finished all query seeds
            }
            long queryCode = queryArray[q];
            while (refArray[r] < queryCode) { // while ref seed < query seed, catch up
                r += 2;
                if (r >= numberOfRefSeeds)
                    return; // finished all ref seeds
            }
            refCode = refArray[r];

            while (queryCode == refCode) {
                final int queryId = querySeeds.getSequenceIdForIndex(q + 1);
                final int queryPos = querySeeds.getSequencePosForIndex(q + 1);
                final byte frameRank = querySeeds.getFrameRankForIndex(q + 1);
                final byte[] querySequence = queryStore.getSequence(queryId, frameRank);

                int r2 = r; // use r2 to loop over all coming ref codes that  current queryCode
                int count = 0;
                while (queryCode == refArray[r2]) {
                    jobStats.incrementSeedMatches();

                    final int refId = refSeeds.getSequenceIdForIndex(r2 + 1);
                    final int refPos = refSeeds.getSequencePosForIndex(r2 + 1);
                    final byte[] refSequence = refStore.getSequence(refId);

                    // we use inline code to determine whether seed and ungapped alignment score high enough
                    {
                        // check min number of seed identities:
                        int countIdentities = 0;
                        if (minSeedIdentities > 0) {
                            int qi = queryPos;
                            int qLimit = queryPos + seedLength;
                            int ri = refPos;
                            while (qi < qLimit) {
                                if (querySequence[qi++] == refSequence[ri++])
                                    if (++countIdentities >= minSeedIdentities)
                                        break;
                            }
                        }
                        if (countIdentities >= minSeedIdentities) // protein alignment needs at least this number of identities in seed
                        {
                            jobStats.incrementHasIdentities();

                            if (isLeftmostSeedMatch(querySequence, queryPos, refSequence, refPos, refStore.getMask(refId))) {
                                jobStats.incrementLeftmost();

                                // compute seed score:
                                int score = 0;
                                for (int i = 0; i < seedLength; i++) {
                                    score += scoringMatrix[querySequence[queryPos + i]][refSequence[refPos + i]];
                                }

                                // extend to left:
                                int bestScore = score;
                                int limit = Math.min(queryPos, refPos) + 1;
                                for (int i = 1; i < limit; i++) {
                                    score += scoringMatrix[querySequence[queryPos - i]][refSequence[refPos - i]];
                                    if (score > bestScore)
                                        bestScore = score;
                                    else if (score <= bestScore - xDrop)
                                        break;
                                }
                                // extend to right:
                                limit = Math.min(querySequence.length - queryPos, refSequence.length - refPos);
                                for (int i = seedLength; i < limit; i++) {
                                    score += scoringMatrix[querySequence[queryPos + i]][refSequence[refPos + i]];
                                    if (score > bestScore)
                                        bestScore = score;
                                    else if (score <= bestScore - xDrop)
                                        break;
                                }
                                if (bestScore >= minUngappedRawScore) {
                                    jobStats.incrementUngappedAlignments();
                                    computeAlignment(querySequence, queryId, queryPos, refSequence, refId, refPos, frameRank);
                                }
                            }
                        }
                    }
                    if (++count == maxOccurrencesRefSeed)
                        break;
                    r2 += 2;
                    if (r2 >= numberOfRefSeeds)
                        break; // finished going over all references with same code as current query

                }
                q += 2;
                if (q >= numberOfQuerySeeds)
                    return;
                queryCode = queryArray[q];
            }
        }
    }

    /**
     * attempt to compute an alignment for the given seed match
     *
     * @param queryId
     * @param queryPos
     * @param refId
     * @param refPos
     * @param frameRank
     */
    public void computeAlignment(final byte[] querySequence, final int queryId, final int queryPos,
                                 final byte[] refSequence, final int refId, final int refPos, final byte frameRank) {

        aligner.computeAlignment(querySequence, refSequence, queryPos, refPos, seedLength);

        if (aligner.getRawScore() >= minRawScore) {
            aligner.computeBitScoreAndExpected();

            if (aligner.getBitScore() >= minBitScore && aligner.getExpected() <= maxExpected) {

                jobStats.incrementGoodAlignments();

                if (minProportionIdentity > 0) // need to filter by percent identity. Can't do this earlier because number of matches not known until alignment has been computed
                {
                    aligner.computeAlignmentByTraceBack(); // need to run this so we know now many identities there are
                    if (aligner.getIdentities() < minProportionIdentity * aligner.getAlignmentLength()) {  // too few identities
                        return;
                    }
                }

                // text producer in case we need the alignment
                final ITextProducer textProducer = new ITextProducer() { // this text producer generates the alignment text only if it is really needed
                    private byte[] text;

                    public byte[] getText() {
                        if (text == null) {
                            jobStats.incrementTextProduced();
                            switch (matchOutputFormat) {
                                case BlastText:
                                    text = aligner.getAlignmentText(queryStore, queryId, frameRank);
                                    break;
                                case BlastTab:
                                    text = aligner.getAlignmentTab(queryStore, queryId, frameRank, null, refStore.getHeader(refId));
                                    break;
                                case RMA:
                                case SAM:
                                    text = aligner.getAlignmentSAM(queryStore, queryId, frameRank, null, querySequence, refStore.getHeader(refId)); // don't pass queryHeader, it is added below
                                    break;
                                default:
                                    text = null;
                            }
                        }
                        return text;
                    }
                };

                // save match here
                if (matchStore.addMatch(queryId, refId, aligner.getRawScore(), textProducer))
                    jobStats.incrementAddedAlignments();
            }
        }
    }

    /**
     * determines whether current seed match is left most (over all seed shapes)
     *
     * @param query
     * @param queryPos
     * @param reference
     * @param referencePos
     * @param refMask
     * @return true, if left most
     */
    private boolean isLeftmostSeedMatch(byte[] query, int queryPos, byte[] reference, int referencePos, Mask refMask) {
        for (final SeedShape aSeedShape : allSeedShapes) {
            final int jump = aSeedShape.getJumpToFirstZero();
            int q = queryPos;
            int r = referencePos;

            if (aSeedShape.getId() >= seedId) { // for seed shapes that come earlier check actual position and not just to the left
                q--;
                r--;
            }

            if (aSeedShape.getLength() > seedLength) { // when using multiple seed shapes we might run of the right side of the query or ref
                int over = q + aSeedShape.getLength() - query.length;
                if (over > 0) {
                    q -= over;
                    r -= over;
                }
                over = ((r + aSeedShape.getLength()) - reference.length);
                if (over > 0) {
                    q -= over;
                    r -= over;
                }
            }

            while (q >= 0 && r >= 0) { // try all left shifts todo: this is ok for semiGlobal alignment, but not for local
                if (!aSeedShape.getAlphabet().equal(query[q], reference[r])) {
                    q -= jump; // letters to left of tested seed match not equal, slide to left to put first zero here
                    r -= jump; // slide to left in reference, as well
                    if (q < 0 || r < 0)
                        break; // is leftmost for this seed
                } else if (refMask != null && refMask.get(aSeedShape.getId(), r)) { // masked
                    q--;
                    r--;
                } else {
                    if (aSeedShape.equalSequences(query, q, reference, r))
                        return false; // not leftmost for this seed
                    q--;
                    r--;
                }
            }
        }
        return true; // leftmost for all seeds
    }
}


