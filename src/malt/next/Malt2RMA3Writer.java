package malt.next;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ListOfLongs;
import jloda.util.ProgressPercentage;
import malt.Version;
import malt.malt2.AnalyzerRMA3;
import malt.mapping.MappingHelper;
import megan.algorithms.MinSupportFilter;
import megan.core.ClassificationType;
import megan.core.SampleAttributeTable;
import megan.data.TextStoragePolicy;
import megan.io.OutputWriter;
import megan.parsers.blast.BlastMode;
import megan.parsers.sam.SAMMatch;
import megan.rma3.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Create an RMA3 file from SAM data in Malt2
 * Created by huson on 5/13/14.
 */
public class Malt2RMA3Writer {
    public static boolean debug = false;

    private final FileFooterRMA3 fileFooter;

    private final MatchFooterRMA3 matchesFooter;

    private final ClassificationsFooterRMA3 classificationsFooter;

    private final AuxBlocksFooterRMA3 auxFooter;

    private final OutputWriter writer;

    private final AnalyzerRMA3 rma3Analyzer;
    private final float minSupportPercent;
    private int minSupport; // not final, can be changed if min support percent is set
    private final MaltOptions2 maltOptions;

    private final boolean doTaxonomy;
    private final boolean doKegg;
    private final boolean doSeed;
    private final boolean doCog;

    private final ReadLineRMA3 readLine;
    private final MatchLineRMA3[] matches;

    private final Map<Integer, ListOfLongs> tax2Location = new HashMap<Integer, ListOfLongs>(1000);
    private final Map<Integer, ListOfLongs> kegg2Location = new HashMap<Integer, ListOfLongs>(1000);
    private final Map<Integer, ListOfLongs> seed2Location = new HashMap<Integer, ListOfLongs>(1000);
    private final Map<Integer, ListOfLongs> cog2Location = new HashMap<Integer, ListOfLongs>(1000);

    private final String rma3FileName;
    private int totalAlignedReads;
    private int totalUnalignedReads;
    private long totalMatches;

    private int numberOfTaxonomyClasses;
    private int numberOfKeggClasses;
    private int numberOfSeedClasses;
    private int numberOfCogClasses;

    /**
     * constructor
     *
     * @param maltOptions
     * @param rma3FileName
     * @throws java.io.IOException
     */
    public Malt2RMA3Writer(final MaltOptions2 maltOptions, String rma3FileName, boolean hasMagnitudes) throws IOException {
        readLine = new ReadLineRMA3(TextStoragePolicy.Embed, hasMagnitudes);
        this.rma3FileName = rma3FileName;
        this.maltOptions = maltOptions;

        writer = new OutputWriter(new File(rma3FileName));

        minSupportPercent = maltOptions.getMinSupportPercentLCA();
        minSupport = maltOptions.getMinSupportLCA();

        doTaxonomy = MappingHelper.getTaxonMapping() != null;
        doKegg = MappingHelper.getKeggMapping() != null;
        doSeed = MappingHelper.getSeedMapping() != null;
        doCog = MappingHelper.getCogMapping() != null;


        FileHeaderRMA3 fileHeader = new FileHeaderRMA3();
        fileHeader.setCreator(Version.SHORT_DESCRIPTION);
        fileHeader.setCreationDate(System.currentTimeMillis());
        fileHeader.write(writer);

        fileFooter = new FileFooterRMA3();
        fileFooter.setCreator(Version.SHORT_DESCRIPTION);
        fileFooter.setCreationDate(System.currentTimeMillis());

        fileFooter.setAlignmentFile("");
        fileFooter.setAlignmentFileSize(0);

        fileFooter.setAlignmentFileFormat("SAM");

        fileFooter.setReadsFile("");
        fileFooter.setReadsFileFormat("");
        fileFooter.setReadsFileSize(0);

        fileFooter.setBlastMode(maltOptions.getMode().toString());

        if (debug)
            System.err.println("fileHeader:\n" + fileHeader.toString());

        matchesFooter = new MatchFooterRMA3();
        matchesFooter.setMaxMatchesPerRead(maltOptions.getMaxAlignmentsPerQuery());
        matchesFooter.setUseKegg(doKegg);
        matchesFooter.setUseSeed(doSeed);
        matchesFooter.setUseCog(doCog);

        classificationsFooter = new ClassificationsFooterRMA3(doKegg, doSeed, doCog, false);

        auxFooter = new AuxBlocksFooterRMA3();

        rma3Analyzer = new AnalyzerRMA3((float) maltOptions.getMinBitScore(), (float) maltOptions.getMaxExpected(), (float) maltOptions.getTopPercentLCA());

        matchesFooter.setReadFormatDef(readLine.getFormatDef());

        matches = new MatchLineRMA3[matchesFooter.getMaxMatchesPerRead()];
        for (int i = 0; i < matches.length; i++) {
            matches[i] = new MatchLineRMA3(TextStoragePolicy.Embed, matchesFooter.isUseKegg(), matchesFooter.isUseSeed(), matchesFooter.isUseCog(), false);
        }
        matchesFooter.setMatchFormatDef(matches[0].getFormatDef());

        fileFooter.setMatchesStart(writer.getPosition());

        totalAlignedReads = 0;
        totalUnalignedReads = 0;
        totalMatches = 0;
    }

