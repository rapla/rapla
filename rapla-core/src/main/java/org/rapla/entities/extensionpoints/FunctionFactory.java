package org.rapla.entities.extensionpoints;

import org.rapla.entities.IllegalAnnotationException;

import java.util.List;


public interface FunctionFactory {
    Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException;

}