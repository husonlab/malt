/**
 * GeneType.java 
 * Copyright (C) 2018 Daniel H. Huson
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

package malt.io.xml;

import javax.xml.bind.annotation.*;
import java.math.BigInteger;


/**
 * <p>Java class for GeneType complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="GeneType">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema>string">
 *       &lt;attribute name="gi" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
 *       &lt;attribute name="ref" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="protein_id" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="product" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="kegg" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="cog" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GeneType", propOrder = {
        "value"
})
public class GeneType {

    @XmlValue
    protected String value;
    @XmlAttribute
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger gi;
    @XmlAttribute
    protected String ref;
    @XmlAttribute(name = "protein_id")
    protected String proteinId;
    @XmlAttribute
    protected String product;
    @XmlAttribute
    protected String kegg;
    @XmlAttribute
    protected String cog;

    /**
     * Gets the value of the value property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Gets the value of the gi property.
     *
     * @return possible object is
     * {@link BigInteger }
     */
    public BigInteger getGi() {
        return gi;
    }

    /**
     * Sets the value of the gi property.
     *
     * @param value allowed object is
     *              {@link BigInteger }
     */
    public void setGi(BigInteger value) {
        this.gi = value;
    }

    /**
     * Gets the value of the ref property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getRef() {
        return ref;
    }

    /**
     * Sets the value of the ref property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setRef(String value) {
        this.ref = value;
    }

    /**
     * Gets the value of the proteinId property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getProteinId() {
        return proteinId;
    }

    /**
     * Sets the value of the proteinId property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setProteinId(String value) {
        this.proteinId = value;
    }

    /**
     * Gets the value of the product property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getProduct() {
        return product;
    }

    /**
     * Sets the value of the product property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setProduct(String value) {
        this.product = value;
    }

    /**
     * Gets the value of the kegg property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getKegg() {
        return kegg;
    }

    /**
     * Sets the value of the kegg property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setKegg(String value) {
        this.kegg = value;
    }

    /**
     * Gets the value of the cog property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getCog() {
        return cog;
    }

    /**
     * Sets the value of the cog property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setCog(String value) {
        this.cog = value;
    }

}
