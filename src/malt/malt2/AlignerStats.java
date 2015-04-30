package malt.malt2;

import jloda.util.Basic;

/**
 * track and report on some simple alignment performance stats
 * by huson on 9/24/14.
 */
public class AlignerStats {
    private final long[] countSeedMatches;
    private final long[] countHasIdentities;
    private final long[] countGoodUngappedAlignments;
    private final long[] countGoodAlignments;
    private final long[] countLeftmost;
    private final long[] countTextProduced;
    private final long[] countAddedAlignments;

    private final JobStats[] jobStats;

    /**
     * constructor
     *
     * @param numberOfJobs
     */
    public AlignerStats(int numberOfJobs) {
        countHasIdentities = new long[numberOfJobs];
        countSeedMatches = new long[numberOfJobs];
        countGoodUngappedAlignments = new long[numberOfJobs];
        countGoodAlignments = new long[numberOfJobs];
        countLeftmost = new long[numberOfJobs];
        countTextProduced = new long[numberOfJobs];
        countAddedAlignments = new long[numberOfJobs];


        jobStats = new JobStats[numberOfJobs];
        for (int i = 0; i < jobStats.length; i++)
            jobStats[i] = new JobStats(i);
    }


    public long getCountSeedMatches() {
        return Basic.getSum(countSeedMatches);
    }

    public long getCountHasIdentities() {
        return Basic.getSum(countHasIdentities);
    }

    public long getCountGoodUngappedAlignments() {
        return Basic.getSum(countGoodUngappedAlignments);
    }

    public long getCountGoodAlignments() {
        return Basic.getSum(countGoodAlignments);
    }

    public long getCountLeftmost() {
        return Basic.getSum(countLeftmost);
    }

    public long getCountTextProduced() {
        return Basic.getSum(countTextProduced);
    }

    public long getCountAddedAlignments() {
        return Basic.getSum(countAddedAlignments);
    }

    public String getStats() {
        return String.format("Aligner algorithm stats:\n")
                + String.format("Seed matches:    %,15d\n", getCountSeedMatches())
                + (getCountSeedMatches() > getCountHasIdentities() ? String.format("Seed identities: %,15d\n", getCountHasIdentities()) : "")
                + String.format("Leftmost seeds:  %,15d\n", getCountLeftmost())
                + String.format("Good ungapped al:%,15d\n", getCountGoodUngappedAlignments())
                + String.format("Good alignments: %,15d\n", getCountGoodAlignments())
                + String.format("Text produced:   %,15d\n", getCountTextProduced())
                + String.format("Added alignments %,15d\n", getCountAddedAlignments());
    }

    /**
     * get the job stats object for the given job number
     *
     * @param job
     * @return job stats
     */
    public JobStats getJobStats(int job) {
        return jobStats[job];
    }

    public class JobStats {
        private final int job;

        public JobStats(int job) {
            this.job = job;
        }

        public void incrementSeedMatches() {
            countSeedMatches[job]++;
        }

        public void incrementHasIdentities() {
            countHasIdentities[job]++;
        }

        public void incrementUngappedAlignments() {
            countGoodUngappedAlignments[job]++;
        }

        public void incrementGoodAlignments() {
            countGoodAlignments[job]++;
        }

        public void incrementLeftmost() {
            countLeftmost[job]++;
        }

        public void incrementTextProduced() {
            countTextProduced[job]++;
        }

        public void incrementAddedAlignments() {
            countAddedAlignments[job]++;
        }
    }
}

