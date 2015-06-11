/**
 * ObjectFactory.java 
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

package malt.io.xml;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each
 * Java content interface and Java element interface
 * generated in the malan.io.xml package.
 * <p>An ObjectFactory allows you to programatically
 * construct new instances of the Java representation
 * for XML content. The Java representation of XML
 * content can consist of schema derived interfaces
 * and classes representing the binding of schema
 * type definitions, element declarations and model
 * groups.  Factory methods for each of these are
 * provided in this class.
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _Species_QNAME = new QName("", "species");
    private final static QName _Strain_QNAME = new QName("", "strain");
    private final static QName _DatasetName_QNAME = new QName("", "datasetName");
    private final static QName _OrganismsReport_QNAME = new QName("", "organismsReport");
    private final static QName _OrganismName_QNAME = new QName("", "organismName");
    private final static QName _Genus_QNAME = new QName("", "genus");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: malan.io.xml
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link GeneType }
     */
    public GeneType createGeneType() {
        return new GeneType();
    }

    /**
     * Create an instance of {@link ReadsType }
     */
    public ReadsType createReadsType() {
        return new ReadsType();
    }

    /**
     * Create an instance of {@link RelativeAmount }
     */
    public RelativeAmount createRelativeAmount() {
        return new RelativeAmount();
    }

    /**
     * Create an instance of {@link DatasetType }
     */
    public DatasetType createDatasetType() {
        return new DatasetType();
    }

    /**
     * Create an instance of {@link ReportType }
     */
    public ReportType createReportType() {
        return new ReportType();
    }

    /**
     * Create an instance of {@link GenesType }
     */
    public GenesType createGenesType() {
        return new GenesType();
    }

    /**
     * Create an instance of {@link OrganismType }
     */
    public OrganismType createOrganismType() {
        return new OrganismType();
    }

    /**
     * Create an instance of {@link Taxonomy }
     */
    public Taxonomy createTaxonomy() {
        return new Taxonomy();
    }

    /**
     * Create an instance of {@link OrganismsType }
     */
    public OrganismsType createOrganismsType() {
        return new OrganismsType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     */
    @XmlElementDecl(namespace = "", name = "species")
    public JAXBElement<String> createSpecies(String value) {
        return new JAXBElement<>(_Species_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     */
    @XmlElementDecl(namespace = "", name = "strain")
    public JAXBElement<String> createStrain(String value) {
        return new JAXBElement<>(_Strain_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     */
    @XmlElementDecl(namespace = "", name = "datasetName")
    public JAXBElement<String> createDatasetName(String value) {
        return new JAXBElement<>(_DatasetName_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReportType }{@code >}}
     */
    @XmlElementDecl(namespace = "", name = "organismsReport")
    public JAXBElement<ReportType> createOrganismsReport(ReportType value) {
        return new JAXBElement<>(_OrganismsReport_QNAME, ReportType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     */
    @XmlElementDecl(namespace = "", name = "organismName")
    public JAXBElement<String> createOrganismName(String value) {
        return new JAXBElement<>(_OrganismName_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     */
    @XmlElementDecl(namespace = "", name = "genus")
    public JAXBElement<String> createGenus(String value) {
        return new JAXBElement<>(_Genus_QNAME, String.class, null, value);
    }

}
