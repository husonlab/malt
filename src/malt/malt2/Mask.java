package malt.malt2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * a simple bit mask that supports multiple bits
 * Created by huson on 9/5/14.
 */
public final class Mask {
    private final byte bits;
    private byte[] data;

    /**
     * constructor
     *
     * @param initialCapacity
     */
    public Mask(byte bits, int initialCapacity) {
        if (bits <= 0)
            throw new RuntimeException("Illegal non-positive bits: " + bits);
        if (initialCapacity <= 0)
            throw new RuntimeException("Illegal non-positive initialCapacity: " + initialCapacity);

        this.bits = bits;
        data = new byte[((bits * (initialCapacity - 1)) >>> 3) + 1];
    }

    /**
     * set the mask value for the given bitmask and index
     *
     * @param bit
     * @param index
     * @param set
     */
    public void set(int bit, int index, boolean set) {
        index = bits * index + bit;
        int dataIndex = index >>> 3;
        int dataBit = index - (dataIndex << 3);
        if (set)
            data[dataIndex] |= (1 << dataBit);
        else
            data[dataIndex] &= ~(1 << dataBit);
    }

    /**
     * get the mask value
     *
     * @param index
     * @return true or false
     */
    public boolean get(int bit, int index) {
        index = bits * index + bit;
        int dataIndex = index >>> 3;
        int dataBit = index - (dataIndex << 3);
        return (data[dataIndex] & (1 << dataBit)) != 0;
    }

    /**
     * write
     *
     * @param outs
     * @throws IOException
     */
    public void write(OutputStream outs) throws IOException {
        outs.write(data, 0, data.length);
    }

    /**
     * read from file. Assumes that the capacity of this has already been set to match the input
     *
     * @param ins
     * @throws IOException
     */
    public void read(InputStream ins) throws IOException {
        for (int i = 0; i < data.length; i++) {
            final int value = ins.read();
            if (value == -1)
                throw new IOException("Mask.read() failed: too few bytes");
            data[i] = (byte) value;
        }
    }
}
