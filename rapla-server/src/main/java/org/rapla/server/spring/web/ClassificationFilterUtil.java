package org.rapla.server.spring.web;

import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Shared classification-filter builder for the REST endpoints under
 * {@code /api/resources} and {@code /api/events}. Extracted from the
 * deleted {@code RaplaResourcesRestPage} when the RestPages were
 * collapsed into their controllers (PRD 049 Phase 3).
 */
final class ClassificationFilterUtil
{
    private ClassificationFilterUtil() {}

    static ClassificationFilter[] getClassificationFilter(RaplaFacade facade,
                                                          Map<String, String> simpleFilter,
                                                          Collection<String> selectedClassificationTypes,
                                                          Collection<String> typeNames) throws RaplaException
    {
        if (simpleFilter == null && typeNames == null)
        {
            return null;
        }
        DynamicType[] types = facade.getDynamicTypes(null);
        List<ClassificationFilter> filterList = new ArrayList<>();
        for (DynamicType type : types)
        {
            String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (classificationType == null || !selectedClassificationTypes.contains(classificationType))
            {
                continue;
            }
            ClassificationFilter classificationFilter = type.newClassificationFilter();
            if (typeNames != null)
            {
                if (!typeNames.contains(type.getKey()))
                {
                    continue;
                }
            }
            if (simpleFilter != null)
            {
                for (String key : simpleFilter.keySet())
                {
                    Attribute att = type.getAttribute(key);
                    if (att != null)
                    {
                        String value = simpleFilter.get(key);
                        Object object = att.convertValue(value);
                        if (object != null)
                        {
                            classificationFilter.addEqualsRule(att.getKey(), object);
                        }
                        filterList.add(classificationFilter);
                    }
                }
            }
            else
            {
                filterList.add(classificationFilter);
            }
        }
        return filterList.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }
}
