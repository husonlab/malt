/**
 * GeneTableAccess.java
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

import jloda.util.*;
import malt.analysis.ReadMatchItem;
import megan.util.interval.Interval;
import megan.util.interval.IntervalTree;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/**
 * class used to access gene table
 * Daniel Huson, 8.2014
 */
public class GeneTableAccess {
    private final int size;
    private final long[] refIndex2FilePos;
    private final IntervalTree<GeneItem>[] refIndex2Intervals;
    private final RandomAccessFile dbRaf;

    private final int syncBits = 1023;
    private final Object[] syncObjects = new Object[syncBits + 1];  // use lots of objects to synchronize on so that threads don't in each others way

    /**
     * construct the gene table from the gene-table index file
     *
     * @param indexFile
     * @param dbFile
     * @throws IOException
     */
    public GeneTableAccess(File indexFile, File dbFile) throws IOException {
        // create the synchronization objects
        for (int i = 0; i < (syncBits + 1); i++) {
            syncObjects[i] = new Object();
        }

        try (DataInputStream ins = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile))); ProgressPercentage progress = new ProgressPercentage("Reading file: " + indexFile)) {
            Basic.readAndVerifyMagicNumber(ins, GeneTableBuilder.MAGIC_NUMBER_IDX);
            size = ins.readInt();
            progress.setMaximum(size);
            refIndex2FilePos = new long[size];
            for (int i = 0; i < size; i++) {
                refIndex2FilePos[i] = ins.readLong();
                progress.incrementProgress();
            }
        }
        refIndex2Intervals = (IntervalTree<GeneItem>[]) new IntervalTree[size];

        try (DataInputStream ins = new DataInputStream(new BufferedInputStream(new FileInputStream(dbFile)))) {
            Basic.readAndVerifyMagicNumber(ins, GeneTableBuilder.MAGIC_NUMBER_DB);
            if (ins.readInt() != size)
                throw new IOException("Sizes differ: " + indexFile + " vs " + dbFile);
        }
        dbRaf = new RandomAccessFile(dbFile, "r");
    }

    private int warned = 0;

    /**
     * get intervals for a given ref index
     *
     * @param refIndex
     * @return intervals or null
     * @throws IOException
     */
    private IntervalTree<GeneItem> getIntervals(int refIndex) {
        synchronized (syncObjects[refIndex & syncBits]) {
            if (refIndex2Intervals[refIndex] == null && refIndex2FilePos[refIndex] != 0) {
                synchronized (dbRaf) {
                    try {
                        dbRaf.seek(refIndex2FilePos[refIndex]);
                        int intervalsLength = dbRaf.readInt();
                        if (intervalsLength > 0) {
                            IntervalTree<GeneItem> intervals = new IntervalTree<>();
                            for (int i = 0; i < intervalsLength; i++) {
                                int start = dbRaf.readInt();
                                int end = dbRaf.readInt();
                                GeneItem geneItem = new GeneItem();
                                geneItem.read(dbRaf);
                                intervals.add(start, end, geneItem);
                                //System.err.println(refIndex+"("+start+"-"+end+") -> "+geneItem);
                            }
                            refIndex2Intervals[refIndex] = intervals;
                        }
                    } catch (IOException ex) {
                        if (warned < 10) {
                            Basic.caught(ex);
                            if (++warned == 0) {
                                System.err.println("Suppressing all further such exceptions");
                            }
                        }
                    }
                }

            }
            return refIndex2Intervals[refIndex];
        }
    }

    /**
     * get genes associated with this read. Genes are reported by decreasing weight of the reference sequence and then decreasing bit score
     *
     * @param refIndex2weight
     * @param matches
     * @param genes
     * @return number of genes returned in array "genes"
     */
    public int getGenes(final Map<Integer, Integer> refIndex2weight, final ReadMatchItem[] matches, GeneItem[] genes) {

        ReadMatchItem[] sorted = new ReadMatchItem[matches.length];
        System.arraycopy(matches, 0, sorted, 0, matches.length);

        // sort matches by decreasing weight of reference sequence:
        if (refIndex2weight != null) {
            Arrays.sort(sorted, new Comparator<ReadMatchItem>() {
                public int compare(ReadMatchItem a, ReadMatchItem b) {
                    Integer aWeight = refIndex2weight.get(a.refIndex);
                    Integer bWeight = refIndex2weight.get(b.refIndex);
                    if (aWeight != null) {
                        if (bWeight == null || bWeight < aWeight)
                            return -1;
                        else if (bWeight > aWeight)
                            return 1;
                        else {
                            return Float.compare(b.score, a.score);
                        }
                    } else if (bWeight != null)
                        return 1;
                    else {       // both references have zero weight
                        return Float.compare(b.score, a.score);
                    }
                }
            });
        } else {
            Arrays.sort(sorted, unweightedComparator);
        }

        int numberOfGenes = 0;
        loop:
        for (ReadMatchItem match : sorted) {
            if (match.refIndex < size) {
                final IntervalTree<GeneItem> intervals = getIntervals(match.refIndex);
                if (intervals != null) {
                    for (Interval<GeneItem> interval : intervals.getIntervals(match.refStart, match.refEnd)) {
                        genes[numberOfGenes++] = interval.getData();
                        if (numberOfGenes == genes.length)
                            break loop;
                    }
                }
            }
        }
        return numberOfGenes;
    }

    /**
     * adds annotations to reference header
     *
     * @param referenceHeader
     * @param refIndex
     * @param alignStart
     * @param alignEnd
     * @return annotated reference header
     */
    public byte[] addAnnotationString(byte[] referenceHeader, Integer refIndex, int alignStart, int alignEnd) {
        final IntervalTree<GeneItem> tree = getIntervals(refIndex);

        if (tree != null) {
            final Interval<Object> alignInterval = new Interval<>(alignStart, alignEnd, null);
            final Interval<GeneItem> refInterval = tree.getBestInterval(alignInterval, 0.9);
            if (refInterval != null) {
                final GeneItem geneItem = refInterval.getData();

                final StringBuilder buf = new StringBuilder();
                buf.append("|ref|").append(Basic.toString(geneItem.getProteinId()));
                if (geneItem.getKeggId() != 0)
                    buf.append("|kegg|").append(geneItem.getKeggId());
                if (geneItem.getCogId() != 0)
                    buf.append("|cog|").append(geneItem.getCogId());
                if (geneItem.getSeedId() != 0)
                    buf.append("|seed|").append(geneItem.getSeedId());
                if (geneItem.getInterproId() != 0)
                    buf.append("|ipr|").append(geneItem.getInterproId());
                if (buf.length() > 0) {
                    String header = Basic.toString(referenceHeader);
                    String remainder;
                    int len = header.indexOf(' ');
                    if (len < header.length()) {
                        remainder = header.substring(len); // keep space...
                        header = header.substring(0, len);
                    } else
                        remainder = "";

                    return (header + (header.endsWith("|") ? "" : "|") + "pos|"
                            + (geneItem.isReverse() ? refInterval.getEnd() + ".." + refInterval.getStart()
                            : refInterval.getStart() + ".." + refInterval.getEnd())
                            + buf.toString() + remainder).getBytes();
                }
            }
        }
        return referenceHeader;
    }

    final static private Comparator<ReadMatchItem>
            unweightedComparator = new Comparator<ReadMatchItem>() {
        public int compare(ReadMatchItem a, ReadMatchItem b) {
            return Float.compare(b.score, a.score);
        }
    };

    public int size() {
        return size;
    }

    /**
     * dump gene table to standard out
     *
     * @param args
     */
    public static void main(String[] args) throws IOException, UsageException, CanceledException {
        args = new String[]{"-i", "/Users/huson/data/malt/genes2/index/annotation.idx"};

        final ArgsOptions options = new ArgsOptions(args, null, "GeneTableDump", "Dump gene table");
        final String idxFile = options.getOptionMandatory("i", "idxFile", "Input annotation.idx file", "index/annotation.idx");
        final String dbFile = options.getOption("d", "dbFile", "Input annotation.db file", Basic.replaceFileSuffix(idxFile, ".db"));
        final String outputFile = options.getOption("o", "output", "Output file (or stdout)", "stdout");
        options.done();

        final GeneTableAccess geneTableAccess = new GeneTableAccess(new File(idxFile), new File(dbFile));

        try (Writer w = new BufferedWriter(outputFile.equals("stdout") ? new OutputStreamWriter(System.out) : new FileWriter(outputFile))) {
            for (int idx = 0; idx < geneTableAccess.size(); idx++) {
                final IntervalTree<GeneItem> tree = geneTableAccess.getIntervals(idx);
                if (tree != null) {
                    if (true) {
                        System.err.println("Tree[" + idxFile + "]: " + Basic.abbreviateDotDotDot(tree.toString(), 1000));
                    } else {
                        w.write("RefIndex=" + idx + "\n");

                        for (Interval<GeneItem> interval : tree) {
                            w.write(interval.getStart() + " " + interval.getEnd() + ": " + interval.getData() + "\n");
                        }
                        w.write("----\n");
                    }
                }
            }
        }
    }
}
