package malt.next;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.ITextProducer;
import malt.io.BlastTextHelper;

import java.io.IOException;
import java.io.Writer;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * container for all matches associated with a dataset
 */
public class MatchStore {
    private final AMatch[] matches;
    private final int maxMatchesPerQuery;
    private final int maxMatchesPerReference;

    // use lots of locks so that threads don't get in each others way
    private final int NUMBER_OF_SYNC_OBJECTS = (1 << 10);
    private final int SYNC_MASK = (NUMBER_OF_SYNC_OBJECTS - 1);
    private final Object[] syncObjects = new Object[NUMBER_OF_SYNC_OBJECTS];

    /**
     * constructor
     *
     * @param numberOfQueries
     * @param maxMatchesPerQuery
     * @param maxMatchesPerReference
     */
    public MatchStore(int numberOfQueries, int maxMatchesPerQuery, int maxMatchesPerReference) {
        matches = new AMatch[numberOfQueries];
        this.maxMatchesPerQuery = maxMatchesPerQuery;
        this.maxMatchesPerReference = maxMatchesPerReference;

        for (int i = 0; i < NUMBER_OF_SYNC_OBJECTS; i++) {
            syncObjects[i] = new Object();
        }
    }

    /**
     * add a match. Ensures that the maximum number of matches per query and per reference id is not violated
     *
     * @param queryId
     * @param referenceId
     * @param rawScore
     * @param textProducer we only call this method if we really want to keep the match
     * @return true, if match added
     */
    public boolean addMatch(final int queryId, final int referenceId, final int rawScore, final ITextProducer textProducer) {

        synchronized (syncObjects[queryId & SYNC_MASK]) {
            AMatch match = matches[queryId];
            if (match == null) {
                matches[queryId] = new AMatch(referenceId, rawScore, textProducer.getText(), null); // this is the first match...
                return true;
            } else { // already have saved some matches, need to determine whether to save the current one
                int worstScoreSameRef = rawScore;
                AMatch worstMatchSameRef = null;
                int countWithSameRefId = 0;

                int worstScore = rawScore;
                int worstRefId = referenceId;
                AMatch worstMatch = null;
                int count = 0;

                while (match != null) {
                    if ((match.getRawScore() < worstScore) || ((match.getRawScore() == worstScore) && (match.getReferenceId() > worstRefId))) {
                        worstMatch = match;
                        worstScore = match.getRawScore();
                        worstRefId = match.getReferenceId();
                    }
                    if (match.getReferenceId() == referenceId) {
                        if (match.getRawScore() < worstScoreSameRef) {
                            worstMatchSameRef = match;
                            worstScoreSameRef = match.getRawScore();
                        }
                        if ((match.getRawScore() == rawScore)) {
                            if (Basic.equal(match.getText(), textProducer.getText())) {

                                System.err.println("Identical matches:");
                                System.err.println(Basic.toString(textProducer.getText()));
                                System.err.println(Basic.toString(match.getText()));

                                return false; // identical match, do not keep
                            }
                        }
                        countWithSameRefId++;
                    }
                    match = match.getNext();
                    count++;
                }

                if (countWithSameRefId >= maxMatchesPerReference) {
                    if (worstMatchSameRef == null)
                        return false; // no worse match to replace, discard this match
                    else {
                        worstMatchSameRef.replace(referenceId, rawScore, textProducer.getText());
                        return true; // replaced a match, no change in number of matches
                    }
                } else if (count >= maxMatchesPerQuery) {
                    if (worstMatch == null)  // no worse match to replace, discard this match
                        return false;
                    else {
                        worstMatch.replace(referenceId, rawScore, textProducer.getText());
                        return true; // replaced a match, no change in number of matches
                    }
                } else { // add new match
                    matches[queryId] = new AMatch(referenceId, rawScore, textProducer.getText(), matches[queryId]);
                    return true;
                }
            }
        }
    }

    /**
     * get the number of queries represented
     *
     * @return number of queries
     */
    public int getNumberOfQueries() {
        return matches.length;
    }

