package malt.next;

import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.data.ISequenceAccessor;
import malt.io.FastAFileIteratorBytes;
import malt.sequence.Alphabet;
import malt.sequence.ISeedExtractor;
import malt.sequence.SeedShape2;
import malt.sequence.SequenceEncoder;

import java.io.*;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Analyses the reference data
 * Created by huson on 8/19/14.
 */
public class ReferenceStoreBuilder implements IReferenceStore, ISequenceAccessor {
    public final static int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private byte[][] headers;
    private byte[][] sequences;
    private long[][] sequenceCodes;
    private Mask[] masks; // one for each seedShape and reference

    private final Alphabet sequenceAlphabet;
    private final ISeedExtractor seedExtractor;
    private final SequenceEncoder sequenceEncoder;
    private final SeedShape2[] seedShapes;
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
    public ReferenceStoreBuilder(Alphabet sequenceAlphabet, ISeedExtractor seedExtractor, final SeedShape2[] seedShapes) {
        this.sequenceAlphabet = sequenceAlphabet;
        this.seedExtractor = seedExtractor;
        this.sequenceEncoder = new SequenceEncoder(sequenceAlphabet);
        this.seedShapes = seedShapes;
        this.seedCounts = new long[seedShapes.length];
        headers = new byte[1000000][];
        sequences = new byte[1000000][];
        sequenceCodes = new long[1000000][];
        masks = new Mask[1000000];

        letterCounts = new long[seedShapes.length][127];
    }

    /**
     * Processes all reference files. The seeds for each seed shape are counted
     *
     * @param fileNames
     * @throws java.io.IOException
     * @throws jloda.util.CanceledException
     */
    public void processFastAFiles(final List<String> fileNames) throws IOException, CanceledException {
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
                    final long[] sequenceCode = sequenceEncoder.encode(sequence);
                    add(header, sequence, sequenceCode);

                    for (int t = 0; t < seedShapes.length; t++) {
                        final SeedShape2 seedShape = seedShapes[t];
                        for (byte letter : sequence) {
                            letterCounts[t][letter]++;
                        }
                        for (int pos = 0; pos < sequence.length - seedShapes[t].getLength() + 1; pos++) {
                            final long value = seedExtractor.getSeedCode(seedShape.getMask(), seedShape.getWeight(), sequenceCode, pos);
                            if (seedExtractor.isGoodSeed(value, seedShape.getWeight())) {
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

        for (int t = 0; t < seedShapes.length; t++) {
            final long[] counts = letterCounts[t];
            System.err.println("Letter counts for seed shape (" + t + ") :");
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] != 0)
                    System.err.println(String.format("%c:%,15d", (char) i, counts[i]));
            }
        }
    }

    /**
     * add a header and sequence to the list of sequenceCodes
     *
     * @param header
     * @param sequenceCode
     */
    private void add(byte[] header, byte[] sequence, long[] sequenceCode) throws IOException {
        if (numberOfSequences == MAX_ARRAY_SIZE)
            throw new IOException("Number of sequences exceeds max: " + MAX_ARRAY_SIZE);

        if (numberOfSequences == sequenceCodes.length) {
            final int newLength = (int) Math.min(MAX_ARRAY_SIZE, 2l * Math.max(16, sequenceCodes.length));

            {
                byte[][] tmp = new byte[newLength][];
                System.arraycopy(headers, 0, tmp, 0, headers.length);
                headers = tmp;
            }
            {
                byte[][] tmp = new byte[newLength][];
                System.arraycopy(sequences, 0, tmp, 0, sequences.length);
                sequences = tmp;
            }
            {
                long[][] tmp = new long[newLength][];
                System.arraycopy(sequenceCodes, 0, tmp, 0, sequenceCodes.length);
                sequenceCodes = tmp;
            }
            {
                Mask[] tmp = new Mask[newLength];
                System.arraycopy(masks, 0, tmp, 0, masks.length);
                masks = tmp;
            }
        }
        headers[numberOfSequences] = header;
        sequences[numberOfSequences] = sequence;
        sequenceCodes[numberOfSequences] = sequenceCode;
        masks[numberOfSequences] = new Mask((byte) seedShapes.length, sequence.length);
        numberOfSequences++;
        numberOfLetters += sequence.length;
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
     * write to a file in binary format
     *
     * @param file
     */
    public void write(final File file, boolean shortHeaders) throws IOException {
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
     * @throws java.io.IOException
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

    public Alphabet getSequenceAlphabet() {
        return sequenceAlphabet;
    }

    public SeedShape2[] getSeedShapes() {
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
        return sequenceEncoder.decode(sequenceCodes[index]);
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
    public void computeSeeds(final int chunk, final int numberOfChunks, final int numberOfParts, final int low, final int high, final SeedShape2 seedShape, final SeedStore[] seedStores, final ProgressPercentage progress) {
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
            int sequenceLength = sequenceEncoder.computeLength(sequenceCode);

            for (int pos = 0; pos < sequenceLength - seedShape.getLength() + 1; pos++) {
                final long value = seedExtractor.getSeedCode(seedMask, seedShape.getWeight(), sequenceCode, pos);
                long key = (value / ReferenceStore.PRIME_NUMBER); // need to divide by some number to get better distribution onto chunks

                if (numberOfChunks == 1 || (key & chunkMask) == chunk) {
                    if (seedExtractor.isGoodSeed(value, seedShape.getWeight())) {
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
