package malt;

import jloda.util.*;
import malt.data.*;
import malt.genes.GeneTableBuilder;
import malt.mapping.CogMapping;
import malt.mapping.KeggMapping;
import malt.mapping.SeedMapping;
import malt.mapping.TaxonMapping;
import malt.util.Utilities;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.IdParser;
import megan.mainviewer.data.TaxonomyData;

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
            long start = System.currentTimeMillis();
            final MaltBuild maltBuild = new MaltBuild();
            ProgramProperties.setProgramIcon(ResourceManager.getIcon("malt-build48.png"));
            ProgramProperties.setProgramName("MaltBuild");
            ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

            maltBuild.run(args);

            System.err.println("Total time: " + ((System.currentTimeMillis() - start) / 1000) + "s");
            System.err.println("Memory use: " + PeakMemoryUsageMonitor.getPeakUsageString());
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


        options.comment("Classification support:");

        final String gi2TaxaFile = options.getOption("-g2t", "gi2taxa", "GI-to-Taxonomy mapping file", "");
        final String refSeq2TaxaFile = options.getOption("-r2t", "ref2taxa", "RefSeq-to-Taxonomy mapping file", "");
        final String synonyms2TaxaFile = options.getOption("-s2t", "syn2taxa", "Synonyms-to-Taxonomy mapping file", "");

        final String gi2KeggFile = options.getOption("-g2k", "gi2kegg", "GI-to-KEGG mapping file", "");
        final String refSeq2KeggFile = options.getOption("-r2k", "ref2kegg", "RefSeq-to-KEGG mapping file", "");
        final String synonyms2KeggFile = options.getOption("-s2k", "syn2kegg", "Synonyms-to-KEGG mapping file", "");

        final String gi2SeedFile = options.getOption("-g2s", "gi2seed", "GI-to-SEED mapping file", "");
        final String refSeq2SeedFile = options.getOption("-r2s", "ref2seed", "RefSeq-to-SEED mapping file", "");
        final String synonyms2SeedFile = options.getOption("-s2s", "syn2seed", "Synonyms-to-SEED mapping file", "");

        final String gi2CogFile = options.getOption("-g2c", "gi2cog", "GI-to-COG mapping file", "");
        final String refSeq2CogFile = options.getOption("-r2c", "ref2cog", "RefSeq-to-COG mapping file", "");
        final String synonyms2CogFile = options.getOption("-s2c", "syn2cog", "Synonyms-to-COG mapping file", "");

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

        // terminate if no valid license:
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

        // build classification index files, if requested
        if (malt.util.Utilities.hasAMapping(synonyms2TaxaFile, refSeq2TaxaFile, gi2TaxaFile) || geneTableFile.length() > 0) {
            final File indexTreeFile = new File(indexDirectory, "taxonomy.tre");
            final File indexMapFile = new File(indexDirectory, "taxonomy.map");
            Basic.writeStreamToFile(ResourceManager.getFileAsStream("ncbi.tre"), indexTreeFile);
            Basic.writeStreamToFile(ResourceManager.getFileAsStream("ncbi.map"), indexMapFile);
            TaxonomyData.getName2IdMap().loadFromFile("ncbi.tre");
            TaxonomyData.getTree().loadFromFile("ncbi.map");

            malt.util.Utilities.loadMapping(synonyms2TaxaFile, IdMapper.MapType.Synonyms, Classification.Taxonomy);
            Utilities.loadMapping(refSeq2TaxaFile, IdMapper.MapType.RefSeq, Classification.Taxonomy);
            Utilities.loadMapping(gi2TaxaFile, IdMapper.MapType.GI, Classification.Taxonomy);

            final IdParser idParser = ClassificationManager.get(Classification.Taxonomy).getIdMapper().createIdParser();
            final TaxonMapping taxonMapping = TaxonMapping.create(referencesDB, idParser, new ProgressPercentage("Building taxon-mapping..."));
            taxonMapping.save(new File(indexDirectory, "taxonomy.idx"));
        }
        if (malt.util.Utilities.hasAMapping(synonyms2KeggFile, refSeq2KeggFile, gi2KeggFile)) {
            Utilities.loadMapping(synonyms2KeggFile, IdMapper.MapType.Synonyms, "KEGG");
            Utilities.loadMapping(refSeq2KeggFile, IdMapper.MapType.RefSeq, "KEGG");
            Utilities.loadMapping(gi2KeggFile, IdMapper.MapType.GI, "KEGG");
            final IdParser idParser = ClassificationManager.get("KEGG").getIdMapper().createIdParser();
            final KeggMapping keggMapping = KeggMapping.create(referencesDB, idParser, new ProgressPercentage("Building KEGG-mapping..."));
            keggMapping.save(new File(indexDirectory, "kegg.idx"));
        }
        if (malt.util.Utilities.hasAMapping(synonyms2SeedFile, refSeq2SeedFile, gi2SeedFile)) {
            Utilities.loadMapping(synonyms2SeedFile, IdMapper.MapType.Synonyms, "SEED");
            Utilities.loadMapping(refSeq2SeedFile, IdMapper.MapType.RefSeq, "SEED");
            Utilities.loadMapping(gi2SeedFile, IdMapper.MapType.GI, "SEED");
            final IdParser idParser = ClassificationManager.get("SEED").getIdMapper().createIdParser();
            SeedMapping seedMapping = SeedMapping.create(referencesDB, idParser, new ProgressPercentage("Building SEED-mapping..."));
            seedMapping.save(new File(indexDirectory, "seed.idx"));
        }
        if (malt.util.Utilities.hasAMapping(synonyms2CogFile, refSeq2CogFile, gi2CogFile)) {
            Utilities.loadMapping(synonyms2CogFile, IdMapper.MapType.Synonyms, "COG");
            Utilities.loadMapping(refSeq2CogFile, IdMapper.MapType.RefSeq, "COG");
            Utilities.loadMapping(gi2CogFile, IdMapper.MapType.GI, "COG");

            final IdParser idParser = ClassificationManager.get("COG").getIdMapper().createIdParser();
            final CogMapping cogMapping = CogMapping.create(referencesDB, idParser, new ProgressPercentage("Building COG-mapping..."));
            cogMapping.save(new File(indexDirectory, "cog.idx"));
        }

        if (doBuildTables) // don't write until after running classification mappers, as they add tags to reference sequences
            referencesDB.save(new File(indexDirectory, "ref.idx"), new File(indexDirectory, "ref.db"), new File(indexDirectory, "ref.inf"), saveFirstWordOfReferenceHeaderOnly);

        if (geneTableFile.length() > 0) {
            GeneTableBuilder geneTableBuilder = new GeneTableBuilder();
            geneTableBuilder.buildAndSaveGeneTable(referencesDB, geneTableFile, new File(indexDirectory, "gene-table.idx"), numberOfThreads);
        }
    }
}
