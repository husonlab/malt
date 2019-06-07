/*
 *  SAMHelper.java Copyright (C) 2019. Daniel H. Huson GPL
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

import jloda.util.BlastMode;
import malt.data.DNA5;

/**
 * helps to create a SAM line from an alignment
 * Daniel Huson, 8.2014
 */
public class SAMHelper {
    private static final String FILE_HEADER_BLASTN_TEMPLATE = "@HD\tVN:1.5\tSO:unsorted\tGO:query\n@PG\tID:1\tPN:MALT\tCL:%s\tDS:BlastN\n@RG\tID:1\tPL:unknown\tSM:unknown\n@CO\tBlastN-like alignments\n" +
            "@CO\tReporting AS: bitScore, ZR: rawScore, ZE: expected, ZI: percent identity, ZL: reference length\n";
    private static final String FILE_HEADER_BLASTP_TEMPLATE = "@HD\tVN:1.5\tSO:unsorted\tGO:query\n@PG\tID:1\tPN:MALT\tCL:%s\tDS:BlastP\n@RG\tID:1\tPL:unknown\tSM:unknown\n@CO\tBlastP-like alignments\n" +
            "@CO\tReporting AS: bitScore, ZR: rawScore, ZE: expected, ZI: percent identity, ZL: reference length\n";
    private static final String FILE_HEADER_BLASTX_TEMPLATE = "@HD\tVN:1.5\tSO:unsorted\tGO:query\n@PG\tID:1\tPN:MALT\tCL:%s\tDS:BlastX\n@RG\tID:1\tPL:unknown\tSM:unknown\n@CO\tBlastX-like alignments\n" +
            "@CO\tReporting AS: bitScore, ZR: rawScore, ZE: expected, ZI: percent identity, ZL: reference length, ZF: frame, ZS: query start DNA coordinate\n";

    /*
   0	QNAME	String
   1	FLAG	Int
   2	RNAME	String
   3	POS	Int
   4	MAPQ	Int
   5	CIGAR	String
   6	RNEXT	String
   7	PNEXT	Int
   8	TLEN	Int
   9	SEQ	String
   10	QUAL	String Regexp/Range [!-?A-~]{1,255} [0,216 -1] \*|[!-()+-<>-~][!-~]* [0,229 -1][0,28 -1] \*|([0-9]+[MIDNSHPX=])+ \*|=|[!-()+-<>-~][!-~]* [0,229 -1] [-229 +1,229 -1] \*|[A-Za-z=.]+ [!-~]+
   11 additional stuff including score and MD
    */

