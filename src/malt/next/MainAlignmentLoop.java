package malt.next;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.align.AlignerOptions;
import malt.sequence.SeedShape2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * The Malt2 main loop
 * <p/>
 * Daniel Huson, 8.2014
 */
public class MainAlignmentLoop {
    private static final boolean verboseAboutJobs = false;

    /**
     * run the main loop
     *
     * @param maltOptions
     * @param alignerOptions
     * @param queryStore
     * @param querySeedStores
     * @param refStore
     * @param refSeedStores
     * @param seedShapes
     * @param matchStore
     */
    public static void run(final ExecutorService threadPool, final MaltOptions2 maltOptions, final AlignerOptions alignerOptions,
                           final int maxRefOccurrencesPerSeed,
                           final QueryStore queryStore, final SeedStore[] querySeedStores,
                           final ReferenceStore refStore, final SeedStore[] refSeedStores, final SeedShape2 seedShape,
                           final SeedShape2[] seedShapes, final MatchStore matchStore) {


        int numberOfJobsToPerform = 0;
        for (int job = 0; job < refSeedStores.length; job++) {
            // System.err.println(String.format("querySeeds=%d refSeeds=%d", querySeedStores[job].size(), refSeedStores[job].size()));
            if (querySeedStores[job].size() > 0 && refSeedStores[job].size() > 0)
                numberOfJobsToPerform++;
        }

        final ProgressPercentage progress = new ProgressPercentage("Aligning...", numberOfJobsToPerform);

        final CountDownLatch countDownLatch = new CountDownLatch(numberOfJobsToPerform);

        final AlignerStats alignerStats = new AlignerStats(refSeedStores.length);


        if (verboseAboutJobs)
            System.err.println("Number of jobs: " + numberOfJobsToPerform);

        final long initialStart = System.currentTimeMillis();

        for (int t = 0; t < refSeedStores.length; t++) {
            final int job = t;
            if (querySeedStores[job].size() > 0 && refSeedStores[job].size() > 0) {

                threadPool.execute(new Runnable() {
                    public void run() {
                        long jobStart = System.currentTimeMillis();

                        try {
                            if (verboseAboutJobs)
                                System.err.println(String.format("\n[Started job=%d: start=%ds querySeeds=%d refSeeds=%d]", job, (jobStart - initialStart) / 1000, querySeedStores[job].size(), refSeedStores[job].size()));

                            AlignmentEngine2 alignmentEngine = new AlignmentEngine2(alignerOptions, maltOptions, maxRefOccurrencesPerSeed, queryStore, querySeedStores[job], refStore, refSeedStores[job], seedShape, seedShapes, matchStore, alignerStats.getJobStats(job));
                            alignmentEngine.run();
                        } finally {
                            countDownLatch.countDown();
                            progress.incrementProgress();

                            if (verboseAboutJobs) {
                                System.err.println(String.format("\n[Finished job=%d: duration=%ds]", job, ((System.currentTimeMillis() - jobStart) / 1000)));
                            }
                        }
                    }
                });
            }
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
        progress.close();

        System.err.print(alignerStats.getStats());
    }
}
