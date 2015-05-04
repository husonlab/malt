package malt.next;

import jloda.util.*;
import malt.Version;
import malt.align.AlignerOptions;
import malt.align.BlastStatisticsHelper;
import malt.align.DNAScoringMatrix;
import malt.align.ProteinScoringMatrix;
import malt.data.SequenceType;
import malt.mapping.MappingHelper;
import malt.sequence.*;
import megan.parsers.blast.BlastMode;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * runs the MALT2 program
 * <p/>
 * Daniel Huson, August 2014
 */
public class MaltRun2 {
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
            final MaltRun2 program = new MaltRun2();
            ProgramProperties.setProgramIcon(ResourceManager.getIcon("malt-run48.png"));
            ProgramProperties.setProgramName("MaltRun2");
            ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

            program.run(args);

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
        final MaltOptions2 maltOptions = new MaltOptions2();
        final AlignerOptions alignerOptions = new AlignerOptions();

        final ArgsOptions options = new ArgsOptions(args, this, "Runs MALT, the MEGAN alignment tool");
        options.setAuthors("Daniel H. Huson");
        options.setVersion(Version.SHORT_DESCRIPTION);

        options.comment("Mode:");
        maltOptions.setMode(BlastMode.valueOfIgnoreCase(options.getOptionMandatory("-m", "mode", "Program mode", BlastMode.values(), BlastMode.BlastX.toString())));
        alignerOptions.setAlignmentType(options.getOption("-at", "alignmentType", "Type of alignment to be performed", AlignerOptions.AlignmentMode.values(), alignerOptions.getAlignmentType().toString()));

        options.comment("Input and output:");
        List<String> inputFileNames = options.getOptionMandatory("-i", "inFile", "Input file(s) containing queries in FastA or FastQ format", new LinkedList<String>());
        if (inputFileNames.size() == 1 || options.isDoHelp()) {
            boolean isList = options.getOption("-il", "isList", "Does the single given input file consist of a list of input files", false);
            if (isList & inputFileNames.size() > 0)
                inputFileNames = Utilities.getAllLines(inputFileNames.get(0));
        }
        String indexDirectory = options.getOptionMandatory("-d", "index", "Index directory created by MaltBuild", "");
        final List<String> outputMatchFileNames = options.getOptionMandatory("-o", "output", "Output file(s), directory or STDOUT", new LinkedList<String>());
        maltOptions.setMatchOutputFormat(options.getOption("-f", "outputFormat", "Output format", MaltOptions2.MatchOutputFormat.values(), maltOptions.getMatchOutputFormat().toString()));
        if (maltOptions.getMatchOutputFormat() != MaltOptions2.MatchOutputFormat.RMA || options.isDoHelp()) {
            maltOptions.setGzipMatches(options.getOption("-z", "gzip", "Compress output using gzip", maltOptions.isGzipMatches()));
        }
        /*
        if (maltOptions.getMatchOutputFormat() == MaltOptions2.MatchOutputFormat.SAM || options.isDoHelp()) {
            maltOptions.setGenerateSamSQLines(options.getOption("ssq", "samSQ", "Place @SQ lines in SAM files", maltOptions.isGenerateSamSQLines()));
        }
        */

        final List<String> outputAlignedFileNames = options.getOption("-oa", "outAligned", "Aligned reads output file(s) or directory or STDOUT", new LinkedList<String>());
        if (outputAlignedFileNames.size() > 0 || options.isDoHelp()) {
            maltOptions.setGzipAlignedReads(options.getOption("-za", "gzipAligned", "Compress aligned reads output using gzip", maltOptions.isGzipAlignedReads()));
        }
        final List<String> outputUnAlignedFileNames = options.getOption("-ou", "outUnaligned", "Unaligned reads output file(s) or directory or STDOUT", new LinkedList<String>());
        if (outputUnAlignedFileNames.size() > 0 || options.isDoHelp()) {
            maltOptions.setGzipUnalignedReads(options.getOption("-zu", "gzipUnaligned", "Compress unaligned reads output using gzip", maltOptions.isGzipUnalignedReads()));
        }