    /**
     * creates a SAM line. If queryHeader==null, does not output the initial query token
     *
     * @param mode
     * @param queryHeader
     * @param queryStart
     * @param queryStartBlastX
     * @param queryEnd
     * @param queryLength
     * @param alignedQuery
     * @param referenceHeader
     * @param referenceStart
     * @param referenceEnd
     * @param alignedReference
     * @param referenceLength
     * @param bitScore
     * @param rawScore
     * @param expected
     * @param percentIdentity
     * @param frame
     * @param softClipped
     * @return
     */
    public static String createSAMLine(final BlastMode mode, final byte[] queryHeader, final byte[] querySequence, final int queryStart, final int queryStartBlastX, final int queryEnd, final int queryLength, final byte[] alignedQuery,
                                       final byte[] referenceHeader, final int referenceStart, final int referenceEnd, final byte[] alignedReference, final int referenceLength,
                                       final double bitScore, final int rawScore, final double expected, final float percentIdentity, int frame, final byte[] qualityValues, boolean softClipped) {

        if (querySequence == null && softClipped)
            softClipped = false;

        final StringBuilder buffer = new StringBuilder();

        // QNAME:
        boolean first = true;
        if (queryHeader != null) {
            for (byte a : queryHeader) {
                if (first && a == '>') {
                    first = false;
                    continue;
                }
                if (a == 0 || Character.isSpaceChar(a))
                    break;
                buffer.append((char) a);
            }
            buffer.append('\t');
        }

        // FLAG
        final boolean reverseComplemented = ((queryStart < queryEnd) != (referenceStart < referenceEnd));
        final int queryOffset;

        switch (mode) {
            case BlastN:
                if (reverseComplemented) {
                    queryOffset = queryLength - queryEnd;
                    buffer.append(0x10); // SEQ is reverse complemented
                } else {
                    queryOffset = queryStart;
                    buffer.append(0);
                }
                break;
            case BlastX:
                if (reverseComplemented)
                    buffer.append(0x10); // SEQ is reverse complemented
                else
                    buffer.append(0);
                queryOffset = 0;  // will explicitly save query start and query end
                break;
            default:
            case BlastP:
                queryOffset = queryStart;
                buffer.append(0);
        }
        buffer.append('\t');

        // RNAME:
        first = true;
        for (byte a : referenceHeader) {
            if (first && a == '>') {
                first = false;
                continue;
            }
            if (a == 0 || Character.isSpaceChar(a))
                break;
            buffer.append((char) a);
        }
        buffer.append('\t');

        // POS:
        int pos = Math.min(referenceStart, referenceEnd);
        buffer.append(pos);
        buffer.append('\t');

        // MAPQ
        buffer.append("255"); // unknown
        buffer.append('\t');

        // CIGAR
        appendCigar(alignedQuery, queryOffset, queryLength, alignedReference, reverseComplemented, softClipped, buffer);
        buffer.append('\t');

        // RNEXT
        buffer.append("*"); // unknown
        buffer.append('\t');

        // PNEXT
        buffer.append("0"); // unknown
        buffer.append('\t');

        // TLEN
        buffer.append("0");
        buffer.append('\t');

        // SEQ
        if (softClipped && querySequence != null) {
            if (reverseComplemented) {
                for (int i = queryLength - 1; i >= 0; i--) {
                    buffer.append((char) DNA5.getInstance().getBaseComplement(querySequence[i]));
                }
            } else {
                for (int i = 0; i < queryLength; i++)
                    buffer.append((char) querySequence[i]);
            }
        } else {
            if (reverseComplemented) {
                for (int i = alignedQuery.length - 1; i >= 0; i--) {
                    byte a = alignedQuery[i];
                    if (a != '-')
                        buffer.append((char) DNA5.getInstance().getBaseComplement(a));
                }
            } else {
                for (byte a : alignedQuery) {
                    if (a != '-')
                        buffer.append((char) a);
                }
            }
        }
        buffer.append('\t');

        // QUAL
        if (qualityValues == null)
            buffer.append("*");
        else {
            if (softClipped) {
                if (reverseComplemented) {
                    for (int i = queryLength - 1; i >= 0; i--)
                        buffer.append((char) qualityValues[i]);
                } else {
                    for (int i = 0; i < queryLength; i++)
                        buffer.append((char) qualityValues[i]);
                }
            } else {
                if (reverseComplemented) {
                    for (int i = queryStart; i < queryEnd; i++)
                        buffer.append((char) qualityValues[queryLength - (i + 1)]);
                } else {
                    for (int i = queryStart; i < queryEnd; i++)
                        buffer.append((char) qualityValues[i]);
                }
            }
        }
        buffer.append('\t');

        // optional stuff:
        buffer.append(String.format("AS:i:%d\t", (int) Math.round(bitScore)));
        buffer.append(String.format("NM:i:%d\t", computeEditDistance(alignedQuery, alignedReference)));
        buffer.append(String.format("ZL:i:%d\t", referenceLength));
        buffer.append(String.format("ZR:i:%d\t", rawScore));
        buffer.append(String.format("ZE:f:%g\t", (float) expected));
        buffer.append(String.format("ZI:i:%d\t", (int) Math.round(percentIdentity)));
        if (mode == BlastMode.BlastX) {
            buffer.append(String.format("ZF:i:%d\t", frame));
            buffer.append(String.format("ZS:i:%d\t", queryStartBlastX));
        }

        appendMDString(alignedQuery, alignedReference, reverseComplemented, buffer);

        return buffer.toString();
    }

    /**
     * append the cigar string
     *
     * @param alignedQuery
     * @param queryOffset
     * @param queryLength
     * @param alignedReference
     * @param reverseComplemented
     * @param softClipped
     * @param buffer
     */
    private static void appendCigar(byte[] alignedQuery, int queryOffset, int queryLength, byte[] alignedReference, boolean reverseComplemented, boolean softClipped, StringBuilder buffer) {
        int clip = (!reverseComplemented ? queryOffset : (queryLength - queryOffset - alignedQuery.length));
        if (clip > 0) {
            buffer.append(clip).append(softClipped ? "S" : "H");
        }

        if (reverseComplemented) {
            char state = 'M'; // M in match, I insert, D deletion
            int count = 0;
            for (int i = alignedQuery.length - 1; i >= 0; i--) {
                if (alignedQuery[i] == '-') {
                    if (state == 'D') {
                        count++;
                    } else if (count > 0) {
                        buffer.append(count).append(state);
                        state = 'D';
                        count = 1;
                    }
                } else if (alignedReference[i] == '-') {
                    if (state == 'I') {
                        count++;
                    } else if (count > 0) {
                        buffer.append(count).append(state);
                        state = 'I';
                        count = 1;
                    }
                } else {  // match or mismatch
                    if (state == 'M') {
                        count++;
                    } else if (count > 0) {
                        buffer.append(count).append(state);
                        state = 'M';
                        count = 1;
                    }
                }
            }
            if (count > 0) {
                buffer.append(count).append(state);

            }
        } else {
            char cigarState = 'M'; // M in match, D deletion, I insertion
            int count = 0;
            for (int i = 0; i < alignedQuery.length; i++) {
                if (alignedQuery[i] == '-') {
                    if (cigarState == 'D') {
                        count++;
                    } else if (count > 0) {
                        buffer.append(count).append(cigarState);
                        cigarState = 'D';
                        count = 1;
                    }
                } else if (alignedReference[i] == '-') {
                    if (cigarState == 'I') {
                        count++;
                    } else if (count > 0) {
                        buffer.append(count).append(cigarState);
                        cigarState = 'I';
                        count = 1;
                    }
                } else {  // match or mismatch
                    if (cigarState == 'M') {
                        count++;
                    } else if (count > 0) {
                        buffer.append(count).append(cigarState);
                        cigarState = 'M';
                        count = 1;
                    }
                }
            }
            if (count > 0) {
                buffer.append(count).append(cigarState);

            }
        }

        clip = (reverseComplemented ? queryOffset : (queryLength - queryOffset - alignedQuery.length));
        if (clip > 0) {
            buffer.append(clip).append(softClipped ? "S" : "H");
        }
    }

