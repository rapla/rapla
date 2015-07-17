/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, of which license fullfill the Open Source     |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.entities.domain.internal;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.internal.ReferenceHandler;

public final class PermissionImpl extends ReferenceHandler implements Permission,EntityReferencer
{
    transient boolean readOnly = false;
    Date pEnd = null;
    Date pStart = null;
    Integer maxAdvance = null;
    Integer minAdvance = null;
    AccessLevel accessLevel = AccessLevel.ALLOCATE_CONFLICTS;
    public static final int NO_PERMISSION = -2;
    public static final int ALL_USER_PERMISSION = -1;
    public static final int USER_PERMISSION = 10000;
    public static final int GROUP_PERMISSION = 5000;

    public void setUser(User user) {
        checkWritable();
        if (user != null)
        	putEntity("group",null);
        putEntity("user",(Entity)user);
    }

    public void setEnd(Date end) {
        checkWritable();
        this.pEnd = end;
        if ( end != null )
            this.maxAdvance = null;
    }

    public Date getEnd() {
        return pEnd;
    }
    
    @Override
    protected Class<? extends Entity> getInfoClass(String key) 
    {
        if ( key.equals("group"))
        {
            return Category.class;
        }
        if ( key.equals("user"))
        {
            return User.class;
        }
        return null;
    }

    public void setStart(Date start) {
        checkWritable();
        this.pStart = start;
        if ( start != null )
            this.minAdvance = null;
    }

    public Date getStart() {
        return pStart;
    }

    public void setMinAdvance(Integer minAdvance) {
        checkWritable();
        this.minAdvance = minAdvance;
        if ( minAdvance != null )
            this.pStart = null;
    }

    public Integer getMinAdvance() {
        return minAdvance;
    }

    public void setMaxAdvance(Integer maxAdvance) {
        checkWritable();
        this.maxAdvance = maxAdvance;
        if ( maxAdvance != null )
            this.pEnd = null;
    }

    public Integer getMaxAdvance() {
        return maxAdvance;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }

    public void setAccessLevel(AccessLevel accessLevel) 
    {
    	this.accessLevel = accessLevel;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public User getUser() {
        return getEntity("user", User.class);
    }

    public void setGroup(Category group) {
        if (group != null)
            putEntity("user",null);
        putEntity("group",(Entity)group);
    }

    public ReferenceHandler getReferenceHandler() {
        return this;
    }

    public Category getGroup() {
        return getEntity("group", Category.class);
    }

    public Date getMinAllowed(Date today) {
        if ( pStart != null )
            return pStart;
        if ( minAdvance != null)
            return new Date( today.getTime()
                             + DateTools.MILLISECONDS_PER_DAY * minAdvance.longValue() );
        return null;
    }

    public Date getMaxAllowed(Date today) {
        if ( pEnd != null )
            return pEnd;
        if ( maxAdvance != null)
            return new Date( today.getTime()
                             + DateTools.MILLISECONDS_PER_DAY * (maxAdvance.longValue() + 1) );
        return null;
    }
    
    public boolean hasTimeLimits()
    {
    	return pStart != null || pEnd != null || minAdvance != null || maxAdvance != null;
    }
    
    /** only checks if the user is allowed to make a reservation in the future */
    public boolean validInTheFuture( Date today ) {
        if ( pEnd != null && ( today == null || pEnd.getTime() + DateTools.MILLISECONDS_PER_DAY<=( today.getTime() ) ) ) {
            return false;
        }
        if ( maxAdvance != null && today != null) {
            long pEndTime = today.getTime()
                + DateTools.MILLISECONDS_PER_DAY * (maxAdvance.longValue() + 1);
            if (  pEndTime < today.getTime() ) {
                //System.out.println( " end after permission " + end  + " > " + pEndTime );
                return false;
            }
        }
        return true;
    }
    
    public boolean covers( Date start, Date end, Date today ) {
        if ( pStart != null && (start == null || start.before ( pStart ) ) ) {
            //System.out.println( " start before permission ");
            return false;
        }
        if ( pEnd != null && ( end == null || pEnd.getTime() + DateTools.MILLISECONDS_PER_DAY<=end.getTime() ) ) {
            //System.out.println( " end before permission ");
            return false;
        }
        if ( minAdvance != null ) {
            long pStartTime = today.getTime()
                + DateTools.MILLISECONDS_PER_DAY * minAdvance.longValue();

            if ( start == null || start.getTime() > pStartTime ) {
                //System.out.println( " start before permission " + start  + " < " + pStartTime );
                return false;
            }
        }
        if ( maxAdvance != null ) {
            long pEndTime = today.getTime()
                + DateTools.MILLISECONDS_PER_DAY * (maxAdvance.longValue() + 1);
            if ( end == null || end.getTime() > pEndTime   ) {
                //System.out.println( " end after permission " + end  + " > " + pEndTime );
                return false;
            }
        }
        return true;
    }
    
   

    public PermissionImpl clone() {
        PermissionImpl clone = new PermissionImpl();
    	clone.links =  new LinkedHashMap<String,List<String>>();
    	for ( String key:links.keySet())
    	{
    		List<String> idList = links.get( key);
    		clone.links.put( key, new ArrayList<String>(idList));
    	}
    	clone.resolver = this.resolver;
        // This must be done first
        clone.accessLevel = accessLevel;
        clone.pEnd = pEnd;
        clone.pStart = pStart;
        clone.minAdvance = minAdvance;
        clone.maxAdvance = maxAdvance;
        return clone;
    }
    
 // compares two Permissions on basis of their attributes: user, group,
 	// start, end, access
 	// therefore, method uses the method equalValues() for comparing two
 	// attributes
 	public boolean equals(Object o) {
 		if (o == null)
 			return false;
 		if ( o == this)
 		{
 		    return true;
 		}
 		PermissionImpl perm = (PermissionImpl) o;
 		if (equalValues(this.getReferenceHandler().getId("user"), perm.getReferenceHandler().getId("user"))
 				&& equalValues(this.getReferenceHandler().getId("group"), perm.getReferenceHandler().getId("group"))
 				&& equalValues(this.getStart(), perm.getStart())
 				&& equalValues(this.getEnd(), perm.getEnd())
 				&& equalValues(this.getMaxAdvance(), perm.getMaxAdvance())
 				&& equalValues(this.getMinAdvance(), perm.getMinAdvance())
 				&& equalValues(this.getAccessLevel(), perm.getAccessLevel()))
 			return true;
 		else
 			return false;
 	}
 	
 	public int hashCode() {
 		StringBuilder buf = new StringBuilder();
 		append( buf,getReferenceHandler().getId("user"));
		append( buf,getReferenceHandler().getId("group"));
		append( buf,getStart());
		append( buf,getEnd());
		append( buf,getMaxAdvance());
		append( buf,getMinAdvance());
        append( buf,getAccessLevel());
 		return buf.toString().hashCode();
 	}

 	private void append(StringBuilder buf, Object obj) {
 		if ( obj != null)
 		{
 			buf.append( obj.toString());
 		}
	}

	// compares two values/attributes
 	// equity is given when both are null or equal
 	private boolean equalValues(Object obj1, Object obj2) {
 		return (obj1 == null && obj2 == null) || (obj1 != null	&& obj1.equals(obj2));
 	}
    
    public String toString()
    {
    	StringBuffer buf = new StringBuffer();
    	if ( getUser() != null)
    	{
    		buf.append("User=" + getUser().getUsername());
    	}
    	if ( getGroup() != null)
    	{
    		buf.append("Group=" + getGroup().getName(Locale.ENGLISH));
    	}
    	
    	buf.append ( " AccessLevel=" + getAccessLevel());
    	if ( getStart() != null)
    	{
    		buf.append ( " Beginning=" + getStart());
        		
    	}
    	if ( getEnd() != null)
    	{
    		buf.append ( " Ending=" + getEnd());
        		
    	}
    	if (getMinAdvance() != null)
    	{
    		buf.append ( " MinAdvance=" + getMinAdvance());
        	
    	}
    	if (getMaxAdvance() != null)
    	{
    		buf.append ( " MaxAdvance=" + getMaxAdvance());
        	
    	}
    	return buf.toString();
    }



}