        options.comment("Performance:");
        maltOptions.setNumberOfThreads(options.getOption("-t", "threads", "Number of worker threads", Runtime.getRuntime().availableProcessors()));
        maltOptions.setQueryBatchSize(options.getOption("-k", "queryBatchSize", "Number of queries to be processed per batch", maltOptions.getQueryBatchSize()));
        maltOptions.setNumberOfChunks(Basic.nextPowerOf2(options.getOption("-sk", "numSeedChunks", "Number of seed chunks to process data in (should be power of 2)", maltOptions.getNumberOfChunks())));
        maltOptions.setUseReplicateQueryCaching(options.getOption("-rqc", "repQueryCache", "Cache results for replicated queries (major speedup when many queries are identical, e.g. using amplicon sequences)", false));
        final int maxNumberOfSeedShapes = options.getOption("-mss", "maxSeedShapes", "Set the maximum number of seed shapes to use (0 = all available)", 0);

        options.comment("Filter:");
        maltOptions.setMinBitScore(options.getOption("-b", "minBitScore", "Minimum bit score", maltOptions.getMinBitScore()));
        maltOptions.setMaxExpected(options.getOption("-e", "maxExpected", "Maximum expected score", maltOptions.getMaxExpected()));
        maltOptions.setMinProportionIdentity(options.getOption("-id", "minPercentIdentity", "Minimum percent identity", 100 * maltOptions.getMinProportionIdentity()) / 100.0);
        maltOptions.setMaxAlignmentsPerQuery(options.getOption("-mq", "maxMatchesPerQuery", "Maximum number of matches per query", maltOptions.getMaxAlignmentsPerQuery()));
        maltOptions.setMaxAlignmentsPerReference(options.getOption("-mr", "maxMatchesPerRef", "Maximum number of matches per reference (0 = use database default)", maltOptions.getMaxAlignmentsPerReference()));
        maltOptions.setMinComplexity(options.getOption("-mc", "minComplexity", "Minimum complexity for a query (Wootten & Federhen)", maltOptions.getMinComplexity()));

        if ((maltOptions.getMode() == BlastMode.BlastN || options.isDoHelp())) {
            options.comment("BlastN parameters:");
            alignerOptions.setMatchScore(options.getOption("-ma", "matchScore", "Match score for BLASTN", alignerOptions.getMatchScore()));
            alignerOptions.setMismatchScore(options.getOption("-mm", "mismatchScore", "Mismatch score for BLASTN", alignerOptions.getMismatchScore()));
            alignerOptions.setLambda(options.getOption("-la", "setLambda", "Parameter Lambda for BLASTN statistics", alignerOptions.getLambda()));
            alignerOptions.setK(options.getOption("-K", "setK", "Parameter K for BLASTN statistics", (float) alignerOptions.getK()));
        }

        String nameOfProteinScoringMatrix = null;
        if (maltOptions.getMode() == BlastMode.BlastP || maltOptions.getMode() == BlastMode.BlastX || options.isDoHelp()) {
            options.comment("BlastP and BlastX parameters:");
            nameOfProteinScoringMatrix = options.getOption("-sm", "subMatrix", "Protein substitution matrix to use", ProteinScoringMatrix.ScoringScheme.values(), ProteinScoringMatrix.ScoringScheme.BLOSUM62.toString());
            alignerOptions.setMatchScore(options.getOption("-ma", "matchScore", "Match score for BLASTN", alignerOptions.getMatchScore()));
            alignerOptions.setMismatchScore(options.getOption("-mm", "mismatchScore", "Mismatch score for BLASTN", alignerOptions.getMismatchScore()));
        }

        if (maltOptions.getMode() == BlastMode.BlastN || maltOptions.getMode() == BlastMode.BlastX || options.isDoHelp()) {
            options.comment("DNA query parameters:");
            maltOptions.setDoReverse(!options.getOption("-fo", "forwardOnly", "Align query forward strand only", false));
            maltOptions.setDoForward(!options.getOption("-ro", "reverseOnly", "Align query reverse strand only", false));
        }

