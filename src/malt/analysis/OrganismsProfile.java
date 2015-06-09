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

package malt.analysis;

import malt.data.ReadMatch;
import malt.mapping.TaxonMapping;
import malt.util.Utilities;
import megan.algorithms.LCAAlgorithm;

import java.util.*;

/**
 * an organism profile reports organisms, contained genes and contained reads.
 * THe weighted LCA is used to determine organisms. For each read, genes are ranked by reference weight
 * Daniel Huson, 8.2014
 */
public class OrganismsProfile {
    protected final TaxonMapping taxonMapping;
    protected final Map<Integer, Integer> refIndex2weight = new HashMap<>(100000);
    protected final QueryItem head = new QueryItem();  // head of query item list
    protected QueryItem tail = head;                     // tail of query item list

    protected String name;

    protected int totalReads;

    private final Set<Integer> refIdAlreadySeenInAddRead = new HashSet<>(2000, 0.9f);

    private final LCAAlgorithm lcaAlgorithm = new LCAAlgorithm();

    protected int[] taxIds = new int[1000];
    private int[] refIds = new int[1000];

    protected double topPercentFactor = 0.9;

    /**
     * constructor
     */
    public OrganismsProfile(final TaxonMapping taxonMapping) {
        this.taxonMapping = taxonMapping;
    }

    /**
     * add a read to the organism profile
     *
     * @param queryHeader
     * @param numberOfMatches
     * @param readMatches
     */
    public void addRead(final byte[] queryHeader, final int numberOfMatches, final ReadMatch[] readMatches) {
        final byte[] queryName = Utilities.getFirstWordSkipLeadingGreaterSign(queryHeader);

        // increment reference weights using naive LCA algorithm
        if (numberOfMatches == 0) {  // no hits
            addNoHitsRead();
        } else if (numberOfMatches == 1) {   // exactly one hit, will use this
            ReadMatch match = readMatches[0];
            int refId = match.getReferenceId();
            Integer weight = refIndex2weight.get(refId);  // increment reference sequence weight
            if (weight == null)
                refIndex2weight.put(refId, 1);
            else
                refIndex2weight.put(refId, weight + 1);
            totalReads++;
            tail.next = new QueryItem(queryName, numberOfMatches, readMatches);
            tail = tail.next;
        } else { // more than one hit.
            // For each read, we store the set of references matched and after processing all reads in this way we
            // then apply the weighted LCA to all such sets of references
            if (refIds.length < numberOfMatches) {   // resize if necessary
                int newSize = Math.max(2 * refIds.length, numberOfMatches);
                refIds = new int[newSize];
                taxIds = new int[newSize];
            }

            final double topScore = readMatches[0].getBitScore();
            final double minScore = Math.min(topScore, topPercentFactor * topScore);
            int numberOfMatchesToUse = 0;
            refIdAlreadySeenInAddRead.clear();
            for (int i = 0; i < numberOfMatches; i++) { // consider all matches in descending order of bit score
                ReadMatch match = readMatches[i];
                if (match.getBitScore() < minScore)
                    break;
                final int refId = match.getReferenceId();
                final int taxId = taxonMapping.get(refId);
                if (taxId > 0 && numberOfMatchesToUse < refIds.length) {
                    if (!refIdAlreadySeenInAddRead.contains(refId)) {   // don't use more than one match to the same reference
                        refIdAlreadySeenInAddRead.add(refId);
                        taxIds[numberOfMatchesToUse] = taxId;
                        refIds[numberOfMatchesToUse++] = refId;
                    }
                }
            }
            if (numberOfMatchesToUse == 0) {
                addNoHitsRead(); // should never happen...
            } else if (numberOfMatchesToUse == 1) {  // only has one good match, increment reference weight
                final int refId = refIds[0];
                final Integer weight = refIndex2weight.get(refId);     // increment reference sequence weight
                if (weight == null)
                    refIndex2weight.put(refId, 1);
                else
                    refIndex2weight.put(refId, weight + 1);
                totalReads++;
                tail.next = new QueryItem(queryName, numberOfMatchesToUse, readMatches);
                tail = tail.next;
            } else {       // compute naive LCA. Then increment weight for any reference whose taxon matches the LCA
                final int lca = lcaAlgorithm.computeNaiveLCA(taxIds, numberOfMatchesToUse);
                if (lca > 0) {
                    for (int i = 0; i < numberOfMatchesToUse; i++) {
                        if (taxIds[i] == lca) {
                            int refId = refIds[i];
                            Integer weight = refIndex2weight.get(refId);
                            if (weight == null)
                                refIndex2weight.put(refId, 1);
                            else
                                refIndex2weight.put(refId, weight + 1);
                        }
                    }
                    totalReads++;
                    tail.next = new QueryItem(queryName, numberOfMatchesToUse, readMatches);
                    tail = tail.next;
                } else
                    addNoHitsRead();
            }
        }
    }

    /**
     * skip a read
     */
    public void addNoHitsRead() {
        totalReads++;
    }

    /**
     * returns getLetterCodeIterator over all query items
     *
     * @return query item getLetterCodeIterator
     */
    public Iterator<QueryItem> iterator() {
        return new Iterator<QueryItem>() {
            private QueryItem item = head.next;

            public boolean hasNext() {
                return item != null;
            }

            public QueryItem next() {
                QueryItem result = item;
                item = item.next;
                return result;
            }

            public void remove() {
            }
        };
    }

    public double getTopPercent() {
        return 100 * (1.0 - topPercentFactor);
    }

    public void setTopPercent(double topPercent) {
        this.topPercentFactor = 1.0 - topPercent / 100.0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTotalReads() {
        return totalReads;
    }

    protected LCAAlgorithm getLcaAlgorithm() {
        return lcaAlgorithm;
    }

    /**
     * finish the analysis
     */
    public void finishAnalysis() {
    }
}
