package malt.tools;

/**
 * Created by huson on 8/26/14.
 */
public class ComputeNumber {
    public static void main(String[] args) {
        long n = 3000000000l;
        long k = 3000000l;
        System.out.println("pow: " + Math.pow(n / k - 0.5, k));
        System.out.println("exp: " + Math.exp(k));
        System.out.println("(n*k/e)^k=" + Math.pow(n * k / Math.exp(1), k));
        double result = Math.exp(k) * (Math.pow(n / k - 0.5, k) / Math.sqrt(2 * Math.PI * k));

        System.out.println(String.format("n=%,15d k=%,12d: n choose k approximately: %.1f", n, k, result));
    }
}
