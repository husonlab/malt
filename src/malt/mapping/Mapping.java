/*
 *  Mapping.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.mapping;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import malt.data.ISequenceAccessor;
import malt.data.RefIndex2ClassId;
import megan.classification.Classification;
import megan.classification.IdParser;

import java.io.File;
import java.io.IOException;

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
    public static Mapping create(String fName, ISequenceAccessor referencesDB, IdParser classificationMapper, ProgressListener progress) throws CanceledException, IOException {
        final Mapping mapping = new Mapping(fName, referencesDB.getNumberOfSequences());
        final String tag = Classification.createShortTag(fName);

        progress.setMaximum(referencesDB.getNumberOfSequences());
        progress.setProgress(0);
        for (int i = 0; i < referencesDB.getNumberOfSequences(); i++) {
            String header = Basic.toString(referencesDB.getHeader(i));
            Integer classId = classificationMapper.getIdFromHeaderLine(header);
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
     * save to a stream and then close the stream
     *
     * @param file
     * @throws IOException
     */
    public void save(File file) throws IOException, CanceledException {
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
