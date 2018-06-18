package org.rapla.client.swing.internal.edit;

import org.jetbrains.annotations.Nullable;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.RaplaListEdit.RaplaListEditFactory;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.ClassificationField.ClassificationFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionListField.PermissionListFieldFactory;
import org.rapla.client.swing.internal.edit.reservation.SortedListModel;
import org.rapla.entities.Entity;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TemplateEdit extends RaplaGUIComponent
{
    RaplaListEdit<Allocatable> templateList;

    Collection<Entity> toStore = new LinkedHashSet<>();
    Collection<Allocatable> toRemove = new LinkedHashSet<>();
    private final CalendarSelectionModel calendarSelectionModel;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final PermissionController permissionController;
    private final EditController editController;
    private DialogInterface.DialogAction editAction;
    AllocatableEditUI allocatableEdit;
    DefaultListModel model = new DefaultListModel();

    @Inject
    public TemplateEdit(final ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel calendarSelectionModel,
            final DialogUiFactoryInterface dialogUiFactory, ClassificationFieldFactory classificationFieldFactory,
            PermissionListFieldFactory permissionListFieldFactory, RaplaListEditFactory raplaListEditFactory, BooleanFieldFactory booleanFieldFactory,
            EditController editController)
    {
        super(facade, i18n, raplaLocale, logger);
        this.calendarSelectionModel = calendarSelectionModel;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        this.editController = editController;
        allocatableEdit = new AllocatableEditUI(facade, i18n, raplaLocale, logger, classificationFieldFactory, permissionListFieldFactory, booleanFieldFactory)
        {
            protected void mapFromObjects() throws RaplaException
            {
                super.mapFromObjects();
                permissionListField.setPermissionLevels(Permission.READ, Permission.EDIT);
                classificationField.setScrollingAlwaysEnabled(false);
                for (Allocatable allocatable:objectList)
                {
                    final User user = getUser();
                    final boolean canEdit = permissionController.canModify(allocatable, user);
                    classificationField.setReadOnly(!canEdit);
                }
            }

            @Override
            public void stateChanged(ChangeEvent evt)
            {
                try
                {
                    allocatableEdit.mapToObjects();
                    List<Allocatable> objects = allocatableEdit.getObjects();
                    templateList.resort();

                    toStore.addAll(objects);
                }
                catch (RaplaException e)
                {
                    getLogger().error(e.getMessage(), e);
                }
            }
        };

        ActionListener callback = evt -> {
            //int index = getSelectedIndex();
            try
            {
                if (evt.getActionCommand().equals("remove"))
                {
                    removeTemplate();
                }
                else if (evt.getActionCommand().equals("copy"))
                {
                    Allocatable template = templateList.getSelectedValue();
                    if ( template != null)
                    {
                        handleException(copyTemplate( template));
                    }
                    //if ( template)
                }
                else if (evt.getActionCommand().equals("new"))
                {
                    createTemplate();
                }
                else if (evt.getActionCommand().equals("edit"))
                {
                    Allocatable template = templateList.getSelectedValue();
                    List<Allocatable> list;
                    if (template != null)
                    {
                        list = Collections.singletonList(template);
                    }
                    else
                    {
                        list = Collections.emptyList();
                    }
                    allocatableEdit.setObjects(list);
                    allocatableEdit.mapFromObjects();
                }

            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(templateList.getComponent(), null));
            }
        };
        templateList = raplaListEditFactory.create( allocatableEdit.getComponent(), callback, true);
        templateList.setNameProvider((object)->object.getName(getLocale()));
        templateList.getList().addListSelectionListener(e->
            {
                final Allocatable selectedValue = templateList.getSelectedValue();
                User user = null;
                try
                {
                    user = getUser();
                    editAction.setEnabled( selectedValue == null || permissionController.canModify( selectedValue, user));
                }
                catch (RaplaException e1)
                {
                    logger.error( e1.getMessage(), e1);
                }

            }
        );
        final ReferenceInfo<User> currentUser = getCurrentUserId();
        templateList.getList().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.getList().setCellRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                if (value instanceof Allocatable)
                {
                    final Allocatable value1 = (Allocatable) value;
                    final ReferenceInfo<User> ownerRef = value1.getOwnerRef();
                    value = value1.getName(getRaplaLocale().getLocale());
                    if (ownerRef != null && !ownerRef.equals(currentUser))
                    {
                        String username = getUsername(ownerRef);
                        value = username + ": " + value;
                    }
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });

        templateList.setMoveButtonVisible(false);
        templateList.getComponent().setPreferredSize(new Dimension(1000, 500));
    }

    @Nullable
    protected ReferenceInfo<User> getCurrentUserId() {
        try {
            return getUser().getReference();
        } catch (RaplaException e) {
            return null;
        }
    }

    private void handleException(Promise<Void> promise) {
        promise.exceptionally((ex)->dialogUiFactory.showException( ex, dialogUiFactory.createPopupContext(templateList)));
    }

    Map<ReferenceInfo<User>, String> usernameMap = new HashMap<>();

    private String getUsername(ReferenceInfo<User> userId)
    {
        String username = usernameMap.get(userId);
        if (username == null)
        {
            try
            {
                username = getClientFacade().getUsername(userId);
                usernameMap.put(userId, username);
            }
            catch (RaplaException e)
            {
                getLogger().error("Could not resolve username for user: " + e.getMessage(), e);
            }
        }
        return username;

    }

    public String getNewTemplateName() throws RaplaException
    {
        Collection<Allocatable> templates = new LinkedHashSet<>(getQuery().getTemplates());
        Collection<String> templateNames = new LinkedHashSet<>();
        Locale locale = getLocale();
        for (Allocatable template : templates)
        {
            templateNames.add(template.getName(locale));
        }
        for (int i = 0; i < model.size(); i++)
        {
            Allocatable template = (Allocatable) model.get(i);
            templateNames.add(template.getName(locale));
        }
        int index = 0;
        String username = getUser().getUsername();
        while (true)
        {
            String indexStr = username + (index == 0 ? "" : " " + index);
            String newEvent = getI18n().format("new_reservation.format", indexStr);
            if (!templateNames.contains(newEvent))
            {
                return newEvent;
            }
            index++;
        }
    }

    private void removeTemplate()
    {
        Allocatable template = templateList.getSelectedValue();
        if (template != null)
        {
            toRemove.add(template);
            model.removeElement(template);
        }
    }

    private Promise<Void> copyTemplate(Allocatable originalTemplate)
    {
        final RaplaFacade facade = getFacade();
        return facade.cloneAsync(originalTemplate).thenCompose(templateCopy->
        {
            final String name = (String) templateCopy.getClassification().getValue("name");
            String newName = name != null ? (name + " (Kopie)") : getNewTemplateName();
            templateCopy.getClassification().setValue("name", newName);
            toStore.add(templateCopy);
            return getQuery().getTemplateReservations(originalTemplate).
                    thenCompose((reservations) -> facade.cloneList(reservations)).thenAccept(clones ->
            {
                for (Reservation clone : clones) {
                    clone.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, templateCopy.getId());
                    toStore.add(clone);
                }
                model.addElement(templateCopy);
                boolean shouldScroll = true;
                templateList.getList().clearSelection();
                templateList.getList().setSelectedValue(templateCopy, shouldScroll);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void createTemplate() throws RaplaException
    {
        String name = getNewTemplateName();
        DynamicType dynamicType = getQuery().getDynamicType(StorageOperator.RAPLA_TEMPLATE);
        Classification newClassification = dynamicType.newClassification();
        newClassification.setValue("name", name);
        final User user = getUser();
        Allocatable template = getFacade().newAllocatable(newClassification, user);
        Collection<Permission> permissionList = new ArrayList<>(template.getPermissionList());
        for (Permission permission : permissionList)
        {
            template.removePermission(permission);
        }
        toStore.add(template);
        model.addElement(template);
        boolean shouldScroll = true;
        templateList.getList().clearSelection();
        templateList.getList().setSelectedValue(template, shouldScroll);
    }

    public void startTemplateEdit()
    {
        final PopupContext popupContext = dialogUiFactory.createPopupContext(() -> getMainComponent());
        try
        {
            Collection<Allocatable> originals = getQuery().getTemplates();
            List<Allocatable> editableTemplates = new ArrayList<>();
            List<Allocatable> nonEditables = new ArrayList<>();
            final User user = getUser();

            for (Allocatable template : originals)
            {
                if ( permissionController.canModify( template, user))
                {
                    editableTemplates.add(template);
                }
                else
                {
                    nonEditables.add( template);
                }
            }
            Collection<Allocatable> copies = getFacade().editList(editableTemplates);
            fillModel(nonEditables,copies);
            Collection<String> options = new ArrayList<>();
            options.add(getString("apply"));
            options.add(getString("cancel"));

            final JComponent component = templateList.getComponent();
            component.setSize(1000, 800);
            final DialogInterface dlg = dialogUiFactory
                    .createContentDialog(popupContext, component, options.toArray(new String[] {}));
            dlg.setTitle(getString("edit-templates"));
            dlg.getAction(options.size() - 1).setIcon(i18n.getIcon("icon.cancel"));

            final Runnable action = new Runnable()
            {
                private static final long serialVersionUID = 1L;

                public void run()
                {
                    Collection<ReferenceInfo<Entity>> toRemoveObj = Collections.synchronizedSet(new LinkedHashSet<>());
                    for (Entity toRem : toRemove)
                    {
                        toRemoveObj.add(toRem.getReference());
                    }
                    Collection<Entity> toStoreObj = Collections.synchronizedSet(new LinkedHashSet<>());
                    toStoreObj.addAll(toStore);
                    Promise<Void> p = ResolvedPromise.VOID_PROMISE;
                    for (Allocatable template : toRemove)
                    {
                        Promise<Collection<Reservation>> reservationsPromise = getQuery().getTemplateReservations(template);
                        final Promise<Void> voidPromise = reservationsPromise.thenAccept((reservations) ->
                        {
                            for (Reservation reservation : reservations)
                            {
                                toRemoveObj.add(((Entity) reservation).getReference());
                            }
                        });

                        for ( Entity entity:toStoreObj)
                        {
                            if ( entity.getTypeClass() == Reservation.class)
                            {
                                String templateId = ((Reservation)entity).getAnnotation( RaplaObjectAnnotations.KEY_TEMPLATE);
                                if ( templateId != null && templateId.equals( template.getId()))
                                {
                                    toStoreObj.remove( entity);
                                }
                            }
                        }

                        p = p.thenCombine(voidPromise, (a, b) ->
                        {
                            return null;
                        });
                    }
                    p = p.thenCompose((a) ->
                    {
                        final Promise<Void> dispatch = getFacade().dispatch(toStoreObj, toRemoveObj);
                        return dispatch;
                    });

                    Promise<Collection<Reservation>> resPromise = p.thenCompose((a) ->
                    {
                        Allocatable selectedTemplate = templateList.getSelectedValue();
                        Promise<Collection<Reservation>> reservations = getQuery().getTemplateReservations(selectedTemplate);
                        return reservations;
                    });
                    final Promise<Void> accept = resPromise.thenAccept((reservations) ->
                    {
                        Allocatable selectedTemplate = templateList.getSelectedValue();

                        Date start = null;
                        if (selectedTemplate != null)
                        {
                            final Boolean annotation = (Boolean)selectedTemplate.getClassification().getValue(ResourceAnnotations.FIXEDTIMEANDDURATION);
                            boolean isFixedTimeAndDate = annotation != null && annotation;
                            final int size = reservations.size();
                            if ( !isFixedTimeAndDate)
                            {
                                if (size == 0)
                                {
                                    RaplaFacade facade = getFacade();
                                    final DynamicType[] dynamicTypes = facade
                                            .getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
                                    final DynamicType dynamicType = dynamicTypes[0];
                                    CalendarSelectionModel model = calendarSelectionModel;

                                    RaplaComponent.newReservation(dynamicType, user, facade, model).thenAccept((reservation)
                                            -> {
                                        reservation.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, selectedTemplate.getId());
                                        editController.edit(reservation, null);
                                    });
                                    getClientFacade().setTemplate(null);
                                }
                                else if (size == 1)
                                {
                                    final Reservation next = reservations.iterator().next();
                                    editController.edit( next, null);
                                    getClientFacade().setTemplate(null);
                                }
                                else
                                {
                                    getClientFacade().setTemplate(selectedTemplate);
                                }
                            }
                            else
                            {
                                for (Reservation r : reservations)
                                {
                                    Date firstDate = r.getFirstDate();
                                    if (start == null || firstDate.before(start))
                                    {
                                        start = firstDate;
                                    }
                                }
                                if (start != null)
                                {
                                    calendarSelectionModel.setSelectedDate(start);
                                }
                                getClientFacade().setTemplate(selectedTemplate);
                            }
                        }
                        else
                        {
                            getClientFacade().setTemplate(null);
                        }

                    }).exceptionally((ex) ->
                        dialogUiFactory.showException(ex, popupContext)
                    ).finally_(()->dlg.close());
                }
            };
            final JList list = templateList.getList();
            list.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getClickCount() >= 2 && editAction.isEnabled())
                    {
                        action.run();//actionPerformed( new ActionEvent( list, ActionEvent.ACTION_PERFORMED, "save"));
                    }
                }
            });
            editAction = dlg.getAction(0);
            editAction.setRunnable(action);
            editAction.setIcon(i18n.getIcon("icon.confirm"));
            editAction.setEnabled( false);
            dlg.start(true);
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    @SuppressWarnings("unchecked")
    public void fillModel(Collection<Allocatable> originals,Collection<Allocatable> templates) throws RaplaException
    {
        for (Allocatable template : originals)
        {
            model.addElement(template);
        }
        for (Allocatable template : templates)
        {
            model.addElement(template);
        }
        User user = getUser();
        Comparator comp = new NamedComparator(getLocale())
        {
            Permission.AccessLevel getLevel( Allocatable allocatable)
            {
                if ( allocatable == null)
                {
                    return Permission.AccessLevel.DENIED;
                }
                if ( permissionController.canAdmin( allocatable, user))
                {
                    return Permission.AccessLevel.ADMIN;
                }
                else if (permissionController.canModify( allocatable, user))
                {
                    return Permission.AccessLevel.EDIT;
                }
                else
                {
                    return Permission.AccessLevel.READ;
                }
            }
            @Override
            public int compare(Named o1, Named o2)
            {
                Permission.AccessLevel accessLevel = getLevel((Allocatable) o1);
                Permission.AccessLevel accessLevel2 = getLevel((Allocatable) o2);
                final int compare = accessLevel.compareTo(accessLevel2);
                if ( compare != 0)
                {
                    return compare;
                }
                return super.compare(o1, o2);
            }
        };
        SortedListModel sortedModel = new SortedListModel(model, SortedListModel.SortOrder.ASCENDING, comp);
        templateList.getList().setModel(sortedModel);
    }
}
