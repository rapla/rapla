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
    private String patchDir = "data/patch";
    private Merge merge = new Merge();
    private boolean fixAdminPassword = false;
    private String legacyContextPath = "";
    private Readmodel readmodel = new Readmodel();
    private Views views = new Views();
    private Documents documents = new Documents();

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

    /**
     * {@code rapla.legacy-context-path} &mdash; PRD 109. The servlet context path a
     * Rapla 2.0 deployment used to run under (e.g. {@code /wochenplan}). When set, the
     * published calendar and index URLs keep answering under that prefix and every other
     * path below it is 301'd onto the canonical path. Empty (the default) = no legacy
     * URL handling at all; the filter is not registered.
     */
    public String getLegacyContextPath()
    {
        return legacyContextPath;
    }

    public void setLegacyContextPath(String legacyContextPath)
    {
        this.legacyContextPath = legacyContextPath;
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

    /**
     * {@code rapla.patch-dir} &mdash; PRD 112. Directory of self-describing artefact files
     * (front-matter head) applied to the artifact store at every start: created when missing,
     * overwritten when the file's {@code updated} stamp is newer than the stored artifact.
     * Relative to the working directory like {@code data/data.xml}; absent directory = no-op.
     */
    public String getPatchDir()
    {
        return patchDir;
    }

    public void setPatchDir(String patchDir)
    {
        this.patchDir = patchDir;
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

    public Views getViews()
    {
        return views;
    }

    public void setViews(Views views)
    {
        this.views = views;
    }

    /**
     * {@code rapla.views.builtin-listed} &mdash; allowlist of BUILTIN view keys that appear in
     * the SPA view switcher. Absent (null): each builtin's own {@code @view(listed:)} decides, as
     * before. Set: a builtin is listed iff its key is in the list &mdash; so a deployment that wants
     * only its custom views is not surprised by a builtin added in a later release. Unlisted
     * builtins stay resolvable by name.
     */
    public static class Views
    {
        private List<String> builtinListed;

        public List<String> getBuiltinListed()
        {
            return builtinListed;
        }

        public void setBuiltinListed(List<String> builtinListed)
        {
            this.builtinListed = builtinListed;
        }
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
    public Documents getDocuments() { return documents; }

    public void setDocuments(Documents documents) { this.documents = documents; }

    /** PRD 097 D6c (2) — document-rendering policy. */
    public static class Documents
    {
        /**
         * Allow author {@code <script>} in stored documents. OFF by default, yml only (never a
         * runtime preference or a per-document field): turning it on changes the sanitizer AND the
         * CSP together, and it NEVER applies to a public document — trust rests on document CRUD
         * being admin-only, which anonymous readability would bypass.
         */
        private boolean authorScripts = false;

        public boolean isAuthorScripts() { return authorScripts; }

        public void setAuthorScripts(boolean authorScripts) { this.authorScripts = authorScripts; }
    }

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
