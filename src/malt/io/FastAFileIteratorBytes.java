/*
 *  FastAFileIteratorBytes.java Copyright (C) 2019. Daniel H. Huson GPL
 *
 *   (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package malt.io;

import jloda.util.Basic;
import jloda.util.ICloseableIterator;
import malt.data.INormalizer;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

/**
 * Reads in a multifast file and places all  headers and sequences in byte arrays
 * Daniel Huson, 8.2014
 */
public class FastAFileIteratorBytes implements Iterator<byte[]>, ICloseableIterator<byte[]> {
    private final INormalizer normalizer;
    private byte[] buffer = new byte[10000000];
    private int length = 0;
    private long position = 0;
    private final long maxProgress;
    private boolean expectingHeader = true;
    private BufferedInputStream inputStream;
    private boolean isFastQ = false;

    private boolean ok = true; // haven't seen next() fail yet
    private boolean nextIsLoaded = false; // have already loaded the next item

    /**
     * constructor
     *
     * @param fileName
     * @throws FileNotFoundException
     */
    public FastAFileIteratorBytes(final String fileName, final INormalizer normalizer) throws IOException {
        this.normalizer = normalizer;
        inputStream = new BufferedInputStream(Basic.getInputStreamPossiblyZIPorGZIP(fileName), 8192);
        maxProgress = Basic.guessUncompressedSizeOfFile(fileName);

        try {
            int value = inputStream.read();
            isFastQ = (value == '@');
        } catch (IOException e) {
        }
    }

    /**
     * has next header or sequence
     *
     * @return true if has a header or sequence
     */
    public boolean hasNext() {
        if (!ok)
            return false;
        else if (nextIsLoaded)
            return true;

        try {
            if (isFastQ) { // expect four lines per read
                try {
                    length = 0;
                    if (expectingHeader) {
                        buffer[length++] = (byte) '>';
                        int value = inputStream.read();
                        if (value == -1)
                            return ok = false;
                        if (value != '@')
                            buffer[length++] = (byte) value;
                        length = readLineIntoBuffer(inputStream, length);
                        position += length;
                        return ok = (length > 1);
                    } else {
                        length = readLineIntoBuffer(inputStream, length);
                        if (length == 0)
                            return ok = false;
                        position += length;
                        position += skipLine(inputStream); // skip comment line
                        position += skipLine(inputStream); // skip quality line
                        return ok = true;
                    }
                } catch (IOException e) {
                    return ok = false;
                }
            } else {
                int value;
                length = 0;
                boolean first = true;
                try {
                    while (true) {
                        value = inputStream.read();
                        if (expectingHeader) {
                            if (value == -1)
                                return ok = false;
                            if (first) {
                                first = false;
                                if (value != '>')
                                    buffer[length++] = '>';
                            }
                            if (value == '\n' || value == '\r') {
                                position += length;
                                return ok = (length > 0);
                            }
                        } else {
                            if (Character.isWhitespace(value))
                                continue;  // skip white space
                            if (value == '>' || value == -1) {
                                position += length;
                                return ok = (length > 0);
                            }
                        }
                        if (length >= buffer.length)
                            growBuffer();
                        buffer[length++] = (byte) value;
                    }
                } catch (IOException e) {
                    return ok = false;
                }
            }
        } finally {
            nextIsLoaded = true;
        }
    }

    /**
     * get next header or sequence
     *
     * @return header or sequence
     */
    public byte[] next() {
        try {
            if (!nextIsLoaded && !hasNext())
                return null;

            expectingHeader = !expectingHeader;
            if (length > 0 || hasNext()) {
                byte[] result = new byte[length];
                if (expectingHeader) // was not expecting header when we entered this method, so this is sequence, so normalize:
                {
                    for (int i = 0; i < length; i++)
                        result[i] = normalizer.getNormalized(buffer[i]);
                } else
                    System.arraycopy(buffer, 0, result, 0, length);
                length = 0;
                return result;
            }
            return null;
        } finally {
            nextIsLoaded = false;
        }
    }

    /**
     * read the next line into the buffer
     *
     * @param inputStream
     * @param offset
     * @return position of next available position in buffer
     */
    private int readLineIntoBuffer(BufferedInputStream inputStream, int offset) throws IOException {
        int value = inputStream.read();
        while (value != '\r' && value != '\n' && value != -1) {
            if (offset >= buffer.length) {  // need to grow buffer
                growBuffer();
            }
            buffer[offset++] = (byte) value;
            value = inputStream.read();
        }
        return offset;
    }

    /**
     * grows the line buffer
     */
    private void growBuffer() {
        byte[] nextBuffer = new byte[(int) Math.min(Integer.MAX_VALUE - 10L, 2 * buffer.length)];
        System.arraycopy(buffer, 0, nextBuffer, 0, buffer.length);
        buffer = nextBuffer;
    }

    /**
     * skip the current line
     *
     * @param inputStream
     * @throws IOException
     */
    private int skipLine(BufferedInputStream inputStream) throws IOException {
        int skipped = 0;
        int value = inputStream.read();
        while (value != '\r' && value != '\n' && value != -1) {
            value = inputStream.read();
            skipped++;
        }
        return skipped;
    }


    public void remove() {
    }

    /**
     * close the stream
     *
     * @throws IOException
     */
    public void close() throws IOException {
        inputStream.close();
    }

    /**
     * gets the maximum progress value
     *
     * @return maximum progress value
     */
    public long getMaximumProgress() {
        return maxProgress;
    }

    /**
     * gets the current progress value
     *
     * @return current progress value
     */
    public long getProgress() {
        return position;
    }

    /**
     * is the file we are reading actually a fastQ file?
     *
     * @return true, if fastQ
     */
    public boolean isFastQ() {
        return isFastQ;
    }
}
