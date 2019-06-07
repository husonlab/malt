/*
 *  DataForInnerLoop.java Copyright (C) 2019. Daniel H. Huson GPL
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
package malt;

/**
 * this contains all query specific data that is passed to the inner loop of the algorithm
 * Daniel Huson, 8.2014
 */

import jloda.util.BlastMode;
import malt.data.DNA5;
import malt.data.Row;
import malt.data.Translator;
import malt.util.Utilities;

import java.io.IOException;

/**
 * this contains all query specific data that is passed to the inner loop of the algorithm
 */
public class DataForInnerLoop {
    private final BlastMode mode;
    private final boolean doForward;
    private final boolean doReverse;
    final int maxNumberOfFrames;
    final int numberOfTables;
    final String[] positiveFrameInfoString;
    final String[] negativeFrameInfoString;

    private int maxQueryLength;
    private int queryLength;
    public int numberOfFrames;
    private int[] frame;
    public byte[][] frameSequence;
    public byte[] qualityValues;
    public int[] frameSequenceLength;
    public Row[][][] frameXTableXSeed2Reference;

    /**
     * constructor
     *
     * @param mode
     * @param maxNumberOfFrames
     * @param numberOfTables
     */
    public DataForInnerLoop(BlastMode mode, boolean doForward, boolean doReverse, int maxNumberOfFrames, int numberOfTables) {
        this.mode = mode;
        this.doForward = doForward;
        this.doReverse = doReverse;
        this.maxNumberOfFrames = maxNumberOfFrames;
        this.numberOfTables = numberOfTables;
        maxQueryLength = 0;
        frame = new int[maxNumberOfFrames];
        frameSequence = new byte[maxNumberOfFrames][maxQueryLength];
        frameSequenceLength = new int[maxNumberOfFrames];
        frameXTableXSeed2Reference = new Row[maxNumberOfFrames][numberOfTables][maxQueryLength];

        // for BlastP and BlastN the frames never replace so we set them here once and for all:
        switch (mode) {
            default:
            case BlastP: {
                frame[0] = 1;
                numberOfFrames = 1;
                positiveFrameInfoString = new String[2]; // no frame info line for BlastP
                negativeFrameInfoString = null;
                break;
            }
            case BlastN: {
                int s = 0;
                if (doForward) {
                    positiveFrameInfoString = new String[2];
                    positiveFrameInfoString[1] = " Strand = Plus / Plus\n";
                    frame[s] = 1;
                    numberOfFrames++;
                    s++;
                } else
                    positiveFrameInfoString = null;
                if (doReverse) {
                    negativeFrameInfoString = new String[2];
                    frame[s] = -1;
                    negativeFrameInfoString[1] = " Strand = Minus / Plus\n";
                    numberOfFrames++;
                } else
                    negativeFrameInfoString = null;
                break;
            }
            case BlastX: {
                positiveFrameInfoString = new String[4];
                negativeFrameInfoString = new String[4];
                for (int i = 1; i <= 3; i++) {
                    positiveFrameInfoString[i] = " Frame = +" + i + "\n";
                    negativeFrameInfoString[i] = " Frame = -" + i + "\n";
                }
            }
        }
    }

    /**
     * compute frames and resize data-structures if necessary
     * For BlastP there are no frames, for BlastN there can be up to two frames, +1 and -1,
     * for BlastX the max number is 6
     *
     * @param query
     * @param queryLength
     * @throws java.io.IOException
     */
    public void computeFrames(byte[] query, byte[] queryQualityValues, int queryLength) throws IOException {
        this.queryLength = queryLength;

        switch (mode) {
            case BlastN: {
                int s = 0;
                if (doForward) {
                    frameSequence[s] = query;
                    frameSequenceLength[s] = queryLength;
                    s++;
                }
                if (doReverse) {
                    if (maxQueryLength < queryLength) {
                        frameSequence[s] = new byte[queryLength];
                    }
                    DNA5.getInstance().getReverseComplement(query, queryLength, frameSequence[s]);
                    frameSequenceLength[s] = queryLength;
                }
                qualityValues = queryQualityValues;
                break;
            }
            case BlastP:
                frameSequence[0] = query;
                frameSequenceLength[0] = queryLength;
                break;
            case BlastX:
                if (maxQueryLength < queryLength)  // don't worry about dividing by 3
                {
                    for (int s = 0; s < maxNumberOfFrames; s++)
                        frameSequence[s] = new byte[queryLength];
                }
                numberOfFrames = Translator.getBestFrames(doForward, doReverse, query, queryLength, frame, frameSequence, frameSequenceLength);
                break;
            default:
                throw new IOException("Unsupported mode: " + mode);
        }
        // resize arrays:
        if (maxQueryLength < queryLength) {
            maxQueryLength = queryLength;
            for (int s = 0; s < maxNumberOfFrames; s++) {
                for (int t = 0; t < numberOfTables; t++) {
                    frameXTableXSeed2Reference[s][t] = Utilities.resizeAndConstructEntries(frameXTableXSeed2Reference[s][t], maxQueryLength);
                }
            }
        }
    }

    public int getStartQueryForOutput(int frameRank, int startQuery) {
        switch (mode) {
            case BlastN: {
                if (frame[frameRank] == 1)
                    return startQuery + 1;
                else
                    return queryLength - startQuery;
            }
            case BlastX: {
                if (frame[frameRank] > 0)
                    return 3 * startQuery + frame[frameRank];
                else
                    return queryLength - 3 * startQuery + frame[frameRank] + 1;
            }
            default:
            case BlastP:
                return startQuery + 1;
        }
    }

    public int getEndQueryForOutput(int frameRank, int endQuery) {
        switch (mode) {
            case BlastN: {
                if (frame[frameRank] == 1)
                    return endQuery;
                else
                    return queryLength - endQuery + 1;
            }
            case BlastX: {
                if (frame[frameRank] > 0)
                    return 3 * endQuery + frame[frameRank] - 1;
                else
                    return queryLength - 3 * endQuery + frame[frameRank] + 2;
            }
            default:
            case BlastP:
                return endQuery;
        }
    }

    public String getFrameInfoLine(int frameRank) {
        int f = frame[frameRank];
        if (f > 0)
            return positiveFrameInfoString[f];
        else
            return negativeFrameInfoString[-f];
    }

    public int getFrameForFrameRank(int frameRank) {
        return frame[frameRank];
    }

    public byte[] getQualityValues() {
        return qualityValues;
    }
}

