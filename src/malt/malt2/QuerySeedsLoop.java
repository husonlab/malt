package malt.malt2;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.data.SeedShape;

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
                           final QueryStore queryStore, final SeedShape seedShape, final SeedStore[] seedStores) {
        final CountDownLatch countDownLatch1 = new CountDownLatch(numberOfThreads);
        final ProgressPercentage progress1 = new ProgressPercentage("Computing *query* seeds...");

        final long numberOfSequences = queryStore.getNumberOfSequences();  // note that threadNumber times numberOfSequences can exceed int size, so need long here

        for (int t = 0; t < numberOfThreads; t++) {
            final int threadNumber = t;
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        int low = (int) ((threadNumber * numberOfSequences) / (long) numberOfThreads);
                        int high = (int) ((((threadNumber + 1) * numberOfSequences)) / (long) numberOfThreads);
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

        final CountDownLatch countDownLatch2 = new CountDownLatch(numberOfJobs);

        final ProgressPercentage progress2 = new ProgressPercentage("Sorting...");
        final boolean showProgressInFirstJob = (numberOfJobs <= numberOfThreads);
        // if number of jobs is at most number of threads, monitor the progress of the first job, otherwise monitor progress of jobs
        if (!showProgressInFirstJob)
            progress2.setMaximum(numberOfJobs);

        for (int t = 0; t < numberOfJobs; t++) {
            final int job = t;
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        seedStores[job].sort(showProgressInFirstJob && job == 0 ? progress2 : null);
                    } finally {
                        countDownLatch2.countDown();
                        if (!showProgressInFirstJob) {
                            synchronized (progress2) {
                                progress2.incrementProgress();
                            }
                        }
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
