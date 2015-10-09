package org.rapla.entities.dynamictype.internal;

import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.extensionpoints.FunctionFactory;

public interface ParseContext
{
    FunctionFactory.Function resolveVariableFunction(String variableName) throws IllegalAnnotationException;
    FunctionFactory getFunctionFactory(String functionName);
}
