/**
 * GeneItem.java 
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
package malt.genes;

import jloda.util.Basic;
import megan.io.OutputWriter;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * a gene item
 * Daniel Huson, 11.2017
 *
 */
public class GeneItem {
    private byte[] proteinId;
    private int keggId;
    private int cogId;
    private int seedId;
    private int interproId;

    public GeneItem() {
    }

    public byte[] getProteinId() {
        return proteinId;
    }

    public void setProteinId(byte[] proteinId) {
        this.proteinId = proteinId;
    }

    public int getKeggId() {
        return keggId;
    }

    public void setKeggId(int keggId) {
        this.keggId = keggId;
    }

    public int getCogId() {
        return cogId;
    }

    public void setCogId(int cogId) {
        this.cogId = cogId;
    }

    public int getSeedId() {
        return seedId;
    }

    public void setSeedId(int seedId) {
        this.seedId = seedId;
    }

    public int getInterproId() {
        return interproId;
    }

    public void setInterproId(int interproId) {
        this.interproId = interproId;
    }

    public String toString() {
        return "proteinId=" + (proteinId == null ? "null" : Basic.toString(proteinId))
                + ", keggId=" + keggId
                + ", cogId=" + cogId
                + ", seedId=" + seedId
                + ", interproId=" + interproId;

    }

    /**
     * write
     *
     * @param outs
     * @throws java.io.IOException
     */
    public void write(OutputWriter outs) throws IOException {
        if (proteinId == null || proteinId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(proteinId.length);
            outs.write(proteinId, 0, proteinId.length);
        }
        outs.writeInt(keggId);
        outs.writeInt(cogId);
        outs.writeInt(seedId);
        outs.writeInt(interproId);
    }

    /**
     * read
     *
     * @param ins
     * @throws IOException
     */
    public void read(DataInputStream ins) throws IOException {
        int length = ins.readInt();
        if (length == 0)
            proteinId = null;
        else {
            proteinId = new byte[length];
            if (ins.read(proteinId, 0, length) != length)
                throw new IOException("read failed");
        }
        keggId = ins.readInt();
        cogId = ins.readInt();
        seedId = ins.readInt();
        interproId = ins.readInt();
    }
}
