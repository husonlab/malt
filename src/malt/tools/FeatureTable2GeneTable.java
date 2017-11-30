package malt.tools;

import jloda.util.*;
import megan.classification.util.TaggedValueIterator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

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

        final String[] inputFiles = options.getOptionMandatory("-i", "input", "Feature tables obtained from NCBI (.gz ok)", new String[0]);
        final String outputFile = options.getOption("-o", "output", "Output file (.gz ok, use 'stdout' for standard out)", "stdout");
        options.done();

        final TaggedValueIterator vit = new TaggedValueIterator(false, true, "ref|");

        int count = 0;

        System.err.println("Writing to file: " + outputFile);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile)); ProgressPercentage progress = new ProgressPercentage(100 * inputFiles.length)) {
            for (String fileName : inputFiles) {
                try (FileInputIterator it = new FileInputIterator(fileName)) {
                    if (it.hasNext()) {
                        {
                            final String aLine = it.next();
                            vit.restart(aLine);
                            if (!vit.hasNext())
                                throw new IOException("Can't find reference accession in file: '" + fileName + "', header line: '" + aLine + "'");
                        }
                        final String refAccession = vit.next();
                        if (it.hasNext()) {
                            String aLine = it.next();
                            if (aLine.length() > 0 && Character.isDigit(aLine.charAt(0))) { // coordinates
                                while (it.hasNext()) { // only makes sense to process if there are more lines to be read, even if aLine!=null
                                    final String[] tokens = Basic.split(aLine, '\t');
                                    if (tokens.length == 3 && tokens[2].endsWith("CDS")) {
                                        int a = Basic.parseInt(tokens[0]);
                                        int b = Basic.parseInt(tokens[1]);
                                        String proteinId = null;
                                        String product = null;
                                        while (it.hasNext()) {
                                            aLine = it.next();
                                            if (aLine.length() > 0 && Character.isDigit(aLine.charAt(0)))
                                                break; // this is start of next feature
                                            else if (aLine.contains("protein_id")) {
                                                vit.restart(aLine);
                                                if (vit.hasNext()) {
                                                    proteinId = vit.next();
                                                }

                                            } else if (aLine.contains("product")) {
                                                product = aLine.replaceAll(".*\t", "").trim();
                                            }
                                        }
                                        if (proteinId != null) {
                                            w.write(String.format("%s\t%d %d\t%s\t%s\n", refAccession, a, b, proteinId, product));
                                            count++;
                                        }
                                    } else
                                        aLine = it.next();
                                    progress.setProgress(progress.getProgress() + 100 * it.getProgress() / it.getMaximumProgress());

                                }
                            }

                        }
                    }
                } catch (IOException ex) {
                    Basic.caught(ex);
                }
            }
        }
        System.err.println(String.format("Lines: %,d", count));
    }

}
