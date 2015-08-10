/**
 * TaxonMapping.java 
 * Copyright (C) 2015 Daniel H. Huson
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
package malt.mapping;

import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import malt.data.ISequenceAccessor;
import malt.data.RefIndex2ClassId;
import malt.data.ReferencesDBBuilder;
import megan.classification.IdParser;

import java.io.File;
import java.io.IOException;

/**
 * Maintains mapping from Reference indices to taxa
 * Daniel Huson, 8.2014
 */
public class TaxonMapping extends RefIndex2ClassId {
    private final static byte[] MAGIC_NUMBER = "MARef2TaxV1.0".getBytes();

    /**
     * construct a table
     *
     * @param maxIndex
     */
    public TaxonMapping(int maxIndex) {
        super(maxIndex);
    }

    /**
     * compute the mapping for the given reference database
     *
     * @param referencesDB
     * @param progress
     */
    public static TaxonMapping create(final ISequenceAccessor referencesDB, IdParser classificationMapper, ProgressListener progress) throws CanceledException, IOException {
        TaxonMapping mapping = new TaxonMapping(referencesDB.getNumberOfSequences());

        int countIdentified = 0;

        progress.setMaximum(referencesDB.getNumberOfSequences());
        progress.setProgress(0);
        for (int i = 0; i < referencesDB.getNumberOfSequences(); i++) {
            String header = Basic.toString(referencesDB.getHeader(i));
            // System.err.println("Header="+header);
            Integer taxonId = classificationMapper.getIdFromHeaderLine(header);
            // System.err.println("taxId="+taxonId);
            if (taxonId > 0) {
                mapping.put(i, taxonId);
                countIdentified++;
                referencesDB.extendHeader(i, ReferencesDBBuilder.TAXON_TAG, taxonId);
            }
            progress.incrementProgress();
        }
        if (progress instanceof ProgressPercentage)
            progress.close();
        System.err.println("Identified taxon for " + countIdentified + " of " + referencesDB.getNumberOfSequences() + " reference sequences");
        return mapping;
    }

    /**
     * put ref-index to taxon-Id
     *
     * @param refIndex
     * @param classId
     */
    public void put(int refIndex, int classId) {
        super.put(refIndex, classId);
    }

    /**
     * get ref-index to taxon-id
     *
     * @param refIndex
     * @return
     */
    public int get(int refIndex) {
        return super.get(refIndex);
    }

    /**
     * save to a stream and then close the stream
     *
     * @param file
     * @throws java.io.IOException
     */
    public void save(File file) throws IOException, CanceledException {
        super.save(file, MAGIC_NUMBER);
    }

    /**
     * construct from an existing file
     *
     * @param file
     * @throws IOException
     * @throws CanceledException
     */
    public TaxonMapping(File file) throws IOException, CanceledException {
        super(file, MAGIC_NUMBER);
    }
}
