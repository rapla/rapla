/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleEntity;

public class UserImpl extends SimpleEntity implements User
{
    private String username = "";
    private String email = "";
    private String name = "";
    private boolean admin = false;

    transient private boolean groupArrayUpToDate = false;
    // The resolved references
    transient private Category[] groups;

    final public RaplaType<User> getRaplaType() {return TYPE;}

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

    public void setResolver( EntityResolver resolver)  {
        super.setResolver(resolver);
        if ( email != null && email.trim().length() > 0)
        {
            try
            {
                final Entity person = resolver.resolveEmail(email);
                if ( person instanceof Allocatable)
                {
                    setPerson((Allocatable)person);
                }
            }
            catch (EntityNotFoundException ex)
            {
            //    System.out.println("Hallo");
            }
        }
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
        if (getReferenceHandler().isRefering(group.getId()))
            return;
        groupArrayUpToDate = false;
        getReferenceHandler().add("groups",group);
    }

    public boolean removeGroup(Category group)   {
        checkWritable();
        return getReferenceHandler().remove(group.getId());
    }

    public Category[] getGroups()  {
        updateGroupArray();
        return groups;
    }

    public boolean belongsTo( Category group ) 
    {
    	for (Category uGroup:getGroups())
    	{
    		if (group.equals( uGroup) || group.isAncestorOf( uGroup))
    		{
    			return true;
    		}
    	}
        return false;
    }

    private void updateGroupArray() {
        if (groupArrayUpToDate)
            return;
        synchronized ( this )
        {
        	Collection<Category> groupList = new ArrayList<Category>();
        	for(Entity o:getReferenceHandler().getList("groups"))
       	 	{
        		groupList.add((Category)o);
        	}
        	groups = groupList.toArray(Category.CATEGORY_ARRAY);
        	groupArrayUpToDate = true;
    	}
    }

    public User clone() {
        UserImpl clone = new UserImpl();
        super.deepClone(clone);
        clone.groupArrayUpToDate = false;
        clone.username = username;
        clone.name = name;
        clone.email = email;
        clone.admin = admin;
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

    public void setPerson(Allocatable person)
    {
    	checkWritable();
    	final ReferenceHandler referenceHandler = getReferenceHandler();
        if ( person == null)
        {
            referenceHandler.putEntity("person", null);
            return;
        }
        final Classification classification = person.getClassification();
        final Attribute attribute = classification.getAttribute("email");
        final String email = attribute != null ? (String)classification.getValue(attribute) : null;
        if ( email != null)
        {
            this.email = email;
            referenceHandler.putEntity("person", (Entity) person);
            setName(person.getClassification().getName(null));
        }
    }
    
    public int compare(User u2) {
        if ( u2 == null)
        {
        	return 1;
        }
    	if ( this==u2 || equals(u2)) return 0;
        int result = String.CASE_INSENSITIVE_ORDER.compare(
                                      getUsername()
                                      ,u2.getUsername()
                                      );
        if ( result !=0 )
            return result;
      
        return super.compareTo( u2 );
    }

    public Allocatable getPerson() 
    {
        final ReferenceHandler referenceHandler = getReferenceHandler();
        final Allocatable person = (Allocatable) referenceHandler.getEntity("person");
        return person;
    }

}
