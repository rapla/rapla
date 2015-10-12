package org.rapla.entities.dynamictype.internal;

import java.util.List;
import java.util.Locale;


final public class EvalContext
{
    private int callStackDepth;
    private Locale locale;
    private String annotationName;
    private List<?> contextObjects;
    private EvalContext parent;

    public EvalContext(Locale locale, String annotationName, List contextObjects)
    {
        this(locale, annotationName, contextObjects, 0);
    }

    EvalContext(Locale locale, String annotationName, List contextObjects, int callStackDepth)
    {
        this.locale = locale;
        this.callStackDepth = callStackDepth;
        this.annotationName = annotationName;
        this.contextObjects = contextObjects;
    }

    public EvalContext(List<Object> contextObjects, EvalContext parent)
    {
        this.locale = parent.locale;
        this.callStackDepth = parent.callStackDepth + 1;
        this.annotationName = parent.annotationName;
        this.contextObjects = contextObjects;
        this.parent = parent;
    }

    EvalContext getParent()
    {
        return parent;
    }

    public EvalContext clone(Locale locale)
    {
        EvalContext clone = new EvalContext(locale, annotationName, contextObjects, callStackDepth);
        return clone;
    }

    public Object getContextObject(int pos)
    {
        if (pos < contextObjects.size())
        {
            return contextObjects.get(pos);
        }
        return null;
    }

    public List<?> getContextObjects()
    {
        return contextObjects;
    }

    public Object getFirstContextObject()
    {
        if (contextObjects.size() > 0)
        {
            return contextObjects.get(0);
        }
        return null;
    }

    public String getAnnotationName()
    {
        return annotationName;
    }

    public int getCallStackDepth()
    {
        return callStackDepth;
    }

    public Locale getLocale()
    {
        return locale;
    }

}
