/**
 * IntStorageCreator.java 
 * Copyright (C) 2015 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package malt.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

/**
 * store integers in a long indexible array that uses memory mapping
 * Created by huson on 6/18/14.
 */
public class IntStorageCreator {
    private final String fileName;
    private final IntBuffer[] segments;
    private final RandomAccessFile[] rafs;
    private final int MAX_SEGMENTS = 1000;
    private final int SEGMENT_SIZE = 10; //Integer.MAX_VALUE;

    private int numberOfSegments = 0;
    private int currentPosition = SEGMENT_SIZE;

    public IntStorageCreator(String fileName) throws IOException {
        this.fileName = fileName;
        segments = new IntBuffer[MAX_SEGMENTS];
        rafs = new RandomAccessFile[MAX_SEGMENTS];
    }

    private void createNewSegment() throws IOException {
        currentPosition = 0;
        final String file = String.format("%s-%04d", fileName, numberOfSegments);
        rafs[numberOfSegments] = (new RandomAccessFile(file, "rw"));
        segments[numberOfSegments] = rafs[numberOfSegments].getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 4 * SEGMENT_SIZE).asIntBuffer();
        numberOfSegments++;
    }

    /**
     * adds a value to end of array
     *
     * @param value
     * @throws IOException
     */
    public void add(int value) throws IOException {
        if (currentPosition == SEGMENT_SIZE)
            createNewSegment();
        segments[numberOfSegments - 1].put(currentPosition++, value);
    }

    /**
     * current size of array
     *
     * @return size
     */
    public int size() {
        if (numberOfSegments == -1)
            return 0;
        else
            return (numberOfSegments - 1) * SEGMENT_SIZE + currentPosition;
    }

    /**
     * puts a specific value
     *
     * @param segment
     * @param position
     * @param value
     * @throws IOException
     */
    public void put(int segment, int position, int value) throws IOException {
        while (segment >= numberOfSegments)
            createNewSegment();
        if (position > currentPosition)
            currentPosition = position;
        segments[segment].put(position, value);
    }

    public int get(int segment, int position) {
        return segments[segment].get(position);
    }

    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            int segment = 0;
            int pos = 0;

            @Override
            public boolean hasNext() {
                return segment < numberOfSegments;
            }

            @Override
            public Integer next() {
                int value = segments[segment].get(pos++);
                if ((segment < numberOfSegments - 1 && pos >= segments[segment].limit()) || (segment == numberOfSegments - 1 && pos == currentPosition)) {
                    segment++;
                    pos = 0;
                }
                return value;
            }

            @Override
            public void remove() {

            }
        };
    }

    /**
     * close all the file challenges
     *
     * @throws IOException
     */
    public void close() throws IOException {
        for (int i = 0; i < numberOfSegments; i++) {
            if (i == numberOfSegments - 1)
                rafs[i].setLength(currentPosition);
            rafs[i].close();
        }
    }

    public static void main(String[] args) throws IOException {
        IntStorageCreator storage = new IntStorageCreator("/Users/huson/x");

        for (int i = 0; i < 30; i++) {
            storage.add(i);
        }

        for (Iterator<Integer> it = storage.iterator(); it.hasNext(); ) {
            System.err.print(" " + (it.next()));
        }
        System.err.println();
    }
}
