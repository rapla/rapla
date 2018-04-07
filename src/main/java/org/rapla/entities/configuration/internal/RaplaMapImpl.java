/*--------------------------------------------------------------------------*
| C
o//pyright (C) 2014 Christopher Kohlhaas                                  |
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

import org.jetbrains.annotations.NotNull;
import org.rapla.components.util.iterator.FilterIterable;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.components.util.iterator.NestedIterable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.rest.GenericObjectSerializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Maps can only support one type value at a time. Especially  a mixture out of references and other values is not supported*/
public class RaplaMapImpl implements EntityReferencer, DynamicTypeDependant, RaplaObject, RaplaMap, GenericObjectSerializable
{
    //this map stores all objects in the map
    private Map<String, String> constants;
    private Map<String, RaplaConfiguration> configurations;
    private Map<String, RaplaMapImpl> maps;
    private Map<String, CalendarModelConfigurationImpl> calendars;
    protected LinkReferenceHandler links;
    transient protected Map<String, Object> map;
    transient EntityResolver resolver;

    static private Class<?extends Entity>[] SUPPORTED_TYPES = new Class[] { Allocatable.class, Category.class, DynamicType.class };
    // this map only stores the references

    // this map only stores the child objects (not the references)

    public RaplaMapImpl()
    {
    }

    @Override
    public Map<String,Object> toMap() {
        return new Map() {
            @Override
            public int size() {
                return RaplaMapImpl.this.size();
            }

            @Override
            public boolean isEmpty() {
                return RaplaMapImpl.this.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return RaplaMapImpl.this.containsKey( key);
            }

            @Override
            public boolean containsValue(Object value) {
                return RaplaMapImpl.this.containsValue( value);
            }

            @Override
            public Object get(Object key) {
                return RaplaMapImpl.this.get( key);
            }

            @Override
            public Object put(Object key, Object value) {
                throw createReadOnlyException();
            }

            @Override
            public Object remove(Object key) {
                throw createReadOnlyException();
            }

            @Override
            public void putAll(@NotNull Map m) {
                throw createReadOnlyException();
            }

            @Override
            public void clear() {
                throw createReadOnlyException();
            }

            @NotNull
            @Override
            public Set keySet() {
                return RaplaMapImpl.this.keySet();
            }

            @NotNull
            @Override
            public Collection values() {
                return RaplaMapImpl.this.values();
            }

            @NotNull
            @Override
            public Set<Entry<String,Object>> entrySet() {
                return RaplaMapImpl.this.entrySet();
            }
        };
    }

    public <T> RaplaMapImpl(Collection<T> list)
    {
        this(makeMap(list));
    }

    @Override public Class<? extends RaplaObject> getTypeClass()
    {
        return RaplaMap.class;
    }

    private static <T> Map<String, T> makeMap(Collection<T> list)
    {
        Map<String, T> map = new TreeMap<>();
        int key = 0;
        for (Iterator<T> it = list.iterator(); it.hasNext(); )
        {
            T next = it.next();
            if (next == null)
            {
                System.err.println("Adding null value in list");
            }
            map.put(new String(String.valueOf(key++)), next);
        }
        return map;
    }

