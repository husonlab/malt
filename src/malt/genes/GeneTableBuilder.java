/*
 *  Copyright (C) 2015 Daniel H. Huson
 *  
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package malt.genes;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.data.ReferencesDBBuilder;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.util.TaggedValueIterator;
import megan.io.OutputWriter;
import megan.util.interval.Interval;
import megan.util.interval.IntervalTree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Builds a table mapping reference indices and positions to genes
 * Daniel Huson, 8.2014, 11.2017
 */
public class GeneTableBuilder {
    final public static byte[] MAGIC_NUMBER = "MAGenesV0.4.".getBytes();

    public static final String[] ACCESSION_TAGS = new String[]{"gb|", "ref|"};

    private final int numberOfSyncObjects = 1024;
    private final Object[] syncObjects = new Object[numberOfSyncObjects];  // use lots of objects to synchronize on so that threads don't in each others way
    private final IdMapper keggMapper;
    private final IdMapper cogMapper;
    private final IdMapper seedMapper;
    private final IdMapper interproMapper;

    /**
     * constructor
     *
     * @throws IOException
     */
    public GeneTableBuilder() throws IOException {
        // create the synchronization objects
        for (int i = 0; i < numberOfSyncObjects; i++)
            syncObjects[i] = new Object();

        if (ClassificationManager.get("KEGG", false).getIdMapper().isActiveMap(IdMapper.MapType.Accession))
            keggMapper = ClassificationManager.get("KEGG", false).getIdMapper();
        else
            keggMapper = null;
        if (ClassificationManager.get("EGGNOG", false).getIdMapper().isActiveMap(IdMapper.MapType.Accession))
            cogMapper = ClassificationManager.get("EGGNOG", false).getIdMapper();
        else
            cogMapper = null;
        if (ClassificationManager.get("SEED", false).getIdMapper().isActiveMap(IdMapper.MapType.Accession))
            seedMapper = ClassificationManager.get("SEED", false).getIdMapper();
        else
            seedMapper = null;

        if (ClassificationManager.get("INTERPRO2GO", false).getIdMapper().isActiveMap(IdMapper.MapType.Accession))
            interproMapper = ClassificationManager.get("INTERPRO2GO", false).getIdMapper();
        else
            interproMapper = null;

    }


    /**
     * build and then save the gene table
     *
     * @param referencesDB
     * @param gffFiles
     * @param indexFile
     * @param numberOfThreads
     * @throws IOException
     */
    public void buildAndSaveGeneTable(final ReferencesDBBuilder referencesDB, final Collection<String> gffFiles, final File indexFile, final int numberOfThreads) throws IOException, CanceledException {
        System.err.println("Building gene table...");
        final Map<String, Integer> accession2refIndex = computeAccession2RefIndex(referencesDB, numberOfThreads);

        final Collection<CDS> annotations = CDS.parseGFFforCDS(gffFiles, new ProgressPercentage());

        final IntervalTree<GeneItem>[] table = computeTable(referencesDB, accession2refIndex, annotations, numberOfThreads);
        accession2refIndex.clear();

        writeTable(indexFile, table);
    }

