/*
 * MaltRun.java Copyright (C) 2023 Daniel H. Huson
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

import jloda.seq.BlastMode;
import jloda.swing.util.ArgsOptions;
import jloda.swing.util.ResourceManager;
import jloda.util.*;
import jloda.util.progress.ProgressPercentage;
import malt.align.AlignerOptions;
import malt.align.BlastStatisticsHelper;
import malt.align.DNAScoringMatrix;
import malt.align.ProteinScoringMatrix;
import malt.data.*;
import malt.io.*;
import malt.mapping.MappingManager;
import malt.util.Utilities;
import megan.classification.ClassificationManager;
import megan.core.Document;
import megan.genes.GeneItemAccessor;
import megan.util.ReadMagnitudeParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * the MALT metagenome alignment tool
 * Daniel Huson, 8.2014
 */
public class MaltRun {
    public static String version;

    private long totalReads = 0;
    private long totalAlignedReads = 0;
    private long totalAlignments = 0;

    /**
     * launch the MALT program
     */
    public static void main(String[] args) {
        ResourceManager.insertResourceRoot(megan.resources.Resources.class);
        try {
            PeakMemoryUsageMonitor.start();
            var program = new MaltRun();
            ResourceManager.setWarningMissingIcon(false);
			jloda.swing.util.ProgramProperties.setProgramIcons(ResourceManager.getIcons("malt-run16.png", "malt-run32.png", "malt-run48.png"));
			ProgramProperties.setProgramName("MaltRun");
            ProgramProperties.setProgramVersion(Version.SHORT_DESCRIPTION);

            program.run(args);

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
     */
    public void run(final String[] args) throws UsageException, IOException, InvalidKeySpecException, NoSuchAlgorithmException, NoSuchProviderException {
        version = Basic.getVersion(this.getClass());
        var maltOptions = new MaltOptions();
        var alignerOptions = new AlignerOptions();

        // parse commandline options:
        var options = new ArgsOptions(args, this, "Aligns sequences using MALT (MEGAN alignment tool)");
        options.setAuthors("Daniel H. Huson");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2023 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");

        options.comment("Mode:");
        maltOptions.setMode(BlastMode.valueOfIgnoreCase(options.getOptionMandatory("m", "mode", "Program mode", BlastMode.values(), BlastMode.BlastX.toString())));
        alignerOptions.setAlignmentType(options.getOption("at", "alignmentType", "Type of alignment to be performed", AlignerOptions.AlignmentMode.values(), alignerOptions.getAlignmentType().toString()));

        SequenceType querySequenceType = Utilities.getQuerySequenceTypeFromMode(maltOptions.getMode());
        SequenceType referenceSequenceType = Utilities.getReferenceSequenceTypeFromMode(maltOptions.getMode());
        options.comment("Input:");
        List<String> inputFileNames = options.getOptionMandatory("i", "inFile", "Input file(s) containing queries in FastA or FastQ format (gzip or zip ok)", new LinkedList<>());
        String indexDirectory = options.getOptionMandatory("d", "index", "Index directory as generated by MaltBuild", "");

        options.comment("Output:");
        var outputRMAFileNames = options.getOption("o", "output", "Output RMA file(s) or directory or stdout", new LinkedList<>());
        if (outputRMAFileNames.size() > 0 || options.isDoHelp())
            maltOptions.setSaveUnalignedToRMA(options.getOption("iu", "includeUnaligned", "Include unaligned queries in RMA output file", false));

       var outputMatchesFileNames = options.getOption("a", "alignments", "Output alignment file(s) or directory or stdout", new LinkedList<>());
        if (outputMatchesFileNames.size() > 0 || options.isDoHelp()) {
            maltOptions.setMatchOutputFormat(options.getOption("f", "format", "Alignment output format", MaltOptions.MatchOutputFormat.values(), maltOptions.getMatchOutputFormat().toString()));
            maltOptions.setGzipMatches(options.getOption("za", "gzipAlignments", "Compress alignments using gzip", maltOptions.isGzipMatches()));
        }

        if ((maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.SAM && maltOptions.getMode() == BlastMode.BlastN) || options.isDoHelp()) {
            alignerOptions.setSamSoftClipping(options.getOption("ssc", "samSoftClip", "Use soft clipping in SAM files (BlastN mode only)", alignerOptions.isSamSoftClipping()));
        }
        if (maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.SAM || options.isDoHelp()) {
            maltOptions.setSparseSAM(options.getOption("sps", "sparseSAM", "Produce sparse SAM format (smaller, faster, but only suitable for MEGAN)", maltOptions.isSparseSAM()));
        }
        var outputAlignedFileNames = options.getOption("oa", "outAligned", "Aligned reads output file(s) or directory or stdout", new LinkedList<>());
        if (outputAlignedFileNames.size() > 0 || options.isDoHelp()) {
            maltOptions.setGzipAlignedReads(options.getOption("zal", "gzipAligned", "Compress aligned reads output using gzip", maltOptions.isGzipAlignedReads()));
        }
        var outputUnAlignedFileNames = options.getOption("ou", "outUnaligned", "Unaligned reads output file(s) or directory or stdout", new LinkedList<>());
        if (outputUnAlignedFileNames.size() > 0 || options.isDoHelp()) {
            maltOptions.setGzipUnalignedReads(options.getOption("zul", "gzipUnaligned", "Compress unaligned reads output using gzip", maltOptions.isGzipUnalignedReads()));
        }

        options.comment("Performance:");
        maltOptions.setNumberOfThreads(options.getOption("t", "numThreads", "Number of worker threads", Runtime.getRuntime().availableProcessors()));
        var memoryMode = MaltOptions.MemoryMode.valueOf(options.getOption("mem", "memoryMode", "Memory mode", MaltOptions.MemoryMode.values(), MaltOptions.MemoryMode.load.toString()));
        var maxNumberOfSeedShapes = options.getOption("mt", "maxTables", "Set the maximum number of seed tables to use (0=all)", 0);
        maltOptions.setUseReplicateQueryCaching(options.getOption("rqc", "replicateQueryCache", "Cache results for replicated queries", false));

        options.comment("Filter:");
        maltOptions.setMinBitScore(options.getOption("b", "minBitScore", "Minimum bit score", maltOptions.getMinBitScore()));
        maltOptions.setMaxExpected(options.getOption("e", "maxExpected", "Maximum expected score", maltOptions.getMaxExpected()));
        maltOptions.setMinProportionIdentity(options.getOption("id", "minPercentIdentity", "Minimum percent identity", 100 * maltOptions.getMinProportionIdentity()) / 100.0);
        maltOptions.setMaxAlignmentsPerQuery(options.getOption("mq", "maxAlignmentsPerQuery", "Maximum number of alignments per query", maltOptions.getMaxAlignmentsPerQuery()));
        maltOptions.setMaxAlignmentsPerReference(options.getOption("mrf", "maxAlignmentsPerRef", "Maximum number of (non-overlapping) alignments per reference", maltOptions.getMaxAlignmentsPerReference()));

        if ((maltOptions.getMode() == BlastMode.BlastN || options.isDoHelp())) {
            options.comment("BlastN parameters:");
            alignerOptions.setMatchScore(options.getOption("ma", "matchScore", "Match score", alignerOptions.getMatchScore()));
            alignerOptions.setMismatchScore(options.getOption("mm", "mismatchScore", "Mismatch score", alignerOptions.getMismatchScore()));
            alignerOptions.setLambda(options.getOption("la", "setLambda", "Parameter Lambda for BLASTN statistics", alignerOptions.getLambda()));
            alignerOptions.setK(options.getOption("K", "setK", "Parameter K for BLASTN statistics", (float) alignerOptions.getK()));
        }

        String nameOfProteinScoringMatrix = null;
        if (maltOptions.getMode() == BlastMode.BlastP || maltOptions.getMode() == BlastMode.BlastX || options.isDoHelp()) {
            options.comment("BlastP and BlastX parameters:");
            nameOfProteinScoringMatrix = options.getOption("psm", "subMatrix", "Protein substitution matrix to use", ProteinScoringMatrix.ScoringScheme.values(), ProteinScoringMatrix.ScoringScheme.BLOSUM62.toString());
        }

        if (querySequenceType == SequenceType.DNA || options.isDoHelp()) {
            options.comment("DNA query parameters:");
            maltOptions.setDoReverse(!options.getOption("fo", "forwardOnly", "Align query forward strand only", false));
            maltOptions.setDoForward(!options.getOption("ro", "reverseOnly", "Align query reverse strand only", false));
        }

        options.comment("LCA parameters:");
        var cNames = (options.isDoHelp() ? ClassificationManager.getAllSupportedClassifications().toArray(new String[0]) : MappingManager.determineAvailableMappings(indexDirectory));

        maltOptions.setTopPercentLCA(options.getOption("top", "topPercent", "Top percent value for LCA algorithm", maltOptions.getTopPercentLCA()));
        maltOptions.setMinSupportPercentLCA(options.getOption("supp", "minSupportPercent", "Min support value for LCA algorithm as a percent of assigned reads (0==off)", maltOptions.getMinSupportPercentLCA()));
        maltOptions.setMinSupportLCA(options.getOption("sup", "minSupport", "Min support value for LCA algorithm (overrides --minSupportPercent)", 0));
        if (maltOptions.getMinSupportLCA() == 0) {
            maltOptions.setMinSupportLCA(1);
        } else if (maltOptions.getMinSupportLCA() > 0) {
            maltOptions.setMinSupportPercentLCA(0); // if user sets minSupport,then turn of minSupportPercentLCA
            if (options.isVerbose())
                System.err.println("\t(--minSupportPercent: overridden, set to 0)");
        }
        maltOptions.setMinPercentIdentityLCA(options.getOption("mpi", "minPercentIdentityLCA", "Min percent identity used by LCA algorithm", maltOptions.getMinPercentIdentityLCA()));

        maltOptions.setUsePercentIdentityFilterLCA(options.getOption("mif", "useMinPercentIdentityFilterLCA", "Use percent identity assignment filter", maltOptions.isUsePercentIdentityFilterLCA()));

        maltOptions.setUseWeightedLCA(options.getOption("-wlca", "weightedLCA", "Use the weighted LCA for taxonomic assignment", false));
        if (options.isDoHelp() || maltOptions.isUseWeightedLCA())
            maltOptions.setLcaCoveragePercent(options.getOption("-lcp", "lcaCoveragePercent", "Set the percent for the LCA to cover", Document.DEFAULT_LCA_COVERAGE_PERCENT_SHORT_READS));
        if (!maltOptions.isUseWeightedLCA())
            maltOptions.setLcaCoveragePercent(Document.DEFAULT_LCA_COVERAGE_PERCENT_SHORT_READS);

        ReadMagnitudeParser.setEnabled(options.getOption("mag", "magnitudes", "Reads have magnitudes (to be used in taxonomic or functional analysis)", false));

        maltOptions.setContaminantsFile(options.getOption("-cf", "conFile", "File of contaminant taxa (one Id or name per line)", ""));

        options.comment("Heuristics:");
        maltOptions.setMaxSeedsPerOffsetPerFrame(options.getOption("spf", "maxSeedsPerFrame", "Maximum number of seed matches per offset per read frame", maltOptions.getMaxSeedsPerOffsetPerFrame()));
        maltOptions.setMaxSeedsPerReference(options.getOption("spr", "maxSeedsPerRef", "Maximum number of seed matches per read and reference", maltOptions.getMaxSeedsPerReference()));
        maltOptions.setShift(options.getOption("sh", "seedShift", "Seed shift", maltOptions.getShift()));

        options.comment("Banded alignment parameters:");
        alignerOptions.setGapOpenPenalty(options.getOption("go", "gapOpen", "Gap open penalty", referenceSequenceType == SequenceType.DNA ? 7 : 11));
        alignerOptions.setGapExtensionPenalty(options.getOption("ge", "gapExtend", "Gap extension penalty", referenceSequenceType == SequenceType.DNA ? 3 : 1));
        alignerOptions.setBand(options.getOption("bd", "band", "Band width/2 for banded alignment", alignerOptions.getBand()));

        options.comment(ArgsOptions.OTHER);
        int replicateQueryCacheBits = options.getOption("rqcb", "replicateQueryCacheBits", "Bits used for caching replicate queries (size is then 2^bits)", 20);
        final boolean showAPart = options.getOption("xP", "xPart", "Show part of the table in human readable form for debugging", false);

        options.done();
        Basic.setDebugMode(options.isVerbose());

		maltOptions.setCommandLine(StringUtils.toString(args, " "));

        // END OF OPTIONS

        if (replicateQueryCacheBits < 10 || replicateQueryCacheBits > 31)
            throw new IOException("replicateQueryCacheBits: supported range is 10-31");

        // make sure that the index contains the correct type of sequences:
        {
            SequenceType indexSequencesType = ReferencesHashTableAccess.getIndexSequenceType(indexDirectory);
            if (referenceSequenceType != indexSequencesType)
                throw new IOException("--mode " + maltOptions.getMode() + " not compatible with index containing sequences of type: " + indexSequencesType);
        }

        if (querySequenceType == SequenceType.Protein) {
            maltOptions.setQueryAlphabet(ProteinAlphabet.getInstance());
        } else if (querySequenceType == SequenceType.DNA) {
            maltOptions.setQueryAlphabet(DNA5.getInstance());
        } else
            throw new UsageException("Undefined query sequence type: " + querySequenceType);

        if (referenceSequenceType == SequenceType.Protein) {
            alignerOptions.setScoringMatrix(ProteinScoringMatrix.create(nameOfProteinScoringMatrix));
            alignerOptions.setReferenceIsDNA(false);
            alignerOptions.setLambdaAndK(BlastStatisticsHelper.lookupLambdaAndK(nameOfProteinScoringMatrix, alignerOptions.getGapOpenPenalty(), alignerOptions.getGapExtensionPenalty()));
        } else if (referenceSequenceType == SequenceType.DNA) {
            alignerOptions.setScoringMatrix(new DNAScoringMatrix(alignerOptions.getMatchScore(), alignerOptions.getMismatchScore()));
            alignerOptions.setReferenceIsDNA(true);
        } else
            throw new UsageException("Undefined reference sequence type: " + referenceSequenceType);

        // check consistency of all options:
        if (inputFileNames.size() == 0)
            throw new UsageException("You must specify at least one input file");
        Utilities.checkFileExists(new File(inputFileNames.iterator().next()));

        for (var aName : outputRMAFileNames) {
            if (outputAlignedFileNames.contains(aName))
                throw new UsageException("-a and -o options: Illegal for both to contain the same file name: " + aName);
        }
        for (var aName : outputAlignedFileNames) {
            if (outputRMAFileNames.contains(aName))
                throw new UsageException("-a and -o options: Illegal for both to contain the same file name: " + aName);
        }

        if (!maltOptions.isDoForward() && !maltOptions.isDoReverse())
            throw new UsageException("Illegal to specify both --forwardOnly and --reverseOnly");

        Utilities.checkFileExists(new File(indexDirectory));

        try {
            ReferencesHashTableAccess.checkFilesExist(indexDirectory, 0);
        } catch (IOException ex) {
            throw new IOException("Index '" + indexDirectory + "' appears to be incomplete: " + ex);
        }

        {
            var fileNumber = 0;
            for (var inFile : inputFileNames) {
				if (FileUtils.fileExistsAndIsNonEmpty(inFile)) {
					var rmaOutputFile = getOutputFileName(fileNumber, inputFileNames, outputRMAFileNames, ".rma6", false);
					if (StringUtils.notBlank(rmaOutputFile))
						FileUtils.checkFileWritable(rmaOutputFile, true);
					var matchesOutputFile = getOutputFileName(fileNumber, inputFileNames, outputMatchesFileNames, maltOptions.getMatchesOutputSuffix(), maltOptions.isGzipMatches());
					if (StringUtils.notBlank(matchesOutputFile))
						FileUtils.checkFileWritable(matchesOutputFile, true);
					var alignedReadsOutputFile = getOutputFileName(fileNumber, inputFileNames, outputAlignedFileNames, "-aligned.fna", maltOptions.isGzipAlignedReads());
					if (StringUtils.notBlank(alignedReadsOutputFile))
						FileUtils.checkFileWritable(alignedReadsOutputFile, true);
					var unalignedReadsOutputFile = getOutputFileName(fileNumber, inputFileNames, outputUnAlignedFileNames, "-unaligned.fna", maltOptions.isGzipUnalignedReads());
					if (StringUtils.notBlank(unalignedReadsOutputFile))
						FileUtils.checkFileWritable(unalignedReadsOutputFile, true);
				}
            }
        }

        // load the index:
        System.err.println("--- LOADING ---:");
        // load the reference file:
        var referencesDB = new ReferencesDBAccess(memoryMode, new File(indexDirectory, "ref.idx"), new File(indexDirectory, "ref.db"), new File(indexDirectory, "ref.inf"));
        alignerOptions.setReferenceDatabaseLength(referencesDB.getNumberOfLetters());

        var numberOfTables = ReferencesHashTableAccess.determineNumberOfTables(indexDirectory);
        if (maxNumberOfSeedShapes > 0 && maxNumberOfSeedShapes < numberOfTables) {
            System.err.println("Using " + maxNumberOfSeedShapes + " of " + numberOfTables + " available seed shapes");
            numberOfTables = maxNumberOfSeedShapes;
        }

        // load all tables:
        var hashTables = new ReferencesHashTableAccess[numberOfTables];
        for (var t = 0; t < numberOfTables; t++) {
            System.err.println("LOADING table (" + t + ") ...");
            hashTables[t] = new ReferencesHashTableAccess(memoryMode, indexDirectory, t);
            System.err.printf("Table size:%,15d%n", hashTables[t].size());
            if (showAPart)
                hashTables[t].showAPart();
        }
        // table.show();

        // load mapping files, if we are going to generate RMA
        if (outputRMAFileNames.size() > 0) {
            MappingManager.loadMappings(cNames, indexDirectory);
        }

        final GeneItemAccessor geneTableAccess;
        if ((new File(indexDirectory, "aadd.idx")).exists()) {
            geneTableAccess = new GeneItemAccessor(new File(indexDirectory, "aadd.idx"), new File(indexDirectory, "aadd.dbx"));
            maltOptions.setParseHeaders(true);
        } else
            geneTableAccess = null;

        // run alignment for each input file:
        System.err.println("--- ALIGNING ---:");
        if (maltOptions.isUseReplicateQueryCaching())
            AlignmentEngine.activateReplicateQueryCaching(replicateQueryCacheBits);

        {
            int fileNumber = 0;
            for (var inFile : inputFileNames) {
                try {
					if (FileUtils.fileExistsAndIsNonEmpty(inFile)) {
						var rmaOutputFile = getOutputFileName(fileNumber, inputFileNames, outputRMAFileNames, ".rma6", false);
						var matchesOutputFile = getOutputFileName(fileNumber, inputFileNames, outputMatchesFileNames, maltOptions.getMatchesOutputSuffix(), maltOptions.isGzipMatches());
						var alignedReadsOutputFile = getOutputFileName(fileNumber, inputFileNames, outputAlignedFileNames, "-aligned.fna", maltOptions.isGzipAlignedReads());
						var unalignedReadsOutputFile = getOutputFileName(fileNumber, inputFileNames, outputUnAlignedFileNames, "-unaligned.fna", maltOptions.isGzipUnalignedReads());
						launchAlignmentThreads(alignerOptions, maltOptions, inFile, rmaOutputFile, matchesOutputFile,
								alignedReadsOutputFile, unalignedReadsOutputFile, referencesDB, hashTables, geneTableAccess);
					} else {
						System.err.println("File not found: '" + inFile + "', skipped");
					}
                } catch (IOException ex) {
                    System.err.println("Exception for file: '" + inFile + "', skipped (" + ex + ")");

                } finally {
                    fileNumber++;
                }
            }
        }

        // close everything:
        referencesDB.close();
        for (var t = 0; t < numberOfTables; t++) {
            hashTables[t].close();
        }

        AlignmentEngine.reportStats();
        if (inputFileNames.size() > 1) {
            System.err.printf("Number of input files: %10d%n", inputFileNames.size());
            System.err.printf("Total num. of queries: %10d%n", totalReads);
            System.err.printf("Total aligned queries: %10d%n", totalAlignedReads);
            System.err.printf("Total num. alignments: %10d%n", totalAlignments);

        }
    }

    /**
     * run search on file of input sequences
     */
    private void launchAlignmentThreads(final AlignerOptions alignerOptions, final MaltOptions maltOptions, final String infile, final String rmaOutputFile,
                                        final String matchesOutputFile,
                                        final String alignedReadsOutputFile, final String unalignedReadsOutputFile,
                                        final ReferencesDBAccess referencesDB, final ReferencesHashTableAccess[] tables,
                                        final GeneItemAccessor geneTableAccess) throws IOException {

        final FastAReader fastAReader = new FastAReader(infile, maltOptions.getQueryAlphabet(), new ProgressPercentage("+++++ Aligning file: " + infile));

        final String matchesOutputFileUsed;
        final boolean usingTemporarySAMOutputFile;
        if (matchesOutputFile != null && maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.SAM && !maltOptions.isSparseSAM()) {
			matchesOutputFileUsed = FileUtils.getTemporaryFileName(matchesOutputFile);
			usingTemporarySAMOutputFile = true;
        } else {
            matchesOutputFileUsed = matchesOutputFile;
            usingTemporarySAMOutputFile = false;
        }

        var matchesWriter = (matchesOutputFileUsed != null ? new FileWriterRanked(matchesOutputFileUsed, maltOptions.getNumberOfThreads(), 1) : null);
        var rmaWriter = (rmaOutputFile != null ? new RMA6Writer(maltOptions, rmaOutputFile) : null);
        var alignedReadsWriter = (alignedReadsOutputFile != null ? new FileWriterRanked(alignedReadsOutputFile, maltOptions.getNumberOfThreads(), 1) : null);
        var unalignedReadsWriter = (unalignedReadsOutputFile != null ? new FileWriterRanked(unalignedReadsOutputFile, maltOptions.getNumberOfThreads(), 1) : null);

        if (matchesWriter == null && rmaWriter == null && alignedReadsWriter == null && unalignedReadsWriter == null)
            System.err.println("Warning: no output specified");

        if (matchesWriter != null) {
            if (maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.Text)
                matchesWriter.writeFirst(BlastTextHelper.getBlastTextHeader(maltOptions.getMode()));
            else if (maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.SAM && !usingTemporarySAMOutputFile) {
                matchesWriter.writeFirst(SAMHelper.getSAMHeader(maltOptions.getMode(), maltOptions.getCommandLine()));
            }
        }

        var alignmentEngines = new AlignmentEngine[maltOptions.getNumberOfThreads()];

        var executor = Executors.newFixedThreadPool(maltOptions.getNumberOfThreads());
        var countDownLatch = new CountDownLatch(maltOptions.getNumberOfThreads());

        try {
            // launch the worker threads
            for (int thread = 0; thread < maltOptions.getNumberOfThreads(); thread++) {
                final int threadNumber = thread;
                executor.execute(() -> {
                    try {
                        alignmentEngines[threadNumber] = new AlignmentEngine(threadNumber, maltOptions, alignerOptions, referencesDB, tables, fastAReader,
                                matchesWriter, rmaWriter, alignedReadsWriter, unalignedReadsWriter, geneTableAccess);
                        alignmentEngines[threadNumber].runOuterLoop();
                        alignmentEngines[threadNumber].finish();
                    } catch (Exception ex) {
                        Basic.caught(ex);
                        System.exit(1);  // just die...
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            try {
                countDownLatch.await();  // await completion of alignment threads
            } catch (InterruptedException e) {
                Basic.caught(e);
            } finally {
                fastAReader.close();
            }
        } finally {
            // shut down threads:
            executor.shutdownNow();
        }

        if (matchesWriter != null) {
            if (maltOptions.getMatchOutputFormat() == MaltOptions.MatchOutputFormat.Text)
                matchesWriter.writeLast(BlastTextHelper.FILE_FOOTER_BLAST);
            matchesWriter.close();
            System.err.println("Alignments written to file: " + matchesOutputFileUsed);
        }
        if (rmaWriter != null) {
            rmaWriter.close(maltOptions.getContaminantsFile());
            System.err.println("Analysis written to file: " + rmaOutputFile);
        }

        // if using temporary file, prepend @SQ lines, if requested, and sort by query name, if requested
        if (usingTemporarySAMOutputFile) {
			var w = new BufferedWriter(new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(matchesOutputFile)));
            w.write(SAMHelper.getSAMHeader(maltOptions.getMode(), maltOptions.getCommandLine()));

            // prepend SQ lines
            {
                var allIds = new BitSet();
                for (var engine : alignmentEngines) {
                    allIds.or(engine.getAlignedReferenceIds());
                }

                if (allIds.cardinality() > 0) {
                    var progress = new ProgressPercentage("Prepending @SQ lines to SAM file: " + matchesOutputFile, allIds.size());
                    for (var r = allIds.nextSetBit(0); r != -1; r = allIds.nextSetBit(r + 1)) {
						w.write("@SQ\tSN:" + (StringUtils.toString(Utilities.getFirstWordSkipLeadingGreaterSign(referencesDB.getHeader(r)))) + "\tLN:" + referencesDB.getSequenceLength(r));
						w.write('\n');
                        progress.incrementProgress();
                    }
                    progress.close();
                }
            }

            // copy matches
            {
                var it = new FileLineIterator(matchesOutputFileUsed);
                var progress = new ProgressPercentage("Copying from temporary file:", it.getMaximumProgress());

                while (it.hasNext()) {
                    w.write(it.next());
                    w.write("\tRG:Z:1\n");
                    progress.incrementProgress();
                }
                it.close();
                progress.close();
            }

            w.close();
            if (new File(matchesOutputFileUsed).delete())
                System.err.println("Deleted temporary file: " + matchesOutputFileUsed);
        }

        if (alignedReadsWriter != null) {
            // merge all thread-specific taxon profiles. This can be quite major computation...
            alignedReadsWriter.close();
            System.err.println("Aligned reads written to file: " + alignedReadsOutputFile);
        }
        if (unalignedReadsWriter != null) {
            // merge all thread-specific taxon profiles. This can be quite major computation...
            unalignedReadsWriter.close();
            System.err.println("Unaligned reads written to file: " + unalignedReadsOutputFile);
        }

        var countReads = AlignmentEngine.getTotalSequencesProcessed(alignmentEngines);
        totalReads += countReads;
        var countAlignedReads = AlignmentEngine.getTotalSequencesWithAlignments(alignmentEngines);
        totalAlignedReads += countAlignedReads;
        var countAlignments = AlignmentEngine.getTotalAlignments(alignmentEngines);
        totalAlignments += countAlignments;

        System.err.printf("Num. of queries: %10d%n", countReads);
        System.err.printf("Aligned queries: %10d%n", countAlignedReads);
        System.err.printf("Num. alignments: %10d%n", countAlignments);
    }

    /**
     * creates the output file name
     */
    private String getOutputFileName(final int fileNumber, final List<String> inFiles, final List<String> outFiles, final String suffix, final boolean gzip) throws IOException {
        final String fileName;
        if (outFiles.size() == 0)
            fileName = null;
        else if (outFiles.size() == 1) {
			if (outFiles.get(0).equalsIgnoreCase("stdout")) {
				fileName = "stdout";
			} else if (inFiles.size() == 1 && !FileUtils.isDirectory(outFiles.get(0))) {
				var outfileName = outFiles.get(0);
				if (gzip && !outfileName.endsWith(".gz"))
					fileName = outfileName + ".gz";
				else
					fileName = outfileName;
			} else {
				if (!FileUtils.isDirectory(outFiles.get(0)))
					throw new IOException("Specified output location does not exist or is not a directory: " + outFiles.get(0));
				var infile = new File(inFiles.get(fileNumber));
				var nameRoot = FileUtils.getFileNameWithoutPath(inFiles.get(fileNumber)).replaceAll(".gz$", "");
				var outfileName = FileUtils.replaceFileSuffix(nameRoot, suffix);
				var outfile = new File(outFiles.get(0), outfileName);
				if (infile.equals(outfile))
					throw new IOException("Output file equals input file: " + infile);
				if (gzip && !outfile.toString().endsWith(".gz"))
					fileName = outfile.getPath() + ".gz";
				else
					fileName = outfile.getPath();
			}
        } else {
            if (inFiles.size() != outFiles.size())
                throw new IOException("Number of output files=" + outFiles.size() + " must equal 1 or number of input files (" + inFiles.size() + ")");
            if (gzip && !outFiles.get(fileNumber).endsWith(".gz"))
                fileName = outFiles.get(fileNumber) + ".gz";
            else
                fileName = outFiles.get(fileNumber);
        }
        if (fileName != null && !fileName.equalsIgnoreCase("stdout")) {
			FileUtils.checkFileWritable(fileName, true);
        }
        return fileName;
    }
}


