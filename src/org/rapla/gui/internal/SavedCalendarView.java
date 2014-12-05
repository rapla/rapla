package org.rapla.gui.internal;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaAction;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.internal.common.MultiCalendarView;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.tableview.internal.AppointmentTableViewFactory;
import org.rapla.plugin.tableview.internal.ReservationTableViewFactory;
import org.rapla.storage.UpdateResult;

public class SavedCalendarView extends RaplaGUIComponent implements ActionListener {

    JComboBox selectionBox;
    
    private boolean listenersEnabled = true;
    List<FileEntry> filenames = new ArrayList<FileEntry>();
    final MultiCalendarView calendarContainer;
    final CalendarSelectionModel model;
    final ResourceSelection resourceSelection; 
    JToolBar toolbar = new JToolBar();
    class SaveAction extends RaplaAction
    {

        public SaveAction(RaplaContext sm)  {
            super(sm);
            final String name = getString("save") ;
            putValue(NAME,name);
            putValue(SHORT_DESCRIPTION,name);
            putValue(SMALL_ICON,getIcon("icon.save"));
        }

        public void actionPerformed(ActionEvent arg0) {
            save();
        }
    }
    
    class PublishAction extends RaplaAction
    {
        PublishDialog publishDialog;
        public PublishAction(RaplaContext sm) throws RaplaException {
            super(sm);
            final String name = getString("publish") ;
            putValue(NAME,name);
            putValue(SHORT_DESCRIPTION,name);
            putValue(SMALL_ICON,getIcon("icon.export"));
            publishDialog = new PublishDialog(getContext());
            
        }

        public void actionPerformed(ActionEvent arg0) {
            try 
            {   
				CalendarSelectionModel model = getService( CalendarSelectionModel.class);
				FileEntry filename = getSelectedFile();
                Component parentComponent = getMainComponent();
                if ( filename.isDefault)
                {
                  	publishDialog.export(model, parentComponent, null);
                }
                else
                {
                	publishDialog.export(model, parentComponent, filename.name);
                }
            }
            catch (RaplaException ex) {
                showException( ex, getMainComponent());
            }
        }
        
        public boolean hasPublishActions()
        {
            return publishDialog.hasPublishActions();
        }
        
    }
    
    class DeleteAction extends RaplaAction
    {
        public DeleteAction(RaplaContext sm)
        {
            super(sm);
            final String name = getString("delete");
            putValue(NAME,name);
            putValue(SHORT_DESCRIPTION,name);
            putValue(SMALL_ICON,getIcon("icon.delete"));
            
        }

        public void actionPerformed(ActionEvent arg0) {
            try 
            {
                String[] objects = new String[] { getSelectedFile().name};
                DialogUI dlg = getInfoFactory().createDeleteDialog( objects, getMainComponent());
                dlg.start();
                if (dlg.getSelectedIndex() != 0)
                    return;
                delete();
            }
            catch (RaplaException ex) {
                showException( ex, getMainComponent());
            }
        }
    }
    
    final SaveAction saveAction;
    final PublishAction publishAction;
    final DeleteAction deleteAction;
    
    class FileEntry implements Comparable<FileEntry>
    {
    	String name;
    	boolean isDefault;
    	public FileEntry(String name) {
    		this.name=name;
    	}
    	
    	public String toString()
    	{
    		return name;
    	}
    	
		public int compareTo(FileEntry o) 
		{
			return toString().compareTo( o.toString());
		}
		
		public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + (isDefault ? 1231 : 1237);
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }
        
