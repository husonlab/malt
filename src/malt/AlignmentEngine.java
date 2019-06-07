/*
 *  AlignmentEngine.java Copyright (C) 2019. Daniel H. Huson GPL
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

import jloda.util.Basic;
import jloda.util.BlastMode;
import malt.align.AlignerOptions;
import malt.align.BandedAligner;
import malt.data.*;
import malt.io.*;
import malt.util.FixedSizePriorityQueue;
import malt.util.Utilities;
import megan.genes.GeneItemAccessor;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * the main alignment engine. This runs in its own thread. It grabs the next read from the read queue and writes
 * the output to the ranked output writer
 * Daniel Huson, 8.2014
 */
public class AlignmentEngine {
    private final int threadNumber; // used for output queuing

    // general data structures:
    private final MaltOptions maltOptions;
    private final ReferencesDBAccess referencesDB;
    private final ReferencesHashTableAccess[] tables;
    private final SeedShape[] seedShapes;

    private final int shift;

    // io:
    private final FastAReader fastAReader;
    private final MaltOptions.MatchOutputFormat matchOutputFormat;
    private final FileWriterRanked matchesWriter;
    private final FileWriterRanked alignedReadsWriter;
    private final FileWriterRanked unalignedReadsWriter;
    private final RMA6Writer rmaWriter;

    private final GeneItemAccessor geneTableAccess;

    // parameters
    private final double minRawScore;
    private final double minBitScore;
    private final double maxExpected;
    private final double percentIdentity;

    // xdrop heuristic:
    private final int xDrop;
    private final int minUngappedRawScore;

    // keep track of all used references:
    private final BitSet alignedReferenceIds;

    // used for stats:
    private long countSequencesProcessed;
    private long countSequencesWithAlignments;
    private long countSeedMatches;
    private long countHashSeedMismatches;
    private long countAlignments;

    // used in inner loop:
    private final FixedSizePriorityQueue<ReadMatch> matchesQueue;
    private final ReadMatch[] recycledMatchesArray;
    private final BandedAligner aligner;
    private final Map<Integer, SeedMatchArray> refIndex2ASeedMatches;

    private final ReadMatch[] readMatchesForRefIndex;

    private SeedMatchArray[] seedArrays;   // used in innerloop to keep track of seedmatches per reference sequence
    private int seedArraysLength = 0;

    static private QuerySequence2MatchesCache querySequence2MatchesCache = null;

    /**
     * construct an instance of the alignment engine. Each instance is run in a separate thread
     */
    AlignmentEngine(final int threadNumber, final MaltOptions maltOptions, AlignerOptions alignerOptions, final ReferencesDBAccess referencesDB,
                    final ReferencesHashTableAccess[] tables, final FastAReader fastAReader,
                    final FileWriterRanked matchesWriter, final RMA6Writer rmaWriter,
                    final FileWriterRanked alignedReadsWriter, final FileWriterRanked unalignedReadsWriter, final GeneItemAccessor geneTableAccess) throws IOException {
        this.threadNumber = threadNumber;
        this.maltOptions = maltOptions;
        this.referencesDB = referencesDB;
        this.tables = tables;
        this.fastAReader = fastAReader;
        this.matchOutputFormat = maltOptions.getMatchOutputFormat();
        this.matchesWriter = matchesWriter;
        this.rmaWriter = rmaWriter;
        this.alignedReadsWriter = alignedReadsWriter;
        this.unalignedReadsWriter = unalignedReadsWriter;
        this.geneTableAccess = geneTableAccess;

        this.shift = maltOptions.getShift();

        this.alignedReferenceIds = (maltOptions.isSparseSAM() ? null : new BitSet());

        seedShapes = new SeedShape[tables.length];
        for (int t = 0; t < tables.length; t++) {
            seedShapes[t] = tables[t].getSeedShape();
        }

        // aligner and parameters
        aligner = new BandedAligner(alignerOptions, maltOptions.getMode());

        minRawScore = aligner.getRawScoreForBitScore(maltOptions.getMinBitScore());
        minBitScore = maltOptions.getMinBitScore();
        maxExpected = maltOptions.getMaxExpected();
        percentIdentity = maltOptions.getMinProportionIdentity();

        // ungapped alignment parameters:
        xDrop = alignerOptions.getUngappedXDrop(maltOptions.getMode());
        minUngappedRawScore = alignerOptions.getUngappedMinRawScore(maltOptions.getMode());

        // data structures used in inner loop:
        matchesQueue = new FixedSizePriorityQueue<>(maltOptions.getMaxAlignmentsPerQuery(), ReadMatch.createComparator());
        recycledMatchesArray = new ReadMatch[maltOptions.getMaxAlignmentsPerQuery()];
        refIndex2ASeedMatches = new HashMap<>(10000, 0.9f);
        readMatchesForRefIndex = new ReadMatch[maltOptions.getMaxAlignmentsPerReference()];
        for (int i = 0; i < readMatchesForRefIndex.length; i++)
            readMatchesForRefIndex[i] = new ReadMatch();

        seedArrays = resizeAndConstructEntries(new SeedMatchArray[0], 1000, maltOptions.getMaxSeedsPerReference());
    }

