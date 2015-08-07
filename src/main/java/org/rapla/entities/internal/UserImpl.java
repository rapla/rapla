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
package org.rapla.entities.internal;

import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.RaplaException;

public class UserImpl extends SimpleEntity implements User, ModifiableTimestamp
{
    private String username = "";
    private String email = "";
    private String name = "";
    private boolean admin = false;
    
    private Date lastChanged;
    private Date createDate;

    final public RaplaType<User> getRaplaType() {return TYPE;}

    UserImpl() {
    	this(null,null);
	}
    
    public UserImpl(Date createDate,Date lastChanged)
    {
        this.createDate = createDate;
        this.lastChanged = lastChanged;
    }
    
    public Date getLastChanged() {
        return lastChanged;
    }
    
    @Deprecated
    public Date getLastChangeTime() {
        return lastChanged;
    }

    public Date getCreateTime() {
        return createDate;
    }

    public void setLastChanged(Date date) {
        checkWritable();
    	lastChanged = date;
    }
    
    public boolean isAdmin() {return admin;}
    public String getName() 
    {
        final Allocatable person = getPerson();
        if ( person != null)
        {
            return person.getName( null );
        }
        return name;
    }
    
    public String getEmail() {
        final Allocatable person = getPerson();
        if ( person != null)
        {
            final Classification classification = person.getClassification();
            final Attribute attribute = classification.getAttribute("email");
            return attribute != null ? (String)classification.getValue(attribute) : null;
        }
        return email;
    }

    public String getUsername()  { 
    	return username; 
    }
    
    public String toString()  
    { 
        return getUsername();
    }

    public void setName(String name)  {
        checkWritable();
        this.name =  name;
    }

    public void setEmail(String email)  {
        checkWritable();
        this.email =  email;
    }
   
    public void setUsername(String username)  {
        checkWritable();
        this.username =  username;
    }

    public void setAdmin(boolean bAdmin)  {
        checkWritable();
        this.admin=bAdmin;
    }

    public String getName(Locale locale) 
    {
        final Allocatable person = getPerson();
        if ( person != null)
        {
            return person.getName(locale);
        }

        final String name = getName();
        if ( name == null || name.length() == 0)
        {
        	return getUsername();
        }
        else
        {
        	return name;
        }
    }

    public void addGroup(Category group) {
        checkWritable();
        if ( isRefering("groups", group.getId()))
        {
            return;
        }
        add("groups",group);
    }

    public boolean removeGroup(Category group)   {
        checkWritable();
        return removeId(group.getId());
    }

    public Category[] getGroups()  {
        Collection<Category> groupList = getGroupList();
        Category[] groups = groupList.toArray(Category.CATEGORY_ARRAY);
        return groups;
    }
    
    public Collection<Category> getGroupList()
    {
        Collection<Category> groupList = getList("groups", Category.class);
        return groupList;
    }
    
//    /** returns if the user or a group of the user is affected by the permission.
//     * Groups are hierarchical. If the user belongs
//     * to a subgroup of the permission-group the user is also
//     * affected by the permission.
//     * returns true if the result of getUserEffect is greater than NO_PERMISSION
//     */
//    public boolean affectsUser( Permission p)
//    {
//        boolean result = getUserEffect(p)>=PermissionImpl.NO_PERMISSION;
//        return result;
//    }
    
    public boolean belongsTo( Category group ) 
    {
    	for (Category uGroup:getGroupList())
    	{
    		if (group.equals( uGroup) || group.isAncestorOf( uGroup))
    		{
    			return true;
    		}
    	}
        return false;
    }

    public User clone() {
        UserImpl clone = new UserImpl();
        super.deepClone(clone);
        clone.username = username;
        clone.name = name;
        clone.email = email;
        clone.admin = admin;
        clone.lastChanged = lastChanged;
        clone.createDate = createDate;
        return clone;
    }

    public int compareTo(User o) {
        int result = toString().compareTo( o.toString());
        if (result != 0)
        {
            return result;
        }
        else
        {
            return super.compareTo(  o);
        }
    }

    public void setPerson(Allocatable person) throws RaplaException
    {
    	checkWritable();
    	if ( person == null)
        {
    		putEntity("person", null);
            return;
        }
        final Classification classification = person.getClassification();
        final Attribute attribute = classification.getAttribute("email");
        final String email = attribute != null ? (String)classification.getValue(attribute) : null;
        if ( email == null || email.length() == 0)
        {
        	throw new RaplaException("Email of " + person + " not set. Linking to user needs an email ");
        }
        else
        {
            this.email = email;
            putEntity("person", (Entity) person);
            setName(person.getClassification().getName(null));
        }
    }

    public Allocatable getPerson() 
    {
        final Allocatable person = getEntity("person", Allocatable.class);
        return person;
    }

    

    

}
