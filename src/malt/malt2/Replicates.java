package malt.malt2;

import malt.util.MurmurHash3;

import java.util.Arrays;

/**
 * Keeps track of replicates
 * <p/>
 * Daniel Huson, 8.2014
 */
public class Replicates {
    private final int hashMask;
    private final Item[] array;
    private int randomNumberSeed = 666;

    private final int numberOfSyncObjects = (1 << 10);
    private final int syncObjectsMask = numberOfSyncObjects - 1;
    // use lots of objects to synchronize on so that threads don't in each others way
    private final Object[] syncTable = new Object[numberOfSyncObjects];

    private int countGet = 0;
    private int countPut = 0;


    private final QueryStore queryStore;

    /**
     * constructor
     *
     * @param queryStore
     */
    public Replicates(final QueryStore queryStore) {
        for (int i = 0; i < numberOfSyncObjects; i++) {
            syncTable[i] = new Object();
        }
        this.queryStore = queryStore;
        int bits = 15;
        array = new Item[1 << bits];
        hashMask = (1 << bits) - 1;
    }

    /**
     * erase
     */
    public void clear() {
        Arrays.fill(array, null);
        countGet = 0;
        countPut = 0;
    }

    /**
     * adds a sequence
     *
     * @param index
     * @param sequence
     */
    public void add(final int index, final byte[] sequence) {
        final int hash = getHash(sequence);
        synchronized (syncTable[hash & syncObjectsMask]) {
            Item item = array[hash];
            if (item == null) {
                array[hash] = new Item(index, null);
                countPut++;
            } else {
                while (item != null) {
                    if (equal(sequence, queryStore.getOriginalSequence(item.index)))
                        return;
                    else
                        item = item.next;
                }
                array[hash] = new Item(index, array[hash]);
                countPut++;
            }
        }
    }

    /**
     * are two strings equal?
     *
     * @param a
     * @param b
     * @return true, if equal
     */
    private static boolean equal(final byte[] a, final byte[] b) {
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++)
            if (a[i] != b[i])
                return false;
        return true;
    }

    /**
     * is the given sequence a representative?
     *
     * @param index
     * @return true, if representative
     */
    public boolean isRepresentative(int index) {
        final byte[] sequence = queryStore.getOriginalSequence(index);
        final int hash = getHash(sequence);
        Item item = array[hash];

        while (item != null) {
            if (item.index == index)
                return true;
            else if (equal(sequence, queryStore.getOriginalSequence(item.index)))
                return false;
            else
                item = item.next;
        }
        return true;
    }

    /**
     * returns the representative for this sequence
     *
     * @return index of representative
     */
    public int getRepresentative(int index) {
        final byte[] sequence = queryStore.getOriginalSequence(index);
        final int hash = getHash(sequence);
        Item item = array[hash];

        while (item != null) {
            if (index == item.index)
                return index;
            if (equal(sequence, queryStore.getOriginalSequence(item.index))) {
                synchronized (syncTable[hash & syncObjectsMask]) {
                    countGet++;
                }
                return item.index;
            } else
                item = item.next;
        }
        return index;
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
        if (value >= array.length)
            value %= array.length; // should never happen
        return value;
    }

    public int getCountGet() {
        return countGet;
    }

    public int getCountPut() {
        return countPut;
    }

    /**
     * a single item
     */
    private class Item {
        int index;
        Item next;

        Item(int index, Item next) {
            this.index = index;
            this.next = next;
        }
    }
}
