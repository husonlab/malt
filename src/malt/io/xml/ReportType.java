
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
