package malt.util;

import malt.AlignmentEngine;
import malt.analysis.OrganismsProfile;

/**
 * methods for merging profiles
 * Daniel Huson, 8.2014
 */
public class ProfileUtilities {
    /**
    /**
     * just get all organism profiles
     *
     * @param alignmentEngines
     * @return profiles
     */
    public static OrganismsProfile[] getOrganismsProfiles(AlignmentEngine[] alignmentEngines) {
        OrganismsProfile[] profiles = new OrganismsProfile[alignmentEngines.length];
        for (int i = 0; i < alignmentEngines.length; i++)
            profiles[i] = alignmentEngines[i].getOrganismsProfile();
        return profiles;
    }
}
