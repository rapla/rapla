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
package org.rapla.client.swing.internal.edit.fields;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.client.swing.internal.edit.ClassificationEditUI;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaListComboBox;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/****************************************************************
 * This is the base-class for all Classification-Panels         *
 ****************************************************************/
public  class  ClassificationField<T extends Classifiable> extends AbstractEditField
    implements EditFieldWithLayout,
            ActionListener
{
	JPanel content = new JPanel();
	RaplaListComboBox typeSelector;
	ClassificationEditUI editUI;

	DynamicType oldDynamicType;
	List<Classification> oldClassifications; // enhancement to array
	final String multipleValues = TextField.getOutputForMultipleValues();
    
	JScrollPane scrollPane;
	JPanel header;
	RaplaButton tabSelector;
	
    boolean mainTabSelected = true;
    private final DialogUiFactoryInterface dialogUiFactory;
    
	ClassificationField(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory, LongFieldFactory longFieldFactory)  {
		super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
		editUI = new ClassificationEditUI(facade, i18n, raplaLocale, logger, treeFactory, dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
		editUI.addChangeListener(e -> fireContentChanged());
		setFieldName("type");
		content.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
	}

	public void setReadOnly(boolean readonly)
	{
		editUI.setReadOnly( readonly);
	}

	@Override
	public EditFieldLayout getLayout() {
	    EditFieldLayout layout = new EditFieldLayout();
	    layout.setBlock( true);
	    layout.setVariableSized( true);
	    return layout;
	}

	public void mapTo(List<T> list) throws RaplaException {
		List<Classification> classifications = editUI.getObjects();
		for (int i = 0; i < list.size(); i++) 
	    {
	        Classification classification = classifications.get( i );
	        Classifiable x = list.get(i);
	        x.setClassification(classification);
	    }
		editUI.mapToObjects();
	}
	
	public void setTypeChooserVisible( boolean visible)
	{
//	    if ( typeSelector != null)
//	    {
//	        typeSelector.setVisible( visible);
//	    }
	}

	@SuppressWarnings("unchecked")
	public void mapFrom(List<T> list) throws RaplaException {
		content.removeAll();
		List<Classifiable> classifiables = new ArrayList<>();
		// read out Classifications from Classifiable
		List<Classification> classifications = new ArrayList<>();
		for (Classifiable classifiable:list)
		{
			classifiables.add( classifiable);
            Classification classification = classifiable.getClassification();
            classifications.add(classification);
		}

		// commit Classifications to ClassificationEditUI
		editUI.setObjects(classifications);
		oldClassifications = classifications;

		// checks unity from RaplaTypes of all Classifiables
		Set<Class> raplaTypes = new HashSet<>();
		for (Classifiable c : classifiables) {
			raplaTypes.add(((RaplaObject) c).getTypeClass());
		}
		Class raplaType;
		// if there is an unitary type then set typ
		if (raplaTypes.size() == 1) {
			raplaType = raplaTypes.iterator().next();
		} else {
			return;
		}

		String classificationType = null;
		boolean isInternal = false;
		if (Reservation.class == raplaType) {
			classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION;
		} else if (Allocatable.class == raplaType) {

			boolean arePersons = true;
			// checks if Classifiables are person
			for (Classifiable c : classifiables) {
				if (((DynamicTypeImpl)c.getClassification().getType()).isInternal())
				{
					isInternal = true;
				}
				else if (!((Allocatable) c).isPerson()) {
					arePersons = false;
				}
			}

			if (isInternal) {
				classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE;
			} else if (arePersons) {
				classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;
			} else {
				classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE;
			}
		}
		DynamicType[] types = raplaFacade.getDynamicTypes(classificationType);

		// determine DynamicTypes of Classifications
		Set<DynamicType> dynamicTypes = new HashSet<>();
		for (Classification c : classifications) {
			dynamicTypes.add(c.getType());
		}
		DynamicType dynamicType;
		// checks if there is a common DynamicType?
		if (dynamicTypes.size() == 1)
			// set dynamicTyp
			dynamicType = dynamicTypes.iterator().next();
		else
			dynamicType = null;
		oldDynamicType = dynamicType;
		RaplaListComboBox jComboBox = new RaplaListComboBox(raplaLocale,types);
		typeSelector = jComboBox;
		if (dynamicType != null) {
			// set common dynamicType of the Classifications in ComboBox
			typeSelector.setSelectedItem(dynamicType);
		} else {
			// ... otherwise set place holder for the several values
			typeSelector.addItem(multipleValues);
			typeSelector.setSelectedItem(multipleValues);
		}
		typeSelector.setRenderer(new NamedListCellRenderer(i18n.getLocale()));
		typeSelector.addActionListener(this);
		final boolean canEdit = !canNotWriteOneAttribute(list) ;
		final boolean canAdmin = canAdmin( list);

		typeSelector.setEnabled(types.length>1 && !isInternal && canEdit);
		content.setLayout(new BorderLayout());
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        final String typeName = classificationType + "_type";
		header.add( new JLabel(i18n.getString(typeName) +":"));
		header.add(Box.createHorizontalStrut(20));
		header.add(typeSelector);
		header.add(Box.createHorizontalStrut(30));
		tabSelector = new RaplaButton();
        header.add(tabSelector);
        header.add(Box.createHorizontalGlue());
        tabSelector.setEnabled( canAdmin);
        tabSelector.addActionListener( this);
        updateTabSelectionText();
        
		content.add(header, BorderLayout.NORTH);
		JComponent editComponent = editUI.getComponent();

		scrollPane = new JScrollPane(editComponent,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setViewportView(editComponent);

		scrollPane.setBorder(BorderFactory.createEtchedBorder());
		scrollPane.setMinimumSize(new Dimension(300, 100));
		scrollPane.setPreferredSize(new Dimension(500, 340));
		scrollPane.getVerticalScrollBar().setUnitIncrement( 10);
		content.add(scrollPane, BorderLayout.CENTER);
	}

	private boolean canAdmin(final List<T> list) throws RaplaException
	{
		final PermissionController permissionController = raplaFacade.getPermissionController();
		final User user = clientFacade.getUser();
		for (T t : list)
		{
			if (!permissionController.canAdmin( (Entity) t ,user))
			{
				return false;
			}
		}
		return true;
	}

    private boolean canNotWriteOneAttribute(final List<T> list) throws RaplaException
    {
        final PermissionController permissionController = raplaFacade.getPermissionController();
        final User user = clientFacade.getUser();
        for (T t : list)
        {
            final Classification classification = t.getClassification();
            if (!permissionController.canModify( (Entity) t ,user))
			{
				return true;
			}
            final Attribute[] attributes = classification.getAttributes();
            for (Attribute attribute : attributes)
            {
                if(!permissionController.canWrite(classification, attribute, user))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateTabSelectionText()
    {
        tabSelector.setText( mainTabSelected ?
                getInfoButton()
                :i18n.getString("back")
                );
        tabSelector.setIcon( mainTabSelected ?
                null
                : RaplaImages.getIcon(i18n.getIcon("icon.list"))
                );
    }

    private String getInfoButton() {
        return i18n.getString("additional-view") + " / " +i18n.getString("permissions");
    }
    
    public boolean isMainTabSelected()
    {
        return mainTabSelected;
    }
	
	public void setScrollingAlwaysEnabled( boolean enabled)
	{
	    scrollPane.setVerticalScrollBarPolicy( enabled ? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED );
	    scrollPane.setHorizontalScrollBarPolicy( enabled ? JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
	}

	// The DynamicType has changed
	public void actionPerformed(ActionEvent event) {
		try {
			Object source = event.getSource();
			if (source == tabSelector) {
			    mainTabSelected = !mainTabSelected;
			    
		        updateTabSelectionText();
		        editUI.mapToObjects();
		        editUI.setSelectedView( mainTabSelected ? AttributeAnnotations.VALUE_EDIT_VIEW_MAIN : AttributeAnnotations.VALUE_EDIT_VIEW_ADDITIONAL);
		        editUI.recreateFields();
		        fireContentChanged();
			}
			if (source == typeSelector) {
				// checks if a DynamicType has been selected in ComboBox
				if (typeSelector.getSelectedItem() instanceof DynamicType) {
					// delete place holder for the several values
					typeSelector.removeItem(multipleValues);
					DynamicType dynamicType = (DynamicType) typeSelector
							.getSelectedItem();
					// checks if no new DynmicType has been selected
					if (dynamicType.equals(oldDynamicType))
						// yes: set last Classifications again
						editUI.setObjects(oldClassifications);
					else {
						// no: set new Classifications
						List<Classification> newClassifications = new ArrayList<>();
						List<Classification> classifications = editUI.getObjects();
						for (int i = 0; i < classifications.size(); i++) {
							Classification classification = classifications.get(i);
                            // checks if Classification hast already the new
							// selected DynamicType
							if (dynamicType.equals(classification .getType())) {
								// yes: adopt Classification
								newClassifications.add( classification );
							} else {
								// no: createInfoDialog new Classification
								newClassifications.add( dynamicType.newClassificationFrom(classification));
							}
						}
						// set new Classifications in ClassificationEditUI
						editUI.setObjects(newClassifications);
					}
				}
			}
		} catch (RaplaException ex) {
		    dialogUiFactory.showException(ex, new SwingPopupContext(content, null));
		}
	}

	public JComponent getComponent() {
		return content;
	}
	
	@Singleton
	public static class ClassificationFieldFactory{
	    
	    private final TreeFactory treeFactory;
	    private final DateFieldFactory dateFieldFactory;
	    private final DialogUiFactoryInterface dialogUiFactory;
        private final BooleanFieldFactory booleanFieldFactory;
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final TextFieldFactory textFieldFactory;
        private final LongFieldFactory longFieldFactory;

	    @Inject
        public ClassificationFieldFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,  DateFieldFactory dateFieldFactory,
                DialogUiFactoryInterface dialogUiFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory, LongFieldFactory longFieldFactory)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.treeFactory = treeFactory;
            this.dateFieldFactory = dateFieldFactory;
            this.dialogUiFactory = dialogUiFactory;
            this.booleanFieldFactory = booleanFieldFactory;
            this.textFieldFactory = textFieldFactory;
            this.longFieldFactory = longFieldFactory;
        }

        public ClassificationField create()
	    {
	        return new ClassificationField(facade, i18n, raplaLocale, logger, treeFactory,  dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
	    }
	}
}


