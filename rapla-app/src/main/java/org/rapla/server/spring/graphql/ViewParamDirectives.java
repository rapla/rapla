package org.rapla.server.spring.graphql;

import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.language.StringValue;
import graphql.parser.Parser;
import java.util.ArrayList;
import java.util.List;

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
