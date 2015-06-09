/**
 * Copyright 2015, Daniel Huson
 * <p/>
 * (Some files contain contributions from other authors, who are then mentioned separately)
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package malt.io;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ListOfLongs;
import jloda.util.ProgressPercentage;
import malt.MaltOptions;
import malt.Version;
import malt.data.ReadMatch;
import malt.mapping.MappingHelper;
import megan.algorithms.MinSupportFilter;
import megan.classification.Classification;
import megan.core.Document;
import megan.core.SampleAttributeTable;
import megan.core.SyncArchiveAndDataTable;
import megan.data.IReadBlock;
import megan.data.IReadBlockIterator;
import megan.io.InputOutputReaderWriter;
import megan.rma6.AnalyzerRMA6;
import megan.rma6.MatchLineRMA6;
import megan.rma6.RMA6Connector;
import megan.rma6.RMA6FileCreator;
import megan.util.ReadMagnitudeParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Create an RMA6 file from SAM data in Malt
 *
 * Daniel Huson, 6.2015
 */
public class RMA6Writer {
    private final RMA6FileCreator rma6FileCreator;
    private final AnalyzerRMA6 rma6Analyzer;
    private final String rma6File;

    private final String[] fNames;

    private final int maxMatchesPerQuery;
    private final float minSupportPercent;
    private int minSupport; // not final, can be changed if min support percent is set
    private final MaltOptions maltOptions;

    private final MatchLineRMA6[] matches;

    final int[][] match2classification2id;

    private byte[] queryText = new byte[10000];
    private byte[] matchesText = new byte[10000];

    private final int taxonMapperIndex;

    private final Map<Integer, ListOfLongs>[] fName2ClassId2Location;
    private final Map<Integer, Integer>[] fName2ClassId2Weight;

    private boolean pairedReads = false;
    private boolean hasMagnitudes = false;

    long totalTaxWeight = 0;
    private int totalAlignedReads;
    private int totalUnalignedReads;
    private long totalMatches;

