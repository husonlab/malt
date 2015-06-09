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
     * @return true, if equal bases
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
