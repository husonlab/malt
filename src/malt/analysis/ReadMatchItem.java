package malt.analysis;

/**
 * a single read match
 * Daniel Huson, 8.2014
 */
public class ReadMatchItem {
    final public int refIndex;
    final public float score;
    final public int refStart;
    final public int refEnd;

    public ReadMatchItem(int refIndex, float score, int refStart, int refEnd) {
        this.refIndex = refIndex;
        this.score = score;
        this.refStart = refStart;
        this.refEnd = refEnd;
    }
}