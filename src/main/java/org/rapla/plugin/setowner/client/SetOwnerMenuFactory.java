package org.rapla.plugin.setowner.client;

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.ListView;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.entities.Entity;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.setowner.SetOwnerResources;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Singleton
@Extension(provides = ObjectMenuFactory.class, id="setowner")
public class SetOwnerMenuFactory implements ObjectMenuFactory
{
    private final DialogUiFactoryInterface dialogUiFactory;
    SetOwnerResources setOwnerI18n;
    RaplaResources i18n;
    RaplaFacade facade;
    ClientFacade clientFacade;
    private final MenuItemFactory menuItemFactory;
    private final Provider<ListView> listViewProvider;
    @Inject
    public SetOwnerMenuFactory(ClientFacade clientFacade, RaplaResources i18n, SetOwnerResources setOwnerI18n, DialogUiFactoryInterface dialogUiFactory, MenuItemFactory menuItemFactory, Provider<ListView> listViewProvider)
    {
        this.setOwnerI18n = setOwnerI18n;
        this.clientFacade = clientFacade;
        this.dialogUiFactory = dialogUiFactory;
        this.menuItemFactory = menuItemFactory;
        this.i18n = i18n;
        this.facade = clientFacade.getRaplaFacade();
        this.listViewProvider = listViewProvider;
    }

    public IdentifiableMenuEntry[] create(final SelectionMenuContext menuContext, final RaplaObject focusedObject )
    {
    	if (!clientFacade.isAdmin())
    	{
    		return IdentifiableMenuEntry.EMPTY_ARRAY;
    	}
    	
    	Collection<Object> selectedObjects = new HashSet<>();
    	Collection<?> selected = menuContext.getSelectedObjects();
    	if ( selected.size() != 0)
    	{
    		selectedObjects.addAll( selected);
    	}
    	if ( focusedObject != null)
    	{
    		selectedObjects.add( focusedObject);
    	}
    	ReferenceInfo<User> selectedOwner = null;
    		
    	final Collection<Entity<? extends Entity>> ownables = new HashSet<>();
    	for ( Object obj: selectedObjects)
    	{
    		final Entity<? extends Entity> ownable;
    		if  ( obj instanceof AppointmentBlock)
    		{
    			ownable = ((AppointmentBlock) obj).getAppointment().getReservation(); 
    		}
    		else if ( obj instanceof Entity )
    		{
    			Class<? extends  Entity> raplaType = ((Entity)obj).getTypeClass();
    	    	if ( raplaType == Appointment.class )
    	        {
    	    		Appointment appointment = (Appointment) obj;
    	    	    		ownable = appointment.getReservation();
    	        }
    	    	else if ( raplaType ==  Reservation.class)
    	    	{
    	    		ownable = (Reservation) obj;
    	    	}
    	    	else if ( raplaType ==  Allocatable.class)
    	    	{
                    final Allocatable allocatable = (Allocatable) obj;
                    //Periods are not ownable
                    if (StorageOperator.PERIOD_TYPE.equals(allocatable.getClassification().getType().getKey()))
                    {
                        ownable = null;
                    }
                    else
                    {
                        ownable = allocatable;
                    }
    	    	}
    	    	else
    	    	{
    	    		ownable = null;
    	    	}
    		}
    		else
    		{
    			ownable  = null;
    		}
    		if ( ownable != null)
    		{
    		    ownables.add( ownable);
                selectedOwner = ownables.size() > 1 ? null :((Ownable)ownable).getOwnerRef();
            }
    	}
    	
    	if ( ownables.size() == 0 )
    	{
    		return IdentifiableMenuEntry.EMPTY_ARRAY;
    	}
        final ReferenceInfo<User> owner = selectedOwner;
        Consumer<PopupContext> action = (popupContext)->
        {
            //PopupContext popupContext = menuContext.getPopupContext();
            showAddDialog(popupContext, owner).thenCompose((newOwner) ->
                    (newOwner != null) ?
                            facade.editListAsync(ownables).thenApply((editableOwnables) ->
                                    editableOwnables.values().stream().map((editableOwnable) -> {
                                        ((Ownable) editableOwnable).setOwner(newOwner);
                                        return editableOwnable;
                                    }).collect(Collectors.toList())).thenCompose((toStore) -> {
                                dialogUiFactory.busy(i18n.getString("save"));
                                return facade.dispatch(toStore, Collections.emptyList());
                            })
                            : ResolvedPromise.VOID_PROMISE
            )
                    .exceptionally((ex) ->
                    dialogUiFactory.showException(ex, popupContext))
                    .finally_(() -> dialogUiFactory.idle());
        };
        IdentifiableMenuEntry setOwnerItem = menuItemFactory.createMenuItem(setOwnerI18n.getString("changeowner"), i18n.getIcon("icon.tree.persons"), action);
        return new IdentifiableMenuEntry[] {setOwnerItem };
    }

    private Promise<User> showAddDialog(PopupContext popupContext,final ReferenceInfo<User> selectedOwner) {
        final DialogInterface dialog;
        final ListView<User> listView = listViewProvider.get();
        User[] userList;
        try
        {
            userList = facade.getUsers();
        }
        catch (RaplaException ex)
        {
            return new ResolvedPromise<>(ex);
        }
        final Collection sorted = sorted(userList);
        listView.setObjects( sorted);
        Optional<User> selectedUser;
        if ( selectedOwner != null)
        {
            selectedUser = sorted.stream().filter(user -> ((User)user).getReference().equals(selectedOwner)).findFirst();
        }
        else
        {
            selectedUser = Optional.empty();
        }
        selectedUser.ifPresent(listView::setSelected);
        dialog = dialogUiFactory.createContentDialog(
                popupContext,
                listView.getComponent(),
                new String[]{i18n.getString("apply"), i18n.getString("cancel")});
        dialog.setTitle(setOwnerI18n.getString("changeownerto"));
        final DialogInterface.DialogAction okAction = dialog.getAction(0);
        okAction.setEnabled(selectedUser.isPresent());
        listView.doubleClicked()
                .doOnNext((user)->
                        okAction.execute()
                )
                .subscribe();
        listView.selectionChanged()
                .doOnNext((selectedObjects)->
                        okAction.setEnabled(selectedObjects.size()>0)
                )
                .subscribe();
        return dialog.start(true).thenApply((index)->
            index == 0 ?  (User)listView.getSelected():null
            );
    }
    
    private <T extends Named> Collection<T> sorted(T[] allocatables) {
        TreeSet<T> sortedList = new TreeSet<>(new NamedComparator<>(i18n.getLocale()));
        sortedList.addAll(Arrays.asList(allocatables));
        return sortedList;
    }
    
}
