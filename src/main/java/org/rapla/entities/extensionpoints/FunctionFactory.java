package org.rapla.entities.extensionpoints;

import java.util.List;

import org.rapla.entities.IllegalAnnotationException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context=InjectionContext.all, id="org.rapla.entities.Function")
public interface FunctionFactory {
    Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException;

}