        options.comment("Banded alignment parameters:");
        alignerOptions.setGapOpenPenalty(options.getOption("-go", "gapOpen", "Gap open penalty (BLASTN default 7, else default 11)", maltOptions.getMode() == BlastMode.BlastN ? 7 : 11));
        alignerOptions.setGapExtensionPenalty(options.getOption("-ge", "gapExtend", "Gap extension penalty (BLASTN default 3, else default 1)", maltOptions.getMode() == BlastMode.BlastN ? 3 : 1));

        alignerOptions.setBand(options.getOption("-bd", "band", "Band width/2 for banded alignment", alignerOptions.getBand()));

        options.comment("LCA:");
        maltOptions.setTopPercentLCA(options.getOption("-top", "topPercent", "Top percent value for LCA algorithm", maltOptions.getTopPercentLCA()));
        maltOptions.setMinSupportPercentLCA(options.getOption("-supp", "minSupportPercent", "Min support value for LCA algorithm as a percent of assigned reads (0 = off)", maltOptions.getMinSupportPercentLCA()));
        maltOptions.setMinSupportLCA(options.getOption("-sup", "minSupport", "Min support value for LCA algorithm", maltOptions.getMinSupportLCA()));

        options.comment("Seed filter heuristic parameters:");
        alignerOptions.setMinSeedIdentities(options.getOption("-sfMI", "sfMinIdentities", "Set the minimum number of seed identities (0: use default)", alignerOptions.getMinSeedIdentities(maltOptions.getMode())));
        alignerOptions.setUngappedMinRawScore(options.getOption("-sfUMS", "sfUngappedMinScore", "Min raw score for ungapped alignment (0: use default", alignerOptions.getUngappedMinRawScore(maltOptions.getMode())));
        alignerOptions.setUngappedXDrop(options.getOption("-sfUX", "sfUngappedXDrop", "Set the ungapped alignment xdrop (0: use default)", alignerOptions.getUngappedXDrop(maltOptions.getMode())));

        options.comment(ArgsOptions.OTHER);
        final int replicateQueryCacheBits;
        if (maltOptions.isUseReplicateQueryCaching() || options.isDoHelp())
            replicateQueryCacheBits = options.getOption("-rqcb", "repQueryCacheBits", "Bits used for caching replicate queries (size is then 2^bits)", 20);
        else
            replicateQueryCacheBits = 0;

        options.done();

        Basic.setDebugMode(options.isVerbose());


        malt.util.Utilities.checkFileExists(new File(indexDirectory, "malt.idx"));
        malt.util.Utilities.checkFileExists(new File(indexDirectory, "ref.idx"));

        final MaltIndexFile index = new MaltIndexFile();
        index.read(new File(indexDirectory, "malt.idx"));
        final SequenceType refSequenceType = index.getSequenceType();
        if (maxNumberOfSeedShapes > 0)
            index.reduceNumberOfSeedShapes(maxNumberOfSeedShapes);
        if (options.isVerbose())
            System.err.println("maxRefOccurrencesPerSeed: " + index.getMaxRefOccurrencesPerSeed());
        final Alphabet refAlphabet = (refSequenceType == SequenceType.Protein ? malt.sequence.ProteinAlphabet.getInstance() : DNA5Alphabet.getInstance());
        final ReducedAlphabet reducedAlphabet = index.getReducedAlphabet();
        final ISeedExtractor seedExtractor = (reducedAlphabet != null ? reducedAlphabet : new SequenceEncoder(refAlphabet));

        final Alphabet queryAlphabet;

        final QueryStore.SequenceType queryType;

