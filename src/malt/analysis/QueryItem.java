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
