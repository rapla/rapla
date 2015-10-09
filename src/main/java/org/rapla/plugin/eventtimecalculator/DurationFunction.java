package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.dynamictype.internal.ParsedText;

import java.util.List;

public class DurationFunction extends ParsedText.Function
{
    EventTimeCalculatorFactory factory;

    public DurationFunction(String name, List<ParsedText.Function> args)
    {
        super(name, args);
    }

    @Override public Object eval(ParsedText.EvalContext context)
    {
        return null;
    }
}
