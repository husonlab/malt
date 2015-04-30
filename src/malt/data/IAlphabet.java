package malt.data;

/**
 * DNA or protein alphabet
 * Daniel Huson, 8.2014
 */
public interface IAlphabet extends INormalizer {
    /**
     * maps letter to normalized base or amino acid
     *
     * @param letter
     * @return
     */
    public byte getNormalized(byte letter);

    /**
     * do letters a and b correspond to the same base or amino acid?
     *
     * @param a
     * @param b
     * @return true, if equal bases
     */
    public boolean equal(byte a, byte b);

    /**
     * is this a protein alphabet?
     *
     * @return true, if protein
     */
    public boolean isProtein();

    /**
     * is this a DNA alphabet?
     *
     * @return true, if DNA
     */
    public boolean isDNA();

    /**
     * gets the name of this alphabet
     *
     * @return name
     */
    public String getName();

    /**
     * get the number of different letters
     *
     * @return size
     */
    public int size();

    /**
     * is this word a good seed?
     *
     * @param word
     * @param length
     * @return true, if good
     */
    public boolean isGoodSeed(byte[] word, int length);
}
