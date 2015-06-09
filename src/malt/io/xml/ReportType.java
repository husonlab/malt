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
