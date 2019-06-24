/*
 *  ReferencesHashTableBuilder.java Copyright (C) 2019. Daniel H. Huson GPL
 *
 *   (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package malt.data;

import jloda.thirdparty.MurmurHash3;
import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import jloda.util.Single;
import malt.util.Utilities;
import megan.io.IntFilePutter;
import megan.io.OutputWriter;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * builds the reference hash table
 * Daniel Huson, 8.2014
 */

public class ReferencesHashTableBuilder {
    public static byte[] MAGIC_NUMBER = "MATableV0.12.".getBytes();

    private final SequenceType referenceSequenceType;
    private final IAlphabet alphabet;

    private long[] tableIndex;   // main table index

    private IntFilePutter tableDataPutter; // main table data

    private final int tableSize;
    private final int hashMask; // use bit mask rather than modulo (five times as fast)

    private final int randomNumberSeed;

    private long theSize = 0; // counts items

    private final int maxHitsPerHash; // this was 10000000

    private final SeedShape seedShape; //  seed shape that is saved and loaded from index

    private final int SYNC_BITMASK = 1023;
    // use lots of objects to synchronize on so that threads don't in each others way
    private final Object[] syncObjects = new Object[SYNC_BITMASK + 1];

    private final int stepSize;

    /**
     * constructor
     *
     * @param seedShape
     * @param numberOfSequences
     * @param numberOfLetters
     * @param randomNumberSeed
     */
    public ReferencesHashTableBuilder(SequenceType referenceSequenceType, IAlphabet alphabet, SeedShape seedShape,
                                      int numberOfSequences, long numberOfLetters, int randomNumberSeed, int maxHitPerSeed, float hashTableLoadFactor, int stepSize) throws IOException {
        this.referenceSequenceType = referenceSequenceType;
        this.alphabet = alphabet;
        this.seedShape = seedShape;
        this.randomNumberSeed = randomNumberSeed;
        this.stepSize = stepSize;

        // total is numberOfLetters minus last letter of each sequence divided by stepSize
        final long totalNumberOfSeeds = (long) (Math.ceil((numberOfLetters - (numberOfSequences * (seedShape.getLength() - 1))) / stepSize));
        // number of possible different seed values:
        final long numberOfPossibleHashValues = (long) Math.ceil(Math.pow(alphabet.size(), seedShape.getWeight()));

        System.err.println(String.format("Seeds found: %,14d", totalNumberOfSeeds));
        // System.err.println("Number of possible hash values: " + numberOfPossibleHashValues);

        long entriesPerTable = (long) (hashTableLoadFactor * Math.min(totalNumberOfSeeds, numberOfPossibleHashValues)); // assume only 90% are used

        if (entriesPerTable >= Integer.MAX_VALUE / 2) {
            tableSize = Basic.MAX_ARRAY_SIZE;
            hashMask = Integer.MAX_VALUE;
        } else {
            long size = 1;
            while (entriesPerTable > size) {
                size *= 2;
            }
            tableSize = (int) size;
            hashMask = tableSize - 1;
        }

        System.err.println(String.format("tableSize=   %,14d", tableSize));
        System.err.println(String.format("hashMask.length=%d", Integer.toBinaryString(hashMask).length()));

        maxHitsPerHash = maxHitPerSeed; // we use the same value because the actual number of seeds used is usually smaller than the table size
        // final double averageWordsPerHashValue = Math.max(1,  (totalNumberOfSeeds / (double) tableSize));
        // maxHitsPerHash = (int)Math.max(1, maxHitPerSeed * averageWordsPerHashValue);
        System.err.println("maxHitsPerHash set to: " + maxHitsPerHash);

        final ProgressPercentage progress = new ProgressPercentage("Initializing arrays...");

        for (int i = 0; i < syncObjects.length; i++) {
            syncObjects[i] = new Object();
        }

        progress.reportTaskCompleted();
    }

