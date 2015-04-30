package malt.amplicon;

import jloda.util.Pair;
import megan.parsers.iterators.FastAFileIterator;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;


/**
 * A class that merges several input fasta files in that way that sequences are dereplicated.
 * <p/>
 * The output is a single fastaIterator with unique sequences.
 * <p/>
 * The readname of each of the reads in that fasta file can then be used to link back to the initial fasta files and position in their file.
 *
 * @author ruscheweyh
 */
public class MergedFastaIterator {
    Map<String, Pair<String, Long>> readname2filenameAndPosition = new HashMap<String, Pair<String, Long>>();
    Map<String, List<String>> sequencesToReadNames = new HashMap<String, List<String>>();
    Map<String, String> clusterId2Sequence = new HashMap<String, String>();
    int numberOfSeqs = 0;

    /**
     * The input fasta files are dereplicated.
     *
     * @param input fasta files
     * @throws IOException
     */
    public MergedFastaIterator(String[] infasta) throws IOException {
        int clusterid = 0;
        for (String fastafile : infasta) {
            long pos = 0;
            FastAFileIterator it = new FastAFileIterator(fastafile);
            while (it.hasNext()) {
                Pair<String, String> next = it.next();
                if (!sequencesToReadNames.containsKey(next.get2())) {
                    sequencesToReadNames.put(next.get2(), new ArrayList<String>());
                    clusterId2Sequence.put(">cluster_" + clusterid, next.get2());
                    clusterid++;
                }
                sequencesToReadNames.get(next.get2()).add(next.get1());
                readname2filenameAndPosition.put(next.get1(), new Pair<String, Long>(fastafile, pos));
                pos++;
                numberOfSeqs++;
            }
            it.close();
        }
    }

    /**
     * One can access the dereplicated sequences with an getLetterCodeIterator.
     *
     * @return An getLetterCodeIterator over dereplicated sequences. Pair<READNAME, SEQUENCE>
     */
    public Iterator<Pair<String, String>> getReadsIterator() {
        List<Pair<String, String>> list = new ArrayList<Pair<String, String>>();
        for (Entry<String, String> entry : clusterId2Sequence.entrySet()) {
            list.add(new Pair<String, String>(entry.getKey(), entry.getValue()));
        }
        return list.iterator();
    }

    /**
     * Get for one clusterId all readnames and associated fileNames and Positions in the initial fasta file
     *
     * @param The clusterId
     * @return Map<ReadName, Pair<FileName, PositionInFile>>
     */
    public Map<String, Pair<String, Long>> dereplicateCluster(String readName) {
        String sequence = clusterId2Sequence.get(readName);
        if (sequence == null && !readName.startsWith(">")) {
            sequence = clusterId2Sequence.get(">" + readName);
        }
        if (sequence == null) {
            throw new NullPointerException("Cluster: " + readName + " not found");
        }
        List<String> readNames = sequencesToReadNames.get(sequence);
        Map<String, Pair<String, Long>> readname2filenameAndPosition2 = new HashMap<String, Pair<String, Long>>();
        for (String readName2 : readNames) {
            readname2filenameAndPosition2.put(readName2, readname2filenameAndPosition.get(readName2));
        }
        return readname2filenameAndPosition2;
    }


    public static void main(String[] args) throws IOException {

        String folder = "/Users/ruscheweyh/Desktop/MeganFor16S_New/betadiv/malt/";
        String[] files = new File(folder).list(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                if (name.endsWith("fasta")) {
                    return true;
                }
                return false;
            }
        });
        for (int i = 0; i < files.length; i++) {
            files[i] = folder + files[i];
        }


        MergedFastaIterator mit = new MergedFastaIterator(files);
        Iterator<Pair<String, String>> it = mit.getReadsIterator();
        int count = 0;
        System.out.println(mit.numberOfSeqs);
        while (it.hasNext()) {
            String clusterName = it.next().get1();
            System.out.println(mit.dereplicateCluster(clusterName));
            count++;
        }


        System.out.println(count);


    }


}
