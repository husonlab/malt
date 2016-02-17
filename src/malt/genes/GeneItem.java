/**
 * GeneItem.java 
 * Copyright (C) 2015 Daniel H. Huson
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
 * Daniel Huson, 8.2014
 */
public class GeneItem {
    private long giNumber;
    private byte[] product;
    private byte[] geneName;
    private byte[] proteinId;
    private byte[] keggId;
    private byte[] cogId;

    public GeneItem() {
    }

    public long getGiNumber() {
        return giNumber;
    }

    public void setGiNumber(long giNumber) {
        this.giNumber = giNumber;
    }

    public byte[] getProduct() {
        return product;
    }

    public void setProduct(byte[] product) {
        this.product = product;
    }

    public byte[] getGeneName() {
        return geneName;
    }

    public void setGeneName(byte[] geneName) {
        this.geneName = geneName;
    }

    public byte[] getProteinId() {
        return proteinId;
    }

    public void setProteinId(byte[] proteinId) {
        this.proteinId = proteinId;
    }

    public byte[] getKeggId() {
        return keggId;
    }

    public void setKeggId(byte[] keggId) {
        this.keggId = keggId;
    }

    public byte[] getCogId() {
        return cogId;
    }

    public void setCogId(byte[] cogId) {
        this.cogId = cogId;
    }

    public String toString() {
        return "gene=" + (geneName == null ? "null" : Basic.toString(geneName))
                + " gi=" + giNumber
                + ", product=" + (product == null ? "null" : Basic.toString(product))
                + ", proteinId=" + (proteinId == null ? "null" : Basic.toString(proteinId))
                + ", keggId=" + (keggId == null ? "null" : Basic.toString(keggId))
                + ", cogId=" + (cogId == null ? "null" : Basic.toString(cogId));
    }

    /**
     * write
     *
     * @param outs
     * @throws java.io.IOException
     */
    public void write(OutputWriter outs) throws IOException {
        outs.writeLong(giNumber);
        if (product == null || product.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(product.length);
            outs.write(product, 0, product.length);
        }
        if (geneName == null || geneName.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(geneName.length);
            outs.write(geneName, 0, geneName.length);
        }
        if (proteinId == null || proteinId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(proteinId.length);
            outs.write(proteinId, 0, proteinId.length);
        }
        if (keggId == null || keggId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(keggId.length);
            outs.write(keggId, 0, keggId.length);
        }
        if (cogId == null || cogId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(cogId.length);
            outs.write(cogId, 0, cogId.length);
        }
    }

    /**
     * read
     *
     * @param ins
     * @throws IOException
     */
    public void read(DataInputStream ins) throws IOException {
        giNumber = ins.readLong();
        int length = ins.readInt();
        if (length == 0)
            product = null;
        else {
            product = new byte[length];
            if (ins.read(product, 0, length) != length)
                throw new IOException("read failed");
        }
        length = ins.readInt();
        if (length == 0)
            geneName = null;
        else {
            geneName = new byte[length];
            if (ins.read(geneName, 0, length) != length)
                throw new IOException("read failed");
        }
        length = ins.readInt();
        if (length == 0)
            proteinId = null;
        else {
            proteinId = new byte[length];
            if (ins.read(proteinId, 0, length) != length)
                throw new IOException("read failed");
        }
        length = ins.readInt();
        if (length == 0)
            keggId = null;
        else {
            keggId = new byte[length];
            if (ins.read(keggId, 0, length) != length)
                throw new IOException("read failed");
        }
        length = ins.readInt();
        if (length == 0)
            cogId = null;
        else {
            cogId = new byte[length];
            if (ins.read(cogId, 0, length) != length)
                throw new IOException("read failed");
        }
    }
}
