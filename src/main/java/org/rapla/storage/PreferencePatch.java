package org.rapla.storage;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

public class PreferencePatch extends RaplaMapImpl {
    String userId;
    Set<String> removedEntries = new LinkedHashSet<>();
    Date lastChanged;
    
    public void addRemove(String role) {
        removedEntries.add( role);
    }
   
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }

    public ReferenceInfo<User> getUserRef()
    {
        return new ReferenceInfo<>(userId, User.class);
    }
    
    public Date getLastChanged() {
        return lastChanged;
    }

    public void setLastChanged(Date lastChanged) {
        this.lastChanged = lastChanged;
    }
    
    public Set<String> getRemovedEntries() 
    {
        return removedEntries;
    }
    
    @Override
    public String toString() {
        return "Patch for " + userId + " " + super.toString() + " Removed " + removedEntries.toString(); 
    }

    public ReferenceInfo<Preferences> getReference()
    {
        return PreferencesImpl.getPreferenceIdFromUser( getUserId());
    }
}
