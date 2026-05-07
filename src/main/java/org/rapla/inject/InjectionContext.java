package org.rapla.inject;

/**
 * Specifies in which context the default implemention or the extension point is available.
 * If you specify more then one InjectionContext then the extension point will be available in all specified contexts.
 * <ul>
 *   <li>server: use on the server</li>
 *   <li>gwt: use on all clients,  gwt, android, ios and swing </li>
 *   <li>swing: use in the swing</li>
 *   <li>gwt: use in the html gwt</li>
 *   <li>android: use in the native android app (not currently used)</li>
 *   <li>ios: use in the native ios app (not currently used)</li>
 *   <li>all is placeholder for server and gwt</li>
 * </ul>
 */
public enum InjectionContext
{
    server, client, swing, gwt, android, ios, all;

    public static final String MODULE_FILE_NAME = "org.rapla.servicelist";
    public static final String MODULE_LIST_LOCATION = "META-INF/" + MODULE_FILE_NAME;

    public static boolean inList(InjectionContext context, InjectionContext... contexts)
    {
        for (InjectionContext cont : contexts)
        {
            if (cont == context)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInjectableOnSwing(InjectionContext... contexts)
    {
        for (InjectionContext context : contexts)
        {
            if (context == swing || context == all || context == client)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInjectableOnGwt(InjectionContext... contexts)
    {
        for (InjectionContext context : contexts)
        {
            if (context == gwt || context == all || context == client)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInjectableOnAndroid(InjectionContext... contexts)
    {
        for (InjectionContext context : contexts)
        {
            if (context == android || context == all || context == client)
            {
                return true;
            }
        }
        return false;

    }

    public static boolean isInjectableOnIos(InjectionContext... contexts)
    {
        for (InjectionContext context : contexts)
        {
            if (context == ios || context == all || context == client)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInjectableOnServer(InjectionContext... contexts)
    {
        for (InjectionContext context : contexts)
        {
            if (context == all || context == server)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInjectableOnClient(InjectionContext... contexts)
    {
        for (InjectionContext context : contexts)
        {
            if (context == all || context == client)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isInjectableEverywhere(InjectionContext... contexts)
    {
        if (contexts.length == 0)
        {
            return true;
        }
        boolean isServer = false;
        boolean isClient = false;
        boolean isGwt = false;
        boolean isSwing = false;
        boolean isAndroid = false;
        boolean isIos = false;
        for (InjectionContext context : contexts)
        {
            if (context == all)
            {
                return true;
            }
            if (context == client)
            {
                isClient = true;
            }
            if (context == server)
            {
                isServer = true;
            }
            if (context == swing)
            {
                isSwing = true;
            }
            if (context == gwt)
            {
                isGwt = true;
            }
            if (context == android)
            {
                isAndroid = true;
            }
            if (context == ios)
            {
                isIos = true;
            }

        }
        if (isGwt && isSwing && isAndroid && isIos)
        {
            isClient = true;
        }
        if (isClient && isServer)
        {
            return true;
        }
        return false;
    }

    public boolean isSupported(InjectionContext... context)
    {
        switch (this)
        {
            case server:
                return isInjectableOnServer(context);
            case swing:
                return isInjectableOnSwing(context);
            case android:
                return isInjectableOnAndroid(context);
            case ios:
                return isInjectableOnAndroid(context);
            case gwt:
                return isInjectableOnGwt(context);
        }
        return false;
    }
}
