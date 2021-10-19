/*
 * MaltBuild.java Copyright (C) 2021. Daniel H. Huson
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
 *
 */
package malt;

import jloda.swing.util.ArgsOptions;
import jloda.swing.util.BasicSwing;
import jloda.swing.util.FastaFileFilter;
import jloda.swing.util.ResourceManager;
import jloda.util.*;
import jloda.util.interval.Interval;
import jloda.util.progress.ProgressPercentage;
import malt.data.*;
import malt.mapping.Mapping;
import malt.util.Utilities;
import megan.accessiondb.AccessAccessionMappingDatabase;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.IdParser;
import megan.genes.GeneItem;
import megan.genes.GeneItemCreator;
import megan.tools.AAdderBuild;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * build MALT index
 * Daniel Huson, 8.2014
 */
public class MaltBuild {
    final public static String INDEX_CREATOR = "MALT";

    /**
     * run the program
     *
     * @param args
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     */
    public static void main(String[] args) {
        ResourceManager.insertResourceRoot(megan.resources.Resources.class);

        try {
            PeakMemoryUsageMonitor.start();
            final MaltBuild maltBuild = new MaltBuild();
            ResourceManager.setWarningMissingIcon(false);
            ProgramProperties.setProgramIcons(ResourceManager.getIcons("malt-build16.png", "malt-build32.png", "malt-build48.png"));
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
        final ArgsOptions options = new ArgsOptions(args, this, "Builds an index for MALT (MEGAN alignment tool)");
        options.setAuthors("Daniel H. Huson");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2021 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");

        options.comment("Input:");
        final List<String> inputFiles = options.getOptionMandatory("i", "input", "Input reference files in FastA format (or specify a single directory)", new LinkedList<>());
        final SequenceType sequenceType = SequenceType.valueOfIgnoreCase(options.getOptionMandatory("s", "sequenceType", "Sequence type", SequenceType.values(), SequenceType.Protein.toString()));

        final List<String> gffFiles = options.getOption("-igff", "inputGFF", "Files that provide CDS annotations of DNA input files in GFF format (or specify a single directory)", new LinkedList<>());

        options.comment("Output:");
        final String indexDirectoryName = options.getOptionMandatory("-d", "index", "Name of index directory", "");

        options.comment("Performance:");
        final int numberOfThreads = options.getOption("-t", "threads", "Number of worker threads", Runtime.getRuntime().availableProcessors());
        final int stepSize = options.getOption("-st", "step", "Step size used to advance seed; a value greater than 1 will reduce index size, but also sensitivity", 1, 1, 100);

        options.comment("Seed:");
        String[] shapes = options.getOption("-ss", "shapes", "Seed shape(s)", new String[]{"default"});
        int maxHitsPerSeed = options.getOption("-mh", "maxHitsPerSeed", "Maximum number of hits per seed", 1000);
        final String proteinReduction;
        if (sequenceType == SequenceType.Protein || options.isDoHelp())
			proteinReduction = options.getOption("-pr", "proteinReduct", "Name or definition of protein alphabet reduction ("
																		 + StringUtils.toString(malt.data.ReducedAlphabet.reductions.keySet(), ",") + ")", "DIAMOND_11");
        else
            proteinReduction = "";

        options.comment("Classification support:");
        final String mapDBFile = options.getOption("-mdb", "mapDB", "MEGAN mapping db (file megan-map.db or megan-nucl.db)", "");
        final Set<String> dbSelectedClassifications = new HashSet<>(Arrays.asList(options.getOption("-on", "only", "Use only named classifications (if not set: use all)", new String[0])));

        options.comment("Deprecated classification support:");
        final boolean parseTaxonNames = options.getOption("-tn", "parseTaxonNames", "Parse taxon names", true);

        final String acc2TaxaFile = options.getOption("-a2t", "acc2taxa", "Accession-to-Taxonomy mapping file", "");
        final String synonyms2TaxaFile = options.getOption("-s2t", "syn2taxa", "Synonyms-to-Taxonomy mapping file", "");

        final HashMap<String, String> class2AccessionFile = new HashMap<>();
        final HashMap<String, String> class2SynonymsFile = new HashMap<>();

        for (String cName : ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy()) {
            class2AccessionFile.put(cName, options.getOption("-a2" + cName.toLowerCase(), "acc2" + cName.toLowerCase(), "Accession-to-" + cName + " mapping file", ""));
            class2SynonymsFile.put(cName, options.getOption("-s2" + cName.toLowerCase(), "syn2" + cName.toLowerCase(), "Synonyms-to-" + cName + " mapping file", ""));
            final String tags = options.getOption("-t4" + cName.toLowerCase(), "tags4" + cName.toLowerCase(), "Tags for " + cName + " id parsing (must set to activate id parsing)", "").trim();
            if (tags.length() > 0)
                ProgramProperties.put(cName + "Tags", tags);
            ProgramProperties.put(cName + "ParseIds", tags.length() > 0);
        }
        if (!acc2TaxaFile.isBlank())
            class2AccessionFile.put(Classification.Taxonomy, acc2TaxaFile);
        if (!synonyms2TaxaFile.isBlank())
            class2SynonymsFile.put(Classification.Taxonomy, synonyms2TaxaFile);

        final boolean functionalClassification = !options.getOption("-nf", "noFun", "Turn off functional classifications for provided mapping files (set this when using GFF files for DNA references)", false);

        options.comment(ArgsOptions.OTHER);
        ProgramProperties.put(IdParser.PROPERTIES_FIRST_WORD_IS_ACCESSION, options.getOption("-fwa", "firstWordIsAccession", "First word in reference header is accession number", ProgramProperties.get(IdParser.PROPERTIES_FIRST_WORD_IS_ACCESSION, true)));
        ProgramProperties.put(IdParser.PROPERTIES_ACCESSION_TAGS, options.getOption("-atags", "accessionTags", "List of accession tags", ProgramProperties.get(IdParser.PROPERTIES_ACCESSION_TAGS, IdParser.ACCESSION_TAGS)));

        final boolean saveFirstWordOfReferenceHeaderOnly = options.getOption("-fwo", "firstWordOnly", "Save only first word of reference header", false);
        final int randomSeed = options.getOption("rns", "random", "Random number generator seed", 666);
        final float hashTableLoadFactor = options.getOption("hsf", "hashScaleFactor", "Hash table scale factor", 0.9f, 0.1f, 1.0f);
        final boolean buildTableInMemory = options.getOption("btm", "buildTableInMemory", "Build the hash table in memory and then save (uses more memory, is much faster)", true);
        final boolean doBuildTables = !options.getOption("!xX", "xSkipTable", "Don't recompute index and tables, just compute profile support", false);

        final boolean lookInside = options.getOption("-ex", "extraStrict", "When given an input directory, look inside every GFF file to check that it is indeed in GFF3 format", false);

        options.done();
        Basic.setDebugMode(options.isVerbose());

        if (sequenceType == null)
            throw new IOException("Sequence type undefined");

        if (inputFiles.size() == 1) {
            final File file = new File(inputFiles.get(0));
            if (file.isDirectory()) {
                System.err.println("Looking for FastA files in directory: " + file);
                inputFiles.clear();
				for (File aFile : BasicSwing.getAllFilesInDirectory(file, new FastaFileFilter(), true)) {
					inputFiles.add(aFile.getPath());
				}
				if (inputFiles.size() == 0)
					throw new IOException("No files found in directory: " + file);
				else
					System.err.printf("Found: %,d%n", inputFiles.size());
			}
		}

		if (StringUtils.notBlank(mapDBFile))
			FileUtils.checkFileReadableNonEmpty(mapDBFile);

		for (var file : class2AccessionFile.values()) {
			if (!file.isBlank())
				FileUtils.checkFileReadableNonEmpty(file);
		}
		for (var file : class2SynonymsFile.values()) {
			if (!file.isBlank())
				FileUtils.checkFileReadableNonEmpty(file);
		}

        final Collection<String> mapDBClassifications = AccessAccessionMappingDatabase.getContainedClassificationsIfDBExists(mapDBFile)
                .stream().filter(s -> dbSelectedClassifications.size() == 0 || dbSelectedClassifications.contains(s)).collect(Collectors.toList());

		if (mapDBClassifications.size() > 0 && (StringUtils.hasPositiveLengthValue(class2AccessionFile) || StringUtils.hasPositiveLengthValue(class2SynonymsFile)))
			throw new UsageException("Illegal to use both --mapDB and ---acc2... or --syn2... options");

        if (mapDBClassifications.size() > 0)
            ClassificationManager.setMeganMapDBFile(mapDBFile);

        final ArrayList<String> cNames = new ArrayList<>();

        if (mapDBClassifications.size() > 0) {
            cNames.addAll(mapDBClassifications);
        } else {
            for (String cName : ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy()) {
				if ((dbSelectedClassifications.size() == 0 || dbSelectedClassifications.contains(cName))
					&& (StringUtils.notBlank(class2AccessionFile.get(cName)) || StringUtils.notBlank(class2SynonymsFile.get(cName))))
					cNames.add(cName);
            }
            if (!cNames.contains(Classification.Taxonomy) && (acc2TaxaFile.length() > 0 || synonyms2TaxaFile.length() > 0))
                cNames.add(Classification.Taxonomy);
        }
        if (cNames.size() > 0)
			System.err.println("Classifications to use: " + StringUtils.toString(cNames, ", "));

        AAdderBuild.setupGFFFiles(gffFiles, lookInside);

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
		System.err.println("Seed shape(s): " + StringUtils.toString(shapes, ", "));

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
        System.err.printf("Number input files: %,12d%n", inputFiles.size());
        referencesDB.loadFastAFiles(inputFiles, referenceAlphabet);
        System.err.printf("Number of sequences:%,12d%n", referencesDB.getNumberOfSequences());
        System.err.printf("Number of letters:%,14d%n", referencesDB.getNumberOfLetters());

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

        for (String cName : cNames) {
			final String cNameLowerCase = cName.toLowerCase();
			final String sourceName = (cName.equals(Classification.Taxonomy) ? "ncbi" : cNameLowerCase);
			ClassificationManager.ensureTreeIsLoaded(cName);
			FileUtils.writeStreamToFile(ResourceManager.getFileAsStream(sourceName + ".tre"), new File(indexDirectory, cNameLowerCase + ".tre"));
			FileUtils.writeStreamToFile(ResourceManager.getFileAsStream(sourceName + ".map"), new File(indexDirectory, cNameLowerCase + ".map"));
		}

        if (mapDBFile.length() == 0) {
            for (String cName : cNames) {
                final String cNameLowerCase = cName.toLowerCase();

                if (class2SynonymsFile.get(cName) != null)
                    Utilities.loadMapping(class2SynonymsFile.get(cName), IdMapper.MapType.Synonyms, cName);
                if (class2AccessionFile.get(cName) != null)
                    Utilities.loadMapping(class2AccessionFile.get(cName), IdMapper.MapType.Accession, cName);

                final IdParser idParser = ClassificationManager.get(cName, true).getIdMapper().createIdParser();
                if (cName.equals(Classification.Taxonomy))
                    idParser.setUseTextParsing(parseTaxonNames);

                if (functionalClassification || cName.equals(Classification.Taxonomy)) {
                    final Mapping mapping = Mapping.create(cName, referencesDB, idParser, new ProgressPercentage("Building " + cName + "-mapping..."));
                    mapping.save(new File(indexDirectory, cNameLowerCase + ".idx"));
                }
            }
        } else {
            final Map<String, Mapping> mappings;
            try (var progress = new ProgressPercentage("Building mappings...")) {
                mappings = Mapping.create(cNames, referencesDB, new AccessAccessionMappingDatabase(mapDBFile), progress);
            }
            for (String cName : mappings.keySet()) {
                final Mapping mapping = mappings.get(cName);
                mapping.save(new File(indexDirectory, cName.toLowerCase() + ".idx"));
            }
        }

        if (doBuildTables) // don't write until after running classification mappers, as they add tags to reference sequences
            referencesDB.save(new File(indexDirectory, "ref.idx"), new File(indexDirectory, "ref.db"), new File(indexDirectory, "ref.inf"), saveFirstWordOfReferenceHeaderOnly);

        if (gffFiles.size() > 0) {
            // setup gene item creator, in particular accession mapping
            final GeneItemCreator creator = AAdderBuild.setupCreator(null, class2AccessionFile);

            // obtains the gene annotations:
            Map<String, ArrayList<Interval<GeneItem>>> dnaId2list = AAdderBuild.computeAnnotations(creator, gffFiles);

            AAdderBuild.saveIndex(INDEX_CREATOR, creator, indexDirectory.getPath(), dnaId2list, referencesDB.refNames());
        }
    }
}
