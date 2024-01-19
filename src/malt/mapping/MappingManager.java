/*
 * MappingManager.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.CanceledException;
import jloda.util.FileUtils;
import megan.classification.Classification;
import megan.classification.ClassificationManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * manages MALT mapping files
 * Daniel Huson, 2.2016
 */
public class MappingManager {
    private static String[] cNames;
    private static int taxonomyIndex;
    private static Mapping[] mappings;

    /**
     * load all mappings
     *
	 */
    public static void loadMappings(String[] cNames, String indexDirectory) throws IOException, CanceledException {
        MappingManager.cNames = cNames;
        mappings = new Mapping[cNames.length];
        taxonomyIndex = -1;
        for (int i = 0; i < cNames.length; i++) {
            String cName = cNames[i];
            if (cName.equals(Classification.Taxonomy))
                taxonomyIndex = i;
            String fileName = cName.toLowerCase() + ".idx";
            ClassificationManager.ensureTreeIsLoaded(cName);
            final File file = new File(indexDirectory, fileName);
            if (file.exists())
                mappings[i] = new Mapping(cName, file);
            else
                mappings[i] = null;
        }
    }

    /**
     * get all names of loaded mappings
     *
     * @return names
     */
    public static String[] getCNames() {
        return cNames;
    }

    /**
     * gets the appopriate mapping for the given fID
     *
     * @return mapping
     */
    public static Mapping getMapping(int fID) {
        return mappings[fID];
    }

    /**
     * gets the taxonomy mapping
     *
     * @return taxonomy mapping
     */
    public static Mapping getTaxonomyMapping() {
        if (taxonomyIndex >= 0)
            return getMapping(taxonomyIndex);
        else
            return null;
    }

    /**
     * determine all available classifications
     *
     * @return list of available classifications
     */
    public static String[] determineAvailableMappings(String indexDirectory) {
        File[] files = (new File(indexDirectory)).listFiles();
        if (files != null) {

            ArrayList<String> cNames = new ArrayList<>(files.length);
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".tre")) {
					name = FileUtils.replaceFileSuffix(name, "");
                    for (String cName : ClassificationManager.getAllSupportedClassifications()) {
                        if (cName.equalsIgnoreCase(name))
                            cNames.add(cName);
                    }
                }
            }
            return cNames.toArray(new String[0]);
        } else
            return new String[0];

    }
}
