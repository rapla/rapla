package org.rapla.entities.dynamictype.internal;

import org.rapla.entities.User;
import org.rapla.storage.PermissionController;

import java.util.List;
import java.util.Locale;
import java.util.Map;


final public class EvalContext
{
    private final int callStackDepth;
    private final Locale locale;
    private final String annotationName;
    private final List<?> contextObjects;
    private EvalContext parent;
    private PermissionController permissionController;
    private final User user;
    Map<String,Object> environment;

    public EvalContext(Locale locale, String annotationName, PermissionController permissionController,Map<String,Object> environment, User user, List contextObjects)
    {
        this(locale, annotationName,permissionController,environment, user, contextObjects, 0);
    }

    EvalContext(Locale locale, String annotationName, PermissionController permissionController,Map<String,Object> environment, User user,List contextObjects, int callStackDepth)
    {
        this.locale = locale;
        this.user = user;
        this.permissionController = permissionController;
        this.environment = environment;
        this.callStackDepth = callStackDepth;
        this.annotationName = annotationName;
        this.contextObjects = contextObjects;
    }

    public EvalContext(List<Object> contextObjects, EvalContext parent)
    {
        this.locale = parent.locale;
        this.user = parent.user;
        this.environment = parent.environment;
        this.callStackDepth = parent.callStackDepth + 1;
        this.annotationName = parent.annotationName;
        this.contextObjects = contextObjects;
        this.parent = parent;
    }

    public User getUser()
    {
        return user;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
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
        EvalContext clone = new EvalContext(locale, annotationName, permissionController,environment,user,contextObjects, callStackDepth);
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
