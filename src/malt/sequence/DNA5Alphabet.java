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
