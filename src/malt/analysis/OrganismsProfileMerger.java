package malt.analysis;

import jloda.util.Basic;
import jloda.util.ProgressPercentage;
import malt.genes.GeneItem;
import malt.genes.GeneTableAccess;
import malt.io.xml.*;
import malt.mapping.TaxonMapping;
import malt.util.TaxonomyUtilities;
import megan.algorithms.LCAAlgorithm;
import megan.mainviewer.data.TaxonomyData;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * merges all thread-specific organism profiles and generates output file
 * Daniel Huson, 8.2014
 */
public class OrganismsProfileMerger extends OrganismsProfile {
    private final Map<Integer, OrganismItem> taxonId2OrganismItem;

    private final GeneTableAccess geneTableAccess;

    private final int numberOfSyncObjects = 1024;
    private final Object[] syncObjects = new Object[numberOfSyncObjects];  // use lots of objects to synchronize on so that threads don't in each others way

    /**
     * constructor
     *
     * @param taxonMapping
     */
    public OrganismsProfileMerger(TaxonMapping taxonMapping, GeneTableAccess geneTableAccess) {
        super(taxonMapping);
        this.geneTableAccess = geneTableAccess;
        taxonId2OrganismItem = new HashMap<>(10000, 1f);

        // create the synchronization objects
        for (int i = 0; i < numberOfSyncObjects; i++)
            syncObjects[i] = new Object();
    }

