/**
 * QueryItem.java 
 * Copyright (C) 2017 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