    /**
     * process a batch of reads and alignments
     *
     * @param queryStore
     * @param matchStore
     * @throws java.io.IOException
     */
    public void processBatch(final QueryStore queryStore, final MatchStore matchStore) throws IOException {
        final ProgressPercentage progress = new ProgressPercentage("Writing to file: " + rma3FileName, queryStore.getNumberOfSequences());

        final SAMMatch samMatch = new SAMMatch(BlastMode.valueOf(fileFooter.getBlastMode()), null, null);

        String firstSAMLineForCurrentRead = null;

        progress.setMaximum(queryStore.getNumberOfSequences());
        progress.setProgress(0);

        for (int q = 0; q < queryStore.getNumberOfSequences(); q++) {
            if (debug)
                System.err.println("------- Processing query " + q);
            byte[] name = queryStore.getName(q);
            final long location = writer.getPosition();
            readLine.setReadUid(location);
            readLine.setText(Basic.toString(queryStore.getHeader(q), '\n', queryStore.getOriginalSequence(q)));

            int countMatches = 0;
            for (AMatch aMatch = matchStore.get(q); aMatch != null; aMatch = aMatch.getNext()) {
                final String aLine = Basic.toString(name, '\t', aMatch.getText());
                samMatch.parse(aLine);

                final MatchLineRMA3 matchLine = matches[countMatches];
                if (countMatches == 0) {
                    matchLine.setText(aLine);
                    firstSAMLineForCurrentRead = aLine;
                } else
                    matchLine.setText(SAMCompress.deflate(firstSAMLineForCurrentRead, aLine)); // deflate SAM line before saving

                matchLine.setExpected(samMatch.getExpected());
                matchLine.setBitScore(samMatch.getBitScore());
                matchLine.setPercentId(samMatch.getPercentIdentity());

                if (doTaxonomy)
                    matchLine.setTaxId(MappingHelper.getTaxonMapping().get(aMatch.getReferenceId()));

                if (doKegg)
                    matchLine.setKeggId(MappingHelper.getKeggMapping().get(aMatch.getReferenceId()));

                if (doSeed)
                    matchLine.setSeedId(MappingHelper.getSeedMapping().get(aMatch.getReferenceId()));

                if (doCog)
                    matchLine.setCogId(MappingHelper.getCogMapping().get(aMatch.getReferenceId()));
                countMatches++;
                if (countMatches > matchesFooter.getMaxMatchesPerRead()) {
                    int count = countMatches;
                    while (aMatch != null) {
                        aMatch = aMatch.getNext();
                        count++;
                    }
                    System.err.println("Number of matches exceeded: " + count);
                    break;
                }
            }
            if (countMatches > 0) {
                totalMatches += countMatches;

                if (doTaxonomy)
                    addTo(tax2Location, rma3Analyzer.getLCA(matches, countMatches), location);
                if (doKegg)
                    addTo(kegg2Location, rma3Analyzer.getKeggId(matches, countMatches), location);
                if (doSeed)
                    addTo(seed2Location, rma3Analyzer.getSeedId(matches, countMatches), location);
                if (doCog)
                    addTo(cog2Location, rma3Analyzer.getCogId(matches, countMatches), location);

                totalAlignedReads++;
            } else
                totalUnalignedReads++;

            readLine.setNumberOfMatches(countMatches);
            readLine.write(writer);
            if (debug) {
                System.err.println("readLine:\n" + readLine.toString());
                System.err.println("Number of matches: " + countMatches);
            }

            for (int i = 0; i < countMatches; i++) {
                matches[i].write(writer);
                if (debug)
                    System.err.println("matches[" + i + "]:\n" + matches[i].toString());
            }

            progress.setProgress(q);
        }
        progress.close();
    }

