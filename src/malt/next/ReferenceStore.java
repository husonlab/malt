package malt.next;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.io.FastAFileIteratorBytes;
import malt.sequence.Alphabet;
import malt.sequence.ISeedExtractor;
import malt.sequence.SeedShape2;
import malt.sequence.SequenceEncoder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * database of reference sequences and their seeds
 * <p/>
 * Huson, 8.2014
 */
public class ReferenceStore implements IReferenceStore {
    public final static int PRIME_NUMBER = 10007;

    public final static int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private byte[][] headers;
    private long[][] sequenceCodes;
    private Mask[] masks; // one for each seedShape and reference

    private final Alphabet sequenceAlphabet;
    private final SequenceEncoder sequenceEncoder;
    private final ISeedExtractor seedExtractor;

    private final byte numberOfSeedShapes;


    private int numberOfSequences;
    private long numberOfLetters;

    /**
     * constructor
     *
     * @param refAlphabet       alphabet used in reference (not alphabet used for seeds)
     * @param numberOfSequences used for initial size
     */
    public ReferenceStore(Alphabet refAlphabet, ISeedExtractor seedExtractor, byte numberOfSeedShapes, int numberOfSequences) {
        this.sequenceAlphabet = refAlphabet;
        this.seedExtractor = seedExtractor;
        this.sequenceEncoder = new SequenceEncoder(refAlphabet);
        this.numberOfSeedShapes = numberOfSeedShapes;

        headers = new byte[numberOfSequences][];
        sequenceCodes = new long[numberOfSequences][];
        masks = null;
    }

    /**
     * erase
     */
    public void clear(boolean freeMemory) {
        numberOfSequences = 0;
        numberOfLetters = 0;
        if (freeMemory) {
            sequenceCodes = new long[0][];
            headers = new byte[0][];
            masks = null;
        }
    }

    /**
     * Get header string
     *
     * @param index index starting at 0
     * @return header
     */
    public byte[] getHeader(int index) {
        return headers[Math.abs(index)];
    }

    /**
     * Get sequence code
     *
     * @param index index starting at 0
     * @return sequence
     */
    public long[] getSequenceCode(int index) {
        return sequenceCodes[index];
    }

    /**
     * get sequence
     *
     * @param index index starting at 0
     * @return sequence
     */
    public byte[] getSequence(int index) {
        return sequenceEncoder.decode(sequenceCodes[index]);
    }

    /**
     * get mask
     *
     * @param index
     * @return mask
     */
    public Mask getMask(int index) {
        if (masks == null)
            return null;
        else
            return masks[index];
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
     * get sequence length for the given index
     *
     * @param index
     * @return length
     */
    public int getSequenceLength(int index) {
        return sequenceEncoder.computeLength(sequenceCodes[index]);
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
     * load data from a binary file
     *
     * @param file
     * @throws java.io.FileNotFoundException
     */
    public void read(final File file) throws IOException, CanceledException {
        final FastAFileIteratorBytes it = new FastAFileIteratorBytes(file.getPath(), sequenceAlphabet);
        ProgressPercentage progress = new ProgressPercentage("Reading *reference* file: " + file);
        progress.setMaximum(it.getMaximumProgress());
        progress.setProgress(0);

        if (headers.length < numberOfSequences) { // resize
            headers = new byte[numberOfSequences][];
            sequenceCodes = new long[numberOfSequences][];
            masks = new Mask[numberOfSequences];
        }

        try {
            for (int i = 0; i < numberOfSequences; i++) {
                headers[i] = it.next();
                byte[] sequence = it.next();
                sequenceCodes[i] = sequenceEncoder.encode(sequence);
                numberOfLetters += sequence.length;
            progress.incrementProgress();
        }
        } finally {
            it.close();
            progress.reportTaskCompleted();
        }
        System.err.println(String.format("Num. of references:%,13d", numberOfSequences));
        System.err.println(String.format("Number of letters:%,14d", numberOfLetters));
    }


    /**
     * load the mask file
     *
     * @param file
     * @throws java.io.IOException
     */
    public void loadMaskFile(final File file) throws IOException {
        if (file.exists()) {
            InputStream ins = Basic.getInputStreamPossiblyZIPorGZIP(file.getPath());
            ProgressPercentage progress = new ProgressPercentage("Reading mask file: " + file);
            progress.setMaximum(numberOfSequences);
            progress.setProgress(0);

            try {
                masks = new Mask[numberOfSequences];
                for (int i = 0; i < numberOfSequences; i++) {
                    masks[i] = new Mask(numberOfSeedShapes, getSequenceLength(i));
                    masks[i].read(ins);
                    progress.setProgress(i);
                }
            } finally {
                ins.close();
                progress.reportTaskCompleted();
            }
        } else
            System.err.println("No mask file");
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
    public void computeSeeds(final int chunk, final int numberOfChunks, final int numberOfParts, final int low, final int high,
                             final SeedShape2 seedShape, final SeedStore[] seedStores,
                             final ProgressPercentage progress) {
        final int chunkMask = numberOfChunks - 1;
        final boolean[] seedMask = seedShape.getMask();

        if (progress != null) {
            progress.setMaximum(high - low);
            progress.setProgress(0);
        }

        final int partsBits = numberOfParts - 1;
        final int numberOfChunksBits = Utilities.getMostSignificantBit(numberOfChunks);

        for (int sequenceId = low; sequenceId < high; sequenceId++) {
            final long[] sequenceCode = sequenceCodes[sequenceId];
            final int sequenceLength = sequenceEncoder.computeLength(sequenceCode);
            final Mask mask = getMask(sequenceId);

            for (int pos = 0; pos < sequenceLength - seedShape.getLength() + 1; pos++) {
                final long value = seedExtractor.getSeedCode(seedMask, seedShape.getWeight(), sequenceCode, pos);
                long key = (value / PRIME_NUMBER); // need to divide by some number to get better distribution onto chunks

                if (numberOfChunks == 1 || (key & chunkMask) == chunk) {
                    if (mask == null || !mask.get(seedShape.getId(), pos)) {
                        final int part = (int) ((key >>> numberOfChunksBits) & partsBits);
                        synchronized (seedStores[part]) {
                            seedStores[part].add(value, sequenceId, pos);
                        }
                    }
                }
            }
            if (progress != null)
                progress.setProgress(sequenceId - low);
        }
    }

    public SequenceEncoder getSequenceEncoder() {
        return sequenceEncoder;
    }

    public ISeedExtractor getSeedExtractor() {
        return seedExtractor;
    }
}
