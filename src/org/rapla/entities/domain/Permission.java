/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.entities.domain;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.rapla.entities.Category;
import org.rapla.entities.User;

/** New feature to restrict the access to allocatables on a per user/group basis.
 * Specify absolute and relative booking-timeframes for each resource
 * per user/group. You can, for example, prevent modifing appointments
 * in the past, by setting the relative start-time to 0.
*/
public interface Permission
{
    String GROUP_CATEGORY_KEY = "user-groups";
    String GROUP_REGISTERER_KEY = "registerer";
    String GROUP_MODIFY_PREFERENCES_KEY = "modify-preferences";
    String GROUP_CAN_READ_EVENTS_FROM_OTHERS = "read-events-from-others";
    String GROUP_CAN_CREATE_EVENTS = "create-events";
    String GROUP_CAN_EDIT_TEMPLATES = "edit-templates";
    int DENIED = 0;
    int READ_ONLY_INFORMATION = 50;
    int READ = 100;
    int ALLOCATE =200;
    int ALLOCATE_CONFLICTS = 300;
    int ADMIN = 400;

    int NO_PERMISSION = -2;
    int ALL_USER_PERMISSION = -1;
    int GROUP_PERMISSION = 5000;
    int USER_PERMISSION = 10000;
    

    public static class AccessTable 
    {
    	final LinkedHashMap<Integer,String> map = new LinkedHashMap<Integer,String>();
    	{
    		map.put( DENIED,"denied");
    		map.put( READ_ONLY_INFORMATION,"read_no_allocation");
    		map.put( READ,"read");
    		map.put( ALLOCATE, "allocate");
    		map.put( ALLOCATE_CONFLICTS, "allocate-conflicts");
     		map.put( ADMIN, "admin");
    	}
		public String get(int accessLevel) 
		{
			return map.get(accessLevel);
		}

		public Integer findAccessLevel(String accessLevelName) 
		{
			for (Map.Entry<Integer, String> entry: map.entrySet())
			{
				if  (entry.getValue().equals( accessLevelName))
				{
					return entry.getKey();
				}
			}
			return null;
		}

		public Set<Integer> keySet() {
			return map.keySet();
		}
    }
    AccessTable ACCESS_LEVEL_NAMEMAP = new AccessTable();
    /*
     * 
    static     
    {
    	Arrays.
    	for (int i=0;i<ACCESS_LEVEL_TYPES.length;i++) 
    	{
    		ACCESS_LEVEL_NAMEMAP.put( ACCESS_LEVEL_TYPES[i], ACCESS_LEVEL_NAMES[i]);
    	}
    };
    */

    /** sets a user for the permission.
     * If a user is not null, the group will be set to null.
     */
    void setUser(User user);
    User getUser();

    /** sets a group for the permission.
     * If the group ist not null, the user will be set to null.
     */
    void setGroup(Category category);
    Category getGroup();

    /** set the minumum number of days a resource must be booked in advance. If days is null, a reservation can be booked anytime.
     * Example: If you set days to 7, a resource must be allocated 7 days before its acutual use */
    void setMinAdvance(Integer days);
    Integer getMinAdvance();

    /** set the maximum number of days a reservation can be booked in advance. If days is null, a reservation can be booked anytime.
    * Example: If you set days to 7, a resource can only be for the next 7 days. */
    void setMaxAdvance(Integer days);
    Integer getMaxAdvance();

    /** sets the starttime of the period in which the resource can be booked*/
    void setStart(Date end);
    Date getStart();

    /** sets the endtime of the period in which the resource can be booked*/
    void setEnd(Date end);
    Date getEnd();

    /** Convenince Method: returns the last date for which the resource can be booked */
    Date getMaxAllowed(Date today);
    /** Convenince Method: returns the first date for which the resource can be booked */
    Date getMinAllowed(Date today);
    
    /** returns true if one of start, end or maxAllowed, MinAllowed is set*/
    boolean hasTimeLimits();
    

    /** returns if the user or a group of the user is affected by the permission.
     * Groups are hierarchical. If the user belongs
     * to a subgroup of the permission-group the user is also
     * affected by the permission.
     * returns true if the result of getUserEffect is greater than NO_PERMISSION
     */
    boolean affectsUser( User user);
    
    /**
     * 
     * @return NO_PERMISSION if permission does not effect user
     * @return ALL_USER_PERMISSION if permission affects all users 
     * @return USER_PERMISSION if permission specifies the current user
     * @return if the permission affects a users group the depth of the permission group category specified 
     */
    int getUserEffect(User user);

    /** returns if the permission covers the interval specified by the start and end date.
     * The current date must be passed to calculate the permissable
     * interval from minAdvance and maxAdvance.
    */
    boolean covers( Date start, Date end, Date currentDate);

    /** Possible values are
     *  DENIED, READ_ONLY_INFORMATION, READ, ALLOCATE, ALLOCATE_CONFLICTS, ADMIN  
     * @param access
     */
    void setAccessLevel(int access);
    
    int getAccessLevel();

    /** Static empty dummy Array.
     * Mainly for using the toArray() method of the collection interface */
    Permission[] PERMISSION_ARRAY = new Permission[0];

}
