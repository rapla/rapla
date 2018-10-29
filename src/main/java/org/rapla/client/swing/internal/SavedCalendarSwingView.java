package org.rapla.client.swing.internal;

import io.reactivex.functions.Action;
import org.rapla.RaplaResources;
import org.rapla.client.CalendarPlacePresenter;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.client.internal.SavedCalendarInterface;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.toolkit.ActionWrapper;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@DefaultImplementation(of=SavedCalendarInterface.class,context = InjectionContext.swing)
public class SavedCalendarSwingView extends RaplaGUIComponent implements SavedCalendarInterface,ActionListener
{

    JComboBox selectionBox;
    private final ApplicationEventBus eventBus;
    private boolean listenersEnabled = true;
    List<FileEntry> filenames = new ArrayList<>();
    final CalendarSelectionModel model;
    JToolBar toolbar = new JToolBar();
    private final Set<PublishExtensionFactory> extensionFactories;
    private final StartupEnvironment environment;
    class SaveAction extends RaplaAction
    {

        public SaveAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)  {
            super(facade, i18n, raplaLocale, logger);
            final String name = getString("save") ;
            putValue(NAME,name);
            putValue(SHORT_DESCRIPTION,name);
            setIcon(i18n.getIcon("icon.save"));
        }

        public void actionPerformed() {
            save();
        }
    }
    
    class PublishAction extends RaplaAction
    {
        PublishDialog publishDialog;
        public PublishAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)  {
            super(facade, i18n, raplaLocale, logger);
            final String name = getString("publish") ;
            putValue(NAME,name);
            putValue(SHORT_DESCRIPTION,name);
            setIcon(i18n.getIcon("icon.export"));
            publishDialog = new PublishDialog(environment,facade, i18n, raplaLocale, logger, extensionFactories,  dialogUiFactory);
        }

        public void actionPerformed() {
            try 
            {   
				FileEntry filename = getSelectedFile();
                final PopupContext popupContext = dialogUiFactory.createPopupContext(null);
                if ( filename.isDefault)
                {
                  	publishDialog.export(model, popupContext, null);
                }
                else
                {
                	publishDialog.export(model, popupContext, filename.name);
                }
            }
            catch (RaplaException ex) {
                dialogUiFactory.showException( ex, null);
            }
        }
        
        public boolean hasPublishActions()
        {
            return publishDialog.hasPublishActions();
        }


    }
    
    class DeleteAction extends RaplaAction
    {
        public DeleteAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
        {
            super(facade, i18n, raplaLocale, logger);
            final String name = getString("delete");
            putValue(NAME,name);
            putValue(SHORT_DESCRIPTION,name);
            setIcon(i18n.getIcon("icon.delete"));
            
        }

        public void actionPerformed() {
            String[] objects = new String[] { getSelectedFile().name};
            PopupContext popupContext = null;
            deleteDialogInterface.showDeleteDialog(popupContext,objects).thenAccept(result->{if (result) delete();}).exceptionally((ex)->dialogUiFactory.showException(ex,popupContext));
        }
    }
    
    final SaveAction saveAction;
    final PublishAction publishAction;
    final DeleteAction deleteAction;

    private final DeleteDialogInterface deleteDialogInterface;

    private final DialogUiFactoryInterface dialogUiFactory;

    private final IOInterface ioInterface;
    
    static class FileEntry implements Comparable<FileEntry>
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
            if (isDefault != other.isDefault)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }
        

    }

    @Inject
    public SavedCalendarSwingView(RaplaMenuBarContainer bar, ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ApplicationEventBus eventBus, final CalendarSelectionModel model, Set<PublishExtensionFactory> extensionFactories, StartupEnvironment environment,
                                  DeleteDialogInterface deleteDialogInterface,  DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface) throws RaplaInitializationException {
        super(facade, i18n, raplaLocale, logger);
        this.eventBus = eventBus;
        this.extensionFactories = extensionFactories;
        this.environment = environment;
        this.deleteDialogInterface = deleteDialogInterface;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        // I18nBundle i18n = getI18n();
        saveAction = new SaveAction(facade, i18n, raplaLocale, logger);
        publishAction = new PublishAction(facade, i18n, raplaLocale, logger);
        deleteAction = new DeleteAction( facade, i18n, raplaLocale, logger);
        this.model = model;
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
        
        save.setAction(new ActionWrapper(saveAction));
        publish.setAction(new ActionWrapper(publishAction));
        RaplaMenu settingsMenu = bar.getSettingsMenu();
        settingsMenu.insertAfterId(()->new JMenuItem(new ActionWrapper(saveAction)), null);
        if ( publishAction.hasPublishActions())
        {
            settingsMenu.insertAfterId(()->new JMenuItem(new ActionWrapper(publishAction)), null);
        }
        settingsMenu.insertAfterId(()->new JMenuItem(new ActionWrapper(deleteAction)),null);
        
        delete.setAction(new ActionWrapper(deleteAction));
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

        try
        {
            update();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }

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
            PopupContext popupContext = dialogUiFactory.createPopupContext( null);
            dialogUiFactory.showException( ex, popupContext);
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

        String place = selectedFile.isDefault ? null: selectedFile.name;

        eventBus.publish( new ApplicationEvent( CalendarPlacePresenter.PLACE_ID, place, null, null));
    }

    @Override
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
	protected DefaultComboBoxModel updateModel() throws RaplaException
    {
        final User user = getClientFacade().getUser();
        final Preferences preferences = getFacade().getPreferences(user);
		RaplaMap<CalendarModelConfiguration> exportMap= preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
		filenames.clear();
         
		if ( exportMap != null) {
		    exportMap.keySet().toArray(new String[]{});
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

    public void updateActions() {
        FileEntry selectedFile = getSelectedFile();
        boolean isDefault = selectedFile == null || selectedFile.isDefault ;
        final boolean modifyPreferencesAllowed = isModifyPreferencesAllowed() && getClientFacade().getTemplate() == null;
        saveAction.setEnabled(modifyPreferencesAllowed );
        publishAction.setEnabled( modifyPreferencesAllowed);
        deleteAction.setEnabled( !isDefault && modifyPreferencesAllowed);
    }
    
    private Promise<Void> save(final String filename)
    {
        return ((CalendarModelImpl)model).save(filename);

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
        final User user = getUser();
        final RaplaFacade facade = getFacade();

        facade.editAsync(facade.getPreferences(user)).thenApply(preferences -> {
                    RaplaMap<CalendarModelConfiguration> exportMap = preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
                    Map<String, CalendarModelConfiguration> newMap = new TreeMap<>();
                    for (Iterator<String> it = exportMap.keySet().iterator(); it.hasNext(); ) {
                        String filename = it.next();
                        if (!filename.equals(selectedFile.name)) {
                            CalendarModelConfiguration entry = exportMap.get(filename);
                            newMap.put(filename, entry);
                        }
                    }
                    preferences.putEntry(AutoExportPlugin.PLUGIN_ENTRY, facade.newRaplaMapForMap(newMap));
                    // TODO Enable undo with a specific implementation, that does not overwrite all preference changes and regards dynamic type changes
                    //        Collection<Preferences> originalList = Collections.singletonList(getQuery().getPreferences());
                    //        Collection<Preferences> newList = Collections.singletonList(preferences);
                    //        String commandoName = getString("delete")+ " " + getString("calendar") + " " +  selectedFile.name;
                    //        SaveUndo<Preferences> cmd = new SaveUndo<Preferences>(getContext(), newList, originalList, commandoName);
                    //        getModification().getCommandHistory().storeAndExecute( cmd);
                    return preferences;
                }).thenCompose(preferences->
        //changeSelection();
        facade.dispatch(Collections.singleton(preferences),Collections.emptyList()).thenRun(()
        ->
                {
                    final int defaultIndex = getDefaultIndex();
                    if (defaultIndex != -1)
                        selectionBox.setSelectedIndex(defaultIndex);
                    else
                        selectionBox.setSelectedIndex(0);
                }
        )).exceptionally((ex)->dialogUiFactory.showException(ex, null));
    }

	private int getDefaultIndex() {
		return ((DefaultComboBoxModel) selectionBox.getModel()).getIndexOf(getString("default"));
	}

    private void save() {
        final FileEntry selectedFile = getSelectedFile();
        
        final Component parentComponent = getMainComponent();
        JPanel panel = new JPanel();
        final JTextField textField = new JTextField(20);
        final RaplaLocale raplaLocale = getRaplaLocale();
        addCopyPaste( textField, getI18n(), raplaLocale, ioInterface, getLogger());
        String dateString;
        CalendarSelectionModel model = this.model;
        dateString = CalendarModelImpl.getStartEndDate(raplaLocale, model);

        final JCheckBox saveSelectedDateField = new JCheckBox(getI18n().format("including_date",dateString));
        
        panel.setPreferredSize( new Dimension(600,300));
        panel.setLayout(new TableLayout( new double[][] {{TableLayout.PREFERRED,5,TableLayout.FILL},{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL}}));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        panel.add(new JLabel(getString("file.enter_name") +":"), "0,0");
        panel.add(textField, "2,0");
        addCopyPaste( textField, getI18n(), raplaLocale, ioInterface, getLogger());
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
        final SwingPopupContext popupContext = new SwingPopupContext(parentComponent, null);

        list.addListSelectionListener(e -> {
            FileEntry filename = (FileEntry) list.getSelectedValue();
            if ( filename != null) {
                textField.setText( filename.toString() );
                try {
                    final CalendarSelectionModel m = getFacade().newCalendarModel( getUser());
                    if (filename.isDefault )
                    {
                         m.load(null);
                    }
                    else
                    {
                        m.load(filename.toString());
                    }
                    final String entry1 = m.getOption(CalendarModel.SAVE_SELECTED_DATE);
                    if( entry1 != null)
                        saveSelectedDateField.setSelected(entry1.equals("true"));
                } catch (RaplaException ex) {
                    dialogUiFactory.showException( ex, popupContext);
                }
            }
        });

        final DialogInterface dlg = dialogUiFactory.createContentDialog(
                popupContext, panel,
                                       new String[] {
                                           getString("save")
                                           ,getString("cancel")
                                       });
        dlg.setTitle(getString("save") + " " +getString("calendar_settings"));
        dlg.getAction(0).setIcon(i18n.getIcon("icon.save"));
        dlg.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
        dlg.getAction(0).setRunnable( new Runnable() {
            private static final long serialVersionUID = 1L;

            public void run() {
                String filename = textField.getText().trim();
                if (filename.length() == 0)
                {
                    dialogUiFactory.showWarning(getString("error.no_name"), popupContext);
                    return;
                }
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
                final Promise<Void> promise;
                listenersEnabled = false;
                dlg.busy(getI18n().getString("save"));
                if ( isDefault)
                {
                    promise = save(null).finally_(() -> { listenersEnabled = true;dlg.idle();}).thenRun(() -> selectionBox.setSelectedIndex(0));
                    handleException(promise);
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
                    Action update = ()->
                    {
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
                    };
                    promise =save(filename).thenRun(update).finally_(()->{ listenersEnabled = true;dlg.idle();});
                }
                handleException(promise.thenRun(()->dlg.close()));
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
        dlg.start(true).thenApply((index) -> Promise.VOID).exceptionally((ex)->dialogUiFactory.showException( ex, null));
    }

    private void handleException(Promise<Void> promise) {
        promise.exceptionally((ex)->dialogUiFactory.showException(ex,null));
    }

    private FileEntry getSelectedFile() 
    {
		ComboBoxModel model2 = selectionBox.getModel();
        FileEntry selectedItem = (FileEntry)model2.getSelectedItem();
		return selectedItem;
    }

    
}
