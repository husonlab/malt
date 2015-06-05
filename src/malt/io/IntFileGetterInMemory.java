package malt.io;

import jloda.map.IIntGetter;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;

import java.io.*;

/**
 * long file getter in memory
 * Daniel Huson, 5.2015
 */
public class IntFileGetterInMemory implements IIntGetter {
    private final int[][] data;
    private final long limit;
    private final int length0;

    /**
     * long file getter in memory
     *
     * @param file
     * @throws IOException
     * @throws CanceledException
     */
    public IntFileGetterInMemory(File file) throws IOException {
        limit = file.length() / 4;

        data = new int[(int) ((limit >> 30)) + 1][];
        length0 = (int) (Math.min(limit, 1 << 30));
        for (int i = 0; i < data.length; i++) {
            int length = Math.min(length0, dataPos(limit) + 1);
            data[i] = new int[length];
        }

        try (InputStream ins = new BufferedInputStream(new FileInputStream(file))) {
            final ProgressPercentage progress = new ProgressPercentage("Reading file: " + file, limit);
            for (long pos = 0; pos < limit; pos++) {
                data[dataIndex(pos)][dataPos(pos)] = ((ins.read() & 0xFF) << 24) + (((ins.read()) & 0xFF) << 16) + (((ins.read()) & 0xFF) << 8) + (ins.read() & 0xFF);
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
