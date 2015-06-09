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

package malt.genes;

import jloda.util.Basic;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
    public void write(DataOutputStream outs) throws IOException {
        outs.writeLong(giNumber);
        if (product == null || product.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(product.length);
            outs.write(product);
        }
        if (geneName == null || geneName.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(geneName.length);
            outs.write(geneName);
        }
        if (proteinId == null || proteinId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(proteinId.length);
            outs.write(proteinId);
        }
        if (keggId == null || keggId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(keggId.length);
            outs.write(keggId);
        }
        if (cogId == null || cogId.length == 0)
            outs.writeInt(0);
        else {
            outs.writeInt(cogId.length);
            outs.write(cogId);
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
