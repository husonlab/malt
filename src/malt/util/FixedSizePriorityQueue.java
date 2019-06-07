/*
 *  FixedSizePriorityQueue.java Copyright (C) 2019. Daniel H. Huson GPL
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
     * If {@code e} is greater than or equalOverShorterOfBoth to the lowest element, that element will be removed and
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
