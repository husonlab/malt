package malt.next;

import jloda.util.ProgressPercentage;
import malt.sequence.*;

import java.io.IOException;

/**
 * database of reference sequences and their seeds
 * <p/>
 * Huson, 8.2014
 */
public class QueryStore {
    public final static int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    public static enum SequenceType {dna, dnax, prot}

    private final QueryMatchesCache queryMatchesCache;

    private final SequenceType sequenceType;
    private final SequenceAndStrandType sequenceAndStrandType;

    private final ISeedExtractor seedExtractor;
    private final SequenceEncoder originalEncoder;
    private final SequenceEncoder sequenceEncoder;

    private final IComplexityMeasure complexityMeasure;

    private byte[][] headers;
    private long[][][] sequenceCodes;
    private long[][] originalCodes; // need to explicitly save original read if output is desired and mode is backward only or dnax
    private int[] sequenceLength;

    private final byte[][] frameTextForBlastOutput;
    private final int[] frameRank2Frame;
    final Alphabet originalAlphabet;
    final Alphabet sequenceAlphabet;
    private final byte numberOfFrames;


    private int numberOfSequences;
    private long numberOfLetters;
    private int numberLowComplexitySequences;
    private int numberCachedQueries;

    private byte[] tmpSequence = new byte[1000];


