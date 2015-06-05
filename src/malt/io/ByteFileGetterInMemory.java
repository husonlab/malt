package malt.io;

import jloda.map.IByteGetter;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;

import java.io.*;

/**
 * int file getter in memory
 * Daniel Huson, 5.2015
 */
public class ByteFileGetterInMemory implements IByteGetter {
    private final byte[][] data;
    private final long limit;
    private final int length0;

    /**
     * int file getter in memory
     *
     * @param file
     * @throws IOException
     * @throws CanceledException
     */
    public ByteFileGetterInMemory(File file) throws IOException, CanceledException {
        limit = file.length();

        data = new byte[(int) ((limit >> 30)) + 1][];
        length0 = (int) (Math.min(limit, 1 << 30));
        for (int i = 0; i < data.length; i++) {
            int length = Math.min(length0, dataPos(limit) + 1);
            data[i] = new byte[length];
        }

        try (InputStream ins = new BufferedInputStream(new FileInputStream(file))) {
            final ProgressPercentage progress = new ProgressPercentage("Reading file: " + file, limit);
            for (long pos = 0; pos < limit; pos++) {
                data[dataIndex(pos)][dataPos(pos)] = (byte) ins.read();
                progress.setProgress(pos);
            }
            progress.close();
        }
    }

    /**
     * gets value for given index
     *
     * @param index
     * @return value or 0
     */
    @Override
    public int get(long index) {
        return data[dataIndex(index)][dataPos(index)];
    }

    /**
     * bulk get
     *
     * @param index
     * @param bytes
     * @param offset
     * @param len
     * @return
     */
    @Override
    public int get(long index, byte[] bytes, int offset, int len) {
        for (int i = 0; i < len; i++)
            bytes[offset + i] = (byte) get(index++);
        return len;
    }

    /**
     * gets next four bytes as a single integer
     *
     * @param index
     * @return integer
     */
    @Override
    public int getInt(long index) {
        return ((get(index++) & 0xFF) << 24) + ((get(index++) & 0xFF) << 16) + ((get(index++) & 0xFF) << 8) + ((get(index) & 0xFF));
    }

    /**
     * length of array
     *
     * @return array length
     * @throws IOException
     */
    @Override
    public long limit() {
        return limit;
    }

    /**
     * close the array
     */
    @Override
    public void close() {

    }

    private int dataIndex(long index) {
        return (int) ((index >> 30));
    }

    private int dataPos(long index) {
        return (int) (index - (index >> 30) * length0);
    }
}