    /**
     * write match in SAM format
     *
     * @param writer
     * @return number of matches written
     * @throws java.io.IOException
     */
    public int writeSAMOrBlastTab(AMatch match, byte[] queryName, Writer writer) throws IOException {
        int count = 0;
        while (match != null) {
            for (byte b : queryName) writer.write((char) b);
            writer.write('\t');
            byte[] text = match.getText();
            for (byte b : text) writer.write((char) b);
            match = match.getNext();
            writer.write('\n');
            count++;
        }
        return count;
    }

    /**
     * write the given query to a string
     *
     * @param match
     * @param writer
     * @return number of matches written
     * @throws java.io.IOException
     */
    public int writeBlastText(AMatch match, byte[] queryHeader, int queryLength, final ReferenceStore refStore, Writer writer) throws IOException {
        writer.write(BlastTextHelper.QUERY_EQUALS_STRING);

        for (byte b : queryHeader) writer.write((char) b);

        writer.write(String.format(BlastTextHelper.QUERY_LETTERS_FORMAT_STRING, queryLength));

        if (match == null) {
            writer.write(BlastTextHelper.NO_HITS_STRING);
            writer.write('\n');
            return 0;
        } else {
            int count = 0;
            while (match != null) {
                byte[] ref = refStore.getHeader(match.getReferenceId());
                for (byte b : ref) writer.write((char) b);
                writer.write(String.format(BlastTextHelper.REFERENCE_LENGTH_FORMAT_STRING, refStore.getSequenceLength(match.getReferenceId())));
                byte[] text = match.getText();
                for (byte b : text) writer.write((char) b);
                match = match.getNext();
                writer.write('\n');
                count++;
            }
            return count;
        }
    }

    /**
     * sort the matches associated with the i-th query
     *
     * @param i
     */
    private void sort(SortedSet<AMatch> set, int i) {
        AMatch aMatch = matches[i];
        if (aMatch != null) {
            /*
            if(count(aMatch)>maxMatchesPerQuery)
                System.err.println("index="+i+": too many matches before sorting: "+count(aMatch));
                */
            set.clear();
            while (aMatch != null) {
                set.add(aMatch);
                aMatch = aMatch.getNext();
            }
            if (set.size() > 1) {
                aMatch = null; //  used to store previous match in code below
                for (AMatch bMatch : set) {
                    bMatch.setNext(null);
                    if (aMatch == null) {
                        matches[i] = bMatch;
                    } else {
                        aMatch.setNext(bMatch);
                    }
                    aMatch = bMatch;
                }
            }
            /*
            if(count(matches[i])>maxMatchesPerQuery)
                System.err.println("index="+i+": too many matches after sorting: "+count(aMatch));
                */

        }
    }

    /**
     * count number of matches in chain
     *
     * @param aMatch
     * @return matches
     */
    private static int count(AMatch aMatch) {
        if (aMatch.getNext() == null)
            return 1;
        else
            return count(aMatch.getNext()) + 1;
    }

    /**
     * sort all matches (in parallel)
     *
     * @param numberOfThreads
     */
    public void sort(final ExecutorService threadPool, final int numberOfThreads) {
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        final ProgressPercentage progress = new ProgressPercentage("Sorting matches...");

        for (int t = 0; t < numberOfThreads; t++) {
            final int threadNumber = t;
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        final int low = (threadNumber * getNumberOfQueries()) / numberOfThreads;
                        final int high = ((threadNumber + 1) * getNumberOfQueries()) / numberOfThreads;
                        if (threadNumber == 0)
                            progress.setMaximum(high);
                        // System.err.println("Range: "+low+" - "+high);

                        final SortedSet<AMatch> set = new TreeSet<AMatch>(new AMatch());
                        for (int i = low; i < high; i++) {
                            sort(set, i);
                            if (threadNumber == 0)
                                progress.setProgress(i);
                        }
                    } finally {
                        countDownLatch.countDown();
                    }

                }
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Basic.caught(e);
        }
        progress.close();
    }

    /**
     * gets a match associated with the given id, or null
     *
     * @param queryId
     * @return match or null
     */
    public AMatch get(int queryId) {
        return matches[queryId];
    }
}
