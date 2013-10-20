/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.ReferenceHandler;

public class PermissionImpl
    implements
        Permission
        ,EntityReferencer
        ,java.io.Serializable
{
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    boolean readOnly = false;
    ReferenceHandler referenceHandler = new ReferenceHandler();
    Date pEnd = null;
    Date pStart = null;
    Integer maxAdvance = null;
    Integer minAdvance = null;
    int accessLevel = ALLOCATE_CONFLICTS;

    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        referenceHandler.resolveEntities( resolver );
    }

    public void setUser(User user) {
        checkWritable();
        if (user != null)
            referenceHandler.put("group",null);
        referenceHandler.put("user",(RefEntity<?>)user);
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

    public void setReadOnly(boolean enable) {
        this.readOnly = enable;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }

    public boolean affectsUser(User user) {
        int userEffect = getUserEffect( user );
		return userEffect> NO_PERMISSION;
    }
    
    
    public int getUserEffect(User user) {
        User pUser = getUser();
        Category pGroup = getGroup();
        if ( pUser == null  && pGroup == null ) 
        {
            return ALL_USER_PERMISSION;
        }
        if ( pUser != null  && user.equals( pUser ) ) 
        {
            return USER_PERMISSION;
        } 
        else if ( pGroup != null ) 
        {
        	if ( user.belongsTo( pGroup))
        	{
                return GROUP_PERMISSION;	
        	}
        }
        return NO_PERMISSION;
    }

    public void setAccessLevel(int accessLevel) 
    {
    	if  (accessLevel <5)
    	{
    		accessLevel*= 100;
    	}
    	this.accessLevel = accessLevel;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public User getUser() {
        return (User) referenceHandler.get("user");
    }

    public void setGroup(Category group) {
        if (group != null)
            referenceHandler.put("user",null);
        referenceHandler.put("group",(RefEntity<?>)group);
    }

    public Period getPeriod() {
        return (Period) referenceHandler.get("period");
    }

    public void setPeriod(Period period) {
        referenceHandler.put("period",(RefEntity<?>)period);
    }

    public ReferenceHandler getReferenceHandler() {
        return referenceHandler;
    }

    public Iterator<RefEntity<?>> getReferences() {
        return referenceHandler.getReferences();
    }

    public boolean isRefering( RefEntity<?> object ) {
        return referenceHandler.isRefering( object );
    }

    public Category getGroup() {
        return (Category) referenceHandler.get("group");
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
    public boolean valid( Date today ) {
    	if ( pEnd != null && ( today == null || pEnd.getTime() + DateTools.MILLISECONDS_PER_DAY<=today.getTime() ) ) {
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

            if ( start == null || start.getTime() < pStartTime ) {
                //System.out.println( " start before permission " + start  + " < " + pStartTime );
                return false;
            }
        }
        if ( maxAdvance != null ) {
            long pEndTime = today.getTime()
                + DateTools.MILLISECONDS_PER_DAY * (maxAdvance.longValue() + 1);
            if ( end == null || pEndTime < end.getTime() ) {
                //System.out.println( " end after permission " + end  + " > " + pEndTime );
                return false;
            }
        }
        return true;
    }
    
   

    public PermissionImpl clone() {
        PermissionImpl clone = new PermissionImpl();
        // This must be done first
        clone.referenceHandler = (ReferenceHandler) referenceHandler.clone();
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

 		Permission perm = (Permission) o;
 		if (equalValues(this.getUser(), perm.getUser())
 				&& equalValues(this.getGroup(), perm.getGroup())
 				&& equalValues(this.getStart(), perm.getStart())
 				&& equalValues(this.getEnd(), perm.getEnd())
 				&& equalValues(this.getAccessLevel(), perm.getAccessLevel()))
 			return true;
 		else
 			return false;
 	}
 	
 	public int hashCode() {
 		StringBuilder buf = new StringBuilder();
 		append( buf,getUser());
		append( buf,getGroup());
		append( buf,getStart());
		append( buf,getEnd());
		append( buf,getAccessLevel());
 		return buf.toString().hashCode();
 	}

 	private void append(StringBuilder buf, Object obj) {
 		if ( obj != null)
 		{
 			buf.append( obj.hashCode());
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
