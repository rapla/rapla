package org.rapla.entities.dynamictype.internal;

import org.rapla.entities.User;
import org.rapla.storage.PermissionController;

import java.util.List;
import java.util.Locale;


final public class EvalContext
{
    private int callStackDepth;
    private Locale locale;
    private String annotationName;
    private List<?> contextObjects;
    private EvalContext parent;
    private PermissionController permissionController;
    private User user;

    public EvalContext(Locale locale, String annotationName, PermissionController permissionController,User user,List contextObjects)
    {
        this(locale, annotationName,permissionController, user, contextObjects, 0);
    }

    EvalContext(Locale locale, String annotationName, PermissionController permissionController,User user,List contextObjects, int callStackDepth)
    {
        this.locale = locale;
        this.user = user;
        this.permissionController = permissionController;
        this.callStackDepth = callStackDepth;
        this.annotationName = annotationName;
        this.contextObjects = contextObjects;
    }

    public EvalContext(List<Object> contextObjects, EvalContext parent)
    {
        this.locale = parent.locale;
        this.user = parent.user;
        this.callStackDepth = parent.callStackDepth + 1;
        this.annotationName = parent.annotationName;
        this.contextObjects = contextObjects;
        this.parent = parent;
    }

    public User getUser()
    {
        return user;
    }

    PermissionController getPermissionController()
    {
        return  permissionController;
    }

    EvalContext getParent()
    {
        return parent;
    }

    public EvalContext clone(Locale locale)
    {
        EvalContext clone = new EvalContext(locale, annotationName, permissionController,user,contextObjects, callStackDepth);
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
