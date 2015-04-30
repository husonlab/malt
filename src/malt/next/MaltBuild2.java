package malt.next;

import jloda.util.*;
import malt.Version;
import malt.data.SeedShape;
import malt.data.SequenceType;
import malt.mapping.CogMapping;
import malt.mapping.KeggMapping;
import malt.mapping.SeedMapping;
import malt.mapping.TaxonMapping;
import malt.sequence.*;
import megan.access.ClassificationHelper;
import megan.access.ClassificationMapper;
import megan.cogviewer.data.CogData;
import megan.main.LicensedSoftware;
import megan.main.MeganProperties;
import megan.mainviewer.data.TaxonomyData;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * build MALT index
 * Daniel Huson, 8.2014
 */
public class MaltBuild2 extends LicensedSoftware {
    // use lots of locks so that threads don't get in each others way
    private final int NUMBER_OF_SYNC_OBJECTS = (1 << 10);
    private final int SYNC_MASK = (NUMBER_OF_SYNC_OBJECTS - 1);
    private final Object[] syncObjects = new Object[NUMBER_OF_SYNC_OBJECTS];

    public MaltBuild2() {
        for (int i = 0; i < NUMBER_OF_SYNC_OBJECTS; i++)
            syncObjects[i] = new Object();
    }

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
            final MaltBuild2 program = new MaltBuild2();
            ProgramProperties.setProgramIcon(ResourceManager.getIcon("malt-build48.png"));
            ProgramProperties.setProgramName("MaltBuild");
            ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

            program.run(args);

            if (!program.hasValidLicense())
                throw new IOException("Failed to verify license");

