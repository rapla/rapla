package org.rapla.plugin.ical;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import org.rapla.components.iolayer.FileContent;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.TreeAllocatableSelection;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;

/**
 * 
 * Creates and shows a dialog for the iCal-import to select a URL or file. Also, you can add resources if you like.
 * 
 * @author Jan Fischer
 */

public class ImportFrom2iCalMenu extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener {

	JMenuItem item;
	String id = "ical";
	ICalImport importService;	
	public ImportFrom2iCalMenu(RaplaContext context,ICalImport importService)
	{
		super(context);
		this.importService = importService;
		setChildBundleName(ImportFromICalPlugin.RESOURCE_FILE);

		item = new JMenuItem(getString("ical.import"));
		item.setIcon(getIcon("icon.import"));
		item.addActionListener(this);
	}
	
	public JMenuItem getMenuElement()  {
        return item;
    }
	
	public String getId() {
		return id;
	}
	
	public void actionPerformed(ActionEvent evt) {
        try {
        	show();
        } catch (Exception ex) {
            showException(ex, getMainComponent());
        }
    }

	String bufferedICal;

	/**
	 * shows a dialog to to import an iCal calendar.
	 * 
	 * @throws RaplaException
	 */
	public void show() throws RaplaException {
		final TreeAllocatableSelection resourceSelection = new TreeAllocatableSelection( getContext());
		JPanel container = new JPanel();
		final JPanel superpanel = new JPanel();
		superpanel.setLayout(new TableLayout(
				new double[][] { { TableLayout.PREFERRED }, { TableLayout.PREFERRED, 2, TableLayout.PREFERRED } }));

		superpanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		superpanel.setAlignmentY(Component.TOP_ALIGNMENT);

		final JPanel panel1 = new JPanel();

		Dimension size = new Dimension(850, 125);
		panel1.setPreferredSize(size);
		panel1.setMinimumSize(size);


		panel1.setLayout(new TableLayout(
				new double[][] { {TableLayout.PREFERRED, 5, TableLayout.FILL, },
                                 { TableLayout.PREFERRED, 5,
                                   TableLayout.PREFERRED, 5,
                                   TableLayout.PREFERRED, 5,
                                   TableLayout.PREFERRED, 5,
                                   TableLayout.PREFERRED, 5,
                                   TableLayout.PREFERRED, 5, } }));
		panel1.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		final String urlText = getString("enter_url");
		final JTextField urlField = new JTextField(urlText);
		addCopyPaste(urlField);
		panel1.add(urlField, "2,0");

		final JTextField fileField = new JTextField(getString("click_for_file"));
		panel1.add(fileField, "2,2");
		fileField.setEditable(false);
		fileField.setBackground(new Color(240, 240, 255));
		fileField.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		
		ButtonGroup bg = new ButtonGroup();
		final JRadioButton urlRadio = new JRadioButton(getString("url"));
		panel1.add(urlRadio, "0,0");
		bg.add(urlRadio);
		final JRadioButton fileRadio = new JRadioButton(getString("file"));
		panel1.add(fileRadio, "0,2");
		bg.add(fileRadio);

		@SuppressWarnings("unchecked")
		final JComboBox comboEventType = new JComboBox(getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION));
        final JLabel labelEventType = new JLabel(getString("reservation_type"));
        panel1.add(labelEventType, "0,4");
        panel1.add(comboEventType, "2,4");

        final JComboBox comboNameAttribute = new JComboBox();
        final JLabel labelNameAttribute = new JLabel(getString("attribute"));
        panel1.add(labelNameAttribute, "0,6");
        panel1.add(comboNameAttribute, "2,6");

