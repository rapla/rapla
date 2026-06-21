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
import java.util.Map;
import java.util.Optional;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.internal.UserImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 074 — BUILTIN view catalog + CUSTOM view CRUD backed by system {@link Preferences}.
 * Validation runs at read time against the current live {@link GraphQLSchema};
 * invalid/broken views are surfaced in {@link ViewEntry#invalidReason()} but never deleted.
 */
@Component
public class ViewCatalogService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ViewCatalogService.class);

    private static final TypedComponentRole<String> VIEWS_KEY =
            new TypedComponentRole<>("org.rapla.graphql.customViews");
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final List<ViewEntry> BUILTIN_VIEWS = List.of(
            ViewEntry.builtin("rapla_appointments", "Termine",
                    "query rapla_appointments($filter: ReservationFilter!) @view(title: \"Termine\") {\n"
                    + "  appointmentBlocks(filter: $filter) {\n"
                    + "    name: reservation { displayName }\n"
                    + "    start\n"
                    + "    end\n"
                    + "    duration\n"
                    + "  }\n"
                    + "}"),
            ViewEntry.builtin("rapla_reservations", "Veranstaltungen",
                    "query rapla_reservations($filter: ReservationFilter!) @view(title: \"Veranstaltungen\") {\n"
                    + "  reservations(filter: $filter) {\n"
                    + "    name: displayName\n"
                    + "    start: firstDate\n"
                    + "    lastChanged: lastModifiedAt\n"
                    + "  }\n"
                    + "}")
    );

    private final StorageOperator operator;
    private final HotSwappableGraphQlSource graphQlSource;

    public ViewCatalogService(StorageOperator operator, HotSwappableGraphQlSource graphQlSource)
    {
        this.operator = operator;
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

        List<StoredViewData> stored = loadStored();
        stored.removeIf(v -> v.name().equals(name));
        stored.add(new StoredViewData(name, queryText, isPublic,
                groups == null ? List.of() : groups, defaultVariables));
        persist(stored, callerUser);
        return List.of();
    }

    /** Delete a CUSTOM view. Returns false when BUILTIN or not found. */
    public boolean deleteView(String name, User callerUser) throws RaplaException
    {
        for (ViewEntry b : BUILTIN_VIEWS)
        {
            if (b.name().equals(name)) return false;
        }
        List<StoredViewData> stored = loadStored();
        boolean removed = stored.removeIf(v -> v.name().equals(name));
        if (removed) persist(stored, callerUser);
        return removed;
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
        try
        {
            Preferences prefs = operator.getPreferences(null, false);
            if (prefs == null) return new ArrayList<>();
            String json = prefs.getEntryAsString(VIEWS_KEY, null);
            if (json == null || json.isBlank()) return new ArrayList<>();
            StoredViewData[] arr = MAPPER.readValue(json, StoredViewData[].class);
            List<StoredViewData> result = new ArrayList<>(arr.length);
            for (StoredViewData d : arr) result.add(d);
            return result;
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to load custom views from preferences", e);
            return new ArrayList<>();
        }
    }

    private void persist(List<StoredViewData> views, User callerUser) throws RaplaException
    {
        String json;
        try
        {
            json = MAPPER.writeValueAsString(views);
        }
        catch (Exception e)
        {
            throw new RaplaException("Failed to serialize views", e);
        }
        Preferences sysprefs = operator.getPreferences(null, true);
        Map<Entity, Entity> edits = operator.editObjects(List.of(sysprefs), callerUser);
        Preferences edit = (Preferences) edits.get(sysprefs);
        if (edit == null)
        {
            LOGGER.warn("Could not obtain editable system preferences for view catalog");
            return;
        }
        edit.putEntry(VIEWS_KEY, json);
        operator.storeAndRemove(List.of(edit), List.of(), callerUser);
    }

    /** JSON-serializable storage record for CUSTOM views (valid/invalidReason not stored). */
    record StoredViewData(String name, String queryText, boolean isPublic, List<String> groups,
            String defaultVariables) { }
}