    /**
     * finish generation of RMA3 file
     *
     * @throws java.io.IOException
     * @throws jloda.util.CanceledException
     */
    public void close() throws IOException {
        final ProgressPercentage progress = new ProgressPercentage("Closing: " + rma3FileName);

        matchesFooter.setNumberOfMatches(totalMatches);
        matchesFooter.setNumberOfReads(totalAlignedReads + totalUnalignedReads);

        fileFooter.setMatchesFooter(writer.getPosition());

        matchesFooter.write(writer);

        if (debug)
            System.err.println("matchesFooter:\n" + matchesFooter.toString());

        fileFooter.setAlignmentFileSize(0);
        fileFooter.setClassificationsStart(writer.getPosition());

        int countTaxaMoved = 0;
        if (minSupportPercent > 0 || minSupport > 1) // apply min-support filter
        {
            progress.setSubtask("Applying the min-support filter");

            Map<Integer, Integer> taxid2count = new HashMap<Integer, Integer>(2 * tax2Location.size() + 100);

            long assigned = 0;
            for (Integer taxId : tax2Location.keySet()) {
                final int count = tax2Location.get(taxId).size();
                taxid2count.put(taxId, count);
                if (taxId > 0)
                    assigned += count;
            }

            if (minSupportPercent > 0) {
                minSupport = (int) Math.max(1, (minSupportPercent / 100.0) * assigned);
            }

            if (minSupport > 1) {
                MinSupportFilter minSupportFilter = new MinSupportFilter(taxid2count, minSupport, progress);
                try {
                    Map<Integer, Integer> old2new = minSupportFilter.apply(); // computes mapping of all ids to new ids.
                    countTaxaMoved = old2new.size();
                    for (Integer oldId : old2new.keySet()) {
                        ListOfLongs oldList = tax2Location.get(oldId);
                        if (oldList != null) {
                            Integer newId = old2new.get(oldId);
                            ListOfLongs newList = tax2Location.get(newId);
                            if (newList == null)
                                tax2Location.put(newId, oldList);
                            else
                                newList.addAll(oldList);
                            tax2Location.keySet().remove(oldId);
                        }
                    }
                } catch (CanceledException e) {
                    Basic.caught(e);
                }
            }
        }

        ClassificationBlockRMA3 taxonomyClassification = new ClassificationBlockRMA3(ClassificationType.Taxonomy);
        for (int id : tax2Location.keySet())
            taxonomyClassification.setSum(id, tax2Location.get(id).size());

        classificationsFooter.setStart(ClassificationType.Taxonomy, writer.getPosition());
        taxonomyClassification.write(writer, tax2Location);
        classificationsFooter.setEnd(ClassificationType.Taxonomy, writer.getPosition());

        if (doTaxonomy) {
            if (debug)
                System.err.println("Taxonomy:\n" + taxonomyClassification.toString());
            numberOfTaxonomyClasses = taxonomyClassification.getKeySet().size();
        }

        if (doKegg) {
            ClassificationBlockRMA3 classification = new ClassificationBlockRMA3(ClassificationType.KEGG);
            for (int id : kegg2Location.keySet())
                classification.setSum(id, kegg2Location.get(id).size());

            classificationsFooter.setStart(ClassificationType.KEGG, writer.getPosition());
            classification.write(writer, kegg2Location);
            classificationsFooter.setEnd(ClassificationType.KEGG, writer.getPosition());
            if (debug)
                System.err.println("KEGG:\n" + classification.toString());
            numberOfKeggClasses = classification.getKeySet().size();
        }

        if (doSeed) {
            ClassificationBlockRMA3 classification = new ClassificationBlockRMA3(ClassificationType.SEED);
            for (int id : seed2Location.keySet())
                classification.setSum(id, seed2Location.get(id).size());

            classificationsFooter.setStart(ClassificationType.SEED, writer.getPosition());
            classification.write(writer, seed2Location);
            classificationsFooter.setEnd(ClassificationType.SEED, writer.getPosition());
            if (debug)
                System.err.println("SEED:\n" + classification.toString());
            numberOfSeedClasses = classification.getKeySet().size();
        }

        if (doCog) {
            ClassificationBlockRMA3 classification = new ClassificationBlockRMA3(ClassificationType.COG);
            for (int id : cog2Location.keySet())
                classification.setSum(id, cog2Location.get(id).size());

            classificationsFooter.setStart(ClassificationType.COG, writer.getPosition());
            classification.write(writer, cog2Location);
            classificationsFooter.setEnd(ClassificationType.COG, writer.getPosition());
            if (debug)
                System.err.println("COG:\n" + classification.toString());
            numberOfCogClasses = classification.getKeySet().size();
        }

        fileFooter.setClassificationsFooter(writer.getPosition());
        classificationsFooter.write(writer);
        if (debug)
            System.err.println("classificationsFooter:\n" + classificationsFooter.toString());

        fileFooter.setAuxStart(writer.getPosition());

        byte[] userState = getHeader(fileFooter.getCreator(), fileFooter.getCreationDate(), rma3FileName, totalAlignedReads + totalUnalignedReads, maltOptions.getMinBitScore(),
                maltOptions.getMaxExpected(), maltOptions.getTopPercentLCA(), maltOptions.getMinSupportPercentLCA(), minSupport).getBytes();

        Map<String, byte[]> label2data = new HashMap<String, byte[]>();
        label2data.put(SampleAttributeTable.USER_STATE, userState);
        auxFooter.writeAuxBlocks(writer, label2data);

        fileFooter.setAuxFooter(writer.getPosition());
        auxFooter.write(writer);
        if (debug)
            System.err.println("auxFooter:\n" + auxFooter.toString());

        fileFooter.setFileFooter(writer.getPosition());
        fileFooter.write(writer);
        if (debug)
            System.err.println("fileFooter:\n" + fileFooter.toString());
        writer.close();
        progress.close();
        if (countTaxaMoved > 0)
            System.err.println("Min-support filter, taxa moved: " + countTaxaMoved);

    }

