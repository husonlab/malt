/**
 * Copyright 2015, Daniel Huson
 * <p/>
 * (Some files contain contributions from other authors, who are then mentioned separately)
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package malt.util;

/**
 * tester
 * Daniel Huson, 2014
 */
public class Tester {

    /**
     * shows that modulo takes three times as long as bitwise-and or shift
     *
     * @param args
     */
    public static void main(String[] args) {
        System.err.println("MAX:  " + Integer.MAX_VALUE + " bin: " + Integer.toBinaryString(Integer.MAX_VALUE));
        System.err.println("MIN+1:  " + (Integer.MIN_VALUE + 1));
        System.err.println("-MIN+1: " + (-(Integer.MIN_VALUE + 1)) + " equals MAX? " + (Integer.MAX_VALUE == (-(Integer.MIN_VALUE + 1))));

        int hash = Integer.MAX_VALUE;
        System.err.print("hash: " + hash);
        if ((long) hash == Integer.MAX_VALUE || (long) hash <= Integer.MIN_VALUE + 1)
            hash = 0;
        System.err.println(" -> " + hash);

        hash = Integer.MIN_VALUE;
        System.err.print("hash: " + hash);
        if ((long) hash == Integer.MAX_VALUE || (long) hash <= Integer.MIN_VALUE + 1)
            hash = 0;
        System.err.println(" -> " + hash);

        hash = Integer.MIN_VALUE + 1;
        System.err.print("hash: " + hash);
        if ((long) hash == Integer.MAX_VALUE || (long) hash <= Integer.MIN_VALUE + 1)
            hash = 0;
        System.err.println(" -> " + hash);

        /*
        for(int i=0;i<32;i++) {
            System.err.println("i="+i+" (1<<i)="+(1<<i)+" ((1<<i) -1)="+((1<<i)-1));
        }
        */

        long numberOfSeeds = (long) (10000);
        System.err.println("numberOfSeeds: " + numberOfSeeds);

        int tableSize;
        if ((long) (0.9 * numberOfSeeds) >= Integer.MAX_VALUE) {
            tableSize = Integer.MAX_VALUE;
        } else {
            tableSize = 1;
            while (numberOfSeeds > tableSize) {
                tableSize *= 2;
            }
        }
        final int mask = tableSize - 1;

        System.err.println("tableSize: " + tableSize);
        System.err.println("mask: " + mask + " bits: " + Integer.toBinaryString(mask));

        for (int i = tableSize - 5; i < tableSize + 5; i++) {
            System.err.println("i=" + i + " i&mask=" + (i & mask));
        }

        for (int i = tableSize - 5; i < tableSize + 5; i++) {
            System.err.println("i=" + (-i) + " (-i)&mask=" + ((i) & mask));
        }


        if (true) {
            int top = Integer.MAX_VALUE >>> 8;
            int aMask = top;
            System.err.println("top: " + top);
            System.err.println("aMask: " + Integer.toBinaryString(aMask));

            {
                long start = System.currentTimeMillis();

                long sum = 0;
                for (int i = 0; i < Integer.MAX_VALUE; i++) {
                    sum += (i % top);
                }
                System.err.println("Time: " + ((System.currentTimeMillis() - start) / 1000.0));
                System.err.println("Sum: " + sum);
            }

            {
                long start = System.currentTimeMillis();

                long sum = 0;
                for (int i = 0; i < Integer.MAX_VALUE; i++) {
                    sum += (i & aMask);
                }
                System.err.println("Time: " + ((System.currentTimeMillis() - start) / 1000.0));
                System.err.println("Sum: " + sum);
            }
        }

        System.exit(0);
    }
}
