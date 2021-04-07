/*
 * Mapping.java Copyright (C) 2021. Daniel H. Huson
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
package malt.mapping;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import malt.data.ISequenceAccessor;
import malt.data.RefIndex2ClassId;
import megan.accessiondb.AccessAccessionMappingDatabase;
import megan.classification.Classification;
import megan.classification.IdParser;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains mapping from Reference indices to classification
 * Daniel Huson, 2.2016
 */
public class Mapping extends RefIndex2ClassId {
    private final static String version = "V1.1";
    private final String fName;

    /**
     * construct a table
     *
     * @param maxIndex
     */
    public Mapping(String fName, int maxIndex) {
        super(maxIndex);
        this.fName = fName;
    }

    /**
     * compute the mapping for the given reference database
     *
     * @param referencesDB
     * @param progress
     */
    public static Mapping create(String fName, ISequenceAccessor referencesDB, IdParser classificationMapper, ProgressListener progress) throws IOException {
        final Mapping mapping = new Mapping(fName, referencesDB.getNumberOfSequences());
        final String tag = Classification.createShortTag(fName);

        progress.setMaximum(referencesDB.getNumberOfSequences());
        progress.setProgress(0);

        for (int i = 0; i < referencesDB.getNumberOfSequences(); i++) {
            String header = Basic.toString(referencesDB.getHeader(i));
            int classId = classificationMapper.getIdFromHeaderLine(header);
            if (classId != 0) {
                mapping.put(i, classId);
                referencesDB.extendHeader(i, tag, classId);
            }
            progress.incrementProgress();
        }
        if (progress instanceof ProgressPercentage)
            progress.close();

        return mapping;
    }

    /**
     * compute the mapping for the given reference database
     *
     * @param referencesDB
     * @param progress
     */
    public static Map<String, Mapping> create(Collection<String> namesToUse, ISequenceAccessor referencesDB, AccessAccessionMappingDatabase mappingDatabase, ProgressListener progress) throws IOException, SQLException {

        final Collection<String> cNames = mappingDatabase.getClassificationNames();
        final int maxIndex = cNames.stream().mapToInt(name -> {
            try {
                return mappingDatabase.getClassificationIndex(name);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                return 0;
            }
        }).max().orElse(-1);

        final Map<String, Mapping> mappings = new HashMap<>();
        final String[] cIndex2Name = new String[maxIndex + 1];
        final String[] tags = new String[maxIndex + 1];

        {
            int c = 0;
            for (String cName : cNames) {
                final int index = mappingDatabase.getClassificationIndex(cName) - 2;

                if (namesToUse.contains(cName)) {
                    mappings.put(cName, new Mapping(cName, referencesDB.getNumberOfSequences()));
                    cIndex2Name[index] = cName;
                    tags[c] = Classification.createShortTag(cName);
                }
                c++;
            }
        }

        progress.setMaximum(referencesDB.getNumberOfSequences());
        progress.setProgress(0);

        final int chunkSize = 10000;
        final String[] accessions = new String[chunkSize];

        for (int offset = 0; offset < referencesDB.getNumberOfSequences(); offset += chunkSize) {

            final int numberInChunk = Math.min(chunkSize, referencesDB.getNumberOfSequences() - offset * chunkSize);
            for (int r = 0; r < numberInChunk; r++) {
                accessions[r] = getFirstWordAccession(referencesDB.getHeader(offset + r));
            }
            final Map<String, int[]> accession2ids = mappingDatabase.getValues(accessions, numberInChunk);
            for (int r = 0; r < numberInChunk; r++) {
                if (accessions[r].length() > 0) {
                    final int[] ids = accession2ids.get(accessions[r]);
                    if (ids != null) {
                        //System.err.println((offset+r)+" -> "+Basic.toString(referencesDB.getHeader(offset + r))+" -> "+accessions[r]);

                        for (int c = 0; c < cIndex2Name.length; c++) {
                            if (cIndex2Name[c] != null) {
                                final int index = ids[c];
                                if (index != 0) {
                                    //System.err.println(cIndex2Name[c]+" -> "+index);
                                    mappings.get(cIndex2Name[c]).put(offset + r, index);
                                    referencesDB.extendHeader(c, tags[c], index);
                                }
                            }
                        }
                    }
                }
            }
            progress.setProgress(offset + numberInChunk);
        }
        return mappings;
    }

    public static String getFirstWordAccession(byte[] bytes) {
        final String aLine = Basic.toString(bytes);
        int a = 0;
        while (a < aLine.length()) {
            if (aLine.charAt(a) == '>' || aLine.charAt(a) == '@' || Character.isWhitespace(aLine.charAt(a)))
                a++;
            else
                break;
        }
        int b = a + 1;
        while (b < aLine.length()) {
            if (Character.isLetterOrDigit(aLine.charAt(b)) || aLine.charAt(b) == '_')
                b++;
            else
                break;
        }
        if (b - a > 4) {
            return aLine.substring(a, b);
        } else
            return "";
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws IOException
     */
    public void save(File file) throws IOException {
        super.save(file, makeMagicNumber(fName));
    }

    /**
     * construct from an existing file
     *
     * @param file
     * @throws IOException
     * @throws CanceledException
     */
    public Mapping(String fName, File file) throws IOException, CanceledException {
        super(file, makeMagicNumber(fName));
        this.fName = fName;
    }

    private static byte[] makeMagicNumber(String fName) {
        return ("MA" + fName + version).getBytes();
    }

}
