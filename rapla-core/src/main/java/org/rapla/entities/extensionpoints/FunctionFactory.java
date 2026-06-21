package org.rapla.entities.extensionpoints;

import org.rapla.entities.IllegalAnnotationException;

import java.util.Collection;
import java.util.List;


public interface FunctionFactory {
    Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException;

    /**
     * PRD 073 — declare the functions this factory provides as {@link FunctionDescriptor}s so a
     * central catalog (GraphQL {@code computeFunctions}, editor autocomplete, view validation) can
     * enumerate them. Default is empty for factories that haven't declared metadata yet — they
     * still work via {@link #createFunction}, they're just absent from the catalog.
     */
    default Collection<FunctionDescriptor> getDescriptors() {
        return List.of();
    }

}
