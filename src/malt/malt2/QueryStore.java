package malt.malt2;

import jloda.util.ProgressPercentage;
import malt.data.DNA5;
import malt.data.IAlphabet;
import malt.data.ProteinAlphabet;
import malt.data.SeedShape;
import malt.io.FastAFileIteratorBytes;

import java.io.IOException;

/**
 * database of reference sequences and their seeds
 * <p/>
 * Huson, 8.2014
 */
public class QueryStore {
    public static enum SequenceType {dna, dnax, prot}

    private final QueryMatchesCache queryMatchesCache;

    private final SequenceType sequenceType;
    private final SequenceAndStrandType sequenceAndStrandType;

    private final IComplexityMeasure complexityMeasure;

    private byte[][] headers;
    private byte[][][] sequences;
    private byte[][] original; // need to explicitly save original read if output is desired and mode is backward only or dnax
    private int[] sequenceLength;

    private final byte[][] frameTextForBlastOutput;
    private final int[] frameRank2Frame;
    final IAlphabet sequenceAlphabet;
    private final byte numberOfFrames;

    private int numberOfSequences;
    private long numberOfLetters;
    private int numberLowComplexitySequences;
    private int numberCachedQueries;


    /**
     * Constructor
     *
     * @param queryAlphabet              alphabet of query sequences
     * @param sequenceType               either dna, dnax (six frameRank translation) or prot
     * @param doForward                  do forward strand (dna or dnax only)
     * @param doBackward                 do backward strand (dna or dnax only)
     * @param approximateNumberOfQueries
     */
    public QueryStore(final QueryMatchesCache queryMatchesCache, final IAlphabet queryAlphabet, final SequenceType sequenceType,
                      final boolean doForward, final boolean doBackward, float minComplexity, boolean keepOriginal, int approximateNumberOfQueries) {
        this.queryMatchesCache = queryMatchesCache;
        this.sequenceAlphabet = queryAlphabet;
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
                original = null;
                frameTextForBlastOutput = new byte[][]{" Strand = Plus / Plus\n".getBytes(), " Strand = Minus / Plus\n".getBytes()};
                frameRank2Frame = new int[]{1, -1};
                break;
            case dnaForwardOnly:
                numberOfFrames = 1;
                sequenceLength = null;
                original = null;
                frameTextForBlastOutput = new byte[][]{" Strand = Plus / Plus\n".getBytes()};
                frameRank2Frame = new int[]{1};
                break;
            case dnaBackwardOnly:
                numberOfFrames = 1;
                sequenceLength = null;
                original = (keepOriginal ? new byte[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Strand = Minus / Plus\n".getBytes()};
                frameRank2Frame = new int[]{-1};
                break;
            case dnaTranslateBoth:
                numberOfFrames = 6;
                sequenceLength = (keepOriginal ? null : new int[approximateNumberOfQueries]);
                original = (keepOriginal ? new byte[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Frame = +1\n".getBytes(), " Frame = +2\n".getBytes(), " Frame = +3\n".getBytes(),
                        " Frame = -1\n".getBytes(), " Frame = -2\n".getBytes(), " Frame = -3\n".getBytes()};
                frameRank2Frame = new int[]{+1, +2, +3, -1, -2, -3};
                break;
            case dnaTranslateForwardOnly:
                numberOfFrames = 3;
                sequenceLength = (keepOriginal ? null : new int[approximateNumberOfQueries]);
                original = (keepOriginal ? new byte[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Frame = +1\n".getBytes(), " Frame = +2\n".getBytes(), " Frame = +3\n".getBytes()};
                frameRank2Frame = new int[]{+1, +2, +3};
                break;
            case dnaTranslateBackwardOnly:
                numberOfFrames = 3;
                sequenceLength = (keepOriginal ? null : new int[approximateNumberOfQueries]);
                original = (keepOriginal ? new byte[approximateNumberOfQueries][] : null);
                frameTextForBlastOutput = new byte[][]{" Frame = -1\n".getBytes(), " Frame = -2\n".getBytes(), " Frame = -3\n".getBytes()};
                frameRank2Frame = new int[]{-1, -2, -3};
                break;
            default:
            case prot:
                numberOfFrames = 1;
                sequenceLength = null;
                original = null;
                frameTextForBlastOutput = new byte[0][0];
                frameRank2Frame = null;
        }
        sequences = new byte[approximateNumberOfQueries][][];
    }

    /**
     * read and index batch of queries
     *
     * @param it
     * @param queryBatchSize
     * @param progress
     * @return number of sequences read
     */
    public long loadFastAFile(FastAFileIteratorBytes it, int queryBatchSize, final ProgressPercentage progress) throws IOException {
        clear();
        progress.setMaximum(it.getMaximumProgress());
        progress.setProgress(0);
        while (it.hasNext()) {
            byte[] header = it.next();
            if (it.hasNext()) {
                byte[] sequence = it.next();
                if (!complexityMeasure.filter(sequence)) {
                    //System.err.println("LOW: " + Basic.toString(sequence));
                    numberLowComplexitySequences++;
                } else {
                    add(header, sequence);
                }
                progress.setProgress(it.getProgress());
                if (numberOfSequences == queryBatchSize)
                    break;
            }
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
    public void computeSeeds(final int chunk, final int numberOfChunks, final int numberOfParts, final int low, final int high, final SeedShape seedShape, final SeedStore[] seedStores, final ProgressPercentage progress) {
        final byte[] seedBytes = seedShape.createBuffer();
        final SeedStore seedStore = seedStores[0];
        final IAlphabet alphabet = (sequenceType == SequenceType.dnax ? new ProteinAlphabet() : sequenceAlphabet);

        if (progress != null) {
            progress.setMaximum(high - low);
            progress.setProgress(0);
        }

        final int chunkMask = numberOfChunks - 1;
        final int partsBits = numberOfParts - 1;
        final int numberOfChunksBits = Utilities.getMostSignificantBit(numberOfChunks);

        for (int sequenceId = low; sequenceId < high; sequenceId++) {
            if (queryMatchesCache == null || !queryMatchesCache.contains(getOriginalSequence(sequenceId))) {
                final byte[][] sequenceFrames = sequences[sequenceId];
                for (byte f = 0; f < sequenceFrames.length; f++) {
                    final byte[] sequence = sequenceFrames[f];
                    if (sequence != null) {
                        for (int pos = 0; pos < sequence.length - seedShape.getLength() + 1; pos++) {
                            seedShape.getSeed(sequence, pos, seedBytes);
                            final long value = seedStore.bytes2long(seedBytes);
                            long key = (value / ReferenceStore.PRIME_NUMBER); // need to divide by some number to get better distribution onto chunks
                            if (numberOfChunks == 1 || (key & chunkMask) == chunk) {
                                if (alphabet.isGoodSeed(seedBytes, seedShape.getWeight())) {
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
                    progress.incrementProgress();
            }
        }
    }

    /**
     * add a header and sequence to the list of sequences
     *
     * @param header
     * @param sequence
     */
    public void add(byte[] header, byte[] sequence) throws IOException {
        if (numberOfSequences == Utilities.MAX_ARRAY_SIZE)
            throw new IOException("Number of sequences exceeds max: " + Utilities.MAX_ARRAY_SIZE);

        if (numberOfSequences >= sequences.length) {
            headers = Utilities.grow(headers);
            sequences = Utilities.grow(sequences);
            if (sequenceLength != null)
                sequenceLength = Utilities.grow(sequenceLength);
            if (original != null)
                original = Utilities.grow(original);
        }
        headers[numberOfSequences] = header;
        if (original != null)
            original[numberOfSequences] = sequence;
        else if (sequenceLength != null)
            sequenceLength[numberOfSequences] = sequence.length;

        final byte[][] sequenceFrames = new byte[numberOfFrames][];
        sequences[numberOfSequences] = sequenceFrames;

        switch (sequenceAndStrandType) {
            case dnaForwardOnly:
                sequenceFrames[0] = sequence;
                break;
            case dnaBackwardOnly:
                sequenceFrames[0] = ((DNA5) sequenceAlphabet).getReverseComplement(sequence);

                break;
            case dnaBoth:
                sequenceFrames[0] = sequence;
                sequenceFrames[1] = ((DNA5) sequenceAlphabet).getReverseComplement(sequence);
                break;
            case dnaTranslateForwardOnly:
                sequenceFrames[0] = TranslatorNew.getTranslation(sequence, 1);
                sequenceFrames[1] = TranslatorNew.getTranslation(sequence, 2);
                sequenceFrames[2] = TranslatorNew.getTranslation(sequence, 3);
                break;
            case dnaTranslateBackwardOnly:
                sequenceFrames[0] = TranslatorNew.getTranslation(sequence, -1);
                sequenceFrames[1] = TranslatorNew.getTranslation(sequence, -2);
                sequenceFrames[2] = TranslatorNew.getTranslation(sequence, -3);
                break;
            case dnaTranslateBoth:
                sequenceFrames[0] = TranslatorNew.getTranslation(sequence, 1);
                sequenceFrames[1] = TranslatorNew.getTranslation(sequence, 2);
                sequenceFrames[2] = TranslatorNew.getTranslation(sequence, 3);
                sequenceFrames[3] = TranslatorNew.getTranslation(sequence, -1);
                sequenceFrames[4] = TranslatorNew.getTranslation(sequence, -2);
                sequenceFrames[5] = TranslatorNew.getTranslation(sequence, -3);
                break;
            default:
            case prot:
                sequenceFrames[0] = sequence;
                break;
        }
        numberOfLetters += sequence.length;
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
        numberLowComplexitySequences = 0;
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
     * Get sequence
     *
     * @param index index starting at 0
     * @return sequence
     */
    public byte[] getSequence(int index, byte frameRank) {
        return sequences[index][frameRank];
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
        else if (original != null)
            return original[index].length;
        else
            return sequences[index][0].length;
    }

    /**
     * get the original read sequence
     *
     * @param index
     * @return original sequence
     */
    public byte[] getOriginalSequence(int index) {
        if (original != null)
            return original[index];
        else
            return getSequence(index, (byte) 0);
    }

}
