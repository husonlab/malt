/**
 * MaltBuild.java 
 * Copyright (C) 2015 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package malt;

import jloda.util.*;
import malt.data.*;
import malt.genes.GeneTableBuilder;
import malt.mapping.Mapping;
import malt.util.Utilities;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.IdParser;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * build MALT index
 * Daniel Huson, 8.2014
 */
public class MaltBuild {
    /**
     * run the program
     *
     * @param args
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     */
    public static void main(String[] args) {
        try {
            PeakMemoryUsageMonitor.start();
            final MaltBuild maltBuild = new MaltBuild();
            ResourceManager.setWarningMissingIcon(false);
            ProgramProperties.setProgramIcon(ResourceManager.getIcon("malt-build48.png"));
            ProgramProperties.setProgramName("MaltBuild");
            ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

            maltBuild.run(args);

            System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
            System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
            if (!ArgsOptions.hasMessageWindow())
                System.exit(0);
            else
                System.err.println("DONE - close window to quit");
        } catch (Exception ex) {
            if (ex.getMessage() == null || !ex.getMessage().startsWith("Help"))
                Basic.caught(ex);
            if (!ArgsOptions.hasMessageWindow())
                System.exit(1);
            else
                System.err.println("DONE - close window to quit");
        }
    }

    /**
     * run the program
     *
     * @param args
     * @throws UsageException
     * @throws IOException
     */
    public void run(String[] args) throws Exception {
// parse commandline options:
        final ArgsOptions options = new ArgsOptions(args, this, "Build an index for MALT (MEGAN alignment tool)");
        options.setAuthors("Daniel H. Huson");
        options.setVersion(malt.Version.SHORT_DESCRIPTION);
        options.setLicense("Copyright (C) 2016 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");

        options.comment("Input:");
        final List<String> inputFiles = options.getOptionMandatory("i", "input", "Input reference file(s)", new LinkedList<String>());
        final SequenceType sequenceType = SequenceType.valueOfIgnoreCase(options.getOptionMandatory("s", "sequenceType", "Sequence type", SequenceType.values(), SequenceType.Protein.toString()));

        options.comment("Output:");
        final String indexDirectoryName = options.getOptionMandatory("d", "index", "Name of index directory", "");

        options.comment("Performance:");
        final int numberOfThreads = options.getOption("t", "threads", "Number of worker threads", Runtime.getRuntime().availableProcessors());
        final int stepSize = options.getOption("st", "step", "Step size used to advance seed, values greater than 1 reduce index size and sensitivity", 1, 1, 100);

        options.comment("Seed:");
        String[] shapes = options.getOption("ss", "shapes", "Seed shape(s)", new String[]{"default"});
        int maxHitsPerSeed = options.getOption("mh", "maxHitsPerSeed", "Maximum number of hits per seed", 1000);
        final String proteinReduction;
        if (sequenceType == SequenceType.Protein || options.isDoHelp())
            proteinReduction = options.getOption("-pr", "proteinReduct", "Name or definition of protein alphabet reduction ("
                    + Basic.toString(malt.data.ReducedAlphabet.reductions.keySet(), ",") + ")", "DIAMOND_11");
        else
            proteinReduction = "";

        final String[] availableFNames = ClassificationManager.getAllSupportedClassifications().toArray(new String[ClassificationManager.getAllSupportedClassifications().size()]);
        options.comment("Classification:");
        String[] cNames = options.getOptionMandatory("-c", "classify", "Classifications (any of " + Basic.toString(availableFNames, " ") + ")", new String[]{Classification.Taxonomy});
        for (String cName : cNames) {
            if (!ClassificationManager.getAllSupportedClassifications().contains(cName))
                throw new UsageException("--classify: Unknown classification: " + cName);
        }

        if (options.isDoHelp())
            cNames = availableFNames;

        final boolean parseTaxonNames = true;

        final String[] gi2FNames = new String[cNames.length];
        final String[] ref2FNames = new String[cNames.length];
        final String[] synonyms2FNames = new String[cNames.length];

        for (int i1 = 0; i1 < cNames.length; i1++) {
            String cName = cNames[i1];
            gi2FNames[i1] = options.getOption("-g2" + cName.toLowerCase(), "gi2" + cName.toLowerCase(), "GI-to-" + cName + " mapping file", "");
            ref2FNames[i1] = options.getOption("-r2" + cName.toLowerCase(), "ref2" + cName.toLowerCase(), "RefSeq-to-" + cName + " mapping file", "");
            synonyms2FNames[i1] = options.getOption("-s2" + cName.toLowerCase(), "syn2" + cName.toLowerCase(), "Synonyms-to-" + cName + " mapping file", "");

            if (cName.equalsIgnoreCase(Classification.Taxonomy))
                options.getOption("-tn", "parseTaxonNames", "Parse taxon names", true);
        }

        final String geneTableFile = options.getOption("-gif", "-geneInfoFile", "File containing gene information", "");

        options.comment(ArgsOptions.OTHER);
        final boolean saveFirstWordOfReferenceHeaderOnly = options.getOption("-fwo", "firstWordOnly", "Save only first word of reference header", false);
        final int randomSeed = options.getOption("rns", "random", "Random number generator seed", 666);
        final float hashTableLoadFactor = options.getOption("hsf", "hashScaleFactor", "Hash table scale factor", 0.9f, 0.1f, 1.0f);
        //final boolean buildTableInMemory = options.getOption("btm", "buildTableInMemory", "Build the hash table in memory and then save (more memory, much faster)", true);
        final boolean buildTableInMemory = true; // don't make this an option because it is really slow...
        final boolean doBuildTables = !options.getOption("!xX", "xSkipTable", "Don't recompute index and tables, just compute profile support", false);

        options.done();
        Basic.setDebugMode(options.isVerbose());

        if (sequenceType == null)
            throw new IOException("Sequence type undefined");

        System.err.println("Reference sequence type set to: " + sequenceType.toString());
        final IAlphabet referenceAlphabet;
        final IAlphabet seedAlphabet;

        switch (sequenceType) {
            case DNA:
                if (shapes[0].equalsIgnoreCase("default")) {
                    shapes = new String[]{SeedShape.SINGLE_DNA_SEED};
                }
                referenceAlphabet = DNA5.getInstance();
                seedAlphabet = DNA5.getInstance();
                break;
            case Protein:
                if (shapes[0].equalsIgnoreCase("default")) {
                    shapes = SeedShape.PROTEIN_SEEDS;
                }
                referenceAlphabet = ProteinAlphabet.getInstance();
                seedAlphabet = new ReducedAlphabet(proteinReduction);
                break;
            default:
                throw new UsageException("Undefined sequence type: " + sequenceType);
        }
        System.err.println("Seed shape(s): " + Basic.toString(shapes, ", "));

        final File indexDirectory = new File(indexDirectoryName);
        if (doBuildTables) {
            if (indexDirectory.exists()) {
                Utilities.cleanIndexDirectory(indexDirectory);
            } else {
                if (!indexDirectory.mkdir())
                    throw new IOException("mkdir failed: " + indexDirectoryName);
            }
        } else
            System.err.println("NOT BUILDING INDEX OR TABLES");

        final File referenceFile = new File(indexDirectory, "ref.idx");
        if ((!referenceFile.exists() || referenceFile.delete()) && !referenceFile.createNewFile())
            throw new IOException("Can't create file: " + referenceFile);

        ReferencesHashTableBuilder.checkCanWriteFiles(indexDirectoryName, 0);

        // load the reference file:
        final ReferencesDBBuilder referencesDB = new ReferencesDBBuilder();
        referencesDB.loadFastAFiles(inputFiles, referenceAlphabet);
        System.err.println(String.format("Number of sequences:%12d", referencesDB.getNumberOfSequences()));
        System.err.println(String.format("Number of letters:  %12d", referencesDB.getNumberOfLetters()));

        // generate hash table for each seed shape
        if (doBuildTables) {
            for (int tableNumber = 0; tableNumber < shapes.length; tableNumber++) {
                final String shape = shapes[tableNumber];
                final SeedShape seedShape = new SeedShape(seedAlphabet, shape);
                System.err.println("BUILDING table (" + tableNumber + ")...");
                final ReferencesHashTableBuilder hashTable = new ReferencesHashTableBuilder(sequenceType, seedAlphabet, seedShape,
                        referencesDB.getNumberOfSequences(), referencesDB.getNumberOfLetters(), randomSeed, maxHitsPerSeed, hashTableLoadFactor, stepSize);
                hashTable.buildTable(new File(indexDirectory, "table" + tableNumber + ".idx"), new File(indexDirectory, "table" + tableNumber + ".db"), referencesDB, numberOfThreads, buildTableInMemory);
                hashTable.saveIndexFile(new File(indexDirectory, "index" + tableNumber + ".idx"));
            }
        }

        // setup classification support
        for (int i = 0; i < cNames.length; i++) {
            final String cName = cNames[i];
            final String cNameLowerCase = cName.toLowerCase();
            final String sourceName = (cName.equals(Classification.Taxonomy) ? "ncbi" : cNameLowerCase);

            ClassificationManager.ensureTreeIsLoaded(cName);
            Basic.writeStreamToFile(ResourceManager.getFileAsStream(sourceName + ".tre"), new File(indexDirectory, cNameLowerCase + ".tre"));
            Basic.writeStreamToFile(ResourceManager.getFileAsStream(sourceName + ".map"), new File(indexDirectory, cNameLowerCase + ".map"));

            Utilities.loadMapping(synonyms2FNames[i], IdMapper.MapType.Synonyms, cName);
            Utilities.loadMapping(ref2FNames[i], IdMapper.MapType.RefSeq, cName);
            Utilities.loadMapping(gi2FNames[i], IdMapper.MapType.GI, cName);

            final IdParser idParser = ClassificationManager.get(cName, true).getIdMapper().createIdParser();
            if (cName.equals(Classification.Taxonomy))
                idParser.setUseTextParsing(parseTaxonNames);
            final Mapping mapping = Mapping.create(cName, referencesDB, idParser, new ProgressPercentage("Building " + cName + "-mapping..."));
            mapping.save(new File(indexDirectory, cNameLowerCase + ".idx"));
        }

        if (doBuildTables) // don't write until after running classification mappers, as they add tags to reference sequences
            referencesDB.save(new File(indexDirectory, "ref.idx"), new File(indexDirectory, "ref.db"), new File(indexDirectory, "ref.inf"), saveFirstWordOfReferenceHeaderOnly);

        if (geneTableFile.length() > 0) {
            GeneTableBuilder geneTableBuilder = new GeneTableBuilder();
            geneTableBuilder.buildAndSaveGeneTable(referencesDB, geneTableFile, new File(indexDirectory, "gene-table.idx"), numberOfThreads);
        }
    }
}
