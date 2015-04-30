package malt.data;

/**
 * Created by huson on 10/23/14.
 */
public interface INormalizer {
    /**
     * returns normalized letter
     *
     * @param letter
     * @return normalized letter
     */
    public byte getNormalized(byte letter);
}