        switch (maltOptions.getMode()) {
            case BlastN:
                if (refSequenceType != SequenceType.DNA)
                    throw new IOException(String.format("Mode %s not compatible with reference index of type %s", maltOptions.getMode(), refSequenceType));
                alignerOptions.setScoringMatrix(new DNAScoringMatrix(alignerOptions.getMatchScore(), alignerOptions.getMismatchScore()));
                alignerOptions.setReferenceIsDNA(true);

                queryAlphabet = DNA5Alphabet.getInstance();
                queryType = QueryStore.SequenceType.dna;
                break;
            case BlastX:
                if (refSequenceType != SequenceType.Protein)
                    throw new IOException(String.format("Mode %s not compatible with reference index of type %s", maltOptions.getMode(), refSequenceType));

                alignerOptions.setScoringMatrix(ProteinScoringMatrix.create(nameOfProteinScoringMatrix));
                alignerOptions.setReferenceIsDNA(false);
                alignerOptions.setLambdaAndK(BlastStatisticsHelper.lookupLambdaAndK(nameOfProteinScoringMatrix, alignerOptions.getGapOpenPenalty(), alignerOptions.getGapExtensionPenalty()));

                queryAlphabet = DNA5Alphabet.getInstance();
                queryType = QueryStore.SequenceType.dnax;
                break;
            case BlastP:
                if (refSequenceType != SequenceType.Protein)
                    throw new IOException(String.format("Mode %s not compatible with reference index of type %s", maltOptions.getMode(), refSequenceType));

                alignerOptions.setScoringMatrix(ProteinScoringMatrix.create(nameOfProteinScoringMatrix));
                alignerOptions.setReferenceIsDNA(false);
                alignerOptions.setLambdaAndK(BlastStatisticsHelper.lookupLambdaAndK(nameOfProteinScoringMatrix, alignerOptions.getGapOpenPenalty(), alignerOptions.getGapExtensionPenalty()));

                queryAlphabet = malt.sequence.ProteinAlphabet.getInstance();
                queryType = QueryStore.SequenceType.prot;
                break;
            default:
                throw new IOException("Unsupported mode: " + maltOptions.getMode().toString());
        }

        final boolean storeOriginalQuerySequences = (outputAlignedFileNames.size() > 0
                || outputUnAlignedFileNames.size() > 0
                || maltOptions.getMatchOutputFormat().equals(MaltOptions2.MatchOutputFormat.RMA));

        // check consistency of all options:

        if (inputFileNames.size() == 0)
            throw new UsageException("You must specify at least one input file");
        malt.util.Utilities.checkFileExists(new File(inputFileNames.get(0)));

        if (!maltOptions.isDoForward() && !maltOptions.isDoReverse())
            throw new UsageException("Illegal to specify both --forwardOnly and --reverseOnly");

        // load mapping files, if present

        if (maltOptions.getMatchOutputFormat() == MaltOptions2.MatchOutputFormat.RMA) {
            MappingHelper.loadTaxonMapping(index.isDoTaxonomy(), indexDirectory);
            MappingHelper.loadKeggMapping(index.isDoKegg(), indexDirectory);
            MappingHelper.loadSeedMapping(index.isDoSeed(), indexDirectory);
            MappingHelper.loadCogMapping(index.isDoCog(), indexDirectory);
        }
        // load all reference files:
        final ReferenceStore refStore = new ReferenceStore(refAlphabet, seedExtractor, index.getNumberOfSeedShapes(), index.getNumberOfSequences());
        refStore.read(new File(indexDirectory, "ref.idx"));
        refStore.loadMaskFile(new File(indexDirectory, "mask.idx.gz"));

        if (refStore.getNumberOfSequences() != index.getNumberOfSequences())
            throw new IOException("ref.idx: numberOfSequences=" + refStore.getNumberOfSequences() + ", expected: " + index.getNumberOfSequences());
        /*
        if (refStore.getNumberOfLetters() != index.getNumberOfLetters())
            throw new IOException("ref.idx: numberOfLetters=" + refStore.getNumberOfLetters() + ", expected: " + index.getNumberOfLetters());
            */

        alignerOptions.setReferenceDatabaseLength(refStore.getNumberOfLetters());

        final SeedShape2[] seedShapes = index.getSeedShapes();
        SeedStore[] querySeedStores = new SeedStore[maltOptions.getNumberOfThreads()];
        final boolean keepRefSeeds = (inputFileNames.size() > 1 && maltOptions.getNumberOfChunks() == 1);
        final SeedStore[][] refSeedStores = new SeedStore[seedShapes.length][];

        final QueryMatchesCache queryMatchesCache = (maltOptions.isUseReplicateQueryCaching() ? new QueryMatchesCache(replicateQueryCacheBits) : null);

        long totalQueries = 0;
        long totalLowComplexity = 0;
        long totalAlignedQueries = 0;
        long totalAlignments = 0;

