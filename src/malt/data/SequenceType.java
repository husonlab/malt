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

package malt.data;

/**
 * sequence type
 * Daniel Huson, 8.2014
 */
public enum SequenceType {
    DNA,   // DNA sequence
    Protein;  // protein sequence

    /**
     * get rank
     *
     * @param sequenceType
     * @return rank
     */
    public static int rankOf(SequenceType sequenceType) {
        for (int i = 0; i < values().length; i++)
            if (values()[i] == sequenceType)
                return i;
        return -1;
    }

    /**
     * get type from rank
     *
     * @param rank
     * @return
     */
    public static SequenceType valueOf(int rank) {
        return values()[rank];
    }

    /**
     * get value ignoring case
     *
     * @param label
     * @return value
     */
    public static SequenceType valueOfIgnoreCase(String label) {
        for (SequenceType type : values())
            if (label.equalsIgnoreCase(type.toString()))
                return type;
        return null;
    }
}
