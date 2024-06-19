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
package org.rapla.client.swing.internal.edit;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.common.NamedListCellRenderer;
import org.rapla.client.swing.internal.edit.fields.AbstractEditField;
import org.rapla.client.swing.internal.edit.fields.AllocatableSelectField;
import org.rapla.client.swing.internal.edit.fields.BooleanField;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.CategoryListField;
import org.rapla.client.swing.internal.edit.fields.CategorySelectField;
import org.rapla.client.swing.internal.edit.fields.DateField;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.SetGetField;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.ItemSelectable;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


public class ClassifiableFilterEdit extends RaplaGUIComponent
    implements
        ActionListener
        ,RaplaWidget
{
    JPanel content = new JPanel();
    JScrollPane scrollPane;
    JCheckBox[] checkBoxes;
    ClassificationEdit[] filterEdit;
    DynamicType[] types;
    boolean isResourceSelection;

    ArrayList<ChangeListener> listenerList = new ArrayList<>();
    final RaplaButton everythingButton = new RaplaButton(RaplaButton.SMALL);
    final RaplaButton  nothingButton = new RaplaButton(RaplaButton.SMALL);
    final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final DateFieldFactory dateFieldFactory;
    private final BooleanFieldFactory booleanFieldFactory;
    private final TextFieldFactory textFieldFactory;
    private final LongFieldFactory longFieldFactory;
    
    public ClassifiableFilterEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, boolean isResourceSelection, DateFieldFactory dateFieldFactory, DialogUiFactoryInterface dialogUiFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory, LongFieldFactory longFieldFactory)  {
        super(facade, i18n, raplaLocale, logger);
        this.treeFactory = treeFactory;
        this.dateFieldFactory = dateFieldFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.booleanFieldFactory = booleanFieldFactory;
        this.textFieldFactory = textFieldFactory;
        this.longFieldFactory = longFieldFactory;
        content.setBackground(UIManager.getColor("List.background"));
        scrollPane = new JScrollPane(content
                                     ,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                                     ,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                                     );
        scrollPane.setPreferredSize(new Dimension(590,400));
        this.isResourceSelection = isResourceSelection;
        content.setBorder( BorderFactory.createEmptyBorder(5,5,5,5));
        everythingButton.setText( getString("select_everything") );
        everythingButton.setIcon( RaplaImages.getIcon(i18n.getIcon("icon.all-checked")));
        nothingButton.setText(getString("select_nothing"));
        nothingButton.setIcon( RaplaImages.getIcon(i18n.getIcon("icon.all-unchecked")));
    }
    
    public JComponent getClassificationTitle(String classificationType) {
        JLabel title = new JLabel( classificationType );
        title.setFont( title.getFont().deriveFont( Font.BOLD ));
        title.setText( getString( classificationType) + ":" );
        return title;
    }
    
    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    protected void fireFilterChanged() {
          
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    	everythingButton.setEnabled( true);
		nothingButton.setEnabled( true);
    }

  

    public void setTypes(DynamicType[] types) {
        this.types = types;
        content.removeAll();
        TableLayout tableLayout = new TableLayout();
        content.setLayout(tableLayout);
        tableLayout.insertColumn(0,TableLayout.PREFERRED);
        tableLayout.insertColumn(1,10);
        tableLayout.insertColumn(2,TableLayout.FILL);
        tableLayout.insertRow(0, TableLayout.PREFERRED);
        if (checkBoxes != null) {
            for (int i=0;i<checkBoxes.length;i++) {
                checkBoxes[i].removeActionListener(this);
            }
        }
        int defaultRowSize = 35;
		scrollPane.setPreferredSize(new Dimension(590,Math.max(200,Math.min(600, 70+ defaultRowSize +  types.length * 33 + (isResourceSelection ? 20:0)))));
        checkBoxes = new JCheckBox[types.length];
        filterEdit = new ClassificationEdit[types.length];
        
        String lastClassificationType= null;
        int row = 0;
       

        for (int i=0;i<types.length;i++) {
            String classificationType = types[i].getAnnotation( DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if ( !classificationType.equals( lastClassificationType)) {
                tableLayout.insertRow( row, 2);
                row ++;
                lastClassificationType = classificationType;
                tableLayout.insertRow( row, TableLayout.MINIMUM);
                content.add( getClassificationTitle( classificationType),"0,"+ row +",1," + row) ;
                if ( i== 0 )
                {
                  
                    everythingButton.setSelected( true);
                    ActionListener resetButtonListener = new ActionListener() {
                    	private boolean enabled = true;
                        public void actionPerformed(ActionEvent evt) {
                        	try
                        	{
		                    	 if ( !enabled)
		                    	 {
		                    		 return;
		                    	 }
		                    	 enabled = false;
		                    	 JButton source = (JButton)evt.getSource();
		                    	 boolean isEverything = source == everythingButton;
		                    	 source.setSelected(isEverything);
		                    	 boolean deselectAllIfFilterIsNull = !isEverything;
		                    	 mapFromIntern( null, deselectAllIfFilterIsNull);
		                         fireFilterChanged();
		                         
		                         source.setEnabled( false);
		                         JButton opposite = isEverything ? nothingButton : everythingButton;
		                    	 opposite.setEnabled( true);
                        	} 
                        	catch (RaplaException ex) 
                        	{
                        	    dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
                        	} 
                        	finally
                        	{
		                    	 enabled = true;
                        	}
                        }
                    };
					everythingButton.addActionListener( resetButtonListener);
                    nothingButton.addActionListener( resetButtonListener);
                    JPanel buttonPanel = new JPanel();
                    buttonPanel.setBackground( content.getBackground());
                    buttonPanel.add( everythingButton);
                    buttonPanel.add( nothingButton);
                    content.add( buttonPanel,"2,"+ row +",r,c");
                   
                }
                row ++;
                tableLayout.insertRow( row, 4);
                row ++;
                tableLayout.insertRow( row, 2);
                content.add( new JPanel() , "0," + row  + ",2,"  + row );
                row ++;

            }
            tableLayout.insertRow( row, 3);
            tableLayout.insertRow( row + 1, TableLayout.MINIMUM);
            tableLayout.insertRow( row + 2, TableLayout.MINIMUM);
            tableLayout.insertRow( row + 3, 3);
            tableLayout.insertRow( row + 4, 2);
            checkBoxes[i] = new JCheckBox(getName(types[i]));
            final JCheckBox checkBox = checkBoxes[i];
            checkBox.setBorder( BorderFactory.createEmptyBorder(0,10,0,0));
            checkBox.setOpaque( false );
            checkBox.addActionListener(this);
            checkBox.setSelected( true );
            content.add( checkBox , "0," + (row + 1) + ",l,t");
            filterEdit[i] = new ClassificationEdit(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), treeFactory,  dialogUiFactory, scrollPane, dateFieldFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
            final ClassificationEdit edit = filterEdit[i];
            content.add( edit.getNewComponent() , "2," + (row + 1));
            content.add( edit.getRulesComponent() , "0," + (row + 2) + ",2,"+ (row + 2));
            content.add( new JPanel() , "0," + (row + 4) + ",2,"  + (row + 4));
            edit.addChangeListener(e -> {
                everythingButton.setEnabled( true);
                nothingButton.setEnabled( true);
                fireFilterChanged();
            }
            );
            
		    row += 5;
        }
    }


    private ClassificationFilter findFilter(DynamicType type,ClassificationFilter[] filters) {
        for (int i=0;i<filters.length;i++)
            if (filters[i].getType().equals(type))
                return filters[i];
        return null;
    }

    public void setFilter(ClassifiableFilter filter) throws RaplaException {

        List<DynamicType> list = new ArrayList<>();
        if ( !isResourceSelection) {
            list.addAll( Arrays.asList( getQuery().getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION )));
        } 
        else
        {
            list.addAll( Arrays.asList( getQuery().getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE )));
            list.addAll( Arrays.asList( getQuery().getDynamicTypes( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON )));
        }
        setTypes( list.toArray( DynamicType.DYNAMICTYPE_ARRAY));

        mapFromIntern(filter, false);
    }


	private void mapFromIntern(ClassifiableFilter classifiableFilter, boolean deselectAllIfFilterIsNull) throws RaplaException {
		final ClassificationFilter[] filters;
        if ( classifiableFilter != null)
        {
            filters = isResourceSelection ? classifiableFilter.getAllocatableFilter() : classifiableFilter.getReservationFilter();
        }
        else
        {
            filters = ClassificationFilter.CLASSIFICATIONFILTER_ARRAY;
        }
        boolean nothingSelectable = false;
        for (int i=0;i<types.length;i++) {
            final DynamicType dynamicType = types[i];
            ClassificationFilter filter = findFilter(dynamicType, filters);
            final boolean fillDefault;
            if ( classifiableFilter != null)
            {
                fillDefault = isResourceSelection ? classifiableFilter.isDefaultResourceTypes() : classifiableFilter.isDefaultEventTypes();
            }
            else
            {
                fillDefault = !deselectAllIfFilterIsNull;
            }
            if ( filter == null && fillDefault)
            {
                filter = dynamicType.newClassificationFilter();
            }
            checkBoxes[i].setSelected( filter != null);
            if ( filter != null)
            {
            	nothingSelectable = true;
            }
            filterEdit[i].mapFrom(filter);
        }
        if ( classifiableFilter != null)
        {
        	everythingButton.setEnabled( ! (isResourceSelection ? classifiableFilter.isDefaultResourceTypes() :classifiableFilter.isDefaultEventTypes()));
        }
        nothingButton.setEnabled( nothingSelectable);
        scrollPane.revalidate();
        scrollPane.repaint();
	}

    
    
    public ClassificationFilter[] getFilters()  {
        ArrayList<ClassificationFilter> list = new ArrayList<>();
        for (int i=0; i< filterEdit.length; i++) {
            ClassificationFilter filter = filterEdit[i].getFilter();
            if (filter != null) {
                list.add(filter);
            }
        }
        return list.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }

    public void actionPerformed(ActionEvent evt) {
    	for (int i=0;i<checkBoxes.length;i++) {
            if (checkBoxes[i] == evt.getSource()) {
                if (checkBoxes[i].isSelected())
                    filterEdit[i].mapFrom(types[i].newClassificationFilter());
                else
                    filterEdit[i].mapFrom(null);
                // activate the i. filter
            }
        }
        content.revalidate();
        content.repaint();
        fireFilterChanged();
    	everythingButton.setEnabled( true);
		nothingButton.setEnabled( true);
    
    }

    public JComponent getComponent() {
        return scrollPane;
    }

}


