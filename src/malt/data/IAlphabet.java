/*
 *  IAlphabet.java Copyright (C) 2019. Daniel H. Huson GPL
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
 * DNA or protein alphabet
 * Daniel Huson, 8.2014
 */
public interface IAlphabet extends INormalizer {
    /**
     * maps letter to normalized base or amino acid
     *
     * @param letter
     * @return
     */
    byte getNormalized(byte letter);

    /**
     * do letters a and b correspond to the same base or amino acid?
     *
     * @param a
     * @param b
     * @return true, if equalOverShorterOfBoth bases
     */
    boolean equal(byte a, byte b);

    /**
     * is this a protein alphabet?
     *
     * @return true, if protein
     */
    boolean isProtein();

    /**
     * is this a DNA alphabet?
     *
     * @return true, if DNA
     */
    boolean isDNA();

    /**
     * gets the name of this alphabet
     *
     * @return name
     */
    String getName();

    /**
     * get the number of different letters
     *
     * @return size
     */
    int size();

    /**
     * is this word a good seed?
     *
     * @param word
     * @param length
     * @return true, if good
     */
    boolean isGoodSeed(byte[] word, int length);
}
