package malt.malt2;

import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.data.IAlphabet;
import malt.data.ISequenceAccessor;
import malt.data.SeedShape;
import malt.io.FastAFileIteratorBytes;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Analyses the reference data
 * Created by huson on 8/19/14.
 */
public class ReferenceStoreBuilder implements IReferenceStore, ISequenceAccessor {
    public final static int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private byte[][] headers;
    private byte[][] sequences;
    private Mask[] masks; // one for each seedShape and reference

    private final IAlphabet sequenceAlphabet;
    private final SeedShape[] seedShapes;
    private final long[][] letterCounts;
    private final long[] seedCounts;
    private int numberOfSequences;
    private long numberOfLetters;
    private long numberOfSeeds;

    /**
     * constructor
     *
     * @param sequenceAlphabet
     * @param seedShapes
     */
    public ReferenceStoreBuilder(IAlphabet sequenceAlphabet, final SeedShape[] seedShapes) {
        this.sequenceAlphabet = sequenceAlphabet;
        this.seedShapes = seedShapes;
        this.seedCounts = new long[seedShapes.length];
        headers = new byte[1000000][];
        sequences = new byte[1000000][];
        masks = new Mask[1000000];

        letterCounts = new long[seedShapes.length][127];
    }

    /**
     * Processes all reference files. The seeds for each seed shape are counted
     *
     * @param fileNames
     * @throws IOException
     * @throws CanceledException
     */
    public void processFastAFiles(final List<String> fileNames) throws IOException, CanceledException {
        final byte[] seedBytes = new byte[1000];

        for (int i = 0; i < seedCounts.length; i++)
            seedCounts[i] = 0;

        for (String fileName : fileNames) {
            final FastAFileIteratorBytes it = new FastAFileIteratorBytes(fileName, sequenceAlphabet);
            ProgressPercentage progress = new ProgressPercentage("Processing file: " + fileName);
            progress.setMaximum(it.getMaximumProgress());
            progress.setProgress(0);

            while (it.hasNext()) {
                final byte[] header = it.next();
                if (it.hasNext()) {
                    final byte[] sequence = it.next();
                    add(header, sequence);

                    for (int t = 0; t < seedShapes.length; t++) {
                        final SeedShape seedShape = seedShapes[t];
                        final IAlphabet alphabet = seedShape.getAlphabet();
                        for (byte letter : sequence) {
                            letterCounts[t][alphabet.getNormalized(letter)]++;
                        }
                        for (int pos = 0; pos < sequence.length - seedShapes[t].getLength() + 1; pos++) {
                            seedShape.getSeed(sequence, pos, seedBytes);
                            if (seedShape.getAlphabet().isGoodSeed(seedBytes, seedShape.getWeight())) {
                                seedCounts[t]++;
                                numberOfSeeds++;
                            }
                        }
                    }
                    progress.setProgress(it.getProgress());
                }
            }
            it.close();
            progress.reportTaskCompleted();
        }

        System.err.println(String.format("Total sequences:  %,11d", numberOfSequences));
        System.err.println(String.format("Total letters:%,15d", numberOfLetters));
        System.err.println(String.format("Total seeds:  %,15d", numberOfSeeds));

        Set<String> seen = new HashSet<String>();
        for (int t = 0; t < seedShapes.length; t++) {
            final IAlphabet alphabet = seedShapes[t].getAlphabet();
            if (!seen.contains(alphabet.getName())) {
                seen.add(alphabet.getName());
                final long[] counts = letterCounts[t];
                System.err.println("Letter counts for: " + alphabet.getName() + ":");
                for (int i = 0; i < counts.length; i++) {
                    if (counts[i] != 0)
                        System.err.println(String.format("%c:%,15d", (char) i, counts[i]));
                }
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
        if (numberOfSequences == MAX_ARRAY_SIZE)
            throw new IOException("Number of sequences exceeds max: " + MAX_ARRAY_SIZE);

        if (numberOfSequences == sequences.length) {
            headers = Utilities.grow(headers);
            sequences = Utilities.grow(sequences);
            masks = Utilities.grow(masks);
        }
        headers[numberOfSequences] = header;
        sequences[numberOfSequences] = sequence;
        masks[numberOfSequences] = new Mask((byte) seedShapes.length, sequence.length);
        numberOfLetters += sequence.length;
        numberOfSequences++;
    }

    /**
     * gets the mask for a given reference sequence
     *
     * @param sequenceNumber
     * @return mask
     */
    public Mask getMask(int sequenceNumber) {
        return masks[sequenceNumber];
    }

    /**
     * write reference sequences to a file
     *
     * @param file
     * @throws IOException
     */
    public void write(File file, boolean shortHeaders) throws IOException {
        final DataOutputStream outs;

        if (file.getPath().endsWith(".gz"))
            outs = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192)));
        else
            outs = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192));

        final ProgressPercentage progress = new ProgressPercentage("Writing file: " + file, numberOfSequences);

        for (int sequenceNumber = 0; sequenceNumber < numberOfSequences; sequenceNumber++) {
            final byte[] header;
            if (shortHeaders)
                header = malt.util.Utilities.getFirstWord(headers[sequenceNumber]);
            else
                header = headers[sequenceNumber];
            outs.write(header, 0, header.length);
            outs.write('\n');
            final byte[] sequence = sequences[sequenceNumber];
            outs.write(sequence, 0, sequence.length);
            outs.write('\n');
            progress.setProgress(sequenceNumber);
        }
        outs.close();
        progress.close();
    }

    /**
     * write all masks
     *
     * @param file
     * @throws IOException
     */
    public void writeMasks(File file) throws IOException {
        final OutputStream outs;

        if (file.getPath().endsWith(".gz"))
            outs = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192));
        else
            outs = new BufferedOutputStream(new FileOutputStream(file), 8192);

        final ProgressPercentage progress = new ProgressPercentage("Writing file: " + file, numberOfSequences);

        for (int sequenceNumber = 0; sequenceNumber < numberOfSequences; sequenceNumber++) {
            getMask(sequenceNumber).write(outs);
            progress.incrementProgress();
        }
        outs.close();
        progress.close();
    }

    public IAlphabet getSequenceAlphabet() {
        return sequenceAlphabet;
    }

    public SeedShape[] getSeedShapes() {
        return seedShapes;
    }

    public long[] getSeedCounts() {
        return seedCounts;
    }

    @Override
    public int getNumberOfSequences() {
        return numberOfSequences;
    }

    public long getNumberOfLetters() {
        return numberOfLetters;
    }

    public long getNumberOfSeeds() {
        return numberOfSeeds;
    }

    @Override
    public byte[] getHeader(int index) {
        return headers[index];
    }

    @Override
    public byte[] getSequence(int index) {
        return sequences[index];
    }

    /**
     * extend the header by the given tag. We use this to write the taxon id into a reference sequence
     *
     * @param index
     * @param tag
     * @param id
     */
    @Override
    public void extendHeader(int index, String tag, Integer id) {
        byte[] header = headers[index];
        int pos = 0;
        while (Character.isWhitespace(header[pos]) && pos < header.length) // skip leading white space
            pos++;
        while (!Character.isWhitespace(header[pos]) && pos < header.length) // go to next white space or end
            pos++;
        byte[] add;
        if (header[pos - 1] == '|')
            add = String.format("%s|%d|", tag, id).getBytes();
        else
            add = String.format("|%s|%d|", tag, id).getBytes();

        byte[] newHeader = new byte[header.length + add.length];
        System.arraycopy(header, 0, newHeader, 0, pos);
        System.arraycopy(add, 0, newHeader, pos, add.length);
        if (pos < header.length) {
            System.arraycopy(header, pos, newHeader, add.length + pos, header.length - pos);
        }
        headers[index] = newHeader;
        //System.err.println("Header="+Basic.toString(headers[Math.abs(index)- 1]));

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
        final int chunkMask = numberOfChunks - 1;
        final SeedStore seedStore = seedStores[0]; // all seed stores here use the same shape, so ok to use for computing bytes2long() below

        if (progress != null) {
            progress.setMaximum(high - low);
            progress.setProgress(0);
        }

        final int partsBits = numberOfParts - 1;
        final int numberOfChunksBits = Utilities.getMostSignificantBit(numberOfChunks);

        for (int sequenceId = low; sequenceId < high; sequenceId++) {
            final byte[] sequence = getSequence(sequenceId);

            for (int pos = 0; pos < sequence.length - seedShape.getLength() + 1; pos++) {
                seedShape.getSeed(sequence, pos, seedBytes);
                final long value = seedStore.bytes2long(seedBytes);
                long key = (value / 17); // need to divide by some number to get better distribution onto chunks

                if (numberOfChunks == 1 || (key & chunkMask) == chunk) {
                    if (sequenceAlphabet.isGoodSeed(seedBytes, seedShape.getWeight())) {
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
