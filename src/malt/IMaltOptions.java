package malt;

import megan.util.BlastMode;

/**
 * Created by huson on 10/17/14.
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
