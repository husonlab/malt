/*
 *  ReferencesHashTableAccess.java Copyright (C) 2019. Daniel H. Huson GPL
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

import jloda.thirdparty.MurmurHash3;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressPercentage;
import malt.MaltOptions;
import malt.util.Utilities;
import megan.io.*;
import megan.io.experimental.IntFileGetterPagedMemory;
import megan.io.experimental.LongFileGetterPagedMemory;

import java.io.*;

/**
 * hash table used for mapping k-mers to sequences and offsets (given by a pair of integers)
 * Daniel Huson, 8.2014
 */

public class ReferencesHashTableAccess implements Closeable {
    public static int BUFFER_SIZE = 8192;  // benchmarking suggested that choosing a large size doesn't make a difference
    private final ILongGetter tableIndexGetter; // each entry points to a row of integers that is contained in the data table

    private final int tableSize;
    private final int hashMask;

    private final int randomNumberSeed;

    private long theSize = 0; // counts items

    private final IAlphabet seedAlphabet;  // alphabet used by seeds
    private final SeedShape seedShape; //  seed shape that is saved and loaded from index

    private IIntGetter tableDataGetter; // used for memory mapping

    /**
     * construct the table from the given directory
     *
     * @param indexDirectory
     */
    public ReferencesHashTableAccess(MaltOptions.MemoryMode memoryMode, String indexDirectory, int tableNumber) throws IOException, CanceledException {
        final File indexFile = new File(indexDirectory, "index" + tableNumber + ".idx");
        final File tableIndexFile = new File(indexDirectory, "table" + tableNumber + ".idx");
        final File tableDataFile = new File(indexDirectory, "table" + tableNumber + ".db");

        try (DataInputStream ins = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile), BUFFER_SIZE))) {
            ProgressPercentage progress = new ProgressPercentage("Reading file: " + indexFile);
            Basic.readAndVerifyMagicNumber(ins, ReferencesHashTableBuilder.MAGIC_NUMBER);

            SequenceType referenceSequenceType = SequenceType.valueOf(ins.readInt());
            System.err.println("Reference sequence type: " + referenceSequenceType.toString());
            if (referenceSequenceType == SequenceType.Protein) {
                int length = ins.readInt();
                byte[] reductionBytes = new byte[length];
                if (ins.read(reductionBytes, 0, length) != length)
                    throw new IOException("Read failed");
                seedAlphabet = new ReducedAlphabet(Basic.toString(reductionBytes));
                System.err.println("Protein reduction: " + seedAlphabet);
            } else
                seedAlphabet = DNA5.getInstance();

            // get all sizes:
            tableSize = ins.readInt();
            // get mask used in hashing
            hashMask = ins.readInt();

            randomNumberSeed = ins.readInt();
            theSize = ins.readLong();
            final int stepSize = ins.readInt();
            if (stepSize > 1)
                System.err.println("Index was built using stepSize=" + stepSize);

            {
                int length = ins.readInt();
                byte[] shapeBytes = new byte[length];
                if (ins.read(shapeBytes, 0, length) != length)
                    throw new IOException("Read failed");
                seedShape = new SeedShape(seedAlphabet, shapeBytes);
            }

            progress.reportTaskCompleted();
        }

        switch (memoryMode) {
            default:
            case load:
                tableIndexGetter = new LongFileGetterInMemory(tableIndexFile);
                tableDataGetter = new IntFileGetterInMemory(tableDataFile);
                break;
            case page:
                tableIndexGetter = new LongFileGetterPagedMemory(tableIndexFile);
                tableDataGetter = new IntFileGetterPagedMemory(tableDataFile);
                break;
            case map:
                tableIndexGetter = new LongFileGetterMappedMemory(tableIndexFile);
                tableDataGetter = new IntFileGetterMappedMemory(tableDataFile);
                break;
        }
    }

    /**
     * lookup all entries for a given key and put them in the given row object. If none found, row is set to empty
     * todo: re-implement this
     *
     * @param key
     * @param row
     */
    public int lookup(byte[] key, Row row) throws IOException {
        int hashValue = getHash(key);
        if (hashValue >= 0 && hashValue < tableIndexGetter.limit() && setRow(tableIndexGetter.get(hashValue), row))
            return row.size();
        row.setEmpty();
        return 0;
    }

    /**
     * get the hash value
     *
     * @param key
     * @return hash value
     */
    public int getHash(byte[] key) {
        int value = MurmurHash3.murmurhash3x8632(key, 0, key.length, randomNumberSeed) & hashMask;
        if (value >= Basic.MAX_ARRAY_SIZE) // only use modulo if we are on or above table size
            value %= Basic.MAX_ARRAY_SIZE;
        return value;
    }

    /**
     * get the number of entries
     *
     * @return number of entries
     */
    public long size() {
        return theSize;
    }

    /**
     * get the seed shape associated with this table
     *
     * @return seed shape
     */
    public SeedShape getSeedShape() {
        return seedShape;
    }

    /**
     * show the whole hash table in human readable form
     *
     * @throws java.io.IOException
     */
    public void show() throws IOException {
        System.err.println("Table (" + tableSize + "):");

        Row row = new Row();

        for (int z = 0; z < tableIndexGetter.limit(); z++) {
            if (z > 50)
                continue;
            System.err.print("hash " + z + " -> ");
            if (setRow(tableIndexGetter.get(z), row)) {
                System.err.print("(" + row.size() / 2 + ")");
                for (int i = 0; i < row.size(); i += 2) {
                    if (i > 100) {
                        System.err.print(" ...");
                        break;
                    }
                    System.err.print(" " + row.get(i) + "/" + row.get(i + 1));
                }
            }
            System.err.println();
        }
    }

    /**
     * set the row for the given location
     *
     * @param location
     * @param row
     * @return false, if location invalid
     */
    private boolean setRow(long location, Row row) throws IOException {
        if (location == 0)
            return false;
        if (location < 0) {
            location = -location;
            row.setPair((int) (location >> 32), (int) location); // is a singleton entry
        } else {
            int length = tableDataGetter.get(location); // length is number int's that follow this first int that tells us the length
            if (row.tmpArray.length <= length)
                row.tmpArray = new int[length + 1];
            row.tmpArray[0] = length;
            for (int i = 1; i <= length; i++)
                row.tmpArray[i] = tableDataGetter.get(location + i);
            row.set(row.tmpArray, 0);
        }
        return true;
    }

    /**
     * get alphabet used for seeds. Note that the seed alphabet may differ from the query alphabet i.e. when using a protein reduction alphabet for seeding
     *
     * @return seed alphabet
     */
    public IAlphabet getSeedAlphabet() {
        return seedAlphabet;
    }

    /**
     * make sure that we can reads the files
     *
     * @param indexDirectory
     * @throws IOException
     */
    public static void checkFilesExist(String indexDirectory, int tableNumber) throws IOException {
        Utilities.checkFileExists(new File(indexDirectory));
        Utilities.checkFileExists(new File(indexDirectory, "index" + tableNumber + ".idx"));
        Utilities.checkFileExists(new File(indexDirectory, "table" + tableNumber + ".idx"));
        Utilities.checkFileExists(new File(indexDirectory, "table" + tableNumber + ".db"));
    }

    /**
     * determines the number of tables existing in the index
     *
     * @param indexDirectory
     * @return number of tables
     */
    public static int determineNumberOfTables(String indexDirectory) {
        int tableNumber = 0;
        while ((new File(indexDirectory, "index" + tableNumber + ".idx")).exists()) {
            tableNumber++;
        }
        return tableNumber;
    }

    /**
     * show part of the hash table in human readable form
     *
     * @throws java.io.IOException
     */
    public void showAPart() throws IOException {
        final Row row = new Row();

        System.err.println("Seed table (" + tableIndexGetter.limit() + "):");
        for (int z = 0; z < tableIndexGetter.limit(); z++) {
            if (z > 10)
                continue;
            System.err.print("hash " + z + " -> ");
            if (setRow(tableIndexGetter.get(z), row)) {
                System.err.print("(" + row.size() / 2 + ")");
                for (int i = 0; i < row.size(); i += 2) {
                    if (i > 100) {
                        System.err.print(" ...");
                        break;
                    }
                    System.err.print(" " + row.get(i) + "/" + row.get(i + 1));
                }
            }
            System.err.println();
        }

    }

    /**
     * construct the table from the given directory
     *
     * @param indexDirectory
     */
    public static SequenceType getIndexSequenceType(String indexDirectory) throws IOException, CanceledException {
        File indexFile = new File(indexDirectory, "index0.idx");
        try (DataInputStream ins = new DataInputStream(new BufferedInputStream(new FileInputStream(indexFile), 8192))) {
            Basic.readAndVerifyMagicNumber(ins, ReferencesHashTableBuilder.MAGIC_NUMBER);
            return SequenceType.valueOf(ins.readInt());
        }
    }

    public void close() {
        tableIndexGetter.close();
        tableDataGetter.close();
    }
}

