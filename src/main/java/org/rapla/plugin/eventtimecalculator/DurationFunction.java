package org.rapla.plugin.eventtimecalculator;

import java.util.List;

import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.entities.extensionpoints.FunctionFactory.Function;

public class DurationFunction extends Function
{
    EventTimeCalculatorFactory factory;

    public DurationFunction(String name, List<Function> args)
    {
        super(name, args);
    }

    @Override public Object eval(ParsedText.EvalContext context)
    {
        return null;
    }
}
