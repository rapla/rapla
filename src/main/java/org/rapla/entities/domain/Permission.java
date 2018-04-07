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

import jsinterop.annotations.JsType;
import org.rapla.entities.Category;
import org.rapla.entities.User;

import java.util.Date;

/** New feature to restrict the access to allocatables on a per user/group basis.
 * Specify absolute and relative booking-timeframes for each resource
 * per user/group. You can, for example, prevent modifing appointments
 * in the past, by setting the relative start-time to 0.
*/
@JsType
public interface Permission
{
    String GROUP_CATEGORY_KEY = "user-groups";
    
    @Deprecated
    String GROUP_MODIFY_PREFERENCES_KEY = "modify-preferences";
    @Deprecated
    String GROUP_CAN_EDIT_TEMPLATES = "edit-templates";
    @Deprecated
    String GROUP_CAN_READ_EVENTS_FROM_OTHERS = "read-events-from-others";
    @Deprecated
    String GROUP_CAN_CREATE_EVENTS = "create-events";
    @Deprecated
    String GROUP_REGISTERER_KEY = "registerer";
    
    enum AccessLevel
    {
        DENIED(0),
        READ_TYPE(20),
        CREATE(30),
        READ_NO_ALLOCATION(50),
        READ(100),
        ALLOCATE(200),
        ALLOCATE_CONFLICTS(300),
        EDIT(350),
        ADMIN(400);
        AccessLevel(int level)
        {
            this.level = level;
        }
        private final int level;
        public static AccessLevel find(int level)
        {
            for(AccessLevel v :values())
            {
                if (v.level == level)
                {
                    return v;
                }
            }
            return null;
        }
        
        @Deprecated
        public int getNumericLevel()
        {
            return level;
        }
        
        public boolean excludes(AccessLevel level) {
            return level.level > this.level;
        }

        public boolean includes(AccessLevel level) {
            return level.level <= this.level;
        }

        public static AccessLevel find(String accessLevel) {
            AccessLevel valueOf = valueOf( accessLevel.toUpperCase());
            if ( valueOf == null && "READ_ONLY_INFORMATION".equalsIgnoreCase(accessLevel))
            {
                return READ_NO_ALLOCATION;
            }
            return valueOf;
        }
    }

    AccessLevel DENIED = AccessLevel.DENIED;
    AccessLevel READ_TYPE =AccessLevel.READ_TYPE;
    AccessLevel CREATE = AccessLevel.CREATE;
    AccessLevel READ_NO_ALLOCATION = AccessLevel.READ_NO_ALLOCATION;
    AccessLevel READ = AccessLevel.READ;
    AccessLevel ALLOCATE = AccessLevel.ALLOCATE;
    AccessLevel ALLOCATE_CONFLICTS = AccessLevel.ALLOCATE_CONFLICTS;
    AccessLevel EDIT = AccessLevel.EDIT;
    AccessLevel ADMIN = AccessLevel.ADMIN;

//    public static class AccessTable 
//    {
//    	final LinkedHashMap<Integer,String> map = new LinkedHashMap<Integer,String>();
//    	{
//    		map.put( READ_TYPE,"read_type");
//    		map.put( CREATE, "createInfoDialog");
//            map.put( DENIED,"denied");
//            map.put( READ_NO_ALLOCATION,"read_no_allocation");
//    		map.put( READ,"read");
//    		map.put( ALLOCATE, "allocate");
//    		map.put( ALLOCATE_CONFLICTS, "allocate_conflicts");
//    		map.put( EDIT, "edit");
//     		map.put( ADMIN, "admin");
//    	}
//		public String get(int accessLevel) 
//		{
//			return map.get(accessLevel);
//		}
//
//		public Integer findAccessLevel(String accessLevelName) 
//		{
//		    AccessLevel.valueOf(arg0)
//			for (Map.Entry<Integer, String> entry: map.entrySet())
//			{
//				if  (entry.getValue().equals( accessLevelName))
//				{
//					return entry.getKey();
//				}
//			}
//			return null;
//		}
//
//		public Set<Integer> keySet() {
//			return map.keySet();
//		}
//    }
    
    //AccessTable ACCESS_LEVEL_NAMEMAP = new AccessTable();
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

    String getUserId();

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
        

    /** returns if the permission covers the interval specified by the start and end date.
     * The current date must be passed to calculate the permissable
     * interval from minAdvance and maxAdvance.
    */
    boolean covers( Date start, Date end, Date currentDate);

    /** Possible values are
     *  DENIED, READ_ONLY_INFORMATION, READ, ALLOCATE, ALLOCATE_CONFLICTS, ADMIN  
     * @param access
     */
    void setAccessLevel(AccessLevel access);
    
    AccessLevel getAccessLevel();

    /** Static empty dummy Array.
     * Mainly for using the toArray() method of the collection interface */
    Permission[] PERMISSION_ARRAY = new Permission[0];
    
    Permission clone();

}
