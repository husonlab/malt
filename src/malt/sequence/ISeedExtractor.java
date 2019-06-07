/*
 *  ISeedExtractor.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.sequence;

/**
 * seed extract
 * Daniel Huson, 2014
 */
public interface ISeedExtractor {
    byte[] decodeSeed(long seedCode, int seedWeight);

    /**
     * extract a seed from a sequence code
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos          @return seed
     */
    long getSeedCode(boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos);

    /**
     * extract a seed from a sequence code
     *
     * @param seedShape
     * @param seedWeight
     * @param sequenceCode
     * @param pos
     * @param failValue    value returned if sequence too short   @return seed
     */
    long getSeedCode(boolean[] seedShape, int seedWeight, long[] sequenceCode, int pos, int failValue);

    /**
     * is this a good seed?
     *
     * @param seedCode
     * @return true, if good
     */
    boolean isGoodSeed(long seedCode, int seedWeight);

    /**
     * get the number of bits per letter
     *
     * @return bits
     */
    int getBitsPerLetter();
}
