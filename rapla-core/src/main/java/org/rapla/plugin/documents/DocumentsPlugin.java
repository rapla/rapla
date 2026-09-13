package org.rapla.plugin.documents;

import org.rapla.framework.TypedComponentRole;

/**
 * PRD 111 D6 — the documents CONTEXT WIRING plugin: the {@code documents} annotation on a dynamic
 * type, its Swing editor field, the {@code DynamicType.documents} resolver and the SPA menu
 * provider. The document pipeline itself ({@code /api/documents}, the template editor, the builtin
 * calendar documents) is core and always on.
 *
 * <p>Enabled by default on both gates (like tableview/export2ical): it works without configuration
 * and stays inert until a type carries the annotation.
 */
public class DocumentsPlugin
{
    public static final String PLUGIN_ID = "org.rapla.plugin.documents";

    public static final boolean ENABLE_BY_DEFAULT = true;

    /** Runtime system preference — read by the resolver and by the Swing editor field. */
    public static final TypedComponentRole<Boolean> ENABLED =
            new TypedComponentRole<>(PLUGIN_ID + "." + "enabled");

    private DocumentsPlugin() {}
}
