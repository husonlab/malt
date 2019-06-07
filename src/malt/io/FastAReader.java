/*
 *  FastAReader.java Copyright (C) 2019. Daniel H. Huson GPL
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
import jloda.util.ProgressPercentage;
import malt.data.DNA5;
import malt.data.IAlphabet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reads in a multifastA (or fastQ) file and places all  headers and sequences in byte arrays. In addition, the headers and sequences are 0-terminated
 * Daniel Huson, 8.2014
 */
public class FastAReader {
    final public static int BUFFER_SIZE = 8192;

    private final IAlphabet alphabet;

    private long position = 0;
    private long maxProgress = 0;
    private int readCount = 0;
    private final BufferedInputStream inputStream;
    private boolean isFastQ = false;

    private final ProgressPercentage progress;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * constructor
     *
     * @param fileName
     * @throws java.io.FileNotFoundException
     */
    public FastAReader(final String fileName, final IAlphabet alphabet) throws IOException {
        this(fileName, alphabet, null);
    }

    /**
     * constructor
     *
     * @param fileName
     * @param progress
     * @throws java.io.FileNotFoundException
     */
    public FastAReader(final String fileName, final IAlphabet alphabet, final ProgressPercentage progress) throws IOException {
        this.alphabet = alphabet;

        maxProgress = Basic.guessUncompressedSizeOfFile(fileName);

        this.progress = progress;
        if (progress != null) {
            progress.setMaximum(maxProgress);
            progress.setProgress(0);
        }

        // determine file type:
        {
            InputStream tmp = new BufferedInputStream(Basic.getInputStreamPossiblyZIPorGZIP(fileName));
            int value = tmp.read();
            if (value != '@' && value != '>')
                throw new IOException("Input file '" + fileName + "' does not appear to be in FastA or FastQ format, as it does not start with a '>' or '@'");
            isFastQ = (value == '@');
            tmp.close();
        }
        inputStream = new BufferedInputStream(Basic.getInputStreamPossiblyZIPorGZIP(fileName), BUFFER_SIZE);
    }

    /**
     * read the next record as fastA. Can be applied to both fastA and fastQ files.
     * Header and sequence are both 0-terminated.
     * This method is thread safe
     *
     * @param fastARecord
     * @return true if read
     * @throws IOException
     */
    public boolean readAsFastA(FastARecord fastARecord) throws IOException {
        lock.lock();
        try {
            if (isFastQ) { // expect four lines per read
                readHeader(fastARecord); // read header
                if (fastARecord.getHeaderLength() == 0)
                    return false; // done
                fastARecord.getHeader()[0] = '>';
                readSequence(fastARecord);
                if (fastARecord.getSequenceLength() == 0)
                    return false; // done
                skipLine();
                if (fastARecord.isWantQualityValues()) {
                    if (readQualityValues(fastARecord) != fastARecord.sequenceLength)
                        throw new IOException("Error reading quality values: wrong number of bytes");
                } else
                    skipLine();
                fastARecord.setId(++readCount);
                return true;
            } else { // fastA
                readHeader(fastARecord); // read header

                byte[] sequence = fastARecord.sequence;
                // read the sequence, which might be spread over multiple lines
                int value;
                int length = 0;
                while (true) {
                    value = inputStream.read();
                    position++;
                    if (Character.isWhitespace(value))
                        continue;  // skip white space
                    if (value == '>' || value == -1) {
                        position += length;
                        sequence[length] = 0;
                        fastARecord.sequenceLength = length;
                        if (length == 0)
                            return false;
                        else {
                            fastARecord.setId(++readCount);
                            return true;
                        }
                    }
                    sequence[length++] = alphabet.getNormalized((byte) value);
                    if (length >= sequence.length)
                        sequence = fastARecord.sequence = grow(sequence);
                }
            }
        } finally {
            if (progress != null)
                progress.setProgress(position);
            lock.unlock();
        }
    }

    /**
     * reads the header line
     *
     * @param fastARecord
     * @throws IOException
     */
    private void readHeader(FastARecord fastARecord) throws IOException {
        byte[] aline = fastARecord.header;
        int value = inputStream.read();
        position++;
        int length = 0;
        while (value != '\r' && value != '\n' && value != -1) {
            if (length == 0 && isFastQ)
                value = '>';
            aline[length++] = (byte) value;
            value = inputStream.read();
            position++;
            if (length >= aline.length) {  // need to grow buffer
                aline = fastARecord.header = grow(aline);
            }
        }
        aline[length] = 0;
        fastARecord.headerLength = length;
    }

    /**
     * reads the sequence line
     *
     * @param fastARecord
     * @throws IOException
     */
    private void readSequence(FastARecord fastARecord) throws IOException {
        byte[] aline = fastARecord.sequence;
        int value = inputStream.read();
        position++;
        int length = 0;
        while (value != '\r' && value != '\n' && value != -1) {
            aline[length++] = alphabet.getNormalized((byte) value);
            value = inputStream.read();
            position++;
            if (length >= aline.length) {  // need to grow buffer
                aline = fastARecord.sequence = grow(aline);
            }
        }
        aline[length] = 0;
        fastARecord.sequenceLength = length;
    }

    /**
     * reads the quality values line
     *
     * @param fastARecord
     * @return the number of letters read
     * @throws IOException
     */
    private int readQualityValues(FastARecord fastARecord) throws IOException {
        byte[] aline = fastARecord.qualityValues;
        int value = inputStream.read();
        position++;
        int length = 0;
        while (value != '\r' && value != '\n' && value != -1) {
            aline[length++] = (byte) value;
            value = inputStream.read();
            position++;
            if (length >= aline.length) {  // need to grow buffer
                aline = fastARecord.qualityValues = grow(aline);
            }
        }
        aline[length] = 0;
        return length;
    }

    /**
     * grow the array
     *
     * @param bytes
     * @return bigger copy of array
     */
    private byte[] grow(byte[] bytes) {
        byte[] result = new byte[Math.min(Integer.MAX_VALUE >> 1, 2 * bytes.length)];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return result;
    }

    /**
     * skip the current line
     *
     * @throws java.io.IOException
     */
    private void skipLine() throws IOException {
        int value = inputStream.read();
        position++;
        while (value != '\r' && value != '\n' && value != -1) {
            value = inputStream.read();
            position++;
        }
    }

    /**
     * close the stream
     *
     * @throws java.io.IOException
     */
    public void close() throws IOException {
        inputStream.close();
        if (progress != null)
            progress.reportTaskCompleted();
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

    /**
     * create a fastA record to be used with this reader
     *
     * @param initialLength
     * @return fastA record
     */
    public static FastARecord createFastARecord(int initialLength, boolean wantQualityValues) {
        return new FastARecord(initialLength, wantQualityValues);
    }

    public static void main(String[] args) throws IOException {
        FastAReader reader = new FastAReader("/Users/huson/data/ma/input/more.fna", DNA5.getInstance());
        FastARecord fastARecord = createFastARecord(1000, false);
        while (reader.readAsFastA(fastARecord)) {
            System.err.print(fastARecord);
        }
    }
}

