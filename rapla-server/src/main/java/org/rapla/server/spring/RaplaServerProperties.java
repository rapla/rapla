package org.rapla.server.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "rapla")
public class RaplaServerProperties
{
    /** Canonical key of the primary database datasource &mdash; bound from
     *  {@code rapla.db-datasources.rapladb}. */
    public static final String MAIN_DB_DATASOURCE = "rapladb";

    /** Canonical key of the primary file datasource &mdash; bound from
     *  {@code rapla.file-datasources.raplafile}. */
    public static final String MAIN_FILE_DATASOURCE = "raplafile";

    private Map<String, DataSourceProperties> dbDatasources = new LinkedHashMap<>();
    private Map<String, String> fileDatasources = new LinkedHashMap<>();
    private Map<String, Boolean> services = new LinkedHashMap<>();
    private String mailSession;
    private String patchScript;
    private Merge merge = new Merge();
    private boolean fixAdminPassword = false;
    private Readmodel readmodel = new Readmodel();

    public Map<String, DataSourceProperties> getDbDatasources()
    {
        return dbDatasources;
    }

    public void setDbDatasources(Map<String, DataSourceProperties> dbDatasources)
    {
        this.dbDatasources = dbDatasources;
    }

    /**
     * {@code rapla.fix-admin-password} — when true, the built-in {@code admin} account
     * is locked: its password cannot be changed and it cannot be deleted (B3). Also
     * suppresses the empty-password change-password nag. Intended for managed/demo
     * deployments that intentionally run {@code admin} with a fixed (e.g. empty) credential.
     */
    public boolean isFixAdminPassword()
    {
        return fixAdminPassword;
    }

    public void setFixAdminPassword(boolean fixAdminPassword)
    {
        this.fixAdminPassword = fixAdminPassword;
    }

    public Map<String, String> getFileDatasources()
    {
        return fileDatasources;
    }

    public void setFileDatasources(Map<String, String> fileDatasources)
    {
        this.fileDatasources = fileDatasources;
    }

    /** Path of the primary XML file store, or {@code null} if none configured. */
    public String getMainFilesource()
    {
        return fileDatasources.get(MAIN_FILE_DATASOURCE);
    }

    public Map<String, Boolean> getServices()
    {
        return services;
    }

    public void setServices(Map<String, Boolean> services)
    {
        this.services = services;
    }

    /**
     * Whether the named service/plugin is enabled. A service absent from the
     * {@code rapla.services} map counts as enabled &mdash; matching the legacy
     * {@code ServerContainerContext.isServiceEnabled} default (PRD 048).
     */
    public boolean isServiceEnabled(String serviceKey)
    {
        Boolean enabled = services.get(serviceKey);
        return enabled == null || enabled;
    }

    public String getMailSession()
    {
        return mailSession;
    }

    public void setMailSession(String mailSession)
    {
        this.mailSession = mailSession;
    }

    public String getPatchScript()
    {
        return patchScript;
    }

    public void setPatchScript(String patchScript)
    {
        this.patchScript = patchScript;
    }

    public Merge getMerge()
    {
        return merge;
    }

    public void setMerge(Merge merge)
    {
        this.merge = merge;
    }

    public Readmodel getReadmodel()
    {
        return readmodel;
    }

    public void setReadmodel(Readmodel readmodel)
    {
        this.readmodel = readmodel;
    }

    /**
     * PRD 082/086 — the in-memory read-model flip. {@code rapla.readmodel.authoritative} (default
     * {@code true}) makes the server serve windowed appointment reads, conflict narrowing, type-bucket
     * {@code getAllocatables}, the permission index and the full-admin window-first path from the
     * in-memory indices instead of the legacy {@code appointmentMap} scan. Reversible: set
     * {@code rapla.readmodel.authoritative: false} in an external/custom {@code application.yml} (or via
     * {@code --spring.config.additional-location}) to fall back to the legacy path — the legacy
     * structures stay maintained, so the switch is instant and lossless.
     */
    public static class Readmodel
    {
        private boolean authoritative = true;

        public boolean isAuthoritative()
        {
            return authoritative;
        }

        public void setAuthoritative(boolean authoritative)
        {
            this.authoritative = authoritative;
        }
    }

    /**
     * Server-side merge-check config. {@link #blockedSyncAttributes} lists the
     * dynamic-type attribute keys whose presence on a resource blocks a merge —
     * a deployment-agnostic replacement for the legacy client-side
     * {@code MergeCheckExtension}. Vanilla rapla leaves it empty (merges are
     * unrestricted).
     */
    public static class Merge
    {
        private List<String> blockedSyncAttributes = new ArrayList<>();

        public List<String> getBlockedSyncAttributes()
        {
            return blockedSyncAttributes;
        }

        public void setBlockedSyncAttributes(List<String> blockedSyncAttributes)
        {
            this.blockedSyncAttributes = blockedSyncAttributes;
        }
    }
}
