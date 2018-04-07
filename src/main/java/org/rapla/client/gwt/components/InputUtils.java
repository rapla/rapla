package org.rapla.client.gwt.components;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SelectElement;
import org.rapla.client.gwt.components.DropDownInputField.DropDownItem;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public class InputUtils
{
    public static native boolean isHtml5DateInputSupported()/*-{
		var datefield = document.createElement("input");
		datefield.setAttribute("type", "date");
		return datefield.type == "date"
    }-*/;

    public static boolean isAttributeBoolean(Attribute attribute)
    {
        if (attribute != null)
        {
            return AttributeType.BOOLEAN.is(attribute.getType());
        }
        return false;
    }

    public static boolean isAttributeString(Attribute attribute)
    {
        if (attribute != null)
        {
            return AttributeType.STRING.is(attribute.getType());
        }
        return false;
    }

    public static boolean isAttributeDate(Attribute attribute)
    {
        if (attribute != null)
        {
            return AttributeType.DATE.is(attribute.getType());
        }
        return false;
    }

    public static boolean isAttributeInt(Attribute attribute)
    {
        if (attribute != null)
        {
            return AttributeType.INT.is(attribute.getType());
        }
        return false;
    }

    public static boolean isAllocatable(Attribute attribute)
    {
        if (attribute != null)
        {
            return AttributeType.ALLOCATABLE.is(attribute.getType());
        }
        return false;
    }

    public static boolean isCategory(Attribute attribute)
    {
        if (attribute != null)
        {
            return AttributeType.CATEGORY.is(attribute.getType());
        }
        return false;
    }

    public static Map<String, Category> createIdMap(Category rootCategory)
    {
        final LinkedHashMap<String, Category> result = new LinkedHashMap<>();
        fill(rootCategory, result);
        return result;
    }

    private static void fill(Category category, final LinkedHashMap<String, Category> result)
    {
        if (category != null)
        {
            result.put(category.getId(), category);
            final Category[] subCategories = category.getCategories();
            if (subCategories != null)
            {
                for (Category subCategory : subCategories)
                {
                    fill(subCategory, result);
                }
            }
        }
    }

    public static Collection<DropDownItem> createDropDownItems(Map<String, Category> idToCategory, Locale locale, Collection<Object> categories)
    {
        Collection<DropDownItem> result = new ArrayList<>();
        for (Entry<String, Category> entry : idToCategory.entrySet())
        {
            boolean selected = categories.contains(entry.getValue());
            result.add(new DropDownItem(entry.getValue().getName(locale), entry.getKey(), selected));
        }
        return result;
    }
    
    public static boolean focusOnFirstInput(NodeList<Node> childNodes)
    {
        final int length = childNodes.getLength();
        for (int i = 0; i < length; i++)
        {
            final Node item = childNodes.getItem(i);
            if (Element.is(item) && !"hidden".equals(Element.as(item).getStyle().getOverflow()))
            {
                if (InputElement.is(item))
                {
                    InputElement.as(item).focus();
                    return true;
                }
                if (SelectElement.is(item))
                {
                    SelectElement.as(item).focus();
                    return true;
                }
            }
            final NodeList<Node> subChildNodes = item.getChildNodes();
            if (focusOnFirstInput(subChildNodes))
            {
                return true;
            }
        }
        return false;
    }


}
