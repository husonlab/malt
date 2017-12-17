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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
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
    final static byte[] MAGIC_NUMBER_IDX = "MAAnnoIdxV0.1.".getBytes();
    final static byte[] MAGIC_NUMBER_DB = "MAAnnoDbV0.1.".getBytes();

    public static final String[] ACCESSION_TAGS = new String[]{"gb|", "ref|"};

    private final IdMapper keggMapper;
    private final IdMapper cogMapper;
    private final IdMapper seedMapper;
    private final IdMapper interproMapper;

    private final int syncBits = 1023;
    private final Object[] syncObjects = new Object[syncBits + 1];  // use lots of objects to synchronize on so that threads don't in each others way

    /**
     * constructor
     *
     * @throws IOException
     */
    public GeneTableBuilder() throws IOException {
        // create the synchronization objects
        for (int i = 0; i < (syncBits + 1); i++) {
            syncObjects[i] = new Object();
        }

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
    public void buildAndSaveAnnotations(final ReferencesDBBuilder referencesDB, final Collection<String> gffFiles, final File indexFile, final File dbFile, final int numberOfThreads) throws IOException, CanceledException {
        System.err.println("Annotating reference sequences...");
        final Map<String, Integer> refAccession2IndexMap = computeRefAccession2IndexMap(referencesDB, numberOfThreads);

        final Collection<CDS> annotations = CDS.parseGFFforCDS(gffFiles, new ProgressPercentage());

        final ArrayList<Interval<GeneItem>>[] table = computeRefIndex2Intervals(referencesDB, refAccession2IndexMap, annotations, numberOfThreads);
        refAccession2IndexMap.clear();

        writeTable(indexFile, dbFile, table);
    }

    /**
     * Compute the reference accessions to reference index mapping
     *
     * @param referencesDB
     * @param numberOfThreads
     * @return accession to reference index mapping
     */
    private Map<String, Integer> computeRefAccession2IndexMap(final ReferencesDBBuilder referencesDB, final int numberOfThreads) {
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
     * compute the reference Id to intervals mapping
     *
     * @param referencesDB
     * @param refAccession2IdMap
     * @param cdsList
     * @param numberOfThreads
     * @return
     * @throws FileNotFoundException
     */
    private ArrayList<Interval<GeneItem>>[] computeRefIndex2Intervals(final ReferencesDBBuilder referencesDB, final Map<String, Integer> refAccession2IdMap, final Collection<CDS> cdsList, int numberOfThreads) throws IOException {
        final ArrayList<Interval<GeneItem>>[] refIndex2Intervals = new ArrayList[referencesDB.getNumberOfSequences()];

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
                            final Integer refIndex = refAccession2IdMap.get(cds.getDnaId());
                            if (refIndex != null) {
                                synchronized (syncObjects[refIndex & syncBits]) {
                                    ArrayList<Interval<GeneItem>> list = refIndex2Intervals[refIndex];
                                    if (list == null)
                                        list = refIndex2Intervals[refIndex] = new ArrayList<>();

                                    final GeneItem geneItem = new GeneItem();
                                    final String accession = cds.getProteinId();
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
                                    geneItem.setReverse(cds.isReverse());
                                    list.add(new Interval<>(cds.getStart(), cds.getEnd(), geneItem));
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
        System.err.println(String.format("Count:%,14d", Basic.getSum(counts)));
        return refIndex2Intervals;
    }

    /**
     * save the annotations
     *
     * @param indexFile
     * @param refIndex2Intervals
     * @throws IOException
     */
    private static void writeTable(File indexFile, File dbFile, final ArrayList<Interval<GeneItem>>[] refIndex2Intervals) throws IOException {
        final long[] refIndex2FilePos = new long[refIndex2Intervals.length];

        int totalRefWithAGene = 0;
        try (OutputWriter outs = new OutputWriter(dbFile); ProgressPercentage progress = new ProgressPercentage("Writing file: " + dbFile, refIndex2Intervals.length)) {
            outs.write(GeneTableBuilder.MAGIC_NUMBER_DB);

            outs.writeInt(refIndex2Intervals.length);

            for (int i = 0; i < refIndex2Intervals.length; i++) {
                final ArrayList<Interval<GeneItem>> list = refIndex2Intervals[i];
                if (list == null) {
                    refIndex2FilePos[i] = 0;
                    outs.writeInt(0);
                } else {
                    refIndex2FilePos[i] = outs.length();
                    outs.writeInt(list.size());
                    for (Interval<GeneItem> interval : Basic.randomize(list, 666)) { // need to save in random order
                        outs.writeInt(interval.getStart());
                        outs.writeInt(interval.getEnd());
                        interval.getData().write(outs);
                    }
                    totalRefWithAGene++;
                }
                progress.incrementProgress();
            }
        }
        try (OutputWriter outs = new OutputWriter(indexFile); ProgressPercentage progress = new ProgressPercentage("Writing file: " + indexFile, refIndex2FilePos.length)) {
            outs.write(GeneTableBuilder.MAGIC_NUMBER_IDX);
            outs.writeInt(refIndex2FilePos.length);
            for (long filePos : refIndex2FilePos) {
                outs.writeLong(filePos);
                progress.incrementProgress();
            }
        }

        System.err.println(String.format("Reference sequences with at least one annotation: %,d of %,d", totalRefWithAGene, refIndex2Intervals.length));
    }
}


