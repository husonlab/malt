package malt.malt2;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.io.BlastTextHelper;
import malt.io.SAMHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Generates the output from MaltRun2
 * <p/>
 * Daniel Huson, 8.2014
 */
public class OutputManager {

    private final String alignmentFile;

    private final Malt2RMA3Writer malt2RMA3Writer;
    private final OutputWriter alignmentWriter;

    private final String alignedQueriesFile;
    private final OutputWriter alignedQueriesWriter;

    private final String unalignedQueriesFile;
    private final OutputWriter unalignedQueriesWriter;

    private long countAlignedQueries;
    private long countUnalignedQueries;
    private long countAlignments;

    /**
     * constructor the manager and open all then named non-null files for writing
     *
     * @param alignmentFile
     * @param alignedQueriesFile
     * @param unalignedQueriesFile
     * @throws IOException
     */
    public OutputManager(final MaltOptions2 maltOptions, final String alignmentFile, final String alignedQueriesFile, final String unalignedQueriesFile) throws IOException {
        this.alignmentFile = alignmentFile;
        this.alignedQueriesFile = alignedQueriesFile;
        this.unalignedQueriesFile = unalignedQueriesFile;

        if (alignmentFile != null && alignmentFile.endsWith(".rma")) {
            malt2RMA3Writer = new Malt2RMA3Writer(maltOptions, alignmentFile);
            alignmentWriter = null;
        } else {
            malt2RMA3Writer = null;
            if (alignmentFile != null) {
                alignmentWriter = new OutputWriter(alignmentFile);
            } else
                alignmentWriter = null;
        }
        if (alignedQueriesFile != null)
            alignedQueriesWriter = new OutputWriter(alignedQueriesFile);
        else
            alignedQueriesWriter = null;
        if (unalignedQueriesFile != null)
            unalignedQueriesWriter = new OutputWriter(unalignedQueriesFile);
        else
            unalignedQueriesWriter = null;
    }

    /**
     * close all the files
     *
     * @throws IOException
     */
    public void close() throws IOException {
        if (malt2RMA3Writer != null) {
            malt2RMA3Writer.close();
        }
        if (alignmentWriter != null)
            alignmentWriter.close();
        if (alignedQueriesWriter != null)
            alignedQueriesWriter.close();
        if (unalignedQueriesWriter != null)
            unalignedQueriesWriter.close();
    }

    /**
     * writes the output
     *
     * @param maltOptions
     * @param queryStore
     * @param matches
     * @throws java.io.IOException
     */
    public void writeAlignments(final MaltOptions2 maltOptions, final QueryStore queryStore, final ReferenceStore refStore, final MatchStore matches, final QueryMatchesCache queryMatchesCache) throws IOException {
        if (malt2RMA3Writer != null) {
            malt2RMA3Writer.processBatch(queryStore, matches);
            countAlignments = malt2RMA3Writer.getTotalMatches();
            countAlignedQueries = malt2RMA3Writer.getTotalAlignedReads();
            countUnalignedQueries = malt2RMA3Writer.getTotalUnalignedReads();
        }

        if (alignmentWriter == null)
            return;

        final ProgressPercentage progress = new ProgressPercentage("Writing file: " + alignmentFile, queryStore.getNumberOfSequences());

        switch (maltOptions.getMatchOutputFormat()) {
            case BlastText: {
                alignmentWriter.write(BlastTextHelper.getBlastTextHeader(maltOptions.getMode()));
                for (int q = 0; q < queryStore.getNumberOfSequences(); q++) {
                    AMatch match = matches.get(q);
                    if (match == null && queryMatchesCache != null) {
                        match = queryMatchesCache.get(queryStore.getOriginalSequence(q));
                    }
                    int add = 0;
                    if (match != null)
                        add = matches.writeBlastText(match, queryStore.getName(q), queryStore.getOriginalQueryLength(q), refStore, alignmentWriter);
                    if (add > 0) {
                        countAlignedQueries++;
                        countAlignments += add;
                    } else
                        countUnalignedQueries++;
                    progress.setProgress(q);
                }
                alignmentWriter.write(BlastTextHelper.FILE_FOOTER_BLAST);
                break;
            }
            case BlastTab: {
                for (int q = 0; q < queryStore.getNumberOfSequences(); q++) {
                    AMatch match = matches.get(q);
                    if (match == null && queryMatchesCache != null) {
                        match = queryMatchesCache.get(queryStore.getOriginalSequence(q));
                    }
                    int add = 0;
                    if (match != null)
                        add = matches.writeSAMOrBlastTab(match, queryStore.getName(q), alignmentWriter);
                    if (add > 0) {
                        countAlignedQueries++;
                        countAlignments += add;
                    } else
                        countUnalignedQueries++;
                    progress.setProgress(q);
                }
                break;
            }
            case RMA:
            case SAM: {
                alignmentWriter.write(SAMHelper.getSAMHeader(maltOptions.getMode(), null));
                for (int q = 0; q < queryStore.getNumberOfSequences(); q++) {
                    AMatch match = matches.get(q);
                    if (match == null && queryMatchesCache != null) {
                        match = queryMatchesCache.get(queryStore.getOriginalSequence(q));
                    }
                    int add = 0;
                    if (match != null)
                        add = matches.writeSAMOrBlastTab(match, queryStore.getName(q), alignmentWriter);
                    if (add > 0) {
                        countAlignedQueries++;
                        countAlignments += add;
                    } else
                        countUnalignedQueries++;
                    progress.setProgress(q);
                }
                break;
            }
        }
        progress.close();
    }

