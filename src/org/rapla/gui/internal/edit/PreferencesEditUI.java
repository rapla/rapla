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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.gui.EditComponent;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.PluginOptionPanel;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.toolkit.RaplaTree;

public class PreferencesEditUI extends RaplaGUIComponent
    implements
        EditComponent<Preferences>
        ,ChangeListener
{
    private JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    protected TitledBorder selectionBorder;
    protected RaplaTree jPanelSelection = new RaplaTree();
    protected JPanel jPanelContainer = new JPanel();
    protected JPanel container = new JPanel();
    JLabel messages = new JLabel();
    JPanel defaultPanel = new JPanel();
    OptionPanel lastOptionPanel;
    Preferences preferences;

    /** called during initialization to create the info component */
    public PreferencesEditUI(RaplaContext context) {
        super( context);
        jPanelContainer.setLayout(new BorderLayout());
        jPanelContainer.add(messages,BorderLayout.SOUTH);
        messages.setForeground( Color.red);
        Border  emptyLineBorder = new Border() {
            Insets insets = new Insets(2,0,2,0);
            Color COLOR = Color.LIGHT_GRAY;
            public void paintBorder( Component c, Graphics g, int x, int y, int width, int height )
            {
                g.setColor( COLOR );
                g.drawLine(0,1, c.getWidth(), 1);
                g.drawLine(0,c.getHeight()-2, c.getWidth(), c.getHeight()-2);
            }
        
            public Insets getBorderInsets( Component c )
            {
                return insets;
            }
        
            public boolean isBorderOpaque()
            {
                return true;
            }
        };
        content.setBorder( emptyLineBorder);
        jPanelContainer.add(content,BorderLayout.CENTER);
        jPanelSelection.getTree().setCellRenderer(getTreeFactory().createRenderer());
        jPanelSelection.setToolTipRenderer(getTreeFactory().createTreeToolTipRenderer());
        container.setPreferredSize( new Dimension(700,550));
        content.setLeftComponent(jPanelSelection);
        content.setRightComponent(container);
        content.setDividerLocation(260);
        Border emptyBorder=BorderFactory.createEmptyBorder(4,4,4,4);
        selectionBorder = BorderFactory.createTitledBorder(emptyBorder, getString("selection") + ":");
        jPanelSelection.setBorder(selectionBorder);
        content.setResizeWeight(0.4);
        jPanelSelection.addChangeListener(this);
    }

    final private TreeFactory getTreeFactory() {
        return getService(TreeFactory.class);
    }

	protected OptionPanel[] getPluginOptions() throws RaplaException {
        Collection<PluginOptionPanel> panelList = getContainer().lookupServicesFor( RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION);
        List<OptionPanel> optionList = new ArrayList<OptionPanel>();
        List<PluginDescriptor<ClientServiceContainer>> pluginList = getService( ClientServiceContainer.CLIENT_PLUGIN_LIST);
        for (final PluginDescriptor<ClientServiceContainer> plugin:pluginList) {
            OptionPanel optionPanel = find(panelList, plugin);
            if ( optionPanel == null ) {
            	
                optionPanel = new DefaultPluginOption(getContext()) {

                    @SuppressWarnings("unchecked")
					public Class<? extends PluginDescriptor<ClientServiceContainer>> getPluginClass() {
                        return (Class<? extends PluginDescriptor<ClientServiceContainer>>) plugin.getClass();
                    }

                    @Override
                    public String getName(Locale locale) {
                    	String string = plugin.getClass().getSimpleName();
						return string;
                    }

                };
            }
            optionList.add( optionPanel );
        }
        sort( optionList);
        return optionList.toArray(new OptionPanel[] {});
    }

    private OptionPanel find(Collection<PluginOptionPanel> panels,
			PluginDescriptor<?> plugin) 
    {
    	for (PluginOptionPanel panel: panels)
    	{
    		Class<? extends PluginDescriptor<?>> pluginClass = panel.getPluginClass();
    		if (plugin.getClass().equals( pluginClass))
    		{
    			return panel;
    		}
			
    	}
    	return null;
	}


	public void sort(List<OptionPanel> list) {
        Collections.sort( list, new NamedComparator<OptionPanel>(getRaplaLocale().getLocale()));
    }

    public OptionPanel[] getUserOptions() throws RaplaException {
        List<OptionPanel> optionList = new ArrayList<OptionPanel>(getContainer().lookupServicesFor( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION ));
        sort( optionList);
        return optionList.toArray(new OptionPanel[] {});
    }

    public OptionPanel[] getAdminOptions() throws RaplaException {
        List<OptionPanel> optionList = new ArrayList<OptionPanel>(getContainer().lookupServicesFor( RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION ));
        sort( optionList);
        return optionList.toArray(new OptionPanel[] {});
    }

    protected JComponent createInfoComponent() {
        JPanel panel = new JPanel();
        return panel;
    }


    private void setOptionPanel(OptionPanel optionPanel) throws Exception {
        String title = getString("nothing_selected");
        JComponent comp = defaultPanel;
        if ( optionPanel != null) {
            title = optionPanel.getName( getRaplaLocale().getLocale());
            comp =  optionPanel.getComponent();
        }

        TitledBorder  titledBorder = new TitledBorder(BorderFactory.createEmptyBorder(4,4,4,4),title);
        container.removeAll();
        container.setLayout(new BorderLayout());
        container.setBorder(titledBorder);
        container.add( comp,BorderLayout.CENTER);
        container.revalidate();
        container.repaint();
    }

    public String getTitle() {
        return getString("options");
    }

    /** maps all fields back to the current object.*/
    public void mapToObjects() throws RaplaException {
        if ( lastOptionPanel != null)
            lastOptionPanel.commit();
    }

    public void setObjects(List<Preferences> o) throws RaplaException {
        this.preferences = o.get(0);
        if ( preferences.getOwner() == null) {
            messages.setText(getString("restart_options"));
        }
        TreeFactory f = getTreeFactory();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        if ( preferences.getOwner() != null) {
            Named[] element = getUserOptions();
            for (int i=0; i< element.length; i++) {
                root.add(  f.newNamedNode( element[i]));
            }
        } else {
            {
                Named[] element = getAdminOptions();
                DefaultMutableTreeNode adminRoot = new DefaultMutableTreeNode("admin-options");
                for (int i=0; i< element.length; i++) {
                    adminRoot.add( f.newNamedNode( element[i]));
                }
                root.add( adminRoot );
            }
            {
                Named[] element = getPluginOptions();
                DefaultMutableTreeNode pluginRoot = new DefaultMutableTreeNode("plugins");
                for (int i=0; i< element.length; i++) {
                    pluginRoot.add( f.newNamedNode( element[i]));
                }
                root.add( pluginRoot );
            }
        }
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        jPanelSelection.exchangeTreeModel(treeModel);
    }

    public List<Preferences> getObjects() {
        return Collections.singletonList(preferences);
    }

    public void stateChanged(ChangeEvent evt) {
        try {
            if ( lastOptionPanel != null)
                lastOptionPanel.commit();

            OptionPanel optionPanel = null;
            if ( getSelectedElement() instanceof OptionPanel ) {
                optionPanel  = (OptionPanel) getSelectedElement();
                if ( optionPanel != null) {
                    optionPanel.setPreferences( preferences );
                    optionPanel.show();
                }
            }
            lastOptionPanel = optionPanel;
            setOptionPanel( lastOptionPanel );
        } catch (Exception ex) {
            showException(ex,getComponent());
        }
    }

    public Object getSelectedElement() {
        return jPanelSelection.getSelectedElement();
    }


    public JComponent getComponent() {
        return jPanelContainer;
    }

}



