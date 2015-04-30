package malt.malt2;

/**
 * filters protein sequences based on their  Wootten and Federhen complexity
 * Daniel Huson, 10.2012
 */
public class ComplexityMeasureProtein implements IComplexityMeasure {
    private static final int N = 20; // alphabet size
    private static final int L = 16;  // window size
    private static final double LFactorial = 20922789888000.0;
    private final byte[] letter2index;
    private final double[] factorial;
    private final float minComplexity;

    /**
     * constructor
     */
    public ComplexityMeasureProtein(final float minComplexity) {
        this.minComplexity = minComplexity;
        letter2index = new byte[127];
        for (int i = 0; i < 127; i++) {
            switch ((char) i) {
                default:
                case 'a':
                case 'A':
                    letter2index[i] = 0;
                    break;
                case 'r':
                case 'R':
                    letter2index[i] = 1;
                    break;
                case 'n':
                case 'N':
                    letter2index[i] = 2;
                    break;
                case 'd':
                case 'D':
                    letter2index[i] = 3;
                    break;
                case 'c':
                case 'C':
                    letter2index[i] = 4;
                    break;
                case 'e':
                case 'E':
                    letter2index[i] = 5;
                    break;
                case 'q':
                case 'Q':
                    letter2index[i] = 6;
                    break;
                case 'g':
                case 'G':
                    letter2index[i] = 7;
                    break;
                case 'h':
                case 'H':
                    letter2index[i] = 8;
                    break;
                case 'i':
                case 'I':
                    letter2index[i] = 9;
                    break;
                case 'l':
                case 'L':
                    letter2index[i] = 10;
                    break;
                case 'k':
                case 'K':
                    letter2index[i] = 11;
                    break;
                case 'm':
                case 'M':
                    letter2index[i] = 12;
                    break;
                case 'f':
                case 'F':
                    letter2index[i] = 13;
                    break;
                case 'p':
                case 'P':
                    letter2index[i] = 14;
                    break;
                case 's':
                case 'S':
                    letter2index[i] = 15;
                    break;
                case 't':
                case 'T':
                    letter2index[i] = 16;
                    break;
                case 'w':
                case 'W':
                    letter2index[i] = 17;
                    break;
                case 'y':
                case 'Y':
                    letter2index[i] = 18;
                    break;
                case 'v':
                case 'V':
                    letter2index[i] = 19;
                    break;
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
    @Override
    public float getComplexity(byte[] s) {
        if (s == null || s.length < L)
            return 0;

        int[] counts = new int[N];

        for (int pos = 0; pos < L; pos++) // first 12 values
        {
            counts[letter2index[s[pos]]]++;
        }
        double minComplexity = 1;

        // System.err.print("Values: ");
        for (int pos = L; pos < s.length - L; pos++) {
            double product = computeProductOfFactorials(counts);
            double K = 1.0 / L * Math.log(LFactorial / product) / Math.log(N);
            counts[letter2index[s[pos - L]]]--;
            counts[letter2index[s[pos]]]++;
            // System.err.print(" "+K);
            if (K < minComplexity)
                minComplexity = K;
        }
        // System.err.println("minComplexity="+minComplexity+", sequence: "+s);
        return (float) Math.max(0.0001, minComplexity);   // MEGAN interprets 0 as being turned off...
    }

    /**
     * filter by complexity
     *
     * @param s
     * @return true, if passes filter, false, otherwise
     */
    @Override
    public boolean filter(byte[] s) {
        if (minComplexity == 0)
            return true;

        if (s == null || s.length < L)
            return false;

        int[] counts = new int[N];

        for (int pos = 0; pos < L; pos++) // first 12 values
        {
            counts[letter2index[s[pos]]]++;
        }

        // System.err.print("Values: ");
        for (int pos = L; pos < s.length - L; pos += L) {
            double product = computeProductOfFactorials(counts);
            double K = 1.0 / L * Math.log(LFactorial / product) / Math.log(N);
            counts[letter2index[s[pos - L]]]--;
            counts[letter2index[s[pos]]]++;
            // System.err.print(" "+K);
            if (K < minComplexity)
                return false;
        }
        return true;
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
