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
 * protein alphabet
 * Daniel Huson, 2014
 */
public class ProteinAlphabet extends Alphabet {
    private static ProteinAlphabet instance;

    /**
     * gets the single instance of the protein alphabet
     *
     * @return instance
     */
    public static ProteinAlphabet getInstance() {
        if (instance == null)
            instance = new ProteinAlphabet();
        return instance;
    }

    /**
     * constructor
     */
    private ProteinAlphabet() {
        super("A C D E F G H I K L M N P Q R S T V W Y", (byte) 'X');
    }
}
