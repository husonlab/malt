package malt.genes;

import jloda.util.*;
import malt.analysis.QueryItem;
import malt.analysis.ReadMatchItem;
import malt.data.ReadMatch;
import net.sf.picard.util.IntervalTree;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
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
                IntervalTree<GeneItem> intervals = new IntervalTree<GeneItem>();
                for (int i = 0; i < intervalsLength; i++) {
                    int start = ins.readInt();
                    int end = ins.readInt();
                    GeneItem geneItem = new GeneItem();
                    geneItem.read(ins);
                    intervals.put(start, end, geneItem);
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
                IntervalTree<GeneItem> intervals = refIndex2IntervalsTable[match.refIndex];
                if (intervals != null) {
                    for (Iterator<IntervalTree.Node<GeneItem>> it = intervals.iterator(match.refStart, match.refEnd); it.hasNext(); ) {
                        IntervalTree.Node<GeneItem> node = it.next();
                        genes[numberOfGenes++] = node.getValue();
                        if (numberOfGenes == genes.length)
                            break loop;
                    }
                }
            }
        }
        return numberOfGenes;
    }

    /**
     * gets the KEGG id
     *
     * @param numberOfMatches
     * @param readMatches
     * @return kegg id or 0
     */
    public int getKegg(final int numberOfMatches, final ReadMatch[] readMatches) {
        if (numberOfMatches > 0) {
            final GeneItem[] genes = new GeneItem[100];
            final QueryItem queryItem = new QueryItem(null, numberOfMatches, readMatches);
            int numberOfGenes = getGenes(null, queryItem.getReadMatchItems(), genes);
            for (int i = 0; i < numberOfGenes; i++) {
                if (genes[i].getKeggId() != null)
                    return Basic.parseInt(Basic.toString(genes[i].getKeggId()));
            }
        }
        return 0;
    }

    /**
     * dump gene table to standard out
     *
     * @param args
     */
    public static void main(String[] args) throws IOException, UsageException, CanceledException {
        args = new String[]{"-i", "/Users/huson/data/ma/index/gene-table.idx"};

        final ArgsOptions options = new ArgsOptions(args, null, "GeneTableDump", "Dump gene table");
        final String inputFile = options.getOptionMandatory("i", "input", "Gene table file", "index/gene-table.idx");
        final String outputFile = options.getOption("o", "output", "Output file", Basic.replaceFileSuffix(inputFile, ".txt"));
        options.done();

        final GeneTableAccess geneTableAccess = new GeneTableAccess(new File(inputFile));

        Writer w = new BufferedWriter(new FileWriter(outputFile));

        for (int i = 0; i < geneTableAccess.refIndex2IntervalsTable.length; i++) {
            final IntervalTree<GeneItem> tree = geneTableAccess.refIndex2IntervalsTable[i];
            if (tree != null) {
                w.write("RefIndex=" + i + "\n");
                for (IntervalTree.Node<GeneItem> gene : tree) {
                    final GeneItem geneItem = gene.getValue();
                    w.write(geneItem.toString() + "\n");
                }
                w.write("----\n");
            }
        }
        w.close();
    }
}