    /**
     * writes aligned and/or unaligned reads
     *
     * @param queryStore
     * @param matches
     * @return queries written
     * @throws IOException
     */
    public void writeAlignedAndUnaligned(final QueryStore queryStore, final MatchStore matches) throws IOException {
        if (alignedQueriesWriter == null && unalignedQueriesWriter == null)
            return;

        final ProgressPercentage progress = new ProgressPercentage(
                "Writing " + (alignedQueriesFile != null ? alignedQueriesFile : "") + " " + (unalignedQueriesFile != null ? unalignedQueriesFile : ""),
                queryStore.getNumberOfSequences());

        for (int q = 0; q < queryStore.getNumberOfSequences(); q++) {
            if (matches.get(q) != null) {
                if (alignedQueriesWriter != null) {
                    final byte[] header = queryStore.getHeader(q);
                    for (byte b : header) alignedQueriesWriter.write((char) b);
                    alignedQueriesWriter.write('\n');
                    final byte[] sequence = queryStore.getOriginalSequence(q);
                    for (byte b : sequence) alignedQueriesWriter.write((char) b);
                    alignedQueriesWriter.write('\n');
                }
            } else {
                if (alignedQueriesWriter != null) {
                    final byte[] header = queryStore.getHeader(q);
                    for (byte b : header) alignedQueriesWriter.write((char) b);
                    alignedQueriesWriter.write('\n');
                    final byte[] sequence = queryStore.getOriginalSequence(q);
                    for (byte b : sequence) alignedQueriesWriter.write((char) b);
                    alignedQueriesWriter.write('\n');
                }
            }
            progress.setProgress(q);
        }
        progress.close();
    }

    /**
     * creates the output file name
     *
     * @param fileNumber
     * @param inFiles
     * @param outFiles
     * @param suffix
     * @return
     * @throws java.io.IOException
     */
    public static String getOutputFileName(final int fileNumber, final List<String> inFiles, final List<String> outFiles, final String suffix, final boolean gzip) throws IOException {
        if (outFiles.size() == 0)
            return null;
        if (outFiles.size() == 1) {
            if (outFiles.get(0).equalsIgnoreCase("STDOUT")) {
                return "STDOUT";
            } else if (inFiles.size() == 1 && !Basic.isDirectory(outFiles.get(0))) {
                String outfileName = outFiles.get(0);
                if (suffix.toLowerCase().endsWith(".rma"))
                    return outfileName;
                if (gzip && !outfileName.endsWith(".gz"))
                    return outfileName + ".gz";
                else
                    return outfileName;
            } else {
                if (!Basic.isDirectory(outFiles.get(0)))
                    throw new IOException("Specified output location does not exist or is not a directory: " + outFiles.get(0));
                File infile = new File(inFiles.get(fileNumber));
                String outfileName = Basic.getFileNameWithoutPath(inFiles.get(fileNumber));
                if (Basic.isZIPorGZIPFile(outfileName))
                    outfileName = Basic.replaceFileSuffix(outfileName, "");
                outfileName = Basic.replaceFileSuffix(outfileName, suffix);
                File outfile = new File(outFiles.get(0), outfileName);
                if (infile.equals(outfile))
                    throw new IOException("Output file equals input file: " + infile);
                if (suffix.toLowerCase().endsWith(".rma"))
                    return outfile.toString();
                if (gzip && !outfile.toString().endsWith(".gz"))
                    return outfile.toString() + ".gz";
                else
                    return outfile.toString();
            }
        } else {
            if (inFiles.size() != outFiles.size())
                throw new IOException("Number of output files=" + outFiles.size() + " must equal 1 or number of input files (" + inFiles.size() + ")");
            if (gzip && !outFiles.get(fileNumber).endsWith(".gz") && !outFiles.get(fileNumber).toLowerCase().endsWith(".rma"))
                return outFiles.get(fileNumber) + ".gz";
            else
                return outFiles.get(fileNumber);
        }
    }

    public long getCountAlignedQueries() {
        return countAlignedQueries;
    }

    public long getCountUnalignedQueries() {
        return countUnalignedQueries;
    }

    public long getCountAlignments() {
        return countAlignments;
    }

    /**
     * gets output stats string
     *
     * @param numberLowComplexity
     * @param cachedQueries
     * @return output stats string
     */
    public String getStatString(long numberLowComplexity, long cachedQueries) {
        final ByteArrayOutputStream outs = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(outs);
        ps.println(String.format("Number queries:    %,13d", (getCountAlignedQueries() + getCountUnalignedQueries())));
        if (numberLowComplexity > 0)
            ps.println(String.format("Low complexity:    %,13d", numberLowComplexity));
        ps.println(String.format("Aligned queries:   %,13d", getCountAlignedQueries()));
        ps.println(String.format("Unalign.queries:   %,13d", getCountUnalignedQueries()));
        if (cachedQueries > 0)
            ps.println(String.format("Cached queries:    %,13d", cachedQueries));
        ps.println(String.format("Num. alignments: %,15d", getCountAlignments()));
        if (malt2RMA3Writer != null) {
            if (malt2RMA3Writer.getNumberOfTaxonomyClasses() > 0)
                ps.println(String.format("Num. taxonomy classes:%,10d", malt2RMA3Writer.getNumberOfTaxonomyClasses()));
            if (malt2RMA3Writer.getNumberOfKeggClasses() > 0)
                ps.println(String.format("Num. KEGG classes:    %,10d", malt2RMA3Writer.getNumberOfKeggClasses()));
            if (malt2RMA3Writer.getNumberOfSeedClasses() > 0)
                ps.println(String.format("Num. SEED classes:    %,10d", malt2RMA3Writer.getNumberOfSeedClasses()));
            if (malt2RMA3Writer.getNumberOfCogClasses() > 0)
                ps.println(String.format("Num. COG classes:     %,10d", malt2RMA3Writer.getNumberOfCogClasses()));
        }
        String result = outs.toString();
        ps.close();
        return result;
    }
}
