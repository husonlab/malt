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
