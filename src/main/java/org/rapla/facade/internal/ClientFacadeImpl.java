package org.rapla.facade.internal;

import io.reactivex.functions.Action;
import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.dbrm.RemoteOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;

@Singleton
@DefaultImplementation(of = ClientFacade.class, context = InjectionContext.client)
public class ClientFacadeImpl implements ClientFacade, StorageUpdateListener {
    final private RaplaResources i18n;
    protected CommandScheduler notifyQueue;

    RaplaFacade raplaFacade;
    private Vector<ModificationListener> modificatonListenerList = new Vector<>();
    //private Vector<AllocationChangeListener> allocationListenerList = new Vector<AllocationChangeListener>();
    private Vector<UpdateErrorListener> errorListenerList = new Vector<>();

    //	private ConflictFinder conflictFinder;
    private Vector<ModificationListener> directListenerList = new Vector<>();
    public CommandHistory commandHistory = new CommandHistory();
    Logger logger;

    @Inject
    public ClientFacadeImpl(RaplaFacade raplaFacade, Logger logger,RaplaResources i18n)
    {
        this.raplaFacade = raplaFacade;
        notifyQueue = raplaFacade.getScheduler();
        this.logger = logger;
        this.i18n = i18n;
    }

    @Override public RaplaFacade getRaplaFacade()
    {
        return raplaFacade;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public void setOperator(StorageOperator operator)
    {
        StorageOperator oldOperator = raplaFacade.getOperator();
        if (oldOperator != null && oldOperator instanceof RemoteOperator)
        {
            ((RemoteOperator)oldOperator).removeStorageUpdateListener( this );
        }
        if ( operator instanceof  RemoteOperator)
        {
            ((RemoteOperator)operator).addStorageUpdateListener(this);
        }
        ((FacadeImpl)raplaFacade).setOperator(operator);
    }



    // Implementation of StorageUpdateListener.
    /**
     * This method is called by the storage-operator, when stored objects have
     * changed.
     *
     * <strong>Caution:</strong> You must not lock the storage operator during
     * processing of this call, because it could have been locked by the store
     * method, causing deadlocks
     */
    public void objectsUpdated(ModificationEvent evt) {
        if (getLogger().isDebugEnabled())
            getLogger().debug("Objects updated");

        if (getWorkingUserId() != null)
        {
            if ( evt.isModified( User.class))
            {
                if (getOperator().tryResolve(getWorkingUserId(), User.class) == null)
                {
                    EntityNotFoundException ex = new EntityNotFoundException("User for id " + getWorkingUserId() + " not found. Maybe it was removed.");
                    fireUpdateError(ex);
                }
            }
        }

        fireUpdateEvent(evt);
    }

    public void storageDisconnected(String message) {
        fireStorageDisconnected(message);
    }

    /******************************
     * Login - Module *
     ******************************/
    public User getUser() throws RaplaException {
        if (this.getWorkingUserId() == null) {
            throw new RaplaException("no user loged in");
        }
        return getOperator().resolve(getWorkingUserId(), User.class);
    }

    /** unlike getUserFromRequest this can be null if working user not set*/
    private User getWorkingUser() throws EntityNotFoundException {
        if ( getWorkingUserId() == null)
        {
            return null;
        }
        return getOperator().resolve(getWorkingUserId(), User.class);
    }

    @Override
    public boolean login(String username, char[] password)
            throws RaplaException {
        ConnectInfo connectInfo =new ConnectInfo(username, password);
        User user = null;
        try {
            if ( getOperator() instanceof RemoteOperator)
            {
                user = ((RemoteOperator) getOperator()).connect(connectInfo);
            }
            else
            {
                user = getOperator().getUser(username);
                if ( user == null)
                {
                    throw new EntityNotFoundException("user with username " + username + " not found.");
                }
            }
        } catch (RaplaSecurityException ex) {
            return false;
        } finally {
            // Clear password
//				for (int i = 0; i < password.length; i++)
//					password[i] = 0;
        }
//       String username = connectInfo.getUsername();
//       if  ( connectInfo.getConnectAs() != null)
//       {
//           username = connectInfo.getConnectAs();
//       }

        if ( user != null)
        {
            getLogger().info("Login " + user.getUsername());
            this.setWorkingUserId(user.getId());
            return true;
        }
        return false;
    }

    public Promise<Void> load()
    {
        if (!( getOperator() instanceof RemoteOperator))
        {
            throw new IllegalStateException("Only RemoteOperator supports async loading");
        }
        final Promise<User> connect = ((RemoteOperator) getOperator()).connectAsync();
        final Promise<Void> promise = connect.thenAccept((user) -> {
            setWorkingUserId(user.getId());
        });
        return promise;
    }

    public boolean canChangePassword() {
        try {
            return getOperator().canChangePassword();
        } catch (RaplaException e) {
            return false;
        }
    }

    public boolean isSessionActive() {
        return (this.getWorkingUserId() != null);
    }

    private boolean aborting;
    public void logout() throws RaplaException {

        if (this.getWorkingUserId() == null )
            return;
        getLogger().info("Logout " + getWorkingUserId());
        aborting = true;

        try
        {
            // now we can add it again
            this.setWorkingUserId(null);
            // we need to remove the storage update listener, because the disconnect
            // would trigger a restart otherwise
            if ( getOperator() instanceof RemoteOperator)
            {
                RemoteOperator remoteOperator = (RemoteOperator) getOperator();
                remoteOperator.removeStorageUpdateListener(this);
                remoteOperator.disconnect();
                remoteOperator.addStorageUpdateListener(this);
            }
        }
        finally
        {
            aborting = false;
        }
    }

    private boolean isAborting() {
        return aborting || !getOperator().isConnected();
    }

    public void changePassword(User user, char[] oldPassword, char[] newPassword) throws RaplaException {
        getOperator().changePassword( user, oldPassword, newPassword);
    }

    public void addModificationListener(ModificationListener listener) {
        if (!(getOperator() instanceof RemoteOperator))
        {
            throw new IllegalStateException("only allowed with RemoteOpertator");
        }
        modificatonListenerList.add(listener);
    }

    /**
     * This will directly call the modificationListener in the refresh thread.
     * You must take care of synchronization issues. It's recommended to use addModifictationListener instead
     */
    public void addDirectModificationListener(ModificationListener listener)
    {
        if (!(getOperator() instanceof RemoteOperator))
        {
            throw new IllegalStateException("only allowed with RemoteOpertator");
        }
        directListenerList.add(listener);
    }

    public void removeModificationListener(ModificationListener listener) {
        directListenerList.remove(listener);
        modificatonListenerList.remove(listener);
    }

    private Collection<ModificationListener> getModificationListeners() {
        if (modificatonListenerList.size() == 0)
        {
            return Collections.emptyList();
        }
        synchronized (this) {
            Collection<ModificationListener> list = new ArrayList<>(3);
            Iterator<ModificationListener> it = modificatonListenerList.iterator();
            while (it.hasNext()) {
                ModificationListener listener =  it.next();
                list.add(listener);
            }
            return list;
        }
    }

	/*
	public void addAllocationChangedListener(AllocationChangeListener listener) {
		if ( operator instanceof RemoteOperator)
		{
			throw new IllegalStateException("You can't add an allocation listener to a client operator because reservation objects are not updated");
		}
		allocationListenerList.add(listener);
	}

	public void removeAllocationChangedListener(AllocationChangeListener listener) {
		allocationListenerList.remove(listener);
	}

	private Collection<AllocationChangeListener> getAllocationChangeListeners() {
		if (allocationListenerList.size() == 0)
		{
			return Collections.emptyList();
		}
		synchronized (this) {
			Collection<AllocationChangeListener> list = new ArrayList<AllocationChangeListener>( 3);
			Iterator<AllocationChangeListener> it = allocationListenerList.iterator();
			while (it.hasNext()) {
				AllocationChangeListener listener = it.next();
				list.add(listener);
			}
			return list;
		}
	}

	public AllocationChangeEvent[] createAllocationChangeEvents(UpdateResult evt) {
		Logger logger = getLogger().getChildLogger("trigger.allocation");
		List<AllocationChangeEvent> triggerEvents = AllocationChangeFinder.getTriggerEvents(evt, logger);
		return triggerEvents.toArray( new AllocationChangeEvent[0]);
	}
	*/

    public void addUpdateErrorListener(UpdateErrorListener listener) {
        errorListenerList.add(listener);
    }

    public void removeUpdateErrorListener(UpdateErrorListener listener) {
        errorListenerList.remove(listener);
    }

    public UpdateErrorListener[] getUpdateErrorListeners() {
        return errorListenerList.toArray(new UpdateErrorListener[] {});
    }

    protected void fireUpdateError(RaplaException ex) {
        UpdateErrorListener[] listeners = getUpdateErrorListeners();
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].updateError(ex);
        }
    }

    protected void fireStorageDisconnected(String message) {
        UpdateErrorListener[] listeners = getUpdateErrorListeners();
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].disconnected(message);
        }
    }

    public StorageOperator getOperator() {
        return raplaFacade.getOperator();
    }

    private String getWorkingUserId() {
        return ((FacadeImpl)raplaFacade).getWorkingUserId();
    }

    private void setWorkingUserId(String workingUserId) {
        ((FacadeImpl)raplaFacade).setWorkingUserId( workingUserId);
    }

