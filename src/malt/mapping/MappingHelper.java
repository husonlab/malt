package malt.mapping;

import jloda.util.Basic;
import jloda.util.CanceledException;
import megan.mainviewer.data.TaxonomyData;

import java.io.File;
import java.io.IOException;

/**
 * helps MALT load mapping files
 * Daniel Huson, 8.2014
 */
public class MappingHelper {
    private static TaxonMapping taxonMapping;
    private static KeggMapping keggMapping;
    private static SeedMapping seedMapping;
    private static CogMapping cogMapping;


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
}
