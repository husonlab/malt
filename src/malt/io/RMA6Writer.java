/*
 *  RMA6Writer.java Copyright (C) 2019. Daniel H. Huson GPL
 *
 *   (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package malt.io;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.MaltOptions;
import malt.Version;
import malt.data.ReadMatch;
import malt.mapping.Mapping;
import malt.mapping.MappingManager;
import megan.classification.Classification;
import megan.core.ContaminantManager;
import megan.core.Document;
import megan.core.SyncArchiveAndDataTable;
import megan.data.IReadBlock;
import megan.data.IReadBlockIterator;
import megan.io.InputOutputReaderWriter;
import megan.rma6.MatchLineRMA6;
import megan.rma6.RMA6Connector;
import megan.rma6.RMA6FileCreator;

import java.io.IOException;
import java.util.Arrays;

/**
 * Create an RMA6 file from SAM data in Malt
 * <p>
 * Daniel Huson, 6.2015
 */
public class RMA6Writer {
    private final RMA6FileCreator rma6FileCreator;
    private final String rma6File;

    private final boolean parseHeaders;

    private final String[] cNames;

    private final int maxMatchesPerQuery;
    private final MaltOptions maltOptions;

    private final MatchLineRMA6[] matches;

    private final int[][] match2classification2id;

    private byte[] queryText = new byte[10000];
    private byte[] matchesText = new byte[10000];

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
        this.parseHeaders = maltOptions.isParseHeaders();

        maxMatchesPerQuery = maltOptions.getMaxAlignmentsPerQuery();

        cNames = MappingManager.getCNames();
        int taxonMapperIndex = Basic.getIndex(Classification.Taxonomy, Arrays.asList(cNames));

        matches = new MatchLineRMA6[maxMatchesPerQuery];
        for (int i = 0; i < matches.length; i++) {
            matches[i] = new MatchLineRMA6(cNames.length, taxonMapperIndex);
        }

        match2classification2id = new int[maxMatchesPerQuery][cNames.length];

        rma6FileCreator = new RMA6FileCreator(rma6File, true);
        rma6FileCreator.writeHeader(Version.SHORT_DESCRIPTION, maltOptions.getMode(), cNames, false);

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

        final String[] key = new String[cNames.length];
        for (int i = 0; i < cNames.length; i++) {
            key[i] = getKey(cNames[i]);
        }

        // setup matches text:
        int matchesTextLength = 0;
        numberOfMatches = Math.min(maxMatchesPerQuery, numberOfMatches);
        for (int m = 0; m < numberOfMatches; m++) {
            final ReadMatch match = matchesArray[m];
            final byte[] matchText = match.getRMA6Text();

            final int approximateLengthToAdd = matchesTextLength + matchText.length + queryName.length;
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

            final String refHeader = (parseHeaders ? getWordAsString(match.getRMA6Text(), 2) : null);

            for (int i = 0; i < cNames.length; i++) {
                int id = 0;
                if (parseHeaders)
                    id = parseIdInHeader(key[i], refHeader);
                if (id == 0) {
                    Mapping mapping = MappingManager.getMapping(i);
                    if (mapping != null)
                        id = MappingManager.getMapping(i).get(match.getReferenceId());
                }
                match2classification2id[m][i] = id;
                matches[m].setFId(i, id);
            }
        }

        rma6FileCreator.addQuery(queryText, queryTextLength, numberOfMatches, matchesText, matchesTextLength, match2classification2id, 0);
    }

    private int parseIdInHeader(String key, String word) {
        int pos = word.indexOf(key);
        if (pos != -1) {
            if (Basic.isInteger(word.substring(pos + key.length())))
                return Basic.parseInt(word.substring(pos + key.length()));
        }
        return 0;
    }

    /**
     * finish generation of RMA6 file
     *
     * @throws IOException
     * @throws CanceledException
     */
    public void close(String contaminantsFile) throws IOException {
        try {
            System.err.println("Finishing file: " + rma6File);

            rma6FileCreator.endAddingQueries();
            rma6FileCreator.writeClassifications(new String[0], null, null);
            rma6FileCreator.close();

            final boolean pairedReads = maltOptions.isPairedReads();
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
            }

            // we need to run data processor

            final Document doc = new Document();
            doc.setTopPercent(maltOptions.getTopPercentLCA());
            doc.setLcaAlgorithm(maltOptions.isUseWeightedLCA() ? Document.LCAAlgorithm.weighted : Document.LCAAlgorithm.naive);
            doc.setLcaCoveragePercent(maltOptions.getLcaCoveragePercent());
            doc.setMinSupportPercent(maltOptions.getMinSupportPercentLCA());
            doc.setMinSupport(maltOptions.getMinSupportLCA());
            doc.setMaxExpected((float) maltOptions.getMaxExpected());
            doc.setMinScore((float) maltOptions.getMinBitScore());
            doc.setPairedReads(pairedReads);
            doc.setMaxExpected((float) maltOptions.getMaxExpected());
            doc.setMinPercentIdentity(maltOptions.getMinPercentIdentityLCA());
            doc.setUseIdentityFilter(maltOptions.isUsePercentIdentityFilterLCA());
            doc.getActiveViewers().addAll(Arrays.asList(MappingManager.getCNames()));

            doc.setReadAssignmentMode(Document.ReadAssignmentMode.readCount); // todo: make this an option

            if (Basic.fileExistsAndIsNonEmpty(contaminantsFile)) {
                ContaminantManager contaminantManager = new ContaminantManager();
                contaminantManager.read(contaminantsFile);
                doc.getDataTable().setContaminants(contaminantManager.getTaxonIdsString());
            }

            doc.getMeganFile().setFileFromExistingFile(rma6File, false);
            doc.loadMeganFile();
            doc.processReadHits();

            // update and then save auxiliary data:
            final String sampleName = Basic.replaceFileSuffix(Basic.getFileNameWithoutPath(rma6File), "");
            SyncArchiveAndDataTable.syncRecomputedArchive2Summary(doc.getReadAssignmentMode(), sampleName, "LCA", doc.getBlastMode(), doc.getParameterString(), new RMA6Connector(rma6File), doc.getDataTable(), 0);
            doc.saveAuxiliaryData();
        } catch (CanceledException ex) {
            throw new IOException(ex); // this can't happen because ProgressPercent never throws CanceledException
        }
    }

    /**
     * get key
     *
     * @param fName
     * @return key
     */
    private static String getKey(String fName) {
        switch (fName.toLowerCase()) {
            case "interpro2go":
                return "ipr|";
            case "eggnog":
                return "cog|";
            default:
                return fName.toLowerCase() + "|";
        }
    }

    /**
     * get a word as string
     *
     * @param text
     * @param whichWord
     * @return string or null
     */
    private static String getWordAsString(byte[] text, int whichWord) {
        int start = -1;
        whichWord--;
        for (int i = 0; i < text.length; i++) {
            if (Character.isWhitespace(text[i])) {
                if (whichWord > 0) {
                    whichWord--;
                    if (whichWord == 0)
                        start = i;
                } else if (whichWord == 0) {
                    return new String(text, start, i - start);
                }
            }
        }
        if (start >= 0)
            return new String(text, start, text.length - start);
        return null;
    }
}
