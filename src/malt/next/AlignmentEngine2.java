package malt.next;

import malt.ITextProducer;
import malt.align.AlignerOptions;
import malt.sequence.ISeedExtractor;
import malt.sequence.SeedShape2;
import malt.sequence.SequenceEncoder;

import java.util.Iterator;

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
    private final ISeedExtractor seedExtractor;
    private final SequenceEncoder sequenceEncoder;
    private final SeedStore refSeeds;
    private final SeedShape2[] allSeedShapes;
    private final int seedId;
    private final int seedLength;
    private final int seedWeight;
    private final MatchStore matchStore;

    private final BandedAligner2 aligner;

    private final MaltOptions2.MatchOutputFormat matchOutputFormat;
    private final double minBitScore;
    private final double maxExpected;
    private final double minProportionIdentity;
    private final int minRawScore;

    private boolean samSoftClipping;

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
                            final ReferenceStore refStore, final SeedStore refSeeds, final SeedShape2 seedShape, final SeedShape2[] allSeedShapes, final MatchStore matchStore, final AlignerStats.JobStats jobStats) {
        this.scoringMatrix = alignerOptions.getScoringMatrix().getMatrix();
        this.queryStore = queryStore;
        this.querySeeds = querySeeds;
        this.refStore = refStore;
        this.seedExtractor = refStore.getSeedExtractor();
        this.sequenceEncoder = refStore.getSequenceEncoder();
        this.refSeeds = refSeeds;
        this.allSeedShapes = allSeedShapes;
        this.seedId = seedShape.getId();
        this.seedLength = seedShape.getLength();
        this.seedWeight = seedShape.getWeight();
        this.matchStore = matchStore;
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

        final SequenceEncoder sequenceEncoder = refStore.getSequenceEncoder();
        final long[] querySpannedCode = new long[2];
        final long[] refSpannedCode = new long[2];

        while (true) {
            long refSeedCode = refArray[r];
            while (queryArray[q] < refSeedCode) {
                q += 2;
                if (q >= numberOfQuerySeeds)
                    return; // finished all query seeds
            }
            long querySeedCode = queryArray[q];
            while (refArray[r] < querySeedCode) {
                r += 2;
                if (r >= numberOfRefSeeds)
                    return; // finished all ref seeds
            }
            refSeedCode = refArray[r];

            // Now we have queryValue==refCode
            do {

                final int queryId = querySeeds.getSequenceIdForIndex(q + 1);
                final int queryPos = querySeeds.getSequencePosForIndex(q + 1);
                final byte frameRank = querySeeds.getFrameRankForIndex(q + 1);
                final long[] querySequenceCode = queryStore.getSequenceCode(queryId, frameRank);
                final int queryLength = sequenceEncoder.computeLength(querySequenceCode);

                int r2 = r; // use r2 to loop over all coming refCode that equal current queryCode
                int count = 0;
                while (querySeedCode == refArray[r2]) {
                    jobStats.incrementSeedMatches();

                    final int refId = refSeeds.getSequenceIdForIndex(r2 + 1);
                    final int refPos = refSeeds.getSequencePosForIndex(r2 + 1);
                    final long[] refSequenceCode = refStore.getSequenceCode(refId);

                    // we use inline code to determine whether seed and  ungapped alignment score high enough
                    {
                        sequenceEncoder.getSeedSpanCode(seedLength, querySequenceCode, queryPos, querySpannedCode);
                        sequenceEncoder.getSeedSpanCode(seedLength, refSequenceCode, refPos, refSpannedCode);
                        // todo: fix this!
                        int identities = Long.bitCount(querySpannedCode[0] & refSpannedCode[0]) +
                                Long.bitCount(querySpannedCode[1] & refSpannedCode[1]);

                        if (identities >= minSeedIdentities) // protein alignment needs at least this number of identities in seed
                        {
                            jobStats.incrementHasIdentities();
                            // compute seed score:
                            int score = 0;
                            Iterator<Byte> queryIterator = sequenceEncoder.getLetterIterator(querySequenceCode, queryPos);
                            Iterator<Byte> refIterator = sequenceEncoder.getLetterIterator(refSequenceCode, refPos);
                            while (queryIterator.hasNext() && refIterator.hasNext())
                                score += scoringMatrix[queryIterator.next()][refIterator.next()];

                            // extend to left:
                            int bestScore = score;
                            queryIterator = sequenceEncoder.getLetterReverseIterator(querySequenceCode, queryPos - 1);
                            refIterator = sequenceEncoder.getLetterReverseIterator(refSequenceCode, refPos - 1);

                            while (queryIterator.hasNext() && refIterator.hasNext()) {
                                score += scoringMatrix[queryIterator.next()][refIterator.next()];
                                if (score > bestScore)
                                    bestScore = score;
                                else if (score <= bestScore - xDrop)
                                    break;
                            }
                            // extend to right:
                            queryIterator = sequenceEncoder.getLetterIterator(querySequenceCode, queryPos + seedLength);
                            refIterator = sequenceEncoder.getLetterIterator(refSequenceCode, refPos + seedLength);

                            while (queryIterator.hasNext() && refIterator.hasNext()) {
                                score += scoringMatrix[queryIterator.next()][refIterator.next()];
                                if (score > bestScore)
                                    bestScore = score;
                                else if (score <= bestScore - xDrop)
                                    break;
                            }
                            if (bestScore >= minUngappedRawScore) {
                                jobStats.incrementUngappedAlignments();
                                computeAlignment(querySequenceCode, queryId, queryPos, refSequenceCode, refId, refPos, frameRank);
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
                if (q >= queryLength)
                    return;
                querySeedCode = queryArray[q];
            }
            while (querySeedCode == refSeedCode);
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
    public void computeAlignment(final long[] querySequenceCode, final int queryId, final int queryPos,
                                 final long[] refSequenceCode, final int refId, final int refPos, final byte frameRank) {

        final byte[] querySequence = sequenceEncoder.decode(querySequenceCode);
        final byte[] refSequence = sequenceEncoder.decode(refSequenceCode);

        // todo: use codes rather than sequences:
        aligner.computeAlignment(querySequence, refSequence, queryPos, refPos, seedLength);
        // aligner.computeAlignment(querySequenceCode, refSequenceCode, queryPos, refPos, seedLength);

        if (aligner.getRawScore() >= minRawScore) {
            aligner.computeBitScoreAndExpected();

            if (aligner.getBitScore() >= minBitScore && aligner.getExpected() <= maxExpected) {

                jobStats.incrementGoodAlignments();

                if (isLeftmostSeedMatch(querySequenceCode, queryPos, refSequenceCode, refPos, refStore.getMask(refId)))
                    jobStats.incrementLeftmost();
                else
                    return;

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
                                case Text:
                                    text = aligner.getAlignmentText(queryStore, queryId, frameRank);
                                    break;
                                case Tab:
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
     * @param queryCode
     * @param queryPos
     * @param referenceCode
     * @param referencePos
     * @param refMask
     * @return true, if left most
     */
    private boolean isLeftmostSeedMatch(final long[] queryCode, final int queryPos, final long[] referenceCode, final int referencePos, final Mask refMask) {
        for (final SeedShape2 aSeedShape : allSeedShapes) {
            int q = queryPos;
            int r = referencePos;

            if (aSeedShape.getId() >= seedId) { // for seed shapes that come earlier check actual position and not just to the left
                q--;
                r--;
            }

            while (q >= 0 && r >= 0) { // try all left shifts todo: this is ok for semiGlobal alignment, but not for local
                if (refMask == null || !refMask.get(aSeedShape.getId(), r)) {
                    long querySeed = seedExtractor.getSeedCode(aSeedShape.getMask(), seedWeight, queryCode, q, 1);
                    long refSeed = seedExtractor.getSeedCode(aSeedShape.getMask(), seedWeight, referenceCode, r, 2);
                    if (querySeed == refSeed)
                        return false;
                }
                q--;
                r--;
            }
        }
        return true; // leftmost for all seeds
    }
}


