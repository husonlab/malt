package malt.align;

import jloda.util.Basic;
import malt.DataForInnerLoop;
import malt.data.DNA5;
import malt.io.SAMHelper;
import malt.util.ReusableByteBuffer;
import malt.util.Utilities;
import megan.parsers.blast.BlastMode;

/**
 * banded DNA aligner. Does both local and semiGlobal alignment
 * Daniel Huson, 8.2014
 */
public class BandedAligner {
    private double lambda = 0.625;
    private double lnK = -0.89159811928378356416921953633132;
    private final static double LN_2 = 0.69314718055994530941723212145818;
    private final static int MINUS_INFINITY = -100000000;

    public static int ALIGNMENT_SEGMENT_LENGTH = 60; // length of alignment segment in text format output
    private final static byte[] MID_TRACK_LEADING_SPACES = "                 ".getBytes(); // spaces used in text format output

    private long referenceDatabaseLength = 10000000;

    private byte[] query;
    private int queryLength;
    private byte[] reference;
    private int referenceLength;

    private final IScoringMatrix scoringMatrix;
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

    private int bestRow;
    private int bestCol;
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
    private static final byte M_FROM_IREF = 2;
    private static final byte M_FROM_IQuery = 3;
    private static final byte IRef_FROM_M = 4;
    private static final byte IRef_FROM_IRef = 5;
    private static final byte IQuery_FROM_M = 6;
    private static final byte IQuery_FROM_IQuery = 7;

    // buffers:
    private byte[] queryTrack = new byte[1000];
    private byte[] midTrack = new byte[1000];
    private byte[] referenceTrack = new byte[1000];
    //private final ByteOutputStream alignmentBuffer = new ByteOutputStream(4096);

    private ReusableByteBuffer alignmentBuffer = new ReusableByteBuffer(10000);

    private int queryPos;
    private int refPos;

    private final boolean samSoftClipping;

