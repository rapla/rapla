package org.rapla.storage;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.LinkedHashSet;
import java.util.Set;

import java.time.LocalDateTime;
import org.rapla.components.util.DateTools;
public class PreferencePatch extends RaplaMapImpl {
    String userId;
    Set<String> removedEntries = new LinkedHashSet<>();
    java.time.LocalDateTime lastChanged;

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


    public void setLastChanged(LocalDateTime lastChanged) {
        this.lastChanged = lastChanged;
    }

    public LocalDateTime getLastChanged() {
        return lastChanged;
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
