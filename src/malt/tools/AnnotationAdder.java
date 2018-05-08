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
import malt.MaltOptions;
import malt.data.ReferencesDBAccess;
import malt.genes.GeneTableAccess;
import megan.parsers.blast.BlastMode;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * add functional annotations to DNA alignments
 * Daniel Huson, 5.2018
 */
public class AnnotationAdder {
    /**
     * add functional annotations to DNA alignments
     */
    public static void main(String[] args) {
        try {
            ProgramProperties.setProgramName("AnnotationAdder");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new AnnotationAdder()).run(args);
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
        final ArgsOptions options = new ArgsOptions(args, this, "Adds functional annotations to DNA alignments");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2018 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input Output");
        final String[] inputFiles = options.getOptionMandatory("-i", "input", "Input SAM file(s) (.gz ok)", new String[0]);
        final String indexDirectory = options.getOptionMandatory("-d", "index", "MALT index directory", "");
        final String[] outputFiles = options.getOptionMandatory("-o", "output", "Output file(s) (.gz ok) or directory", new String[0]);
        options.comment(ArgsOptions.OTHER);
        final BlastMode blastMode = Basic.valueOfIgnoreCase(BlastMode.class, options.getOption("-bm", "blastMode", "The blast mode", BlastMode.values(), BlastMode.BlastN.toString()));
        final boolean saveMap = options.getOption("-sim", "saveMap", "Save additional map to MALT index to speedup startup", true);
        options.done();

        final File outputDir;
        if (outputFiles.length == 1 && ((new File(outputFiles[0])).isDirectory())) {
            outputDir = new File(outputFiles[0]);
        } else {
            outputDir = null;
            if (inputFiles.length != outputFiles.length)
                throw new UsageException("Number of output files doesn't match number of input files");
        }

        final Map<String, Integer> ref2index = new HashMap<>(10000000);

        final File ref2IndexFile = new File(indexDirectory, "ref2index.map.gz");
        if (ref2IndexFile.exists()) {
            try (FileInputIterator it = new FileInputIterator(ref2IndexFile, true)) {
                while (it.hasNext()) {
                    final String[] tokens = Basic.split(it.next(), '\t');
                    if (tokens.length == 2)
                        ref2index.put(tokens[0], Basic.parseInt(tokens[1]));
                }
            }
        } else {
            try (ReferencesDBAccess referencesDB = new ReferencesDBAccess(MaltOptions.MemoryMode.map, new File(indexDirectory, "ref.idx"), new File(indexDirectory, "ref.db"), new File(indexDirectory, "ref.inf"));
                 ProgressListener progress = new ProgressPercentage(referencesDB.getNumberOfSequences())) {

                System.err.println("Loading headers");
                for (int i = 0; i < referencesDB.getNumberOfSequences(); i++) {
                    String ref = Basic.swallowLeadingGreaterSign(Basic.toString(referencesDB.getHeader(i)));
                    if (ref.contains("."))
                        ref = ref.substring(0, ref.indexOf('.'));
                    ref2index.put(ref, i);
                    progress.incrementProgress();
                }
            }
            if (saveMap) {
                try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(ref2IndexFile))));
                     ProgressListener progressListener = new ProgressPercentage("Writing file: " + ref2IndexFile, ref2index.size())) {

                    for (Map.Entry<String, Integer> entry : ref2index.entrySet()) {
                        w.write(String.format("%s\t%d\n", entry.getKey(), entry.getValue()));
                        progressListener.incrementProgress();
                    }
                }
            }
        }
        final GeneTableAccess geneTableAccess = new GeneTableAccess(new File(indexDirectory, "annotation.idx"), new File(indexDirectory, "annotation.db"));
        System.err.println("Annotations: " + geneTableAccess.size());

        for (int i = 0; i < inputFiles.length; i++) {
            File inputFile = new File(inputFiles[i]);
            final File outputFile;
            if (outputDir != null) {
                outputFile = new File(outputDir, inputFile.getName() + ".out");
            } else
                outputFile = new File(outputFiles[i]);
            if (inputFile.equals(outputFile))
                throw new IOException("Input file equals output file: " + inputFile);
            final boolean gzipOutput = outputFile.getName().toLowerCase().endsWith(".gz");

            try (final FileInputIterator it = new FileInputIterator(inputFile, true);
                 final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(gzipOutput ? new GZIPOutputStream(new FileOutputStream(outputFile)) : new FileOutputStream(outputFile)))) {
                System.err.println("Writing file: " + outputFile);

                long countLines = 0;
                long countAlignments = 0;
                long countAnnotated = 0;

                while (it.hasNext()) {
                    final String aLine = it.next();
                    if (aLine.startsWith("@"))
                        w.write(aLine);
                    else {

                        final String[] tokens = Basic.split(aLine, '\t');

                        if (tokens.length < 2 || tokens[2].equals("*")) {
                            w.write(aLine);
                        } else {
                            final Integer refIndex;
                            {
                                final int pos = tokens[2].indexOf(".");
                                final String ref = (pos > 0 ? tokens[2].substring(0, pos) : tokens[2]);
                                refIndex = ref2index.get(ref);
                                if (refIndex == null) {
                                    System.err.println("Ref not found: " + ref);
                                    continue;
                                }
                            }

                            final int startSubject = Basic.parseInt(tokens[3]);
                            final int endSubject = startSubject + getRefLength(tokens[5]) - 1;

                            final String annotatedRef = geneTableAccess.annotateRefString(tokens[2], refIndex, startSubject, endSubject);
                            for (int t = 0; t < tokens.length; t++) {
                                if (t > 0)
                                    w.write('\t');
                                if (t == 2 && !annotatedRef.equals(tokens[2])) {
                                    w.write(annotatedRef);
                                    countAnnotated++;
                                } else
                                    w.write(tokens[t]);
                            }
                        }
                        countAlignments++;
                    }
                    w.write("\n");
                    countLines++;
                }
                System.err.println(String.format("Lines:     %,11d", countLines));
                System.err.println(String.format("Alignments:%,11d", countAlignments));
                System.err.println(String.format("Annotated: %,11d", countAnnotated));
            }
        }
    }

    private static Pattern pattern = Pattern.compile("[0-9]+[MDN]+");

    public static int getRefLength(String cigar) {
        final Matcher matcher = pattern.matcher(cigar);
        final ArrayList<String> pairs = new ArrayList<>();
        while (matcher.find())
            pairs.add(matcher.group());

        int length = 0;
        for (String p : pairs) {
            int num = Integer.parseInt(p.substring(0, p.length() - 1));
            length += num;
        }
        return length;

    }
}
