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
    private final IntervalTree<GeneItem>[] refIndex2IntervalsTable;

    final static private Comparator<ReadMatchItem>
            unweightedComparator = new Comparator<ReadMatchItem>() {
        public int compare(ReadMatchItem a, ReadMatchItem b) {
            if (a.score > b.score)
                return -1;
            else if (a.score < b.score)
                return 1;
            else
                return 0;
        }
    };


    /**
     * construct the gene table from the gene-table index file
     *
     * @param inputFile
     * @throws IOException
     */
    public GeneTableAccess(File inputFile) throws IOException {

        DataInputStream ins = new DataInputStream(new BufferedInputStream(new FileInputStream(inputFile)));

        Basic.readAndVerifyMagicNumber(ins, GeneTableBuilder.MAGIC_NUMBER);

        int tableLength = ins.readInt();
        ProgressPercentage progress = new ProgressPercentage("Reading file: " + inputFile, tableLength);

        long numberOfGeneLocations = 0;
        refIndex2IntervalsTable = new IntervalTree[tableLength];

        for (int refIndex = 0; refIndex < tableLength; refIndex++) {
            int intervalsLength = ins.readInt();
            if (intervalsLength > 0) {
                IntervalTree<GeneItem> intervals = new IntervalTree<>();
                for (int i = 0; i < intervalsLength; i++) {
                    int start = ins.readInt();
                    int end = ins.readInt();
                    GeneItem geneItem = new GeneItem();
                    geneItem.read(ins);
                    intervals.add(start, end, geneItem);
                    //System.err.println(refIndex+"("+start+"-"+end+") -> "+geneItem);
                    numberOfGeneLocations++;
                }
                refIndex2IntervalsTable[refIndex] = intervals;
            }
            progress.incrementProgress();
        }
        progress.close();
        System.err.println("Number of gene locations: " + numberOfGeneLocations);
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
                            if (a.score > b.score)
                                return -1;
                            else if (a.score < b.score)
                                return 1;
                            else
                                return 0;
                        }
                    } else if (bWeight != null)
                        return 1;
                    else {       // both references have zero weight
                        if (a.score > b.score)
                            return -1;
                        else if (a.score < b.score)
                            return 1;
                        else
                            return 0;
                    }
                }
            });
        } else {
            Arrays.sort(sorted, unweightedComparator);
        }

        int numberOfGenes = 0;
        loop:
        for (ReadMatchItem match : sorted) {
            if (match.refIndex < refIndex2IntervalsTable.length) {
                final IntervalTree<GeneItem> intervals = refIndex2IntervalsTable[match.refIndex];
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
     * @param referenceHeader
     * @param refIndex
     * @param startReference
     * @param endReference
     * @return annotations
     */
    public byte[] addAnnotationString(byte[] referenceHeader, Integer refIndex, int startReference, int endReference) {
        final IntervalTree<GeneItem> tree = refIndex2IntervalsTable[refIndex];

        int kegg = 0;
        int cog = 0;
        int seed = 0;
        int interpro = 0;
        String proteinId = null;

        if (tree != null) {
            // note that we use negative coordinates in the interval tree to start genes on the opposite strand
            final Interval<GeneItem> alignmentInterval = new Interval<>(startReference < endReference ? startReference : -startReference, startReference < endReference ? endReference : -endReference, null);
            for (Interval<GeneItem> interval : tree.getIntervalsSortedByDecreasingIntersectionLength(alignmentInterval.getStart(), alignmentInterval.getEnd())) {
                if (alignmentInterval.intersectionLength(interval.getStart(), interval.getEnd()) < 0.9 * alignmentInterval.length())
                    break; // require at least 90% of the alignment to be covered by the gene

                final GeneItem geneItem = interval.getData();
                if (kegg == 0)
                    kegg = geneItem.getKeggId();
                if (cog == 0)
                    cog = geneItem.getCogId();
                if (seed == 0)
                    seed = geneItem.getSeedId();
                if (interpro == 0)
                    interpro = geneItem.getInterproId();
                if (proteinId == null && geneItem.getProteinId() != null)
                    proteinId = Basic.toString(geneItem.getProteinId());
            }
            final StringBuilder buf = new StringBuilder();
            if (proteinId != null)
                buf.append("|ref|").append(proteinId);
            if (kegg != 0)
                buf.append("|kegg|").append(kegg);
            if (cog != 0)
                buf.append("|cog|").append(cog);
            if (seed != 0)
                buf.append("|seed|").append(seed);
            if (interpro != 0)
                buf.append("|ipr|").append(interpro);
            if (buf.length() > 0) {
                String header = Basic.toString(referenceHeader);
                String remainder;
                int len = header.indexOf(' ');
                if (len < header.length()) {
                    remainder = header.substring(len); // keep space...
                    header = header.substring(0, len);
                } else
                    remainder = "";
                return (header + (header.endsWith("|") ? "" : "|") + "pos|" + startReference + ".." + endReference + buf.toString() + remainder).getBytes();
            }

        }
        return referenceHeader;
    }


    /**
     * dump gene table to standard out
     *
     * @param args
     */
    public static void main(String[] args) throws IOException, UsageException, CanceledException {
        args = new String[]{"-i", "/Users/huson/data/malt/genes/index/gene-table.idx"};

        final ArgsOptions options = new ArgsOptions(args, null, "GeneTableDump", "Dump gene table");
        final String inputFile = options.getOptionMandatory("i", "input", "Gene table file", "index/gene-table.idx");
        final String outputFile = options.getOption("o", "output", "Output file (or stdout)", "stdout");
        options.done();

        final GeneTableAccess geneTableAccess = new GeneTableAccess(new File(inputFile));

        try (Writer w = new BufferedWriter(outputFile.equals("stdout") ? new OutputStreamWriter(System.out) : new FileWriter(outputFile))) {
            for (int i = 0; i < geneTableAccess.refIndex2IntervalsTable.length; i++) {
                final IntervalTree<GeneItem> tree = geneTableAccess.refIndex2IntervalsTable[i];
                if (tree != null) {
                    w.write("RefIndex=" + i + "\n");

                    for (Interval<GeneItem> interval : tree) {
                        w.write(interval.getStart() + " " + interval.getEnd() + ": " + interval.getData() + "\n");
                    }

                    w.write("----\n");
                }
            }
        }
    }
}
