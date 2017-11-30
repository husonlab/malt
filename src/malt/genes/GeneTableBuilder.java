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
import jloda.util.FileInputIterator;
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
     * @param inputTableFile
     * @param indexFile
     * @param numberOfThreads
     * @throws IOException
     */
    public void buildAndSaveGeneTable(final ReferencesDBBuilder referencesDB, final String inputTableFile, final File indexFile, final int numberOfThreads) throws IOException {
        System.err.println("Building gene table...");
        final Map<String, Integer> accession2refIndex = computeAccession2RefIndex(referencesDB, numberOfThreads);

        final IntervalTree<GeneItem>[] table = computeTable(referencesDB, accession2refIndex, inputTableFile, numberOfThreads);
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
     * @param geneTableFile
     * @param numberOfThreads
     * @return
     * @throws FileNotFoundException
     */
    private IntervalTree<GeneItem>[] computeTable(final ReferencesDBBuilder referencesDB, final Map<String, Integer> accession2refIndex, String geneTableFile, int numberOfThreads) throws IOException {
        final IntervalTree<GeneItem>[] refIndex2Intervals = new IntervalTree[referencesDB.getNumberOfSequences()];  // plus one because refindices start at 1

        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        final long[] countLinesRead = new long[numberOfThreads];
        final long[] countLinesParsed = new long[numberOfThreads];

        final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10 * numberOfThreads);
        final String sentinel = "DONE!!!:";

        // launch the worker threads
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadNumber = thread;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            final String aLine = queue.take();
                            if (aLine == sentinel)
                                break;
                            countLinesRead[threadNumber]++;
                            if (processALine(aLine, referencesDB, accession2refIndex, refIndex2Intervals)) {
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
        try (final FileInputIterator it = new FileInputIterator(geneTableFile, true)) {
            while (it.hasNext()) {
                try {
                    queue.put(it.next());
                } catch (InterruptedException e) {
                    Basic.caught(e);
                }
            }
        }
        // add sentinels
        for (int i = 0; i < numberOfThreads; i++) {
            try {
                queue.put(sentinel);
            } catch (InterruptedException e) {
                e.printStackTrace();
                while (countDownLatch.getCount() > 0)
                    countDownLatch.countDown();
            }
        }

        try {
            countDownLatch.await();  // await completion of alignment threads
        } catch (InterruptedException e) {
            Basic.caught(e);
        } finally {
            // shut down threads:
            executor.shutdownNow();
        }
        System.err.println("Lines parsed: " + Basic.getSum(countLinesParsed) + " of " + Basic.getSum(countLinesRead));
        return refIndex2Intervals;
    }

    /**
     * processes a line of input and adds genes to appropriate interval tree
     * todo: Format: reference-accession coordinates gene-accession product
     *
     * @param aLine
     * @param accession2refIndex
     * @param refIndex2Intervals
     * @return true, if successfully parsed
     */
    private boolean processALine(String aLine, final ReferencesDBBuilder referencesDB, final Map<String, Integer> accession2refIndex, final IntervalTree<GeneItem>[] refIndex2Intervals) {
        final String[] tokens = aLine.split("\t");
        if (tokens.length == 4) {
            try {
                final GeneItem geneItem = new GeneItem();
                final String referenceAcc = tokens[0].trim();
                if (referenceAcc.length() == 0)
                    return false;
                final Integer refIndex = accession2refIndex.get(referenceAcc);
                if (refIndex == null)
                    return false;

                int[] location;
                if (tokens[1].equals("*")) { // a "*" indicates to use the whole sequence
                    location = new int[]{1, referencesDB.getSequence(refIndex).length};
                } else {
                    location = parseLocations(tokens[1]);
                    if (location == null || location.length == 0)
                        return false; // no locations, skip
                }
                final String accession;
                if (tokens[2].equals("*")) { // a "*" indicates to use the same GI number as reference
                    accession = referenceAcc;
                } else
                    accession = tokens[2].trim();
                // set ko number:
                if (keggMapper != null)
                {
                    final Integer id = keggMapper.getIdFromAccession(accession);
                    if (id != null && id != 0) {
                        geneItem.setKeggId(id);
                    }
                }
                // set cog:
                if (cogMapper != null)
                {
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

                geneItem.setProteinId(accession.getBytes());
                geneItem.setGeneName(tokens[2].trim().getBytes());
                geneItem.setProduct(tokens[3].trim().getBytes());

                if (accession.length() == 0 && geneItem.getProteinId().length == 0 && (geneItem.getGeneName() == null || geneItem.getGeneName().length == 0)
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

                    if (start > end) { // use negative coordinates for opposite strand
                        start *= -1;
                        end *= -1;
                    }

                    if (end >= start + 50) {
                        int length = end - start + 1;
                        if (length >= 20 && length <= 500000)
                            intervals.add(start, end, geneItem);
                        else
                            System.err.println("Unrealistic gene coordinates: " + start + " - " + end + ", length= "
                                    + (end - start + 1) + " for accession=" + accession);
                    }

                    if (location.length == 4) {
                        start = location[2];
                        end = location[3];

                        if (start > end) { // use negative coordinates for opposite strand
                            start *= -1;
                            end *= -1;
                        }

                        if (end >= start + 50) {
                            int length = end - start + 1;
                            if (length >= 20 && length <= 500000)
                                intervals.add(start, end, geneItem);
                            else
                                System.err.println("Unrealistic gene coordinates: " + start + " - " + end + ", length= "
                                        + (end - start + 1) + " for accession=" + accession);
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


    /**
     * parses the location of a gene.
     * Possible formats
     * START END
     * START..END,START..END,...
     * complement(START..END,..)
     * join(START..END,START..END,...)
     * complement(join(START..END,START..END,..))
     * START and END are integer
     * START can have prefix LABEL:  - if it does, then we will ignore this entry
     * In addition,
     * START can have prefix <
     * END can have prefix >
     *
     * @param aLine
     * @return
     */
    static int[] parseLocations(String aLine) {
        if (Basic.countOccurrences(aLine, ' ') == 1) {
            int pos = aLine.indexOf(' ');
            if (Basic.isInteger(aLine.substring(0, pos)) && Basic.isInteger(aLine.substring(pos + 1)))
                return new int[]{Basic.parseInt(aLine.substring(0, pos)), Basic.parseInt(aLine.substring(pos + 1))};
        }
        if (Basic.countOccurrences(aLine, '(') != Basic.countOccurrences(aLine, ')'))
            return null;
        int a = aLine.lastIndexOf("(");
        if (a != -1)
            aLine = aLine.substring(a + 1, aLine.length());
        int b = aLine.indexOf(")");
        if (b != -1)
            aLine = aLine.substring(0, b);

        String[] tokens = aLine.split(",");
        int count = 0;
        int start1 = -1;
        int end1 = 0;
        int start2 = 0;
        int end2 = -1;
        for (String token : tokens) {
            if (!token.contains(":")) {
                switch (count) {
                    case 0: {
                        start1 = getFirstNumber(token);
                        end1 = getLastNumber(token);
                        count++;
                        break;
                    }
                    case 1: {
                        start2 = getFirstNumber(token);
                        end2 = getLastNumber(token);
                        count++;
                        break;
                    }
                }
                if (count == 2)
                    break;
            }
        }
        int length1 = (end1 - start1 + 1);
        int length2 = (end2 - start2 + 1);

        if (length1 >= 20) {
            if (length2 >= 20 && start2 > 0 && end2 >= start1 + 50) {
                return new int[]{start1, end1, start2, end2};
            } else
                return new int[]{start1, end1};
        } else if (length2 >= 20) {
            return new int[]{start2, end2};
        } else
            return null;
    }

    private static int getFirstNumber(String str) {
        int a = 0;
        while (a < str.length() && !Character.isDigit(str.charAt(a)))
            a++;
        int b = a;
        while (b < str.length() && Character.isDigit(str.charAt(b)))
            b++;
        if (a < b)
            return Integer.parseInt(str.substring(a, b));
        else
            return 0;
    }

    private static int getLastNumber(String str) {
        int b = str.length();
        while (b > 0 && !Character.isDigit(str.charAt(b - 1)))
            b--;
        int a = b - 1;
        while (a > 0 && Character.isDigit(str.charAt(a - 1)))
            a--;
        if (a < b)
            return Integer.parseInt(str.substring(a, b));
        else
            return 0;
    }
}


