package malt.next;

import jloda.util.Basic;
import malt.align.AlignerOptions;
import malt.align.ProteinScoringMatrix;
import malt.data.DNA5;
import malt.io.SAMHelper;
import malt.util.ReusableByteBuffer;
import malt.util.Utilities;
import megan.util.BlastMode;

import java.io.IOException;

/**
 * Banded DNA aligner. Does both local and semiGlobal alignment
 * Daniel Huson, 8.2014
 */
public class BandedAligner2 {
    private double lambda = 0.625;
    private double lnK = -0.89159811928378356416921953633132;
    private final static double LN_2 = 0.69314718055994530941723212145818;
    private final static int MINUS_INFINITY = -100000000;

    public static int ALIGNMENT_SEGMENT_LENGTH = 60; // length of alignment segment in text format output
    private final static byte[] MID_TRACK_LEADING_SPACES = "                 ".getBytes(); // spaces used in text format output

    private long referenceDatabaseLength = 10000000;

    private byte[] query;
    private byte[] reference;

    private final int[][] scoringMatrix;
    private final int gapOpenPenalty;
    private final int gapExtensionPenalty;
    private final int band;

    private int rawScore;
    private float bitScore = 0;
    private double expected = 0;

    private final boolean isDNAAlignment;

    private int identities;
    private int mismatches;
    private int gaps;
    private int gapOpens;
    private int alignmentLength;

    private final BlastMode mode;
    private final boolean doSemiGlobal;

    private int refOffset; // needed convert from row to position in reference

    private int startQuery; // first alignment position of query
    private int endQuery = -1;   // last alignment position of query +1
    private int startReference;
    private int endReference;

    private int[][] matrixM;
    private int[][] matrixIRef;
    private int[][] matrixIQuery;

    private byte[][] traceBackM;
    private byte[][] traceBackIRef;
    private byte[][] traceBackIQuery;

    private static final byte DONE = 9;
    private static final byte M_FROM_M = 1;
    private static final byte M_FROM_IRef = 2;
    private static final byte M_FROM_IQuery = 3;
    private static final byte IRef_FROM_M = 4;
    private static final byte IRef_FROM_IRef = 5;
    private static final byte IQuery_FROM_M = 6;
    private static final byte IQuery_FROM_IQuery = 7;

    // number of rows depends only on band width
    private final int rows;
    private final int lastRowToFill;
    private final int middleRow;

    // buffers:
    private byte[] queryTrack = new byte[1000];
    private byte[] midTrack = new byte[1000];
    private byte[] referenceTrack = new byte[1000];

    // last computed alignment
    private byte[][] alignment;


    //private final ByteOutputStream alignmentBuffer = new ByteOutputStream(4096);

    private ReusableByteBuffer alignmentBuffer = new ReusableByteBuffer(10000);

    private int queryPos;
    private int refPos;
    private int seedLength;

    private final boolean samSoftClipping;

    /**
     * constructor
     *
     * @param alignerOptions
     */
    public BandedAligner2(final AlignerOptions alignerOptions, final BlastMode mode) {
        this.scoringMatrix = alignerOptions.getScoringMatrix().getMatrix();
        this.isDNAAlignment = (mode == BlastMode.BlastN);
        this.doSemiGlobal = alignerOptions.getAlignmentType() == AlignerOptions.AlignmentMode.SemiGlobal;

        this.lambda = alignerOptions.getLambda();
        this.lnK = alignerOptions.getLnK();

        this.mode = mode;

        band = alignerOptions.getBand();
        gapOpenPenalty = alignerOptions.getGapOpenPenalty();
        gapExtensionPenalty = alignerOptions.getGapExtensionPenalty();
        referenceDatabaseLength = alignerOptions.getReferenceDatabaseLength();

        rows = 2 * band + 3;
        lastRowToFill = rows - 2;
        middleRow = rows / 2; // half


        matrixM = new int[0][0]; // don't init here, need to initialize properly
        matrixIRef = new int[0][0];
        matrixIQuery = new int[0][0];
        traceBackM = new byte[0][0];
        traceBackIRef = new byte[0][0];
        traceBackIQuery = new byte[0][0];
        // todo: only use one traceback matrix

        samSoftClipping = alignerOptions.isSamSoftClipping();
    }

    /**
     * Computes a banded local or semiGlobal alignment.
     * The raw score is computed.
     *
     * @param query
     * @param reference
     * @param queryPos
     * @param refPos
     */
    public void computeAlignment(byte[] query, byte[] reference, int queryPos, int refPos, int seedLength) {
        this.query = query;
        this.reference = reference;
        this.queryPos = queryPos;
        this.refPos = refPos;
        this.seedLength = seedLength;
        if (doSemiGlobal)
            computeSemiGlobalAlignment();
        else
            computeLocalAlignment();
    }

