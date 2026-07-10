package org.rapla.entities.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.Timestamp;

/**
 * PRD 098 — server-only application-scoped artifact (GraphQL view query, Mustache document
 * template, CSS, partial, image). Identity is the natural key {@code kind + ":" + name}; never
 * transferred to clients.
 */
public interface StoredArtifact extends Entity<StoredArtifact>, Ownable, Timestamp
{
    String KIND_VIEW = "VIEW";

    /** PRD 097 OQ8 — a Mustache document (body = template, metadata = viewName + visibility).
     *  Deliberately NOT "TEMPLATE": rapla already calls a {@code KEY_TEMPLATE}-annotated
     *  Reservation a template/Vorlage, and DynamicType nameformats are "name templates" too
     *  (see {@code docs/architecture/glossary.md}). */
    String KIND_DOCUMENT = "DOCUMENT";
    String KIND_PARTIAL = "PARTIAL";
    String KIND_CSS = "CSS";
    String KIND_IMAGE = "IMAGE";

    String getKind();

    String getName();

    String getBody();

    /** kind-specific metadata JSON (isPublic, groups, defaultVariables, mimeType, ...) */
    String getMetadata();

    static String createId(String kind, String name)
    {
        return kind + ":" + name;
    }
}
