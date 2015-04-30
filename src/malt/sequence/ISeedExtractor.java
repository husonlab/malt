package malt.sequence;

/**
 * Created by huson on 10/1/14.
 */
public interface ISeedExtractor {
    byte[] decodeSeed(long seedCode, int seedWeight);

    /**
     * extract a seed from a sequence code
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos          @return seed
     */
    public long getSeedCode(boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos);

    /**
     * extract a seed from a sequence code
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos
     * @param failValue    value returned if sequence too short   @return seed
     */
    public long getSeedCode(boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos, int failValue);

    /**
     * is this a good seed?
     *
     * @param seedCode
     * @return true, if good
     */
    public boolean isGoodSeed(long seedCode, int seedWeight);

    /**
     * get the number of bits per letter
     *
     * @return bits
     */
    public int getBitsPerLetter();
}
