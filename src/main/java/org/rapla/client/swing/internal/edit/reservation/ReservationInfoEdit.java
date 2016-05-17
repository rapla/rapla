/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.client.swing.internal.edit.ClassificationEditUI;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.swing.internal.edit.fields.PermissionListField.PermissionListFieldFactory;
import org.rapla.client.swing.internal.edit.fields.SetGetField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.toolkit.EmptyLineBorder;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaListComboBox;
import org.rapla.client.RaplaWidget;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
/**
   Gui for editing the {@link Classification} of a reservation. Same as
   {@link org.rapla.client.swing.internal.edit.ClassificationEditUI}. It will only layout the
   field with a {@link java.awt.FlowLayout}.
 */
public class ReservationInfoEdit extends RaplaGUIComponent
    implements
        RaplaWidget
        ,ActionListener
        ,ChangeListener
{
    JPanel content = new JPanel();
    MyClassificationEditUI editUI;
    PermissionListField permissionListField;
    
    private Classification classification;
    private Classification lastClassification = null;
    private Classifiable classifiable;
    private CommandHistory commandHistory;
    
    ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();
    ArrayList<DetailListener> detailListenerList = new ArrayList<DetailListener>();
    RaplaListComboBox typeSelector;
    RaplaButton tabSelector = new RaplaButton();
//    RaplaButton permissionButton = new RaplaButton();
    private boolean internalUpdate = false;
    enum TabSelected
    {
        Main,
        Info
    }
    
    TabSelected selectedView = TabSelected.Main;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;

    public ReservationInfoEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,  CommandHistory commandHistory, RaplaImages raplaImages, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory, PermissionListFieldFactory permissionListFieldFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory, LongFieldFactory longFieldFactory) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        typeSelector = new RaplaListComboBox( raplaLocale );
        this.commandHistory = commandHistory;
        editUI = new MyClassificationEditUI(facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
        this.permissionListField = permissionListFieldFactory.create("permissions");
        this.permissionListField.setPermissionLevels(Permission.DENIED, Permission.READ,Permission.EDIT, Permission.ADMIN);
        this.permissionListField.setDefaultAccessLevel( Permission.READ );
    }
    
    public JComponent getComponent() {
        return content;
    }


    public void requestFocus() {
        editUI.requestFocus();
    }

