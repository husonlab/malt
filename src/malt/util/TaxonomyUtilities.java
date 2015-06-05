package malt.util;

import jloda.graph.Node;
import megan.mainviewer.data.TaxonomicLevels;
import megan.mainviewer.data.TaxonomyData;

import java.util.ArrayList;
import java.util.List;

/**
 * simple utilities
 * Daniel Huson, 8.2014
 */
public class TaxonomyUtilities {

    /**
     * get the species for the taxon
     *
     * @param taxonId
     * @return species
     */

    public static String getContainingSpecies(Integer taxonId) {
        Node v = TaxonomyData.getTree().getTaxon2Node(taxonId);
        while (v != null) {
            taxonId = (Integer) v.getInfo();
            byte level = TaxonomyData.getName2IdMap().getRank(taxonId);
            if (level == TaxonomicLevels.getSpeciesId())
                return TaxonomyData.getName2IdMap().get(taxonId);
            if (v.getInDegree() > 0)
                v = v.getFirstInEdge().getSource();
            else
                v = null;
        }
        return null;
    }

    /**
     * get the genus
     *
     * @param taxonId
     * @return genus
     */
    public static String getContainingGenus(Integer taxonId) {
        Node v = TaxonomyData.getTree().getTaxon2Node(taxonId);
        while (v != null) {
            taxonId = (Integer) v.getInfo();
            byte level = TaxonomyData.getName2IdMap().getRank(taxonId);
            if (level == TaxonomicLevels.getGenusId())
                return TaxonomyData.getName2IdMap().get(taxonId);
            if (v.getInDegree() > 0)
                v = v.getFirstInEdge().getSource();
            else
                v = null;
        }
        return null;
    }

    /**
     * gets the strain
     *
     * @param taxonId
     * @return
     */
    public static String getStrain(int taxonId) {
        final int speciesId = TaxonomicLevels.getSpeciesId();
        final int subspeciesId = TaxonomicLevels.getSubspeciesId();
        Node v = TaxonomyData.getTree().getTaxon2Node(taxonId);
        while (v != null && v.getInDegree() > 0 && v.getInfo() != null) {
            v = v.getFirstInEdge().getSource();
            taxonId = (Integer) v.getInfo();
            String name = TaxonomyData.getName2IdMap().get(taxonId);
            if (name != null && (name.equals("root") || name.equals("cellular organisms")))
                break;
            byte level = TaxonomyData.getName2IdMap().getRank(taxonId);
            if (level > speciesId)
                break;
            if (level > subspeciesId)
                break;
            if (level != subspeciesId)
                break;
        }
        return null;
    }

    /**
     * gets the taxonomic path to the named taxon
     *
     * @param taxonId
     * @return
     */
    public static String getPath(int taxonId) {
        final byte genus = TaxonomicLevels.getGenusId();

        if (taxonId == 1)// root taxon
            return TaxonomyData.getName2IdMap().get(taxonId);

        List<Integer> path = new ArrayList<Integer>();
        Node v = TaxonomyData.getTree().getTaxon2Node(taxonId);

        while (v != null) {
            taxonId = (Integer) v.getInfo();
            if (TaxonomyData.getName2IdMap().getRank(taxonId) != 0) // ignore unranked nodes
                path.add(taxonId);
            if (v.getInDegree() > 0)
                v = v.getFirstInEdge().getSource();
            else
                v = null;
        }

        StringBuilder buf = new StringBuilder();
        if (path.size() > 0) {
            boolean isFirst = true;
            for (int i = path.size() - 1; i >= 0; i--) {
                if (isFirst)
                    isFirst = false;
                else
                    buf.append("; ");
                buf.append(TaxonomyData.getName2IdMap().get(path.get(i)));
            }
            byte level = TaxonomyData.getName2IdMap().getRank(path.get(path.size() - 1));
            if (level == genus)
                buf.append(".");
        }
        return buf.toString();
    }
}
