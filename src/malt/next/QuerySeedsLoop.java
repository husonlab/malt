package malt.next;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.sequence.SeedShape2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * Main loop for computing query seeds
 * <p/>
 * Daniel Huson, 8.2014
 */
public class QuerySeedsLoop {
    /**
     * run the main loop for computing query seeds
     *
     * @param numberOfChunks
     * @param numberOfJobs
     * @param queryStore
     * @param seedShape
     * @param seedStores
     */
    public static void run(final ExecutorService threadPool, final int chunk, final int numberOfChunks, final int numberOfJobs, final int numberOfThreads,
                           final QueryStore queryStore, final SeedShape2 seedShape, final SeedStore[] seedStores) {
        final CountDownLatch countDownLatch1 = new CountDownLatch(numberOfThreads);
        final ProgressPercentage progress1 = new ProgressPercentage("Computing *query* seeds...");
        progress1.setProgress(0);

        final long numberOfSequences = queryStore.getNumberOfSequences();

        for (int t = 0; t < numberOfThreads; t++) {
            final int threadNumber = t;
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        int low = (int) ((threadNumber * numberOfSequences) / (long) numberOfThreads);
                        int high = (int) (((threadNumber + 1) * numberOfSequences) / (long) numberOfThreads);
                        queryStore.computeSeeds(chunk, numberOfChunks, numberOfJobs, low, high, seedShape, seedStores, threadNumber == 0 ? progress1 : null);
                    } finally {
                        countDownLatch1.countDown();
                    }
                }
            });
        }
        try {
            countDownLatch1.await();
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
        progress1.close();

        long seedCount = 0;
        for (SeedStore seedStore : seedStores) {
            seedCount += seedStore.size();
        }
        System.err.println(String.format("Number of seeds: %,15d", seedCount));

        final ProgressPercentage progress2 = new ProgressPercentage("Sorting...");
        progress1.setProgress(0);

        final CountDownLatch countDownLatch2 = new CountDownLatch(numberOfJobs);
        for (int t = 0; t < numberOfJobs; t++) {
            final int job = t;
            final boolean showProgress = (job == 0 && numberOfJobs <= numberOfThreads);
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        seedStores[job].sort(showProgress ? progress2 : null);
                    } finally {
                        countDownLatch2.countDown();
                    }
                }
            });
        }
        try {
            countDownLatch2.await();
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
        progress2.close();
    }
}
