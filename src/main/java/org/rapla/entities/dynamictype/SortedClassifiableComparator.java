package org.rapla.entities.dynamictype;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.rapla.entities.NamedComparator;

public class SortedClassifiableComparator implements Comparator<Classifiable>
{
    Locale locale;
    NamedComparator comp;
    Map<DynamicType,Collection<Attribute>> sortingCache = new LinkedHashMap<DynamicType,Collection<Attribute>>();

    public SortedClassifiableComparator(Locale locale)
    {
        this.locale = locale;
        comp = new NamedComparator(locale);
    }

    @Override public int compare(Classifiable o1, Classifiable o2)
    {
        Classification classification1 = o1.getClassification();
        Classification classification2 = o2.getClassification();
        if (classification1 == classification2)
        {
            return 0;
        }
        DynamicType type1 = classification1.getType();
        DynamicType type2 = classification2.getType();
        if (type1.equals(type2))
        {
            Collection<Attribute> sortAttributes = sortingCache.get(type1);
            if ( sortAttributes == null)
            {
                sortAttributes = new ArrayList<Attribute>();
                for (Attribute attribute : type1.getAttributeIterable())
                {
                    String sorting = attribute.getAnnotation(AttributeAnnotations.KEY_SORTING);
                    if (sorting != null)
                    {
                        sortAttributes.add( attribute );
                    }
                }
                sortingCache.put(type1, sortAttributes);
            }
            for (Attribute attribute:sortAttributes)
            {
                String sorting = attribute.getAnnotation(AttributeAnnotations.KEY_SORTING);
                {
                    int order = 0;
                    if (sorting.equals(AttributeAnnotations.VALUE_SORTING_ASCENDING))
                    {
                        order = 1;
                    }
                    if (sorting.equals(AttributeAnnotations.VALUE_SORTING_DESCENDING))
                    {
                        order = -1;
                    }
                    if (order != 0)
                    {
                        Object value1 = classification1.getValue(attribute);
                        Object value2 = classification2.getValue(attribute);
                        if (value1 != null)
                        {
                            if (value2 == null)
                            {
                                return order;
                            }
                            if (value1 instanceof Comparable && value2 instanceof Comparable)
                            {
                                Comparable comparable = (Comparable) value1;
                                @SuppressWarnings("unchecked") int result = order * comparable.compareTo(value2);
                                if (result != 0)
                                {
                                    return result;
                                }
                            }
                            // TODO if field is allocatable link, then we need to sort with this comparator
                            // what todo with links to allocatables that could cause infinite recursion
                        }
                        else if (value2 != null)
                        {
                            return -order;
                        }
                    }
                }
            }
        }
        return comp.compare(o1, o2);
    }

}