    /**
     * build the hash table
     *  @param referencesDB
     * @param numberOfThreads
     */
    public void buildTable(final File tableIndexFile, final File tableDataFile, final ReferencesDBBuilder referencesDB, int numberOfThreads, boolean buildTableInMemory) throws IOException {
        tableIndex = new long[tableSize];

        countSeeds(referencesDB, numberOfThreads);
        long limit = allocateTable(numberOfThreads);
        tableDataPutter = new IntFilePutter(tableDataFile, limit + 1, buildTableInMemory); // limit+1 because we start with index 1
        fillTable(referencesDB, numberOfThreads);
        randomizeBuildRows(numberOfThreads);
        saveTableIndex(tableIndex, tableIndexFile);
        tableIndex = null;
        tableDataPutter.close();
    }

    /**
     * save the table index
     * @param tableIndex
     * @param tableIndexFile
     * @throws IOException
     */
    private void saveTableIndex(long[] tableIndex, File tableIndexFile) throws IOException {
        final ProgressPercentage progress = new ProgressPercentage("Writing file: " + tableIndexFile, tableIndex.length);
        try (OutputWriter outs = new OutputWriter(tableIndexFile)) {
            for (long value : tableIndex) {
                outs.writeLong(value);
                progress.incrementProgress();
            }
        }
        progress.close();
    }

