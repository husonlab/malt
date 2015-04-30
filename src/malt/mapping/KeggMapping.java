package malt.mapping;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import malt.data.ISequenceAccessor;
import malt.data.RefIndex2ClassId;
import malt.data.ReferencesDBBuilder;
import megan.access.ClassificationMapper;

import java.io.File;
import java.io.IOException;

/**
 * Maintains mapping from Reference indices to Kegg
 * Daniel Huson, 8.2014
 */
public class KeggMapping extends RefIndex2ClassId {
    private final static byte[] MAGIC_NUMBER = "MAKeggV1.0.".getBytes();

    /**
     * construct a table
     *
     * @param maxIndex
     */
    public KeggMapping(int maxIndex) {
        super(maxIndex);
    }

    /**
     * compute the mapping for the given reference database
     *
     * @param referencesDB
     * @param progress
     */
    public static KeggMapping create(ISequenceAccessor referencesDB, ClassificationMapper classificationMapper, ProgressListener progress) throws CanceledException {
        KeggMapping mapping = new KeggMapping(referencesDB.getNumberOfSequences());

        progress.setMaximum(referencesDB.getNumberOfSequences());
        progress.setProgress(0);
        for (int i = 0; i < referencesDB.getNumberOfSequences(); i++) {
            String header = Basic.toString(referencesDB.getHeader(i));
            Integer classId = classificationMapper.getKeggIdFromHeaderLine(header);
            if (classId != null) {
                mapping.put(i, classId);
                referencesDB.extendHeader(i, ReferencesDBBuilder.KEGG_TAG, classId);
            }
            progress.incrementProgress();
        }
        if (progress instanceof ProgressPercentage)
            progress.close();

        return mapping;
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws java.io.IOException
     */
    public void save(File file) throws IOException, CanceledException {
        super.save(file, MAGIC_NUMBER);
    }

    /**
     * construct from an existing file
     *
     * @param file
     * @throws java.io.IOException
     * @throws jloda.util.CanceledException
     */
    public KeggMapping(File file) throws IOException, CanceledException {
        super(file, MAGIC_NUMBER);
    }

}
