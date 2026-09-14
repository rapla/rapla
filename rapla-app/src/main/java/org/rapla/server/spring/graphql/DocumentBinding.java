package org.rapla.server.spring.graphql;

import graphql.language.Argument;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import java.util.Locale;

/**
 * PRD 111 D2 — derive a context document's binding from its view, instead of declaring it.
 *
 * <p>A context document is a by-id view: one {@code @param} feeds the {@code id} argument of a
 * root field, and that root field names the entity kind. Nothing to author, nothing that can
 * drift from the query. A param feeding a list path ({@code filter.allocatableIdsIn}) is not a
 * context binding and answers null — the caller turns that into "cannot be hung on a type".
 */
public final class DocumentBinding
{
    public enum Kind { RESERVATION, ALLOCATABLE, USER }

    /** The public URL parameter that carries the id, and the kind of entity it identifies. */
    public record Binding(String param, Kind kind) {}

    private DocumentBinding() {}

    /** The binding of {@code queryText}, or null when the view is not a by-id context view. */
    public static Binding derive(String queryText)
    {
        if (queryText == null || queryText.isBlank()) return null;
        OperationDefinition op = firstOperation(queryText);
        if (op == null) return null;

        ViewParamDirectives.Declarations decl = ViewParamDirectives.parse(queryText);
        if (decl.params().isEmpty()) return null;

        for (Selection<?> selection : op.getSelectionSet().getSelections())
        {
            if (!(selection instanceof Field root)) continue;
            Kind kind = kindOf(root.getName());
            if (kind == null) continue;
            String variable = idVariable(root);
            if (variable == null) continue;
            // The @param whose private `into` path IS the variable name binds it. A dotted path
            // (filter.allocatableIdsIn) never equals a variable name, so list params drop out here.
            for (ViewParamDirectives.Param param : decl.params())
            {
                if (variable.equals(param.into())) return new Binding(param.name(), kind);
            }
        }
        return null;
    }

    /** The variable name passed as this root field's {@code id} argument, if any. */
    private static String idVariable(Field root)
    {
        for (Argument argument : root.getArguments())
        {
            if ("id".equals(argument.getName()) && argument.getValue() instanceof VariableReference ref)
            {
                return ref.getName();
            }
        }
        return null;
    }

    private static Kind kindOf(String rootFieldName)
    {
        return switch (rootFieldName.toLowerCase(Locale.ROOT))
        {
            case "reservation" -> Kind.RESERVATION;
            case "resource" -> Kind.ALLOCATABLE;
            case "user" -> Kind.USER;
            default -> null;
        };
    }

    private static OperationDefinition firstOperation(String queryText)
    {
        try
        {
            Document document = Parser.parse(queryText);
            return document.getDefinitions().stream()
                    .filter(OperationDefinition.class::isInstance)
                    .map(OperationDefinition.class::cast)
                    .findFirst().orElse(null);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }
}
