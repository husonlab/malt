
package malt.io.xml;

import javax.xml.bind.annotation.*;
import java.math.BigInteger;


/**
 * <p>Java class for anonymous complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType>
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema>string">
 *       &lt;attribute name="taxon_id" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "value"
})
@XmlRootElement(name = "taxonomy")
public class Taxonomy {

    @XmlValue
    protected String value;
    @XmlAttribute(name = "taxon_id")
    @XmlSchemaType(name = "positiveInteger")
    protected BigInteger taxonId;

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
     * Gets the value of the taxonId property.
     *
     * @return possible object is
     * {@link BigInteger }
     */
    public BigInteger getTaxonId() {
        return taxonId;
    }

    /**
     * Sets the value of the taxonId property.
     *
     * @param value allowed object is
     *              {@link BigInteger }
     */
    public void setTaxonId(BigInteger value) {
        this.taxonId = value;
    }

}
