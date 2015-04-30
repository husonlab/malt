package malt.malt2;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.data.IAlphabet;
import malt.data.SeedShape;
import malt.io.FastAFileIteratorBytes;

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
    private byte[][] sequences;
    private Mask[] masks; // one for each seedShape and reference

    private final IAlphabet sequenceAlphabet;
    private final byte numberOfSeedShapes;

    private int numberOfSequences;
    private long numberOfLetters;

    /**
     * constructor
     *
     * @param refAlphabet       alphabet used in reference (not alphabet used for seeds)
     * @param numberOfSequences used for initial size
     */
    public ReferenceStore(IAlphabet refAlphabet, byte numberOfSeedShapes, int numberOfSequences) {
        this.sequenceAlphabet = refAlphabet;
        this.numberOfSeedShapes = numberOfSeedShapes;

        headers = new byte[numberOfSequences][];
        sequences = new byte[numberOfSequences][];
        masks = null;
    }

    /**
     * erase
     */
    public void clear(boolean freeMemory) {
        numberOfSequences = 0;
        numberOfLetters = 0;
        if (freeMemory) {
            sequences = new byte[0][];
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
    @Override
    public byte[] getHeader(int index) {
        return headers[Math.abs(index)];
    }

    /**
     * get sequence
     *
     * @param index index starting at 0
     * @return sequence
     */
    @Override
    public byte[] getSequence(int index) {
        return sequences[index];
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
    @Override
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
        return sequences[index].length;
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
     * load data from a fastA file
     *
     * @param file
     * @throws java.io.FileNotFoundException
     */
    public void loadFastAFile(final File file) throws IOException, CanceledException {
        final FastAFileIteratorBytes it = new FastAFileIteratorBytes(file.getPath(), sequenceAlphabet);
        ProgressPercentage progress = new ProgressPercentage("Reading *reference* file: " + file);
        progress.setMaximum(it.getMaximumProgress());
        progress.setProgress(0);

        try {
            while (it.hasNext()) {
                byte[] header = it.next();
                if (it.hasNext()) {
                    byte[] sequence = it.next();
                    add(header, sequence);
                    progress.setProgress(it.getProgress());
                }
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
     * @throws IOException
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
                    masks[i] = new Mask(numberOfSeedShapes, sequences[i].length);
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
     * add a header and sequence to the list of sequences
     *
     * @param header
     * @param sequence
     */
    public void add(byte[] header, byte[] sequence) throws IOException {
        if (numberOfSequences == MAX_ARRAY_SIZE)
            throw new IOException("Number of sequences exceeds max: " + MAX_ARRAY_SIZE);

        if (numberOfSequences == sequences.length) {
            headers = Utilities.grow(headers);
            sequences = Utilities.grow(sequences);
        }
        headers[numberOfSequences] = header;
        sequences[numberOfSequences] = sequence;
        numberOfLetters += sequence.length;
        numberOfSequences++;
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
    @Override
    public void computeSeeds(final int chunk, final int numberOfChunks, final int numberOfParts, final int low, final int high,
                             final SeedShape seedShape, final SeedStore[] seedStores, final ProgressPercentage progress) {
        final byte[] seedBytes = seedShape.createBuffer();
        final int chunkMask = numberOfChunks - 1;
        final SeedStore seedStore = seedStores[0]; // all seed stores here use the same shape, so ok to use for computing bytes2long() below

        if (progress != null) {
            progress.setMaximum(high - low);
            progress.setProgress(0);
        }

        final int partsBits = numberOfParts - 1;
        final int numberOfChunksBits = Utilities.getMostSignificantBit(numberOfChunks);

        for (int sequenceId = low; sequenceId < high; sequenceId++) {
            final byte[] sequence = sequences[sequenceId];
            final Mask mask = getMask(sequenceId);

            for (int pos = 0; pos < sequence.length - seedShape.getLength() + 1; pos++) {
                seedShape.getSeed(sequence, pos, seedBytes);
                final long value = seedStore.bytes2long(seedBytes);
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
}
