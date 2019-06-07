/*
 *  SequenceType.java Copyright (C) 2019. Daniel H. Huson GPL
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
