package malt.data;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.util.Utilities;

import java.io.*;

/**
 * maintains a mapping from reference indices to class ids (e.g. taxon ids or KEGG KOs)
 * todo: mappings now start at 0, this breaks old Malt
 * Daniel Huson, 8.2014
 */
public class RefIndex2ClassId {
    private static final byte[] MAGIC_NUMBER = "MAClassV1.1.".getBytes();

    private int maxRefId;
    private int[] refIndex2ClassId;

    public RefIndex2ClassId(int numberOfReferences) {
        refIndex2ClassId = new int[numberOfReferences];
        maxRefId = numberOfReferences;
    }

    /**
     * put, indices start at 0
     *
     * @param refIndex
     * @param classId
     */
    public void put(int refIndex, int classId) {
        refIndex2ClassId[refIndex] = classId;
    }

    /**
     * get, indices start at 0
     *
     * @param refIndex
     * @return class id for given reference id
     */
    public int get(int refIndex) {
        return refIndex2ClassId[refIndex];
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws java.io.IOException
     */
    public void save(File file) throws IOException, CanceledException {
        save(file, MAGIC_NUMBER);
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws java.io.IOException
     */
    public void save(File file, byte[] magicNumber) throws IOException, CanceledException {
        final byte[] buffer = new byte[8];

        final ProgressPercentage progressListener = new ProgressPercentage("Writing file: " + file, maxRefId);
        final BufferedOutputStream outs = new BufferedOutputStream(new FileOutputStream(file), 8192);
        try {
            outs.write(magicNumber);

            // number of entries
            Utilities.writeInt(outs, maxRefId, buffer);

            // write headers and sequences:
            for (int i = 0; i < maxRefId; i++) {
                Utilities.writeInt(outs, refIndex2ClassId[i], buffer);
                // System.err.println("write: "+i+" "+refIndex2ClassId[i]);
                progressListener.incrementProgress();
            }
        } finally {
            progressListener.close();
            outs.close();
        }
    }

    /**
     * constructor from a file
     *
     * @param file
     */
    public RefIndex2ClassId(File file) throws IOException, CanceledException {
        this(file, MAGIC_NUMBER);
    }

    /**
     * constructor from a file
     *
     * @param file
     */
    public RefIndex2ClassId(File file, byte[] magicNumber) throws IOException, CanceledException {
        final byte[] buffer = new byte[8];

        final BufferedInputStream ins = new BufferedInputStream(new FileInputStream(file), 8192);
        ProgressPercentage progressListener = null;
        try {
            // check magic number:
            Basic.readAndVerifyMagicNumber(ins, magicNumber);
            maxRefId = Utilities.readInt(ins, buffer);
            progressListener = new ProgressPercentage("Reading file: " + file, maxRefId);
            refIndex2ClassId = new int[maxRefId + 1];
            // write headers and sequences:
            for (int i = 0; i < maxRefId; i++) {
                refIndex2ClassId[i] = Utilities.readInt(ins, buffer);
                // System.err.println("read: "+i+" "+refIndex2ClassId[i]);
                progressListener.incrementProgress();
            }
        } finally {
            if (progressListener != null)
            progressListener.close();
            ins.close();
        }
    }
}