            System.err.println("Total time: " + ((System.currentTimeMillis() - start) / 1000) + "s");
            System.err.println("Memory use: " + PeakMemoryUsageMonitor.getPeakUsageString());
            if (!ArgsOptions.hasMessageWindow())
                System.exit(0);
            else
                System.err.println("DONE - close window to quit");
        } catch (Exception ex) {
            if (ex.getMessage() == null || !ex.getMessage().equals("Help"))
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
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     */
    public void run(String[] args) throws UsageException, IOException, CanceledException, InterruptedException {
        loadPublicKey();

        // parse commandline options:
        final ArgsOptions options = new ArgsOptions(args, this, "Builds the MALT index");
        options.setAuthors("Daniel H. Huson");
        options.setVersion(Version.SHORT_DESCRIPTION);

        options.comment("Input and output:");
        final List<String> inputFiles = options.getOptionMandatory("-i", "input", "Input reference file(s)", new LinkedList<String>());
        final SequenceType sequenceType = SequenceType.valueOfIgnoreCase(options.getOptionMandatory("-s", "sequenceType", "Sequence type", SequenceType.values(), SequenceType.Protein.toString()));
        final String indexDirectoryName = options.getOptionMandatory("-d", "index", "Name of index directory", "");
        final boolean shortHeaders = options.getOption("-sh", "shortHeaders", "Only store first word of each reference header", false);

        options.comment("Performance:");
        final int numberOfThreads = (options.getOption("-t", "threads", "Number of worker threads", Runtime.getRuntime().availableProcessors()));
        final int numberOfChunks = Basic.nextPowerOf2(options.getOption("-sk", "numSeedChunks", "Number of seed chunks to process data in (should be power of 2)", 1));

        options.comment("Seed:");
        String[] shapes = options.getOption("-ss", "shapes", "Seed shape(s)", new String[]{"default"});
        final String proteinReduction;
        if (sequenceType == SequenceType.Protein || options.isDoHelp())
            proteinReduction = options.getOption("-pr", "proteinReduct", "Name or definition of protein alphabet reduction ("
                    + Basic.toString(malt.data.ReducedAlphabet.reductions.keySet(), ",") + ")", "DIAMOND_11");
        else
            proteinReduction = "";
        final int maxOccurrencesPerSeed = options.getOption("-mos", "maxOccurrencesSeed", "Maximum number of occurrences for a seed", 10);
        /*
        float seedCoverage = options.getOption("-sc", "seedCover", "Proportion of seed occurrences to cover", 0.67f, 0.5f, 1.0f);
        boolean saveHistogram = options.getOption("-ssh", "saveHistogram", "Save seed histograms to files", false);
*/
        final boolean doComputeMask = options.getOption("-mk", "mask", "Perform mask calculation (necessary e.g. for 16S references)", true);
        final double maskFileThreshold;
        if (doComputeMask || options.isDoHelp())
            maskFileThreshold = options.getOption("-mt", "maskThreshold", "Create a mask file if proportion of masked seeds exceeds this threshold", 0.005);
        else
            maskFileThreshold = 0;

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

        options.comment("Additional files:");
        final String taxTreeFile = options.getOption("-tre", "taxTree", "NCBI tree file (ncbi.tre as used by MEGAN)", null, "",
                ClassificationHelper.hasAMapping(synonyms2TaxaFile, refSeq2TaxaFile, gi2TaxaFile) || geneTableFile.length() > 0);
        final String taxMapFile = options.getOption("-map", "taxMap", "NCBI map file (ncbi.map as used by MEGAN)", null, "",
                ClassificationHelper.hasAMapping(synonyms2TaxaFile, refSeq2TaxaFile, gi2TaxaFile) || geneTableFile.length() > 0);

        final String cogMapFile = options.getOption("-cmf", "cogMappingFile", "COG mapping file (cog.map file as used by MEGAN)", null, "",
                (gi2CogFile.length() > 0 || refSeq2CogFile.length() > 0 || synonyms2CogFile.length() > 0));

        options.comment("Properties and license:");
        if (!loadPropertiesAndLicense(options))
            throw new IOException("License file not found: " + ProgramProperties.get(MeganProperties.LICENSE_FILE));

        options.done();
        Basic.setDebugMode(options.isVerbose());

        verifyLicense();
        if (hasValidLicense())
            System.err.println("\nMEGAN5 license certificate:\n" + license.toString());

        System.err.println("Reference sequence type set to: " + sequenceType.toString());
        final SeedShape2[] seedShapes;
        final Alphabet refAlphabet;
        final ReducedAlphabet reducedAlphabet;
        final ISeedExtractor seedExtractor;

        switch (sequenceType) {
            case DNA:
                if (shapes[0].equalsIgnoreCase("default")) {
                    shapes = new String[]{SeedShape.SINGLE_DNA_SEED};
                }
                seedShapes = new SeedShape2[shapes.length];
                for (int t = 0; t < shapes.length; t++) {
                    seedShapes[t] = new SeedShape2(shapes[t]);
                }
                refAlphabet = DNA5Alphabet.getInstance();
                reducedAlphabet = null;
                seedExtractor = new SequenceEncoder(refAlphabet);
                break;
            case Protein:
                if (shapes[0].equalsIgnoreCase("default")) {
                    shapes = SeedShape.PROTEIN_SEEDS;
                }
                seedShapes = new SeedShape2[shapes.length];
                for (int t = 0; t < shapes.length; t++) {
                    seedShapes[t] = new SeedShape2(shapes[t]);
                }
                refAlphabet = malt.sequence.ProteinAlphabet.getInstance();
                reducedAlphabet = new ReducedAlphabet(refAlphabet, proteinReduction);
                seedExtractor = reducedAlphabet;

                break;
            default:
                throw new UsageException("Undefined sequence type: " + sequenceType);
        }
        System.err.println("Seed shape(s):\n\t" + Basic.toString(shapes, "\t\n"));

        // terminate if invalid license:
        if (licenseInvalid)
            return;

        final File indexDirectory = new File(indexDirectoryName);

        if (indexDirectory.exists()) {
            malt.util.Utilities.cleanIndexDirectory(indexDirectory);
        } else {
            if (!indexDirectory.mkdir())
                throw new IOException("mkdir failed: " + indexDirectoryName);
        }

        final File referenceFile = new File(indexDirectory, "ref.idx");
        if ((!referenceFile.exists() || referenceFile.delete()) && !referenceFile.createNewFile())
            throw new IOException("Can't create file: " + referenceFile);

        final ReferenceStoreBuilder refStoreBuilder = new ReferenceStoreBuilder(refAlphabet, seedExtractor, seedShapes);
        refStoreBuilder.processFastAFiles(inputFiles);

        final MaltIndexFile index = new MaltIndexFile();
        index.setSequenceType(sequenceType);
        index.setReducedAlphabet(reducedAlphabet);
        index.setNumberOfSequences(refStoreBuilder.getNumberOfSequences());
        index.setNumberOfLetters(refStoreBuilder.getNumberOfLetters());
        index.setSeedCounts(refStoreBuilder.getSeedCounts());
        index.setSeedShapes(seedShapes);

        // terminate if no valid license:
        if (!licenseValid)
            return;

        final ClassificationMapper mapper = new ClassificationMapper();
        // build classification index files, if requested
        if (ClassificationHelper.hasAMapping(synonyms2TaxaFile, refSeq2TaxaFile, gi2TaxaFile) || geneTableFile.length() > 0) {
            final File indexTreeFile = new File(indexDirectory, "taxonomy.tre");
            final File indexMapFile = new File(indexDirectory, "taxonomy.map");
            if (!indexTreeFile.equals(new File(taxTreeFile)))
                Basic.copyFileUncompressed(new File(taxTreeFile), indexTreeFile);
            if (!indexMapFile.equals(new File(taxMapFile)))
                Basic.copyFileUncompressed(new File(taxMapFile), indexMapFile);

            TaxonomyData.getName2IdMap().loadFromFile(taxMapFile);
            TaxonomyData.getTree().loadFromFile(taxTreeFile);

            ClassificationHelper.loadTaxonMapping(synonyms2TaxaFile, refSeq2TaxaFile, gi2TaxaFile, mapper);
            final TaxonMapping taxonMapping = TaxonMapping.create(refStoreBuilder, mapper, new ProgressPercentage("Building taxon-mapping..."));
            taxonMapping.save(new File(indexDirectory, "taxonomy.idx"));
            index.setDoTaxonomy(true);
        }
        if (ClassificationHelper.hasAMapping(synonyms2KeggFile, refSeq2KeggFile, gi2KeggFile)) {
            ClassificationHelper.loadKeggMapping(synonyms2KeggFile, refSeq2KeggFile, gi2KeggFile, mapper);
            final KeggMapping keggMapping = KeggMapping.create(refStoreBuilder, mapper, new ProgressPercentage("Building KEGG-mapping..."));
            keggMapping.save(new File(indexDirectory, "kegg.idx"));
            index.setDoKegg(true);
        }
        if (ClassificationHelper.hasAMapping(synonyms2SeedFile, refSeq2SeedFile, gi2SeedFile)) {
            ClassificationHelper.loadSeedMapping(synonyms2SeedFile, refSeq2SeedFile, gi2SeedFile, mapper);
            SeedMapping seedMapping = SeedMapping.create(refStoreBuilder, mapper, new ProgressPercentage("Building SEED-mapping..."));
            seedMapping.save(new File(indexDirectory, "seed.idx"));
            index.setDoSeed(true);
        }
        if (ClassificationHelper.hasAMapping(synonyms2CogFile, refSeq2CogFile, gi2CogFile)) {
            CogData.getName2IdMap().loadFromFile(cogMapFile);
            if (synonyms2CogFile.length() > 0) {
                ClassificationMapper.loadMappingFile(CogData.getName2IdMap().getName2Id(), ClassificationMapper.Classification.COG, ClassificationMapper.MapType.SYNONYMS_MAP, synonyms2CogFile, new ProgressPercentage("Reading file: " + synonyms2CogFile), true);
                mapper.setActive(ClassificationMapper.Classification.COG, ClassificationMapper.MapType.SYNONYMS_MAP, true);
            }
            if (gi2CogFile.length() > 0) {
                ClassificationMapper.loadMappingFile(CogData.getName2IdMap().getName2Id(), ClassificationMapper.Classification.COG, ClassificationMapper.MapType.GI_MAP, gi2CogFile, new ProgressPercentage("Reading file: " + gi2CogFile), true);
                mapper.setActive(ClassificationMapper.Classification.COG, ClassificationMapper.MapType.GI_MAP, true);
            }
            if (refSeq2CogFile.length() > 0) {
                ClassificationMapper.loadMappingFile(CogData.getName2IdMap().getName2Id(), ClassificationMapper.Classification.COG, ClassificationMapper.MapType.REFSEQ_MAP, refSeq2CogFile, new ProgressPercentage("Reading file: " + refSeq2CogFile), true);
                mapper.setActive(ClassificationMapper.Classification.COG, ClassificationMapper.MapType.REFSEQ_MAP, true);
            }
            final CogMapping cogMapping = CogMapping.create(refStoreBuilder, mapper, new ProgressPercentage("Building COG-mapping..."));
            cogMapping.save(new File(indexDirectory, "cog.idx"));
            index.setDoCog(true);
        }

        long countSeedsAllSeedShapes = 0;
        long countMaskedAllSeedShapes = 0;

        if (doComputeMask) { // necessary for 16S data
            final ExecutorService threadPool = Executors.newFixedThreadPool(numberOfThreads);

            for (int s = 0; s < seedShapes.length; s++) {
                final SeedShape2 seedShape = seedShapes[s];
                System.err.println("Seed shape:\n\t" + seedShape);

                final long numberOfSeeds = (refStoreBuilder.getNumberOfLetters() - (seedShape.getLength() - 1) * refStoreBuilder.getNumberOfSequences());
                final int numberOfJobs = 10 * Math.max(numberOfThreads, (int) (numberOfSeeds / (numberOfChunks * 100000000l)));
                final SeedStore[] seedStores = new SeedStore[numberOfJobs];

                {
                    final ProgressPercentage progress = new ProgressPercentage("Initializing...");

                    for (int job = 0; job < numberOfJobs; job++) {
                        seedStores[job] = new SeedStore(seedExtractor, seedShape.getWeight(), numberOfSeeds / (numberOfChunks * numberOfJobs));
                        progress.incrementProgress();
                    }
                    progress.close();
                }

                System.err.println("Processing high occurrence reference seeds...");
                long totalSeeds = 0;
                long totalSeedLocationsMasked = 0;

                for (int chunk = 0; chunk < numberOfChunks; chunk++) {
                    System.err.println("Processing chunk " + (chunk + 1) + " of " + numberOfChunks + ":");

                    for (int job = 0; job < numberOfJobs; job++) {
                        seedStores[job].clear();
                    }
                    System.err.println("Number of jobs: " + numberOfJobs);

                    ReferenceSeedsLoop.run(threadPool, chunk, numberOfChunks, numberOfJobs, numberOfThreads, refStoreBuilder, seedShape, seedStores);

                    final long[] seedLocationsMasked = new long[seedStores.length];
                    long countSeedLocations = 0;

                    final int sFinal = s;
                    final ProgressPercentage progress = new ProgressPercentage("Masking high occurrence reference seeds...");
                    final CountDownLatch countDownLatch = new CountDownLatch(seedStores.length);

                    for (int i = 0; i < seedStores.length; i++) {
                        final int iFinal = i;
                        final SeedStore seedStore = seedStores[iFinal];

                        countSeedLocations += seedStore.size();

                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (seedStore.size() > 0) {
                                        final long[] seedArray = seedStore.getDataArray();
                                        final int arrayLength = seedStore.getLength();
                                        final Random random = new Random(seedShape.getId() * 666);

                                        if (iFinal == 0)
                                            progress.setMaximum(arrayLength);

                                        long previousSeed = 0;
                                        int seedCount = 0;

                                        for (int s = 0; s < arrayLength; s += 2) {
                                            final long seed = seedArray[s];

                                            if (seed != previousSeed) {
                                                int s2 = s;
                                                seedCount = 0;
                                                while (seedArray[s2] == seed) {
                                                    seedCount++;
                                                    s2 += 2;
                                                    if (s2 >= arrayLength)
                                                        break;
                                                }
                                                previousSeed = seed;
                                            }

                                            if (seedCount == 0 || (seedCount > maxOccurrencesPerSeed && random.nextInt(seedCount) >= maxOccurrencesPerSeed)) {
                                                final int seqId = seedStore.getSequenceIdForIndex(s + 1);
                                                final int seqPos = seedStore.getSequencePosForIndex(s + 1);
                                                synchronized (syncObjects[seqId & SYNC_MASK]) {
                                                    refStoreBuilder.getMask(seqId).set(sFinal, seqPos, true);
                                                }
                                                seedLocationsMasked[iFinal]++;
                                            }
                                            if (iFinal == 0)
                                                progress.setProgress(s);
                                        }
                                    }
                                } catch (Exception ex) {
                                    Basic.caught(ex);
                                } finally {

                                    countDownLatch.countDown();
                                }

                            }
                        });
                    }
                    countDownLatch.await();
                    progress.close();
                    System.err.println(String.format("Seeds count:  %,15d", countSeedLocations));
                    System.err.println(String.format("Seeds masked: %,15d", Basic.getSum(seedLocationsMasked)));
                    totalSeedLocationsMasked += Basic.getSum(seedLocationsMasked);
                    totalSeeds += countSeedLocations;
                }
                System.err.println("--------------------------------");
                System.err.println(String.format("Total seeds:  %,15d", totalSeeds));
                System.err.println(String.format("Total masked: %,15d", totalSeedLocationsMasked));
                countSeedsAllSeedShapes += totalSeeds;
                countMaskedAllSeedShapes += totalSeedLocationsMasked;
            }

            index.setMaxRefOccurrencesPerSeed(maxOccurrencesPerSeed);
            threadPool.shutdownNow();
        }

        index.write(new File(indexDirectory, "malt.idx"));

        refStoreBuilder.write(referenceFile, shortHeaders);

        if (countSeedsAllSeedShapes > 0) {
            System.err.println(String.format("Proportion masked: %10.5f", (double) countMaskedAllSeedShapes / (double) countSeedsAllSeedShapes));

            if (doComputeMask && (double) countMaskedAllSeedShapes / (double) countSeedsAllSeedShapes > maskFileThreshold) {
                refStoreBuilder.writeMasks(new File(indexDirectory, "mask.idx.gz"));
            } else
                System.err.println("No mask file written");
        }
    }
}
