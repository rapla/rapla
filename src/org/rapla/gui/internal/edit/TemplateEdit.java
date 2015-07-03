package org.rapla.gui.internal.edit;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.RaplaListEdit.NameProvider;
import org.rapla.gui.internal.edit.reservation.SortedListModel;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.storage.StorageOperator;

public class TemplateEdit extends RaplaGUIComponent 
{
    RaplaListEdit<Allocatable> templateList;
    DefaultListModel model = new DefaultListModel();
    AllocatableEditUI allocatableEdit;
    Collection<Allocatable> toStore = new LinkedHashSet<Allocatable>();
    Collection<Allocatable> toRemove = new LinkedHashSet<Allocatable>();
    
    
    public TemplateEdit(RaplaContext context) throws RaplaException {
        super(context);
        I18nBundle i18n = getI18n();
        boolean internal = true;
        allocatableEdit = new AllocatableEditUI(context, internal)
        {
            protected void mapFromObjects() throws RaplaException {
                super.mapFromObjects();
                permissionField.setPermissionLevels(  Permission.READ,  Permission.EDIT);
                classificationField.setScrollingAlwaysEnabled( false);
            }
            
            @Override
            public void stateChanged(ChangeEvent evt) {
                try {
                    allocatableEdit.mapToObjects();
                    List<Allocatable> objects = allocatableEdit.getObjects();
                    toStore.addAll( objects);
                } catch (RaplaException e) {
                    getLogger().error( e.getMessage(), e);
                }
          }  
        };
        
        
        ActionListener callback = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent evt) {
                //int index = getSelectedIndex();
                try {
                    if (evt.getActionCommand().equals("remove")) {
                        removeTemplate();
                    } else if (evt.getActionCommand().equals("new")) {
                        createTemplate();
                    } else if (evt.getActionCommand().equals("edit")) {
                        Allocatable template = templateList.getSelectedValue();
                        List<Allocatable> list;
                        if (template != null)
                        {
                            list= Collections.singletonList( template);
                        } else {
                            list = Collections.emptyList();
                        }
                        allocatableEdit.setObjects( list);
                        allocatableEdit.mapFromObjects();
                    }

                } catch (RaplaException ex) {
                    showException(ex, templateList.getComponent());
                }
            }
        };
        templateList = new RaplaListEdit<Allocatable>(i18n, allocatableEdit.getComponent(), callback);
        templateList.setNameProvider( new NameProvider<Allocatable>()
                {

                    @Override
                    public String getName(Allocatable object) {
                        return object.getName(getLocale());
                    }
                }
                );
        templateList.getList().setSelectionMode( ListSelectionModel.SINGLE_SELECTION);
        templateList.setMoveButtonVisible( false );
        templateList.getComponent().setPreferredSize( new Dimension(1000, 500));
    }

    public String getNewTemplateName() throws RaplaException {
        Collection<Allocatable> templates = new LinkedHashSet<Allocatable>(getQuery().getTemplates());
        Collection<String> templateNames= new LinkedHashSet<String>();
        Locale locale = getLocale();
        for ( Allocatable template:templates)
        {
            templateNames.add( template.getName(locale));
        }
        for ( int i= 0;i<model.size();i++)
        {
            Allocatable template = (Allocatable) model.get( i);
            templateNames.add( template.getName(locale));
        }
        int index = 0;
        String username = getUser().getUsername();
        while ( true)
        {
            String indexStr = username + (index == 0 ? "" : " "+ index);
            String newEvent = getI18n().format("new_reservation.format", indexStr);
            if ( !templateNames.contains( newEvent))
            {
                return newEvent;
            }
            index++;
        }
    }

    private void removeTemplate() 
    {
        Allocatable template =  templateList.getSelectedValue();
        if ( template != null)
        {
            toRemove.add( template);
            model.removeElement( template);
        }        
    }
    
    @SuppressWarnings("unchecked")
    private void createTemplate() throws RaplaException {
        String name = getNewTemplateName();
        DynamicType dynamicType = getQuery().getDynamicType( StorageOperator.RAPLA_TEMPLATE);
        Classification newClassification = dynamicType.newClassification();
        newClassification.setValue("name", name);
        Allocatable template = getModification().newAllocatable( newClassification);
        Collection<Permission> permissionList = new ArrayList<Permission>(template.getPermissionList());
        for ( Permission permission: permissionList)
        {
            template.removePermission(permission);
        }
        toStore.add( template);
        model.addElement( template);
        boolean shouldScroll = true;
        templateList.getList().clearSelection();
        templateList.getList().setSelectedValue(  template ,shouldScroll );
    }

    public void startTemplateEdit() {
        final Component parentComponent = getMainComponent();
        try {
            
            Collection<Allocatable> originals = getQuery().getTemplates();
            List<Allocatable> editableTemplates = new ArrayList<Allocatable>();
            for ( Allocatable template:originals)
            {
                if ( canModify( template))
                {
                    editableTemplates.add( template);
                }
            }
            Collection<Allocatable> copies = getModification().edit( editableTemplates);
            fillModel(copies);
            
            Collection<String> options = new ArrayList<String>();
            options.add( getString("apply") );
            options.add(getString("cancel"));
            final DialogUI dlg = DialogUI.create(
                    getContext(),
                    parentComponent,true,templateList.getComponent(),
                    options.toArray(new String[] {}));
            dlg.setTitle(getString("edit-templates"));
            dlg.getButton(options.size() - 1).setIcon(getIcon("icon.cancel"));

            final AbstractAction action = new AbstractAction() {
                private static final long serialVersionUID = 1L;
    
                public void actionPerformed(ActionEvent e) {
                    try
                    {
                        LinkedHashSet<RaplaObject> toRemoveObj = new LinkedHashSet<RaplaObject>();
                        toRemoveObj.addAll( toRemove);
                        LinkedHashSet<RaplaObject> toStoreObj = new LinkedHashSet<RaplaObject>();
                        toStoreObj.addAll( toStore);
                        for ( Allocatable template:toRemove)
                        {
                            Collection<Reservation> reservations = getQuery().getTemplateReservations(template);
                            toRemoveObj.addAll( reservations);
                        }
              
                        //Collection<Allocatable> newEntity = Collections.singleton( template);
                        //Collection<Allocatable> originalEntity = null;
                        //SaveUndo<Allocatable> cmd = new SaveUndo<Allocatable>(getContext(), newEntity, originalEntity);
                        //getModification().getCommandHistory().storeAndExecute( cmd);
                        
                        Entity[] storedObjects = toStoreObj.toArray(Entity.ENTITY_ARRAY); 
                        Entity[] removedObjects = toRemoveObj.toArray(Entity.ENTITY_ARRAY);
                        // FIXME Implement as undo
                        getModification().storeAndRemove(storedObjects, removedObjects);
              
                        Allocatable selectedTemplate = templateList.getSelectedValue();
                        Date start = null;
                        
                        if ( selectedTemplate != null)
                        {
                            Collection<Reservation> reservations = getQuery().getTemplateReservations(selectedTemplate);
                            for ( Reservation r:reservations)
                            {
                                Date firstDate = r.getFirstDate();
                                if ( start == null || firstDate.before(start ))
                                {
                                    start = firstDate;
                                }
                            }
                            if ( start != null)
                            {
                                getService(CalendarSelectionModel.class).setSelectedDate( start);
                            }
                        }
                        getModification().setTemplate( selectedTemplate);
                    }
                    catch (RaplaException ex)
                    {
                        showException( ex, getMainComponent());
                    }
                    dlg.close();
                }
            };
            final JList list = templateList.getList();
            list.addMouseListener( new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if ( e.getClickCount() >=2)
                    {
                        action.actionPerformed( new ActionEvent( list, ActionEvent.ACTION_PERFORMED, "save"));
                    }
                }
            });
            dlg.getButton(0).setAction( action);
            dlg.getButton(0).setIcon(getIcon("icon.confirm"));
            dlg.start();
        } catch (RaplaException ex) {
            showException( ex, parentComponent);
        }
    }


    @SuppressWarnings("unchecked")
    public void fillModel(Collection<Allocatable> templates) {
        for ( Allocatable template:templates)
        {
            model.addElement( template);
        }
        Comparator comp = new NamedComparator(getLocale());
        SortedListModel sortedModel = new SortedListModel(model, SortedListModel.SortOrder.ASCENDING,comp );
        templateList.getList().setModel( sortedModel );
    }
    
   
    
 
}