    /**
     * constructor
     *
     * @param maltOptions
     * @param rma6File
     * @throws IOException
     */
    public RMA6Writer(final MaltOptions maltOptions, String rma6File) throws IOException {
        System.err.println("Starting file: " + rma6File);
        this.maltOptions = maltOptions;
        this.rma6File = rma6File;

        rma6Analyzer = new AnalyzerRMA6((float) maltOptions.getMinBitScore(), (float) maltOptions.getMaxExpected(), (float) maltOptions.getTopPercentLCA());

        maxMatchesPerQuery = maltOptions.getMaxAlignmentsPerQuery();

        minSupportPercent = maltOptions.getMinSupportPercentLCA();
        minSupport = maltOptions.getMinSupportLCA();

        pairedReads = maltOptions.isPairedReads();
        hasMagnitudes = maltOptions.isHasMagnitudes();

        fNames = MappingHelper.getFNames();
        taxonMapperIndex = Basic.getIndex(Classification.Taxonomy, Arrays.asList(fNames));

        fName2ClassId2Location = new HashMap[fNames.length];
        fName2ClassId2Weight = new HashMap[fNames.length];
        for (int i = 0; i < fNames.length; i++) {
            fName2ClassId2Location[i] = new HashMap<>(10000);
            fName2ClassId2Weight[i] = new HashMap<>(10000);
        }

        matches = new MatchLineRMA6[maxMatchesPerQuery];
        for (int i = 0; i < matches.length; i++) {
            matches[i] = new MatchLineRMA6(fNames.length, taxonMapperIndex);
        }

        match2classification2id = new int[maxMatchesPerQuery][fNames.length];

        rma6FileCreator = new RMA6FileCreator(rma6File);
        rma6FileCreator.writeHeader(Version.SHORT_DESCRIPTION, maltOptions.getMode(), fNames, false);

        totalAlignedReads = 0;
        totalUnalignedReads = 0;
        totalMatches = 0;

        rma6FileCreator.startAddingQueries();
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
        // setup query text:
        byte[] queryName = Basic.swallowLeadingGreaterSign(Basic.getFirstWord(queryHeader)).getBytes();
        byte[] queryHeaderText = queryHeader.getBytes();
        byte[] querySequenceText = querySequence.getBytes();
        if (queryHeaderText.length + querySequenceText.length + 100 > queryText.length) {
            queryText = new byte[100 + queryHeaderText.length + querySequenceText.length];
        }
        System.arraycopy(queryHeaderText, 0, queryText, 0, queryHeaderText.length);
        int queryTextLength = queryHeaderText.length;
        queryText[queryTextLength++] = '\n';
        System.arraycopy(querySequenceText, 0, queryText, queryTextLength, querySequenceText.length);
        queryTextLength += querySequenceText.length;
        queryText[queryTextLength++] = '\n';


        // setup matches text:
        int matchesTextLength = 0;
        numberOfMatches = Math.min(maxMatchesPerQuery, numberOfMatches);
        for (int m = 0; m < numberOfMatches; m++) {
            final ReadMatch match = matchesArray[m];
            final byte[] matchText = match.getRMA3Text();


            final int approximateLengthToAdd = matchesTextLength + matchesTextLength + queryName.length;
            if (approximateLengthToAdd + 100 > matchesText.length) {
                byte[] tmp = new byte[approximateLengthToAdd + 10000];
                System.arraycopy(matchesText, 0, tmp, 0, matchesTextLength);
                matchesText = tmp;
            }
            System.arraycopy(queryName, 0, matchesText, matchesTextLength, queryName.length);
            matchesTextLength += queryName.length;
            matchesText[matchesTextLength++] = '\t';

            System.arraycopy(matchText, 0, matchesText, matchesTextLength, matchText.length);
            matchesTextLength += matchText.length;
            matchesText[matchesTextLength++] = '\n';


            matches[m].setBitScore(match.getBitScore());
            matches[m].setExpected(match.getExpected());
            matches[m].setPercentIdentity(match.getPercentIdentity());
            for (int i = 0; i < fNames.length; i++) {
                final int id = MappingHelper.getMapping(i).get(match.getReferenceId());
                match2classification2id[m][i] = id;
                matches[m].setFId(i, id);
            }
        }

        int readWeight = (hasMagnitudes ? ReadMagnitudeParser.parseMagnitude(queryHeader) : 1);

        final long location = rma6FileCreator.addQuery(queryText, queryTextLength, numberOfMatches, matchesText, matchesTextLength, match2classification2id, 0);

        if (numberOfMatches > 0) {
            totalAlignedReads++;
            totalMatches += numberOfMatches;
        } else
            totalUnalignedReads++;

            for (int i = 0; i < fNames.length; i++) {
                final int id;
                if (i == taxonMapperIndex)
                    id = rma6Analyzer.getLCA(matches, numberOfMatches);
                else
                    id = rma6Analyzer.getId(i, matches, numberOfMatches);
                addTo(fName2ClassId2Location[i], id, location);
                final Integer totalWeight = fName2ClassId2Weight[i].get(id);
                fName2ClassId2Weight[i].put(id, totalWeight == null ? readWeight : totalWeight + readWeight);
                if (i == taxonMapperIndex)
                    totalTaxWeight += readWeight;
            }
    }

