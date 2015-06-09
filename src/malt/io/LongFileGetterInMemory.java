/**
 * Copyright 2015, Daniel Huson
 * <p/>
 * (Some files contain contributions from other authors, who are then mentioned separately)
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package malt.io;

import jloda.map.ILongGetter;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;

import java.io.*;

/**
 * long file getter in memory
 * Daniel Huson, 5.2015
 */
public class LongFileGetterInMemory implements ILongGetter {
    private final long[][] data;
    private final long limit;
    private final int length0;

    /**
     * long file getter in memory
     *
     * @param file
     * @throws IOException
     * @throws CanceledException
     */
    public LongFileGetterInMemory(File file) throws IOException {
        limit = file.length() / 8;

        data = new long[(int) ((limit >> 30)) + 1][];
        length0 = (int) (Math.min(limit, 1 << 30));
        for (int i = 0; i < data.length; i++) {
            int length = Math.min(length0, dataPos(limit) + 1);
            data[i] = new long[length];
        }

        try (InputStream ins = new BufferedInputStream(new FileInputStream(file))) {
            final ProgressPercentage progress = new ProgressPercentage("Reading file: " + file, limit);
            for (long pos = 0; pos < limit; pos++) {
                data[dataIndex(pos)][dataPos(pos)] = (((long) (ins.read()) & 0xFF) << 56) + (((long) (ins.read()) & 0xFF) << 48) + (((long) (ins.read()) & 0xFF) << 40) + (((long) (ins.read()) & 0xFF) << 32)
                        + (((long) (ins.read()) & 0xFF) << 24) + (((long) (ins.read()) & 0xFF) << 16) + (((long) (ins.read()) & 0xFF) << 8) + (((long) (ins.read()) & 0xFF));
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
    public long get(long index) {
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