//	final class UpdateCommandAllocation implements Runnable, Command {
//		Collection<AllocationChangeListener> listenerList;
//		AllocationChangeEvent[] allocationChangeEvents;
//
//		public UpdateCommandAllocation(Collection<AllocationChangeListener> allocationChangeListeners, UpdateResult evt) {
//			this.listenerList= new ArrayList<AllocationChangeListener>(allocationChangeListeners);
//			if ( allocationChangeListeners.size() > 0)
//			{
//				allocationChangeEvents = createAllocationChangeEvents(evt);
//			}
//		}
//
//		public void execute() {
//			run();
//		}
//
//		public void run() {
//			for (AllocationChangeListener listener: listenerList)
//			{
//				try {
//					if (isAborting())
//						return;
//					if (getLogger().isDebugEnabled())
//						getLogger().debug("Notifying " + listener);
//					if (allocationChangeEvents.length > 0) {
//						listener.changed(allocationChangeEvents);
//					}
//				} catch (Exception ex) {
//					getLogger().error("update-exception", ex);
//				}
//			}
//		}
//	}

    final class UpdateCommandModification implements Action {
        ModificationListener listenerList;
        ModificationEvent modificationEvent;

        public UpdateCommandModification(ModificationListener modificationListeners, ModificationEvent evt) {
            this.listenerList = modificationListeners;
            this.modificationEvent = evt;
        }

        public void run() {
            //]for (ModificationListener listener: listenerList)
            ModificationListener listener = listenerList;
            {

                try {
                    if (isAborting())
                        return;
                    if (getLogger().isDebugEnabled())
                        getLogger().debug("Notifying " + listener);
                    listener.dataChanged(modificationEvent);
                } catch (Exception ex) {
                    getLogger().error("update-exception", ex);
                }
            }
        }

    }	/**
     * fires update event asynchronous.
     */
    protected void fireUpdateEvent(ModificationEvent evt) {
        {
            Collection<ModificationListener> modificationListeners = directListenerList;
            for (ModificationListener mod:modificationListeners)
            {
                //if (modificationListeners.size() > 0 ) {
                new UpdateCommandModification(mod,evt).run();
            }
        }
        {
            Collection<ModificationListener> modificationListeners = getModificationListeners();
            for (ModificationListener mod:modificationListeners)
            {
                notifyQueue.scheduleSynchronized(mod,new UpdateCommandModification(mod, evt));
            }
//			Collection<AllocationChangeListener> allocationChangeListeners = getAllocationChangeListeners();
//			if (allocationChangeListeners.size() > 0) {
//				notifyQueue.schedule(new UpdateCommandAllocation(allocationChangeListeners, evt),0);
//			}
        }
    }



    public void setTemplate(Allocatable template)
    {
        ((FacadeImpl)raplaFacade).setTemplateId(template != null ? template.getId() : null);
//		User workingUser;
//        try {
//            workingUser = getWorkingUser();
//        } catch (EntityNotFoundException e) {
//            // system user as change initiator won't hurt
//            workingUser = null;
//            getLogger().error(e.getMessage(),e);
//        }
        ModificationEventImpl updateResult = new ModificationEventImpl();
        updateResult.setSwitchTemplateMode(true);
        updateResult.setInvalidateInterval( new TimeInterval(null, null));
        fireUpdateEvent( updateResult);
    }

    public CommandHistory getCommandHistory()
    {
        return commandHistory;
    }

    public void changeName(String title, String firstname, String surname) throws RaplaException
    {
        User user = getUser();
        getOperator().changeName(user, title, firstname, surname);
    }

    public void changeEmail(String newEmail)  throws RaplaException
    {
        User user = getUser();
        getOperator().changeEmail(user, newEmail);
    }

    public void confirmEmail(String newEmail) throws RaplaException {
        User user = getUser();
        getOperator().confirmEmail(user, newEmail);
    }

    public Allocatable getTemplate()
    {
        return ((FacadeImpl)raplaFacade).getTemplate();
    }

    @Override
    public String getUsername(ReferenceInfo<User> id) throws RaplaException {
        return getOperator().getUsername( id);
    }

    @Override
    public boolean isAdmin() {
        final User workingUser;
        try {
            workingUser = getWorkingUser();
        } catch (EntityNotFoundException e) {
            return false;
        }
        return workingUser.isAdmin();
    }
}
