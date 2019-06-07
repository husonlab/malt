/*
 *  Row.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt.data;

/**
 * a row of numbers that are stored in a larger array.
 * Daniel Huson, 8.2014
 */

public class Row {
    private int size;
    private int offset;
    private int[] containingArray;

    public int[] tmpArray = new int[10000]; // // todo temporary array used during implementation of memory mapped index

    /**
     * constructor
     */
    public Row() {
    }

    /**
     * Set the row. array[offset] must contain size, i.e. the number of integers to be used
     * array[offset+1]... array[offset+size-1] are the numbers
     *
     * @param array  array containing size followed by entries
     * @param offset location of size entry in array
     */
    public void set(int[] array, int offset) {
        this.size = array[offset];
        this.containingArray = array;
        this.offset = offset + 1;
    }

    /**
     * setting a single pair of numbers
     *
     * @param refId
     * @param position
     */
    public void setPair(int refId, int position) {
        size = 2;
        offset = 0;
        tmpArray[0] = refId;
        tmpArray[1] = position;
        containingArray = tmpArray;
    }

    /**
     * set to empty
     */
    public void setEmpty() {
        size = 0;
    }

    /**
     * gets the number of int in this row
     *
     * @return size
     */
    public int size() {
        return size;
    }

    /**
     * use this to access numbers 0,..,size-1
     *
     * @param index
     * @return item
     */
    public int get(int index) {
        return containingArray[offset + index];
    }

    /**
     * get offset at which numbers start (position of size entry plus 1)
     *
     * @return offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * get string representation
     *
     * @return
     */
    public String toString() {
        if (size > 0) {
            final StringBuilder buf = new StringBuilder();
            buf.append("(").append(size()).append("): ");
            for (int i = 0; i < size(); i += 2)
                buf.append(" ").append(get(i)).append("/").append(get(i + 1));
            return buf.toString();
        } else
            return "null";
    }
}