    /**
     * Performs a banded local alignment and return the raw score.
     */
    private void computeLocalAlignment() {
        alignment = null; // will need to call alignmentByTraceBack to compute this

        startQuery = startReference = 0;
        refOffset = refPos - queryPos - band - 2; // need this to compute index in reference sequence

        final int cols = query.length + 2; // query plus one col before and one after

        final int firstSeedCol = queryPos + 1; // +1 because col=pos+1
        final int lastSeedCol = queryPos + seedLength; // +1 because col=pos+1, but then -1 because want to be last in seed (not first after seed)

        //if (lastSeedCol > query.length)
        //     return; // too long

        // ------- compute score that comes from seed (without first and last member)
        rawScore = 0;
        {
            for (int col = firstSeedCol + 1; col < lastSeedCol; col++) {
                final int refIndex = middleRow + col + refOffset;
                rawScore += scoringMatrix[query[col - 1]][reference[refIndex]];
            }
            if (rawScore <= 0) {
                rawScore = 0;
                return;
            }
        }

        // ------- resize matrices if necessary:
        if (cols >= matrixM.length) {  // all values will be 0
            // resize:
            matrixM = new int[cols][rows];
            matrixIRef = new int[cols][rows];
            matrixIQuery = new int[cols][rows];
            traceBackM = new byte[cols][rows];
            traceBackIRef = new byte[cols][rows];
            traceBackIQuery = new byte[cols][rows];

            // initialize first column:
            for (int r = 1; r < rows; r++) {
                // matrixM[0][r] = matrixIRef[0][r] = matrixIQuery[0][r] = 0;
                traceBackM[0][r] = traceBackIRef[0][r] = traceBackIQuery[0][r] = DONE;
            }
            // initialize the first and last row:
            for (int c = 0; c < cols; c++) {
                // matrixM[c][0] = matrixIRef[c][0] = matrixIQuery[c][0] = matrixM[c][rows - 1] = matrixIRef[c][rows - 1] = matrixIQuery[c][rows - 1] = 0;
                traceBackM[c][0] = traceBackIRef[c][0] = traceBackIQuery[c][0] = traceBackM[c][rows - 1] = traceBackIRef[0][rows - 1] = traceBackIQuery[0][rows - 1] = DONE;
            }
        }


        // ------- fill dynamic programming matrix from 0 to first column of seed:
        {
            final int firstCol = Math.max(1, -refOffset - 2 * band - 1); // the column for which refIndex(firstCol,bottom-to-last row)==0
            if (firstCol > 1) {
                final int prevCol = firstCol - 1;
                final int secondToLastRow = rows - 2;
                traceBackM[prevCol][secondToLastRow] = traceBackIRef[prevCol][secondToLastRow] = traceBackIQuery[prevCol][secondToLastRow] = DONE; // set previous column to done
                matrixM[prevCol][secondToLastRow] = matrixIRef[prevCol][secondToLastRow] = matrixIQuery[prevCol][secondToLastRow] = 0;
            }

            // note that query pos is c-1, because c==0 is before start of query

            for (int col = firstCol; col <= firstSeedCol; col++) {   // we never modify the first column or the first or last row
                for (int row = 1; row <= lastRowToFill; row++) {
                    final int refIndex = row + col + refOffset;

                    if (refIndex == -1) { // in column before reference starts, init
                        traceBackM[col][row] = traceBackIRef[col][row] = traceBackIQuery[col][row] = DONE;
                        matrixM[col][row] = matrixIRef[col][row] = matrixIQuery[col][row] = 0;
                    } else if (refIndex >= 0) //do the actual alignment:
                    {
                        int bestMScore = 0;
                        // match or mismatch
                        {
                            final int s = scoringMatrix[query[col - 1]][reference[refIndex]];

                            int score = matrixM[col - 1][row] + s;
                            if (score > 0) {
                                traceBackM[col][row] = M_FROM_M;
                                bestMScore = score;
                            }
                            score = matrixIRef[col - 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IRef;
                                bestMScore = score;
                            }
                            score = matrixIQuery[col - 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IQuery;
                                bestMScore = score;
                            }
                            if (bestMScore == 0) {
                                traceBackM[col][row] = DONE;
                            }
                            matrixM[col][row] = bestMScore;
                        }

                        // insertion in reference:
                        int bestIRefScore = 0;
                        {
                            int score = matrixM[col][row - 1] - gapOpenPenalty;

                            if (score > bestIRefScore) {
                                traceBackIRef[col][row] = IRef_FROM_M;
                                bestIRefScore = score;
                            }

                            score = matrixIRef[col][row - 1] - gapExtensionPenalty;
                            if (score > bestIRefScore) {
                                bestIRefScore = score;
                                traceBackIRef[col][row] = IRef_FROM_IRef;
                            }
                            if (bestIRefScore == 0) {
                                traceBackIRef[col][row] = DONE;
                            }
                            matrixIRef[col][row] = bestIRefScore;

                        }

                        // insertion in query:
                        int bestIQueryScore = 0;
                        {
                            int score = matrixM[col - 1][row + 1] - gapOpenPenalty;

                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_M;
                            }

                            score = matrixIQuery[col - 1][row + 1] - gapExtensionPenalty;
                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_IQuery;
                            }
                            if (bestIQueryScore == 0) {
                                traceBackIQuery[col][row] = DONE;
                            }
                            matrixIQuery[col][row] = bestIQueryScore;
                        }

                    }
                    // else refIndex < -1

                }
            }
        }

        // ------- fill dynamic programming matrix from end of query to last column of seed:
        {
            final int lastCol = Math.min(query.length + 1, queryPos + reference.length - refPos + 1); // last column, fill upto lastCol-1

            // initial last column:

            for (int row = 1; row < rows; row++) {
                matrixM[lastCol][row] = matrixIRef[lastCol][row] = matrixIQuery[lastCol][row] = 0;
                traceBackM[lastCol][row] = traceBackIRef[lastCol][row] = traceBackIQuery[lastCol][row] = DONE;
            }

            // note that col=pos-1, or pos=col+1, because c==0 is before start of query

            /*
            System.err.println("lastSeedCol: " + lastSeedCol);
            System.err.println("lastCol: " + lastCol);
            System.err.println("lastRowToFill: " + lastRowToFill);
*/

            for (int col = lastCol - 1; col >= lastSeedCol; col--) {   // we never modify the first column or the first or last row
                for (int row = lastRowToFill; row >= 1; row--) {
                    final int refIndex = row + col + refOffset;

                    if (refIndex >= reference.length) { // out of range of the alignment
                        traceBackM[col][row] = traceBackIRef[col][row] = traceBackIQuery[col][row] = DONE;
                        matrixM[col][row] = matrixIRef[col][row] = matrixIQuery[col][row] = 0;
                    } else if (refIndex < reference.length) { // do the actual alignment:
                        int bestMScore = 0;
                        // match or mismatch
                        {
                            final int s = scoringMatrix[query[col - 1]][reference[refIndex]]; // pos in query=col-1

                            int score = matrixM[col + 1][row] + s;
                            if (score > 0) {
                                traceBackM[col][row] = M_FROM_M;
                                bestMScore = score;
                            }
                            score = matrixIRef[col + 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IRef;
                                bestMScore = score;
                            }
                            score = matrixIQuery[col + 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IQuery;
                                bestMScore = score;
                            }
                            if (bestMScore == 0) {
                                traceBackM[col][row] = DONE;
                            }
                            matrixM[col][row] = bestMScore;
                        }

                        // insertion in ref
                        int bestIRefScore = 0;
                        {
                            int score = matrixM[col][row + 1] - gapOpenPenalty;

                            if (score > bestIRefScore) {
                                traceBackIRef[col][row] = IRef_FROM_M;
                                bestIRefScore = score;
                            }

                            score = matrixIRef[col][row + 1] - gapExtensionPenalty;
                            if (score > bestIRefScore) {
                                bestIRefScore = score;
                                traceBackIRef[col][row] = IRef_FROM_IRef;
                            }
                            if (bestIRefScore == 0) {
                                traceBackIRef[col][row] = DONE;
                            }
                            matrixIRef[col][row] = bestIRefScore;

                        }

                        // insertion in query:
                        int bestIQueryScore = 0;
                        {
                            int score = matrixM[col + 1][row - 1] - gapOpenPenalty;

                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_M;
                            }

                            score = matrixIQuery[col + 1][row - 1] - gapExtensionPenalty;
                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_IQuery;
                            }
                            if (bestIQueryScore == 0) {
                                traceBackIQuery[col][row] = DONE;
                            }
                            matrixIQuery[col][row] = bestIQueryScore;
                        }

                    }
                    // else  refIndex >reference.length
                }
            }
        }

        /*
        if (false) {
            {
                System.err.println("queryPos: " + queryPos);
                System.err.println("refPos:    " + refPos);
                System.err.println("seedLen.: " + seedLength);

                System.err.println("Query:");
                System.err.println(Basic.toString(query));
                System.err.println("Reference:");
                System.err.println(Basic.toString(reference));
            }

            {
                System.err.println("SeedScore:   " + rawScore);
                int firstScore = Math.max(Math.max(matrixIQuery[firstSeedCol][middleRow], matrixIRef[firstSeedCol][middleRow]), matrixM[firstSeedCol][middleRow]);
                System.err.println("FirstScore:  " + firstScore);
                int secondScore = Math.max(Math.max(matrixIQuery[lastSeedCol][middleRow], matrixIRef[lastSeedCol][middleRow]), matrixM[lastSeedCol][middleRow]);
                System.err.println("secondScore: " + secondScore);
                System.err.println("totalScore:  " + (rawScore + firstScore + secondScore));
            }
            {
                System.err.println("Matrix M:");
                System.err.println(toString(matrixM, 0, cols, query));
                System.err.println("Matrix IQuery:");
                System.err.println(toString(matrixIQuery, 0, cols, query));
                System.err.println("Matrix IRef:");
                System.err.println(toString(matrixIRef, 0, cols, query));
            }
        }
        */

        rawScore += Math.max(Math.max(matrixIQuery[firstSeedCol][middleRow], matrixIRef[firstSeedCol][middleRow]), matrixM[firstSeedCol][middleRow]);
        rawScore += Math.max(Math.max(matrixIQuery[lastSeedCol][middleRow], matrixIRef[lastSeedCol][middleRow]), matrixM[lastSeedCol][middleRow]);
    }

    /**
     * Performs a banded semi-global alignment.
     */
    private void computeSemiGlobalAlignment() {
        alignment = null; // will need to call alignmentByTraceBack to compute this

        startQuery = startReference = 0;
        refOffset = refPos - queryPos - band - 2; // need this to compute index in reference sequence

        final int cols = query.length + 2; // query plus one col before and one after

        final int firstSeedCol = queryPos + 1; // +1 because col=pos+1
        final int lastSeedCol = queryPos + seedLength; // +1 because col=pos+1, but then -1 because want to be last in seed (not first after seed)

        //if (lastSeedCol > query.length)
        //    return; // too long

        // ------- compute score that comes from seed (without first and last member)
        rawScore = 0;
        {
            for (int col = firstSeedCol + 1; col < lastSeedCol; col++) {
                final int refIndex = middleRow + col + refOffset;
                rawScore += scoringMatrix[query[col - 1]][reference[refIndex]];
            }
            if (rawScore <= 0) {
                rawScore = 0;
                return;
            }
        }

        // ------- resize matrices if necessary:
        if (cols >= matrixM.length) {  // all values will be 0
            // resize:
            matrixM = new int[cols][rows];
            matrixIRef = new int[cols][rows];
            matrixIQuery = new int[cols][rows];
            traceBackM = new byte[cols][rows];
            traceBackIRef = new byte[cols][rows];
            traceBackIQuery = new byte[cols][rows];

            // initialize first column:
            for (int r = 1; r < rows; r++) {
                traceBackM[0][r] = traceBackIRef[0][r] = traceBackIQuery[0][r] = DONE;
                matrixIQuery[0][r] = -gapOpenPenalty;
            }
            // initialize the first and last row:
            for (int c = 0; c < cols; c++) {
                matrixM[c][0] = matrixIRef[c][0] = matrixIQuery[c][0]
                        = matrixM[c][rows - 1] = matrixIRef[c][rows - 1] = matrixIQuery[c][rows - 1]
                        = MINUS_INFINITY; // must never go outside band
            }
        }

        // ------- fill dynamic programming matrix from 0 to first column of seed:
        {
            final int firstCol = Math.max(1, -refOffset - 2 * band - 1); // the column for which refIndex(firstCol,bottom-to-last row)==0
            if (firstCol > 1) {
                final int prevCol = firstCol - 1;
                final int secondToLastRow = rows - 2;
                traceBackM[prevCol][secondToLastRow] = traceBackIRef[prevCol][secondToLastRow] = traceBackIQuery[prevCol][secondToLastRow] = DONE; // set previous column to done
                matrixM[prevCol][secondToLastRow] = matrixIRef[prevCol][secondToLastRow] = matrixIQuery[prevCol][secondToLastRow] = 0;
            }

            // note that query pos is c-1, because c==0 is before start of query

            for (int col = firstCol; col <= firstSeedCol; col++) {   // we never modify the first column or the first or last row
                for (int row = 1; row <= lastRowToFill; row++) {
                    final int refIndex = row + col + refOffset;

                    if (refIndex == -1) { // in column before reference starts, init
                        traceBackM[col][row] = traceBackIRef[col][row] = traceBackIQuery[col][row] = DONE;
                        matrixM[col][row] = 0;
                        matrixIRef[col][row] = matrixIQuery[col][row] = -gapOpenPenalty;
                    } else if (refIndex >= 0) //do the actual alignment:
                    {
                        int bestMScore = Integer.MIN_VALUE;
                        // match or mismatch
                        {
                            final int s = scoringMatrix[query[col - 1]][reference[refIndex]];

                            int score = matrixM[col - 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_M;
                                bestMScore = score;
                            }
                            score = matrixIRef[col - 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IRef;
                                bestMScore = score;
                            }
                            score = matrixIQuery[col - 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IQuery;
                                bestMScore = score;
                            }
                            matrixM[col][row] = bestMScore;
                        }

                        // insertion in reference:
                        int bestIRefScore = Integer.MIN_VALUE;
                        {
                            int score = matrixM[col][row - 1] - gapOpenPenalty;

                            if (score > bestIRefScore) {
                                traceBackIRef[col][row] = IRef_FROM_M;
                                bestIRefScore = score;
                            }

                            score = matrixIRef[col][row - 1] - gapExtensionPenalty;
                            if (score > bestIRefScore) {
                                bestIRefScore = score;
                                traceBackIRef[col][row] = IRef_FROM_IRef;
                            }
                            matrixIRef[col][row] = bestIRefScore;

                        }

                        // insertion in query:
                        int bestIQueryScore = Integer.MIN_VALUE;
                        {
                            int score = matrixM[col - 1][row + 1] - gapOpenPenalty;

                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_M;
                            }

                            score = matrixIQuery[col - 1][row + 1] - gapExtensionPenalty;
                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_IQuery;
                            }
                            matrixIQuery[col][row] = bestIQueryScore;
                        }

                    }
                    // else refIndex < -1

                }
            }
        }

        // ------- fill dynamic programming matrix from end of query to last column of seed:
        {
            final int lastCol = Math.min(query.length + 1, queryPos + reference.length - refPos + 1); // last column, fill upto lastCol-1

            // initial last column:

            for (int row = 1; row < rows - 1; row++) { // no need to init first or last row...
                matrixM[lastCol][row] = 0;
                matrixIRef[lastCol][row] = matrixIQuery[lastCol][row] = -gapOpenPenalty;
                traceBackM[lastCol][row] = traceBackIRef[lastCol][row] = traceBackIQuery[lastCol][row] = DONE;
            }

            // note that col=pos-1, or pos=col+1, because c==0 is before start of query

            /*
            System.err.println("lastSeedCol: " + lastSeedCol);
            System.err.println("lastCol: " + lastCol);
            System.err.println("lastRowToFill: " + lastRowToFill);
            */

            for (int col = lastCol - 1; col >= lastSeedCol; col--) {   // we never modify the first column or the first or last row
                for (int row = lastRowToFill; row >= 1; row--) {
                    final int refIndex = row + col + refOffset;

                    if (refIndex >= reference.length) { // out of range of the alignment
                        traceBackM[col][row] = traceBackIRef[col][row] = traceBackIQuery[col][row] = DONE;
                        matrixM[col][row] = matrixIRef[col][row] = matrixIQuery[col][row] = -gapOpenPenalty;
                    } else if (refIndex < reference.length) { // do the actual alignment:
                        int bestMScore = Integer.MIN_VALUE;
                        // match or mismatch
                        {
                            final int s = scoringMatrix[query[col - 1]][reference[refIndex]]; // pos in query=col-1

                            int score = matrixM[col + 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_M;
                                bestMScore = score;
                            }
                            score = matrixIRef[col + 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IRef;
                                bestMScore = score;
                            }
                            score = matrixIQuery[col + 1][row] + s;
                            if (score > bestMScore) {
                                traceBackM[col][row] = M_FROM_IQuery;
                                bestMScore = score;
                            }
                            matrixM[col][row] = bestMScore;
                        }

                        // insertion in ref
                        int bestIRefScore = Integer.MIN_VALUE;
                        {
                            int score = matrixM[col][row + 1] - gapOpenPenalty;

                            if (score > bestIRefScore) {
                                traceBackIRef[col][row] = IRef_FROM_M;
                                bestIRefScore = score;
                            }

                            score = matrixIRef[col][row + 1] - gapExtensionPenalty;
                            if (score > bestIRefScore) {
                                bestIRefScore = score;
                                traceBackIRef[col][row] = IRef_FROM_IRef;
                            }
                            matrixIRef[col][row] = bestIRefScore;
                        }

                        // insertion in query:
                        int bestIQueryScore = Integer.MIN_VALUE;
                        {
                            int score = matrixM[col + 1][row - 1] - gapOpenPenalty;

                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_M;
                            }

                            score = matrixIQuery[col + 1][row - 1] - gapExtensionPenalty;
                            if (score > bestIQueryScore) {
                                bestIQueryScore = score;
                                traceBackIQuery[col][row] = IQuery_FROM_IQuery;
                            }
                            matrixIQuery[col][row] = bestIQueryScore;
                        }
                    }
                    // else  refIndex >reference.length
                }
            }
        }

        /*
        if (false) {
            {
                System.err.println("queryPos: " + queryPos);
                System.err.println("refPos:    " + refPos);
                System.err.println("seedLen.: " + seedLength);

                System.err.println("Query:");
                System.err.println(Basic.toString(query));
                System.err.println("Reference:");
                System.err.println(Basic.toString(reference));
            }

            {
                System.err.println("SeedScore:   " + rawScore);
                int firstScore = Math.max(Math.max(matrixIQuery[firstSeedCol][middleRow], matrixIRef[firstSeedCol][middleRow]), matrixM[firstSeedCol][middleRow]);
                System.err.println("FirstScore:  " + firstScore);
                int secondScore = Math.max(Math.max(matrixIQuery[lastSeedCol][middleRow], matrixIRef[lastSeedCol][middleRow]), matrixM[lastSeedCol][middleRow]);
                System.err.println("secondScore: " + secondScore);
                System.err.println("totalScore:  " + (rawScore + firstScore + secondScore));
            }
            {
                System.err.println("Matrix M:");
                System.err.println(toString(matrixM, 0, cols, query));
                System.err.println("Matrix IQuery:");
                System.err.println(toString(matrixIQuery, 0, cols, query));
                System.err.println("Matrix IRef:");
                System.err.println(toString(matrixIRef, 0, cols, query));
            }
        }
        */

        rawScore += Math.max(Math.max(matrixIQuery[firstSeedCol][middleRow], matrixIRef[firstSeedCol][middleRow]), matrixM[firstSeedCol][middleRow]);
        rawScore += Math.max(Math.max(matrixIQuery[lastSeedCol][middleRow], matrixIRef[lastSeedCol][middleRow]), matrixM[lastSeedCol][middleRow]);
    }

    /**
     * compute the bit score and expected score from the raw score
     */
    public void computeBitScoreAndExpected() {
        if (rawScore > 0) {
            bitScore = (float) ((lambda * rawScore - lnK) / LN_2);
            expected = referenceDatabaseLength * query.length * Math.pow(2, -bitScore);
        } else {
            bitScore = 0;
            expected = Double.MAX_VALUE;
        }
    }

    /**
     * gets the alignment. Also sets the number of matches, mismatches and gaps
     *
     * @return alignment
     */
    public void computeAlignmentByTraceBack() {
        if (rawScore <= 0) {
            alignment = null;
            return;
        }

        gaps = 0;
        gapOpens = 0;
        identities = 0;
        mismatches = 0;

        // get first part of alignment:
        int length = 0;
        {
            int r = middleRow;
            int c = queryPos + 1;

            byte[][] traceBack;
            traceBack = traceBackM;
            if (matrixIRef[c][r] > matrixM[c][r]) {
                traceBack = traceBackIRef;
                if (matrixIQuery[c][r] > matrixIRef[c][r])
                    traceBack = traceBackIQuery;
            } else if (matrixIQuery[c][r] > matrixM[c][r])
                traceBack = traceBackIQuery;

            loop:
            while (true) {
                int refIndex = r + c + refOffset;

                switch (traceBack[c][r]) {
                    case DONE:
                        startQuery = c;
                        startReference = r + c + refOffset + 1;
                        break loop;
                    case M_FROM_M:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = reference[refIndex];
                        if (queryTrack[length] == referenceTrack[length]) {
                            if (isDNAAlignment)
                                midTrack[length] = '|';
                            else
                                midTrack[length] = queryTrack[length];
                            identities++;
                        } else {
                            if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                                midTrack[length] = ' ';
                            else
                                midTrack[length] = '+';
                            mismatches++;
                        }
                        c--;
                        traceBack = traceBackM;
                        break;
                    case M_FROM_IRef:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = reference[refIndex];
                        if (queryTrack[length] == referenceTrack[length]) {
                            if (isDNAAlignment)
                                midTrack[length] = '|';
                            else
                                midTrack[length] = queryTrack[length];
                            identities++;
                        } else {
                            if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                                midTrack[length] = ' ';
                            else
                                midTrack[length] = '+';
                        }
                        c--;
                        traceBack = traceBackIRef;
                        break;
                    case M_FROM_IQuery:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = reference[refIndex];
                        if (queryTrack[length] == referenceTrack[length]) {
                            if (isDNAAlignment)
                                midTrack[length] = '|';
                            else
                                midTrack[length] = queryTrack[length];
                            identities++;
                        } else {
                            if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                                midTrack[length] = ' ';
                            else
                                midTrack[length] = '+';
                        }
                        c--;
                        traceBack = traceBackIQuery;
                        break;
                    case IRef_FROM_M:
                        queryTrack[length] = '-';
                        referenceTrack[length] = reference[refIndex];
                        midTrack[length] = ' ';
                        r--;
                        traceBack = traceBackM;
                        gaps++;
                        gapOpens++;
                        break;
                    case IRef_FROM_IRef:
                        queryTrack[length] = '-';
                        referenceTrack[length] = reference[refIndex];
                        midTrack[length] = ' ';
                        r--;
                        traceBack = traceBackIRef;
                        gaps++;
                        break;
                    case IQuery_FROM_M:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = '-';
                        midTrack[length] = ' ';
                        c--;
                        r++;
                        traceBack = traceBackM;
                        gaps++;
                        gapOpens++;
                        break;
                    case IQuery_FROM_IQuery:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = '-';
                        midTrack[length] = ' ';
                        c--;
                        r++;
                        traceBack = traceBackIQuery;
                        gaps++;
                        break;
                    default:
                        throw new RuntimeException("Undefined trace-back state: " + traceBack[c][r]);
                }
                if (queryTrack[length] == '-' && referenceTrack[length] == '-')
                    System.err.println("gap-gap at: " + length);

                if (++length >= queryTrack.length) {
                    queryTrack = grow(queryTrack);
                    midTrack = grow(midTrack);
                    referenceTrack = grow(referenceTrack);
                }
            } // end of loop

            reverse(queryTrack, length);
            reverse(midTrack, length);
            reverse(referenceTrack, length);
        }

        // get second part of alignment:
        {
            for (int i = 1; i < seedLength - 1; i++) {
                queryTrack[length] = query[queryPos + i];
                referenceTrack[length] = reference[refPos + i];
                if (queryTrack[length] == referenceTrack[length]) {
                    if (isDNAAlignment)
                        midTrack[length] = '|';
                    else
                        midTrack[length] = queryTrack[length];
                    identities++;
                } else {
                    if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                        midTrack[length] = ' ';
                    else
                        midTrack[length] = '+';
                    mismatches++;
                }
                if (++length >= queryTrack.length) {
                    queryTrack = grow(queryTrack);
                    midTrack = grow(midTrack);
                    referenceTrack = grow(referenceTrack);
                }
            }
        }

        // get third part of alignment:
        {
            int r = middleRow;
            int c = queryPos + seedLength; // +1 because col=pos+1, but -1 because want to be in last position of seed

            byte[][] traceBack;
            traceBack = traceBackM;
            if (matrixIRef[c][r] > matrixM[c][r]) {
                traceBack = traceBackIRef;
                if (matrixIQuery[c][r] > matrixIRef[c][r])
                    traceBack = traceBackIQuery;
            } else if (matrixIQuery[c][r] > matrixM[c][r])
                traceBack = traceBackIQuery;

            loop:
            while (true) {
                int refIndex = r + c + refOffset;

                switch (traceBack[c][r]) {
                    case DONE:
                        endQuery = c;
                        endReference = r + c + refOffset + 1;
                        break loop;
                    case M_FROM_M:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = reference[refIndex];
                        if (queryTrack[length] == referenceTrack[length]) {
                            if (isDNAAlignment)
                                midTrack[length] = '|';
                            else
                                midTrack[length] = queryTrack[length];
                            identities++;
                        } else {
                            if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                                midTrack[length] = ' ';
                            else
                                midTrack[length] = '+';
                            mismatches++;
                        }
                        c++;
                        traceBack = traceBackM;
                        break;
                    case M_FROM_IRef:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = reference[refIndex];
                        if (queryTrack[length] == referenceTrack[length]) {
                            if (isDNAAlignment)
                                midTrack[length] = '|';
                            else
                                midTrack[length] = queryTrack[length];
                            identities++;
                        } else {
                            if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                                midTrack[length] = ' ';
                            else
                                midTrack[length] = '+';
                        }
                        c++;
                        traceBack = traceBackIRef;
                        break;
                    case M_FROM_IQuery:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = reference[refIndex];
                        if (queryTrack[length] == referenceTrack[length]) {
                            if (isDNAAlignment)
                                midTrack[length] = '|';
                            else
                                midTrack[length] = queryTrack[length];
                            identities++;
                        } else {
                            if (isDNAAlignment || scoringMatrix[queryTrack[length]][referenceTrack[length]] <= 0)
                                midTrack[length] = ' ';
                            else
                                midTrack[length] = '+';
                        }
                        c++;
                        traceBack = traceBackIQuery;
                        break;
                    case IRef_FROM_M:
                        queryTrack[length] = '-';
                        referenceTrack[length] = reference[refIndex];
                        midTrack[length] = ' ';
                        r++;
                        traceBack = traceBackM;
                        gaps++;
                        gapOpens++;
                        break;
                    case IRef_FROM_IRef:
                        queryTrack[length] = '-';
                        referenceTrack[length] = reference[refIndex];
                        midTrack[length] = ' ';
                        r++;
                        traceBack = traceBackIRef;
                        gaps++;
                        break;
                    case IQuery_FROM_M:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = '-';
                        midTrack[length] = ' ';
                        c++;
                        r--;
                        traceBack = traceBackM;
                        gaps++;
                        gapOpens++;
                        break;
                    case IQuery_FROM_IQuery:
                        queryTrack[length] = query[c - 1];
                        referenceTrack[length] = '-';
                        midTrack[length] = ' ';
                        c++;
                        r--;
                        traceBack = traceBackIQuery;
                        gaps++;
                        break;
                    default: {
                        throw new RuntimeException("Undefined trace-back state: " + traceBack[c][r]);
                    }
                }
                if (queryTrack[length] == '-' && referenceTrack[length] == '-')
                    System.err.println("gap-gap at: " + length);

                if (++length >= queryTrack.length) {
                    queryTrack = grow(queryTrack);
                    midTrack = grow(midTrack);
                    referenceTrack = grow(referenceTrack);
                }
            } // end of loop
        }
        alignmentLength = length;
        alignment = new byte[][]{copy(queryTrack, length), copy(midTrack, length), copy(referenceTrack, length)};
    }

    public int getStartQuery() {
        return startQuery;
    }

    public int getEndQuery() {
        return endQuery;
    }

    public int getStartReference() {
        return startReference;
    }

    public int getEndReference() {
        return endReference;
    }

    public int getGaps() {
        return gaps;
    }

    public int getGapOpens() {
        return gapOpens;
    }

    public int getIdentities() {
        return identities;
    }

    public int getMismatches() {
        return mismatches;
    }

    public int getRawScore() {
        return rawScore;
    }

    public float getBitScore() {
        return bitScore;
    }

    public double getExpected() {
        return expected;
    }

    public int getAlignmentLength() {
        return alignmentLength;
    }

    public long getReferenceDatabaseLength() {
        return referenceDatabaseLength;
    }

    public void setReferenceDatabaseLength(long referenceDatabaseLength) {
        this.referenceDatabaseLength = referenceDatabaseLength;
    }

    /**
     * reverse order of entries
     *
     * @param array
     */
    private void reverse(byte[] array, int length) {
        final int mid = length >> 1;
        for (int i = 0; i < mid; i++) {
            final int j = length - i - 1;
            final byte tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    /**
     * grow an array
     *
     * @param a
     * @return larger array containing values
     */
    private byte[] grow(byte[] a) {
        final byte[] result = new byte[Math.max(2, 2 * a.length)];
        System.arraycopy(a, 0, result, 0, a.length);
        return result;
    }

    /**
     * return a copy
     *
     * @param array
     * @param length
     * @return copy
     */
    public byte[] copy(byte[] array, int length) {
        byte[] result = new byte[length];
        System.arraycopy(array, 0, result, 0, length);
        return result;
    }

    /**
     * to string
     *
     * @param colRowMatrix
     * @return
     */
    private String toString(int[][] colRowMatrix, int firstCol, int cols, byte[] query) {
        StringBuilder buf = new StringBuilder();

        buf.append(String.format("   |"));
        for (int i = firstCol; i < cols; i++) {
            buf.append(String.format(" %3d", i));
        }
        buf.append("\n");
        buf.append(String.format("   |    "));
        for (int i = firstCol + 1; i < cols; i++) {
            buf.append("   ").append(i - 1 < query.length ? (char) query[i - 1] : ' ');
        }
        buf.append("\n");
        buf.append(String.format("---+"));
        for (int i = firstCol; i < cols; i++) {
            buf.append("----");
        }
        buf.append("\n");

        int r = 0;
        boolean hasRow = true;
        while (hasRow) {
            hasRow = false;
            for (int i = firstCol; i < cols; i++) {
                int[] aColRowMatrix = colRowMatrix[i];
                if (aColRowMatrix.length > r) {
                    if (!hasRow) {
                        hasRow = true;
                        buf.append(String.format("%2d |", r));
                    }
                    int value = aColRowMatrix[r];
                    if (value <= MINUS_INFINITY)
                        buf.append(" -oo");
                    else
                        buf.append(String.format(" %3d", value));
                }
            }
            buf.append("\n");
            r++;
        }
        return buf.toString();
    }

    /**
     * computes and get the alignment text
     *
     * @param queryStore
     * @param queryId
     * @param frameRank
     * @return alignment in text format
     */
    public byte[] getAlignmentText(final QueryStore queryStore, int queryId, byte frameRank) {
        if (alignment == null)
            computeAlignmentByTraceBack();

        alignmentBuffer.reset();

        if (getExpected() != 0)
            alignmentBuffer.writeAsAscii(String.format(" Score = %.1f bits (%d), Expect = %.1g\n", getBitScore(), getRawScore(), getExpected()));
        else
            alignmentBuffer.writeAsAscii(String.format(" Score = %.1f bits (%d), Expect = 0.0\n", getBitScore(), getRawScore()));

        if (isDNAAlignment)
            alignmentBuffer.writeAsAscii(String.format(" Identities = %d/%d (%.0f%%), Gaps = %d/%d (%.0f%%)\n", getIdentities(), getAlignmentLength(),
                    (100.0 * (getIdentities()) / getAlignmentLength()), getGaps(), getAlignmentLength(), (100.0 * (getGaps()) / getAlignmentLength())));
        else // protein alignment
        {
            int numberOfPositives = getAlignmentLength() - Basic.countOccurrences(alignment[1], ' ');
            alignmentBuffer.writeAsAscii(String.format(" Identities = %d/%d (%.0f%%), Positives = %d/%d (%.0f%%), Gaps = %d/%d (%.0f%%)\n",
                    getIdentities(), getAlignmentLength(), (100.0 * (getIdentities()) / getAlignmentLength()),
                    numberOfPositives, getAlignmentLength(), (100.0 * (numberOfPositives) / getAlignmentLength()),
                    getGaps(), getAlignmentLength(), (100.0 * (getGaps()) / getAlignmentLength())));
        }

        byte[] frameInfo = (queryStore != null ? queryStore.getFrameTextForBlastOutput(frameRank) : null);
        if (frameInfo != null)
            alignmentBuffer.write(frameInfo);

        int qFactor;
        if (mode == BlastMode.BlastN)
            qFactor = 1;
        else
            qFactor = 3;

        if (alignment != null) {
            int qStart = (queryStore != null ? queryStore.getOriginalCoordinate(queryId, frameRank, startQuery) + 1 : startQuery + 1);
            int qDirection = (queryStore == null || queryStore.getOriginalCoordinate(queryId, frameRank, endQuery) + 1 - qStart >= 0 ? 1 : -1);
            int sStart = startReference + 1;

            for (int pos = 0; pos < alignment[0].length; pos += ALIGNMENT_SEGMENT_LENGTH) {
                int add = Math.min(ALIGNMENT_SEGMENT_LENGTH, alignment[0].length - pos);
                int qGaps = Utilities.countGaps(alignment[0], pos, add);
                int qEnd = qStart + qFactor * qDirection * ((add - qGaps) - 1);
                if (qFactor == 3) {
                    qEnd += 2 * qDirection;
                }
                alignmentBuffer.writeAsAscii(String.format("\nQuery: %9d ", qStart));
                alignmentBuffer.write(alignment[0], pos, add);
                alignmentBuffer.writeAsAscii(String.format(" %d\n", qEnd));
                qStart = qEnd + qDirection;
                alignmentBuffer.write(MID_TRACK_LEADING_SPACES);
                alignmentBuffer.write(alignment[1], pos, add);
                int sGaps = Utilities.countGaps(alignment[2], pos, add);
                int sEnd = sStart + (add - sGaps) - 1;
                alignmentBuffer.writeAsAscii(String.format("\nSbjct: %9d ", sStart));
                alignmentBuffer.write(alignment[2], pos, add);
                alignmentBuffer.writeAsAscii(String.format(" %d\n", sEnd));
                sStart = sEnd + 1;
            }
               /*       // old code: each row on one line
                alignmentBuffer.writeAsAscii(String.format("\nQuery: %-9d ", data.getStartQueryForOutput(frameRank, startQuery)));
                alignmentBuffer.write(alignment[0]);
                alignmentBuffer.writeAsAscii(String.format(" %d\n", data.getEndQueryForOutput(frameRank, endQuery)));
                alignmentBuffer.write(midTrackSpaces);
                alignmentBuffer.write(alignment[1]);
                alignmentBuffer.writeAsAscii(String.format("\nSbjct: %-9d ", startReference + 1));
                alignmentBuffer.write(alignment[2]);
                alignmentBuffer.writeAsAscii(String.format(" %d\n", endReference));
            */
        }
        return alignmentBuffer.makeCopy();
    }

    /**
     * get alignment in tabular format. If queryHeader==null, skips the first entry which is the query name
     *
     * @param queryStore
     * @param queryId
     * @param frameRank
     * @param queryHeader
     * @param referenceHeader
     * @return alignment in tab format
     */
    public byte[] getAlignmentTab(final QueryStore queryStore, int queryId, byte frameRank, final byte[] queryHeader, final byte[] referenceHeader) {
        // call get alignment to initialize all values:
        if (alignment == null)
            computeAlignmentByTraceBack();

        int outputStartQuery = queryStore.getOriginalCoordinate(queryId, frameRank, startQuery) + 1;
        int outputEndQuery = queryStore.getOriginalCoordinate(queryId, frameRank, endQuery) + 1;

        // queryId, subjectId, percIdentity, alnLength, mismatchCount, gapOpenCount, queryStart, queryEnd, subjectStart, subjectEnd, eVal, bitScore
        alignmentBuffer.reset();
        if (queryHeader != null) {
            int length = Utilities.getFirstWordSkipLeadingGreaterSign(queryHeader, queryTrack);
            alignmentBuffer.write(queryTrack, 0, length);
            alignmentBuffer.write('\t');
        }
        int length = Utilities.getFirstWordSkipLeadingGreaterSign(referenceHeader, queryTrack);
        alignmentBuffer.write(queryTrack, 0, length);
        alignmentBuffer.write('\t');
        if (getExpected() == 0)
            alignmentBuffer.writeAsAscii(String.format("%.1f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t0.0\t%d", ((100.0 * getIdentities()) / getAlignmentLength()), getAlignmentLength(),
                    getMismatches(), getGapOpens(), outputStartQuery, outputEndQuery, getStartReference() + 1, getEndReference(), Math.round(getBitScore())));
        else
            alignmentBuffer.writeAsAscii(String.format("%.1f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.1g\t%d", ((100.0 * getIdentities()) / getAlignmentLength()), getAlignmentLength(),
                    getMismatches(), getGapOpens(), outputStartQuery, outputEndQuery, getStartReference() + 1, getEndReference(), getExpected(), Math.round(getBitScore())));

        return alignmentBuffer.makeCopy();
    }

    /**
     * get alignment in SAM format
     *
     * @param queryHeader
     * @param referenceHeader
     * @param frameRank
     * @return SAM line
     */
    public byte[] getAlignmentSAM(final QueryStore queryStore, int queryId, byte frameRank, final byte[] queryHeader, final byte[] querySequence, final byte[] referenceHeader) {
        if (alignment == null)
            computeAlignmentByTraceBack();

        final int frame = queryStore.getFrameForFrameRank(frameRank);
        final boolean queryIsReverseComplemented = isDNAAlignment && frame < 0;

        final int outputStartReference;
        final int outputEndReference;
        if (queryIsReverseComplemented) {
            outputStartReference = endReference + 1;
            outputEndReference = startReference + 1;
            DNA5.getInstance().reverseComplement(alignment[0]);
            DNA5.getInstance().reverse(alignment[1]);
            DNA5.getInstance().reverseComplement(alignment[2]);
        } else {
            outputStartReference = startReference + 1;
            outputEndReference = endReference;
        }
        int blastXQueryStart = 0;
        if (mode == BlastMode.BlastX) {
            blastXQueryStart = queryStore.getOriginalCoordinate(queryId, frameRank, startQuery) + 1;
        }
        return SAMHelper.createSAMLine(mode, queryHeader, querySequence, startQuery, blastXQueryStart, endQuery, query.length, alignment[0], referenceHeader,
                outputStartReference, outputEndReference, alignment[2], reference.length, bitScore, rawScore, expected, 100 * identities / alignmentLength, frame, null, samSoftClipping).getBytes();
    }


    /**
     * computes the best ungapped raw score using the xDrop heuristic and using the full length of the seed
     *
     * @param querySequence
     * @param refSequence
     * @param queryPos
     * @param refPos
     * @param seedLength
     * @param xdrop
     * @return best ungapped raw score
     */
    public int checkMinSeedScoreAndComputeUngappedRawScoreUsingXDrop(byte[] querySequence, byte[] refSequence, int queryPos, int refPos, int seedLength, int minSeedScore, int xdrop) {
        int score = 0;
        for (int i = 0; i < seedLength; i++) {
            score += scoringMatrix[querySequence[queryPos + i]][refSequence[refPos + i]];
        }
        if (score < minSeedScore)
            return 0;

        // extend to left:
        int bestScore = score;
        int limit = Math.min(queryPos, refPos) + 1;
        for (int i = 1; i < limit; i++) {
            score += scoringMatrix[querySequence[queryPos - i]][refSequence[refPos - i]];
            if (score > bestScore)
                bestScore = score;
            else if (score < bestScore - xdrop)
                break;
        }
        // extend to right:
        limit = Math.min(querySequence.length - queryPos, refSequence.length - refPos);
        for (int i = seedLength; i < limit; i++) {
            score += scoringMatrix[querySequence[queryPos + i]][refSequence[refPos + i]];
            if (score > bestScore)
                bestScore = score;
            else if (score < bestScore - xdrop)
                break;
        }
        return bestScore;
    }

    /**
     * maps a bit score to a raw score
     *
     * @param bitScore
     * @return raw score
     */
    public int getRawScoreForBitScore(double bitScore) {
        return (int) Math.floor((LN_2 * bitScore + lnK) / lambda);
    }

    public static void main(String[] args) throws IOException {

        String query = "CTRTGRDPPKGVLLHGPPGTGKTILAKAVAQSTEATF";
        String reference = "MPDYLGADQRKTKEDEKDDKPIRALDEGDIALLKTYGQSTYSRQIKQVEDDIQQLLKKINELTGIKESDTGLAPPALWDLAADKQTLQSEQPLQVARCTKIINADSEDPKYIINVKQFAKFVVDLSDQVAPTDIEEGMRVGVDRNKYQIHIPLPPKIDPTVTMMQVEEKPDVTYSDVGGCKEQIEKLREVVETPLLHPERFVNLGIEPPKGVLLFGPPGTGKTLCARAVANRTDACFIRVIGSELVQKYVGEGARMVRELFEMARTKKACLIFFDEIDAIGGARFDDGAGGDNEVQRTMLELINQLDGFDPRGNIKVLMATNRPDTLDPALMRPGRLDRKIEFSLPDLEGRTHIFKIHARSMSVERDIRFELLARLCPNSTGAEIRSVCTEAGMFAIRARRKIATEKDFLEAVNKVIKSYAKFSATPRYMTYN";
        int queryPos = 6;
        int refPos = 206;
        final int seedLength = 23;


        AlignerOptions alignerOptions = new AlignerOptions();
        alignerOptions.setBand(4);
        alignerOptions.setGapOpenPenalty(11);
        alignerOptions.setGapExtensionPenalty(1);
        alignerOptions.setMatchScore(2);
        alignerOptions.setMismatchScore(-3);
        alignerOptions.setScoringMatrix(ProteinScoringMatrix.getBlosum62());
        alignerOptions.setAlignmentType("SemiGlobal");
        //alignerOptions.setAlignmentType("Local");

        BandedAligner2 aligner = new BandedAligner2(alignerOptions, BlastMode.BlastP);
        aligner.setReferenceDatabaseLength(reference.length());

        System.err.println("Query: " + query);
        System.err.println("Refe.: " + reference);

        System.err.println("queryPos: " + queryPos + " refPos:" + refPos);
        System.err.println("query length: " + query.length());
        System.err.println("refe. length: " + reference.length());

        System.err.println("Remaining ref: " + (reference.length() - refPos));

        System.err.println("seedLength: " + seedLength);
        System.err.println("querySeed: " + query.substring(queryPos, queryPos + seedLength));
        System.err.println("refSeed:   " + reference.substring(refPos, refPos + seedLength));

        for (int xdrop = 0; xdrop <= 70; xdrop += 10) {
            System.err.println("xDrop(" + xdrop + ") raw score: " + aligner.checkMinSeedScoreAndComputeUngappedRawScoreUsingXDrop(query.getBytes(), reference.getBytes(), queryPos, refPos, seedLength, 40, xdrop));
        }
        aligner.computeAlignment(query.getBytes(), reference.getBytes(), queryPos, refPos, seedLength);

        if (aligner.getRawScore() > 0) {
            System.err.println(Basic.toString(aligner.getAlignmentText(null, 0, (byte) 0)));
        } else
            System.err.println("No alignment");
    }
}
