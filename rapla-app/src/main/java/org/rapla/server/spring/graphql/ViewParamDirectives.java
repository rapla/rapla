package org.rapla.server.spring.graphql;

import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.VariableDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PRD 074 §"Window and inputs directives" — the declared public-input surface of a stored view:
 * its repeatable {@code @param(name, into, required)} directives plus whether it declares a
 * {@code @window} (which opens the {@code from}/{@code to} URL keys). Consumed by the PRD 097
 * document path to gate URL parameters; the SPA fills variables by type and never reads this.
 */
public final class ViewParamDirectives
{
    /** One declared public input: URL key {@code name} fills the private dotted path {@code into}. */
    public record Param(String name, String into, boolean required) {}

    /** The view's declared input surface. {@code windowInto} is the @window target variable (default "filter"). */
    public record Declarations(List<Param> params, boolean hasWindow, String windowInto)
    {
        public Param byName(String name)
        {
            for (Param p : params) if (p.name().equals(name)) return p;
            return null;
        }
    }

    private static final Declarations NONE = new Declarations(List.of(), false, "filter");

    private ViewParamDirectives() {}

    /** Parse the declarations off the first operation of {@code queryText}; unparseable → none declared. */
    public static Declarations parse(String queryText)
    {
        OperationDefinition op = firstOperation(queryText);
        if (op == null) return NONE;

        List<Param> params = new ArrayList<>();
        boolean hasWindow = false;
        String windowInto = "filter";
        for (Directive d : op.getDirectives())
        {
            if ("param".equals(d.getName()))
            {
                String name = stringArg(d, "name");
                String into = stringArg(d, "into");
                if (name != null && into != null)
                {
                    params.add(new Param(name, into, Boolean.TRUE.equals(boolArg(d, "required"))));
                }
            }
            else if ("window".equals(d.getName()))
            {
                hasWindow = true;
                String into = stringArg(d, "into");
                if (into != null) windowInto = into;
            }
        }
        return new Declarations(List.copyOf(params), hasWindow, windowInto);
    }

    /** One validation problem, anchored to the offending directive so an editor can mark it red. */
    public record Issue(String message, int line, int column) {}

    /**
     * Save-time validation of the declared input surface (PRD 074): every {@code @param.into}
     * must resolve to a real variable path in the schema, public names must be unique (and not
     * shadow {@code from}/{@code to} when {@code @window} is declared), and a {@code @window}
     * target variable must be an input object carrying {@code from}/{@code to}. A typo'd path
     * is rejected here instead of surfacing as a silently-empty document render.
     */
    public static List<String> validate(String queryText, GraphQLSchema schema)
    {
        return validateWithPositions(queryText, schema).stream().map(Issue::message).toList();
    }

    /**
     * Same checks as {@link #validate}, but each issue carries the source position of the
     * directive that caused it — an editor cannot mark a line red without one. Walks the
     * directives directly (rather than the parsed {@link Declarations}) precisely to keep hold
     * of each {@link SourceLocation}.
     */
    public static List<Issue> validateWithPositions(String queryText, GraphQLSchema schema)
    {
        OperationDefinition op = firstOperation(queryText);
        if (op == null) return List.of();

        List<Issue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean hasWindow = op.getDirectives().stream().anyMatch(d -> "window".equals(d.getName()));

        for (Directive d : op.getDirectives())
        {
            SourceLocation loc = d.getSourceLocation();
            if ("param".equals(d.getName()))
            {
                String name = stringArg(d, "name");
                String into = stringArg(d, "into");
                if (name == null || into == null)
                {
                    issues.add(issue(loc, "@param requires both 'name' and 'into'"));
                    continue;
                }
                if (name.isBlank() || name.contains("."))
                {
                    issues.add(issue(loc, "@param name '" + name + "' must be a plain public key"));
                }
                if (!seen.add(name))
                {
                    issues.add(issue(loc, "@param name '" + name + "' is declared twice"));
                }
                if (hasWindow && ("from".equals(name) || "to".equals(name)))
                {
                    issues.add(issue(loc, "@param name '" + name
                            + "' collides with the @window URL keys from/to"));
                }
                String pathError = resolveIntoPath(into, op, schema);
                if (pathError != null) issues.add(issue(loc, pathError));
            }
            else if ("window".equals(d.getName()))
            {
                String into = stringArg(d, "into");
                String target = into != null ? into : "filter";
                GraphQLType type = variableType(target, op, schema);
                if (type == null)
                {
                    issues.add(issue(loc, "@window targets '" + target + "', which is not a declared"
                            + " variable — declared variables: " + declaredVariableNames(op)));
                }
                else if (!(type instanceof GraphQLInputObjectType obj)
                        || obj.getField("from") == null || obj.getField("to") == null)
                {
                    issues.add(issue(loc, "@window targets '" + target
                            + "', whose type carries no from/to fields (a @window needs a date-range"
                            + " input such as ReservationFilter)"));
                }
            }
        }
        return issues;
    }

    private static Issue issue(SourceLocation loc, String message)
    {
        return new Issue(message, loc != null ? loc.getLine() : 1, loc != null ? loc.getColumn() : 1);
    }

