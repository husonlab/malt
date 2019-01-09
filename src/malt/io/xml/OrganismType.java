/**
 * OrganismType.java
 * Copyright (C) 2019 Daniel H. Huson
 * <p>
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package malt.io.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OrganismType complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="OrganismType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{}relativeAmount"/>
 *         &lt;element ref="{}taxonomy"/>
 *         &lt;element ref="{}organismName" minOccurs="0"/>
 *         &lt;element ref="{}genus" minOccurs="0"/>
 *         &lt;element ref="{}species" minOccurs="0"/>
 *         &lt;element ref="{}strain" minOccurs="0"/>
 *         &lt;element name="genes" type="{}GenesType" minOccurs="0"/>
 *         &lt;element name="reads" type="{}ReadsType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OrganismType", propOrder = {
        "relativeAmount",
        "taxonomy",
        "organismName",
        "genus",
        "species",
        "strain",
        "genes",
        "reads"
})
public class OrganismType {

    @XmlElement(required = true)
    protected RelativeAmount relativeAmount;
    @XmlElement(required = true)
    protected Taxonomy taxonomy;
    protected String organismName;
    protected String genus;
    protected String species;
    protected String strain;
    protected GenesType genes;
    protected ReadsType reads;

    /**
     * Gets the value of the relativeAmount property.
     *
     * @return possible object is
     * {@link RelativeAmount }
     */
    public RelativeAmount getRelativeAmount() {
        return relativeAmount;
    }

    /**
     * Sets the value of the relativeAmount property.
     *
     * @param value allowed object is
     *              {@link RelativeAmount }
     */
    public void setRelativeAmount(RelativeAmount value) {
        this.relativeAmount = value;
    }

    /**
     * Gets the value of the taxonomy property.
     *
     * @return possible object is
     * {@link Taxonomy }
     */
    public Taxonomy getTaxonomy() {
        return taxonomy;
    }

    /**
     * Sets the value of the taxonomy property.
     *
     * @param value allowed object is
     *              {@link Taxonomy }
     */
    public void setTaxonomy(Taxonomy value) {
        this.taxonomy = value;
    }

    /**
     * Gets the value of the organismName property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getOrganismName() {
        return organismName;
    }

    /**
     * Sets the value of the organismName property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setOrganismName(String value) {
        this.organismName = value;
    }

    /**
     * Gets the value of the genus property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getGenus() {
        return genus;
    }

    /**
     * Sets the value of the genus property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setGenus(String value) {
        this.genus = value;
    }

    /**
     * Gets the value of the species property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getSpecies() {
        return species;
    }

    /**
     * Sets the value of the species property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setSpecies(String value) {
        this.species = value;
    }

    /**
     * Gets the value of the strain property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getStrain() {
        return strain;
    }

    /**
     * Sets the value of the strain property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setStrain(String value) {
        this.strain = value;
    }

    /**
     * Gets the value of the genes property.
     *
     * @return possible object is
     * {@link GenesType }
     */
    public GenesType getGenes() {
        return genes;
    }

    /**
     * Sets the value of the genes property.
     *
     * @param value allowed object is
     *              {@link GenesType }
     */
    public void setGenes(GenesType value) {
        this.genes = value;
    }

    /**
     * Gets the value of the reads property.
     *
     * @return possible object is
     * {@link ReadsType }
     */
    public ReadsType getReads() {
        return reads;
    }

    /**
     * Sets the value of the reads property.
     *
     * @param value allowed object is
     *              {@link ReadsType }
     */
    public void setReads(ReadsType value) {
        this.reads = value;
    }

}
