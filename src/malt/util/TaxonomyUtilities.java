/*
 *  TaxonomyUtilities.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.util;

import jloda.graph.Node;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.viewer.TaxonomicLevels;

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
        final Classification classification = ClassificationManager.get(Classification.Taxonomy, false);
        Node v = classification.getFullTree().getTaxon2Node(taxonId);
        while (v != null) {
            taxonId = (Integer) v.getInfo();
            int level = classification.getName2IdMap().getRank(taxonId);
            if (level == TaxonomicLevels.getSpeciesId())
                return classification.getName2IdMap().get(taxonId);
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
        final Classification classification = ClassificationManager.get(Classification.Taxonomy, false);
        Node v = classification.getFullTree().getTaxon2Node(taxonId);
        while (v != null) {
            taxonId = (Integer) v.getInfo();
            int level = classification.getName2IdMap().getRank(taxonId);
            if (level == TaxonomicLevels.getGenusId())
                return classification.getName2IdMap().get(taxonId);
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
        final Classification classification = ClassificationManager.get(Classification.Taxonomy, false);
        final int speciesId = TaxonomicLevels.getSpeciesId();
        final int subspeciesId = TaxonomicLevels.getSubspeciesId();
        Node v = classification.getFullTree().getTaxon2Node(taxonId);
        while (v != null && v.getInDegree() > 0 && v.getInfo() != null) {
            v = v.getFirstInEdge().getSource();
            taxonId = (Integer) v.getInfo();
            String name = classification.getName2IdMap().get(taxonId);
            if (name != null && (name.equals("root") || name.equals("cellular organisms")))
                break;
            int level = classification.getName2IdMap().getRank(taxonId);
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
        final Classification classification = ClassificationManager.get(Classification.Taxonomy, false);
        final int genus = TaxonomicLevels.getGenusId();

        if (taxonId == 1)// root taxon
            return classification.getName2IdMap().get(taxonId);

        List<Integer> path = new ArrayList<>();
        Node v = classification.getFullTree().getTaxon2Node(taxonId);

        while (v != null) {
            taxonId = (Integer) v.getInfo();
            if (classification.getName2IdMap().getRank(taxonId) != 0) // ignore unranked nodes
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
                buf.append(classification.getName2IdMap().get(path.get(i)));
            }
            int level = classification.getName2IdMap().getRank(path.get(path.size() - 1));
            if (level == genus)
                buf.append(".");
        }
        return buf.toString();
    }
}