class ClassificationEdit extends RaplaGUIComponent implements ItemListener {
    JPanel ruleListPanel = new JPanel();
    JPanel newPanel = new JPanel();
    List<RuleComponent> ruleList = new ArrayList<>();
    JComboBox attributeSelector;
    JButton newLabel = new JButton();
    DynamicType type;
    TreeFactory treeFactory;
    
    ArrayList<ChangeListener> listenerList = new ArrayList<>();
    JScrollPane pane;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final DateFieldFactory dateFieldFactory;
    private final BooleanFieldFactory booleanFieldFactory;
    private final TextFieldFactory textFieldFactory;
    private final LongFieldFactory longFieldFactory;
    
    ClassificationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,TreeFactory treeFactory, DialogUiFactoryInterface dialogUiFactory,JScrollPane pane, DateFieldFactory dateFieldFactory, BooleanFieldFactory booleanFieldFactory, TextFieldFactory textFieldFactory, LongFieldFactory longFieldFactory){
        super(facade, i18n, raplaLocale, logger);
        this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.pane = pane;
        this.dateFieldFactory = dateFieldFactory;
        this.booleanFieldFactory = booleanFieldFactory;
        this.textFieldFactory = textFieldFactory;
        this.longFieldFactory = longFieldFactory;
        ruleListPanel.setOpaque( false );
        ruleListPanel.setLayout(new BoxLayout(ruleListPanel,BoxLayout.Y_AXIS));
        newPanel.setOpaque( false );
        newPanel.setLayout(new TableLayout(new double[][] {{TableLayout.PREFERRED},{TableLayout.PREFERRED}}));
    }
    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    protected void fireFilterChanged() {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    }

    public JComponent getRulesComponent() {
        return ruleListPanel;
    }

    public JComponent getNewComponent() {
        return newPanel;
    }

    @SuppressWarnings("unchecked")
	public void mapFrom(ClassificationFilter filter) {
        getRulesComponent().removeAll();
        ruleList.clear();
        getNewComponent().removeAll();
        if ( filter == null) {
            type = null;
            return;
        }
        this.type = filter.getType();
        Attribute[] attributes = type.getAttributes();
        if (attributes.length == 0 )
            return;

        if (attributeSelector != null)
            attributeSelector.removeItemListener(this);
		JComboBox jComboBox = new JComboBox(attributes);
		attributeSelector = jComboBox;

        attributeSelector.setRenderer(new NamedListCellRenderer(getI18n().getLocale()) {
            private static final long serialVersionUID = 1L;


                public Component getListCellRendererComponent(JList list,
                                                              Object value,
                                                              int index,
                                                              boolean isSelected,
                                                              boolean cellHasFocus) {
                    if (value == null) {
                        value = getString("new_rule");
                    }
                    return super.getListCellRendererComponent(list, value,index,isSelected,cellHasFocus);
                }
            });

        attributeSelector.addItemListener(this);
        newPanel.add(newLabel,"0,0,f,c");
        newPanel.add(attributeSelector,"0,0,f,c");
        newLabel.setText(getString("new_rule"));
        newLabel.setVisible(false);
        attributeSelector.setSelectedItem(null);
        Iterator<? extends ClassificationFilterRule> it = filter.ruleIterator();
        while (it.hasNext()) {
            ClassificationFilterRule rule = it.next();
            RuleComponent ruleComponent = new RuleComponent( rule, treeFactory);
            ruleList.add( ruleComponent );
        }
        update();
    }

    public void update() {
        ruleListPanel.removeAll();
        int i=0;
        for (Iterator<RuleComponent> it = ruleList.iterator();it.hasNext();) {
            RuleComponent rule =  it.next();
            ruleListPanel.add( rule);
            rule.setAndVisible( i > 0);
            i++;
        }

        ruleListPanel.revalidate();
        ruleListPanel.repaint();
    }
    
	public void itemStateChanged(ItemEvent e) {
		Object item = e.getItem();
        if ( e.getStateChange() != ItemEvent.SELECTED)
        {
        	return;
        }
		Attribute att = (Attribute)item;
        if (att != null) {
            RuleComponent ruleComponent = getComponent(att);
            final RuleRow row;
            if (ruleComponent == null) {
                ruleComponent = new RuleComponent( att, treeFactory);
                ruleList.add( ruleComponent );
            } 
            row = ruleComponent.addOr();
            final RuleComponent comp = ruleComponent;
            update();
           
            // invokeLater prevents a deadlock in jdk <=1.3
            javax.swing.SwingUtilities.invokeLater(() -> {
                attributeSelector.setSelectedIndex(-1);
                comp.scrollRowVisible(row);
            });
            fireFilterChanged();
        }
		
	}

    public ClassificationFilter getFilter() {
        if ( type == null )
             return null;
        ClassificationFilter filter = type.newClassificationFilter();
        int i=0;
        for (Iterator<RuleComponent> it = ruleList.iterator();it.hasNext();) {
            RuleComponent ruleComponent =  it.next();
            Attribute attribute = ruleComponent.getAttribute();
            List<RuleRow> ruleRows = ruleComponent.getRuleRows();
            int size =  ruleRows.size();
            Object[][] conditions = new Object[size][2];
            for (int j=0;j<size;j++) {
                RuleRow ruleRow = ruleRows.get(j);
                conditions[j][0] = ruleRow.getOperatorValue();
                conditions[j][1] = ruleRow.getValue();
            }
            filter.setRule(i++ , attribute, conditions);
        }
        return filter;
    }

    private RuleComponent getComponent(Attribute attribute) {
        for (Iterator<RuleComponent> it = ruleList.iterator();it.hasNext();) {
            RuleComponent c2 = it.next();
            if (attribute.equals(c2.getAttribute())) {
                return c2;
            }
        }
        return null;
    }

    private void deleteRule(Component ruleComponent) {
        ruleList.remove( ruleComponent );
        update();
    }
  
    class RuleComponent extends JPanel {
        private static final long serialVersionUID = 1L;

        Attribute attribute;
        TreeFactory treeFactory;
        private final Listener listener = new Listener();
        List<RuleRow> ruleRows = new ArrayList<>();
        List<RaplaButton> deleteButtons = new ArrayList<>();
        boolean isAndVisible;
        JLabel and;

        RuleComponent(Attribute attribute, TreeFactory treeFactory) {
            this.treeFactory = treeFactory;
            Border outer = BorderFactory.createCompoundBorder(
                                                               BorderFactory.createEmptyBorder(5,20,0,3)
                                                               ,BorderFactory.createEtchedBorder()
                                                               );
            this.setBorder(BorderFactory.createCompoundBorder(
                                                              outer
                                                              ,BorderFactory.createEmptyBorder(2,3,2,3)
                                                              ));
            this.setOpaque( false );
            this.attribute = attribute;
        }
        
        RuleComponent(ClassificationFilterRule rule, TreeFactory treeFactory){
            this( rule.getAttribute(), treeFactory);
            Assert.notNull(attribute);
            Object[] ruleValues = rule.getValues();
            String[] operators = rule.getOperators();
            for (int i=0;i<ruleValues.length;i++) {
                RuleRow row = createRow(operators[i], ruleValues[i]);
                ruleRows.add(row);
            }
            rebuild();
        }

        public Attribute getAttribute() {
            return attribute;
        }

        public List<RuleRow> getRuleRows() {
            return ruleRows;
        }

        
        private RuleRow addOr() {
            RuleRow row = createRow(null,null);
            ruleRows.add(row);
            rebuild();
            return row;
        }

        protected void scrollRowVisible(RuleRow row) {
            Component ruleComponent = row.ruleLabel;
            if ( ruleComponent == null || ruleComponent.getParent() == null)
            {
                ruleComponent = row.field.getComponent();
            }
            if ( ruleComponent != null)
            {
                Point location1 = ruleComponent.getLocation();
                Point location2 = getLocation();
                Point location3 = ruleListPanel.getLocation();
                int y = location1.y + location2.y + location3.y ;
                int height2 = (int)ruleComponent.getPreferredSize().getHeight()+20;
                Rectangle aRect = new Rectangle(location1.x,y , 10, height2);
                
                JViewport viewport = pane.getViewport();
                viewport.scrollRectToVisible( aRect);
            }
        }

       

        public void setAndVisible( boolean andVisible) {
            this.isAndVisible = andVisible;
            if ( and!= null)
            {
                if ( andVisible) {
                    and.setText( getString("and"));
                } else {
                    and.setText("");
                }
            }
        }

        private void rebuild() {
            this.removeAll();
            TableLayout layout = new TableLayout();
            layout.insertColumn(0,TableLayout.PREFERRED);
            layout.insertColumn(1,10);
            layout.insertColumn(2,TableLayout.PREFERRED);
            layout.insertColumn(3,5);
            layout.insertColumn(4,TableLayout.PREFERRED);
            layout.insertColumn(5,5);
            layout.insertColumn(6,TableLayout.FILL);
            this.setLayout(layout);

            int row =0;
            layout.insertRow(row,TableLayout.PREFERRED);
            and = new JLabel();
        //  and.setAlignmentX( and.LEFT_ALIGNMENT);
            this.add("0,"+row +",6,"+ row + ",l,c", and);
            if ( isAndVisible) {
                and.setText( getString("and"));
            } else {
                and.setText("");
            }
            row ++;

            int size =  ruleRows.size();
            for (int i=0;i<size;i++) {
                RuleRow ruleRow = ruleRows.get(i);
                RaplaButton deleteButton =  deleteButtons.get(i);
                layout.insertRow(row,TableLayout.PREFERRED);
                this.add("0," + row + ",l,c", deleteButton);
                if (i == 0)
                    this.add("2," + row + ",l,c", ruleRow.ruleLabel);
                else
                    this.add("2," + row + ",r,c", new JLabel(getString("or")));
                this.add("4," + row + ",l,c", ruleRow.operatorComponent);
                this.add("6," + row + ",f,c", ruleRow.field.getComponent());
                row ++;
                if (i<size -1) {
                     layout.insertRow(row , 2);
                     row++;
                }
            }
            revalidate();
            repaint();
        }

       

        private RuleRow createRow(String operator,Object ruleValue) {
            RaplaButton deleteButton = new RaplaButton(RaplaButton.SMALL);
            deleteButton.setToolTipText(getString("delete"));
            deleteButton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.delete")));
            deleteButton.addActionListener(listener);
            deleteButtons.add(deleteButton);
            RuleRow row = new RuleRow(treeFactory, attribute,operator,ruleValue);
            return row;
        }

        class Listener implements ActionListener {
            public void actionPerformed(ActionEvent evt) {
                int index = deleteButtons.indexOf(evt.getSource());
                if (ruleRows.size() <= 1) {
                    deleteRule(RuleComponent.this);
                } else {
                    ruleRows.remove(index);
                    deleteButtons.remove(index);
                    rebuild();
                }
                fireFilterChanged();
            }
        }

    }

    class RuleRow implements ChangeListener, ItemListener{
        Object ruleValue;
        JLabel ruleLabel;
        JComponent operatorComponent;
        AbstractEditField field;
        Attribute attribute;
        TreeFactory treeFactory;

        public void stateChanged(ChangeEvent e) {
            fireFilterChanged();
        }
        
        public void itemStateChanged(ItemEvent e) {
            fireFilterChanged();
        }
        
		RuleRow(TreeFactory treeFactory, Attribute attribute,String operator,Object ruleValue) {
            this.treeFactory = treeFactory;
            this.attribute = attribute;
            this.ruleValue = ruleValue;
            ruleLabel = new JLabel();
            ruleLabel.setText(attribute.getName().getName(getI18n().getLang()));
            createField( attribute );
            // we can cast here, because we tested in createField
            @SuppressWarnings("unchecked")
			SetGetField<Object> setGetField = (SetGetField<Object>)field;
			setGetField.setValue(ruleValue);
            field.addChangeListener( this);
            setOperatorValue(operator);
            
            if ( operatorComponent instanceof ItemSelectable)
            {
                ((ItemSelectable)operatorComponent).addItemListener(this);
            }
        }


        public String getOperatorValue() {
            AttributeType type = attribute.getType();
            if (type.equals(AttributeType.ALLOCATABLE) || type.equals(AttributeType.CATEGORY) || type.equals(AttributeType.BOOLEAN) )
                return "is";
            if (type.equals(AttributeType.STRING)) {
            	int index = ((JComboBox)operatorComponent).getSelectedIndex();
            	if (index == 0)
                    return "contains";
            	else if (index == 1)
            		return "starts";
                else if (index == 2)
                    return "ends";
            }
            if (type.equals(AttributeType.DATE) || type.equals(AttributeType.INT)) {
                int index = ((JComboBox)operatorComponent).getSelectedIndex();
                if (index == 0)
                    return "<";
                if (index == 1)
                    return "=";
                if (index == 2)
                    return ">";
                if (index == 3)
                    return "<>";
                if (index == 4)
                    return "<=";
                if (index == 5)
                    return ">=";
                
            }
            Assert.notNull(field,"Unknown AttributeType" + type);
            return null;
        }

        private void setOperatorValue(String operator) {
            AttributeType type = attribute.getType();
            if ((type.equals(AttributeType.DATE) || type.equals(AttributeType.INT)))
            {
                if (operator == null)
                    operator = "<";
                JComboBox box = (JComboBox)operatorComponent;
                if (operator.equals("<"))
                    box.setSelectedIndex(0);
                if (operator.equals("=") || operator.equals("is"))
                    box.setSelectedIndex(1);
                if (operator.equals(">"))
                    box.setSelectedIndex(2);
                if (operator.equals("<>"))
                    box.setSelectedIndex(3);
                if (operator.equals("<="))
                    box.setSelectedIndex(4);
                if (operator.equals(">="))
                    box.setSelectedIndex(5);
                
            }
            if (type.equals(AttributeType.STRING)) {
                JComboBox box = (JComboBox)operatorComponent;
                if (operator == null)
                    operator = "contains";
                if (operator.equals("contains"))
                    box.setSelectedIndex(0);
                else if (operator.equals("starts"))
                    box.setSelectedIndex(1);
                else if (operator.equals("ends"))
                    box.setSelectedIndex(2);
            }
        }

        private EditField createField(Attribute attribute) {
            operatorComponent = null;
            AttributeType type = attribute.getType();
            // used for static testing of the field type 
            @SuppressWarnings("unused")
            SetGetField test;
            final ClientFacade facade = getClientFacade();
            if (type.equals(AttributeType.ALLOCATABLE))
            {
                operatorComponent = new JLabel("");
                DynamicType dynamicTypeConstraint = (DynamicType)attribute.getConstraint( ConstraintIds.KEY_DYNAMIC_TYPE);
                AllocatableSelectField newField = new AllocatableSelectField(facade, getI18n(), getRaplaLocale(), getLogger(), treeFactory,  dynamicTypeConstraint, dialogUiFactory);
                field = newField;
                test = newField;
               
            }
            else if (type.equals(AttributeType.CATEGORY))
            {
                operatorComponent = new JLabel("");
                Category rootCategory = (Category)attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                if (rootCategory.getDepth() > 2) {
                    Category defaultCategory = (Category) attribute.defaultValue();
                    CategorySelectField newField = new CategorySelectField(facade, getI18n(), getRaplaLocale(), getLogger(), treeFactory,  dialogUiFactory, rootCategory, defaultCategory);
					field = newField;
					test = newField;
                } else {
                    CategoryListField newField = new CategoryListField(facade, getI18n(), getRaplaLocale(), getLogger(), rootCategory);
					field = newField;
					test = newField;
                }
            }
            else if (type.equals(AttributeType.STRING))
            {
                TextField newField = textFieldFactory.create();
				field = newField;
				test = newField;
                @SuppressWarnings("unchecked")
				DefaultComboBoxModel model = new DefaultComboBoxModel(new String[] {
                		 getString("filter.contains")
                        ,getString("filter.starts")
                        ,getString("filter.ends")
                    });
                @SuppressWarnings("unchecked")
				JComboBox jComboBox = new JComboBox(model);
				operatorComponent = jComboBox;
            }
            else if (type.equals(AttributeType.INT))
            {
                LongField newField = longFieldFactory.create();
				field = newField;
				test = newField;
                @SuppressWarnings("unchecked")
				DefaultComboBoxModel model = new DefaultComboBoxModel(new String[] {
                    getString("filter.is_smaller_than")
                    ,getString("filter.equals")
                    ,getString("filter.is_greater_than")
                    ,getString("filter.not_equals")
                    ,getString("filter.smaller_or_equals")
                    ,getString("filter.greater_or_equals")
                });
                @SuppressWarnings("unchecked")
				JComboBox jComboBox = new JComboBox(model);
				operatorComponent = jComboBox;
                
            }
            else if (type.equals(AttributeType.DATE))
            {
                DateField newField = dateFieldFactory.create();
				field = newField;
				test = newField;
                @SuppressWarnings("unchecked")
				DefaultComboBoxModel model = new DefaultComboBoxModel(new String[] {
                    getString("filter.earlier_than")
                    ,getString("filter.equals")
                    ,getString("filter.later_than")
                    ,getString("filter.not_equals")
                }); 
                @SuppressWarnings("unchecked")
				JComboBox jComboBox = new JComboBox(model);
				operatorComponent = jComboBox;            }
            else if (type.equals(AttributeType.BOOLEAN))
            {
                operatorComponent = new JLabel("");
                BooleanField newField = booleanFieldFactory.create();
				field = newField;
				test = newField;
                ruleValue = Boolean.FALSE;
            }
           
            Assert.notNull(field,"Unknown AttributeType");
            return field;
        }


        public Object getValue() {
            ruleValue = ((SetGetField<?>)field).getValue();
            return ruleValue;
        }

    }


}

