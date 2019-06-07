/*
 *  DumpReferencesFromMaltIndex.java Copyright (C) 2019. Daniel H. Huson GPL
 *
 *   (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package malt.tools;

import jloda.swing.util.ArgsOptions;
import jloda.util.*;
import malt.MaltOptions;
import malt.data.ReferencesDBAccess;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DumpReferencesFromMaltIndex {
    /**
     * dump references from malt index
     */
    public static void main(String[] args) {
        try {
            ProgramProperties.setProgramName("DumpReferencesFromMaltIndex");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new DumpReferencesFromMaltIndex()).run(args);
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
        final ArgsOptions options = new ArgsOptions(args, this, "Dump references from MALT index");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2019 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input Output");
        final String indexDirectory = options.getOptionMandatory("-d", "index", "Index directory", "");
        final String outputFile = options.getOptionMandatory("-o", "output", "Output file (.gz ok)", "");
        options.comment(ArgsOptions.OTHER);
        final boolean headersOnly = options.getOption("-ho", "headersOnly", "Only save headers", false);
        options.done();

        System.err.println("Loading references from: " + indexDirectory);
        final ReferencesDBAccess referencesDB = new ReferencesDBAccess(MaltOptions.MemoryMode.load, new File(indexDirectory, "ref.idx"), new File(indexDirectory, "ref.db"), new File(indexDirectory, "ref.inf"));
        int count = 0;
        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile)); ProgressPercentage progress = new ProgressPercentage("Writing file: " + outputFile, referencesDB.getNumberOfSequences())) {
            for (int i = 0; i < referencesDB.getNumberOfSequences(); i++) {
                w.write(Basic.toString(referencesDB.getHeader(i)) + "\n");
                if (!headersOnly) w.write(Basic.toString(referencesDB.getSequence(i)) + "\n");
                progress.incrementProgress();
                count++;
            }
        }
        System.err.println(String.format("Lines: %,d", count));
    }
}