    /**
     * append the MD string
     *
     * @param alignedQuery
     * @param alignedReference
     * @param reverseComplemented
     * @param buffer
     */
    private static void appendMDString(final byte[] alignedQuery, final byte[] alignedReference, final boolean reverseComplemented, final StringBuilder buffer) {
        buffer.append("MD:Z:");
        if (reverseComplemented) {
            int countMatches = 0;
            boolean inDeletion = false;
            for (int i = alignedQuery.length - 1; i >= 0; i--) {
                if (alignedQuery[i] == '-') { // gap in query
                    if (countMatches > 0) {
                        buffer.append(countMatches);
                        countMatches = 0;
                    }
                    if (!inDeletion) {
                        buffer.append("^");
                        inDeletion = true;
                    }
                    buffer.append((char) (DNA5.getInstance().getBaseComplement(alignedReference[i])));
                } else if (alignedReference[i] != '-') {  // match or mismatch
                    if (alignedQuery[i] == alignedReference[i]) {
                        countMatches++;
                    } else {
                        if (inDeletion)
                            buffer.append(0);
                        if (countMatches > 0) {
                            buffer.append(countMatches);
                            countMatches = 0;
                        }
                        buffer.append((char) (DNA5.getInstance().getBaseComplement(alignedReference[i])));
                    }
                    if (inDeletion)
                        inDeletion = false;
                }
                // else alignedReference[i] == '-': this has no effect
            }
            if (countMatches > 0)
                buffer.append(countMatches);
            else if (inDeletion)
                buffer.append(0);
        } else {
            int countMatches = 0;
            boolean inDeletion = false;
            for (int i = 0; i < alignedQuery.length; i++) {
                if (alignedQuery[i] == '-') { // gap in query
                    if (countMatches > 0) {
                        buffer.append(countMatches);
                        countMatches = 0;
                    }
                    if (!inDeletion) {
                        buffer.append("^");
                        inDeletion = true;
                    }
                    buffer.append((char) alignedReference[i]);
                } else if (alignedReference[i] != '-') {  // match or mismatch
                    if (alignedQuery[i] == alignedReference[i]) {
                        countMatches++;
                    } else {
                        if (inDeletion)
                            buffer.append("0");
                        if (countMatches > 0) {
                            buffer.append(countMatches);
                            countMatches = 0;
                        }
                        buffer.append((char) alignedReference[i]);
                    }
                    if (inDeletion)
                        inDeletion = false;
                }
                // else alignedReference[i] == '-': this has no effect
            }
            if (countMatches > 0)
                buffer.append(countMatches);
            else if (inDeletion)
                buffer.append(0);
        }
    }

    /**
     * compute edit distance from alignment
     *
     * @param alignedQuery
     * @param alignedReference
     * @return edit distance
     */
    private static int computeEditDistance(byte[] alignedQuery, byte[] alignedReference) {
        int distance = 0;
        for (int i = 0; i < alignedQuery.length; i++) {
            if (alignedQuery[i] == '-' || alignedReference[i] == '-' || alignedQuery[i] != alignedReference[i])
                distance++;
        }
        return distance;
    }

    /**
     * gets the SAM header line
     *
     * @param mode
     * @return SAM header line or null
     */
    public static String getSAMHeader(BlastMode mode, String commandLine) {
        switch (mode) {
            case BlastN:
                return String.format(FILE_HEADER_BLASTN_TEMPLATE, (commandLine != null ? commandLine : ""));
            case BlastP:
                return String.format(FILE_HEADER_BLASTP_TEMPLATE, (commandLine != null ? commandLine : ""));
            case BlastX:
                return String.format(FILE_HEADER_BLASTX_TEMPLATE, (commandLine != null ? commandLine : ""));
            default:
                return "???";
        }
    }
}
