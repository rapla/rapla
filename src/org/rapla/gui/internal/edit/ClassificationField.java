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
package org.rapla.gui.internal.edit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.internal.common.NamedListCellRenderer;

/****************************************************************
 * This is the base-class for all Classification-Panels         *
 ****************************************************************/
public  class  ClassificationField<T extends Classifiable> extends AbstractEditField
    implements
            ActionListener
{
	JPanel content = new JPanel();
	JComboBox typeSelector;
	ClassificationEditUI editUI;

	DynamicType oldDynamicType;
	List<Classification> oldClassifications; // enhancement to array
	final String multipleValues = TextField.getOutputForMultipleValues();

	public ClassificationField(RaplaContext sm)  {
		super(sm);
		editUI = new ClassificationEditUI(sm);
		setFieldName("type");
		content.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
	}

	public boolean isBlock() {
		return true;
	}

	public boolean isVariableSized() {
		return true;
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

	@SuppressWarnings("unchecked")
	public void mapFrom(List<T> list) throws RaplaException {
		content.removeAll();
		List<Classifiable> classifiables = new ArrayList<Classifiable>();
		// read out Classifications from Classifiable
		List<Classification> classifications = new ArrayList<Classification>();
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
		Set<RaplaType> raplaTypes = new HashSet<RaplaType>();
		for (Classifiable c : classifiables) {
			raplaTypes.add(((RaplaObject) c).getRaplaType());
		}
		RaplaType raplaType;
		// if there is an unitary type then set typ
		if (raplaTypes.size() == 1) {
			raplaType = raplaTypes.iterator().next();
		} else {
			return;
		}

		String classificationType = null;
		if (Reservation.TYPE.equals(raplaType)) {
			classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION;
		} else if (Allocatable.TYPE.equals(raplaType)) {

			boolean arePersons = true;
			// checks if Classifiables are person
			for (Classifiable c : classifiables) {
				if (!((Allocatable) c).isPerson()) {
					arePersons = false;
				}
			}

			if (arePersons) {
				classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;
			} else {
				classificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE;
			}
		}
		DynamicType[] types = getQuery().getDynamicTypes(classificationType);

		// determine DynamicTypes of Classifications
		Set<DynamicType> dynamicTypes = new HashSet<DynamicType>();
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

		JComboBox jComboBox = new JComboBox(types);
		typeSelector = jComboBox;
		typeSelector.setEnabled( types.length > 1);
		if (dynamicType != null)
			// set common dynamicType of the Classifications in ComboBox
			typeSelector.setSelectedItem(dynamicType);
		else {
			// ... otherwise set place holder for the several values
			typeSelector.addItem(multipleValues);
			typeSelector.setSelectedItem(multipleValues);
		}
		typeSelector.setRenderer(new NamedListCellRenderer(getI18n().getLocale()));
		typeSelector.addActionListener(this);

		content.setLayout(new BorderLayout());
		JPanel container = new JPanel();
		container.setLayout(new BorderLayout());
		container.add(typeSelector, BorderLayout.WEST);
		content.add(container, BorderLayout.NORTH);

		JComponent editComponent = editUI.getComponent();

		JScrollPane scrollPane = new JScrollPane(editComponent,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setViewportView(editComponent);

		scrollPane.setBorder(BorderFactory.createEtchedBorder());
		scrollPane.setMinimumSize(new Dimension(300, 100));
		scrollPane.setPreferredSize(new Dimension(500, 340));
		scrollPane.getVerticalScrollBar().setUnitIncrement( 10);
		content.add(scrollPane, BorderLayout.CENTER);
	}


	// The DynamicType has changed
	public void actionPerformed(ActionEvent event) {
		try {
			Object source = event.getSource();
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
						List<Classification> newClassifications = new ArrayList<Classification>();
						List<Classification> classifications = editUI.getObjects();
						for (int i = 0; i < classifications.size(); i++) {
							Classification classification = classifications.get(i);
                            // checks if Classification hast already the new
							// selected DynamicType
							if (dynamicType.equals(classification .getType())) {
								// yes: adopt Classification
								newClassifications.add( classification );
							} else {
								// no: create new Classification
								newClassifications.add( dynamicType.newClassification(classification));
							}
						}
						// set new Classifications in ClassificationEditUI
						editUI.setObjects(newClassifications);
					}
				}
			}
		} catch (RaplaException ex) {
			showException(ex, content);
		}
	}

	public JComponent getComponent() {
		return content;
	}
}