    public RaplaMapImpl(Map<String, ?> map)
    {
        for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); )
        {
            String key = it.next();
            Object o = map.get(key);
            putPrivate(key, o);
        }
    }

    /** This method is only used in storage operations, please dont use it from outside, as it skips type protection and resolving*/
    public void putPrivate(String key, Object value)
    {
        cachedEntries = null;
        if (value == null)
        {
            if (links != null)
            {
                links.removeWithKey(key);
            }
            if (maps != null)
            {
                maps.remove(key);
            }
            if (map != null)
            {
                map.remove(key);
            }
            if (configurations != null)
            {
                configurations.remove(key);
            }
            if (maps != null)
            {
                maps.remove(key);
            }
            if (calendars != null)
            {
                calendars.remove(key);
            }
            if (constants != null)
            {
                constants.remove(key);
            }
            return;
        }
        //	   if ( ! (value instanceof RaplaObject ) && !(value instanceof String) )
        //       {
        //       }
        if (value instanceof Entity)
        {
            Entity entity = (Entity) value;
            Class<? extends  Entity> raplaType = entity.getTypeClass();
            if (!isTypeSupportedAsLink(raplaType))
            {
                throw new IllegalArgumentException("RaplaType " + raplaType + " cannot be stored as link in map");
            }
            putIdPrivate(key,entity.getReference());
        }
        else if (value instanceof RaplaConfiguration)
        {
            if (configurations == null)
            {
                configurations = new LinkedHashMap<>();
            }
            configurations.put(key, (RaplaConfiguration) value);
            getMap().put(key, value);
        }
        else if (value instanceof RaplaMap)
        {
            if (maps == null)
            {
                maps = new LinkedHashMap<>();
            }
            maps.put(key, (RaplaMapImpl) value);
            getMap().put(key, value);
        }
        else if (value instanceof CalendarModelConfiguration)
        {
            if (calendars == null)
            {
                calendars = new LinkedHashMap<>();
            }
            calendars.put(key, (CalendarModelConfigurationImpl) value);
            getMap().put(key, value);
        }
        else if (value instanceof String)
        {
            if (constants == null)
            {
                constants = new LinkedHashMap<>();
            }
            constants.put(key, (String) value);
            getMap().put(key, value);
        }
        else
        {
            throw new IllegalArgumentException("Map type not supported only category, dynamictype, allocatable, raplamap, raplaconfiguration or String.");
        }
    }

    private Map<String, Object> getMap()
    {
        if (links != null)
        {
            Map<String, ?> linkMap = links.getLinkMap();
            @SuppressWarnings("unchecked") Map<String, Object> casted = (Map<String, Object>) linkMap;
            return casted;
        }
        if (maps == null && configurations == null && constants == null && calendars == null)
        {
            return Collections.emptyMap();
        }
        if (map == null)
        {
            map = new LinkedHashMap<>();
            fillMap(maps);
            fillMap(configurations);
            fillMap(constants);
            fillMap(calendars);
        }
        return map;
    }

    private void fillMap(Map<String, ?> map)
    {
        if (map == null)
        {
            return;
        }
        this.map.putAll(map);
    }

    public void putIdPrivate(String key, ReferenceInfo referenceInfo)
    {
        cachedEntries = null;
        if (links == null)
        {
            links = new LinkReferenceHandler();
            Class<? extends Entity> typeClass = referenceInfo.getType();
            String localname = RaplaType.getLocalName( typeClass);
            links.setLinkType(localname);
            if (resolver != null)
            {
                links.setResolver(resolver);
            }
        }
        links.putId(key, referenceInfo.getId());
        map = null;
    }

    @Override public Iterable<ReferenceInfo> getReferenceInfo()
    {
        NestedIterable<ReferenceInfo, EntityReferencer> refIt = new NestedIterable<ReferenceInfo, EntityReferencer>(getEntityReferencers())
        {
            public Iterable<ReferenceInfo> getNestedIterable(EntityReferencer obj)
            {
                Iterable<ReferenceInfo> referencedIds = obj.getReferenceInfo();
                return referencedIds;
            }
        };
        if (links == null)
        {
            return refIt;
        }
        Iterable<ReferenceInfo> referencedLinks = links.getReferenceInfo();
        return new IterableChain<>(refIt, referencedLinks);
    }

    private Iterable<EntityReferencer> getEntityReferencers()
    {
        return new FilterIterable<EntityReferencer>(getMap().values())
        {
            protected boolean isInIterator(Object obj)
            {
                return obj instanceof EntityReferencer;
            }
        };
    }

   /*
   public Iterator getReferences() {
       return getReferenceHandler().getReferences();
   }

   public boolean isRefering(Entity entity) {
       return getReferenceHandler().isRefering( entity);
   }*/

    public void setResolver(EntityResolver resolver)
    {
        this.resolver = resolver;
        if (links != null)
        {
            links.setResolver(resolver);
        }
        setResolver(calendars);
        setResolver(maps);
        map = null;
    }

    private void setResolver(Map<String, ? extends EntityReferencer> map)
    {
        if (map == null)
        {
            return;
        }
        for (EntityReferencer ref : map.values())
        {
            ref.setResolver(resolver);
        }
    }

    @Override
    public Object get(Object key)
    {
        if (links != null)
        {
            return links.getEntity((String) key);
        }
        return getMap().get(key);
    }

    /**
     * @see java.util.Map#clear()
     */
    public void clear()
    {
        throw createReadOnlyException();
    }

    protected ReadOnlyException createReadOnlyException()
    {
        return new ReadOnlyException("RaplaMap is readonly you must createInfoDialog a new Object");
    }

    /**
     * @see java.util.Map#size()
     */
    @Override
    public int size()
    {
        return getMap().size();
    }

    /**
     * @see java.util.Map#isEmpty()
     */
    public boolean isEmpty()
    {
        return getMap().isEmpty();
    }

    /**
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key)
    {
        return getMap().containsKey(key);
    }

    /**
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object key)
    {
        return getMap().containsValue(key);
    }

    /**
     * @see java.util.Map#keySet()
     */
    @Override
    public Set<String> keySet()
    {
        return getMap().keySet();
    }

    public boolean needsChange(DynamicType type)
    {
        for (Iterator it = getMap().values().iterator(); it.hasNext(); )
        {
            Object obj = it.next();
            if (obj instanceof DynamicTypeDependant)
            {
                if (((DynamicTypeDependant) obj).needsChange(type))
                    return true;
            }
        }
        return false;
    }

    public void commitChange(DynamicType type)
    {
        for (Object obj : getMap().values())
        {
            if (obj instanceof DynamicTypeDependant)
            {
                ((DynamicTypeDependant) obj).commitChange(type);
            }
        }
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException
    {
        for (Object obj : getMap().values())
        {
            if (obj instanceof DynamicTypeDependant)
            {
                ((DynamicTypeDependant) obj).commitRemove(type);
            }
        }
    }

    /** Clones the entity and all subentities*/
    public RaplaMapImpl deepClone()
    {
        RaplaMapImpl clone = new RaplaMapImpl(getMap());
        clone.setResolver(resolver);
        return clone;
    }

    //	public Collection<RaplaObject> getLinkValues()
    //	{
    //		ArrayList<RaplaObject> result = new ArrayList<RaplaObject>();
    //		EntityResolver resolver = getReferenceHandler().getResolver();
    //		for (String id: getReferencedIds())
    //		{
    //			result.add( resolver.tryResolve( id));
    //		}
    //		return result;
    //
    //	}

    /** Clones the entity while preserving the references to the subentities*/
    public Object clone()
    {
        return deepClone();
    }

    /**
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public Object put(Object key, Object value)
    {
        throw createReadOnlyException();
    }

    public Object remove(Object arg0)
    {
        throw createReadOnlyException();
    }

    /**
     * @see java.util.Map#putAll(java.util.Map)
     */
    public void putAll(Map m)
    {
        throw createReadOnlyException();
    }

    /**
     * @see java.util.Map#values()
     */
    @Override
    public Collection values()
    {
        if (links == null)
        {
            return getMap().values();
        }
        else
        {
            List<Entity> result = new ArrayList<>();
            for (Map.Entry<String, Object> entry : entrySet())
            {
                result.add((Entity) entry.getValue());
            }
            //    		Iterable<String> values = links.getReferencedIds();
            //    		for (String id: values)
            //    		{
            //				EntityResolver resovler = links.getResolver();
            //				if (resovler == null)
            //				{
            //				    throw new IllegalStateException("Resolver not set in map. ");
            //				}
            //                Entity resolved = resovler.tryResolve( id);
            //				result.add( resolved);
            //    		}
            return result;
        }
    }

    public static final class LinkReferenceHandler extends ReferenceHandler
    {
        protected String linkType;
        transient private Class<? extends Entity> linkClass;

        protected Class<? extends Entity> getInfoClass(String key)
        {
            return getLinkClass();
        }

        public Entity getEntity(String key)
        {
            Class<? extends Entity> linkClass = getLinkClass();
            return getEntity(key, linkClass);
        }

        private Class<? extends Entity> getLinkClass()
        {
            if (linkClass != null)
            {
                return linkClass;
            }
            if (linkType != null)
            {
                for (Class<?extends Entity> type : SUPPORTED_TYPES)
                {
                    String localname = RaplaType.getLocalName( type);
                    if (linkType.equals(localname))
                    {
                        this.linkClass = type;
                        return linkClass;
                    }
                }
                throw new IllegalArgumentException("Unsupported Linktype in map " + linkType);
            }
            else {
                throw new IllegalArgumentException("Linktype in map not set "  + toString());
            }

        }

        public void setLinkType(String type)
        {
            if ( linkType != null && !linkType.equals(type))
            {
                throw new IllegalStateException("Can't put " + type + " in a rapla map containing " + linkType);
            }
            this.linkType = type;
            linkClass = null;
        }

    }

    class Entry implements Map.Entry<String, Object>
    {
        String key;
        String id;

        Entry(String key, String id)
        {
            this.key = key;
            this.id = id;
            if (id == null)
            {
                throw new IllegalArgumentException("Empty id added");
            }
        }

        public String getKey()
        {
            return key;
        }

        public Object getValue()
        {
            if (id == null)
            {
                return null;
            }
            EntityResolver resolver = links.getResolver();
            if (resolver == null)
            {
                throw new IllegalStateException("Resolver not set in links map. ");
            }
            final Class<? extends Entity> linkClass = links.getLinkClass();
            Entity resolve = resolver.tryResolve(id, linkClass);
            return resolve;
        }

        public Object setValue(Object value)
        {
            throw new UnsupportedOperationException();
        }

        public int hashCode()
        {
            return key.hashCode();
        }

        @Override public boolean equals(Object obj)
        {
            return key.equals(((Entry) obj).key);
        }

        public String toString()
        {
            Entity value = links.getResolver().tryResolve(id, links.getLinkClass());
            return key + "=" + ((value != null) ? value : "unresolvable_" + id);
        }
    }

    transient Set<Map.Entry<String, Object>> cachedEntries;

    public Set<Map.Entry<String, Object>> entrySet()
    {
        if (links != null)
        {
            if (cachedEntries == null)
            {
                cachedEntries = new HashSet<>();
                for (String key : links.getReferenceKeys())
                {
                    String id = links.getId(key);
                    if (id == null)
                    {
                        System.err.println("Empty id " + id);
                    }
                    cachedEntries.add(new Entry(key, id));
                }
            }
            return cachedEntries;
        }
        else
        {
            if (cachedEntries == null)
            {
                cachedEntries = new HashSet<>();
                for (Map.Entry<String, Object> entry : getMap().entrySet())
                {
                    if (entry.getValue() == null)
                    {
                        System.err.println("Empty value for  " + entry.getKey());
                    }
                    cachedEntries.add(entry);
                }
            }
            return cachedEntries;
        }
    }

    public String toString()
    {
        return entrySet().toString();
    }

    public boolean isTypeSupportedAsLink(Class<?extends Entity> raplaType)
    {
        for (Class<? extends Entity> type : SUPPORTED_TYPES)
        {
            if (type == raplaType)
            {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public void replace(ReferenceInfo origId, ReferenceInfo newId)
    {
        final Iterable<EntityReferencer> entityReferencers = getEntityReferencers();
        final Iterator<EntityReferencer> iterator = entityReferencers.iterator();
        while(iterator.hasNext())
        {
            final EntityReferencer next = iterator.next();
            next.replace(origId, newId);
        }
    }

    @Override public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RaplaMapImpl raplaMap = (RaplaMapImpl) o;

        if (constants != null ? !constants.equals(raplaMap.constants) : raplaMap.constants != null)
            return false;
        if (configurations != null ? !configurations.equals(raplaMap.configurations) : raplaMap.configurations != null)
            return false;
        if (maps != null ? !maps.equals(raplaMap.maps) : raplaMap.maps != null)
            return false;
        if (calendars != null ? !calendars.equals(raplaMap.calendars) : raplaMap.calendars != null)
            return false;
        return links != null ? links.equals(raplaMap.links) : raplaMap.links == null;

    }

    @Override public int hashCode()
    {
        int result = constants != null ? constants.hashCode() : 0;
        result = 31 * result + (configurations != null ? configurations.hashCode() : 0);
        result = 31 * result + (maps != null ? maps.hashCode() : 0);
        result = 31 * result + (calendars != null ? calendars.hashCode() : 0);
        result = 31 * result + (links != null ? links.hashCode() : 0);
        return result;
    }
}