    /**
     * finish generation of RMA3 file
     *
     * @throws IOException
     * @throws CanceledException
     */
    public void close() throws IOException {
        System.err.println("Finishing file: "+rma6File);

        rma6FileCreator.endAddingQueries();

        try
        {
            int minSupport = this.minSupport;
            if (minSupportPercent > 0) {
                minSupport = (int) Math.max(1, (minSupportPercent / 100.0) * totalTaxWeight);
            }

            if (minSupport > 1) // apply min-support filter
            {
                final ProgressPercentage progress = new ProgressPercentage("Applying min-support filter");
                final MinSupportFilter minSupportFilter = new MinSupportFilter(fName2ClassId2Weight[taxonMapperIndex], minSupport, progress);
                final Map<Integer, Integer> old2new = minSupportFilter.apply(); // computes mapping of all ids to new ids.
                final Map<Integer, ListOfLongs> tax2Location = fName2ClassId2Location[taxonMapperIndex];

                for (Integer oldId : old2new.keySet()) {
                    final ListOfLongs oldList = tax2Location.get(oldId);
                    if (oldList != null) {
                        final Integer newId = old2new.get(oldId);
                        final ListOfLongs newList = tax2Location.get(newId);
                        if (newList == null)
                            tax2Location.put(newId, oldList);
                        else
                            newList.addAll(oldList);
                        tax2Location.keySet().remove(oldId);
                    }
                }
                progress.close();
                System.err.println(String.format("Min-supp. changes:%,12d", old2new.size()));
            }
            System.err.println(String.format("Total reads:  %,16d", totalAlignedReads + totalUnalignedReads));
            System.err.println(String.format("Total matches:%,16d ", totalMatches));

            rma6FileCreator.writeClassifications(fNames, fName2ClassId2Location, fName2ClassId2Weight);

            String userState = getHeader(rma6FileCreator.getHeaderSectionRMA6().getCreator(),
                    rma6FileCreator.getHeaderSectionRMA6().getCreationDate(), rma6File,
                    totalAlignedReads + totalUnalignedReads, maltOptions.getMinBitScore(),
                    maltOptions.getMaxExpected(), maltOptions.getTopPercentLCA(), maltOptions.getMinSupportPercentLCA(), minSupport);

            Map<String, byte[]> label2data = new HashMap<>();
            label2data.put(SampleAttributeTable.USER_STATE, userState.getBytes());
            rma6FileCreator.writeAuxBlocks(label2data);

            rma6FileCreator.close();

            final Document doc;
            if (pairedReads) { // update paired reads info and then run dataprocessor
                long count = 0;
                try (InputOutputReaderWriter raf = new InputOutputReaderWriter(rma6File, "rw");
                     IReadBlockIterator it = (new RMA6Connector(rma6File)).getAllReadsIterator(0, 1000, false, false)) {
                    final ProgressPercentage progress = new ProgressPercentage("Linking paired reads");
                    progress.setProgress(0);
                    progress.setProgress(it.getMaximumProgress());

                    while (it.hasNext()) {
                        final IReadBlock readBlock = it.next();
                        if (readBlock.getMateUId() > 0) {
                            if (readBlock.getMateUId() > readBlock.getUId())
                                throw new IOException("Mate uid=" + readBlock.getMateUId() + ": too big");
                            raf.seek(readBlock.getMateUId());
                            raf.writeLong(readBlock.getUId());
                            count++;
                        }
                        progress.setProgress(it.getProgress());
                    }
                    progress.close();
                    System.err.println(String.format("Number of pairs:%,14d", count));
                }

                // we need to run data processor to take the paired reads into account
                doc = new Document();
                doc.getMeganFile().setFileFromExistingFile(rma6File, false);
                doc.loadMeganFile();
                doc.processReadHits();
        } else {
                doc = new Document();
                doc.getMeganFile().setFileFromExistingFile(rma6File, false);
                doc.loadMeganFile();
        }

            // update and then save auxiliary data:
            final String sampleName = Basic.replaceFileSuffix(Basic.getFileNameWithoutPath(rma6File), "");
            SyncArchiveAndDataTable.syncRecomputedArchive2Summary(sampleName, "LCA", doc.getParameterString(), new RMA6Connector(rma6File), doc.getDataTable(), 0);
            doc.saveAuxiliaryData();
        } catch (CanceledException ex) {
            throw new IOException(ex); // this can't happen because ProgressPercent never throws CanceledException
        }
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
    private String getHeader(final String creator, final long creationDate, final String fileName, final int numberOfQueries, final double minScore, final double maxExpected,
                            final double topPercent, final double minSupportPercent, final int minSupport) {
        return "@MEGAN6\n"
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
}
