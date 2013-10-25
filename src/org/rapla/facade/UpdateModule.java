/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.facade;

import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
public interface UpdateModule
{
	public final static TypedComponentRole<Integer> REFRESH_INTERVAL_ENTRY = new TypedComponentRole<Integer>("org.rapla.refreshInterval");
    public final static TypedComponentRole<Integer> ARCHIVE_AGE = new TypedComponentRole<Integer>("org.rapla.archiveAge");
	public static final int REFRESH_INTERVAL_DEFAULT = 30000;
    
    /**  
     *  Refreshes the data that is in the cache (or on the client)
        and notifies all registered {@link ModificationListener ModificationListeners}
        with an update-event. 
        There are two types of refreshs.
        
        <ul>
        <li>Incremental Refresh: Only the changes are propagated</li>
        <li>Full Refresh: The complete data is reread. (Currently disabled in Rapla)</li>
        </ul>
        
        <p>
        Incremental refreshs are the normal case if you have a client server basis.
        (In a single user system no refreshs are necessary at all). 
        The refreshs are triggered in defined intervals if you use the webbased communication 
        and automaticaly if you use the old communication layer. You can change the refresh interval
        via the admin options.
        </p>
        <p>
        Of course you can call a refresh anytime you want to synchronize with the server, e.g. if 
        you want to ensure you are uptodate before editing. If you are on the server you dont need to refresh.
        </p>
        
        
        <strong>WARNING: When using full refresh on a local file storage
        all information will be  changed. So use it  
        only if you modify the data from external.
        You better re-get and re-draw all
        the information in the Frontend after a full refresh.
        </strong>
       
    */
    void refresh() throws RaplaException;
    /** returns if the Facade is connected through a server (false if it has a local store)*/
    boolean isClientForServer();
    /**
     *  registers a new ModificationListener.
     *  A ModifictionEvent will be fired to every registered DateChangeListener
     *  when one or more entities have been added, removed or changed
     * @see ModificationListener
     * @see ModificationEvent
    */
    void addModificationListener(ModificationListener listener);
    void removeModificationListener(ModificationListener listener);
    void addUpdateErrorListener(UpdateErrorListener listener);
    void removeUpdateErrorListener(UpdateErrorListener listener);
    
    void addAllocationChangedListener(AllocationChangeListener triggerListener);
    void removeAllocationChangedListener(AllocationChangeListener triggerListener);
}





