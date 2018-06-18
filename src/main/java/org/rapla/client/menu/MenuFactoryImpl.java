/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.internal.ResourceCalendarTask;
import org.rapla.client.internal.admin.client.CategoryMenuContext;
import org.rapla.client.internal.admin.client.DynamicTypeMenuContext;
import org.rapla.client.internal.admin.client.PeriodMenuContext;
import org.rapla.client.internal.admin.client.UserMenuContext;
import org.rapla.client.menu.impl.AppointmentAction;
import org.rapla.client.menu.impl.RaplaObjectActions;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
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
import org.rapla.plugin.abstractcalendar.RaplaBlock;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private final Provider<AppointmentAction> appointmentActions;
    private final Provider<UserAction> userActions;
    private final Provider<PasswordChangeAction> passwordChangeAction;
    private final RaplaClipboard clipboard;
    private final ApplicationEventBus eventBus;

    @Inject public MenuFactoryImpl(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, MenuItemFactory menuItemFactory, Set<ReservationWizardExtension> reservationWizards, Set<ObjectMenuFactory> objectMenuFactories,
            CalendarSelectionModel model, Provider<RaplaObjectActions> actions, Provider<AppointmentAction> appointmentActions, Provider<UserAction> userActions,
            Provider<PasswordChangeAction> passwordChangeAction, RaplaClipboard raplaClipboard, ApplicationEventBus eventBus)
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
        this.appointmentActions = appointmentActions;
        this.userActions = userActions;
        this.passwordChangeAction = passwordChangeAction;
        this.clipboard = raplaClipboard;
        this.eventBus = eventBus;
    }

    @Override
    public MenuInterface addCalendarSelectionMenu(MenuInterface menu, SelectionMenuContext context) throws RaplaException
    {
        PopupContext popupContext = context.getPopupContext();
        addReservationWizards(menu, context, null);
        MenuItemFactory f = getItemFactory();
        User user = getUser();
        if (permissionController.canCreateReservation(user))
        {
            //	        	 User user = getUserFromRequest();
            //	 	        Date today = getQuery().today();
            //	 	        boolean canAllocate = false;
            //	 	        Collection<Allocatable> selectedAllocatables = getMarkedAllocatables();
            //	 	        for ( Allocatable alloc: selectedAllocatables) {
            //	 	            if (alloc.canAllocate( user, start, end, today))
            //	 	                canAllocate = true;
            //	 	        }
            //	 	       canAllocate || (selectedAllocatables.size() == 0 &&

//            if (permissionController.canUserAllocateSomething(user))
//            {
//                ReservationEdit[] editWindows = editController.getEditWindows();
//                if (editWindows.length > 0)
//                {
//                    RaplaMenu addItem = new RaplaMenu("add_to");
//                    addItem.setText(i18n.getString("add_to"));
//                    menu.add(addItem);
//                    for (ReservationEdit reservationEdit : editWindows)
//                    {
//                        createAction( popupContext).setAddTo(reservationEdit).addTo( menu, f);
//                    }
//                }
//            }
//            else
//            {
//                JMenuItem cantAllocate = new JMenuItem(i18n.getString("permission.denied"));
//                cantAllocate.setEnabled(false);
//                menu.add(cantAllocate);
//            }
        }
        //
        Appointment appointment = clipboard.getAppointment();
        if (appointment != null)
        {
            if (clipboard.isPasteExistingPossible())
            {
                createAction( popupContext).setPaste().addTo(menu, f );
            }
            createAction(popupContext).setPasteAsNew().addTo(menu, f);
        }
        return menu;
    }

    @Override
    public MenuInterface addCopyCutListMenu( MenuInterface editMenu, SelectionMenuContext menuContext, String afterId,
            Consumer<PopupContext> cutListener, Consumer<PopupContext> copyListener) throws RaplaException
    {
        // add the new reservations wizards
        Collection<AppointmentBlock> selection = (Collection<AppointmentBlock>) menuContext.getSelectedObjects();
        //TODO add cut and copyReservations for more then 1 block
        if (selection.size() == 1)
        {
            if ( cutListener != null)
            {
                final IdentifiableMenuEntry menuItem = menuItemFactory.createMenuItem(i18n.getString("cut"), i18n.getIcon("icon.cut"), cutListener);
                editMenu.insertAfterId(menuItem, afterId);
            }
            if ( copyListener != null)
            {
                final IdentifiableMenuEntry menuItem = menuItemFactory.createMenuItem(i18n.getString("copy"), i18n.getIcon("icon.copy"), copyListener);
                editMenu.insertAfterId(menuItem, afterId);
            }
        }
        return editMenu;
    }

    private MenuInterface addAppointmentBlockMenu(MenuInterface menu, RaplaBlock b, PopupContext popupContext) throws RaplaException
    {
        AppointmentBlock appointmentBlock = b.getAppointmentBlock();
        Appointment appointment = appointmentBlock.getAppointment();
        Date start = b.getStart();
        boolean isException = b.isException();
        Allocatable groupAllocatable = b.getGroupAllocatable();
        Collection<Allocatable> copyContextAllocatables;
        if (groupAllocatable != null)
        {
            copyContextAllocatables = Collections.singleton(groupAllocatable);
        }
        else
        {
            copyContextAllocatables = Collections.emptyList();
        }

        MenuItemFactory f = getItemFactory();
        createAction(popupContext).setCopy(appointmentBlock, copyContextAllocatables).addTo(menu, f);
        createAction(popupContext).setCut(appointmentBlock, copyContextAllocatables).addTo( menu, f);
        createAction(popupContext).setEdit(appointmentBlock).addTo( menu, f);
        if (!isException)
        {
            createAction( popupContext).setDelete(appointmentBlock).addTo( menu, f);
        }
        createAction( popupContext).setView(appointmentBlock).addTo( menu, f);

        Iterator<ObjectMenuFactory> it = objectMenuFactories.iterator();
        while (it.hasNext())
        {
            ObjectMenuFactory objectMenuFact = it.next();
            SelectionMenuContext menuContext = new SelectionMenuContext( appointment,popupContext);
            menuContext.setSelectedDate( start);
            IdentifiableMenuEntry[] items = objectMenuFact.create(menuContext, appointment);
            for (IdentifiableMenuEntry item:items)
            {
                menu.addMenuItem(item);
            }
        }
        return menu;
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

    private int addNewMenus(MenuInterface menu, String afterId) throws RaplaException
    {
        int count=0;
        boolean canAllocateSelected = canAllocateSelected();
        if (canAllocateSelected)
        {
            Map<String, IdentifiableMenuEntry> sortedMap = new TreeMap<>();
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
                    count++;
                }
            }
        }
        return count;
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

    public MenuInterface addNewMenu(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException
    {
        return addNew(menu, context, afterId, false);
    }

    @Override
    public MenuInterface addReservationMenu(final MenuInterface menu, final SelectionMenuContext context, final
    String afterId) throws RaplaException {
        return addNew(menu, context, afterId, true);
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
        final boolean dynamicTypeEdit = context instanceof DynamicTypeMenuContext;
        boolean periodNodeContext = context instanceof PeriodMenuContext || focusedObject instanceof Period;
        boolean allocatableNodeContext = !periodNodeContext && !dynamicTypeEdit &&  (allocatableType || focusedObject instanceof Allocatable || focusedObject == CalendarModelImpl.ALLOCATABLES_ROOT );
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
        boolean userNodeContext = focusedObject instanceof User || context instanceof UserMenuContext;
        boolean categoryNodeContext = context instanceof CategoryMenuContext;
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
            if (dynamicTypeEdit)
            {
                final String classificationType = ((DynamicTypeMenuContext) context).getClassificationType();
                if (classificationType != null) {
                    addTypeMenuNew(menu, classificationType, popupContext);
                }
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
                addCategoryMenuNew(menu, (CategoryMenuContext)context, focusedObject);
            }
        }
        return menu;
    }

    @Override
    public int addReservationWizards(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException
    {
        int count = 0;
        final User user = getUser();
        if (permissionController.canCreateReservation(user))
        {
            count+=addNewMenus(menu, afterId);
        }
        return count;
    }

    @Override
    public MenuInterface addEventMenu(MenuInterface editMenu, SelectionMenuContext menuContext, Consumer<PopupContext> cutListener, Consumer<PopupContext> copyListener) throws RaplaException
    {
        String afterId = "NEW";
        MenuInterface newMenu = menuItemFactory.createMenu(i18n.getString("new"), i18n.getIcon("icon.new"), afterId);
        editMenu.addMenuItem(newMenu);
        editMenu.setTitle(i18n.getString("selection"));

        boolean canUserAllocateSomething = permissionController.canUserAllocateSomething(getUser());
        addCopyCutListMenu(  editMenu, menuContext, afterId, copyListener, cutListener);
        addObjectMenu( editMenu, menuContext,afterId);
        // add the new reservations wizards
        int count = addReservationWizards( newMenu, menuContext, afterId);

        boolean enableNewMenu = count > 0 && canUserAllocateSomething;

        newMenu.setEnabled(enableNewMenu);
        return editMenu;
    }




    @Override
    public MenuInterface addObjectMenu(MenuInterface menu, SelectionMenuContext context, String afterId) throws RaplaException
    {
        Object focusedObject = context.getFocusedObject();
        final PopupContext popupContext = context.getPopupContext();
        Collection<Entity<?>> list = new LinkedHashSet<>();
        if (focusedObject != null)
        {
            if ((focusedObject instanceof Entity))
            {
                Entity<?> obj = (Entity<?>) focusedObject;
                if (isContextEditable(context, obj))
                {
                    list.add(obj);
                }
                if ( !(focusedObject instanceof DynamicType))
                {
                    final RaplaObjectActions action = newObjectAction(popupContext).setView(obj);
                    addAction(menu, action, afterId);
                }
            }
            if ((focusedObject instanceof RaplaBlock))
            {
                addAppointmentBlockMenu(menu, (RaplaBlock)focusedObject, popupContext);
            }

        }

        MenuItemFactory f = getItemFactory();
        if ( focusedObject  != null && focusedObject instanceof AppointmentBlock)
        {
            AppointmentBlock appointmentBlock = (AppointmentBlock) focusedObject;
            {
                AppointmentAction action = createAction(popupContext);
                action.setDelete(appointmentBlock);
                final IdentifiableMenuEntry menuItem = action.createMenuEntry(f);
                menu.insertAfterId(menuItem, afterId);
            }
            {
                AppointmentAction action = createAction(popupContext);
                action.setView(appointmentBlock);
                final IdentifiableMenuEntry menuItem = action.createMenuEntry(f);
                menu.insertAfterId(menuItem, afterId);
            }
            {
                AppointmentAction action = createAction(popupContext);
                action.setEdit(appointmentBlock);
                final IdentifiableMenuEntry menuItem = action.createMenuEntry(f);
                menu.insertAfterId(menuItem, afterId);
            }
        }

        final Collection<?> selectedObjects = context.getSelectedObjects();
        Collection<Entity<?>> deletableEntities = new ArrayList<>();
        Collection<AppointmentBlock> deletableAppointmenBlocks = new ArrayList<>();
        for (Object obj : selectedObjects)
        {
            if (obj instanceof Entity)
            {
                final Entity<?> entity = (Entity<?>) obj;
                if (isContextEditable(context, entity))
                {
                    list.add(entity);
                    if (isDeletable(entity ))
                    {
                        deletableEntities.add(entity );
                    }
                }

            }
            if (obj instanceof AppointmentBlock)
            {
                deletableAppointmenBlocks.add( (AppointmentBlock) obj);
            }
        }

        if ( deletableAppointmenBlocks.size() > 1)
        {
            AppointmentAction action = createAction(popupContext);
            action.setDeleteSelection(deletableAppointmenBlocks);
            final IdentifiableMenuEntry menuItem = action.createMenuEntry(f);
            menu.insertAfterId(menuItem, afterId);
        }
        if (!deletableEntities.isEmpty())
        {
            final RaplaObjectActions action = newObjectAction(popupContext).setDeleteSelection(deletableEntities);
            addAction(menu, action, afterId);
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
            if ( focusedObject instanceof AppointmentBlock)
            {
                obj = ((AppointmentBlock)focusedObject).getAppointment();
            }
            IdentifiableMenuEntry[] items = objectMenuFact.create(context, obj);
            for (IdentifiableMenuEntry item:items)
            {
                menu.insertAfterId(item, afterId);
            }
        }
        return menu;
    }

    public boolean isContextEditable(SelectionMenuContext context, Entity<?> obj) {
        return !obj.getTypeClass().equals(DynamicType.class) || (context instanceof DynamicTypeMenuContext);
    }

    private boolean isMultiEditSupported(List<Entity<?>> editableObjects)
    {
        if (!editableObjects.isEmpty())
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
        //final String string = i18n.getString(classificationType + "_type");
        final String string = i18n.getString("new");
        final IdentifiableMenuEntry menuItem = menuItemFactory.createMenuItem(string, i18n.getIcon("icon.new"), (popupContext1) -> newReservationType.actionPerformed());
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

    private void addCategoryMenuNew(MenuInterface menu, CategoryMenuContext menuContext, Object obj)
    {
        PopupContext popupContext = menuContext.getPopupContext();
        RaplaObjectActions newAction = newObjectAction(popupContext).setNew(Category.class);

        if (obj instanceof Category)
        {
            newAction.changeObject((Category) obj);
        }
        else if (obj != null && obj.equals(i18n.getString("categories")))
        {
            newAction.changeObject(raplaFacade.getSuperCategory());
        }
        else
        {
            newAction.changeObject( menuContext.getRootCategory());
        }
        newAction.setName(i18n.getString("new_category"));
        //newAction.setName(i18n.getString("category"));
        newAction.addTo( menu );
    }

    private void addPeriodMenuNew(MenuInterface menu, PopupContext popupContext)
    {
        RaplaObjectActions action = newObjectAction(popupContext).setNew(Period.class).addTo( menu);
        action.setName(i18n.getString("new"));
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

    public AppointmentAction createAction(PopupContext popupContext)
    {
        AppointmentAction action = appointmentActions.get().setPopupContext( popupContext);
        return action;
    }

    // This will exclude DynamicTypes and non editable Objects from the list
    private List<Entity<?>> getEditableObjects(Collection<Entity<?>> list) throws RaplaException
    {
        Iterator<Entity<?>> it = list.iterator();
        ArrayList<Entity<?>> editableObjects = new ArrayList<>();
        final User user = getUser();
        while (it.hasNext())
        {
            Entity o = it.next();

            if (permissionController.canModify(o, user))
                editableObjects.add((Entity<?>) o);
        }
        return editableObjects;
    }

    private boolean isDeletable(Entity<?> o) throws RaplaException
    {
        final User user = getUser();
        Category superCategory = raplaFacade.getSuperCategory();
        return permissionController.canDelete(o, user) && !o.equals(superCategory);
    }

    // method for filtering a selection(Parameter: list) of similar RaplaObjekte
    // (from type raplaType)
    // criteria: RaplaType: isPerson-Flag
    private <T extends RaplaObject> List<T> getObjectsWithSameType(Collection<T> list, Class raplaType, boolean isPerson)
    {
        ArrayList<T> objects = new ArrayList<>();

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

    @Override
    public void executeCalenderAction(AllocatableReservationMenuContext menuContext)
    {
        final List<Allocatable> selectedObjects = (List<Allocatable>) menuContext.getSelectedObjects();
        final EditApplicationEventContext tEditApplicationEventContext = new EditApplicationEventContext(selectedObjects);
        tEditApplicationEventContext.setCalendarModel( menuContext.getModel());
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for ( Allocatable alloc:selectedObjects)
        {
            if ( first)
            {
                first = false;
            }
            else
            {
                builder.append(";");
            }
            builder.append(alloc.getId());
        }
        final String info = builder.toString();
        ApplicationEvent applicationEvent = new ApplicationEvent(ResourceCalendarTask.ID, info,menuContext.getPopupContext(),tEditApplicationEventContext);
        eventBus.publish(applicationEvent);
    }
}










