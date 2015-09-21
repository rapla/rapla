package org.rapla.server.internal;

import java.util.Set;

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
