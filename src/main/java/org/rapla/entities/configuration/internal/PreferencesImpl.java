/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities.configuration.internal;

import org.rapla.components.util.iterator.IterableChain;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.PreferencePatch;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class PreferencesImpl extends SimpleEntity implements Preferences, ModifiableTimestamp, DynamicTypeDependant
{
    private Date lastChanged;
    private Date createDate;

    RaplaMapImpl map = new RaplaMapImpl();

    @Override public Class<Preferences> getTypeClass()
    {
        return Preferences.class;
    }

    private transient PreferencePatch patch = new PreferencePatch();

    PreferencesImpl()
    {
        this(null, null);
    }

    public PreferencesImpl(Date createDate, Date lastChanged)
    {
        super();
        this.createDate = createDate;
        this.lastChanged = lastChanged;
    }

    public Date getLastChanged()
    {
        return lastChanged;
    }

    @Deprecated public Date getLastChangeTime()
    {
        return lastChanged;
    }

    public Date getCreateDate()
    {
        return createDate;
    }

    @Override public void setCreateDate(Date date)
    {
        checkWritable();
        this.createDate = date;
    }

    public void setLastChanged(Date date)
    {
        checkWritable();
        if( createDate == null)
        {
            createDate = date;
        }
        lastChanged = date;
    }

    @Override public void putEntry(TypedComponentRole<CalendarModelConfiguration> role, CalendarModelConfiguration entry)
    {
        putEntryPrivate(role.getId(), entry);
    }

    @Override public void putEntry(TypedComponentRole<RaplaConfiguration> role, RaplaConfiguration entry)
    {
        putEntryPrivate(role.getId(), entry);
    }

    @Override public <T> void putEntry(TypedComponentRole<RaplaMap<T>> role, RaplaMap<T> entry)
    {
        putEntryPrivate(role.getId(), entry);
    }

    public void putEntryPrivate(String role, RaplaObject entry)
    {
        updateMap(role, entry);
    }

    private void updateMap(String role, Object entry )
    {
        checkWritable();
        map.putPrivate(role, entry);
        patch.putPrivate(role, entry);
        if (entry == null)
        {
            patch.addRemove(role);
        }
    }

    public void putEntryPrivate(String role, String entry)
    {
        updateMap(role, entry);
    }

    public void setResolver(EntityResolver resolver)
    {
        super.setResolver(resolver);
        map.setResolver(resolver);
        patch.setResolver(resolver);
    }

    public <T> T getEntry(String role)
    {
        return getEntry(role, null);
    }

    public <T> T getEntry(String role, T defaultValue)
    {
        try
        {
            @SuppressWarnings("unchecked") T result = (T) map.get(role);
            if (result == null)
            {
                return defaultValue;
            }
            return result;
        }
        catch (ClassCastException ex)
        {
            throw new ClassCastException("Stored entry is not of requested Type: " + ex.getMessage());
        }
    }

    private String getEntryAsString(String role)
    {
        return (String) map.get(role);
    }

    public String getEntryAsString(TypedComponentRole<String> role, String defaultValue)
    {
        String value = getEntryAsString(role.getId());
        if (value != null)
            return value;
        return defaultValue;
    }

    public Iterable<String> getPreferenceEntries()
    {
        return map.keySet();
    }

    public void removeEntry(String role)
    {
        updateMap(role, null);
    }

    @Override public Iterable<ReferenceInfo> getReferenceInfo()
    {
        Iterable<ReferenceInfo> parentReferences = super.getReferenceInfo();
        Iterable<ReferenceInfo> mapReferences = map.getReferenceInfo();
        IterableChain<ReferenceInfo> iteratorChain = new IterableChain<>(parentReferences, mapReferences);
        return iteratorChain;
    }

    public boolean isEmpty()
    {
        return map.isEmpty();
    }

    public PreferencesImpl clone()
    {
        PreferencesImpl clone = new PreferencesImpl();
        super.deepClone(clone);
        clone.map = map.deepClone();
        clone.createDate = createDate;
        clone.lastChanged = lastChanged;
        // we clear the patch on a clone
        clone.patch = new PreferencePatch();
        clone.patch.setUserId(getOwnerId());
        return clone;
    }

    @Override public void setOwner(User owner)
    {
        super.setOwner(owner);
        patch.setUserId(getOwnerId());
    }

    public PreferencePatch getPatch()
    {
        return patch;
    }

    private User getOwner()
    {
        return getEntity("owner", User.class);
    }

    /**
     * @see org.rapla.entities.Named#getName(java.util.Locale)
     */
    public String getName(Locale locale)
    {
        StringBuffer buf = new StringBuffer();
        if (getOwner() != null)
        {
            buf.append("Preferences of ");
            buf.append(getOwner().getName(locale));
        }
        else
        {
            buf.append("Rapla Preferences!");
        }
        return buf.toString();
    }

    /* (non-Javadoc)
     * @see org.rapla.entities.configuration.Preferences#getEntryAsBoolean(java.lang.String, boolean)
     */
    public Boolean getEntryAsBoolean(TypedComponentRole<Boolean> role, boolean defaultValue)
    {
        String entry = getEntryAsString(role.getId());
        if (entry == null)
            return defaultValue;
        return Boolean.valueOf(entry).booleanValue();
    }

    /* (non-Javadoc)
     * @see org.rapla.entities.configuration.Preferences#getEntryAsInteger(java.lang.String, int)
     */
    public Integer getEntryAsInteger(TypedComponentRole<Integer> role, int defaultValue)
    {
        String entry = getEntryAsString(role.getId());
        if (entry == null)
            return defaultValue;
        return Integer.parseInt(entry);
    }

    public boolean needsChange(DynamicType type)
    {
        return map.needsChange(type);
    }

    public void commitChange(DynamicType type)
    {
        map.commitChange(type);
        patch.commitChange(type);
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException
    {
        map.commitRemove(type);
        patch.commitRemove(type);
    }

    public String toString()
    {
        return super.toString() + " " + map.toString();
    }

    public <T extends RaplaObject> void putEntry(TypedComponentRole<T> role, T entry)
    {
        putEntryPrivate(role.getId(), entry);
    }

    public void applyPatch(PreferencePatch patch)
    {
        checkWritable();
        Set<String> removedEntries = patch.getRemovedEntries();
        for (String key : patch.keySet())
        {
            Object value = patch.get(key);
            map.putPrivate(key, value);
        }

        for (String remove : removedEntries)
        {
            map.putPrivate( remove, null );
        }
        Date lastChangedPatch = patch.getLastChanged();
        if (lastChangedPatch != null)
        {
            Date lastChanged = getLastChanged();
            if (lastChanged == null || lastChanged.before(lastChangedPatch))
            {
                setLastChanged(lastChangedPatch);
            }

        }
    }

    public <T extends RaplaObject> T getEntry(TypedComponentRole<T> role)
    {
        return getEntry(role, null);
    }

    public <T extends RaplaObject> T getEntry(TypedComponentRole<T> role, T defaultValue)
    {
        return getEntry(role.getId(), defaultValue);
    }

    public boolean hasEntry(TypedComponentRole<?> role)
    {
        return map.get(role.getId()) != null;
    }

    public void putEntry(TypedComponentRole<Boolean> role, Boolean entry)
    {
        putEntry_(role, entry != null ? entry.toString() : null);
    }

    public void putEntry(TypedComponentRole<Integer> role, Integer entry)
    {
        putEntry_(role, entry != null ? entry.toString() : null);
    }

    public void putEntry(TypedComponentRole<String> role, String entry)
    {
        putEntry_(role, entry);
    }

    protected void putEntry_(TypedComponentRole<?> role, Object entry)
    {
        checkWritable();
        String key = role.getId();
        updateMap(key, entry);
    }

    public static ReferenceInfo<Preferences> getPreferenceIdFromUser(String userId)
    {
        if (userId != null)
        {
            return new ReferenceInfo<>(Preferences.ID_PREFIX + userId, Preferences.class);
        }
        else
        {
            return SYSTEM_PREFERENCES_ID;
        }
    }

    @Deprecated public Configuration getOldPluginConfig(String pluginClassName)
    {
        RaplaConfiguration raplaConfig = getEntry(RaplaComponent.PLUGIN_CONFIG);
        Configuration pluginConfig = null;
        if (raplaConfig != null)
        {
            pluginConfig = raplaConfig.find("class", pluginClassName);
        }
        if (pluginConfig == null)
        {
            pluginConfig = new RaplaConfiguration("plugin");
        }
        return pluginConfig;
    }
    
    @Override
    public void replace(ReferenceInfo origId, ReferenceInfo newId)
    {
        super.replace(origId, newId);
        map.replace(origId, newId);
    }

}