    /**
     * constructor
     *
     * @param alignerOptions
     */
    public BandedAligner(final AlignerOptions alignerOptions, final BlastMode mode) {
        this.scoringMatrix = alignerOptions.getScoringMatrix();
        this.isDNAAlignment = (mode == BlastMode.BlastN);
        this.doSemiGlobal = alignerOptions.getAlignmentType() == AlignerOptions.AlignmentMode.SemiGlobal;

        this.lambda = alignerOptions.getLambda();
        this.lnK = alignerOptions.getLnK();

        this.mode = mode;

        band = alignerOptions.getBand();
        gapOpenPenalty = alignerOptions.getGapOpenPenalty();
        gapExtensionPenalty = alignerOptions.getGapExtensionPenalty();
        referenceDatabaseLength = alignerOptions.getReferenceDatabaseLength();

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
     * @param queryLength
     * @param reference
     * @param referenceLength
     * @param queryPos
     * @param refPos
     */
    public void computeAlignment(byte[] query, int queryLength, byte[] reference, int referenceLength, int queryPos, int refPos) {
        this.query = query;
        this.queryLength = queryLength;
        this.reference = reference;
        this.referenceLength = referenceLength;
        this.queryPos = queryPos;
        this.refPos = refPos;
        if (doSemiGlobal)
            computeSemiGlobalAlignment();
        else
            computeLocalAlignment();
    }

    /**
     * Performs a banded local alignment and return the raw score.
     */
    private void computeLocalAlignment() {
        startQuery = startReference = 0;

        final int rows = 2 * band + 3;
        final int cols = queryLength + 1;

        if (cols > matrixM.length) {  // all values will be 0
            matrixM = new int[cols][rows];
            matrixIRef = new int[cols][rows];
            matrixIQuery = new int[cols][rows];
            traceBackM = new byte[cols][rows];
            traceBackIRef = new byte[cols][rows];
            traceBackIQuery = new byte[cols][rows];
            for (int r = 1; r < rows; r++) {
                matrixM[0][r] = matrixIRef[0][r] = matrixIQuery[0][r] = 0;
                traceBackM[0][r] = traceBackIRef[0][r] = traceBackIQuery[0][r] = DONE;
            }
            for (int c = 0; c < cols; c++) {
                matrixM[c][0] = matrixIRef[c][0] = matrixIQuery[c][0] = matrixM[c][rows - 1] = matrixIRef[c][rows - 1] = matrixIQuery[c][rows - 1] = 0;
                traceBackM[c][0] = traceBackIRef[0][0] = traceBackIQuery[0][0]
                        = traceBackM[c][rows - 1] = traceBackIRef[0][rows - 1] = traceBackIQuery[0][rows - 1] = DONE;
            }
        }

        refOffset = refPos - queryPos - band - 2; // need this to compute index in reference sequence

        final int firstCol = Math.max(1, -refOffset - 2 * band - 1); // the column for which refIndex(firstCol,bottom-to-last row)==0
        if (firstCol > 1) {
            final int prevCol = firstCol - 1;
            final int secondToLastRow = 2 * band + 1;
            traceBackM[prevCol][secondToLastRow] = traceBackIRef[prevCol][secondToLastRow] = traceBackIQuery[prevCol][secondToLastRow] = DONE; // set previous column to done
            matrixM[prevCol][secondToLastRow] = matrixIRef[prevCol][secondToLastRow] = matrixIQuery[prevCol][secondToLastRow] = 0;
        }

        final int lastCol = Math.min(cols - 1, referenceLength - refOffset - 2); // the column for which refIndex(lastCol,1)==refLength-1

        bestRow = -1;
        bestCol = -1;
        rawScore = 0;

        final int lastRow = rows - 2;

        for (int col = firstCol; col <= lastCol; col++) {   // we never modify the first column or the first or last row
            for (int row = 1; row <= lastRow; row++) {
                final int refIndex = row + col + refOffset;

                if (refIndex < -1) {
                    // do nothing, this cell will not be used
                } else if (refIndex == -1) {
                    traceBackM[col][row] = traceBackIRef[col][row] = traceBackIQuery[col][row] = DONE; // this is column before reference starts, set to done
                    matrixM[col][row] = matrixIRef[col][row] = matrixIQuery[col][row] = 0;
                } else if (refIndex < referenceLength) {  // do the actual alignment:

                    int bestMScore = 0;
                    // match or mismatch
                    {
                        final int s = scoringMatrix.getScore(query[col - 1], reference[refIndex]);

                        int score = matrixM[col - 1][row] + s;
                        if (score > 0) {
                            traceBackM[col][row] = M_FROM_M;
                            bestMScore = score;
                        }
                        score = matrixIRef[col - 1][row] + s;
                        if (score > bestMScore) {
                            traceBackM[col][row] = M_FROM_IREF;
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

                    // insertion in ref
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

                    int bestScore = Math.max(Math.max(bestMScore, bestIRefScore), bestIQueryScore);
                    if (bestScore > rawScore) {
                        rawScore = bestScore;
                        bestRow = row;
                        bestCol = col;
                    }
                } else { // refIndex>=referenceLength
                    break; // all remaining refIndex for this column will be too big
                }
            }
        }

        if (false) {
            System.err.println("Matrix M:");
            System.err.println(toString(matrixM, firstCol, lastCol, query));
            System.err.println("Matrix IQuery:");
            System.err.println(toString(matrixIQuery, firstCol, lastCol, query));
            System.err.println("Matrix IRef:");
            System.err.println(toString(matrixIRef, firstCol, lastCol, query));
        }

        if (bestRow == -1 || bestCol == -1) {
            rawScore = 0;
        }
    }

    /**
     * Performs a banded semi-global alignment.
     */
    private void computeSemiGlobalAlignment() {
        startQuery = startReference = 0;

        final int rows = 2 * band + 3;
        final int cols = queryLength + 1;

        if (cols > matrixM.length) { // reinitialize
            matrixM = new int[cols][rows];
            matrixIRef = new int[cols][rows];
            matrixIQuery = new int[cols][rows];
            traceBackM = new byte[cols][rows];
            traceBackIRef = new byte[cols][rows];
            traceBackIQuery = new byte[cols][rows];
            for (int r = 1; r < rows; r++) {
                traceBackM[0][r] = traceBackIRef[0][r] = traceBackIQuery[0][r] = DONE;
                matrixIQuery[0][r] = -gapOpenPenalty;
            }
            for (int c = 0; c < cols; c++)
                matrixM[c][0] = matrixIRef[c][0] = matrixIQuery[c][0]
                        = matrixM[c][rows - 1] = matrixIRef[c][rows - 1] = matrixIQuery[c][rows - 1]
                        = MINUS_INFINITY; // must never go outside band
        }

        refOffset = refPos - queryPos - band - 2; // need this to compute index in reference sequence

        final int firstCol = Math.max(1, -refOffset - 2 * band - 1); // the column for which refIndex(firstCol,bottom-to-last row)==0

        if (firstCol > 1) {
            final int prevCol = firstCol - 1;
            final int secondToLastRow = rows - 2;
            traceBackM[prevCol][secondToLastRow] = traceBackIRef[prevCol][secondToLastRow] = traceBackIQuery[prevCol][secondToLastRow] = DONE; // set previous column to done
            matrixM[prevCol][secondToLastRow] = matrixIRef[prevCol][secondToLastRow] = matrixIQuery[prevCol][secondToLastRow] = 0;
        }

        final int lastCol = Math.min(cols - 1, referenceLength - refOffset - 2); // the column for which refIndex(lastCol,1)==refLength-1

        bestRow = -1;
        bestCol = -1;
        rawScore = 0;

        final int lastRowToFill = rows - 2;

        for (int col = firstCol; col <= lastCol; col++) {   // we never modify the first column or the first or last row
            for (int row = 1; row <= lastRowToFill; row++) {
                final int refIndex = row + col + refOffset;

                if (refIndex < -1) {
                    // do nothing, this cell will not be used
                } else if (refIndex == -1) {
                    traceBackM[col][row] = traceBackIRef[col][row] = traceBackIQuery[col][row] = DONE; // this is column before reference starts, set to done
                    matrixM[col][row] = 0;
                    matrixIRef[col][row] = matrixIQuery[col][row] = -gapOpenPenalty;
                } else if (refIndex < referenceLength) {  // do the actual alignment:
                    int bestMScore = Integer.MIN_VALUE;
                    // match or mismatch
                    {
                        final int s = scoringMatrix.getScore(query[col - 1], reference[refIndex]);  // note: queryIndex=c-1

                        int score = matrixM[col - 1][row] + s;
                        if (score > bestMScore) {
                            traceBackM[col][row] = M_FROM_M;
                            bestMScore = score;
                        }
                        score = matrixIRef[col - 1][row] + s;
                        if (score > bestMScore) {
                            traceBackM[col][row] = M_FROM_IREF;
                            bestMScore = score;
                        }
                        score = matrixIQuery[col - 1][row] + s;
                        if (score > bestMScore) {
                            traceBackM[col][row] = M_FROM_IQuery;
                            bestMScore = score;
                        }
                        matrixM[col][row] = bestMScore;
                    }

                    // insertion in ref
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

                    // insertion in query
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

                    if ((col == lastCol || (col <= lastCol && refIndex == referenceLength - 1))
                            && Math.max(Math.max(bestMScore, bestIRefScore), bestIQueryScore) > rawScore) {
                        rawScore = Math.max(Math.max(bestMScore, bestIRefScore), bestIQueryScore);
                        bestRow = row;
                        bestCol = col;
                    }
                } else { // refIndex>=referenceLength
                    break; // all remaining refIndex for this column will be too big
                }
            }
        }

        if (false) {
            System.err.println("Matrix M:");
            System.err.println(toString(matrixM, firstCol - 1, lastCol + 1, query));
            System.err.println("Matrix IQuery:");
            System.err.println(toString(matrixIRef, firstCol - 1, lastCol + 1, query));
            System.err.println("Matrix IRef:");
            System.err.println(toString(matrixIQuery, firstCol - 1, lastCol + 1, query));
        }

        if (bestRow == -1 || bestCol == -1) {
            rawScore = 0;
        }
    }

    /**
     * compute the bit score and expected score from the raw score
     */
    public void computeBitScoreAndExpected() {
        if (rawScore > 0) {
            bitScore = (float) ((lambda * rawScore - lnK) / LN_2);
            expected = referenceDatabaseLength * queryLength * Math.pow(2, -bitScore);
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
    public byte[][] getAlignmentByTraceBack() {
        int r = bestRow;
        int c = bestCol;

        if (bestRow == -1 || bestCol == -1)
            return null;

        byte[][] traceBack;
        traceBack = traceBackM;
        if (matrixIRef[c][r] > matrixM[c][r]) {
            traceBack = traceBackIRef;
            if (matrixIQuery[c][r] > matrixIRef[c][r])
                traceBack = traceBackIQuery;
        } else if (matrixIQuery[c][r] > matrixM[c][r])
            traceBack = traceBackIQuery;

        gaps = 0;
        gapOpens = 0;
        identities = 0;
        mismatches = 0;

        int pos = 0;

        endQuery = c;
        endReference = r + c + refOffset + 1;

        loop:
        while (true) {
            int refIndex = r + c + refOffset;

            switch (traceBack[c][r]) {
                case DONE:
                    startQuery = c;
                    startReference = r + c + refOffset + 1;
                    break loop;
                case M_FROM_M:
                    queryTrack[pos] = query[c - 1];
                    referenceTrack[pos] = reference[refIndex];
                    if (queryTrack[pos] == referenceTrack[pos]) {
                        if (isDNAAlignment)
                            midTrack[pos] = '|';
                        else
                            midTrack[pos] = queryTrack[pos];
                        identities++;
                    } else {
                        if (isDNAAlignment || scoringMatrix.getScore(queryTrack[pos], referenceTrack[pos]) <= 0)
                            midTrack[pos] = ' ';
                        else
                            midTrack[pos] = '+';
                        mismatches++;
                    }
                    c--;
                    traceBack = traceBackM;
                    break;
                case M_FROM_IREF:
                    queryTrack[pos] = query[c - 1];
                    referenceTrack[pos] = reference[refIndex];
                    if (queryTrack[pos] == referenceTrack[pos]) {
                        if (isDNAAlignment)
                            midTrack[pos] = '|';
                        else
                            midTrack[pos] = queryTrack[pos];
                        identities++;
                    } else {
                        if (isDNAAlignment || scoringMatrix.getScore(queryTrack[pos], referenceTrack[pos]) <= 0)
                            midTrack[pos] = ' ';
                        else
                            midTrack[pos] = '+';
                    }
                    c--;
                    traceBack = traceBackIRef;
                    break;
                case M_FROM_IQuery:
                    queryTrack[pos] = query[c - 1];
                    referenceTrack[pos] = reference[refIndex];
                    if (queryTrack[pos] == referenceTrack[pos]) {
                        if (isDNAAlignment)
                            midTrack[pos] = '|';
                        else
                            midTrack[pos] = queryTrack[pos];
                        identities++;
                    } else {
                        if (isDNAAlignment || scoringMatrix.getScore(queryTrack[pos], referenceTrack[pos]) <= 0)
                            midTrack[pos] = ' ';
                        else
                            midTrack[pos] = '+';
                    }
                    c--;
                    traceBack = traceBackIQuery;
                    break;
                case IRef_FROM_M:
                    queryTrack[pos] = '-';
                    referenceTrack[pos] = reference[refIndex];
                    midTrack[pos] = ' ';
                    r--;
                    traceBack = traceBackM;
                    gaps++;
                    gapOpens++;
                    break;
                case IRef_FROM_IRef:
                    queryTrack[pos] = '-';
                    referenceTrack[pos] = reference[refIndex];
                    midTrack[pos] = ' ';
                    r--;
                    traceBack = traceBackIRef;
                    gaps++;
                    break;
                case IQuery_FROM_M:
                    queryTrack[pos] = query[c - 1];
                    referenceTrack[pos] = '-';
                    midTrack[pos] = ' ';
                    c--;
                    r++;
                    traceBack = traceBackM;
                    gaps++;
                    gapOpens++;
                    break;
                case IQuery_FROM_IQuery:
                    queryTrack[pos] = query[c - 1];
                    referenceTrack[pos] = '-';
                    midTrack[pos] = ' ';
                    c--;
                    r++;
                    traceBack = traceBackIQuery;
                    gaps++;
                    break;
                default:
                    throw new RuntimeException("Undefined trace-back state: " + traceBack[c][r]);
            }
            if (queryTrack[pos] == '-' && referenceTrack[pos] == '-')
                System.err.println("gap-gap at: " + pos);

            if (++pos >= queryTrack.length) {
                queryTrack = grow(queryTrack);
                midTrack = grow(midTrack);
                referenceTrack = grow(referenceTrack);
            }
        } // end of loop

        alignmentLength = pos;

        /*
        if(alignmentLength<5) {
            System.err.println("Matrix M:");
            System.err.println(toString(matrixM, 0, endQuery+1, query));
            System.err.println("Matrix I:");
            System.err.println(toString(matrixI,0, endQuery+1, query));
        }
        */
        return new byte[][]{reverse(queryTrack, pos), reverse(midTrack, pos), reverse(referenceTrack, pos)};
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
     * reverse bytes
     *
     * @param array
     * @return reversed bytes
     */
    private byte[] reverse(byte[] array, int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++)
            result[i] = array[length - i - 1];
        return result;
    }

    /**
     * grow an array
     *
     * @param a
     * @return larger array containing values
     */
    private byte[] grow(byte[] a) {
        byte[] result = new byte[Math.max(2, 2 * a.length)];
        System.arraycopy(a, 0, result, 0, a.length);
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
            buf.append("   ").append((char) query[i - 1]);
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
     * gets the alignment text
     *
     * @param data
     * @return alignment text
     */
    public byte[] getAlignmentText(DataForInnerLoop data, int frameRank) {
        byte[][] alignment = getAlignmentByTraceBack();

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

        String frameInfo = data.getFrameInfoLine(frameRank);
        if (frameInfo != null)
            alignmentBuffer.writeAsAscii(frameInfo);

        int qFactor;
        if (mode == BlastMode.BlastN)
            qFactor = 1;
        else
            qFactor = 3;

        if (alignment != null) {
            int qStart = data.getStartQueryForOutput(frameRank, startQuery);
            int qDirection = (data.getEndQueryForOutput(frameRank, endQuery) - qStart >= 0 ? 1 : -1);
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
     * @param data
     * @param queryHeader
     * @param referenceHeader
     * @param frameRank
     * @return tabular format without first field
     */
    public byte[] getAlignmentTab(final DataForInnerLoop data, final byte[] queryHeader, final byte[] referenceHeader, final int frameRank) {
        // call get alignment to initialize all values:
        getAlignmentByTraceBack();

        int outputStartQuery = data.getStartQueryForOutput(frameRank, startQuery);
        int outputEndQuery = data.getEndQueryForOutput(frameRank, endQuery);

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
    public byte[] getAlignmentSAM(final DataForInnerLoop data, final byte[] queryHeader, final byte[] querySequence, final byte[] referenceHeader, final int frameRank) {
        final int frame = data.getFrameForFrameRank(frameRank);
        final boolean queryIsReverseComplemented = isDNAAlignment && frame < 0;

        final byte[][] alignment = getAlignmentByTraceBack(); // need to first compute alignment so that values are set

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
            blastXQueryStart = data.getStartQueryForOutput(frameRank, startQuery);
        }
        return SAMHelper.createSAMLine(mode, queryHeader, querySequence, startQuery, blastXQueryStart, endQuery, queryLength, alignment[0], referenceHeader,
                outputStartReference, outputEndReference, alignment[2], referenceLength, bitScore, rawScore, expected, 100 * identities / alignmentLength, frame, data.getQualityValues(), samSoftClipping).getBytes();
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

    private static final int minNumberOfExactMatches = 10;
    private static final int windowForMinNumberOfExactMatches = 30;

    /**
     * heuristically check whether there is going to be a good alignment
     *
     * @param query
     * @param reference
     * @param queryPos
     * @param refPos
     * @return true, if good alignment is likely
     */
    public boolean quickCheck(final byte[] query, final int queryLength, final byte[] reference, final int referenceLength, final int queryPos, final int refPos) {
        if (mode == BlastMode.BlastN)
            return true;

        if (queryPos + minNumberOfExactMatches >= queryLength || refPos + minNumberOfExactMatches >= referenceLength)
            return false;

        int count = 0;
        final int maxSteps = Math.min(windowForMinNumberOfExactMatches, Math.min(queryLength - queryPos, referenceLength - refPos));
        for (int i = 0; i < maxSteps; i++) {
            if (query[queryPos + i] == reference[refPos + i]) {
                count++;
                if (count == minNumberOfExactMatches)
                    return true;
            }
        }
        return false;
    }
}