    /**
     * Compute the accession to references mapping
     *
     * @param referencesDB
     * @param numberOfThreads
     * @return accession to reference index mapping
     */
    private Map<String, Integer> computeAccession2RefIndex(final ReferencesDBBuilder referencesDB, final int numberOfThreads) {
        final Map<String, Integer> accession2refIndex = new HashMap<>(referencesDB.getNumberOfSequences(), 1f);

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        final ProgressPercentage progress = new ProgressPercentage("Mapping accessions to references...", referencesDB.getNumberOfSequences());

        // launch the worker threads
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadNumber = thread;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        final TaggedValueIterator it = new TaggedValueIterator(true, true, ACCESSION_TAGS);
                        for (int refIndex = threadNumber; refIndex < referencesDB.getNumberOfSequences(); refIndex += numberOfThreads) {
                            it.restart(Basic.toString(referencesDB.getHeader(refIndex)));
                            while (it.hasNext()) {
                                synchronized (accession2refIndex) {
                                    accession2refIndex.put(it.next(), refIndex);
                                }
                            }
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
        return accession2refIndex;
    }

    /**
     * compute the gene location table
     *
     * @param referencesDB
     * @param accession2refIndex
     * @param cdsList
     * @param numberOfThreads
     * @return
     * @throws FileNotFoundException
     */
    private IntervalTree<GeneItem>[] computeTable(final ReferencesDBBuilder referencesDB, final Map<String, Integer> accession2refIndex, final Collection<CDS> cdsList, int numberOfThreads) throws IOException {
        final IntervalTree<GeneItem>[] refIndex2Intervals = new IntervalTree[referencesDB.getNumberOfSequences()];

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        final ArrayBlockingQueue<CDS> queue = new ArrayBlockingQueue<>(10 * numberOfThreads);
        final CDS sentinel = new CDS();

        final int[] counts = new int[numberOfThreads];

        // launch the worker threads
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadNumber = thread;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            final CDS cds = queue.take();
                            if (cds == sentinel)
                                break;
                            final Integer refIndex = accession2refIndex.get(cds.getDnaAccession());
                            if (refIndex != null) {
                                synchronized (syncObjects[refIndex % 1024]) {
                                    IntervalTree<GeneItem> tree = refIndex2Intervals[refIndex];
                                    if (tree == null)
                                        tree = refIndex2Intervals[refIndex] = new IntervalTree<>();

                                    final GeneItem geneItem = new GeneItem();
                                    final String accession = cds.getProteinAccession();
                                    geneItem.setProteinId(accession.getBytes());
                                    if (keggMapper != null) {
                                        final Integer id = keggMapper.getIdFromAccession(accession);
                                        if (id != null && id != 0) {
                                            geneItem.setKeggId(id);
                                        }
                                    }
                                    // set cog:
                                    if (cogMapper != null) {
                                        final Integer id = cogMapper.getIdFromAccession(accession);
                                        if (id != null && id != 0) {
                                            geneItem.setCogId(id);
                                        }
                                    }
                                    // set seed:
                                    if (seedMapper != null) {
                                        final Integer id = seedMapper.getIdFromAccession(accession);
                                        if (id != null && id != 0) {
                                            geneItem.setSeedId(id);
                                        }
                                    }
                                    // set interpro:
                                    if (interproMapper != null) {
                                        final Integer id = interproMapper.getIdFromAccession(accession);
                                        if (id != null && id != 0) {
                                            geneItem.setInterproId(id);
                                        }
                                    }
                                    if (cds.isReverse()) {
                                        tree.add(-cds.getEnd(), -cds.getStart(), geneItem);
                                    } else {
                                        tree.add(cds.getStart(), cds.getEnd(), geneItem);
                                    }
                                    counts[threadNumber]++;
                                }
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
        try (ProgressPercentage progress = new ProgressPercentage("Annotating references", cdsList.size() + numberOfThreads)) {
            for (CDS cds : cdsList) {
                queue.put(cds);
                progress.incrementProgress();
            }
            for (int i = 0; i < numberOfThreads; i++) {
                queue.put(sentinel);
                progress.incrementProgress();

            }
            countDownLatch.await();
        } catch (InterruptedException ex) {
            Basic.caught(ex);
        } finally {
            executor.shutdownNow();
        }
        System.err.println("Count: " + Basic.getSum(counts));
        return refIndex2Intervals;
    }

    /**
     * write the table to the named file
     *
     * @param file
     * @param refIndex2Intervals
     * @throws IOException
     */
    static void writeTable(File file, final IntervalTree<GeneItem>[] refIndex2Intervals) throws IOException {

        int totalRefWithAGene = 0;
        try (OutputWriter outs = new OutputWriter(file)) {
            outs.write(GeneTableBuilder.MAGIC_NUMBER, 0, GeneTableBuilder.MAGIC_NUMBER.length);

            outs.writeInt(refIndex2Intervals.length);

            ProgressPercentage progress = new ProgressPercentage("Writing file: " + file, refIndex2Intervals.length);

            for (IntervalTree<GeneItem> intervals : refIndex2Intervals) {
                if (intervals == null) {
                    outs.writeInt(0);
                } else {
                    outs.writeInt(intervals.size());
                    for (Interval<GeneItem> interval : intervals) {
                        outs.writeInt(interval.getStart());
                        outs.writeInt(interval.getEnd());
                        interval.getData().write(outs);
                    }
                    totalRefWithAGene++;
                }
                progress.incrementProgress();
            }
            progress.close();
        }
        System.err.println("Reference sequences with at least one gene: " + totalRefWithAGene + " of " + refIndex2Intervals.length);
    }
}


