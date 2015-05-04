package malt;


import megan.parsers.blast.BlastMode;

/**
 * malt options
 *
 * Daniel Huson, 4.2015
 */
public interface IMaltOptions {

    int getMaxAlignmentsPerQuery();

    double getMinBitScore();

    double getMaxExpected();

    double getMinProportionIdentity();

    BlastMode getMode();

    double getTopPercentLCA();

    int getMinSupportLCA();

    float getMinSupportPercentLCA();
}
