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

package malt.io;

import megan.algorithms.TaxonAssignmentUsingLCA;
import megan.classification.IdMapper;
import megan.rma3.MatchLineRMA3;

import java.util.HashSet;
import java.util.Set;

/**
 * algorithms for binning reads
 * Daniel Huson, 6.2014
 */
public class AnalyzerRMA3 {
    private final float minScore;
    private final float maxExpected;

    private final float topPercentFactor;
    private final Set<Integer> taxIds;

    /**
     * constructor
     *
     * @param minScore
     * @param maxExpected
     * @param topPercent
     */
    public AnalyzerRMA3(float minScore, float maxExpected, float topPercent) {
        this.minScore = minScore;
        this.maxExpected = maxExpected;
        topPercentFactor = (100.0f - topPercent) / 100.0f;
        taxIds = new HashSet<>(100);
    }

    /**
     * get the LCA for a set of matches
     *
     * @param matches
     * @param numberOfMatches
     * @return LCA
     */
    public int getLCA(MatchLineRMA3[] matches, int numberOfMatches) {
        if (numberOfMatches == 0)
            return IdMapper.NOHITS_ID;
        float minScore = this.minScore;
        for (int i = 0; i < numberOfMatches; i++) {
            final MatchLineRMA3 matchLine = matches[i];
            minScore = Math.max(minScore, topPercentFactor * matchLine.getBitScore());
        }

        taxIds.clear();
        for (int i = 0; i < numberOfMatches; i++) {
            final MatchLineRMA3 matchLine = matches[i];
            if (matchLine.getTaxId() > 0 && matchLine.getBitScore() >= minScore && matchLine.getExpected() <= maxExpected) {
                taxIds.add(matchLine.getTaxId());
            }
        }
        if (taxIds.size() == 0)
            return IdMapper.NOHITS_ID;
        else if (taxIds.size() == 1)
            return taxIds.iterator().next();
        else
            return TaxonAssignmentUsingLCA.getLCA(taxIds, true);
    }

    /**
     * return the first KEGG id found
     *
     * @param matches
     * @param numberOfMatches
     * @return first KEGG id found
     */
    public int getKeggId(MatchLineRMA3[] matches, int numberOfMatches) {
        if (numberOfMatches == 0)
            return IdMapper.NOHITS_ID;
        for (int i = 0; i < numberOfMatches; i++) {
            final MatchLineRMA3 matchLine = matches[i];
            if (matchLine.getKeggId() > 0 && matchLine.getBitScore() >= minScore && matchLine.getExpected() <= maxExpected) {
                return matchLine.getKeggId();
            }
        }
        return IdMapper.UNASSIGNED_ID;
    }

    /**
     * return the first SEED id found
     *
     * @param matches
     * @param numberOfMatches
     * @return first SEED id found
     */
    public int getSeedId(MatchLineRMA3[] matches, int numberOfMatches) {
        if (numberOfMatches == 0)
            return IdMapper.NOHITS_ID;
        for (int i = 0; i < numberOfMatches; i++) {
            final MatchLineRMA3 matchLine = matches[i];
            if (matchLine.getSeedId() > 0 && matchLine.getBitScore() >= minScore && matchLine.getExpected() <= maxExpected) {
                return matchLine.getSeedId();
            }
        }
        return IdMapper.UNASSIGNED_ID;
    }

    /**
     * return the first COG id found
     *
     * @param matches
     * @param numberOfMatches
     * @return first COG id found
     */
    public int getCogId(MatchLineRMA3[] matches, int numberOfMatches) {
        if (numberOfMatches == 0)
            return IdMapper.NOHITS_ID;
        for (int i = 0; i < numberOfMatches; i++) {
            final MatchLineRMA3 matchLine = matches[i];
            if (matchLine.getCogId() > 0 && matchLine.getBitScore() >= minScore && matchLine.getExpected() <= maxExpected) {
                return matchLine.getCogId();
            }
        }
        return IdMapper.UNASSIGNED_ID;
    }


}
