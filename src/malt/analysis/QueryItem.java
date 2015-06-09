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

/**
 * a single link in the linked list of queries
 * Daniel Huson, 8.2014
 */
public class QueryItem {
    final byte[] queryName;
    final ReadMatchItem[] readMatchItems;
    QueryItem next;

    public QueryItem() {
        queryName = null;
        readMatchItems = null;
    }

    public QueryItem(final byte[] queryName, final int length, final ReadMatch[] readMatches) {
        this.queryName = queryName;
        this.readMatchItems = new ReadMatchItem[length];
        for (int i = 0; i < length; i++) {
            ReadMatch readMatch = readMatches[i];
            this.readMatchItems[i] = new ReadMatchItem(readMatch.getReferenceId(), (float) readMatch.getBitScore(), readMatch.getStartRef(), readMatch.getEndRef());
        }
    }

    public ReadMatchItem[] getReadMatchItems() {
        return readMatchItems;
    }
}