    /**
     * The main outer loop. Grabs the next input read and determines all possible seed matches. Then calls the inner loop
     */
    void runOuterLoop() {
        try {
            final int maxFramesPerQuery = Utilities.getMaxFramesPerQuery(maltOptions.getMode(), maltOptions.isDoForward(), maltOptions.isDoReverse());

            // setup thread specific data-structure:
            final DataForInnerLoop dataForInnerLoop = new DataForInnerLoop(maltOptions.getMode(), maltOptions.isDoForward(), maltOptions.isDoReverse(), maxFramesPerQuery, tables.length);

            // setup buffers for seeds.
            final byte[][][] seedBytes = new byte[maxFramesPerQuery][tables.length][];
            for (int s = 0; s < maxFramesPerQuery; s++) {
                for (int t = 0; t < tables.length; t++) {
                    seedBytes[s][t] = seedShapes[t].createBuffer(); // shape-specific buffer
                }
            }

            // iterate over all available queries, this method is thread-safe
            final FastARecord query = FastAReader.createFastARecord(1024, isWantQualityValues());
            while (fastAReader.readAsFastA(query)) {
                if (querySequence2MatchesCache != null && querySequence2MatchesCache.contains(query.getSequence(), query.getSequenceLength())) {
                    runInnerLoop(query, 0, null); // query is cached, no need to compute frames etc
                } else {
                    // determine all frames to use:
                    dataForInnerLoop.computeFrames(query.getSequence(), query.getQualityValues(), query.getSequenceLength());

                    // find seed matches for all frames and using all seed tables:
                    int totalSize = 0;
                    for (int s = 0; s < dataForInnerLoop.numberOfFrames; s++) {  // for each frame of query
                        for (int t = 0; t < tables.length; t++) {  // consider each seed table
                            final ReferencesHashTableAccess table = tables[t];
                            final SeedShape seedShape = table.getSeedShape();
                            int top = dataForInnerLoop.frameSequenceLength[s] - seedShape.getLength() + 1;
                            for (int qOffset = 0; qOffset < dataForInnerLoop.frameSequenceLength[s]; qOffset += shift) {  // consider all offsets
                                if (qOffset < top) {
                                    final byte[] seed = seedShape.getSeed(dataForInnerLoop.frameSequence[s], qOffset, seedBytes[s][t]);
                                    totalSize += table.lookup(seed, dataForInnerLoop.frameXTableXSeed2Reference[s][t][qOffset]);
                                } else
                                    dataForInnerLoop.frameXTableXSeed2Reference[s][t][qOffset].setEmpty();
                            }
                        }
                    }
                    // run the inner loop
                    runInnerLoop(query, totalSize, dataForInnerLoop);
                }
            }
        } catch (Exception ex) {
            Basic.caught(ex);
            System.exit(1);  // just die...
        }
    }

