/*
 *  BuildRow.java Copyright (C) 2019. Daniel H. Huson GPL
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

public class BuildRow {
    private int size;
    private int offset;
    private int[] containingArray;
    private final int[] pair = new int[]{0, 0};

    /**
     * constructor
     */
    public BuildRow() {
    }

    /**
     * Set the row. array[offset] must contain size, i.e. the number of integers to be used
     * array[offset+1]... array[offset+size-1] are the numbers
     *
     * @param array  array containing size followed by entries
     * @param offset location of of size entry in array
     */
    public void set(int[] array, int offset) {
        this.size = array[offset];
        this.containingArray = array;
        this.offset = offset + 1;
    }

    /**
     * setting a single pair of numbers
     *
     * @param firstNumber
     * @param secondNumber
     */
    public void setPair(int firstNumber, int secondNumber) {
        size = 2;
        offset = 0;
        pair[0] = firstNumber;
        pair[1] = secondNumber;
        containingArray = pair;
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
     * get array that contains numbers
     *
     * @return full row
     */
    public int[] getContainingArray() {
        return containingArray;
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
            StringBuilder buf = new StringBuilder();
            buf.append("(").append(size()).append("): ");
            for (int i = 0; i < size(); i++)
                buf.append(" ").append(get(i));
            return buf.toString();
        } else
            return "null";
    }
}