    /**
     * Constructor
     *
     * @param sequenceType               either dna, dnax (six frameRank translation) or prot
     * @param doForward                  do forward strand (dna or dnax only)
     * @param doBackward                 do backward strand (dna or dnax only)
     * @param approximateNumberOfQueries
     */
    public QueryStore(final QueryMatchesCache queryMatchesCache, final ISeedExtractor seedExtractor, final SequenceType sequenceType,
                      final boolean doForward, final boolean doBackward, float minComplexity, boolean keepOriginal, int approximateNumberOfQueries) {
        this.queryMatchesCache = queryMatchesCache;
        switch (sequenceType) {
            case dnax:
                originalAlphabet = DNA5Alphabet.getInstance();
                sequenceAlphabet = ProteinAlphabet.getInstance();
                break;
            case prot:
                originalAlphabet = ProteinAlphabet.getInstance();
                sequenceAlphabet = ProteinAlphabet.getInstance();
                break;
            default:
            case dna:
                originalAlphabet = DNA5Alphabet.getInstance();
                sequenceAlphabet = DNA5Alphabet.getInstance();
        }
        originalEncoder = new SequenceEncoder(originalAlphabet);
        sequenceEncoder = new SequenceEncoder(sequenceAlphabet);
        this.seedExtractor = seedExtractor;
        this.sequenceType = sequenceType;
        sequenceAndStrandType = SequenceAndStrandType.get(sequenceType, doForward, doBackward);
        if (sequenceType == SequenceType.prot)
            complexityMeasure = new ComplexityMeasureProtein(minComplexity);
        else
            complexityMeasure = new ComplexityMeasureDNA(minComplexity);

        headers = new byte[approximateNumberOfQueries][];
        switch (sequenceAndStrandType) {
            case dnaBoth:
                numberOfFrames = 2;
                sequenceLength = null;
                originalCodes = null;
                frameTextForBlastOutput = new byte[][]{" Strand = Plus / Plus\n".getBytes(), " Strand = Minus / Plus\n".getBytes()};
                frameRank2Frame = new int[]{1, -1};
                break;
            case dnaForwardOnly:
                numberOfFrames = 1;
                sequenceLength = null;
                originalCodes = null;
                frameTextForBlastOutput = new byte[][]{" Strand = Plus / Plus\n".getBytes()};
                frameRank2Frame = new int[]{1};
                break;
            case dnaBackwardOnly:
                numberOfFrames = 1;
                sequenceLength = null;
                originalCodes = (keepOriginal ? new long[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Strand = Minus / Plus\n".getBytes()};
                frameRank2Frame = new int[]{-1};
                break;
            case dnaTranslateBoth:
                numberOfFrames = 6;
                sequenceLength = (keepOriginal ? null : new int[approximateNumberOfQueries]);
                originalCodes = (keepOriginal ? new long[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Frame = +1\n".getBytes(), " Frame = +2\n".getBytes(), " Frame = +3\n".getBytes(),
                        " Frame = -1\n".getBytes(), " Frame = -2\n".getBytes(), " Frame = -3\n".getBytes()};
                frameRank2Frame = new int[]{+1, +2, +3, -1, -2, -3};
                break;
            case dnaTranslateForwardOnly:
                numberOfFrames = 3;
                sequenceLength = (keepOriginal ? null : new int[approximateNumberOfQueries]);
                originalCodes = (keepOriginal ? new long[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Frame = +1\n".getBytes(), " Frame = +2\n".getBytes(), " Frame = +3\n".getBytes()};
                frameRank2Frame = new int[]{+1, +2, +3};
                break;
            case dnaTranslateBackwardOnly:
                numberOfFrames = 3;
                sequenceLength = (keepOriginal ? null : new int[approximateNumberOfQueries]);
                originalCodes = (keepOriginal ? new long[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Frame = -1\n".getBytes(), " Frame = -2\n".getBytes(), " Frame = -3\n".getBytes()};
                frameRank2Frame = new int[]{-1, -2, -3};
                break;
            default:
            case prot:
                numberOfFrames = 1;
                sequenceLength = null;
                originalCodes = null;
                frameTextForBlastOutput = new byte[0][0];
                frameRank2Frame = null;
        }
        sequenceCodes = new long[approximateNumberOfQueries][][];
    }

    /**
     * read and index batch of queries
     *
     * @param it
     * @param queryBatchSize
     * @param progress
     * @return number of sequences read
     */
    public long loadFastAFile(FastAFileIteratorCode it, int queryBatchSize, final ProgressPercentage progress) throws IOException {
        clear();
        progress.setMaximum(it.getMaximumProgress());
        progress.setProgress(0);
        while (it.hasNext()) {
            byte[] header = it.nextHeader();
            long[] sequenceCode = it.nextSequenceCode();
            /*
                if (!complexityMeasure.filter(sequence)) {
                    //System.err.println("LOW: " + Basic.toString(sequence));
                    numberLowComplexitySequences++;
                } else
                 */
            {
                add(header, sequenceCode);
            }
            progress.setProgress(it.getProgress());
            if (numberOfSequences == queryBatchSize)
                break;
        }
        progress.reportTaskCompleted();
        System.err.println(String.format("Num. of queries: %,15d", numberOfSequences));
        if (numberLowComplexitySequences > 0)
            System.err.println(String.format("Low complexity:  %,15d", numberLowComplexitySequences));
        System.err.println(String.format("Number of letters:%,14d", numberOfLetters));

        return numberOfSequences;
    }

    /**
     * compute seeds in this store
     *
     * @param chunk
     * @param numberOfChunks
     * @param low            start at this seed id
     * @param high           end below this seed id
     * @param seedShape
     * @param seedStores
     * @param progress
     */
    public void computeSeeds(final int chunk, final int numberOfChunks, final int numberOfParts, final int low, final int high, final SeedShape2 seedShape, final SeedStore[] seedStores, final ProgressPercentage progress) {
        final boolean[] seedMask = seedShape.getMask();

        if (progress != null) {
            progress.setMaximum(high - low);
            progress.setProgress(0);
        }

        final int chunkMask = numberOfChunks - 1;
        final int partsBits = numberOfParts - 1;
        final int numberOfChunksBits = Utilities.getMostSignificantBit(numberOfChunks);

        if (progress != null)
            progress.setMaximum(high - low);

        for (int sequenceId = low; sequenceId < high; sequenceId++) {
            if (queryMatchesCache == null || !queryMatchesCache.contains(getOriginalSequence(sequenceId))) {
                final long[][] sequenceFrames = sequenceCodes[sequenceId];
                for (byte f = 0; f < sequenceFrames.length; f++) {
                    final long[] sequenceCode = sequenceFrames[f];
                    if (sequenceCode != null) {
                        for (int pos = 0; pos < sequenceCode.length - seedShape.getLength() + 1; pos++) {
                            final long value = seedExtractor.getSeedCode(seedMask, seedShape.getWeight(), sequenceCode, pos);
                            long key = (value / ReferenceStore.PRIME_NUMBER); // need to divide by some number to get better distribution onto chunks
                            if (numberOfChunks == 1 || (key & chunkMask) == chunk) {
                                if (seedExtractor.isGoodSeed(value, seedShape.getWeight())) {
                                    final int part = (int) ((key >>> numberOfChunksBits) & partsBits);
                                    synchronized (seedStores[part]) {
                                        seedStores[part].add(value, sequenceId, pos, f);
                                    }
                                }
                            }
                        }
                    }
                }
                if (progress != null)
                    progress.setProgress(sequenceId - low);
            }
        }
    }

    /**
     * add a header and sequence to the list of sequences
     *
     * @param header
     * @param sequenceCode
     */
    public void add(byte[] header, long[] sequenceCode) throws IOException {

        if (numberOfSequences == Utilities.MAX_ARRAY_SIZE)
            throw new IOException("Number of sequences exceeds max: " + Utilities.MAX_ARRAY_SIZE);

        if (numberOfSequences >= sequenceCodes.length) {
            headers = Utilities.grow(headers);
            sequenceCodes = Utilities.grow(sequenceCodes);
            if (sequenceLength != null)
                sequenceLength = Utilities.grow(sequenceLength);
            if (originalCodes != null)
                originalCodes = Utilities.grow(originalCodes);
        }
        headers[numberOfSequences] = header;
        if (originalCodes != null)
            originalCodes[numberOfSequences] = sequenceCode;
        else if (sequenceLength != null)
            sequenceLength[numberOfSequences] = originalEncoder.computeLength(sequenceCode);

        final long[][] sequenceFrames = new long[numberOfFrames][];
        sequenceCodes[numberOfSequences] = sequenceFrames;

        switch (sequenceAndStrandType) {
            case dnaForwardOnly: {
                sequenceFrames[0] = sequenceCode;
                break;
            }
            case dnaBackwardOnly: {
                final byte[] sequence = sequenceEncoder.decode(sequenceCode);
                if (sequence.length > tmpSequence.length)
                    tmpSequence = new byte[sequence.length];
                DNA5Alphabet.reverseComplement(sequence, tmpSequence);
                sequenceFrames[0] = sequenceEncoder.encode(tmpSequence);

                break;
            }
            case dnaBoth: {
                final byte[] sequence = sequenceEncoder.decode(sequenceCode);
                sequenceFrames[0] = sequenceCode;
                if (sequence.length > tmpSequence.length)
                    tmpSequence = new byte[sequence.length];
                DNA5Alphabet.reverseComplement(sequence, tmpSequence);
                sequenceFrames[1] = sequenceEncoder.encode(tmpSequence);
                break;
            }
            case dnaTranslateForwardOnly: {
                final byte[] sequence = sequenceEncoder.decode(sequenceCode);
                if (sequence.length > tmpSequence.length)
                    tmpSequence = new byte[sequence.length];
                int length;
                length = TranslatorNew.getTranslation(sequence, 1, tmpSequence);
                if (length > 0)
                    sequenceFrames[0] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, 2, tmpSequence);
                if (length > 0)
                    sequenceFrames[1] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, 3, tmpSequence);
                if (length > 0)
                    sequenceFrames[2] = sequenceEncoder.encode(tmpSequence, length);
                break;
            }
            case dnaTranslateBackwardOnly: {
                final byte[] sequence = sequenceEncoder.decode(sequenceCode);
                if (sequence.length > tmpSequence.length)
                    tmpSequence = new byte[sequence.length];
                int length;
                length = TranslatorNew.getTranslation(sequence, -1, tmpSequence);
                if (length > 0)
                    sequenceFrames[0] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, -2, tmpSequence);
                if (length > 0)
                    sequenceFrames[1] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, -3, tmpSequence);
                if (length > 0)
                    sequenceFrames[2] = sequenceEncoder.encode(tmpSequence, length);
                break;
            }
            case dnaTranslateBoth: {
                final byte[] sequence = sequenceEncoder.decode(sequenceCode);
                if (sequence.length > tmpSequence.length)
                    tmpSequence = new byte[sequence.length];
                int length;
                length = TranslatorNew.getTranslation(sequence, 1, tmpSequence);
                if (length > 0)
                    sequenceFrames[0] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, 2, tmpSequence);
                if (length > 0)
                    sequenceFrames[1] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, 3, tmpSequence);
                if (length > 0)
                    sequenceFrames[2] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, -1, tmpSequence);
                if (length > 0)
                    sequenceFrames[3] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, -2, tmpSequence);
                if (length > 0)
                    sequenceFrames[4] = sequenceEncoder.encode(tmpSequence, length);
                length = TranslatorNew.getTranslation(sequence, -3, tmpSequence);
                if (length > 0)
                    sequenceFrames[5] = sequenceEncoder.encode(tmpSequence, length);
            }
            break;
            default:
            case prot: {
                sequenceFrames[0] = sequenceCode;
                break;
            }
        }
        numberOfLetters += originalEncoder.computeLength(sequenceCode);
        numberOfSequences++;
    }

    /**
     * we track both sequence type and DNA strand usage using a single enum
     */
    private enum SequenceAndStrandType {
        dnaBoth, dnaForwardOnly, dnaBackwardOnly, dnaTranslateBoth, dnaTranslateForwardOnly,
        dnaTranslateBackwardOnly, prot;

        public static SequenceAndStrandType get(SequenceType sequenceType, boolean doForward, boolean doBackward) {
            switch (sequenceType) {
                case dna:
                    if (doForward && !doBackward)
                        return dnaForwardOnly;
                    else if (!doForward && doBackward)
                        return dnaBackwardOnly;
                    else
                        return dnaBoth;
                case dnax:
                    if (doForward && !doBackward)
                        return dnaTranslateForwardOnly;
                    else if (!doForward && doBackward)
                        return dnaTranslateBackwardOnly;
                    else
                        return dnaTranslateBoth;
                default:
                case prot:
                    return prot;
            }
        }
    }

    /**
     * gets the original coordinate (range 0-query.length-1)
     *
     * @param index
     * @param frameRank  number from 0 to 5
     * @param coordinate
     * @return original coordinate
     */
    public int getOriginalCoordinate(int index, byte frameRank, int coordinate) {
        switch (sequenceAndStrandType) {
            case dnaForwardOnly:
                return coordinate;
            case dnaBackwardOnly:
                return getOriginalQueryLength(index) - coordinate - 1;
            case dnaBoth:
                if (frameRank > 1)
                    throw new RuntimeException("Bad frameRank: " + frameRank);
                return (frameRank == 0 ? coordinate : getOriginalQueryLength(index) - coordinate - 1);
            case dnaTranslateForwardOnly:
                if (frameRank > 2)
                    throw new RuntimeException("Bad frameRank: " + frameRank);
                return 3 * coordinate + frameRank;
            case dnaTranslateBackwardOnly:
                if (frameRank > 2)
                    throw new RuntimeException("Bad frameRank: " + frameRank);
                return getOriginalQueryLength(index) - 3 * coordinate - (frameRank + 1);
            case dnaTranslateBoth:
                return (frameRank < 3 ? 3 * coordinate + frameRank : getOriginalQueryLength(index) - 3 * coordinate - (frameRank - 2));
            case prot:
            default:
                return coordinate;
        }
    }

    /**
     * gets frame text for blast output
     *
     * @param frameRank
     * @return blast frame text
     */
    public byte[] getFrameTextForBlastOutput(int frameRank) {
        if (frameTextForBlastOutput.length == 0)
            return null;
        else
            return frameTextForBlastOutput[frameRank];
    }

    /**
     * gets a frame (1 or -1 for DNA, +1,+2,+3,-1,-2,-3 for translated DNA) from a frame rank (0-1 for DNA, 0-6 for translated DNA)
     *
     * @param frameRank
     * @return frame
     */
    public int getFrameForFrameRank(byte frameRank) {
        return frameRank2Frame == null ? 0 : frameRank2Frame[frameRank];
    }

    /**
     * gets the number of sequences
     *
     * @return number of sequences
     */
    public int getNumberOfSequences() {
        return numberOfSequences;
    }

    /**
     * gets the number of letters
     *
     * @return number of letters
     */
    public long getNumberOfLetters() {
        return numberOfLetters;
    }

    /**
     * get the number of low complexity sequences
     *
     * @return number of filtered sequences
     */
    public int getNumberLowComplexitySequences() {
        return numberLowComplexitySequences;
    }

    public SequenceType getSequenceType() {
        return sequenceType;
    }

    public int getNumberCachedQueries() {
        return numberCachedQueries;
    }

    /**
     * erase
     */
    public void clear() {
        numberOfSequences = 0;
        numberOfLetters = 0;
        numberCachedQueries = 0;
    }

    /**
     * Get header string
     *
     * @param index index starting at 0
     * @return header
     */
    public byte[] getHeader(int index) {
        return headers[index];
    }

    /**
     * get name of query
     *
     * @param index
     * @return name (first word, excluding '>')
     */
    public byte[] getName(int index) {
        return malt.util.Utilities.getFirstWordSkipLeadingGreaterSign(headers[index]);
    }

    /**
     * Get sequence code
     *
     * @param index index starting at 0
     * @return sequence
     */
    public long[] getSequenceCode(int index, byte frameRank) {
        return sequenceCodes[index][frameRank];
    }

    /**
     * Get sequence
     *
     * @param index index starting at 0
     * @return sequence
     */
    public byte[] getSequence(int index, byte frameRank) {
        return sequenceEncoder.decode(sequenceCodes[index][frameRank]);
    }

    /**
     * get the the length of the original query
     *
     * @param index
     * @return original query length
     */
    public int getOriginalQueryLength(int index) {
        if (sequenceLength != null)
            return sequenceLength[index];
        else if (originalCodes != null)
            return originalEncoder.computeLength(originalCodes[index]);
        else
            return sequenceEncoder.computeLength(sequenceCodes[index][0]);
    }

    /**
     * get the original read sequence
     *
     * @param index
     * @return original sequence
     */
    public byte[] getOriginalSequence(int index) {
        if (originalCodes != null)
            return originalEncoder.decode(originalCodes[index]);
        else
            return sequenceEncoder.decode(sequenceCodes[index][0]);
    }

}
