/**
 * GeneTableBuilder.java
 * Copyright (C) 2017 Daniel H. Huson
 * <p>
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package malt.genes;

import jloda.util.Basic;
import jloda.util.FileInputIterator;
import jloda.util.ProgressPercentage;
import malt.data.ReferencesDBBuilder;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.util.interval.IntervalTree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Builds a table mapping reference indices and positions to genes
 * Daniel Huson, 8.2014
 *
 * @deprecated
 */
public class GeneTableBuilderUsingGIs {
    private final int numberOfSyncObjects = 1024;
    private final Object[] syncObjects = new Object[numberOfSyncObjects];  // use lots of objects to synchronize on so that threads don't in each others way
    private final IdMapper keggMapper;
    private final IdMapper cogMapper;

    /**
     * constructor
     *
     * @throws IOException
     */
    public GeneTableBuilderUsingGIs() throws IOException {
        // create the synchronization objects
        for (int i = 0; i < numberOfSyncObjects; i++)
            syncObjects[i] = new Object();

        if (ClassificationManager.get("KEGG", false).getIdMapper().isActiveMap(IdMapper.MapType.GI))
            keggMapper = ClassificationManager.get("KEGG", false).getIdMapper();
        else
            keggMapper = null;
        if (ClassificationManager.get("EGGNOG", false).getIdMapper().isActiveMap(IdMapper.MapType.GI))
            cogMapper = ClassificationManager.get("EGGNOG", false).getIdMapper();
        else
            cogMapper = null;
    }

    /**
     * build and then save the gene table
     *
     * @param referencesDB
     * @param inputTableFile
     * @param indexFile
     * @param numberOfThreads
     * @throws IOException
     */
    public void buildAndSaveGeneTable(final ReferencesDBBuilder referencesDB, final String inputTableFile, final File indexFile, final int numberOfThreads) throws IOException {
        System.err.println("Building gene table...");
        final Map<Long, Integer> gi2refIndex = computeGi2RefIndex(referencesDB, numberOfThreads);

        IntervalTree<GeneItem>[] table = computeTable(referencesDB, gi2refIndex, inputTableFile, numberOfThreads);
        gi2refIndex.clear();

        GeneTableBuilder.writeTable(indexFile, table);
    }

    /**
     * Compute the GI to references mapping
     *
     * @param referencesDB
     * @param numberOfThreads
     * @return gi to reference index mapping
     */
    private Map<Long, Integer> computeGi2RefIndex(final ReferencesDBBuilder referencesDB, final int numberOfThreads) {
        final Map<Long, Integer> gi2refIndex = new HashMap<>(referencesDB.getNumberOfSequences(), 1f);

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        final ProgressPercentage progress = new ProgressPercentage("Mapping GI numbers to references...", referencesDB.getNumberOfSequences());

        // launch the worker threads
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadNumber = thread;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        for (int refIndex = threadNumber + 1; refIndex <= referencesDB.getNumberOfSequences(); refIndex += numberOfThreads) {
                            long gi = parseGI(Basic.toString(referencesDB.getHeader(refIndex)));
                            if (gi > 0)
                                gi2refIndex.put(gi, refIndex);
                            progress.incrementProgress();
                        }
                    } catch (Exception ex) {
                        Basic.caught(ex);
                        System.exit(1);  // just die...
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        try {
            countDownLatch.await();  // await completion of alignment threads
        } catch (InterruptedException e) {
            Basic.caught(e);
        } finally {
            // shut down threads:
            executor.shutdownNow();
        }
        progress.close();
        return gi2refIndex;
    }

    /**
     * compute the gene location table
     *
     * @param referencesDB
     * @param gi2refIndex
     * @param geneTableFile
     * @param numberOfThreads
     * @return
     * @throws FileNotFoundException
     */
    private IntervalTree<GeneItem>[] computeTable(final ReferencesDBBuilder referencesDB, final Map<Long, Integer> gi2refIndex, String geneTableFile, int numberOfThreads) throws IOException {
        final IntervalTree<GeneItem>[] refIndex2Intervals = new IntervalTree[referencesDB.getNumberOfSequences() + 1];  // plus one because refindices start at 1
        final FileInputIterator it = new FileInputIterator(geneTableFile);

        final ProgressPercentage progress = new ProgressPercentage("Processing file: " + geneTableFile, (new File(geneTableFile)).length() / 100);

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        final long[] countLinesRead = new long[]{0L};
        final long[] countLinesParsed = new long[numberOfThreads];

        // launch the worker threads
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadNumber = thread;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        while (it.hasNext()) {
                            String aLine;
                            synchronized (refIndex2Intervals) {
                                if (it.hasNext()) {
                                    aLine = it.next();
                                    progress.setProgress(it.getProgress());
                                    countLinesRead[0]++;
                                } else
                                    return; // input has finished

                            }
                            if (processALine(aLine, referencesDB, gi2refIndex, refIndex2Intervals)) {
                                countLinesParsed[threadNumber]++;
                            }
                        }

                    } catch (Exception ex) {
                        Basic.caught(ex);
                        System.exit(1);  // just die...
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        try {
            countDownLatch.await();  // await completion of alignment threads
        } catch (InterruptedException e) {
            Basic.caught(e);
        } finally {
            // shut down threads:
            executor.shutdownNow();
        }
        it.close();
        progress.close();
        System.err.println("Lines parsed: " + Basic.getSum(countLinesParsed) + " of " + countLinesRead[0]);
        return refIndex2Intervals;
    }

