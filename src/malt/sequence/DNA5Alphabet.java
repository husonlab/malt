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
 * DNA5 alphabet
 * Created by huson on 9/30/14.
 */
public class DNA5Alphabet extends Alphabet {
    private static DNA5Alphabet instance;

    final static private byte[] normalizedComplement = {
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', '-', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'T',
            'N', 'G', 'N', 'N', 'N', 'C', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'A', 'A', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'T', 'N', 'G', 'N', 'N', 'N', 'C', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'A', 'A', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N'
    };

    /**
     * gets the single instance of the protein alphabet
     *
     * @return instance
     */
    public static DNA5Alphabet getInstance() {
        if (instance == null)
            instance = new DNA5Alphabet();
        return instance;
    }

    /**
     * constructor
     */
    private DNA5Alphabet() {
        super("A C G TU", (byte) 'N');
    }

    /**
     * gets the reverse complement
     *
     * @param sequence
     * @param reverseComplement
     */
    public static void reverseComplement(byte[] sequence, byte[] reverseComplement) {
        for (int i = 0; i < sequence.length; i++) {
            reverseComplement[sequence.length - (i + 1)] = normalizedComplement[sequence[i]];
        }
    }
}
