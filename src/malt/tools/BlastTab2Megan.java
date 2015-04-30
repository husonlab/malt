package malt.tools;

import jloda.util.*;
import megan.access.ClassificationHelper;
import megan.access.ClassificationMapper;
import megan.algorithms.MinSupportAlgorithm;
import megan.algorithms.TaxonAssignmentUsingLCA;
import megan.cogviewer.data.CogData;
import megan.core.Document;
import megan.core.SampleAttributeTable;
import megan.keggviewer.data.KeggData;
import megan.main.MeganProperties;
import megan.mainviewer.data.TaxonomyData;
import megan.mainviewer.data.TaxonomyName2IdMap;
import megan.seedviewer.data.SeedData;

import java.io.*;
import java.util.*;

/**
 * compute MEGAN files from Blast-BlastTab files
 * Daniel Huson, 7.2014
 */
public class BlastTab2Megan {
    /**
     * merge RMA files
     *
     * @param args
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     */
    public static void main(String[] args) {
        try {
            long start = System.currentTimeMillis();
            (new BlastTab2Megan()).run(args);
            System.err.println("Time: " + ((System.currentTimeMillis() - start) / 1000) + "s");
            System.exit(0);
        } catch (Exception ex) {
            Basic.caught(ex);
            System.exit(1);
        }
    }

