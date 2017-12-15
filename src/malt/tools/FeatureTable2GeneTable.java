package malt.tools;

import jloda.util.*;
import malt.genes.CDS;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class FeatureTable2GeneTable {
    /**
     * convert feature tables to gene table
     *
     * @param args
     * @throws UsageException
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws Exception {
        try {
            ProgramProperties.setProgramName("FeatureTable2GeneTable");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new FeatureTable2GeneTable()).run(args);
            System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
            System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
            System.exit(0);
        } catch (Exception ex) {
            Basic.caught(ex);
            System.exit(1);
        }
    }

    /**
     * run the program
     *
     * @param args
     */
    public void run(String[] args) throws CanceledException, IOException, UsageException {
        final ArgsOptions options = new ArgsOptions(args, this, "Creates a gene table from a list of feature tables");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2017 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        final List<String> inputFiles = options.getOptionMandatory("-i", "input", "Feature tables obtained from NCBI (.gz ok)", new LinkedList<String>());
        final String outputFile = options.getOption("-o", "output", "Output file (.gz ok, use 'stdout' for standard out)", "stdout");
        options.done();

        final Collection<CDS> list = CDS.parseGFFforCDS(inputFiles, new ProgressPercentage());
        System.err.println("Writing to file: " + outputFile);
        int count = 0;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile)); ProgressPercentage progress = new ProgressPercentage(list.size())) {
            for (CDS CDS : list) {
                w.write(String.format("%s\t%d\t%d\t%s\n", CDS.getDnaAccession(),
                        CDS.isReverse() ? CDS.getEnd() : CDS.getStart(), CDS.isReverse() ? CDS.getStart() : CDS.getEnd(),
                        CDS.getProteinAccession()));
                progress.incrementProgress();
                count++;
            }
        }
        System.err.println(String.format("Lines: %,d", count));
    }
}
