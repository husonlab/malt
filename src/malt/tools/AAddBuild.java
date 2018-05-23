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

package malt.tools;

import jloda.util.*;
import malt.genes.CDS;
import malt.genes.GeneItem;
import megan.io.OutputWriter;
import megan.util.interval.Interval;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * build the aadd index
 * Daniel Huson, 5.2018
 */
public class AAddBuild {
    final static byte[] MAGIC_NUMBER_IDX = "AAddIdxV0.1.".getBytes();
    final static byte[] MAGIC_NUMBER_DBX = "AAddDbxV0.1.".getBytes();

    /**
     * add functional annotations to DNA alignments
     */
    public static void main(String[] args) {
        try {
            ProgramProperties.setProgramName("AAddBuild");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new AAddBuild()).run(args);
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
     */
    public void run(String[] args) throws CanceledException, IOException, UsageException {
        final ArgsOptions options = new ArgsOptions(args, this, "Build the index for AAdd");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2018 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input Output");
        final List<String> gffFiles = options.getOptionMandatory("-igff", "inputGFF", "Input GFF3 files or directory (.gz ok)", new LinkedList<String>());
        final String indexDirectory = options.getOptionMandatory("-d", "index", "Index directory", "");
        options.comment(ArgsOptions.OTHER);
        final boolean lookInside = options.getOption("-ex", "extraStrict", "When given an input directory, look inside every input file to check that it is indeed in GFF3 format", false);
        options.done();

        if (gffFiles.size() == 1) {
            final File file = new File(gffFiles.get(0));
            if (file.isDirectory()) {
                System.err.println("Collecting all GFF3 files in directory: " + file);
                gffFiles.clear();
                for (File aFile : Basic.getAllFilesInDirectory(file, new GFF3FileFilter(true, lookInside), true)) {
                    gffFiles.add(aFile.getPath());
                }
                if (gffFiles.size() == 0)
                    throw new IOException("No GFF files found in directory: " + file);
                else
                    System.err.println(String.format("Found: %,d", gffFiles.size()));
            }
        }


        // obtains the gene annotations:
        Map<String, ArrayList<Interval<GeneItem>>> dnaId2list = new HashMap<>();
        {
            final Collection<CDS> annotations = CDS.parseGFFforCDS(gffFiles, new ProgressPercentage("Processing GFF files"));

            try (ProgressListener progress = new ProgressPercentage("Building annotation list", annotations.size())) {
                for (CDS cds : annotations) {
                    ArrayList<Interval<GeneItem>> list = dnaId2list.get(cds.getDnaId());
                    if (list == null) {
                        list = new ArrayList<>();
                        dnaId2list.put(cds.getDnaId(), list);
                    }
                    final GeneItem geneItem = new GeneItem();
                    final String accession = cds.getProteinId();
                    geneItem.setProteinId(accession.getBytes());
                    geneItem.setReverse(cds.isReverse());
                    list.add(new Interval<>(cds.getStart(), cds.getEnd(), geneItem));
                    progress.incrementProgress();
                }
            }
        }

        // writes the index file:
        long totalRefWithAGene = 0;

        final File indexFile = new File(indexDirectory, "aadd.idx");
        final File dbFile = new File(indexDirectory, "aadd.dbx");
        try (OutputWriter idxWriter = new OutputWriter(indexFile); OutputWriter dbxWriter = new OutputWriter(dbFile);
             ProgressPercentage progress = new ProgressPercentage("Writing files: " + indexFile + "\n               " + dbFile, dnaId2list.size())) {
            idxWriter.write(MAGIC_NUMBER_IDX);
            dbxWriter.write(MAGIC_NUMBER_DBX);

            idxWriter.writeInt(dnaId2list.size());

            for (String dnaId : dnaId2list.keySet()) {
                idxWriter.writeString(dnaId);
                final ArrayList<Interval<GeneItem>> list = dnaId2list.get(dnaId);
                if (list == null) {
                    idxWriter.writeLong(0); // no intervals
                } else {
                    idxWriter.writeLong(dbxWriter.getPosition()); // position of intervals in DB file

                    dbxWriter.writeInt(list.size());
                    for (Interval<GeneItem> interval : Basic.randomize(list, 666)) { // need to save in random order
                        dbxWriter.writeInt(interval.getStart());
                        dbxWriter.writeInt(interval.getEnd());
                        interval.getData().write(dbxWriter);
                    }
                    totalRefWithAGene++;
                }
                progress.incrementProgress();
            }
        }

        System.err.println(String.format("Reference sequences with at least one annotation: %,d of %,d", totalRefWithAGene, dnaId2list.size()));
    }
}