        // run alignment for each input file:
        int fileNumber = 0;

        final ExecutorService threadPool = Executors.newFixedThreadPool(maltOptions.getNumberOfThreads());

        int numberOfJobs = 0;

        // process each input file
        for (String inFile : inputFileNames) {
            try {
                if ((new File(inFile).exists())) {
                    System.err.println("-----------------------------");
                    // setup input file:
                    FastAFileIteratorCode queryIterator = new FastAFileIteratorCode(inFile, new SequenceEncoder(queryAlphabet));

                    // setup output files:
                    final String matchesOutputFile = OutputManager.getOutputFileName(fileNumber, inputFileNames, outputMatchFileNames, maltOptions.getMatchesOutputSuffix(), maltOptions.isGzipMatches());
                    final String alignedReadsOutputFile = OutputManager.getOutputFileName(fileNumber, inputFileNames, outputAlignedFileNames, "-aligned.fna", maltOptions.isGzipAlignedReads());
                    final String unalignedReadsOutputFile = OutputManager.getOutputFileName(fileNumber, inputFileNames, outputUnAlignedFileNames, "-unaligned.fna", maltOptions.isGzipAlignedReads());
                    final OutputManager outputManager = new OutputManager(maltOptions, matchesOutputFile, alignedReadsOutputFile, unalignedReadsOutputFile);

                    final QueryStore queryStore = new QueryStore(queryMatchesCache, seedExtractor, queryType, maltOptions.isDoForward(), maltOptions.isDoReverse(),
                            maltOptions.getMinComplexity(), storeOriginalQuerySequences, 1000000);
                    int queryBatch = 0;
                    int numberOfQueries = 0;
                    int numberLowComplexity = 0;
                    while (queryIterator.hasNext()) {
                        queryBatch++;
                        numberOfQueries += queryStore.loadFastAFile(queryIterator, maltOptions.getQueryBatchSize(), new ProgressPercentage("Indexing *query* file: " + inFile + (queryBatch > 1 ? " (batch " + queryBatch + ")" : "")));
                        if (numberOfJobs == 0 || !keepRefSeeds) // if we are keeping ref seeds then we must not change the number of jobs
                            numberOfJobs = computeNumberOfJobs(maltOptions.getNumberOfChunks(), maltOptions.getNumberOfThreads(), index, queryStore, maltOptions.getQueryBatchSize());

                        if (querySeedStores.length < numberOfJobs) {
                            querySeedStores = Utilities.grow(querySeedStores, numberOfJobs);
                        }
                        MatchStore matches = new MatchStore(queryStore.getNumberOfSequences(), maltOptions.getMaxAlignmentsPerQuery(), maltOptions.getMaxAlignmentsPerReference());

                        // process each seed shape:
                        for (int s = 0; s < seedShapes.length; s++) {
                            final SeedShape2 seedShape = seedShapes[s];
                            System.err.println("Seed shape:\n\t" + seedShape);

                            for (int chunk = 0; chunk < maltOptions.getNumberOfChunks(); chunk++) {
                                if (maltOptions.getNumberOfChunks() > 1)
                                    System.err.println("Processing chunk " + (chunk + 1) + " of " + maltOptions.getNumberOfChunks() + ":");

                                boolean isRefSeedsAreSet = true;

                                for (int h = 0; h < numberOfJobs; h++) {
                                    if (querySeedStores[h] == null) {
                                        querySeedStores[h] = new SeedStore(seedExtractor, seedShapes[s].getWeight(),
                                                seedShape.getMaxSeedCount(queryStore.getNumberOfSequences(), queryStore.getNumberOfLetters(), maltOptions.getNumberOfChunks() * numberOfJobs));
                                    } else
                                        querySeedStores[h].reset();
                                    if (refSeedStores[s] == null)
                                        refSeedStores[s] = new SeedStore[numberOfJobs];
                                    if (refSeedStores[s][h] == null) {
                                        refSeedStores[s][h] = new SeedStore(seedExtractor, seedShapes[s].getWeight(), index.getSeedCounts()[s] / (maltOptions.getNumberOfChunks() * numberOfJobs));
                                        isRefSeedsAreSet = false;
                                    } else if (!keepRefSeeds) {
                                        refSeedStores[s][h].reset();
                                        isRefSeedsAreSet = false;
                                    }
                                }

                                if (!keepRefSeeds || !isRefSeedsAreSet) {
                                    ReferenceSeedsLoop.run(threadPool, chunk, maltOptions.getNumberOfChunks(), numberOfJobs, maltOptions.getNumberOfThreads(), refStore, seedShape, refSeedStores[s]);
                                }
                                QuerySeedsLoop.run(threadPool, chunk, maltOptions.getNumberOfChunks(), numberOfJobs, maltOptions.getNumberOfThreads(), queryStore, seedShape, querySeedStores);
                                numberLowComplexity += queryStore.getNumberLowComplexitySequences();

                                MainAlignmentLoop.run(threadPool, maltOptions, alignerOptions, index.getMaxRefOccurrencesPerSeed(), queryStore, querySeedStores, refStore, refSeedStores[s], seedShape, seedShapes, matches);
                            }
                        }
                        matches.sort(threadPool, maltOptions.getNumberOfThreads());
                        // if we are using caching, copy all matches found in this batch
                        if (queryMatchesCache != null)
                            queryMatchesCache.addMatches(queryStore, matches);

                        // produced desired output:
                        outputManager.writeAlignments(maltOptions, queryStore, refStore, matches, queryMatchesCache);
                        outputManager.writeAlignedAndUnaligned(queryStore, matches);
                    }
                    totalQueries += numberOfQueries;
                    totalLowComplexity += numberLowComplexity;

                    totalAlignedQueries += outputManager.getCountAlignedQueries();
                    totalAlignments += outputManager.getCountAlignments();
                    outputManager.close();
                    System.err.println(outputManager.getStatString(numberLowComplexity, (queryMatchesCache != null ? queryMatchesCache.getSize() : 0)));
                } else {
                    System.err.println("File not found: '" + inFile + "', skipped");
                }
            } finally {
                fileNumber++;
            }
        }

