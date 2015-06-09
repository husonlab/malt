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
