package malt.data;

/**
 * Created by huson on 8/22/14.
 */
public interface ISequenceAccessor {
    int getNumberOfSequences();

    byte[] getHeader(int index);

    byte[] getSequence(int index);

    void extendHeader(int index, String tag, Integer id);
}
