package org.rapla.server.internal;

import java.util.Set;

/**
 * Created by Christopher on 16.09.2015.
 */
public class ResourceBundleList
{
    private Set<String> bundleIds;
    public ResourceBundleList(Set<String> bundleIds)
    {
        this.bundleIds = bundleIds;
    };

    public Set<String> getBundleIds()
    {
        return bundleIds;
    }

}
