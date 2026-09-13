package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.documents.DocumentsPlugin;
import org.rapla.server.spring.document.DocumentCatalogService;
import org.rapla.server.spring.document.DocumentEntry;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 111 D3 — resolves {@code DynamicType.documents}: the type's {@code documents} annotation
 * (comma-separated document names, menu order) joined against the document catalog AS THE CALLER.
 *
 * <p>Everything the SPA needs to build the row menu without a round-trip: the name (reference +
 * label) and the URL parameter that carries the id, derived from the document's view per D2.
 *
 * <p>D6 gate 1 — the whole bean disappears when the deployment turns the plugin off; the schema
 * field is nullable so that absence is representable. Gate 2 — the runtime system preference is
 * read per request and answers an empty list when off.
 */
@Controller
@ConditionalOnProperty(prefix = "rapla.services", name = DocumentsPlugin.PLUGIN_ID, matchIfMissing = true)
public class DocumentTypeResolver
{
    private final StorageOperator operator;
    private final DocumentCatalogService documents;
    private final ViewCatalogService views;

    public DocumentTypeResolver(StorageOperator operator, DocumentCatalogService documents,
            ViewCatalogService views)
    {
        this.operator = operator;
        this.documents = documents;
        this.views = views;
    }

    @SchemaMapping(typeName = "DynamicType", field = "documents")
    public List<Map<String, Object>> documents(DynamicType type,
            graphql.schema.DataFetchingEnvironment env)
    {
        if (type == null || !runtimeEnabled()) return List.of();
        String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_DOCUMENTS);
        if (annotation == null || annotation.isBlank()) return List.of();

        User caller = RequestContextInstrumentation.from(env.getGraphQlContext()).caller();
        // §12 — the caller's own catalog decides what exists at all; a name the caller may not see
        // and a name that was deleted are indistinguishable from here, which is the point.
        List<DocumentEntry> visible = documents.list(caller);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : new LinkedHashSet<>(List.of(annotation.split(","))).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList())
        {
            DocumentEntry entry = visible.stream().filter(d -> d.name().equals(name)).findFirst()
                    .orElse(null);
            if (entry == null) continue;                       // deleted or not readable — dropped
            DocumentBinding.Binding binding = bindingOf(entry, caller);
            if (binding == null) continue;                     // not a by-id context document
            if (!matchesClassificationKind(type, binding.kind())) continue;
            result.add(Map.of("name", entry.name(), "param", binding.param()));
        }
        return result;
    }

    /** The document's binding, derived from its view's text (D2). Null when not bindable. */
    private DocumentBinding.Binding bindingOf(DocumentEntry document, User caller)
    {
        Optional<ViewEntry> view = views.findViewForCaller(document.viewName(), caller);
        return view.map(v -> DocumentBinding.derive(v.queryText())).orElse(null);
    }

    /**
     * A reservation document belongs on an event type, an allocatable document on a resource or
     * person type. USER has no dynamic type (D1), so a user-anchored document never matches.
     */
    private static boolean matchesClassificationKind(DynamicType type, DocumentBinding.Kind kind)
    {
        String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if (classificationType == null) return false;
        return switch (kind)
        {
            case RESERVATION -> DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION
                    .equals(classificationType);
            case ALLOCATABLE -> DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE
                    .equals(classificationType)
                    || DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(classificationType);
            case USER -> false;
        };
    }

    private boolean runtimeEnabled()
    {
        try
        {
            return operator.getPreferences(null, false)
                    .getEntryAsBoolean(DocumentsPlugin.ENABLED, DocumentsPlugin.ENABLE_BY_DEFAULT);
        }
        catch (RaplaException e)
        {
            return DocumentsPlugin.ENABLE_BY_DEFAULT;
        }
    }
}