    /**
     * merge all thread-specific profiles and build final organism profiles
     *
     * @param profiles
     */
    public void mergeAndCompute(final OrganismsProfile[] profiles) {
        final int maxGenesPerRead = 5;
        final double proportionOfWeightToCover = 0.8; // todo: makes these options user accessible
        // merge all refIndex to weight maps:
        final Map<Integer, Integer> refIndex2weight = new HashMap<>(100000);
        for (OrganismsProfile current : profiles) {
            // merge refIndex2weight
            for (Map.Entry<Integer, Integer> entry : current.refIndex2weight.entrySet()) {
                if (entry.getValue() != null) {
                    Integer count = refIndex2weight.get(entry.getKey());
                    if (count == null)
                        refIndex2weight.put(entry.getKey(), entry.getValue());
                    else
                        refIndex2weight.put(entry.getKey(), count + entry.getValue());
                }
            }
            totalReads += current.totalReads;
        }

        final ProgressPercentage progress = new ProgressPercentage("Computing organism profiles...", totalReads);

        // one thread for each profile (as we can assume that each profile was computed in a separate thread)
        int numberOfThreads = profiles.length;
        final ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        // launch the worker threads
        for (int thread = 0; thread < numberOfThreads; thread++) {
            final int threadNumber = thread;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        final OrganismsProfile profile = profiles[threadNumber];
                        final LCAAlgorithm lcaAlgorithm = profiles[threadNumber].getLcaAlgorithm();
                        final HashMap<Integer, Integer> tax2weight = new HashMap<Integer, Integer>(10000, 1f);

                        final GeneItem[] genes = new GeneItem[maxGenesPerRead];

                        // Consider all queries:
                        for (Iterator<QueryItem> it = profile.iterator(); it.hasNext(); ) {
                            QueryItem queryItem = it.next();
                            // prepare for weighted-LCA algorithm:
                            tax2weight.clear();
                            int numberOfTaxa = 0;
                            for (int i = 0; i < queryItem.readMatchItems.length; i++) {
                                ReadMatchItem readMatchItem = queryItem.readMatchItems[i];
                                int taxId = taxonMapping.get(readMatchItem.refIndex);
                                if (taxId > 0) {
                                    taxIds[numberOfTaxa++] = taxId;
                                    Integer add = refIndex2weight.get(readMatchItem.refIndex);
                                    if (add == null)
                                        add = 1;
                                    Integer weight = tax2weight.get(taxId);
                                    if (weight == null || weight < add)  // keep best weight seen for this taxon
                                        tax2weight.put(taxId, add);
                                }
                            }
                            /*
                            int totalWeight = 0;  // compute total weight
                            for (Integer w : tax2weight.values()) {
                                totalWeight += w;
                            }
                            */

                            // apply weighted LCA algorithm:
                            final int lca = lcaAlgorithm.computeWeightedLCA(tax2weight, proportionOfWeightToCover);

                            // only report those reads that have a taxon assignment:
                            if (lca > 0) {
                                int numberOfGenes = geneTableAccess.getGenes(refIndex2weight, queryItem.readMatchItems, genes);

                                synchronized (syncObjects[Math.abs(lca) % numberOfSyncObjects]) {
                                    OrganismItem organismItem = taxonId2OrganismItem.get(lca);
                                    if (organismItem == null) {
                                        organismItem = new OrganismItem();
                                        taxonId2OrganismItem.put(lca, organismItem);
                                    }
                                    if (numberOfGenes > 0)
                                        organismItem.genes.addAll(Arrays.asList(genes).subList(0, numberOfGenes));
                                    organismItem.queryNames.add(queryItem.queryName);
                                }
                            }
                            progress.incrementProgress();
                        }
                    } catch (Exception ex)

                    {
                        Basic.caught(ex);
                        System.exit(1);  // just die...
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        try {
            countDownLatch.await();  // await completion of threads
        } catch (InterruptedException e) {
            Basic.caught(e);
        } finally {
            // shut down threads:
            executor.shutdownNow();
        }

        progress.close();
    }

    private class OrganismItem {
        final List<GeneItem> genes;
        final List<byte[]> queryNames;

        OrganismItem() {
            genes = new LinkedList<>();
            queryNames = new LinkedList<>();
        }

    }

    /**
     * write the profile to a file
     *
     * @param outs
     */
    public void write(OutputStream outs) throws JAXBException, IOException {
        // setup xml report
        ReportType report = generateXMLReport();
        // write to stream
        writeReport(outs, report);
    }

    /**
     * generates the XML report
     *
     * @return XML report
     */
    private ReportType generateXMLReport() {
        ReportType report = new ReportType();
        report.setOrganisms(new OrganismsType());
        report.setDataset(new DatasetType());
        if (name != null)
            report.getDataset().setDatasetName(name);

        int countOrganisms = 0;
        long countGenes = 0;
        long countReads = 0;

        for (Integer taxId : taxonId2OrganismItem.keySet()) {
            OrganismItem organismItem = taxonId2OrganismItem.get(taxId);
            if (organismItem != null) {
                countOrganisms++;
                final OrganismType organism = new OrganismType();
                report.getOrganisms().getOrganism().add(organism);
                organism.setTaxonomy(new Taxonomy());

                organism.setOrganismName(TaxonomyData.getName2IdMap().get(taxId));
                String genus = TaxonomyUtilities.getContainingGenus(taxId);
                if (genus != null)
                    organism.setGenus(genus);
                final String species = TaxonomyUtilities.getContainingSpecies(taxId);
                if (species != null)
                    organism.setSpecies(species);
                final String strain = TaxonomyUtilities.getStrain(taxId);
                if (strain != null)
                    organism.setStrain(strain);
                organism.setOrganismName(TaxonomyData.getName2IdMap().get(taxId));
                organism.getTaxonomy().setTaxonId(BigInteger.valueOf(taxId));
                organism.getTaxonomy().setValue(TaxonomyUtilities.getPath(taxId));
                organism.getTaxonomy().setValue(TaxonomyUtilities.getPath(taxId));

                organism.setRelativeAmount(new RelativeAmount());
                organism.getRelativeAmount().setCount(BigInteger.valueOf(organismItem.queryNames.size()));
                organism.getRelativeAmount().setValue(BigDecimal.valueOf(100.0 * (double) organismItem.queryNames.size() / (double) totalReads));

                // add all genes to organism
                organism.setGenes(new GenesType());
                final Set<String> seen = new HashSet<>();
                for (GeneItem geneItem : organismItem.genes) {
                    if (geneItem.getGeneName() != null) {
                        String geneName = Basic.toString(geneItem.getGeneName());
                        if (!seen.contains(geneName)) {
                            seen.add(geneName);
                            GeneType geneType = new GeneType();
                            geneType.setValue(geneName);
                            if (geneItem.getGiNumber() != 0)
                                geneType.setGi(BigInteger.valueOf(geneItem.getGiNumber()));
                            if (geneItem.getProduct() != null)
                                geneType.setProduct(Basic.toString(geneItem.getProduct()));
                            if (geneItem.getProteinId() != null)
                                geneType.setProteinId(Basic.toString(geneItem.getProteinId()));
                            organism.getGenes().getGene().add(geneType);
                            countGenes++;
                        }
                    }
                }

                // add all reads to organism
                organism.setReads(new ReadsType());
                for (byte[] queryName : organismItem.queryNames) {
                    organism.getReads().getSequence().add(Basic.toString(queryName));
                }
                countReads += organismItem.queryNames.size();
            }
        }
        System.err.println("Organisms: " + countOrganisms + " genes: " + countGenes + " reads: " + countReads);
        return report;
    }

    /**
     * write the report
     *
     * @param outs
     * @param report
     * @throws javax.xml.bind.JAXBException
     * @throws java.io.FileNotFoundException
     */
    private void writeReport(OutputStream outs, ReportType report) throws JAXBException, IOException {
        javax.xml.bind.JAXBContext jaxbContext = javax.xml.bind.JAXBContext.newInstance("malt.io.xml");
        // create a Marshaller and do marshal
        javax.xml.bind.Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(report, outs);
    }
}
