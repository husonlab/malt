/**
 * Copyright 2015, Daniel Huson
 *
 *(Some files contain contributions from other authors, who are then mentioned separately)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package malt.mapping;

import jloda.util.Basic;
import jloda.util.CanceledException;
import malt.data.RefIndex2ClassId;
import megan.classification.Classification;
import megan.mainviewer.data.TaxonomyData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * helps MALT load mapping files
 * Daniel Huson, 8.2014
 */
public class MappingHelper {
    private static TaxonMapping taxonMapping;
    private static KeggMapping keggMapping;
    private static SeedMapping seedMapping;
    private static CogMapping cogMapping;

    private static RefIndex2ClassId[] index2mapping;

    /**
     * loads taxon mapping
     *
     * @param indexDirectory
     * @return taxon mapping
     * @throws IOException
     * @throws CanceledException
     */
    public static TaxonMapping loadTaxonMapping(boolean load, String indexDirectory) throws IOException, CanceledException {
        try {
            if (load) {
                TaxonomyData.load();
                    final File ref2taxonomyFile = new File(indexDirectory, "taxonomy.idx");
                    return taxonMapping = new TaxonMapping(ref2taxonomyFile);
            }
        } catch (Exception ex) {
            Basic.caught(ex);
        }
        return null;
    }

    /**
     * load the functional mapping file from the index
     *
     * @param indexDirectory
     * @return kegg mapping
     * @throws IOException
     * @throws CanceledException
     */
    public static KeggMapping loadKeggMapping(boolean load, String indexDirectory) throws IOException, CanceledException {
        try {
            if (load) {
                final File file = new File(indexDirectory, "kegg.idx");
                if (file.exists())
                    return keggMapping = new KeggMapping(file);
            }
        } catch (Exception ex) {
        }
        return null;

    }


    /**
     * load the functional mapping file from the index
     *
     * @param indexDirectory
     * @return seed mapping
     * @throws IOException
     * @throws CanceledException
     */
    public static SeedMapping loadSeedMapping(boolean load, String indexDirectory) throws IOException, CanceledException {
        try {
            if (load) {
                final File file = new File(indexDirectory, "seed.idx");
                if (file.exists())
                    return seedMapping = new SeedMapping(file);
            }
        } catch (Exception ex) {
        }
        return null;
    }

    /**
     * load the functional mapping file from the index
     *
     * @param indexDirectory
     * @return cog mapping
     * @throws IOException
     * @throws CanceledException
     */
    public static CogMapping loadCogMapping(boolean load, String indexDirectory) {
        try {
            if (load) {
                final File file = new File(indexDirectory, "cog.idx");
                if (file.exists())
                    return cogMapping = new CogMapping(file);
            }
        } catch (Exception ex) {
        }
        return null;
    }


    public static TaxonMapping getTaxonMapping() {
        return taxonMapping;
    }

    public static KeggMapping getKeggMapping() {
        return keggMapping;
    }

    public static SeedMapping getSeedMapping() {
        return seedMapping;
    }

    public static CogMapping getCogMapping() {
        return cogMapping;
    }

    public static String[] getFNames() {
        List<String> list = new ArrayList<>();
        if (getTaxonMapping() != null) {
            list.add(Classification.Taxonomy);
        }
        if (getKeggMapping() != null) {
            list.add("KEGG");
        }
        if (getSeedMapping() != null) {
            list.add("SEED");
        }
        if (getCogMapping() != null) {
            list.add("COG");
        }

        return list.toArray(new String[list.size()]);
    }

    /**
     * gets the appopriate mapping for the given fID
     *
     * @param fID
     * @return mapping
     */
    public static RefIndex2ClassId getMapping(int fID) {
        if (index2mapping == null) {
            List<RefIndex2ClassId> list = new ArrayList<>();
            if (getTaxonMapping() != null) {
                list.add(getTaxonMapping());
            }
            if (getKeggMapping() != null) {
                list.add(getKeggMapping());
            }
            if (getSeedMapping() != null) {
                list.add(getSeedMapping());
            }
            if (getCogMapping() != null) {
                list.add(getCogMapping());
            }
            index2mapping = list.toArray(new RefIndex2ClassId[list.size()]);

        }
        return index2mapping[fID];
    }
}
