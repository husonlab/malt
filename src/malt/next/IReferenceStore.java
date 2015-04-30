package malt.next;

import jloda.util.ProgressPercentage;
import malt.sequence.SeedShape2;

/**
 * Created by huson on 8/27/14.
 */
public interface IReferenceStore {
    byte[] getHeader(int index);

    byte[] getSequence(int index);

    int getNumberOfSequences();

    void computeSeeds(int chunk, int numberOfChunks, int numberOfParts, int low, int high, SeedShape2 seedShape, SeedStore[] seedStores, ProgressPercentage progress);
}
