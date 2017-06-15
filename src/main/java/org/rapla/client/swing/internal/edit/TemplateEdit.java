package org.rapla.client.swing.internal.edit;

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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.google.gwt.logging.client.DefaultLevel;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.gwt.Rapla;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.RaplaListEdit.NameProvider;
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
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

public class TemplateEdit extends RaplaGUIComponent
{
    RaplaListEdit<Allocatable> templateList;
    DefaultListModel model = new DefaultListModel();
    AllocatableEditUI allocatableEdit;
    Collection<Entity> toStore = new LinkedHashSet<Entity>();
    Collection<Allocatable> toRemove = new LinkedHashSet<Allocatable>();
    private final CalendarSelectionModel calendarSelectionModel;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final PermissionController permissionController;
    private final EditController editController;
    private DialogInterface.DialogAction editAction;

    private TemplateEdit(final ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarSelectionModel calendarSelectionModel,
            RaplaImages raplaImages, final DialogUiFactoryInterface dialogUiFactory, ClassificationFieldFactory classificationFieldFactory,
            PermissionListFieldFactory permissionListFieldFactory, RaplaListEditFactory raplaListEditFactory, BooleanFieldFactory booleanFieldFactory,
            EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.calendarSelectionModel = calendarSelectionModel;
        this.raplaImages = raplaImages;
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

        ActionListener callback = new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent evt)
            {
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
                            copyTemplate( template);
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
            }
        };
        final ReferenceInfo<User> userReference = getUser().getReference();
        templateList = raplaListEditFactory.create(i18n, allocatableEdit.getComponent(), callback, true);
        templateList.setNameProvider(new NameProvider<Allocatable>()
        {
            @Override
            public String getName(Allocatable object)
            {
                return object.getName(getLocale());
            }
        });
        templateList.getList().addListSelectionListener(new ListSelectionListener()
        {
            @Override
            public void valueChanged(ListSelectionEvent e)
            {
                final Allocatable selectedValue = templateList.getSelectedValue();
                User user = null;
                try
                {
                    user = getUser();
                    editAction.setEnabled( selectedValue != null && permissionController.canModify( selectedValue, user));
                }
                catch (RaplaException e1)
                {
                    logger.error( e1.getMessage(), e1);
                }

            }
        });
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
                    if (ownerRef != null && !ownerRef.equals(userReference))
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

    Map<ReferenceInfo<User>, String> usernameMap = new HashMap<ReferenceInfo<User>, String>();

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
        Collection<Allocatable> templates = new LinkedHashSet<Allocatable>(getQuery().getTemplates());
        Collection<String> templateNames = new LinkedHashSet<String>();
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

    private void copyTemplate(Allocatable originalTemplate) throws RaplaException
    {
        final User user = getUser();
        final Allocatable templateCopy = getFacade().clone(originalTemplate, user);
        final String name = (String)templateCopy.getClassification().getValue("name");
        String newName = name != null ? (name + " (Kopie)") :getNewTemplateName();
        templateCopy.getClassification().setValue("name", newName);
        toStore.add(templateCopy);
         getQuery().getTemplateReservations(originalTemplate).
        thenAccept(( reservations ->
        {

            for ( Reservation reservation:reservations)
            {
                final Reservation clone = getFacade().clone(reservation, user);
                clone.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, templateCopy.getId());
                toStore.add( clone);
            }
            model.addElement(templateCopy);
            boolean shouldScroll = true;
            templateList.getList().clearSelection();
            templateList.getList().setSelectedValue(templateCopy, shouldScroll);
        }));



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
        Collection<Permission> permissionList = new ArrayList<Permission>(template.getPermissionList());
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
        final Component parentComponent = getMainComponent();
        try
        {
            Collection<Allocatable> originals = getQuery().getTemplates();
            List<Allocatable> editableTemplates = new ArrayList<Allocatable>();
            List<Allocatable> nonEditables = new ArrayList<Allocatable>();
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
            Collection<Allocatable> copies = getFacade().edit(editableTemplates);
            fillModel(nonEditables,copies);
            Collection<String> options = new ArrayList<String>();
            options.add(getString("apply"));
            options.add(getString("cancel"));

            final DialogInterface dlg = dialogUiFactory
                    .create(new SwingPopupContext(parentComponent, null), false, templateList.getComponent(), options.toArray(new String[] {}));
            dlg.setTitle(getString("edit-templates"));
            dlg.setSize(1000, 800);
            dlg.getAction(options.size() - 1).setIcon("icon.cancel");

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
                            boolean isOnlySingle = annotation != null && annotation;
                            final int size = reservations.size();
                            if ( !isOnlySingle)
                            {
                                if (size == 1)
                                {
                                    final Reservation next = reservations.iterator().next();
                                    editController.edit( next, null);
                                }
                            }
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
                            }
                        }
                        getUpdateModule().setTemplate(selectedTemplate);
                    }).whenComplete((a, ex) ->
                    {
                        if (ex != null)
                        {
                            dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
                        }
                        dlg.close();
                    });
                }
            };
            final JList list = templateList.getList();
            list.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getClickCount() >= 2)
                    {
                        action.run();//actionPerformed( new ActionEvent( list, ActionEvent.ACTION_PERFORMED, "save"));
                    }
                }
            });
            editAction = dlg.getAction(0);
            editAction.setRunnable(action);
            editAction.setIcon("icon.confirm");
            editAction.setEnabled( false);
            dlg.start(true);
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(parentComponent, null));
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

    @Singleton
    public static class TemplateEditFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final CalendarSelectionModel calendarSelectionModel;
        private final RaplaImages raplaImages;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final ClassificationFieldFactory classificationFieldFactory;
        private final PermissionListFieldFactory permissionListFieldFactory;
        private final RaplaListEditFactory raplaListEditFactory;
        private final BooleanFieldFactory booleanFieldFactory;
        private final PermissionController permissionController;
        private final EditController editController;

        @Inject
        public TemplateEditFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
                CalendarSelectionModel calendarSelectionModel, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory,
                ClassificationFieldFactory classificationFieldFactory, PermissionListFieldFactory permissionListFieldFactory,
                RaplaListEditFactory raplaListEditFactory, BooleanFieldFactory booleanFieldFactory, EditController editController)
        {
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.calendarSelectionModel = calendarSelectionModel;
            this.raplaImages = raplaImages;
            this.dialogUiFactory = dialogUiFactory;
            this.classificationFieldFactory = classificationFieldFactory;
            this.permissionListFieldFactory = permissionListFieldFactory;
            this.raplaListEditFactory = raplaListEditFactory;
            this.booleanFieldFactory = booleanFieldFactory;
            this.permissionController = facade.getRaplaFacade().getPermissionController();
            this.editController = editController;
        }

        public TemplateEdit create() throws RaplaException
        {
            return new TemplateEdit(facade, i18n, raplaLocale, logger, calendarSelectionModel, raplaImages, dialogUiFactory, classificationFieldFactory,
                    permissionListFieldFactory, raplaListEditFactory, booleanFieldFactory, editController);
        }
    }

}
