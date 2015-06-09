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

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * A priority queue implementation with a fixed size
 */
public class FixedSizePriorityQueue<E> {
    private final PriorityQueue<E> priorityQueue; /* backing data structure */
    private final Comparator<? super E> comparator;
    private final int maxSize;

    /**
     * Constructs a {@link FixedSizePriorityQueue} with the specified {@code maxSize}
     * and {@code comparator}.
     *
     * @param maxSize    - The maximum size the queue can reach, must be a positive integer.
     * @param comparator - The comparator to be used to compare the elements in the queue, must be non-null.
     */
    public FixedSizePriorityQueue(final int maxSize, final Comparator<? super E> comparator) {
        super();
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize = " + maxSize + "; expected a positive integer.");
        }
        if (comparator == null) {
            throw new NullPointerException("Comparator is null.");
        }
        this.maxSize = maxSize;
        this.comparator = comparator;

        this.priorityQueue = new PriorityQueue<>(maxSize, comparator);
    }

    /**
     * Adds an element to the queue. If the queue contains {@code maxSize} elements, {@code e} will
     * be compared to the lowest element in the queue using {@code comparator}.
     * If {@code e} is greater than or equal to the lowest element, that element will be removed and
     * {@code e} will be added instead. Otherwise, the queue will not be modified
     * and {@code e} will not be added.
     *
     * @param e - Element to be added, must be non-null.
     * @return returns true if added
     */
    public boolean add(final E e) {
        if (e == null) {
            throw new NullPointerException("e is null.");
        }
        if (priorityQueue.size() >= maxSize) {
            if (comparator.compare(e, priorityQueue.peek()) <= 0)
                return false;
            priorityQueue.poll(); // remove smallest element
        }
        return priorityQueue.add(e);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public E poll() {
        return priorityQueue.poll();
    }

    public int size() {
        return priorityQueue.size();
    }

    public void clear() {
        priorityQueue.clear();
    }

    public boolean remove(E entry) {
        return priorityQueue.remove(entry);
    }

    /**
     * get as collection
     *
     * @return collection
     */
    public java.util.Collection<E> getCollection() {
        return priorityQueue;
    }
}
