package malt.next;

import jloda.util.Basic;

import java.util.Comparator;

/**
 * A match to be stored
 * Daniel Huson, 8.2014
 */
public class AMatch implements Comparator<AMatch> {
    private int referenceId;
    private int rawScore;
    private byte[] text;
    private AMatch next;

    /**
     * constructor
     */
    public AMatch() {
    }

    /**
     * constructor
     *
     * @param referenceId
     * @param rawScore
     * @param text
     */
    public AMatch(int referenceId, int rawScore, byte[] text, AMatch next) {
        this.referenceId = referenceId;
        this.rawScore = rawScore;
        this.text = text;
        this.next = next;
    }

    /**
     * replace the values
     *
     * @param referenceId
     * @param rawScore
     * @param text
     */
    public void replace(int referenceId, int rawScore, byte[] text) {
        this.referenceId = referenceId;
        this.rawScore = rawScore;
        this.text = text;
    }

    /**
     * reference id
     *
     * @return
     */
    public int getReferenceId() {
        return referenceId;
    }

    /**
     * raw score
     *
     * @return
     */
    public int getRawScore() {
        return rawScore;
    }

    /**
     * text
     *
     * @return
     */
    public byte[] getText() {
        return text;
    }

    public AMatch getNext() {
        return next;
    }

    public void setNext(AMatch next) {
        this.next = next;
    }

    public String toString() {
        return referenceId + " " + rawScore + " " + Basic.toString(text);
    }

    @Override
    public int compare(AMatch a, AMatch b) {
        if (a.getRawScore() > b.getRawScore())
            return -1;
        else if (a.getRawScore() < b.getRawScore())
            return 1;
        else if (a.getReferenceId() < b.getReferenceId())
            return -1;
        else if (a.getReferenceId() > b.getReferenceId())
            return 1;
        else {
            final byte[] aText = a.getText();
            final byte[] bText = b.getText();

            int pos = 0;
            while (pos < aText.length && pos < bText.length) {
                if (aText[pos] < bText[pos])
                    return -1;
                else if (aText[pos] > bText[pos])
                    return 1;
                pos++;
            }
            if (pos < aText.length)
                return -1;
            else if (pos < bText.length)
                return 1;
            else
                return 0;
        }
    }
}
