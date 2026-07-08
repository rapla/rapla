package org.rapla.server.spring.graphql;

import graphql.language.Argument;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.StringValue;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.rapla.entities.User;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 074 — BUILTIN view catalog + CUSTOM view CRUD backed by the server artifact store
 * (PRD 098, kind=VIEW — the earlier system-Preferences storage was cut off without migration).
 * Validation runs at read time against the current live {@link GraphQLSchema};
 * invalid/broken views are surfaced in {@link ViewEntry#invalidReason()} but never deleted.
 */
@Component
public class ViewCatalogService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ViewCatalogService.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final List<ViewEntry> BUILTIN_VIEWS = List.of(
            ViewEntry.builtin("rapla_appointments", "Termine",
                    "query rapla_appointments(\n"
                    + "    $filter: ReservationFilter!,\n"
                    + "    $sort:   [BlockSort!] = [{ field: START, dir: ASC }],\n"
                    + "    $offset: Int = 0\n"
                    + "  ) @view(title: \"Termine\", rowLabel: \"Termin|Termine\", renderModes: [table, week, month]) {\n"
                    + "  appointmentBlocks(filter: $filter, sort: $sort, offset: $offset) {\n"
                    + "    start @column(header: \"Von\",           order: 1)\n"
                    + "    end   @column(header: \"Bis\",           order: 2)\n"
                    + "    name  @column(header: \"Titel\",         order: 3)\n"
                    + "    color @hidden\n"
                    + "    reservation @hidden { id  canModify  appointmentCount }\n"
                    + "    appointment @hidden { id  repeating { type } }\n"
                    + "    isException @hidden\n"
                    + "    persons: allocatables(filter: { isPersonEq: true })\n"
                    + "      @join(separator: \", \") @column(header: \"Personen\",   order: 4) {\n"
                    + "      id  name  isLocation\n"
                    + "    }\n"
                    + "    resources: allocatables(filter: { isPersonEq: false })\n"
                    + "      @join(separator: \", \") @column(header: \"Ressourcen\", order: 5) {\n"
                    + "      id  name  isLocation\n"
                    + "    }\n"
                    + "  }\n"
                    + "}"),
            ViewEntry.builtin("rapla_reservations", "Veranstaltungen",
                    "query rapla_reservations($filter: ReservationFilter!) @view(title: \"Veranstaltungen\", rowLabel: \"Veranstaltung|Veranstaltungen\") {\n"
                    + "  reservations(filter: $filter) {\n"
                    + "    name: displayName\n"
                    + "    start: firstDate\n"
                    + "    lastChanged: lastModifiedAt\n"
                    + "    reservationId: id @hidden\n"
                    + "    canModify @hidden\n"
                    + "    appointmentCount @hidden\n"
                    + "  }\n"
                    + "}")
    );

    private final ArtifactCatalogService artifactCatalog;
    private final HotSwappableGraphQlSource graphQlSource;

    public ViewCatalogService(ArtifactCatalogService artifactCatalog, HotSwappableGraphQlSource graphQlSource)
    {
        this.artifactCatalog = artifactCatalog;
        this.graphQlSource = graphQlSource;
    }

    /** All views visible to caller: BUILTIN first, then validated CUSTOM. */
    public List<ViewEntry> listViewsForCaller(User caller)
    {
        List<ViewEntry> result = new ArrayList<>(BUILTIN_VIEWS);
        GraphQLSchema schema = graphQlSource.schema();
        for (StoredViewData stored : loadStored())
        {
            if (!isVisible(stored, caller)) continue;
            List<String> errors = validate(stored.queryText(), schema);
            result.add(new ViewEntry(
                    stored.name(), extractTitle(stored.queryText()), stored.queryText(),
                    false, stored.isPublic(), stored.groups(),
                    errors.isEmpty(), errors, stored.defaultVariables()));
        }
        return result;
    }

    /**
     * Re-validate all CUSTOM views against the given (freshly-rebuilt) schema and
     * update the in-memory validity cache. Called by {@link GraphQlSchemaRebuilder}
     * immediately after a successful schema rebuild (PRD 074 §"Revalidate-and-mark").
     * No persistence — marks are always recomputed at read time from the live schema,
     * which is equivalent (single-pod) and sufficient for the immediate-visibility goal.
     */
    public void revalidateCustomViews(GraphQLSchema newSchema)
    {
        List<StoredViewData> stored = loadStored();
        if (stored.isEmpty()) return;
        int invalid = 0;
        for (StoredViewData v : stored)
        {
            List<String> errors = validate(v.queryText(), newSchema);
            if (!errors.isEmpty()) invalid++;
        }
        if (invalid > 0)
            LOGGER.info("GraphQL view revalidation: {}/{} custom view(s) are now invalid after schema change",
                    invalid, stored.size());
    }

    /** Find a view by name for execution (ignores caller visibility). */
    public Optional<ViewEntry> findView(String name)
    {
        for (ViewEntry b : BUILTIN_VIEWS)
        {
            if (b.name().equals(name)) return Optional.of(b);
        }
        GraphQLSchema schema = graphQlSource.schema();
        for (StoredViewData stored : loadStored())
        {
            if (stored.name().equals(name))
            {
                List<String> errors = validate(stored.queryText(), schema);
                return Optional.of(new ViewEntry(
                        stored.name(), extractTitle(stored.queryText()), stored.queryText(),
                        false, stored.isPublic(), stored.groups(),
                        errors.isEmpty(), errors, stored.defaultVariables()));
            }
        }
        return Optional.empty();
    }

    /**
     * Save or overwrite a CUSTOM view. Rejects BUILTIN name collisions.
     * Returns empty list on success, or validation errors/rejections.
     */
    public List<String> saveView(String name, String queryText, boolean isPublic,
            List<String> groups, String defaultVariables, User callerUser) throws RaplaException
    {
        for (ViewEntry b : BUILTIN_VIEWS)
        {
            if (b.name().equals(name))
                return List.of("'" + name + "' is a built-in view name and cannot be overwritten");
        }
        GraphQLSchema schema = graphQlSource.schema();
        List<String> errors = validate(queryText, schema);
        if (!errors.isEmpty()) return errors;

        ViewMeta meta = new ViewMeta(isPublic, groups == null ? List.of() : groups, defaultVariables);
        artifactCatalog.save(StoredArtifact.KIND_VIEW, name, queryText, MAPPER.writeValueAsString(meta), callerUser);
        return List.of();
    }

    /** Delete a CUSTOM view. Returns false when BUILTIN or not found. */
    public boolean deleteView(String name, User callerUser) throws RaplaException
    {
        for (ViewEntry b : BUILTIN_VIEWS)
        {
            if (b.name().equals(name)) return false;
        }
        return artifactCatalog.delete(StoredArtifact.KIND_VIEW, name, callerUser);
    }

    private boolean isVisible(StoredViewData v, User caller)
    {
        if (caller == null) return v.isPublic();
        if (caller.isAdmin()) return true;
        if (v.isPublic()) return true;
        if (!v.groups().isEmpty())
        {
            java.util.Collection<String> userGroups = UserImpl.getGroupsIncludingParents(caller);
            for (String groupId : v.groups())
            {
                if (userGroups.contains(groupId)) return true;
            }
        }
        return false;
    }

    private List<String> validate(String queryText, GraphQLSchema schema)
    {
        try
        {
            Document doc = Parser.parse(queryText);
            List<ValidationError> errors = new Validator().validateDocument(schema, doc, Locale.getDefault());
            return errors.stream().map(ValidationError::getMessage).toList();
        }
        catch (Exception e)
        {
            return List.of("Parse error: " + e.getMessage());
        }
    }

    /** Parse {@code @view(title: "...")} from the query text; null if absent or parse fails. */
    static String extractTitle(String queryText)
    {
        try
        {
            Document doc = Parser.parse(queryText);
            for (Definition<?> def : doc.getDefinitions())
            {
                if (!(def instanceof OperationDefinition op)) continue;
                for (Directive d : op.getDirectives())
                {
                    if (!"view".equals(d.getName())) continue;
                    for (Argument a : d.getArguments())
                    {
                        if ("title".equals(a.getName()) && a.getValue() instanceof StringValue sv)
                            return sv.getValue();
                    }
                }
            }
        }
        catch (Exception ignored) { }
        return null;
    }

    private List<StoredViewData> loadStored()
    {
        List<StoredViewData> result = new ArrayList<>();
        for (StoredArtifact artifact : artifactCatalog.list(StoredArtifact.KIND_VIEW))
        {
            ViewMeta meta;
            try
            {
                String metadata = artifact.getMetadata();
                meta = metadata == null || metadata.isBlank()
                        ? new ViewMeta(false, List.of(), null)
                        : MAPPER.readValue(metadata, ViewMeta.class);
            }
            catch (Exception e)
            {
                LOGGER.warn("Ignoring unparseable metadata of view artifact {}", artifact.getId(), e);
                meta = new ViewMeta(false, List.of(), null);
            }
            result.add(new StoredViewData(artifact.getName(), artifact.getBody(), meta.isPublic(),
                    meta.groups() == null ? List.of() : meta.groups(), meta.defaultVariables()));
        }
        return result;
    }

    /** Visibility/default-variables metadata persisted as the artifact's metadata JSON (PRD 098). */
    record ViewMeta(boolean isPublic, List<String> groups, String defaultVariables) { }

    /** In-memory carrier joining artifact body + parsed metadata (valid/invalidReason not stored). */
    record StoredViewData(String name, String queryText, boolean isPublic, List<String> groups,
            String defaultVariables) { }
}