    /** One legal {@code @param(into:)} target — a dotted path plus the GraphQL type it lands on. */
    public record IntoPath(String path, String type) {}

    /**
     * PRD 074 — every dotted path a {@code @param(into:)} could legally target in this query:
     * each declared variable, plus (one level down) the fields of any input-object variable.
     * Feeds the editor's completion so it offers exactly what {@link #validate} would accept —
     * one walk, so the two cannot disagree.
     *
     * <p>Depth is one level: rapla's filters are flat ({@code filter.allocatableIdsIn}), and
     * nesting deeper ({@code filter.allocatableMatching.…}) would enumerate a combinatorial tree
     * for no present use case.
     */
    public static List<IntoPath> intoPaths(String queryText, GraphQLSchema schema)
    {
        OperationDefinition op = firstOperation(queryText);
        if (op == null) return List.of();

        List<IntoPath> paths = new ArrayList<>();
        for (VariableDefinition v : op.getVariableDefinitions())
        {
            GraphQLType type = variableType(v.getName(), op, schema);
            if (type == null) continue;
            if (type instanceof GraphQLInputObjectType obj)
            {
                for (GraphQLInputObjectField f : obj.getFields())
                {
                    paths.add(new IntoPath(v.getName() + "." + f.getName(),
                            GraphQLTypeUtil.simplePrint(f.getType())));
                }
            }
            else
            {
                // a scalar/enum variable is itself a legal target (e.g. into: "eventId")
                paths.add(new IntoPath(v.getName(), GraphQLTypeUtil.simplePrint(type)));
            }
        }
        return paths;
    }

    /**
     * Walk a dotted {@code into} path from its variable's declared type; null when it resolves.
     * A failure names the alternatives ("available: …") — GraphiQL is CDN-loaded and cannot be
     * given a completion provider for a directive's String argument, so the error message is the
     * authoring affordance. (Monaco-side completion reuses this same walk — PRD 074 Phase 4.)
     */
    private static String resolveIntoPath(String into, OperationDefinition op, GraphQLSchema schema)
    {
        String[] path = into.split("\\.");
        GraphQLType type = variableType(path[0], op, schema);
        if (type == null)
        {
            return "@param into '" + into + "': '" + path[0] + "' is not a declared variable"
                    + " — declared variables: " + declaredVariableNames(op);
        }
        for (int i = 1; i < path.length; i++)
        {
            if (!(type instanceof GraphQLInputObjectType obj))
            {
                return "@param into '" + into + "': '" + path[i - 1] + "' is not an input object,"
                        + " so '" + path[i] + "' cannot be reached";
            }
            GraphQLInputObjectField field = obj.getField(path[i]);
            if (field == null)
            {
                return "@param into '" + into + "': " + obj.getName() + " has no field '" + path[i]
                        + "' — available: " + fieldNames(obj);
            }
            type = GraphQLTypeUtil.unwrapAll(field.getType());
        }
        return null;
    }

    /** The input object's field names, for the "available: …" hint. */
    private static String fieldNames(GraphQLInputObjectType obj)
    {
        return obj.getFields().stream().map(GraphQLInputObjectField::getName)
                .sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    /** The operation's declared variable names, for the "declared variables: …" hint. */
    private static String declaredVariableNames(OperationDefinition op)
    {
        return op.getVariableDefinitions().stream().map(VariableDefinition::getName)
                .sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    /** The unwrapped schema type of the operation variable {@code name}, or null if undeclared. */
    private static GraphQLType variableType(String name, OperationDefinition op, GraphQLSchema schema)
    {
        for (VariableDefinition v : op.getVariableDefinitions())
        {
            if (!name.equals(v.getName())) continue;
            graphql.language.Type<?> t = v.getType();
            while (true)
            {
                if (t instanceof graphql.language.NonNullType nn) t = nn.getType();
                else if (t instanceof graphql.language.ListType lt) t = lt.getType();
                else break;
            }
            if (t instanceof graphql.language.TypeName tn)
            {
                return schema.getType(tn.getName());
            }
            return null;
        }
        return null;
    }

    private static OperationDefinition firstOperation(String queryText)
    {
        if (queryText == null || queryText.isBlank()) return null;
        try
        {
            Document doc = new Parser().parseDocument(queryText);
            return doc.getDefinitions().stream()
                    .filter(d -> d instanceof OperationDefinition)
                    .map(d -> (OperationDefinition) d)
                    .findFirst().orElse(null);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String stringArg(Directive d, String name)
    {
        Argument a = arg(d, name);
        return (a != null && a.getValue() instanceof StringValue sv) ? sv.getValue() : null;
    }

    private static Boolean boolArg(Directive d, String name)
    {
        Argument a = arg(d, name);
        return (a != null && a.getValue() instanceof BooleanValue bv) ? bv.isValue() : null;
    }

    private static Argument arg(Directive d, String name)
    {
        for (Argument a : d.getArguments()) if (name.equals(a.getName())) return a;
        return null;
    }
}