    /**
     * run the inner loop. This tries to extend all found seed matches. If caching is used, first tries to find alignments in cache
     */
    private void runInnerLoop(final FastARecord query, final int totalSize, final DataForInnerLoop dataForInnerLoop) throws IOException {
        countSequencesProcessed++;

        // if cache active and query found, use the cached matches:
        ReadMatch[] matchesArray = (querySequence2MatchesCache != null ? querySequence2MatchesCache.get(query.getSequence(), query.getSequenceLength()) : null);
        int numberOfMatches = (matchesArray != null ? matchesArray.length : 0);

        if (matchesArray != null) // found is cache, rescan counts
        {
            if (numberOfMatches > 0) {
                countAlignments += numberOfMatches;
                countSequencesWithAlignments++;
            }
        } else // not found in cache, need to compute...
        {
            if (totalSize > 0) { // have some seeds to look at
                try {
                    // key a list of seed arrays that we reuse and reset here:
                    if (seedArraysLength > 0) {
                        for (int i = 0; i < seedArraysLength; i++) {
                            seedArrays[i].clear();
                        }
                        seedArraysLength = 0;
                    }

                    // determine all the seeds to be used, map each ref-index to its seeds, seeds know which frame of the query was used
                    for (int s = 0; s < dataForInnerLoop.numberOfFrames; s++) {
                        for (int t = 0; t < seedShapes.length; t++) { // for each seed-shape specific hash table
                            for (int qOffset = 0; qOffset < dataForInnerLoop.frameSequenceLength[s]; qOffset += shift) {
                                final Row matchLocations = dataForInnerLoop.frameXTableXSeed2Reference[s][t][qOffset];  // all locations of a particular seed
                                int seedMatchesUsed = 0;

                                for (int a = 0; a < matchLocations.size(); a += 2) {
                                    countSeedMatches++;
                                    final int refIndex = matchLocations.get(a);
                                    final int refOffset = matchLocations.get(a + 1);

                                    // todo: debugging
                                    if (refIndex >= referencesDB.getNumberOfSequences()) {
                                        System.err.println("matchLocations=" + matchLocations.toString());
                                        throw new IOException("refIndex=" + refIndex + ": out of bounds: " + referencesDB.getNumberOfSequences());
                                    }

                                    final byte[] referenceSequence = referencesDB.getSequence(refIndex);

                                    try {
                                        if (seedShapes[t].equalSequences(dataForInnerLoop.frameSequence[s], qOffset, referenceSequence, refOffset)) {
                                            if (seedMatchesUsed++ >= maltOptions.getMaxSeedsPerOffsetPerFrame()) {
                                                break;  // exceeded the maximum number of seeds per frame
                                            }

                                            SeedMatchArray set = refIndex2ASeedMatches.get(refIndex);
                                            if (set == null) {
                                                if (seedArraysLength >= seedArrays.length) {
                                                    //System.err.println("seedArray: " + seedArrays.length + " -> " + (2 * seedArraysLength));
                                                    seedArrays = resizeAndConstructEntries(seedArrays, 2 * seedArraysLength, maltOptions.getMaxSeedsPerReference());
                                                }
                                                set = seedArrays[seedArraysLength++];
                                                refIndex2ASeedMatches.put(refIndex, set);
                                            }
                                            if (set.size() < maltOptions.getMaxSeedsPerReference()) {
                                                set.setNext(qOffset, refOffset, s, seedShapes[t].getLength());
                                                // else System.err.println("SKIPPED");
                                            }
                                        } else
                                            countHashSeedMismatches++;
                                    } catch (Exception ex) {
                                        Basic.caught(ex);
                                    }
                                }
                            }
                        }
                    }

                    // try to align each seed

                    for (Integer refIndex : refIndex2ASeedMatches.keySet()) {
                        SeedMatch previous = null;
                        final SeedMatchArray seedMatches = refIndex2ASeedMatches.get(refIndex);
                        seedMatches.sort();
                        int numberOfReadMatchesForRefIndex = 0;  // we keep a short array of best hits for the given reference index

                        for (int i = 0; i < seedMatches.size(); i++) {
                            SeedMatch seedMatch = seedMatches.get(i);
                            if (!seedMatch.follows(previous)) {   // ignore back-to-back matches
                                // todo: debugging
                                if (refIndex >= referencesDB.getNumberOfSequences()) {
                                    System.err.println("seedMatch=" + seedMatch.toString());
                                    throw new IOException("refIndex=" + refIndex + ": out of bounds: " + referencesDB.getNumberOfSequences());
                                }
                                final byte[] referenceSequence = referencesDB.getSequence(refIndex);
                                final byte[] sequence = dataForInnerLoop.frameSequence[seedMatch.getRank()];
                                int length = dataForInnerLoop.frameSequenceLength[seedMatch.getRank()];

                                if (aligner.quickCheck(sequence, length, referenceSequence, referenceSequence.length, seedMatch.getQueryOffset(), seedMatch.getReferenceOffset())) {

                                    aligner.computeAlignment(sequence, length, referenceSequence, referenceSequence.length, seedMatch.getQueryOffset(), seedMatch.getReferenceOffset(), seedMatch.getSeedLength());

                                    if (aligner.getRawScore() >= minRawScore) {  // have found match with sufficient rawScore
                                        // compute bitscore and expected score
                                        aligner.computeBitScoreAndExpected();

                                        if (aligner.getBitScore() >= minBitScore && aligner.getExpected() <= maxExpected) {
                                            ReadMatch readMatch;
                                            boolean foundPlaceToKeepThisMatch;
                                            boolean incrementedNumberOfReadMatchesForRefIndex = false;

                                            if (readMatchesForRefIndex.length == 1) {  // only allowing one hit per reference...
                                                readMatch = readMatchesForRefIndex[0];
                                                numberOfReadMatchesForRefIndex = 1;
                                                foundPlaceToKeepThisMatch = true;
                                                incrementedNumberOfReadMatchesForRefIndex = true;
                                            } else {  //allow more than one hit
                                                // ensure that this match does not overlap an existing match of same or better quality
                                                boolean overlap = false;
                                                for (int z = 0; z < numberOfReadMatchesForRefIndex; z++) {
                                                    readMatch = readMatchesForRefIndex[z];
                                                    if (readMatch.getBitScore() >= aligner.getBitScore() && readMatch.overlap(aligner.getStartReference(), aligner.getEndReference())) {
                                                        overlap = true;
                                                        break;
                                                    }
                                                }
                                                if (overlap)
                                                    continue;

                                                // keep this match, if array not full:
                                                if (numberOfReadMatchesForRefIndex < readMatchesForRefIndex.length) {
                                                    readMatch = readMatchesForRefIndex[numberOfReadMatchesForRefIndex++];
                                                    foundPlaceToKeepThisMatch = true;
                                                    incrementedNumberOfReadMatchesForRefIndex = true;
                                                } else {  // otherwise replace one with lower rawScore
                                                    foundPlaceToKeepThisMatch = false;
                                                    readMatch = null;
                                                    for (int z = 0; z < numberOfReadMatchesForRefIndex; z++) {
                                                        readMatch = readMatchesForRefIndex[z];
                                                        if (aligner.getBitScore() > readMatch.getBitScore()) {
                                                            foundPlaceToKeepThisMatch = true;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }

                                            if (foundPlaceToKeepThisMatch) {
                                                final byte[] referenceHeader;
                                                if (geneTableAccess == null)
                                                    referenceHeader = referencesDB.getHeader(refIndex);
                                                else {
                                                    int start = aligner.getStartReference();
                                                    if (start == -1) {
                                                        aligner.computeAlignmentByTraceBack();
                                                        start = aligner.getStartReference();
                                                    }
                                                    int end = aligner.getEndReference();
                                                    referenceHeader = geneTableAccess.annotateRefString(Basic.toString(referencesDB.getHeader(refIndex)), refIndex, start, end).getBytes();
                                                    //System.err.println(Basic.toString(referenceHeader));
                                                }

                                                byte[] text = null;
                                                byte[] rma6Text = null;
                                                if (matchesWriter != null) {
                                                    switch (matchOutputFormat) {
                                                        default:
                                                        case Text: {
                                                            text = aligner.getAlignmentText(dataForInnerLoop, seedMatch.getRank());
                                                            break;
                                                        }
                                                        case Tab: {
                                                            text = aligner.getAlignmentTab(dataForInnerLoop, null, referenceHeader, seedMatch.getRank()); // don't pass queryHeader, it is added below
                                                            break;
                                                        }
                                                        case SAM: {
                                                            rma6Text = text = aligner.getAlignmentSAM(dataForInnerLoop, null, query.getSequence(), referenceHeader, seedMatch.getRank()); // don't pass queryHeader, it is added below
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (rmaWriter != null && rma6Text == null) {
                                                    rma6Text = aligner.getAlignmentSAM(dataForInnerLoop, null, query.getSequence(), referenceHeader, seedMatch.getRank()); // don't pass queryHeader, it is added below
                                                }
                                                if (percentIdentity > 0) // need to filter by percent identity. Can't do this earlier because number of matches not known until alignment has been computed
                                                {
                                                    if (text == null && rma6Text == null)  // haven't computed alignment, so number of matches not yet computed
                                                        aligner.computeAlignmentByTraceBack(); // compute number of matches
                                                    if (aligner.getIdentities() < percentIdentity * aligner.getAlignmentLength()) {  // too few identities
                                                        if (incrementedNumberOfReadMatchesForRefIndex)
                                                            numberOfReadMatchesForRefIndex--; // undo increment, won't be saving this match
                                                        continue;
                                                    }
                                                }
                                                readMatch.set(aligner.getBitScore(), refIndex, text, rma6Text, aligner.getStartReference(), aligner.getEndReference());
                                            }
                                            previous = seedMatch;
                                        }
                                    }
                                }
                            }
                        }
                        for (int z = 0; z < numberOfReadMatchesForRefIndex; z++) {
                            matchesQueue.add(readMatchesForRefIndex[z].getCopy());
                        }
                    }
                } finally {
                    // erase the seed sets
                    refIndex2ASeedMatches.clear();
                }
            }

            if (matchesQueue.size() > 0) {
                countAlignments += matchesQueue.size();
                countSequencesWithAlignments++;
                numberOfMatches = matchesQueue.size();
                for (int i = numberOfMatches - 1; i >= 0; i--) {  // places matches into array ordered by descending score
                    recycledMatchesArray[i] = matchesQueue.poll();
                }
                matchesArray = recycledMatchesArray; // we reuse the matches array in the case that we are not using matches cache
            }
            // if use caching, save, even if no matches found!
            if (querySequence2MatchesCache != null) {
                querySequence2MatchesCache.put(query.getSequence(), query.getSequenceLength(), matchesArray, numberOfMatches); // ok to pass matchesArray==null when numberOfMatches==0
            }
        }

        // output the alignments or skip the read (or output on skip, if negative filter...):
        if (numberOfMatches > 0) {
            if (matchesWriter != null) {
                switch (matchOutputFormat) {
                    default:
                    case Text: {
                        byte[][] strings = new byte[3 * numberOfMatches + 1][];
                        strings[0] = BlastTextHelper.makeQueryLine(query);
                        for (int i = 0; i < numberOfMatches; i++) {
                            final ReadMatch readMatch = matchesArray[i];
                            strings[3 * i + 1] = referencesDB.getHeader(readMatch.getReferenceId());
                            strings[3 * i + 2] = String.format("\tLength=%d\n", referencesDB.getSequenceLength(readMatch.getReferenceId())).getBytes();
                            strings[3 * i + 3] = readMatch.getText();
                        }
                        matchesWriter.writeByRank(threadNumber, query.getId(), strings);
                        break;
                    }
                    case SAM:
                    case Tab: {
                        byte[] queryNamePlusTab = BlastTextHelper.getQueryNamePlusTab(query);
                        byte[][] strings = new byte[2 * numberOfMatches][];
                        for (int i = 0; i < numberOfMatches; i++) {
                            ReadMatch readMatch = matchesArray[i];
                            strings[2 * i] = queryNamePlusTab;
                            strings[2 * i + 1] = readMatch.getText();
                        }
                        matchesWriter.writeByRank(threadNumber, query.getId(), strings);
                        break;
                    }
                }
            }
            if (rmaWriter != null) {
                rmaWriter.processMatches(query.getHeaderString(), query.getSequenceString(), matchesArray, numberOfMatches);
            }

            if (alignedReferenceIds != null) {
                for (int i = 0; i < numberOfMatches; i++) {
                    final ReadMatch readMatch = matchesArray[i];
                    alignedReferenceIds.set(readMatch.getReferenceId());
                }
            }

            if (alignedReadsWriter != null) {
                alignedReadsWriter.writeByRank(threadNumber, query.getId(), Utilities.getFirstWordEnsureLeadingGreaterSign(query.getHeader()), Utilities.copy0Terminated(query.getSequence()));
            }
            if (unalignedReadsWriter != null) {
                unalignedReadsWriter.skipByRank(threadNumber, query.getId());
            }
            // matchesQueue.erase();    // not necessary because queue is consumed when building array
        } else {   // no match
            if (matchesWriter != null) {
                switch (matchOutputFormat) {
                    case Text: // report no-hits statement
                        matchesWriter.writeByRank(threadNumber, query.getId(), BlastTextHelper.makeQueryLine(query), BlastTextHelper.NO_HITS);
                        break;
                    default:
                        matchesWriter.skipByRank(threadNumber, query.getId());
                        break;
                }
            }
            if (rmaWriter != null && maltOptions.isSaveUnalignedToRMA()) {
                rmaWriter.processMatches(query.getHeaderString(), query.getSequenceString(), matchesArray, 0);
            }
            if (alignedReadsWriter != null) {
                alignedReadsWriter.skipByRank(threadNumber, query.getId());
            }
            if (unalignedReadsWriter != null) {
                unalignedReadsWriter.writeByRank(threadNumber, query.getId(), Utilities.getFirstWordEnsureLeadingGreaterSign(query.getHeader()), Utilities.copy0Terminated(query.getSequence()));
            }
        }
    }

    /**
     * finish up after outer loop completed
     */
    public void finish() {
    }

    /**
     * compute total sequences processed
     */
    static long getTotalSequencesProcessed(final AlignmentEngine[] alignmentEngines) {
        long total = 0;
        for (AlignmentEngine alignmentEngine : alignmentEngines) {
            total += alignmentEngine.countSequencesProcessed;
        }
        return total;
    }

    /**
     * compute total with alignments
     */
    static long getTotalSequencesWithAlignments(final AlignmentEngine[] alignmentEngines) {
        long total = 0;
        for (AlignmentEngine alignmentEngine : alignmentEngines) {
            total += alignmentEngine.countSequencesWithAlignments;
        }
        return total;
    }

    /**
     * compute total number of alignments
     */
    static long getTotalAlignments(final AlignmentEngine[] alignmentEngines) {
        long total = 0;
        for (AlignmentEngine alignmentEngine : alignmentEngines) {
            total += alignmentEngine.countAlignments;
        }
        return total;
    }

    BitSet getAlignedReferenceIds() {
        return alignedReferenceIds;
    }

    /**
     * resize the array of seed match arrays
     */
    private SeedMatchArray[] resizeAndConstructEntries(SeedMatchArray[] array, int newSize, int maxLength) {
        SeedMatchArray[] result = new SeedMatchArray[newSize];
        for (int i = array.length; i < newSize; i++)
            result[i] = new SeedMatchArray(maxLength);
        System.arraycopy(array, 0, result, 0, Math.min(newSize, array.length));
        return result;
    }

    /**
     * initialize the read sequence 2 matches cache
     */
    static void activateReplicateQueryCaching(int bits) {
        System.err.println("Using replicate query cache (cache size=" + (1 << bits) + ")");
        querySequence2MatchesCache = new QuerySequence2MatchesCache(bits);
    }

    /**
     * report on cache usage, if any
     */
    static void reportStats() {
        if (querySequence2MatchesCache != null)
            querySequence2MatchesCache.reportStats();
    }

    /**
     * do we want to collect and save quality values?
     *
     * @return true, if mode is BLASTN, output format is SAM and input file is fastQ
     */
    private boolean isWantQualityValues() {
        return (maltOptions.getMode() == BlastMode.BlastN &&
                maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.SAM && fastAReader.isFastQ());
    }

    /**
     * an array of seed matches
     */
    class SeedMatchArray {
        int size;
        SeedMatch[] matches;

        SeedMatchArray(int length) {
            matches = SeedMatch.resizeAndConstructEntries(matches, length);
        }

        public int size() {
            return size;
        }

        public SeedMatch get(int i) {
            return matches[i];
        }

        void setNext(int queryOffset, int referenceOffset, int rank, int seedLength) {
            matches[size++].set(queryOffset, referenceOffset, rank, seedLength);
        }

        public void clear() {
            size = 0;
        }

        public void sort() {
            Arrays.sort(matches, 0, size, SeedMatch.getComparator());
        }
    }
}
