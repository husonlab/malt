/**
 * ReportType.java 
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

package malt.io.xml;

import javax.xml.bind.annotation.*;


/**
 * <p>Java class for ReportType complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="ReportType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="dataset" type="{}DatasetType"/>
 *         &lt;element name="organisms" type="{}OrganismsType"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReportType", propOrder = {
        "dataset",
        "organisms"
})
@XmlRootElement(name = "organismsReport")
public class ReportType {

    @XmlElement(required = true)
    protected DatasetType dataset;
    @XmlElement(required = true)
    protected OrganismsType organisms;

    /**
     * Gets the value of the dataset property.
     *
     * @return possible object is
     * {@link DatasetType }
     */
    public DatasetType getDataset() {
        return dataset;
    }

    /**
     * Sets the value of the dataset property.
     *
     * @param value allowed object is
     *              {@link DatasetType }
     */
    public void setDataset(DatasetType value) {
        this.dataset = value;
    }

    /**
     * Gets the value of the organisms property.
     *
     * @return possible object is
     * {@link OrganismsType }
     */
    public OrganismsType getOrganisms() {
        return organisms;
    }

    /**
     * Sets the value of the organisms property.
     *
     * @param value allowed object is
     *              {@link OrganismsType }
     */
    public void setOrganisms(OrganismsType value) {
        this.organisms = value;
    }

}
