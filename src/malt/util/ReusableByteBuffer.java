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

package malt.util;

/**
 * a reusable byte buffer
 * Daniel Huson, 8.2014
 */
public class ReusableByteBuffer {
    private byte[] bytes;
    private int pos = 0;

    /**
     * constructor
     *
     * @param size
     */
    public ReusableByteBuffer(int size) {
        bytes = new byte[size];
    }

    /**
     * write string
     *
     * @param str
     */
    public void writeAsAscii(String str) {
        if (pos + str.length() >= bytes.length) {
            bytes = resize(bytes, pos + str.length() + 1024);
        }
        for (int i = 0; i < str.length(); i++) {
            bytes[pos++] = (byte) str.charAt(i);
        }
    }

    /**
     * write bytes
     *
     * @param add
     */
    public void write(byte[] add) {
        if (pos + add.length >= bytes.length) {
            bytes = resize(bytes, pos + add.length + 1024);
        }
        System.arraycopy(add, 0, bytes, pos, add.length);
        pos += add.length;
    }

    /**
     * write char as byte
     *
     * @param add
     */
    public void write(char add) {
        if (pos + 1 >= bytes.length) {
            bytes = resize(bytes, pos + 1024);
        }
        bytes[pos++] = (byte) add;
    }

    /**
     * write byte
     *
     * @param add
     */
    public void write(byte add) {
        if (pos + 1 >= bytes.length) {
            bytes = resize(bytes, pos + 1024);
        }
        bytes[pos++] = add;
    }

    /**
     * write bytes
     *
     * @param add
     * @param offset
     * @param length
     */
    public void write(byte[] add, int offset, int length) {
        if (pos + length >= bytes.length) {
            bytes = resize(bytes, pos + length + 1024);
        }
        System.arraycopy(add, offset, bytes, pos, length);
        pos += length;
    }

    /**
     * erase
     */
    public void reset() {
        pos = 0;
    }

    /**
     * return a copy of the byte buffer
     *
     * @return copy
     */
    public byte[] makeCopy() {
        byte[] result = new byte[pos];
        System.arraycopy(bytes, 0, result, 0, pos);
        return result;
    }

    private byte[] resize(byte[] array, int newSize) {
        byte[] result = new byte[newSize];
        System.arraycopy(array, 0, result, 0, Math.min(newSize, array.length));
        return result;
    }
}
