package malt.malt2;

import megan.algorithms.TaxonAssignmentUsingLCA;
import megan.classification.IdMapper;
import megan.mainviewer.data.TaxonomyName2IdMap;
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
        taxIds = new HashSet<Integer>(100);
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
            return TaxonomyName2IdMap.NOHITS_TAXONID;
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
            return TaxonomyName2IdMap.UNASSIGNED_TAXONID;
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
