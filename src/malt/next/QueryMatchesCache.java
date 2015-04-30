package malt.next;

import malt.util.MurmurHash3;

/**
 * hash table used for caching matches associated with a given queries
 * Created by huson on 7/9/14.
 */
public class QueryMatchesCache {
    private final int hashMask;
    private final Item[] hash2data;
    private int randomNumberSeed = 666;

    private final int numberOfSyncObjects = (1 << 10);
    private final int syncObjectsMask = numberOfSyncObjects - 1;
    // use lots of objects to synchronize on so that threads don't in each others way
    private final Object[] syncTable = new Object[numberOfSyncObjects];

    private long size = 0;

    /**
     * constructor
     *
     * @param bits
     * @throws Exception
     */
    public QueryMatchesCache(int bits) {
        if (bits > 31)
            throw new RuntimeException("bits exceed 31");
        hash2data = new Item[1 << bits];
        hashMask = (1 << bits) - 1;

        for (int i = 0; i < numberOfSyncObjects; i++) {
            syncTable[i] = new Object();
        }
    }

    /**
     * add a sequence and its matches to the cache
     *
     * @param sequence
     * @param matches
     */
    public void add(byte[] sequence, AMatch matches) {
        int hash = getHash(sequence);
        synchronized (syncTable[hash & syncObjectsMask]) {
            if (hash2data[hash] == null) // check again here, because could have been set while waiting...
            {
                // System.err.println("Put: "+ Basic.toString(sequence, sequenceLength)+" hash: "+hash);
                Item item = hash2data[hash];
                if (item == null) {
                    hash2data[hash] = new Item(sequence, matches);
                    size++;
                } else if (item.addIfNew(sequence, matches))
                    size++;
            }
        }
    }

    /**
     * get the read matches associated with this sequence, if cached
     *
     * @param sequence
     * @return associated read matches
     */
    public AMatch get(byte[] sequence) {
        int hash = getHash(sequence);
        // System.err.println("Get: "+ Basic.toString(sequence, sequenceLength)+" hash: "+hash);

        synchronized (syncTable[hash & syncObjectsMask]) {
            Item item = hash2data[hash];
            if (item != null) {
                return item.getMatches(sequence); // get matches if correct sequence found
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
    private int getHash(byte[] key) {
        int value = MurmurHash3.murmurhash3x8632(key, 0, key.length, randomNumberSeed) & hashMask; // & also removes negative sign
        if (value >= hash2data.length)
            value %= hash2data.length;
        return value;
    }

    /**
     * determine whether cache contains this sequence
     *
     * @param sequence
     * @return true, if sequence contained in cache
     */
    public boolean contains(byte[] sequence) {
        int hash = getHash(sequence);
        // System.err.println("Get: "+ Basic.toString(sequence, sequenceLength)+" hash: "+hash);

        synchronized (syncTable[hash & syncObjectsMask]) {
            Item item = hash2data[hash];
            return item != null && item.contains(sequence);
        }
    }

    /**
     * add all computed matches to the store
     *
     * @param queryStore
     * @param matches
     */
    public void addMatches(final QueryStore queryStore, final MatchStore matches) {
        for (int i = 0; i < matches.getNumberOfQueries(); i++) {
            add(queryStore.getOriginalSequence(i), matches.get(i));
        }
    }

    public long getSize() {
        return size;
    }

    /**
     * hash table item
     */
    class Item {
        private Item next;
        private byte[] sequence;
        private AMatch matches;

        /**
         * constructor
         *
         * @param sequence
         * @param matches
         */
        public Item(byte[] sequence, AMatch matches) {
            this.sequence = sequence;
            this.matches = matches;
        }

        /**
         * add item if new
         *
         * @param sequence
         * @param matches
         * @return true, if added, false if not
         */
        public boolean addIfNew(byte[] sequence, AMatch matches) {
            Item current = this;

            while (current != null && !equal(sequence, current.sequence)) {
                if (current.next == null) {
                    current.next = new Item(sequence, matches);
                    return true;
                } else
                    current = current.next;
            }
            if (current != null) // add the matches
                current.matches = matches;
            return false;
        }

        /**
         * does this item contain this sequence
         *
         * @param sequence
         * @return true, if this item or any chained to it equals the given one
         */
        public boolean contains(byte[] sequence) {
            Item current = this;

            while (current != null) {
                if (equal(sequence, current.sequence))
                    return true;
                current = current.next;
            }
            return false;
        }

        /**
         * get list of matches for the given sequence2
         *
         * @param sequence
         * @return matches or null
         */
        public AMatch getMatches(byte[] sequence) {
            Item current = this;

            while (current != null) {
                if (equal(sequence, current.sequence))
                    return current.matches;
                current = current.next;
            }
            return null;
        }


        /**
         * check whether two strings are equal
         *
         * @param a
         * @param b
         * @return true, if equal
         */
        private boolean equal(byte[] a, byte[] b) {
            if (a.length != b.length)
                return false;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i])
                    return false;
            }
            return true;
        }
    }
}