    /**
     * count the seeds. He we use forwardTable and reverseTable to hold the counts, later the counts are replaced by locations
     *
     * @param referencesDB
     * @param numberOfThreads0
     */
    private void countSeeds(final ReferencesDBBuilder referencesDB, int numberOfThreads0) {
        final int numberOfThreads = Math.min(referencesDB.getNumberOfSequences(), numberOfThreads0);
        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        final ProgressPercentage progressPercentage = new ProgressPercentage("Analysing seeds...", referencesDB.getNumberOfSequences());
        final int[] countsForProgress = new int[numberOfThreads];
        final long[] countLowComplexitySeeds = new long[numberOfThreads];

        // launch the worker threads
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNumber = i;

            executor.execute(new Runnable() {
                public void run() {
                    try {
                        final byte[] seedBytes = seedShape.createBuffer();
                        for (int refIndex = threadNumber; refIndex < referencesDB.getNumberOfSequences(); refIndex += numberOfThreads) {
                            byte[] sequence = referencesDB.getSequence(refIndex);
                            int top = sequence.length - seedShape.getLength() + 1;
                            for (int pos = 0; pos < top; pos += stepSize) {
                                seedShape.getSeed(sequence, pos, seedBytes);
                                if (!Utilities.hasAtMostTwoLetters(seedBytes)) {
                                    int hashValue = getHash(seedBytes);
                                    synchronized (syncObjects[hashValue & SYNC_BITMASK]) {
                                        if (tableIndex[hashValue] <= maxHitsPerHash)
                                            tableIndex[hashValue]++;
                                    }
                                } else
                                    countLowComplexitySeeds[threadNumber]++;
                            }
                            countsForProgress[threadNumber]++;
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        // wait for jobs to complete:
        while (countDownLatch.getCount() > 0) {
            try {
                Thread.sleep(100); // sleep and then report progress
            } catch (InterruptedException e) {
                Basic.caught(e);
                break;
            }
            progressPercentage.setProgress(Basic.getSum(countsForProgress));
        }
        progressPercentage.close();
        System.err.println(String.format("Number of low-complexity seeds skipped: %,d", Basic.getSum(countLowComplexitySeeds)));
        executor.shutdown();
    }

    /**
     * allocate the hash table
     *
     * @param numberOfThreads0
     */
    private long allocateTable(final int numberOfThreads0) throws IOException {
        final int numberOfThreads = Math.min(tableSize, numberOfThreads0);
        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        ProgressPercentage progressPercentage = new ProgressPercentage("Allocating hash table...", tableSize);
        final int[] countsForProgress = new int[numberOfThreads];

        final long[] totalKeys = new long[numberOfThreads];
        final long[] totalSeeds = new long[numberOfThreads];
        final long[] totalDropped = new long[numberOfThreads];

        final Single<Long> nextFreeIndex = new Single<>(1L);

        // launch the worker threads
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNumber = i;

            executor.execute(new Runnable() {
                public void run() {
                    try {
                        for (long index = threadNumber; index < tableSize; index += numberOfThreads) {
                            final long count = tableIndex[(int) index];  // here count is number of seeds that will be saved for given index

                            if (count > maxHitsPerHash) {
                                tableIndex[(int) index] = 0L;  // need to overwrite the count
                                totalDropped[threadNumber] += count;
                            } else if (count > 1) {
                                totalSeeds[threadNumber] += count;
                                totalKeys[threadNumber]++;
                                synchronized (nextFreeIndex) {
                                    final long location = nextFreeIndex.get();
                                    tableIndex[(int) index] = location;
                                    nextFreeIndex.set(location + 2 * count + 1);
                                }
                            } else if (count == 1) {   // will write refInd and offset directly into table, use value of -1 to indicate this
                                totalSeeds[threadNumber]++;
                                totalKeys[threadNumber]++;
                                tableIndex[(int) index] = -1L;
                            } else if (count < 0)
                                throw new IOException("negative count: " + count);
                            countsForProgress[threadNumber]++;
                        }
                    } catch (Exception ex) {
                        Basic.caught(ex);
                        System.exit(1);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        // wait for jobs to complete:
        while (countDownLatch.getCount() > 0) {
            try {
                Thread.sleep(100); // sleep and then report progress
            } catch (InterruptedException e) {
                Basic.caught(e);
                break;
            }
            progressPercentage.setProgress(Basic.getSum(countsForProgress));
        }
        progressPercentage.reportTaskCompleted();
        System.err.println(String.format("Total keys used:    %,14d", Basic.getSum(totalKeys)));
        System.err.println(String.format("Total seeds matched:%,14d", Basic.getSum(totalSeeds)));
        System.err.println(String.format("Total seeds dropped:%,14d", Basic.getSum(totalDropped)));
        // shut down threads:
        executor.shutdownNow();
        return nextFreeIndex.get();
    }


    /**
     * Fill the hash table
     *
     * @param referencesDB
     * @param numberOfThreads0
     */
    private void fillTable(final ReferencesDBBuilder referencesDB, int numberOfThreads0) {
        final int numberOfThreads = Math.min(referencesDB.getNumberOfSequences(), numberOfThreads0);
        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // populate the table
        final ProgressPercentage progressPercentage = new ProgressPercentage("Filling hash table...", referencesDB.getNumberOfSequences());
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        final int[] countsForProgress = new int[numberOfThreads];
        final long[] counts = new long[numberOfThreads];

        // launch the worker threads
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNumber = i;

            executor.execute(new Runnable() {
                public void run() {
                    try {
                        final byte[] seedBytes = seedShape.createBuffer();
                        for (int refIndex = threadNumber; refIndex < referencesDB.getNumberOfSequences(); refIndex += numberOfThreads) {
                            final byte[] sequence = referencesDB.getSequence(refIndex);
                            final int top = sequence.length - seedShape.getLength() + 1;
                            for (int pos = 0; pos < top; pos += stepSize) {
                                seedShape.getSeed(sequence, pos, seedBytes);
                                if (!Utilities.hasAtMostTwoLetters(seedBytes)) {
                                    final int hashValue = getHash(seedBytes);

                                    synchronized (syncObjects[hashValue & SYNC_BITMASK]) {
                                        final long location = tableIndex[hashValue];
                                        if (location == -1) {    // has been marked as singleton, so store value directly
                                            final long value = -(((long) refIndex << 32) | pos);
                                            tableIndex[hashValue] = value;
                                        } else if (location > 0) {
                                            final int length = tableDataPutter.get(location);
                                            tableDataPutter.put(location, length + 2);
                                            tableDataPutter.put(location + length + 1, refIndex);
                                            tableDataPutter.put(location + length + 2, pos);
                                        }
                                    }
                                    counts[threadNumber]++;
                                }
                            }
                            countsForProgress[threadNumber]++;
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        // wait for jobs to complete:
        while (countDownLatch.getCount() > 0) {
            try {
                Thread.sleep(100); // sleep and then report progress
            } catch (InterruptedException e) {
                Basic.caught(e);
                break;
            }
            progressPercentage.setProgress(Basic.getSum(countsForProgress));
        }
        progressPercentage.reportTaskCompleted();

        // shut down threads:
        executor.shutdownNow();

        theSize = Basic.getSum(counts);
    }

    /**
     * randomize the rows of the table, parallel version
     *
     * @param numberOfThreads
     */
    private void randomizeBuildRows(final int numberOfThreads) {
        final ProgressPercentage progressPercentage = new ProgressPercentage("Randomizing rows...", tableSize);

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        final int[] countsForProgress = new int[numberOfThreads];

        // launch the worker threads
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNumber = i;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        final Random random = new Random();
                        for (long index = threadNumber; index < tableSize; index += numberOfThreads) { // need to use long otherwise can get overflow
                            if (index < tableIndex.length) {
                                long location = tableIndex[(int) index];
                                if (location > 0) {
                                    int size = tableDataPutter.get(location);
                                    if (size > 2) {
                                        random.setSeed(index * index);  // use location in hash table as seed.
                                        Utilities.randomizePairs(tableDataPutter, location + 1, size, random);
                                    }
                                }
                            }
                            countsForProgress[threadNumber]++;
                        }
                    } catch (Exception ex) {
                        Basic.caught(ex);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        // wait for all tasks to be completed:
        while (countDownLatch.getCount() > 0) {
            try {
                Thread.sleep(100); // sleep and then report progress
            } catch (InterruptedException e) {
                Basic.caught(e);
                break;
            }
            progressPercentage.setProgress(Basic.getSum(countsForProgress));
        }
        progressPercentage.reportTaskCompleted();

        // shut down threads:
        executor.shutdownNow();
    }


    /**
     * for a given key, add the reference id and sequence offset to table
     * uses very naive synchronization
     *
     * @param key
     * @return hash value
     */
    public int getHash(byte[] key) {
        int value = MurmurHash3.murmurhash3x8632(key, 0, key.length, randomNumberSeed) & hashMask; // & also removes negative sign

        if (value >= Basic.MAX_ARRAY_SIZE)
            value %= Basic.MAX_ARRAY_SIZE;
        return value;
    }

    /**
     * get the number of entries
     *
     * @return number of entries
     */
    public long size() {
        return theSize;
    }

    /**
     * save master index file
     *
     * @param file
     * @throws IOException
     */
    public void saveIndexFile(File file) throws IOException {
        final ProgressPercentage progressPercentage = new ProgressPercentage("Writing file: " + file);

        try (OutputWriter outs = new OutputWriter(file)) {
            outs.write(MAGIC_NUMBER);
            outs.writeInt(SequenceType.rankOf(referenceSequenceType));
            if (referenceSequenceType == SequenceType.Protein) {
                final byte[] bytes = alphabet.toString().getBytes();
                outs.writeInt(bytes.length);
                outs.write(bytes);
            }
            outs.writeInt(tableSize);
            outs.writeInt(hashMask);
            outs.writeInt(randomNumberSeed);
            outs.writeLong(theSize);
            outs.writeInt(stepSize);

            final byte[] shapeBytes = seedShape.getBytes();
            outs.writeInt(shapeBytes.length);
            outs.write(shapeBytes);
        } finally {
            progressPercentage.reportTaskCompleted();

        }
    }

    /**
     * make sure that we can write the files
     *
     * @param indexDirectory
     * @throws IOException
     */
    public static void checkCanWriteFiles(String indexDirectory, int tableNumber) throws IOException {
        final File indexFile = new File(indexDirectory, "index" + tableNumber + ".idx");
        if ((!indexFile.exists() || indexFile.delete()) && !indexFile.createNewFile())
            throw new IOException("Can't create file: " + indexFile);
        final File tableIndexFile = new File(indexDirectory, "table" + tableNumber + ".idx");
        if ((!tableIndexFile.exists() || tableIndexFile.delete()) && !tableIndexFile.createNewFile())
            throw new IOException("Can't create file: " + tableIndexFile);
        final File tableDBFile = new File(indexDirectory, "table" + tableNumber + ".db");
        if ((!tableDBFile.exists() || tableDBFile.delete()) && !tableDBFile.createNewFile())
            throw new IOException("Can't create file: " + tableDBFile);
    }

}
