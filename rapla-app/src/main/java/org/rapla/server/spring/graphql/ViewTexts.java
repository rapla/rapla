package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import org.rapla.RaplaResources;
import org.rapla.SpaResources;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.server.internal.ServerLocaleResolver;
import org.rapla.storage.StorageOperator;
import org.rapla.server.spring.JwtUserResolver;
import org.rapla.server.spring.web.RequestLanguage;
import org.springframework.stereotype.Component;

/**
 * PRD 124 OQ3 — built-in view texts (title, rowLabel, column headers, search groups) are stored as
 * {@code i18n:<key>} and resolved here in the request language: RaplaResources first, then SpaResources;
 * an unknown key renders as the key. Text without the prefix (stored user views) passes through untouched.
 */
@Component
public class ViewTexts
{
    public static final String PREFIX = "i18n:";

    private final RaplaResources raplaResources;
    private final SpaResources spaResources;

    private final RequestLanguage requestLanguage;
    private final JwtUserResolver jwtUserResolver;
    private final StorageOperator operator;
    private final RaplaLocale raplaLocale;

    public ViewTexts(RaplaResources raplaResources, SpaResources spaResources,
            RequestLanguage requestLanguage, JwtUserResolver jwtUserResolver,
            StorageOperator operator, RaplaLocale raplaLocale)
    {
        this.operator = operator;
        this.raplaLocale = raplaLocale;
        this.raplaResources = raplaResources;
        this.spaResources = spaResources;
        this.requestLanguage = requestLanguage;
        this.jwtUserResolver = jwtUserResolver;
    }

    /** {@link #resolve} in the language of the request bound to the current thread (server language outside one). */
    public String resolveForRequest(String text)
    {
        if (text == null || !text.startsWith(PREFIX)) return text;
        Locale locale = null;
        try
        {
            locale = requestLanguage.resolveCurrent(jwtUserResolver.resolveCurrentUserOrNull());
        }
        catch (RaplaException e)
        {
            locale = null;
        }
        return resolve(text, locale != null ? locale : ServerLocaleResolver.resolve(operator, raplaLocale));
    }

    public String resolve(String text, Locale locale)
    {
        if (text == null || !text.startsWith(PREFIX)) return text;
        String key = text.substring(PREFIX.length());
        try
        {
            return raplaResources.getString(key, locale);
        }
        catch (MissingResourceException e)
        {
            try
            {
                return spaResources.getString(key, locale);
            }
            catch (MissingResourceException e2)
            {
                return key;
            }
        }
    }

    /** Resolves every prefixed string inside a nested map/list payload (the {@code extensions.view} meta). */
    public Object resolveDeep(Object value, Locale locale)
    {
        if (value instanceof String s) return resolve(s, locale);
        if (value instanceof Map<?, ?> m)
        {
            Map<Object, Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k, v) -> out.put(k, resolveDeep(v, locale)));
            return out;
        }
        if (value instanceof List<?> l)
        {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(resolveDeep(o, locale));
            return out;
        }
        return value;
    }
}
