package malt.data;

/**
 * sequence type
 * Daniel Huson, 8.2014
 */
public enum SequenceType {
    DNA,   // DNA sequence
    Protein;  // protein sequence

    /**
     * get rank
     *
     * @param sequenceType
     * @return rank
     */
    public static int rankOf(SequenceType sequenceType) {
        for (int i = 0; i < values().length; i++)
            if (values()[i] == sequenceType)
                return i;
        return -1;
    }

    /**
     * get type from rank
     *
     * @param rank
     * @return
     */
    public static SequenceType valueOf(int rank) {
        return values()[rank];
    }

    /**
     * get value ignoring case
     *
     * @param label
     * @return value
     */
    public static SequenceType valueOfIgnoreCase(String label) {
        for (SequenceType type : values())
            if (label.equalsIgnoreCase(type.toString()))
                return type;
        return null;
    }
}
