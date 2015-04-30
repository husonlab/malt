package malt.next;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.data.SequenceType;
import malt.sequence.DNA5Alphabet;
import malt.sequence.ProteinAlphabet;
import malt.sequence.ReducedAlphabet;
import malt.sequence.SeedShape2;

import java.io.*;

/**
 * malt index file. Stores seed shapes and seed counts
 * <p/>
 * Daniel Huson, 8.2104
 */
public class MaltIndexFile {
    public static final byte[] MAGIC_NUMBER = "MaltIndexV0.4.".getBytes();

    private malt.sequence.ReducedAlphabet reducedAlphabet;
    private SeedShape2[] seedShapes;
    private long[] seedCounts;
    private int maxRefOccurrencesPerSeed;
    private int numberOfSequences;
    private long numberOfLetters;
    private SequenceType sequenceType;

    private boolean doTaxonomy;
    private boolean doKegg;
    private boolean doSeed;
    private boolean doCog;

    /**
     * default constructor
     */
    public MaltIndexFile() {
    }

    /**
     * write this index to a file
     *
     * @param file
     * @throws java.io.IOException
     */
    public void write(File file) throws IOException {
        final ProgressPercentage progressPercentage = new ProgressPercentage("Writing file: " + file);

        final DataOutputStream outs = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192));
        try {
            outs.write(MAGIC_NUMBER);
            {
                byte[] bytes = sequenceType.toString().getBytes();
                outs.writeInt(bytes.length);
                outs.write(bytes, 0, bytes.length);
            }
            outs.writeInt(numberOfSequences);
            outs.writeLong(numberOfLetters);

            if (reducedAlphabet == null) {
                outs.writeInt(0);
            } else {
                byte[] alphaDef = reducedAlphabet.getDefinitionString().getBytes();
                outs.writeInt(alphaDef.length);
                for (byte anAlphaDef : alphaDef) outs.writeByte(anAlphaDef);
            }

            outs.writeInt(seedShapes.length);
            for (int t = 0; t < seedShapes.length; t++) {
                final SeedShape2 shape = seedShapes[t];
                {
                    byte[] bytes = shape.toString().getBytes();
                    outs.writeInt(bytes.length);
                    outs.write(bytes, 0, bytes.length);
                }
                outs.writeLong(seedCounts[t]);
            }
            outs.writeInt(maxRefOccurrencesPerSeed);
            outs.writeBoolean(doTaxonomy);
            outs.writeBoolean(doKegg);
            outs.writeBoolean(doSeed);
            outs.writeBoolean(doCog);
        } finally {
            progressPercentage.reportTaskCompleted();
            outs.close();
        }
    }

    /**
     * read the index from a file
     *
     * @param file
     * @throws java.io.IOException
     */
    public void read(File file) throws IOException {
        final ProgressPercentage progressPercentage = new ProgressPercentage("Reading file: " + file);

        final DataInputStream ins = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 8192));
        try {
            Basic.readAndVerifyMagicNumber(ins, MAGIC_NUMBER);
            {
                int length = ins.readInt();
                byte[] bytes = new byte[length];
                if (ins.read(bytes, 0, length) < length)
                    throw new IOException("read failed");
                sequenceType = SequenceType.valueOf(Basic.toString(bytes));
            }
            numberOfSequences = ins.readInt();
            numberOfLetters = ins.readLong();

            int defStringLength = ins.readInt();
            if (defStringLength == 0) {
                reducedAlphabet = null;
            } else {
                byte[] defStringByte = new byte[defStringLength];
                for (int i = 0; i < defStringLength; i++)
                    defStringByte[i] = ins.readByte();
                reducedAlphabet = new ReducedAlphabet(sequenceType == SequenceType.Protein ? ProteinAlphabet.getInstance() :
                        DNA5Alphabet.getInstance(), Basic.toString(defStringByte));
            }
            int numberOfShapes = ins.readInt();
            seedShapes = new SeedShape2[numberOfShapes];
            seedCounts = new long[numberOfShapes];
            for (int t = 0; t < numberOfShapes; t++) {
                {
                    int length = ins.readInt();
                    byte[] bytes = new byte[length];
                    if (ins.read(bytes, 0, length) < length)
                        throw new IOException("read failed");
                    String name = Basic.toString(bytes);
                    seedShapes[t] = new SeedShape2(name);
                    seedShapes[t].setId(t);
                }
                seedCounts[t] = ins.readLong();
            }
            maxRefOccurrencesPerSeed = ins.readInt();
            doTaxonomy = ins.readBoolean();
            doKegg = ins.readBoolean();
            doSeed = ins.readBoolean();
            doCog = ins.readBoolean();
        } finally {
            progressPercentage.reportTaskCompleted();
            ins.close();
        }
    }

    /**
     * reduce the number of seed shapes to the given number
     *
     * @param maxSeedShapes
     */
    public void reduceNumberOfSeedShapes(int maxSeedShapes) {
        if (seedShapes.length > maxSeedShapes) {
            System.err.println("Using only " + maxSeedShapes + " of " + seedShapes.length + " available seed shapes");
            SeedShape2[] tmpSeedShapes = new SeedShape2[maxSeedShapes];
            System.arraycopy(seedShapes, 0, tmpSeedShapes, 0, maxSeedShapes);
            seedShapes = tmpSeedShapes;
            long[] tmpSeedCounts = new long[maxSeedShapes];
            System.arraycopy(seedCounts, 0, tmpSeedCounts, 0, maxSeedShapes);
            seedCounts = tmpSeedCounts;
        }

    }

    public static byte[] getMAGIC_NUMBER() {
        return MAGIC_NUMBER;
    }

    public SequenceType getSequenceType() {
        return sequenceType;
    }

    public void setSequenceType(SequenceType sequenceType) {
        this.sequenceType = sequenceType;
    }

    public SeedShape2[] getSeedShapes() {
        return seedShapes;
    }

    public void setSeedShapes(SeedShape2[] seedShapes) {
        this.seedShapes = seedShapes;
    }

    public long[] getSeedCounts() {
        return seedCounts;
    }

    public void setSeedCounts(long[] seedCounts) {
        this.seedCounts = seedCounts;
    }

    public int getMaxRefOccurrencesPerSeed() {
        return maxRefOccurrencesPerSeed;
    }

    public void setMaxRefOccurrencesPerSeed(int maxRefOccurrencesPerSeed) {
        this.maxRefOccurrencesPerSeed = maxRefOccurrencesPerSeed;
    }

    public int getNumberOfSequences() {
        return numberOfSequences;
    }

    public byte getNumberOfSeedShapes() {
        return (byte) seedShapes.length;
    }

    public void setNumberOfSequences(int numberOfSequences) {
        this.numberOfSequences = numberOfSequences;
    }

    public long getNumberOfLetters() {
        return numberOfLetters;
    }

    public void setNumberOfLetters(long numberOfLetters) {
        this.numberOfLetters = numberOfLetters;
    }

    public ReducedAlphabet getReducedAlphabet() {
        return reducedAlphabet;
    }

    public void setReducedAlphabet(ReducedAlphabet reducedAlphabet) {
        this.reducedAlphabet = reducedAlphabet;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(sequenceType.toString()).append("\n");
        buf.append(reducedAlphabet).append("\n");
        buf.append(numberOfSequences).append("\n");
        buf.append(numberOfLetters).append("\n");
        buf.append(seedShapes.length).append("\n");
        for (int t = 0; t < seedShapes.length; t++) {
            final SeedShape2 shape = seedShapes[t];
            buf.append(shape.toString()).append("\n");
            buf.append(seedCounts[t]).append("\n");
        }
        buf.append("doTax.: ").append(doTaxonomy).append("\n");
        buf.append("doKegg: ").append(doKegg).append("\n");
        buf.append("doSeed: ").append(doSeed).append("\n");
        buf.append("doCog:  ").append(doCog).append("\n");
        return buf.toString();
    }

    public boolean isDoTaxonomy() {
        return doTaxonomy;
    }

    public void setDoTaxonomy(boolean doTaxonomy) {
        this.doTaxonomy = doTaxonomy;
    }

    public boolean isDoKegg() {
        return doKegg;
    }

    public void setDoKegg(boolean doKegg) {
        this.doKegg = doKegg;
    }

    public boolean isDoSeed() {
        return doSeed;
    }

    public void setDoSeed(boolean doSeed) {
        this.doSeed = doSeed;
    }

    public boolean isDoCog() {
        return doCog;
    }

    public void setDoCog(boolean doCog) {
        this.doCog = doCog;
    }
}