        comboEventType.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    updateNameAttributes(comboEventType, comboNameAttribute);
                }
            }
        });

        updateNameAttributes(comboEventType, comboNameAttribute);

		urlRadio.setSelected(true);
		fileField.setEnabled(false);

        setRenderer(comboEventType, comboNameAttribute);

        superpanel.add(panel1, "0,0");
		superpanel.add(resourceSelection.getComponent(), "0,2");

		container.setLayout( new BorderLayout());
		JLabel warning = new JLabel("Warning: The ical import is still experimental. Not all events maybe imported correctly.");
		warning.setForeground( Color.RED);
		container.add( warning, BorderLayout.NORTH);
		container.add( superpanel, BorderLayout.CENTER);
		
		final DialogUI dlg = DialogUI.create(getContext(),
                getMainComponent(), false, container, new String[] { getString("import"), getString("cancel") });
		
        final ActionListener radioListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean isURL = urlRadio.isSelected() && urlRadio.isEnabled();
				boolean isFile = fileRadio.isSelected() && fileRadio.isEnabled();
				urlField.setEnabled(isURL);
				fileField.setEnabled(isFile);
			}
		};

		urlRadio.addActionListener(radioListener);
		fileRadio.addActionListener(radioListener);
		MouseAdapter listener = new MouseAdapter(){
			
			@Override
			 public void mouseClicked(MouseEvent e) {
				Object source = e.getSource();
				if ( source == urlField)
				{
					if (urlField.isEnabled() && urlField.getText().equalsIgnoreCase(urlText)) {
						urlField.setText("");
					}
					dlg.getButton(0).setEnabled( true );
				} 
				else if ( source == fileField)
				{
					dlg.getButton(0).setEnabled( bufferedICal != null );
	                if (fileField.isEnabled()) {
	                    final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
	                    IOInterface io =  getService( IOInterface.class);
	                    try {
	                        FileContent file = io.openFile( frame, null, new String[] {".ics"});
	                        if ( file != null) 
	                        {
	                            BufferedReader reader = new BufferedReader(new InputStreamReader( file.getInputStream()));
	                            StringBuffer buf = new StringBuffer();
	                            while ( true)
	                            {
	                                String line = reader.readLine();
	                                if ( line == null)
	                                {
	                                    break;
	                                }
	                                buf.append( line);
	                                buf.append( "\n");
	                            }
	                            fileField.setText(file.getName());
	                            bufferedICal = buf.toString();
	                            dlg.getButton(0).setEnabled( true );
	                        }
	                    } catch (IOException ex) {
	                        bufferedICal = null;
	                        dlg.getButton(0).setEnabled( false );
	                        showException(ex, getMainComponent());
	                    }
	             
	                }
				}
			}
		};
	    urlField.addMouseListener(listener);
	    fileField.addMouseListener(listener);
		
		final String title = "iCal-" + getString("import");
		dlg.setTitle(title);
		dlg.setSize(new Dimension(850, 100));
		// dlg.setResizable(false);
		dlg.getButton(0).setIcon(getIcon("icon.import"));
		dlg.getButton(1).setIcon(getIcon("icon.cancel"));

		dlg.getButton(0).setAction(new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent e) {

				Collection<Allocatable> liste = resourceSelection.getAllocatables();
				try {
					boolean isURL = urlRadio.isSelected() && urlRadio.isEnabled();
				    String iCal;
					if (isURL) {
						iCal = urlField.getText();
					} else {
					    iCal = bufferedICal;
					}
					String[] allocatableIds = new String[liste.size()];
					int i = 0;
					for ( Allocatable alloc:liste)
					{
						allocatableIds[i++] = alloc.getId();
					}
                    final DynamicType dynamicType = (DynamicType) comboEventType.getSelectedItem();
                    if (dynamicType == null)
                        return;
                    String eventTypeKey = dynamicType.getKey();

                    Object selectedItem = comboNameAttribute.getSelectedItem();
                    if (selectedItem == null)
                        return;
                    String eventTypeAttributeNameKey = ((Attribute) selectedItem).getKey();

                    Integer[] status  = importService.importICal(iCal, isURL, allocatableIds, eventTypeKey, eventTypeAttributeNameKey).get();
                    int eventsInICal = status[0];
            		int eventsImported = status[1];
            		int eventsPresent = status[2];
            		int eventsSkipped = status[3];
            		getClientFacade().refresh();
					dlg.close();
					String text = "Imported " + eventsImported + "/" + eventsInICal + ". " + eventsPresent + " present";
					if (eventsSkipped > 0)
					{
						text += " and " + eventsSkipped + " skipped ";
					}
					else
					{
						text+=".";
					}
					DialogUI okDlg = DialogUI.create(getContext(), getMainComponent(), false,  title, text);
					okDlg.start();
				} catch (Exception e1) {
					showException(e1, getMainComponent());
				}

			}
		});
		dlg.start();
	}

	@SuppressWarnings("unchecked")
	private void setRenderer(final JComboBox comboEventType,
			final JComboBox comboNameAttribute) {
		final DefaultListCellRenderer aRenderer = new NamedListCellRenderer();
        comboEventType.setRenderer(aRenderer);
        comboNameAttribute.setRenderer(aRenderer);
	}


    final protected TreeFactory getTreeFactory() {
        return getService(TreeFactory.class);
    }

    private class NamedListCellRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus)
        {
            if (value != null && value instanceof Named)
                value = ((Named) value).getName(ImportFrom2iCalMenu.this.getLocale());
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }

    @SuppressWarnings("unchecked")
	private void updateNameAttributes(JComboBox comboEventType, JComboBox comboNameAttribute) {
        final DynamicType dynamicType = (DynamicType) comboEventType.getSelectedItem();
        ComboBoxModel result;
        if (dynamicType == null) {
            //no dynamic type found, so empty combobox model
            result = new DefaultComboBoxModel();
        } else {
            // found one, so determine all string attribute of event type and update model
            final Attribute[] attributes = dynamicType.getAttributes();
            final List<Attribute> attributeResult = new ArrayList<Attribute>();
            for (Attribute attribute : attributes) {
                if (attribute.getType().is(AttributeType.STRING))
                {
                    attributeResult.add(attribute);
                }
            }
			DefaultComboBoxModel defaultComboBoxModel = new DefaultComboBoxModel(attributeResult.toArray());
			result = defaultComboBoxModel;
        }
        //initialize model
        comboNameAttribute.setModel(result);
    }
	
}