		public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FileEntry other = (FileEntry) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (isDefault != other.isDefault)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }
        
		private SavedCalendarView getOuterType() {
            return SavedCalendarView.this;
        }
    }
    
    
    public SavedCalendarView(RaplaContext context, final MultiCalendarView calendarContainer, final ResourceSelection resourceSelection, final CalendarSelectionModel model) throws RaplaException {
        super(context);
        // I18nBundle i18n = getI18n();
        saveAction = new SaveAction(context);
        publishAction = new PublishAction(context);
        deleteAction = new DeleteAction( context);
        this.model = model;
        this.calendarContainer = calendarContainer;
        this.resourceSelection = resourceSelection;
        JButton save = new JButton();
        JButton publish = new JButton();
        JButton delete = new JButton();
        
       
        toolbar.setFloatable( false);
        selectionBox = new JComboBox();
        selectionBox.setToolTipText( getString("calendar"));
        selectionBox.setMinimumSize( new Dimension(120,30));
        selectionBox.setSize( new Dimension(150,30));
        // rku: updated, the next line prevented resizing the combobox when using the divider of the splitpane
        // especially, when having long filenames this is annoying

        //selectionBox.setMaximumSize( new Dimension(200,30));
        selectionBox.setPreferredSize( new Dimension(150,30));
        
        save.setAction( saveAction);
        publish.setAction(publishAction);
        RaplaMenu settingsMenu = getService(InternMenus.CALENDAR_SETTINGS);
        settingsMenu.insertAfterId(new JMenuItem(saveAction), null);
        if ( publishAction.hasPublishActions())
        {
            settingsMenu.insertAfterId(new JMenuItem(publishAction), null);
        }
        settingsMenu.insertAfterId(new JMenuItem(deleteAction),null);
        
        delete.setAction( deleteAction);
       // toolbar.add(new JLabel(getString("calendar")));
       // toolbar.add(new JToolBar.Separator());
        toolbar.add( selectionBox);
        toolbar.add(new JToolBar.Separator());
        
        toolbar.add(save);
        save.setText("");
        publish.setText("");
        delete.setText("");
        if ( publishAction.hasPublishActions())
        {
            toolbar.add(publish);
        }
        toolbar.add(delete);
        toolbar.setBorder( BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        update();
        
        final int defaultIndex = getDefaultIndex();
        if (defaultIndex != -1)
        	selectionBox.setSelectedIndex(defaultIndex); 
        else
        	selectionBox.setSelectedIndex(0); 
        updateTooltip();
        selectionBox.addActionListener( this);
    }
    
    public void actionPerformed(ActionEvent e) {
    	updateTooltip();
         
    	if ( !listenersEnabled)
        {
            return;
        }
        try 
        {
        	changeSelection();
        }
        catch (RaplaException ex) {
            showException( ex, getMainComponent());
        }
    }
    
	protected void updateTooltip() {
		Object selectedItem = selectionBox.getSelectedItem();
		if ( selectedItem != null )
		{
			selectionBox.setToolTipText(selectedItem.toString());
		}
	}
 
    public JComponent getComponent() {
        return toolbar;
    }
    
    private void changeSelection() throws RaplaException
    {
        FileEntry selectedFile = getSelectedFile();

        // keep current date   in mind
        final Date tmpDate = model.getSelectedDate();
        // keep in mind if current model had saved date
      
        String tmpModelHasStoredCalenderDate = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
        if(tmpModelHasStoredCalenderDate == null)
        	tmpModelHasStoredCalenderDate = "false"; 
        // load sets stored date
        if ( selectedFile.isDefault)
        {
        	model.load(null);
        }
        else
        {
        	model.load(selectedFile.name);
        }
        closeFilter();
        // check if new model had stored date
        String newModelHasStoredCalenderDate = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
        if(newModelHasStoredCalenderDate == null)
        	newModelHasStoredCalenderDate = "false"; 
        if ("false".equals(newModelHasStoredCalenderDate)) {

            if ("false".equals(tmpModelHasStoredCalenderDate))
                    // if we are switching from a model with saved date to a model without date we reset to current date
            {
               model.setSelectedDate(tmpDate);
            } else {
               model.setSelectedDate(new Date());
            }
        }
            
        updateActions();
		Entity preferences = getQuery().getPreferences();
		UpdateResult modificationEvt = new UpdateResult( getUser());
        modificationEvt.addOperation( new UpdateResult.Change(preferences, preferences));
        resourceSelection.dataChanged(modificationEvt);
        calendarContainer.update(modificationEvt);
        calendarContainer.getSelectedCalendar().scrollToStart();
    }

	public void closeFilter() {
		// CKO Not a good solution. FilterDialogs should close themselfs when model changes.
        // BJO 00000139
        if(resourceSelection.getFilterButton().isOpen())
        	resourceSelection.getFilterButton().doClick();
        if(calendarContainer.getFilterButton().isOpen())
        	calendarContainer.getFilterButton().doClick();
    
        // BJO 00000139
	}
   
    public void update() throws RaplaException
    {
        updateActions();
        try
        {
            listenersEnabled = false;
            final FileEntry item = getSelectedFile();
            DefaultComboBoxModel model = updateModel();
           
            if ( item != null )
            {
                model.setSelectedItem( item );
            }
        }
        finally
        {
            listenersEnabled = true;
        }
    
    }

	@SuppressWarnings("unchecked")
	protected DefaultComboBoxModel updateModel() throws RaplaException,
			EntityNotFoundException {
		final Preferences preferences = getQuery().getPreferences();
		Map<String, CalendarModelConfiguration> exportMap= preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
		filenames.clear();
         
		if ( exportMap != null) {
		    for (Iterator<String> it= exportMap.keySet().iterator();it.hasNext();) {
		        String filename = it.next();
		        filenames.add( new FileEntry(filename));
		    }
		}
		// rku: sort entries by name
		Collections.sort(filenames);
		
		final FileEntry defaultEntry = new FileEntry(getString("default") ); 
		defaultEntry.isDefault = true;
		filenames.add(0,defaultEntry);
		DefaultComboBoxModel model = new DefaultComboBoxModel(filenames.toArray());
		selectionBox.setModel(model);
		return model;
	}

    private void updateActions() {
        FileEntry selectedFile = getSelectedFile();
        boolean isDefault = selectedFile == null || selectedFile.isDefault ;
        final boolean modifyPreferencesAllowed = isModifyPreferencesAllowed() && getModification().getTemplate() == null;
        saveAction.setEnabled(modifyPreferencesAllowed );
        publishAction.setEnabled( modifyPreferencesAllowed);
        deleteAction.setEnabled( !isDefault && modifyPreferencesAllowed);
    }
    
    public void save(final String filename) throws RaplaException 
    {
        Preferences preferences = ((CalendarModelImpl)model).createStorablePreferences(filename);
        getModification().store( preferences);
        
        // TODO Enable undo with a specific implementation, that does not overwrite all preference changes and regards dynamic type changes
//        Map<Preferences, Preferences> originalMap = getModification().getPersistant(Collections.singletonList(preferences) );
//        Preferences original = originalMap.get(preferences);
//		Collection<Preferences> originalList = original != null ? Collections.singletonList( original): null;
//        Collection<Preferences> newList = Collections.singletonList(preferences);
//        String file = (filename != null) ? filename : getString("default");
//        String commandoName = getString("save")+ " " + getString("calendar") + " " + file ;
//        SaveUndo<Preferences> cmd = new SaveUndo<Preferences>(getContext(), newList, originalList, commandoName);
//        getModification().getCommandHistory().storeAndExecute( cmd);

    }
    
    private void delete() throws RaplaException
    {
    	final FileEntry selectedFile = getSelectedFile();
    	if ( selectedFile == null || selectedFile.isDefault)
    	{
    		return;
    	}
        final Preferences preferences =  newEditablePreferences();
        Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
        Map<String,CalendarModelConfiguration> newMap = new TreeMap<String,CalendarModelConfiguration>();
        for (Iterator<String> it= exportMap.keySet().iterator();it.hasNext();) {
            String filename = it.next();
            if (!filename.equals( selectedFile.name)) {
            	CalendarModelConfiguration entry = exportMap.get( filename );
				newMap.put( filename, entry);
            }
        }
        preferences.putEntry( AutoExportPlugin.PLUGIN_ENTRY, getModification().newRaplaMap( newMap ));

        getModification().store( preferences);
        // TODO Enable undo with a specific implementation, that does not overwrite all preference changes and regards dynamic type changes
//        Collection<Preferences> originalList = Collections.singletonList(getQuery().getPreferences());
//        Collection<Preferences> newList = Collections.singletonList(preferences);
//        String commandoName = getString("delete")+ " " + getString("calendar") + " " +  selectedFile.name;
//        SaveUndo<Preferences> cmd = new SaveUndo<Preferences>(getContext(), newList, originalList, commandoName);
//        getModification().getCommandHistory().storeAndExecute( cmd);
        final int defaultIndex = getDefaultIndex();
        if (defaultIndex != -1)
            selectionBox.setSelectedIndex(defaultIndex);
        else
        	selectionBox.setSelectedIndex(0);
        //changeSelection();
    }

	private int getDefaultIndex() {
		return ((DefaultComboBoxModel) selectionBox.getModel()).getIndexOf(getString("default"));
	}

    private void save() {
        final FileEntry selectedFile = getSelectedFile();
        
        final Component parentComponent = getMainComponent();
        try {
            
   
        JPanel panel = new JPanel();
        final JTextField textField = new JTextField(20);
        addCopyPaste( textField);
        String dateString; 	
    	if( model.getViewId().equals(ReservationTableViewFactory.TABLE_VIEW) 
    	 || model.getViewId().equals(AppointmentTableViewFactory.TABLE_VIEW)) 
            dateString = getRaplaLocale().formatDate(model.getStartDate()) + " - " + getRaplaLocale().formatDate(model.getEndDate());
    	else
    		dateString =  getRaplaLocale().formatDate(model.getSelectedDate());

        final JCheckBox saveSelectedDateField = new JCheckBox(getI18n().format("including_date",dateString));
        
        panel.setPreferredSize( new Dimension(600,300));
        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.FILL},{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL}}));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        panel.add(new JLabel(getString("file.enter_name") +":"), "0,0");
        panel.add(textField, "2,0");
        addCopyPaste( textField);
        panel.add(saveSelectedDateField, "2,2");
        
        final String entry = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
        if(entry != null)
        	saveSelectedDateField.setSelected(entry.equals("true")); 

        @SuppressWarnings("unchecked")
		final JList list = new JList(filenames.toArray());

        panel.add( new JScrollPane(list), "0,4,2,1");
        //int selectedIndex = selectionBox.getSelectedIndex();
        Object selectedItem = selectionBox.getSelectedItem();
        if ( selectedItem != null)
        {
        	list.setSelectedValue( selectedItem,true);
        }
        textField.setText( selectedFile.toString());
        list.addListSelectionListener( new ListSelectionListener() {

            public void valueChanged( ListSelectionEvent e )
            {
            	FileEntry filename = (FileEntry) list.getSelectedValue();
                if ( filename != null) {
                    textField.setText( filename.toString() );
                    try {
                        final CalendarSelectionModel m = getModification().newCalendarModel( getUser());
                        if (filename.isDefault )
                        {
                         	m.load(null);
                        }
                        else
                        {
                        	m.load(filename.toString());
                        }
                        final String entry = m.getOption(CalendarModel.SAVE_SELECTED_DATE);
                        if( entry != null)
                        	saveSelectedDateField.setSelected(entry.equals("true"));
                    } catch (RaplaException ex) {
                           showException( ex, getMainComponent());
                    }
                }
              
            }

        });
        
        final DialogUI dlg = DialogUI.create(
                getContext(),
                                        parentComponent,true,panel,
                                       new String[] {
                                           getString("save")
                                           ,getString("cancel")
                                       });
        dlg.setTitle(getString("save") + " " +getString("calendar_settings"));
        dlg.getButton(0).setIcon(getIcon("icon.save"));
        dlg.getButton(1).setIcon(getIcon("icon.cancel"));
        dlg.getButton(0).setAction( new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                String filename = textField.getText().trim();
                if (filename.length() == 0)
                {
                    showWarning(getString("error.no_name"), parentComponent);
                    return;
                }
                dlg.close();
               
                
                try 
                {
                    String saveSelectedDate = saveSelectedDateField.isSelected() ? "true" : "false";
                    model.setOption( CalendarModel.SAVE_SELECTED_DATE, saveSelectedDate);
                    FileEntry selectedValue = (FileEntry)list.getSelectedValue();
                    final boolean isDefault;
                    if ( selectedValue != null)
                    {
                    	isDefault = selectedValue.isDefault && filename.equals( selectedValue.name );
                    }
                    else
                    {
                    	isDefault = filename.equals( getString("default") );
                    }
                    if ( isDefault)
                    {
                    	save(null);
                    	try
	                    {
	                        listenersEnabled = false;
	                        selectionBox.setSelectedIndex(0);
	                    }
	                    finally
	                    {
	                        listenersEnabled = true;
	                    } 
                    }
                    else
                    {
	                    // We reset exports for newly created files
                    	{
                    		FileEntry fileEntry = findInModel(filename);
							if ( fileEntry == null)
		                    {
		                        model.resetExports();
							}
                    	}
	                    save(filename);
	                    try
	                    {
	                    	listenersEnabled = false;
	                    	updateModel();
	                    	FileEntry fileEntry = findInModel(filename);
	                        if ( fileEntry != null)
	                        {
	                        	selectionBox.setSelectedItem( fileEntry);
	                        }
	                        else 
	                        {
	                        	selectionBox.setSelectedIndex(0);
	                        }
	                    }
	                    finally
	                    {
	                        listenersEnabled = true;
	                    }
                    }
                }
                catch (RaplaException ex) 
                {
                    showException( ex, parentComponent);
                }
                
            }

            private FileEntry findInModel(String filename) 
            {
            	ComboBoxModel selection = selectionBox.getModel();
                for ( int i=0;i<selection.getSize();i++)
                {
                    final FileEntry elementAt = (FileEntry) selection.getElementAt( i);
                    if ( !elementAt.isDefault  && elementAt.toString().equals(filename))
                    {
                        return elementAt;
                    }
                }
                return null;
            }



        });
        dlg.start();
        } catch (RaplaException ex) {
            showException( ex, parentComponent);
        }
    }
    
    private FileEntry getSelectedFile() 
    {
		ComboBoxModel model2 = selectionBox.getModel();
        FileEntry selectedItem = (FileEntry)model2.getSelectedItem();
		return selectedItem;
    }

    
}
