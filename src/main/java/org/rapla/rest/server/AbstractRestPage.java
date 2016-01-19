package org.rapla.rest.server;

import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.jsonrpc.server.JsonServlet;
import org.rapla.storage.StorageOperator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class AbstractRestPage 
{

    protected StorageOperator operator;
    protected JsonServlet servlet;
    protected RaplaFacade facade;

    public AbstractRestPage(RaplaFacade facade) throws RaplaException
    {
        this.facade = facade;
        operator = facade.getOperator();
    }

    public ClassificationFilter[] getClassificationFilter(Map<String, String> simpleFilter, Collection<String> selectedClassificationTypes,
            Collection<String> typeNames) throws RaplaException
    {
        ClassificationFilter[] filters = null;
        if (simpleFilter == null && typeNames == null)
        {
            return null;
        }
        {
            DynamicType[] types = getFacade().getDynamicTypes(null);
            List<ClassificationFilter> filterList = new ArrayList<ClassificationFilter>();
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
                            // convert from string to attribute type
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
            filters = filterList.toArray(new ClassificationFilter[] {});
        }
        return filters;
    }

    protected RaplaFacade getFacade()
    {
        return facade;
    }


}