package org.rapla.plugin.externaleventimport;

public interface ExternalEventImportPlugin
{
    String PLUGIN_ID = "org.rapla.plugin.externaleventimport";

    boolean ENABLE_BY_DEFAULT = false;

    /**
     * Spring property toggling both the server-side {@link ExternalEventImportService} impl and
     * the client-side wizard. When false (or absent), neither bean exists.
     */
    String ENABLE_PROPERTY = "rapla.externalevents.enabled";
}
