package org.rapla.server.spring.graphql;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * PRD 098 — bounds for the server artifact store, bound from {@code rapla.artifact.*}
 * in application.yml.
 */
@ConfigurationProperties(prefix = "rapla.artifact")
public class RaplaArtifactProperties
{
    /**
     * Save-time cap on an artifact body. The default is a category guard, not abuse
     * protection (writes are admin-only): content that belongs in the store is small;
     * attachment-shaped uploads (video/scan/PDF) should fail loudly. Raise per
     * deployment for a legitimate outlier — the DB column is unbounded.
     */
    private DataSize maxBodySize = DataSize.ofMegabytes(10);

    public DataSize getMaxBodySize() { return maxBodySize; }
    public void setMaxBodySize(DataSize maxBodySize) { this.maxBodySize = maxBodySize; }
}
