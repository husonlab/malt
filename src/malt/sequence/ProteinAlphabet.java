package malt.sequence;

/**
 * Created by huson on 9/30/14.
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
