package org.rapla.facade.client;

import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.scheduler.Promise;

public interface ClientFacade
{
    RaplaFacade getRaplaFacade();

    /** The login method establishes the connection and loads data.
     * @return false on an invalid login.
     * @throws RaplaException if the connection can't be established.
     */
    boolean login(String username,char[] password) throws RaplaException;

    /** logout of the current user */
    void logout() throws RaplaException;

    /** returns if a session is active. True between a successful login and logout. */
    boolean isSessionActive();

    /** throws an Exception if no user has loged in.
     @return the user that has loged in. */
    User getUser() throws RaplaException;

    String getUsername(ReferenceInfo<User> id) throws RaplaException;

    void changePassword(User user,char[] oldPassword,char[] newPassword) throws RaplaException;
    boolean canChangePassword();

    /** changes the name for the logged in user. If a person is connected then all three fields are used. Otherwise only lastname is used*/
    void changeName(String title, String firstname, String surname) throws RaplaException;

    void confirmEmail(String newEmail) throws RaplaException;

    /** changes the name for the user that is logged in. */
    void changeEmail(String newEmail) throws RaplaException;


    TypedComponentRole<Integer> REFRESH_INTERVAL_ENTRY = new TypedComponentRole<>("org.rapla.refreshInterval");
    TypedComponentRole<Integer> ARCHIVE_AGE = new TypedComponentRole<>("org.rapla.archiveAge");
    int REFRESH_INTERVAL_DEFAULT = 30000;

    /**
     *  registers a new ModificationListener.
     *  A ModifictionEvent will be fired to every registered DateChangeListener
     *  when one or more entities have been added, removed or changed
     * @see ModificationListener
     * @see org.rapla.facade.ModificationEvent
     */
    void addModificationListener(ModificationListener listener);
    void removeModificationListener(ModificationListener listener);
    void addUpdateErrorListener(UpdateErrorListener listener);
    void removeUpdateErrorListener(UpdateErrorListener listener);

    //void addAllocationChangedListener(AllocationChangeListener triggerListener);
    //void removeAllocationChangedListener(AllocationChangeListener triggerListener);

    void setTemplate(Allocatable template);
    Allocatable getTemplate();
    CommandHistory getCommandHistory();

    Promise<Void> load();

    boolean isAdmin();
}