    /**
     * processes a line of input and adds genes to appropriate interval tree
     * todo: Format: reference-gi-number coordinates gene-gi-number protein-id gene-name product
     *
     * @param aLine
     * @param gi2refIndex
     * @param refIndex2Intervals
     * @return true, if successfully parsed
     */
    private boolean processALine(String aLine, final ReferencesDBBuilder referencesDB, final Map<Long, Integer> gi2refIndex, final IntervalTree<GeneItem>[] refIndex2Intervals) {

        String[] tokens = aLine.split("\t");
        if (tokens.length == 6) {
            try {
                GeneItem geneItem = new GeneItem();
                long referenceGi = Basic.parseLong(tokens[0].trim());
                if (referenceGi == 0)
                    return false;
                Integer refIndex = gi2refIndex.get(referenceGi);
                if (refIndex == null || refIndex == 0)
                    return false;

                int[] location;
                if (tokens[1].equals("*")) { // a "*" indicates to use the whole sequence
                    location = new int[]{1, referencesDB.getSequence(refIndex).length};
                } else {
                    location = GeneTableBuilder.parseLocations(tokens[1]);
                    if (location == null || location.length == 0)
                        return false; // no locations, skip
                }
                final long giNumber;
                if (tokens[2].equals("*")) { // a "*" indicates to use the same GI number as reference
                    giNumber = referenceGi;
                } else
                    giNumber = Basic.parseLong(tokens[2].trim());
                // set ko number:
                if (keggMapper != null) {
                    Integer ko = keggMapper.getIdFromGI(giNumber);
                    if (ko != null && ko != 0) {
                        geneItem.setKeggId(String.format("K%05d", ko).getBytes());
                        // System.err.println("gi: "+geneItem.getGiNumber()+" ko: "+ko);
                    }
                }
                // set cog:
                if (cogMapper != null) {
                    Integer cog = cogMapper.getIdFromGI(giNumber);
                    if (cog != null && cog != 0) {
                        String name = cogMapper.getName2IdMap().get(cog);
                        if (name != null)
                            geneItem.setCogId(name.getBytes());
                    }
                }

                geneItem.setProteinId(tokens[3].trim().getBytes());
                geneItem.setGeneName(tokens[4].trim().getBytes());
                geneItem.setProduct(tokens[5].trim().getBytes());

                if (giNumber == 0 && geneItem.getProteinId().length == 0 && (geneItem.getGeneName() == null || geneItem.getGeneName().length == 0)
                        && (geneItem.getProduct() == null || geneItem.getProduct().length == 0))
                    return false; // no info, skip

                synchronized (syncObjects[refIndex % numberOfSyncObjects]) {
                    IntervalTree<GeneItem> intervals = refIndex2Intervals[refIndex];
                    if (intervals == null) {
                        intervals = new IntervalTree<>();
                        refIndex2Intervals[refIndex] = intervals;
                    }
                    int start = location[0];
                    int end = location[1];
                    if (start > 0 && end >= start + 50) {
                        int length = end - start + 1;
                        if (length >= 20 && length <= 500000)
                            intervals.add(start, end, geneItem);
                        else
                            System.err.println("Unrealistic gene coordinates: " + start + " - " + end + ", length= "
                                    + (end - start + 1) + " for gi number=" + referenceGi);
                    }

                    if (location.length == 4) {
                        start = location[2];
                        end = location[3];
                        if (start > 0 && end >= start + 50) {
                            int length = end - start + 1;
                            if (length >= 20 && length <= 500000)
                                intervals.add(start, end, geneItem);
                            else
                                System.err.println("Unrealistic gene coordinates: " + start + " - " + end + ", length= "
                                        + (end - start + 1) + " for gi number=" + referenceGi);
                        }
                    }
                    return location.length > 0;
                }
            } catch (Exception ex) {
                // Basic.caught(ex);
                // System.err.println("Skipping line: " + aLine);
            }
        }
        return false;
    }

    /**
     * gets gi number
     *
     * @param string
     * @return accession
     */
    private static long parseGI(String string) {
        try {
            int a = string.indexOf("gi|");
            if (a >= 0 && a + "gi|".length() < string.length()) {
                a += "gi|".length();
                while (!Character.isDigit(string.charAt(a)) && a < string.length())
                    a++;
                int b = a;
                while (b < string.length() && Character.isDigit(string.charAt(b))) {
                    b++;
                }
                if (a < b)
                    return Basic.parseLong(string.substring(a, b));
            }
        } catch (Exception ex) {
        }
        return 0L;
    }
}