        threadPool.shutdownNow();

        System.err.println("------------Summary-------------");
        System.err.println(String.format("Number of queries: %,13d", totalQueries));
        if (totalLowComplexity > 0)
            System.err.println(String.format("Low complexity:    %,13d", totalLowComplexity));
        System.err.println(String.format("Aligned queries:   %,13d", totalAlignedQueries));
        System.err.println(String.format("Unalign.queries:   %,13d", (totalQueries - totalAlignedQueries)));
        if (queryMatchesCache != null)
            System.err.println(String.format("Cached queries:    %,13d", queryMatchesCache.getSize()));
        System.err.println(String.format("Num. alignments: %,15d", totalAlignments));
        System.err.println("--------------------------------");
    }

    /**
     * compute the number of parallel jobs to perform per chunk
     *
     * @param numberOfChunks
     * @param numberOfThreads
     * @param index
     * @param queryStore
     * @return number of jobs per chunk
     */
    private int computeNumberOfJobs(final int numberOfChunks, final int numberOfThreads, final MaltIndexFile index, final QueryStore queryStore, final int queryBatchSize) {
        int maxForAGivenSeed = 0;

        for (int t = 0; t < index.getSeedShapes().length; t++) {
            SeedShape2 seedShape = index.getSeedShapes()[t];
            long approximateNumberOfQuerySeeds = (queryStore.getNumberOfLetters() - queryStore.getNumberOfSequences() * (seedShape.getWeight() - 1));
            if (queryBatchSize < queryStore.getNumberOfSequences()) {
                approximateNumberOfQuerySeeds *= (queryBatchSize / queryStore.getNumberOfSequences());
            }
            long numberOfRefSeeds = index.getSeedCounts()[t];
            long maxNumber = Math.max(approximateNumberOfQuerySeeds, numberOfRefSeeds);
            maxForAGivenSeed = (int) Math.max(maxForAGivenSeed, maxNumber / (numberOfChunks * 100000000));
        }
        return Basic.nextPowerOf2(Math.max(100 * numberOfThreads, maxForAGivenSeed));
    }
}
