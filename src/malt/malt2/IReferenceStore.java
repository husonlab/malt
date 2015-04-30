package malt.malt2;

import jloda.util.ProgressPercentage;
import malt.data.SeedShape;

/**
 * Created by huson on 8/27/14.
 */
public interface IReferenceStore {
    byte[] getHeader(int index);

    byte[] getSequence(int index);

    int getNumberOfSequences();

    void computeSeeds(int chunk, int numberOfChunks, int numberOfParts, int low, int high, SeedShape seedShape, SeedStore[] seedStores, ProgressPercentage progress);
}
