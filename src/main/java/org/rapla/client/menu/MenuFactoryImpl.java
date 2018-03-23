/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.menu;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Singleton @DefaultImplementation(of = MenuFactory.class, context = InjectionContext.client) public class MenuFactoryImpl
        implements MenuFactory
{

    private final MenuItemFactory menuItemFactory;
    private final Set<ReservationWizardExtension> reservationWizards;
    private final Set<ObjectMenuFactory> objectMenuFactories;
    private final PermissionController permissionController;
    private final CalendarSelectionModel model;
    private final ClientFacade clientFacade;
    private final RaplaFacade raplaFacade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Provider<RaplaObjectActions> actions;
    private final Provider<UserAction> userActions;
    private final Provider<PasswordChangeAction> passwordChangeAction;

    @Inject public MenuFactoryImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, MenuItemFactory menuItemFactory,  Set<ReservationWizardExtension> reservationWizards, Set<ObjectMenuFactory> objectMenuFactories,
            CalendarSelectionModel model,Provider<RaplaObjectActions> actions, Provider<UserAction> userActions, Provider<PasswordChangeAction> passwordChangeAction)
    {
        this.raplaLocale = raplaLocale;
        this.i18n = i18n;
        this.clientFacade = facade;
        this.menuItemFactory = menuItemFactory;
        this.raplaFacade = facade.getRaplaFacade();
        this.reservationWizards = reservationWizards;
        this.objectMenuFactories = objectMenuFactories;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        this.model = model;

        this.actions = actions;
        this.userActions = userActions;
        this.passwordChangeAction = passwordChangeAction;
    }

    public User getUser() throws RaplaException
    {
        return clientFacade.getUser();
    }

    /**
     * @param model
     * @param startDate
     * @return
     */
    protected Date getEndDate(CalendarModel model, Date startDate)
    {
        Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
        Date endDate = null;
        if (markedIntervals.size() > 0)
        {
            TimeInterval first = markedIntervals.iterator().next();
            endDate = first.getEnd();
        }
        if (endDate != null)
        {
            return endDate;
        }
        return new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
    }

    protected Date getStartDate(CalendarModel model) throws RaplaException
    {
        Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
        Date startDate = null;
        if (markedIntervals.size() > 0)
        {
            TimeInterval first = markedIntervals.iterator().next();
            startDate = first.getStart();
        }
        if (startDate != null)
        {
            return startDate;
        }

        Date selectedDate = model.getSelectedDate();
        if (selectedDate == null)
        {
            selectedDate = raplaFacade.today();
        }
        final CalendarOptions calendarOptions = RaplaComponent.getCalendarOptions(getUser(), raplaFacade);
        Date time = new Date(DateTools.MILLISECONDS_PER_MINUTE * calendarOptions.getWorktimeStartMinutes());
        startDate = raplaLocale.toDate(selectedDate, time);
        return startDate;
    }

    private void addNewMenus(MenuInterface menu, String afterId) throws RaplaException
    {
        boolean canAllocateSelected = canAllocateSelected();
        if (canAllocateSelected)
        {
            Map<String, IdentifiableMenuEntry> sortedMap = new TreeMap<String, IdentifiableMenuEntry>();
            for (ReservationWizardExtension entry : reservationWizards)
            {
                if ( entry.isEnabled())
                {
                    sortedMap.put(entry.getId(), entry);
                }
            }
            for (IdentifiableMenuEntry wizard : sortedMap.values())
            {
                Object component = wizard.getComponent();
                if ( component != null)
                {
                    menu.insertAfterId(() -> component, afterId);
                }
            }
        }
        //        else
        //        {
        //        	JMenuItem cantAllocate = new JMenuItem(i18n.getString("permission.denied"));
        //        	cantAllocate.setEnabled( false);
        //	        menu.insertAfterId(cantAllocate, afterId);
        //	    }
    }

    protected boolean canAllocateSelected() throws RaplaException
    {
        User user = getUser();
        Date today = raplaFacade.today();
        boolean canAllocate = false;
        Collection<Allocatable> selectedAllocatables = model.getMarkedAllocatables();
        Date start = getStartDate(model);
        Date end = getEndDate(model, start);
        for (Allocatable alloc : selectedAllocatables)
        {
            if (permissionController.canAllocate(alloc, user, start, end, today))
                canAllocate = true;
        }
        boolean canAllocateSelected = canAllocate || (selectedAllocatables.size() == 0 && permissionController.canUserAllocateSomething(user));
        return canAllocateSelected;
    }

    public MenuInterface addNew(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException
    {
        return addNew(menu, context, afterId, false);
    }

    public MenuInterface addNew(MenuInterface menu, SelectionMenuContext context, String afterId, boolean addNewReservationMenu) throws RaplaException
    {
        // Do nothing if the user can't allocate anything
        User user = getUser();
        final PopupContext popupContext = context.getPopupContext();
        Object focusedObject = context.getFocusedObject();

        if (permissionController.canUserAllocateSomething(user))
        {
            if (addNewReservationMenu)
            {
                addReservationWizards(menu, context, afterId);
            }
        }
        boolean allocatableType = false;
        boolean reservationType = false;
        DynamicType type = null;
        if (focusedObject instanceof DynamicType)
        {
            type = (DynamicType) focusedObject;
        }
        else if (focusedObject instanceof Classifiable)
        {
            type = ((Classifiable) focusedObject).getClassification().getType();
        }

        if (type != null)
        {
            String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            allocatableType = classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON) || classificationType
                    .equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
            reservationType = classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        }

        boolean allocatableNodeContext = allocatableType || focusedObject instanceof Allocatable || focusedObject == CalendarModelImpl.ALLOCATABLES_ROOT;
        User workingUser = getUser();

        final boolean isAdmin = workingUser.isAdmin();
        final boolean localGroupAdmin = !isAdmin && PermissionController.getAdminGroups(workingUser).size() > 0;
        boolean canAdminUsers = isAdmin || localGroupAdmin;

        if (permissionController.isRegisterer(type, user) || isAdmin)
        {
            if (allocatableNodeContext)
            {
                menu.addSeparator();
                addAllocatableMenuNew(menu, popupContext, focusedObject);
            }
        }
        boolean reservationNodeContext = reservationType || (focusedObject != null && focusedObject.equals(i18n.getString("reservation_type")));
        boolean userNodeContext = focusedObject instanceof User || (focusedObject != null && focusedObject.equals(i18n.getString("users")));
        boolean periodNodeContext = focusedObject instanceof Period || (focusedObject != null && focusedObject.equals(i18n.getString("periods")));
        boolean categoryNodeContext = focusedObject instanceof Category || (focusedObject != null && focusedObject.equals(i18n.getString("categories")));
        if (userNodeContext || allocatableNodeContext || reservationNodeContext || periodNodeContext || categoryNodeContext)
        {
            if (allocatableNodeContext || addNewReservationMenu)
            {
                menu.addSeparator();
            }
        }
        if (canAdminUsers)
        {
            if (userNodeContext)
            {
                addUserMenuNew(menu, popupContext);
            }
        }
        if (isAdmin)
        {
            if (allocatableNodeContext && addNewReservationMenu)
            {
                addTypeMenuNew(menu, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE, popupContext);
                addTypeMenuNew(menu, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON, popupContext);
            }
            if (periodNodeContext)
            {
                addPeriodMenuNew(menu, popupContext);
            }
        }
        if (canAdminUsers)
        {
            if (categoryNodeContext)
            {
                addCategoryMenuNew(menu, popupContext, focusedObject);
            }
        }
        if (isAdmin)
        {
            if (reservationNodeContext)
            {
                addTypeMenuNew(menu, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION, popupContext);
            }
        }
        return menu;
    }

    @Override
    public void addReservationWizards(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException
    {
        final User user = getUser();
        if (permissionController.canCreateReservation(user))
        {
            addNewMenus(menu, afterId);
        }
    }

    @Override
    public MenuInterface addObjectMenu(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException
    {
        Object focusedObject = context.getFocusedObject();
        final PopupContext popupContext = context.getPopupContext();

        Collection<Entity<?>> list = new LinkedHashSet<Entity<?>>();
        if (focusedObject != null && (focusedObject instanceof Entity))
        {
            Entity<?> obj = (Entity<?>) focusedObject;
            list.add(obj);
            final RaplaObjectActions action = newObjectAction(popupContext).setView(obj);
            addAction(menu, action, afterId);
        }

        for (Object obj : context.getSelectedObjects())
        {
            if (obj instanceof Entity)
            {
                list.add((Entity<?>) obj);
            }
        }

        {
            List<Entity<?>> deletableObjects = getDeletableObjects(list);
            if (deletableObjects.size() > 0)
            {
                final RaplaObjectActions action = newObjectAction(popupContext).setDeleteSelection(deletableObjects);
                addAction(menu, action, afterId);
            }
        }

        List<Entity<?>> editableObjects = getEditableObjects(list);
        Collection<Entity<?>> editObjects = getObjectsWithSameType(editableObjects);
        if (editableObjects.size() == 1)
        {
            Entity<?> first = editObjects.iterator().next();
            final RaplaObjectActions action = newObjectAction(popupContext).setEdit(first);
            addAction(menu, action, afterId);
        }
        else if (isMultiEditSupported(editableObjects))
        {
            final RaplaObjectActions action = newObjectAction(popupContext).setEditSelection(editObjects);
            addAction(menu, action, afterId);
        }
        if (editableObjects.size() == 1)
        {
            RaplaObject next = editableObjects.iterator().next();
            if (next.getTypeClass() == User.class)
            {
                addUserMenuEdit(menu, popupContext, (User) next, afterId);
            }
        }

        Iterator<ObjectMenuFactory> it = objectMenuFactories.iterator();
        while (it.hasNext())
        {
            ObjectMenuFactory objectMenuFact = it.next();
            RaplaObject obj = focusedObject instanceof RaplaObject ? (RaplaObject) focusedObject : null;
            IdentifiableMenuEntry[] items = objectMenuFact.create(context, obj);
            for (IdentifiableMenuEntry item:items)
            {
                menu.insertAfterId(item, afterId);
            }
        }
        return menu;
    }

    private boolean isMultiEditSupported(List<Entity<?>> editableObjects)
    {
        if (editableObjects.size() > 0)
        {
            Class<?> raplaType = editableObjects.iterator().next().getTypeClass();
            if (raplaType == Allocatable.class || raplaType == User.class || raplaType == Reservation.class)
            {
                return true;
            }
        }
        return false;
    }

    private void addAllocatableMenuNew(MenuInterface menu, PopupContext popupContext, Object focusedObj) throws RaplaException
    {
        MenuItemFactory f = getItemFactory();
        RaplaObjectActions newResource = newObjectAction( popupContext).setNew(Allocatable.class);
        final Locale locale = raplaLocale.getLocale();
        if (focusedObj != CalendarModelImpl.ALLOCATABLES_ROOT)
        {
            if (focusedObj instanceof DynamicType)
            {
                if (((DynamicType) focusedObj).getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE)
                        .equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
                {
                    newResource.setPerson(true);
                }
                newResource.changeObject((DynamicType) focusedObj);
            }
            if (focusedObj instanceof Allocatable)
            {
                if (((Allocatable) focusedObj).isPerson())
                {
                    newResource.setPerson(true);
                }
                newResource.changeObject((Allocatable) focusedObj);

            }
            DynamicType[] types = newResource.guessTypes();
            if (types.length == 1)    //user has clicked on a resource/person type
            {
                DynamicType type = types[0];
                newResource.setName(type.getName(locale));
            }
        }
        else
        {
            //user has clicked on top "resources" folder :
            //add an entry to createInfoDialog a new resource and another to createInfoDialog a new person
            DynamicType[] resourceType = raplaFacade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
            if (resourceType.length == 1)
            {
                newResource.setName(resourceType[0].getName(locale));
            }
            else
            {
                newResource.setName( i18n.getString("resource"));
            }

            RaplaObjectActions newPerson = newObjectAction(popupContext).setNew(Allocatable.class);
            newPerson.setPerson(true);
            DynamicType[] personType = raplaFacade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
            if (personType.length == 1)
            {
                newPerson.setName( personType[0].getName(locale));
            }
            else
            {
                newPerson.setName(i18n.getString("person"));
            }
            newPerson.addTo( menu );
        }
        newResource.addTo( menu );
    }

    private void addTypeMenuNew(MenuInterface menu, String classificationType, PopupContext popupContext)
    {
        RaplaObjectActions newReservationType = newObjectAction(popupContext);
        newReservationType.setClassificationType(classificationType );
        final IdentifiableMenuEntry menuItem = menuItemFactory.createMenuItem(i18n.getString(classificationType + "_type"), null, (popupContext1) -> newReservationType.actionPerformed());
        menu.addMenuItem(menuItem);
    }

    private void addUserMenuEdit(MenuInterface menu, PopupContext popupContext, User obj, String afterId)
    {

        menu.insertAfterId(menuItemFactory.createSeparator("sep1"), afterId);
        menu.insertAfterId(menuItemFactory.createSeparator("sep2"), afterId);
        PasswordChangeAction passwordChangeAction = this.passwordChangeAction.get().setPopupContext( popupContext);
        passwordChangeAction.changeObject(obj);
        menu.insertAfterId(passwordChangeAction.createMenuEntry(), "sep2");

        UserAction switchUserAction = userActions.get().setPopupContext(popupContext);
        switchUserAction.setSwitchToUser();
        switchUserAction.changeObject(obj);
        menu.insertAfterId(switchUserAction.createMenuEntry(), "sep2");
    }

    private void addUserMenuNew(MenuInterface menu, PopupContext popupContext)
    {
        UserAction newUserAction = userActions.get().setPopupContext(popupContext);
        newUserAction.setNew();
        final IdentifiableMenuEntry menuItem = newUserAction.createMenuEntry();
        menu.addMenuItem( menuItem);
    }

    private void addCategoryMenuNew(MenuInterface menu, PopupContext popupContext, Object obj)
    {
        RaplaObjectActions newAction = newObjectAction(popupContext).setNew(Category.class);
        if (obj instanceof Category)
        {
            newAction.changeObject((Category) obj);
        }
        else if (obj != null && obj.equals(i18n.getString("categories")))
        {
            newAction.changeObject(raplaFacade.getSuperCategory());
        }
        newAction.setName(i18n.getString("category"));
        newAction.addTo( menu );
    }

    private void addPeriodMenuNew(MenuInterface menu, PopupContext popupContext)
    {
        RaplaObjectActions action = newObjectAction(popupContext).setNew(Period.class).addTo( menu);
        action.setName(i18n.getString("period"));
        action.addTo( menu );
    }

    private RaplaObjectActions addAction(MenuInterface menu,  RaplaObjectActions action, String id)
    {
        IdentifiableMenuEntry item =  action.createMenuEntry();
        menu.insertAfterId( item, id);
        return action;
    }

    private RaplaObjectActions newObjectAction(PopupContext popupContext)
    {
        return actions.get().setPopupContext( popupContext);
    }



    // This will exclude DynamicTypes and non editable Objects from the list
    private List<Entity<?>> getEditableObjects(Collection<Entity<?>> list) throws RaplaException
    {
        Iterator<Entity<?>> it = list.iterator();
        ArrayList<Entity<?>> editableObjects = new ArrayList<Entity<?>>();
        final User user = getUser();
        while (it.hasNext())
        {
            Entity o = it.next();
            if (permissionController.canModify(o, user))
                editableObjects.add((Entity<?>) o);
        }
        return editableObjects;
    }

    private List<Entity<?>> getDeletableObjects(Collection<Entity<?>> list) throws RaplaException
    {
        Iterator<Entity<?>> it = list.iterator();
        Category superCategory = raplaFacade.getSuperCategory();
        ArrayList<Entity<?>> deletableObjects = new ArrayList<Entity<?>>();
        final User user = getUser();
        while (it.hasNext())
        {
            Entity<?> o = it.next();
            if (permissionController.canDelete(o, user) && !o.equals(superCategory))
                deletableObjects.add(o);
        }
        return deletableObjects;
    }

    // method for filtering a selection(Parameter: list) of similar RaplaObjekte
    // (from type raplaType)
    // criteria: RaplaType: isPerson-Flag
    private <T extends RaplaObject> List<T> getObjectsWithSameType(Collection<T> list, Class raplaType, boolean isPerson)
    {
        ArrayList<T> objects = new ArrayList<T>();

        for (RaplaObject o : list)
        {
            // element will be added if it is from the stated RaplaType...
            if (raplaType != null && (o.getTypeClass() == raplaType))
            {
                // ...furthermore the flag isPerson at allocatables has to
                // be conform, because person and other resources aren't
                // able to process at the same time
                if (raplaType != Allocatable.class || ((Allocatable) o).isPerson() == isPerson)
                {
                    @SuppressWarnings("unchecked") T casted = (T) o;
                    objects.add(casted);
                }
            }

        }
        return objects;
    }

    private <T extends RaplaObject> Collection<T> getObjectsWithSameType(Collection<T> list)
    {
        Iterator<T> iterator = list.iterator();
        if (!iterator.hasNext())
        {
            return list;
        }
        RaplaObject obj = iterator.next();
        Class raplaType = obj.getTypeClass();
        boolean isPerson = raplaType == Allocatable.class && ((Allocatable) obj).isPerson();
        return getObjectsWithSameType(list, raplaType, isPerson);
    }

    @Override
    public MenuItemFactory getItemFactory() {
        return menuItemFactory;
    }
}









