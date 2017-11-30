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
 * Daniel Huson, 8.2014, 11.2017
 *
 */
public class GeneItem {
    private byte[] product;
    private byte[] geneName;
    private byte[] proteinId;
    private byte[] keggId;
    private byte[] cogId;
    private byte[] seedId;
    private byte[] interproId;

    public GeneItem() {
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

    public byte[] getSeedId() {
        return seedId;
    }

    public void setSeedId(byte[] seedId) {
        this.seedId = seedId;
    }

    public byte[] getInterproId() {
        return interproId;
    }

    public void setInterproId(byte[] interproId) {
        this.interproId = interproId;
    }

    public String toString() {
        return "gene=" + (geneName == null ? "null" : Basic.toString(geneName))
                + ", product=" + (product == null ? "null" : Basic.toString(product))
                + ", proteinId=" + (proteinId == null ? "null" : Basic.toString(proteinId))
                + ", keggId=" + (keggId == null ? "null" : Basic.toString(keggId))
                + ", cogId=" + (cogId == null ? "null" : Basic.toString(cogId))
                + ", seedId=" + (seedId == null ? "null" : Basic.toString(seedId))
                + ", interproId=" + (interproId == null ? "null" : Basic.toString(interproId));

    }

    /**
     * write
     *
     * @param outs
     * @throws java.io.IOException
     */
    public void write(OutputWriter outs) throws IOException {
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
        if (seedId == null || seedId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(seedId.length);
            outs.write(seedId, 0, seedId.length);
        }
        if (interproId == null || interproId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(interproId.length);
            outs.write(interproId, 0, interproId.length);
        }

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
        length = ins.readInt();
        if (length == 0)
            seedId = null;
        else {
            seedId = new byte[length];
            if (ins.read(seedId, 0, length) != length)
                throw new IOException("read failed");
        }
        length = ins.readInt();
        if (length == 0)
            interproId = null;
        else {
            interproId = new byte[length];
            if (ins.read(interproId, 0, length) != length)
                throw new IOException("read failed");
        }
    }
}
