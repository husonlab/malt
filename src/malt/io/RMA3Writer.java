package malt.io;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ListOfLongs;
import jloda.util.ProgressPercentage;
import malt.IMaltOptions;
import malt.Version;
import malt.data.ReadMatch;
import malt.mapping.MappingHelper;
import megan.algorithms.MinSupportFilter;
import megan.classification.IdMapper;
import megan.core.ClassificationType;
import megan.core.SampleAttributeTable;
import megan.data.TextStoragePolicy;
import megan.io.OutputWriter;
import megan.rma3.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Create an RMA3 file from SAM data in Malt2
 * Created by huson on 5/13/14.
 */
public class RMA3Writer {
    public static boolean debug = false;

    private final FileFooterRMA3 fileFooter;

    private final MatchFooterRMA3 matchesFooter;

    private final ClassificationsFooterRMA3 classificationsFooter;

    private final AuxBlocksFooterRMA3 auxFooter;

    private final OutputWriter writer;

    private final AnalyzerRMA3 rma3Analyzer;
    private final float minSupportPercent;
    private int minSupport; // not final, can be changed if min support percent is set
    private final IMaltOptions maltOptions;

    private final boolean doTaxonomy;
    private final boolean doKegg;
    private final boolean doSeed;
    private final boolean doCog;

    private final ReadLineRMA3 readLine = new ReadLineRMA3(TextStoragePolicy.Embed, true);
    private final MatchLineRMA3[] matches;

    private final Map<Integer, ListOfLongs> tax2Location = new HashMap<>(1000);
    private final Map<Integer, ListOfLongs> kegg2Location = new HashMap<>(1000);
    private final Map<Integer, ListOfLongs> seed2Location = new HashMap<>(1000);
    private final Map<Integer, ListOfLongs> cog2Location = new HashMap<>(1000);

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
     * @throws IOException
     */
    public RMA3Writer(final IMaltOptions maltOptions, String rma3FileName) throws IOException {
        this.rma3FileName = rma3FileName;
        this.maltOptions = maltOptions;

        writer = new OutputWriter(new File(rma3FileName));
        System.err.println("Creating file: " + rma3FileName);

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
     * process the matches associated with a given query.
     * This is used in malt1
     *
     * @param queryHeader
     * @param matchesArray
     * @param numberOfMatches
     * @throws IOException
     */
    public synchronized void processMatches(String queryHeader, String querySequence, ReadMatch[] matchesArray, int numberOfMatches) throws IOException {
        final long location = writer.getPosition();
        readLine.setReadUid(location);
        readLine.setText(queryHeader + "\n" + querySequence + "\n");

        int countMatches = 0;
        String firstSAMLineForCurrentRead = null;
        for (int i = 0; i < numberOfMatches; i++) {
            final ReadMatch match = matchesArray[i];
            final MatchLineRMA3 matchLine = matches[i];
            if (i == 0) {
                firstSAMLineForCurrentRead = Basic.getFirstWord(queryHeader) + "\t" + Basic.toString(match.getRMA3Text());
                matchLine.setText(firstSAMLineForCurrentRead);
            } else
                matchLine.setText(SAMCompress.deflate(firstSAMLineForCurrentRead, Basic.getFirstWord(queryHeader) + "\t" + Basic.toString(match.getRMA3Text()))); // deflate SAM line before saving

            matchLine.setExpected(match.getExpected());
            matchLine.setBitScore((int) match.getBitScore());
            matchLine.setPercentId(match.getPercentIdentity());

            if (doTaxonomy)
                matchLine.setTaxId(MappingHelper.getTaxonMapping().get(match.getReferenceId()));

            if (doKegg)
                matchLine.setKeggId(MappingHelper.getKeggMapping().get(match.getReferenceId()));

            if (doSeed)
                matchLine.setSeedId(MappingHelper.getSeedMapping().get(match.getReferenceId()));

            if (doCog)
                matchLine.setCogId(MappingHelper.getCogMapping().get(match.getReferenceId()));
            if (countMatches++ > matchesFooter.getMaxMatchesPerRead()) {
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
        } else {
            if (doTaxonomy)
                addTo(tax2Location, IdMapper.NOHITS_ID, location);
            if (doKegg)
                addTo(kegg2Location, IdMapper.NOHITS_ID, location);
            if (doSeed)
                addTo(seed2Location, IdMapper.NOHITS_ID, location);
            if (doCog)
                addTo(cog2Location, IdMapper.NOHITS_ID, location);

            totalUnalignedReads++;
        }

        readLine.setNumberOfMatches(countMatches);
        readLine.write(writer);

        for (int i = 0; i < countMatches; i++) {
            matches[i].write(writer);
            if (debug)
                System.err.println("matches[" + i + "]:\n" + matches[i].toString());
        }
    }

    /**
     * finish generation of RMA3 file
     *
     * @throws IOException
     * @throws CanceledException
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

        final String userState = getHeader(fileFooter.getCreator(), fileFooter.getCreationDate(), rma3FileName, totalAlignedReads + totalUnalignedReads, maltOptions.getMinBitScore(),
                maltOptions.getMaxExpected(), maltOptions.getTopPercentLCA(), maltOptions.getMinSupportPercentLCA(), minSupport);

        if (debug)
            System.err.println(userState);

        Map<String, byte[]> label2data = new HashMap<>();
        label2data.put(SampleAttributeTable.USER_STATE, userState.getBytes());
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
        return "@MEGAN4\n"
                + "@Creator\t" + creator + "\n"
                + "@CreationDate\t" + creationDate + "\n"
                + "@ContentType\tSummary4\n"
                + "@Names\t" + fileName + "\n"
                + "@Uids\t" + creationDate + "\n"
                + "@Sizes\t" + numberOfQueries + "\n"
                + "@TotalReads\t" + numberOfQueries + "\n"
                + "@Algorithm\tTaxonomy\tLCA\n"
                + "@Parameters\tminScore=" + minScore + "\tmaxExpected=" + maxExpected + "\ttopPercent=" + topPercent
                + (minSupportPercent > 0 ? ("\tminSupportPercent=" + (float) minSupportPercent) : "") + "\tminSupport=" + minSupport + "\n";
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