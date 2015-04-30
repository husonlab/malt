package malt.malt2;

/**
 * computes the minimum complexity encountered in a DNA string
 * Daniel Huson, 9.2012
 */
public class ComplexityMeasureDNA implements IComplexityMeasure {
    private static final int N = 4; // alphabet size
    private static final int L = 16;  // window size
    private static final double LFactorial = 20922789888000.0;
    private final byte[] letter2index;
    private final double[] factorial;
    private final float minComplexity;

    /**
     * constructor
     */
    public ComplexityMeasureDNA(float minComplexity) {
        this.minComplexity = minComplexity;
        letter2index = new byte[127];
        for (int i = 0; i < 127; i++) {
            switch ((char) i) {
                case 'c':
                case 'C':
                    letter2index[i] = 1;
                case 'g':
                case 'G':
                    letter2index[i] = 2;
                case 't':
                case 'T':
                case 'u':
                case 'U':
                    letter2index[i] = 3;
            }
        }
        factorial = new double[L + 1];
        double value = 1.0;
        for (int i = 0; i <= L; i++) {
            if (i > 0)
                value *= i;
            factorial[i] = value;
        }

    }

    /**
     * uses Wootten and Federhen to compute the complexity of a sequence
     *
     * @param s
     * @return average complexity
     */
    public float getComplexity(byte[] s) {
        if (s == null || s.length < L)
            return 0;

        int[] counts = new int[N];

        for (int pos = 0; pos < L; pos++) // first 12 values
        {
            counts[letter2index[s[pos]]]++;
        }

        int count = 0;
        float sum = 0;
        // System.err.print("Values: ");
        for (int pos = L; pos < s.length - L; pos++) {
            double product = computeProductOfFactorials(counts);
            double K = 1.0 / L * Math.log(LFactorial / product) / Math.log(N);
            counts[letter2index[s[pos - L]]]--;
            counts[letter2index[s[pos]]]++;
            //System.err.println(" "+K);
            sum += K;
            count++;
        }
        // System.err.println("avgComplexity="+ sum/count+", sequence: "+ Basic.toString(s));
        return Math.max(0.0001f, sum / count);   // MEGAN interprets 0 as being turned off...
    }

    /**
     * filter by complexity
     *
     * @param s
     * @return true, if passes filter, false, otherwise
     */
    @Override
    public boolean filter(byte[] s) {
        return minComplexity == 0 || getComplexity(s) >= minComplexity;
    }

    /**
     * computes the produce of factorials (of values up to L)
     *
     * @param counts
     * @return produce of factorials
     */
    private double computeProductOfFactorials(int[] counts) {
        double result = 1.0;
        for (int count : counts) {
            result *= factorial[count];
        }
        return result;
    }
}