    /**
     * add to the appropriate list of longs
     *
     * @param id2Location
     * @param id
     * @param position
     */
    private static void addTo(Map<Integer, ListOfLongs> id2Location, int id, long position) {
        ListOfLongs list = id2Location.get(id);
        if (list == null) {
            list = new ListOfLongs(1000);
            id2Location.put(id, list);
        }
        list.add(position);
    }

    /**
     * gets the aux data header
     *
     * @param creator
     * @param creationDate
     * @param fileName
     * @param numberOfQueries
     * @param minScore
     * @param maxExpected
     * @param topPercent
     * @param minSupport
     * @return header
     */
    public String getHeader(final String creator, final long creationDate, final String fileName, final int numberOfQueries, final double minScore, final double maxExpected,
                            final double topPercent, final double minSupportPercent, final int minSupport) {
        return "@Creator\t" + creator + "\n"
                + "@CreationDate\t" + creationDate + "\n"
                + "@ContentType\tSummary4\n" + "@Names\t" + fileName + "\n"
                + "@Uids\t" + creationDate + "\n" + "@Sizes\t" + numberOfQueries + "\n"
                + "@TotalReads\t" + numberOfQueries + "\n" + "@Algorithm\tTaxonomy\t LCA" + "@Parameters minScore=" + minScore + " maxExpected=" + maxExpected + " topPercent=" + topPercent
                + (minSupportPercent > 0 ? (" minSupportPercent=" + minSupportPercent) : "") + " minSupport=" + minSupport + "\n";
    }

    public int getTotalAlignedReads() {
        return totalAlignedReads;
    }

    public int getTotalUnalignedReads() {
        return totalUnalignedReads;
    }

    public long getTotalMatches() {
        return totalMatches;
    }

    public int getNumberOfTaxonomyClasses() {
        return numberOfTaxonomyClasses;
    }

    public int getNumberOfKeggClasses() {
        return numberOfKeggClasses;
    }

    public int getNumberOfSeedClasses() {
        return numberOfSeedClasses;
    }

    public int getNumberOfCogClasses() {
        return numberOfCogClasses;
    }
}