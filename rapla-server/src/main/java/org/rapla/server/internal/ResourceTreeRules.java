package org.rapla.server.internal;

import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.facade.RaplaComponent;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class ResourceTreeRules
{
    private ResourceTreeRules()
    {
    }

    public static Attribute categorizationAttribute(Classification classification)
    {
        for (Attribute attribute : classification.getType().getAttributeIterable())
        {
            if ("true".equals(attribute.getAnnotation(AttributeAnnotations.KEY_CATEGORIZATION)))
            {
                return attribute;
            }
        }
        return null;
    }

    public static List<List<String>> groupPaths(Classification classification, Locale locale, Predicate<Object> readable)
    {
        Attribute attribute = categorizationAttribute(classification);
        if (attribute == null)
        {
            return List.of();
        }
        return classification.getValues(attribute).stream()
                .filter(readable)
                .map(value -> RaplaComponent.getName(value, locale))
                .filter(name -> !name.isBlank())
                .map(List::of)
                .toList();
    }
}