    /**
     * run
     *
     * @param args
     * @throws jloda.util.UsageException
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    public void run(String[] args) throws UsageException, IOException, ClassNotFoundException, CanceledException {
        ArgsOptions options = new ArgsOptions(args, this, "Create MEGAN file from BLAST-TAB file");

        options.comment("Input");
        String[] blastFiles = options.getOptionMandatory("-i", "in", "Input BLAST TAB file(s)", new String[0]);
        String[] metaDataFiles = options.getOption("-mdf", "metaData", "Files containing metadata to be included in MEGAN file(s)", new String[0]);

        options.comment("Output");
        String[] outputFiles = options.getOptionMandatory("-o", "out", "Output file(s), one for each input file, or a directory", new String[0]);

        options.comment("Parameters");
        float minScore = options.getOption("-ms", "minScore", "Min score", Document.DEFAULT_MINSCORE);
        float maxExpected = options.getOption("-me", "maxExpected", "Max expected", Document.DEFAULT_MAXEXPECTED);
        float topPercent = options.getOption("-top", "topPercent", "Top percent", Document.DEFAULT_TOPPERCENT);
        float minSupportPercent = options.getOption("-supp", "minSupportPercent", "Min support as percent of assigned reads (0==off)", 0);
        int minSupport = options.getOption("-sup", "minSupport", "Min support as absolute number", Document.DEFAULT_MINSUPPORT);
        options.comment("Classifications:");
        boolean doTaxonomy = options.getOption("-pt", "tax", "Do taxonomic profile", true);
        boolean doKegg = options.getOption("-pk", "kegg", "Do KEGG profile", true);
        boolean doSeed = options.getOption("-ps", "seed", "Do SEED profile", true);
        boolean doCog = options.getOption("-pc", "cog", "Do COG profile", true);

        options.comment("Classification support:");
        final String dataDirectory = options.getOptionMandatory("-dd", "dataDir", "Data resource directory (such as malt/data)", "");

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

        options.done();

        final File ncbiTreeFile = Basic.gzippedIfNecessary(new File(dataDirectory, "ncbi.tre"));
        final File ncbiMapFile = Basic.gzippedIfNecessary(new File(dataDirectory, "ncbi.map"));
        final File seedTreeFile = Basic.gzippedIfNecessary(new File(dataDirectory, "seed.tre"));
        final File seedMapFile = Basic.gzippedIfNecessary(new File(dataDirectory, "seed.map"));
        final File cogTreeFile = Basic.gzippedIfNecessary(new File(dataDirectory, "cog.tre"));
        final File cogMapFile = Basic.gzippedIfNecessary(new File(dataDirectory, "cog.map"));
        final File keggTreeFile = Basic.gzippedIfNecessary(new File(dataDirectory, "kegg.tre"));
        final File keggMapFile = Basic.gzippedIfNecessary(new File(dataDirectory, "kegg.map"));

        ProgramProperties.put(MeganProperties.MAX_NUMBER_CORES, "4");

        for (String fileName : blastFiles) {
            Basic.checkFileReadableNonEmpty(fileName);
        }

        for (String fileName : metaDataFiles) {
            Basic.checkFileReadableNonEmpty(fileName);
        }

        if (outputFiles.length == 1) {
            if (blastFiles.length == 1) {
                if ((new File(outputFiles[0]).isDirectory()))
                    outputFiles[0] = (new File(outputFiles[0], Basic.replaceFileSuffix(Basic.getFileNameWithoutPath(Basic.getFileNameWithoutZipOrGZipSuffix(blastFiles[0])), ".rma3"))).getPath();
            } else if (blastFiles.length > 1) {
                if (!(new File(outputFiles[0]).isDirectory()))
                    throw new IOException("Multiple files given, but given single output is not a directory");
                outputFiles = new String[blastFiles.length];
                for (int i = 0; i < blastFiles.length; i++)
                    outputFiles[i] = Basic.replaceFileSuffix(Basic.getFileNameWithoutZipOrGZipSuffix(blastFiles[i]), ".rma3");
            }
        } else // output.length >1
        {
            if (blastFiles.length != outputFiles.length)
                throw new IOException("Number of input and output files do not match");
        }

        if (metaDataFiles.length > 1 && metaDataFiles.length != blastFiles.length) {
            throw new IOException("Number of metadata files (" + metaDataFiles.length + ") doesn't match number of SAM files (" + blastFiles.length + ")");
        }


        // Load all mapping files:
        ClassificationMapper mapper = new ClassificationMapper();
        if (doTaxonomy) {
            TaxonomyData.getName2IdMap().loadFromFile(ncbiMapFile.getPath());
            TaxonomyData.getTree().loadFromFile(ncbiTreeFile.getPath());
            ClassificationHelper.loadTaxonMapping(synonyms2TaxaFile, refSeq2TaxaFile, gi2TaxaFile, mapper);
        }

        if (doSeed && ClassificationHelper.hasAMapping(synonyms2SeedFile, refSeq2SeedFile, gi2SeedFile)) {
            ProgramProperties.put(MeganProperties.SEEDTREEFILE, seedTreeFile);
            ProgramProperties.put(MeganProperties.SEEDMAPFILE, seedMapFile);
            SeedData.setupTree(new ProgressPercentage());
            ClassificationHelper.loadSeedMapping(synonyms2SeedFile, refSeq2SeedFile, gi2SeedFile, mapper);
        } else doSeed = false;

        if (doCog && ClassificationHelper.hasAMapping(synonyms2CogFile, refSeq2CogFile, gi2CogFile)) {
            ProgramProperties.put(MeganProperties.COGTREEFILE, cogTreeFile);
            ProgramProperties.put(MeganProperties.COGMAPFILE, cogMapFile);
            CogData.setupTree(new ProgressPercentage());
            ClassificationHelper.loadCogMapping(synonyms2CogFile, refSeq2CogFile, gi2CogFile, mapper);
        } else
            doCog = false;

        if (doKegg && ClassificationHelper.hasAMapping(synonyms2KeggFile, refSeq2KeggFile, gi2KeggFile)) {
            ProgramProperties.put(MeganProperties.KEGGTREEFILE, keggTreeFile);
            ProgramProperties.put(MeganProperties.KEGGMAPFILE, keggMapFile);
            KeggData.setupTree(new ProgressPercentage());
            ClassificationHelper.loadKeggMapping(synonyms2KeggFile, refSeq2KeggFile, gi2KeggFile, mapper);
        } else
            doKegg = false;

        /**
         * process each set of files:
         */
        for (int i = 0; i < blastFiles.length; i++) {
            System.err.println("Processing BLAST file: " + blastFiles[i]);
            System.err.println("Output file: " + outputFiles[i]);

            byte[] metaData = null;
            if (metaDataFiles.length > 0) {
                try {
                    System.err.println("Saving metadata:");
                    SampleAttributeTable sampleAttributeTable = new SampleAttributeTable();
                    sampleAttributeTable.read(new FileReader(metaDataFiles[Math.min(i, metaDataFiles.length - 1)]),
                            Arrays.asList(Basic.getFileBaseName(Basic.getFileNameWithoutPath(outputFiles[i]))), false);
                    metaData = sampleAttributeTable.getBytes();
                    System.err.println("done");
                } catch (Exception ex) {
                    Basic.caught(ex);
                }
            }

            ProgressListener progressListener = new ProgressPercentage();
            createMEGANFileFromBlastTabFile(blastFiles[i], outputFiles[i], mapper,
                    minScore, maxExpected, topPercent, minSupportPercent, minSupport, doTaxonomy, doKegg, doSeed, doCog, metaData, progressListener);
            progressListener.close();
        }
    }

    /**
     * create a MEGAN file from a BlastTab file
     *
     * @param blastFile
     * @param outputFile
     * @param classificationMapper
     * @param minScore
     * @param maxExpected
     * @param topPercent
     * @param minSupportPercent
     * @param doKegg
     * @param doSeed
     * @param doCog
     * @param progressListener
     */
    private void createMEGANFileFromBlastTabFile(String blastFile, String outputFile, ClassificationMapper classificationMapper,
                                                 float minScore, float maxExpected, float topPercent, float minSupportPercent, int minSupport,
                                                 boolean doTaxonomy, boolean doKegg, boolean doSeed, boolean doCog,
                                                 byte[] metaData,
                                                 ProgressListener progressListener) throws IOException, CanceledException {
        final Map<Integer, Integer> tax2count = new TreeMap<Integer, Integer>();
        final Map<Integer, Integer> kegg2count = new TreeMap<Integer, Integer>();
        final Map<Integer, Integer> seed2count = new TreeMap<Integer, Integer>();
        final Map<Integer, Integer> cog2count = new TreeMap<Integer, Integer>();

        FileInputIterator it = new FileInputIterator(blastFile);
        progressListener.setSubtask(blastFile);
        progressListener.setMaximum(it.getMaximumProgress());
        progressListener.setProgress(0);

        String previousQuery = null;

        int totalReads = 0;
        int totalReadsAssignedToTaxa = 0;

        final float proportionOfBest = (100.0f - topPercent) / 100.0f;
        final Set<Integer> taxIds = new HashSet<Integer>(100);

        // used to list all matches for a give read:
        int[] taxa = new int[1000];
        float[] scores = new float[1000];
        int numberOfTaxa = 0;

        int keggId = 0;
        int seedId = 0;
        int cogId = 0;

        while (it.hasNext()) {
            String[] tokens = it.next().trim().split("\t");
            // SRR072232.240499        gi|253322479|gb|CP001665.1|     64.3    1005    308     24      988     1       2246496 2247476 9e-24   109
            // 0:query-id  1:subject-id  2:percent-id  3:length  4:mismatches  5:gap-open    6:qstart 7:qend 8:sstart  9:send    10:expected 11:bits

            final String queryId = tokens[0];

            if (previousQuery != null) {
                if (!queryId.equals(previousQuery)) {
                    // process previousQuery
                    if (doTaxonomy) {
                        int taxId = applyLCA(topPercent, proportionOfBest, taxa, scores, numberOfTaxa, taxIds);
                        Integer count = tax2count.get(taxId);
                        tax2count.put(taxId, count == null ? 1 : count + 1);
                        if (taxId > 0)
                            totalReadsAssignedToTaxa++;
                        numberOfTaxa = 0;
                    }

                    if (doKegg && keggId != 0) {
                        Integer count = kegg2count.get(keggId);
                        kegg2count.put(keggId, count == null ? 1 : count + 1);
                        keggId = 0;
                    }
                    if (doSeed && seedId != 0) {
                        Integer count = seed2count.get(seedId);
                        seed2count.put(seedId, count == null ? 1 : count + 1);
                        seedId = 0;
                    }
                    if (doCog && cogId != 0) {
                        Integer count = cog2count.get(cogId);
                        cog2count.put(cogId, count == null ? 1 : count + 1);
                        cogId = 0;
                    }

                    previousQuery = queryId;
                    totalReads++;
                }
            } else
                previousQuery = queryId;

            if (tokens.length >= 12) {
                float score = Float.parseFloat(tokens[11]);
                if (score >= minScore) {
                    double expected = Double.parseDouble(tokens[10]);
                    if (expected <= maxExpected) {
                        String refName = tokens[1];

                        if (doTaxonomy) {
                            Integer taxId = classificationMapper.getTaxonIdFromHeaderLine(refName);
                            if (taxId != null && taxId > 0) {
                                if (numberOfTaxa >= taxa.length) { // grow array if necessary
                                    int[] tmp = new int[2 * taxa.length];
                                    System.arraycopy(taxa, 0, tmp, 0, numberOfTaxa);
                                    taxa = tmp;
                                    float[] tmp2 = new float[2 * scores.length];
                                    System.arraycopy(scores, 0, tmp2, 0, numberOfTaxa);
                                    scores = tmp2;
                                }
                                taxa[numberOfTaxa] = taxId;
                                scores[numberOfTaxa] = score;
                                numberOfTaxa++;
                            }
                        }

                        if (doKegg && keggId == 0) {
                            Integer id = classificationMapper.getKeggIdFromHeaderLine(refName);
                            if (id != null && id > 0)
                                keggId = id;
                        }
                        if (doSeed && seedId == 0) {
                            Integer id = classificationMapper.getSeedIdFromHeaderLine(refName);
                            if (id != null && id > 0)
                                seedId = id;
                        }
                        if (doCog && cogId == 0) {
                            Integer id = classificationMapper.getCogIdFromHeaderLine(refName);
                            if (id != null && id > 0)
                                cogId = id;
                        }
                    }
                }
            }
            progressListener.setProgress(it.getProgress());
        }

        // process the last read:
        if (previousQuery != null) {
            // process previousQuery
            if (doTaxonomy) {
                int taxId = applyLCA(topPercent, proportionOfBest, taxa, scores, numberOfTaxa, taxIds);
                Integer count = tax2count.get(taxId);
                tax2count.put(taxId, count == null ? 1 : count + 1);
                if (taxId > 0)
                    totalReadsAssignedToTaxa++;
            }

            if (doKegg && keggId != 0) {
                Integer count = kegg2count.get(keggId);
                kegg2count.put(keggId, count == null ? 1 : count + 1);
            }
            if (doSeed && seedId != 0) {
                Integer count = seed2count.get(seedId);
                seed2count.put(seedId, count == null ? 1 : count + 1);
            }
            if (doCog && cogId != 0) {
                Integer count = cog2count.get(cogId);
                cog2count.put(cogId, count == null ? 1 : count + 1);
            }
            totalReads++;
        }

        it.close();
        progressListener.close();

        // run minsupport filter:
        if (minSupportPercent > 0 || minSupport > 1) {
            if (minSupportPercent > 0) {
                minSupport = (int) (Math.max(1, (minSupportPercent / 100.0) * totalReadsAssignedToTaxa));
                System.err.println("Min support set to: " + minSupport);
            }
            ProgressPercentage progress = new ProgressPercentage("Applying min support filter...");
            MinSupportAlgorithm.apply(tax2count, minSupport, progress);
            progress.close();
        }

        System.err.println("Number of reads: " + totalReads);
        if (doTaxonomy)
            System.err.println("Number of taxon classes: " + tax2count.size());
        if (doKegg)
            System.err.println("Number of KEGG classes:  " + kegg2count.size());
        if (doSeed)
            System.err.println("Number of SEED classes:  " + seed2count.size());
        if (doCog)
            System.err.println("Number of COG classes:   " + cog2count.size());

        saveToFile(outputFile, totalReads, minScore, maxExpected, topPercent, minSupport, doTaxonomy, doKegg, doSeed, doCog,
                tax2count, kegg2count, seed2count, cog2count, metaData);
    }

    /**
     * apply the LCA algorithm
     *
     * @param minScore
     * @param proportionOfBest
     * @param taxa
     * @param scores
     * @param numberOfTaxa
     * @param taxIds
     * @return LCA
     */
    private int applyLCA(float minScore, float proportionOfBest, int[] taxa, float[] scores, int numberOfTaxa, Set<Integer> taxIds) {
        float bestScore = 0;
        for (int i = 0; i < numberOfTaxa; i++) {
            bestScore = Math.max(bestScore, scores[i]);
        }
        float threshold = Math.max(minScore, proportionOfBest * bestScore);

        taxIds.clear();
        for (int i = 0; i < numberOfTaxa; i++) {
            float score = scores[i];
            if (score >= threshold)
                taxIds.add(taxa[i]);
        }
        if (taxIds.size() == 0)
            return TaxonomyName2IdMap.NOHITS_TAXONID;
        else
            return TaxonAssignmentUsingLCA.getLCA(taxIds, true);

    }

    /**
     * save to a MEGAN file
     *
     * @param outputFile
     * @param totalReads
     * @param minScore
     * @param maxExpected
     * @param topPercent
     * @param minSupport
     * @param doTaxonomy
     * @param doKegg
     * @param doSeed
     * @param doCog
     * @param tax2count
     * @param kegg2count
     * @param seed2count
     * @param cog2count
     * @param metaData
     * @throws IOException
     */
    private void saveToFile(String outputFile, long totalReads, float minScore, float maxExpected, float topPercent, int minSupport,
                            boolean doTaxonomy, boolean doKegg, boolean doSeed, boolean doCog,
                            Map<Integer, Integer> tax2count, Map<Integer, Integer> kegg2count, Map<Integer, Integer> seed2count, Map<Integer, Integer> cog2count, byte[] metaData) throws IOException {
        // save to file:
        System.err.println("Writing file: " + outputFile);
        BufferedWriter w = new BufferedWriter(new FileWriter(outputFile));

        w.write("@Creator\tMaltRun (version " + Basic.getVersion(this.getClass()) + ")\n" +
                "@CreationDate\t" + ((new Date()).toString()) + "\n" +
                "@ContentType\tSummary4\n" +
                "@Names\t" + Basic.getFileNameWithoutPath(Basic.replaceFileSuffix(outputFile, "")) + "\n" +
                "@Uids\t" + System.currentTimeMillis() + "\n" +
                "@Sizes\t" + totalReads + "\n" +
                "@TotalReads\t" + totalReads + "\n" +
                "@AdditionalReads\t0\n" +
                "@Collapse\n" +
                "@Algorithm\tTaxonomy\tLCA\n" +
                "@Parameters\tminScore=" + minScore + " maxExpected=" + maxExpected +
                " topPercent=" + topPercent + " minSupport=" + minSupport + "\n");
        if (doTaxonomy) {
            for (int id : tax2count.keySet()) {
                w.write(String.format("TAX\t%d\t%d\n", id, tax2count.get(id)));
            }
        }
        if (doKegg) {
            for (int id : kegg2count.keySet()) {
                w.write(String.format("KEGG\t%d\t%d\n", id, kegg2count.get(id)));
            }
        }
        if (doSeed) {
            for (int id : seed2count.keySet()) {
                w.write(String.format("SEED\t%d\t%d\n", id, seed2count.get(id)));
            }
        }
        if (doCog) {
            for (int id : cog2count.keySet()) {
                w.write(String.format("COG\t%d\t%d\n", id, cog2count.get(id)));
            }
        }
        w.write("END_OF_DATA_TABLE\n");
        if (metaData != null) {
            w.write("SAMPLE_ATTRIBUTES\n");
            w.write(Basic.toString(metaData) + "\n");
        }
        w.close();
    }
}
