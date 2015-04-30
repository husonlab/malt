package malt.next;

/**
 * Created by huson on 8/22/14.
 */
public interface IComplexityMeasure {
    float getComplexity(byte[] s);

    boolean filter(byte[] s);
}
