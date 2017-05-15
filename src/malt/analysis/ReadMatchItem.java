/**
 * ReadMatchItem.java 
 * Copyright (C) 2017 Daniel H. Huson
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
package malt.analysis;

/**
 * a single read match
 * Daniel Huson, 8.2014
 */
public class ReadMatchItem {
    final public int refIndex;
    final public float score;
    final public int refStart;
    final public int refEnd;

    public ReadMatchItem(int refIndex, float score, int refStart, int refEnd) {
        this.refIndex = refIndex;
        this.score = score;
        this.refStart = refStart;
        this.refEnd = refEnd;
    }
}