//    private boolean hasSecondTab(Classification classification) {
//        Attribute[] atts = classification.getAttributes();
//        for ( int i=0; i < atts.length; i++ ) {
//            String view = atts[i].getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW,AttributeAnnotations.VALUE_EDIT_VIEW_MAIN);
//            if ( view.equals(AttributeAnnotations.VALUE_EDIT_VIEW_ADDITIONAL)) {
//                return true;
//            }
//        }
//        return false;
//    }
    JScrollPane scrollPane;
    public void setReservation(Classifiable classifiable) throws RaplaException {
        content.removeAll();
        this.classifiable = classifiable;
        classification = classifiable.getClassification();
        lastClassification = classification;

        DynamicType[] types = getQuery().getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        DynamicType dynamicType = classification.getType();
        List<DynamicType> creatableTypes = new ArrayList<DynamicType>();
        User user = getUser();
        PermissionController permissionController = getFacade().getPermissionController();
        for ( DynamicType type: types)
        {
            if (permissionController.canCreate(type, user))
                creatableTypes.add( type );
            
        }
        RaplaListComboBox jComboBox =  new RaplaListComboBox( getRaplaLocale(), creatableTypes.toArray());
		typeSelector =  jComboBox;
        typeSelector.setEnabled( creatableTypes.size() > 1);
        typeSelector.setSelectedItem(dynamicType);
        typeSelector.setEnabled(!canNotWriteOneAttribute());
        setRenderer();


        content.setLayout( new BorderLayout());
        JPanel header = new JPanel();
        header.setLayout( null );
        header.add( typeSelector );
        header.add( tabSelector );
  //      header.add( permissionButton );
        
        header.setBorder(  BorderFactory.createTitledBorder( new EmptyLineBorder(), getString("reservation_type") +":"));
        Dimension dim = typeSelector.getPreferredSize();
        typeSelector.setBounds(135,0, dim.width,dim.height);
        
//        permissionButton.setText(getString("permissions"));
//        Dimension dim2 = permissionButton.getPreferredSize();
//        permissionButton.setBounds(145 + dim.width,0, dim2.width,dim2.height);

        tabSelector.setText(getInfoButton() );
        Dimension dim3 = tabSelector.getPreferredSize();
        tabSelector.setBounds(155 + dim.width  ,0,dim3.width,dim3.height);
        
        header.setPreferredSize( new Dimension(600, Math.max(dim.height, dim3.height)));
        content.add( header,BorderLayout.NORTH);
        content.add( editUI.getComponent(),BorderLayout.CENTER);
        content.add( permissionListField.getComponent(), BorderLayout.SOUTH);
        updatePermissionFieldVisiblity();
        //tabSelector.setVisible( hasSecondTab( classification ) || selectedView == TabSelected.Info);
        editUI.setObjects( Collections.singletonList(classification ));
        permissionListField.mapFrom( Collections.singletonList((Reservation) classifiable ));
        
        permissionListField.addChangeListener( this);
        editUI.getComponent().validate();
        typeSelector.addActionListener( this );
        tabSelector.addActionListener( this );
        updateHeight();
        content.validate();
        
        
    }

    private boolean canNotWriteOneAttribute() throws RaplaException
    {
        final PermissionController permissionController = getClientFacade().getRaplaFacade().getPermissionController();
        final User user = getUser();
        final Attribute[] attributes = this.classification.getAttributes();
        for (Attribute attribute : attributes)
        {
            if(!permissionController.canWrite(classification, attribute, user))
            {
                return true;
            }
        }
        return false;
    }

    private void updatePermissionFieldVisiblity() throws RaplaException {
        PermissionController permissionController = getFacade().getPermissionController();
        final User user = getUser();
        boolean canAdmin = permissionController.canAdmin((Reservation) classifiable, user);
        permissionListField.getComponent().setVisible( selectedView == TabSelected.Info && canAdmin);
    }

    private String getInfoButton() {
        return getString("additional-view") + " / " +getString("permissions");
    }

	@SuppressWarnings("unchecked")
	private void setRenderer() {
		typeSelector.setRenderer(new NamedListCellRenderer(getI18n().getLocale()));
	}

    /** registers new ChangeListener for this component.
     *  An ChangeEvent will be fired to every registered ChangeListener
     *  when the info changes.
     * @see javax.swing.event.ChangeListener
     * @see javax.swing.event.ChangeEvent
    */
    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    public void addDetailListener(DetailListener listener) {
        detailListenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeDetailListener(DetailListener listener) {
        detailListenerList.remove(listener);
    }

    public DetailListener[] getDetailListeners() {
        return detailListenerList.toArray(new DetailListener[]{});
    }

    protected void fireDetailChanged() {
        DetailListener[] listeners = getDetailListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].detailChanged();
        }
    }

    public interface DetailListener {
    	void detailChanged();
    }

    protected void fireInfoChanged() {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    }
    
    public void stateChanged(ChangeEvent evt) {
        if ( evt.getSource() != permissionListField)
        {
            return;
        }
        if (internalUpdate) return;
        try {
            PermissionContainer permissionContainer = (PermissionContainer)classifiable;
            Collection<Permission> oldPermissions = permissionContainer.getPermissionList();
            Collection<Permission> newPermissions = permissionListField.getPermissionList();
            if ( PermissionContainer.Util.differs( oldPermissions, newPermissions)) {
                UndoPermissionChange permissionChange = new UndoPermissionChange(oldPermissions, newPermissions);
                commandHistory.storeAndExecute(permissionChange);   
            }
        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(this.getComponent(), null));
        }
    }

    // The DynamicType has changed
    public void actionPerformed(ActionEvent event) {
        try {
            Object source = event.getSource();
            
            if (source == typeSelector ) {
    	        if (internalUpdate) return;
    	        
    			DynamicType oldDynamicType       = lastClassification.getType();
    			DynamicType newDynamicType       = (DynamicType) typeSelector.getSelectedItem();
    			Classification oldClassification = ((ClassificationImpl) lastClassification).clone();
    			Classification newClassification = ((ClassificationImpl) newDynamicType.newClassification(classification)).clone();
    	        
            	UndoReservationTypeChange command = new UndoReservationTypeChange(oldClassification, newClassification, oldDynamicType, newDynamicType);
            	commandHistory.storeAndExecute(command);
            	
            	lastClassification = newClassification;
            }
            
            if (source == tabSelector ) {
                boolean tabSelected = selectedView == TabSelected.Info;
                selectedView = tabSelected ? TabSelected.Main : TabSelected.Info;
                editUI.setSelectedView( tabSelected ? AttributeAnnotations.VALUE_EDIT_VIEW_MAIN : AttributeAnnotations.VALUE_EDIT_VIEW_ADDITIONAL);
                fireDetailChanged();
                if ( selectedView == TabSelected.Info )
                {
                    //editUI.getComponent().setSize( new Dimension( 600,500));
                    content.remove( editUI.getComponent());
                    if ( scrollPane == null)
                    {
                        scrollPane = new JScrollPane(editUI.getComponent());
                    }
                    else
                    {
                        scrollPane.setViewportView( editUI.getComponent());
                    }
                    content.add( scrollPane, BorderLayout.CENTER);
                }
                else
                {
                    if ( scrollPane != null)
                    {
                        content.remove( scrollPane);
                    }
                    content.add( editUI.getComponent(), BorderLayout.CENTER);
                }
                editUI.layout();
                if ( selectedView == TabSelected.Info )
                {
                    editUI.getComponent().setPreferredSize(new Dimension( 600,500));
                }
                else
                {
                    int newHeight = editUI.getHeight();
                //scrollPane.setPreferredSize(new Dimension(600,newHeight));
                    editUI.getComponent().setPreferredSize(new Dimension(600,newHeight));
                }
                updatePermissionFieldVisiblity();
                content.validate();
                tabSelector.setText( tabSelected ?
                        getInfoButton()
                        :getString("appointments")
                        );
                tabSelector.setIcon( tabSelected ?
                        null
                        : raplaImages.getIconFromKey("icon.list")
                        );
                
            }
//            if (source == permissionButton ) {
//                boolean tabSelected = selectedView == TabSelected.Permission;
//                selectedView = tabSelected ? TabSelected.Main : TabSelected.Permission;
//                //isMainViewSelected = !isMainViewSelected;
//                fireDetailChanged();
//                editUI.layout();
//                tabSelector.setText( tabSelected ?
//                        getString("permissions")
//                        :getString("appointments")
//                        );
//                tabSelector.setIcon( tabSelected ?
//                        null
//                        : raplaImages.getIconFromKey("icon.list")
//                        );
//            }

        } catch (RaplaException ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(content, null));
        }
    }

    private void updateHeight()
    {
        if ( selectedView == TabSelected.Main ) {
            int newHeight = editUI.getHeight();
            //scrollPane.setPreferredSize(new Dimension(600,newHeight));
            editUI.getComponent().setPreferredSize(new Dimension(600,newHeight));
        }
        else
        {
         //   editUI.getComponent().setPreferredSize(null);
        }
    }
    

    class MyClassificationEditUI extends ClassificationEditUI {
        int height  = 0;
        public MyClassificationEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory, LongFieldFactory longFieldFactory) {
            super(facade, i18n, raplaLocale, logger, treeFactory, raplaImages, dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
        }

        public int getHeight()
        {
            return height;
        }
        
        protected void layout() {
            editPanel.removeAll();
            editPanel.setLayout( null );
            if ( selectedView == TabSelected.Info ) {
                super.layout();
                return;
            }
            /*
            FlowLayout layout = new FlowLayout();
            layout.setAlignment(FlowLayout.LEFT);
            layout.setHgap(10);
            layout.setVgap(2);
            editPanel.setLayout(layout);
            for (int i=0;i<fields.length;i++) {
                String tabview = getAttribute( i ).getAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_MAIN_VIEW);
                JPanel fieldPanel = new JPanel();
                fieldPanel.setLayout( new BorderLayout());
                fieldPanel.add(new JLabel(fields[i].getName() + ": "),BorderLayout.WEST);
                fieldPanel.add(fields[i].getComponent(),BorderLayout.CENTER);
                if ( tabview.equals("main-view") || !isMainViewSelected ) {
                    editPanel.add(fieldPanel);
                }
            }
            */
            TableLayout layout = new TableLayout();
            
            layout.insertColumn(0, 5);
            layout.insertColumn(1,TableLayout.PREFERRED);
            layout.insertColumn(2,TableLayout.PREFERRED);
            layout.insertColumn(3, 10);
            layout.insertColumn(4,TableLayout.PREFERRED);
            layout.insertColumn(5,TableLayout.PREFERRED);
            layout.insertColumn(6,TableLayout.FULL);
            
            int col= 1;
            int row = 0;
            layout.insertRow( row, 8);   
            row ++;
            layout.insertRow( row, TableLayout.PREFERRED);   
            editPanel.setLayout(layout);
            height = 10;
            int maxCompHeightInRow = 0;
            for (int i=0;i<fields.size();i++) {
                EditField field = fields.get(i);
                final Attribute attribute = getAttribute( i );
                if ( !super.isVisible(attribute)) {
                    continue;
                }
                editPanel.add(new JLabel(getFieldName(field) + ": "),col + "," + row +",l,c");
                col ++;
                editPanel.add(field.getComponent(), col + "," + row +",f,c");
                int compHeight = (int)field.getComponent().getPreferredSize().getHeight();
                compHeight =  Math.max(25, compHeight);
                // first row
                
                maxCompHeightInRow = Math.max(maxCompHeightInRow ,compHeight);
                
                col ++;
                col ++;
                if ( col >= layout.getNumColumn())
                {
                    col = 1;
                    if ( i < fields.size() -1)
                    {
                        row ++;
                        layout.insertRow( row, 5);
                        height +=5;
                        row ++;
                        layout.insertRow( row, TableLayout.PREFERRED);
                        height += maxCompHeightInRow;
                        maxCompHeightInRow = 0;    
                    }
                }
            }
            height += maxCompHeightInRow;
            
        }

        public void requestFocus() {
            if (fields.size()>0)
                fields.get(0).getComponent().requestFocus();
        }
        
        @Override
        protected boolean isVisible(Attribute attribute)
        {
            return true;
        }
        

        public void stateChanged(ChangeEvent evt) {
            try {
            	SetGetField<?> editField = (SetGetField<?>) evt.getSource();
            	String keyName   = getKey(editField);
            	Object oldValue = getAttValue(keyName); //this.classification.getValue(keyName);
            	mapTo( editField );
                Object newValue = getAttValue(keyName);
                if ( keyName.equals("permission_modify"))
                {
                    
                }
                UndoClassificationChange classificationChange = new UndoClassificationChange(oldValue, newValue, keyName);
                if (oldValue != newValue && (oldValue == null || newValue == null || !oldValue.equals(newValue))) {
                    commandHistory.storeAndExecute(classificationChange);	
                }
            } catch (RaplaException ex) {
                dialogUiFactory.showException(ex, new SwingPopupContext(this.getComponent(), null));
            }
        }
        
        
        private Object getAttValue(String keyName) 
        {
            Set<Object> uniqueAttValues = getUniqueAttValues(keyName);
            if ( uniqueAttValues.size() > 0)
            {
                return uniqueAttValues.iterator().next();
            }
            return null;
        }


        /**
         * This class collects any information of changes done to all fields at the top of the edit view.
         * This is where undo/redo for all fields at the top of the edit view
         * is realized. 
         * @author Jens Fritz
         *
         */
        
        //Erstellt von Dominik Krickl-Vorreiter
        public class UndoClassificationChange implements CommandUndo<RaplaException> {

        	private final Object oldValue;
        	private final Object newValue;
        	private final String keyName;
        	
        	public UndoClassificationChange(Object oldValue, Object newValue, String fieldName) {
        		this.oldValue = oldValue;
        		this.newValue = newValue;
        		
        		this.keyName   = fieldName;
        	}
        	
			public Promise<Void> execute() {
				return mapValue(newValue);
			}
			
			public Promise<Void> undo()  {
				return mapValue(oldValue);
			}

			protected Promise<Void> mapValue(Object valueToSet)  {
                try
                {
                    Object attValue = getAttValue(keyName);
                    if (attValue != valueToSet && (attValue == null || valueToSet == null || !attValue.equals(valueToSet)))
                    {
                        SetGetField<?> editField = (SetGetField<?>) getEditField();

                        if (editField == null)
                            throw new RaplaException("Field with key " + keyName + " not found!");

                        setAttValue(keyName, valueToSet);
                        mapFrom(editField);
                    }
                    fireInfoChanged();
                    return ResolvedPromise.VOID_PROMISE;
                }
                catch ( RaplaException ex)
                {
                    return new ResolvedPromise<Void>(ex);
                }
			}

			protected EditField getEditField() {
				EditField editField = null;
				
				for (EditField field: editUI.fields) {
					if (getKey(field).equals(keyName)) {
						editField = field;
						break;
					}
				}
				return editField;
			}

			public String getCommandoName() 
			{
				EditField editField = getEditField();
				String fieldName;
				if ( editField != null)
				{
					fieldName = getFieldName(editField);
				}
				else
				{
					fieldName = getString("attribute");
				}
				return getString("change") + " " + fieldName;
			}
			
        }
        
        
    }

    public boolean isMainView() {
        return selectedView == TabSelected.Main;
    }

    
    /**
     * This class collects any information of changes done to the reservation type checkbox.
     * This is where undo/redo for the reservatoin type-selection at the top of the edit view
	 * is realized 
     * @author Jens Fritz
     *
     */
    //Erstellt von Matthias Both
	public class UndoReservationTypeChange implements CommandUndo<RaplaException>{
		private final Classification oldClassification;
		private final Classification newClassification;
		private final DynamicType oldDynamicType;
		private final DynamicType newDynamicType;
		
		public UndoReservationTypeChange(Classification oldClassification, Classification newClassification, DynamicType oldDynamicType, DynamicType newDynamicType) {
        	this.oldDynamicType    = oldDynamicType;
			this.newDynamicType    = newDynamicType;
			
        	this.oldClassification = oldClassification;
        	this.newClassification = newClassification;
		}
		
		public Promise<Void> execute() {
	        classification = newClassification;
			return setType(newDynamicType);
		}
		
		public Promise<Void> undo()   {
	        classification = oldClassification;
			return setType(oldDynamicType);
		}

		protected Promise<Void> setType(DynamicType typeToSet) {
			if (!typeSelector.getSelectedItem().equals(typeToSet)) {
		        internalUpdate = true;
		        try {
		        	typeSelector.setSelectedItem(typeToSet);
		        } finally {
		        	internalUpdate = false;
		        }
	        }
	        
	        classifiable.setClassification(classification);
            try
            {
                editUI.setObjects(Collections.singletonList(classification));
                permissionListField.getComponent().setVisible(selectedView == TabSelected.Info);
                //tabSelector.setVisible(hasSecondTab(classification) || selectedView == TabSelected.Info);
                editUI.layout();
                editUI.getComponent().invalidate();
                content.validate();
                updateHeight();
                content.repaint();
                fireInfoChanged();
                return ResolvedPromise.VOID_PROMISE;
            }
            catch (RaplaException ex)
            {
                return new ResolvedPromise<Void>(ex);
            }
		}
	
		public String getCommandoName() 
		{
			return getString("change") + " " + getString("dynamictype");
		}
	
	}
	
	public class UndoPermissionChange implements CommandUndo<RaplaException>{
        private final Collection<Permission> oldPermissions;
        private final Collection<Permission> newPermissions;
        boolean first =true;
        
        public UndoPermissionChange(Collection<Permission> oldPermissions, Collection<Permission> newPermissions) {
            
            this.oldPermissions = fillWithClones( oldPermissions);
            
            this.newPermissions = fillWithClones(newPermissions);
        }
        
        private Collection<Permission> fillWithClones(Collection<Permission> oldPermissions2) {
            ArrayList<Permission> test = new ArrayList<Permission>();
            for ( Permission p:oldPermissions2)
            {
                test.add( ((PermissionImpl)p).clone() );
            }
            return test;
        }

        public Promise<Void> execute()  {
            return setPermissions(newPermissions);
        }
        
        public Promise<Void> undo()   {
            return setPermissions(oldPermissions);
        }

        protected Promise<Void> setPermissions(Collection<Permission> permissions) {
            
            PermissionContainer permissionContainer = (PermissionContainer) classifiable;
            internalUpdate = true;
            try {
                PermissionContainer.Util.replace( permissionContainer, permissions);
                if (first)
                {
                    first = false;
                }
                else
                {
                    permissionListField.mapFrom( Collections.singletonList( permissionContainer));
                }
                permissionListField.getComponent().setVisible( selectedView == TabSelected.Info);
                
                
                //tabSelector.setVisible(hasSecondTab(classification) || selectedView == TabSelected.Info);
                content.validate();
                updateHeight();
                content.repaint();
            } finally {
                internalUpdate = false;
            }
            fireInfoChanged();
            return ResolvedPromise.VOID_PROMISE;
        }
    
        public String getCommandoName() 
        {
            return getString("change") + " " + getString("permissions");
        }
    
    }

    @Singleton
    public static class ReservationInfoEditFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final TreeFactory treeFactory;
        private final RaplaImages raplaImages;
        private final DateFieldFactory dateFieldFactory;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final PermissionListFieldFactory permissionListFieldFactory;
        private final BooleanFieldFactory booleanFieldFactory;
        private final TextFieldFactory textFieldFactory;
        private final LongFieldFactory longFieldFactory;

        @Inject
        public ReservationInfoEditFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,
                RaplaImages raplaImages, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory,
                PermissionListFieldFactory permissionListFieldFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory,
                LongFieldFactory longFieldFactory)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.treeFactory = treeFactory;
            this.raplaImages = raplaImages;
            this.dateFieldFactory = dateFieldFactory;
            this.dialogUiFactory = dialogUiFactory;
            this.permissionListFieldFactory = permissionListFieldFactory;
            this.booleanFieldFactory = booleanFieldFactory;
            this.textFieldFactory = textFieldFactory;
            this.longFieldFactory = longFieldFactory;
        }

        public ReservationInfoEdit create(CommandHistory commandHistory) throws RaplaException
        {
            return new ReservationInfoEdit(facade, i18n, raplaLocale, logger, treeFactory, commandHistory, raplaImages, dateFieldFactory,
                    dialogUiFactory, permissionListFieldFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
        }
    }
}
