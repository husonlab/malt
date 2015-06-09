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

package malt.data;

import malt.util.MurmurHash3;

/**
 * hash table used for caching matches associated with a given read
 * Created by huson on 7/9/14.
 */
public class QuerySequence2MatchesCache {
    private final int hashMask;
    private final Item[] hash2data;
    private int randomNumberSeed = 666;

    private final int numberOfSyncObjects = (1 << 10);
    private final int syncObjectsMask = numberOfSyncObjects - 1;
    // use lots of objects to synchronize on so that threads don't in each others way
    private final Object[] syncTable = new Object[numberOfSyncObjects];

    private long countGet = 0;
    private long countPut = 0;

    /**
     * constructor
     *
     * @param bits
     * @throws Exception
     */
    public QuerySequence2MatchesCache(int bits) {
        if (bits > 31)
            throw new RuntimeException("bits exceed 31");
        hash2data = new Item[1 << bits];
        hashMask = (1 << bits) - 1;

        for (int i = 0; i < numberOfSyncObjects; i++) {
            syncTable[i] = new Object();
        }
    }

    /**
     * put a copy into the cache
     *
     * @param sequence
     * @param sequenceLength
     * @param matches
     * @param numberOfMatches
     */
    public void put(byte[] sequence, int sequenceLength, ReadMatch[] matches, int numberOfMatches) {
        int hash = getHash(sequence, sequenceLength);
        synchronized (syncTable[hash & syncObjectsMask]) {
            if (hash2data[hash] == null) // check again here, because could have been set while waiting...
            {
                // System.err.println("Put: "+ Basic.toString(sequence, sequenceLength)+" hash: "+hash);
                Item item = hash2data[hash];
                if (item == null) {
                    hash2data[hash] = new Item(sequence, sequenceLength, matches, numberOfMatches);
                    countPut++;
                } else if (item.addIfNew(sequence, sequenceLength, matches, numberOfMatches))
                    countPut++;
            }
        }
    }

    /**
     * get the read matches associated with this sequence, if cached
     *
     * @param sequence
     * @param sequenceLength
     * @return associated read matches
     */
    public ReadMatch[] get(byte[] sequence, int sequenceLength) {
        int hash = getHash(sequence, sequenceLength);
        // System.err.println("Get: "+ Basic.toString(sequence, sequenceLength)+" hash: "+hash);

        synchronized (syncTable[hash & syncObjectsMask]) {
            Item item = hash2data[hash];
            if (item != null) {
                countGet++;
                return item.getMatches(sequence, sequenceLength); // get matches if correct sequence found
            }
            return null;
        }
    }

    /**
     * for a given key, add the reference id and sequence offset to table
     * uses very naive synchronization
     *
     * @param key
     * @return hash value
     */
    private int getHash(byte[] key, int length) {
        int value = MurmurHash3.murmurhash3x8632(key, 0, length, randomNumberSeed) & hashMask; // & also removes negative sign
        if (value >= hash2data.length)
            value %= hash2data.length;
        return value;
    }

    /**
     * report stats on usage of the table
     */
    public void reportStats() {
        System.err.println("Replicate query cache: in=" + countPut + ", out=" + countGet);
    }

    /**
     * determine whether cache contains this sequence
     *
     * @param sequence
     * @param sequenceLength
     * @return true, if sequence contained in cache
     */
    public boolean contains(byte[] sequence, int sequenceLength) {
        int hash = getHash(sequence, sequenceLength);
        // System.err.println("Get: "+ Basic.toString(sequence, sequenceLength)+" hash: "+hash);

        synchronized (syncTable[hash & syncObjectsMask]) {
            Item item = hash2data[hash];
            return item != null && item.contains(sequence, sequenceLength);
        }
    }

    /**
     * hash table item
     */
    class Item {
        private Item next;
        private byte[] sequence;
        private ReadMatch[] matches;

        /**
         * constructor
         *
         * @param sequence
         * @param sequenceLength
         * @param matches
         * @param numberOfMatches
         */
        public Item(byte[] sequence, int sequenceLength, ReadMatch[] matches, int numberOfMatches) {
            this.sequence = copy(sequence, sequenceLength);
            this.matches = copy(matches, numberOfMatches);
        }

        /**
         * add item if new
         *
         * @param sequence
         * @param sequenceLength
         * @param matches
         * @param numberOfMatches
         * @return true, if added, false if not
         */
        public boolean addIfNew(byte[] sequence, int sequenceLength, ReadMatch[] matches, int numberOfMatches) {
            Item current = this;

            while (current != null && !equal(sequence, sequenceLength, current.sequence, current.sequence.length)) {
                if (current.next == null) {
                    current.next = new Item(sequence, sequenceLength, matches, numberOfMatches);
                    return true;
                } else
                    current = current.next;
            }
            return false;
        }

        /**
         * does this item contain this sequence
         *
         * @param sequence
         * @param sequenceLength
         * @return true, if this item or any chained to it equals the given one
         */
        public boolean contains(byte[] sequence, int sequenceLength) {
            Item current = this;

            while (current != null) {
                if (equal(sequence, sequenceLength, current.sequence, current.sequence.length))
                    return true;
                current = current.next;
            }
            return false;
        }

        /**
         * get list of matches for the given sequence2
         *
         * @param sequence
         * @param sequenceLength
         * @return matches or null
         */
        public ReadMatch[] getMatches(byte[] sequence, int sequenceLength) {
            Item current = this;

            while (current != null) {
                if (equal(sequence, sequenceLength, current.sequence, current.sequence.length))
                    return current.matches;
                current = current.next;
            }
            return null;
        }


        /**
         * check whether two strings are equal
         *
         * @param a
         * @param aLength
         * @param b
         * @param bLength
         * @return true, if equal
         */
        private boolean equal(byte[] a, int aLength, byte[] b, int bLength) {
            if (aLength != bLength)
                return false;
            for (int i = 0; i < aLength; i++) {
                if (a[i] != b[i])
                    return false;
            }
            return true;
        }

        /**
         * copy a byte array
         *
         * @param array
         * @param length
         * @return copy
         */
        private byte[] copy(byte[] array, int length) {
            byte[] tmp = new byte[length];
            System.arraycopy(array, 0, tmp, 0, length);
            return tmp;
        }

        /**
         * copy a read match array. Makes a copy of each entry
         *
         * @param array
         * @param length
         * @return read match array copy
         */
        private ReadMatch[] copy(ReadMatch[] array, int length) {
            ReadMatch[] tmp = new ReadMatch[length];
            for (int i = 0; i < length; i++) {
                tmp[i] = array[i].getCopy();
            }
            return tmp;
        }
    }
}
