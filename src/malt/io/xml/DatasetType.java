
package malt.io.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatasetType complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="DatasetType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{}datasetName"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatasetType", propOrder = {
        "datasetName"
})
public class DatasetType {

    @XmlElement(required = true)
    protected String datasetName;

    /**
     * Gets the value of the datasetName property.
     *
     * @return possible object is
     * {@link String }
     */
    public String getDatasetName() {
        return datasetName;
    }

    /**
     * Sets the value of the datasetName property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setDatasetName(String value) {
        this.datasetName = value;
    }

}
