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
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
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

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.extensionpoints.SystemOptionPanel;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.OptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides = EditComponent.class, id="org.rapla.entities.configuration.Preferences")
public class PreferencesEditUI extends RaplaGUIComponent
    implements
        EditComponent<Preferences,JComponent>
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

    private final Provider<Set<UserOptionPanel>> userOptionPanel;
    private final Provider<Set<SystemOptionPanel>> systemOptionPanel;
    private final Map<String,Provider<PluginOptionPanel>> pluginOptionPanel;
    private final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;

    /** called during initialization to create the info component 
     */
    @Inject
    public PreferencesEditUI( TreeFactory treeFactory, Provider<Set<UserOptionPanel>> userOptionPanel,
            Provider<Set<SystemOptionPanel>> systemOptionPanel, Map<String, Provider<PluginOptionPanel>> pluginOptionPanel, ClientFacade facade,
            RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.treeFactory = treeFactory;
        this.userOptionPanel = userOptionPanel;
        this.systemOptionPanel = systemOptionPanel;
        this.pluginOptionPanel = pluginOptionPanel;
        this.dialogUiFactory = dialogUiFactory;
        jPanelContainer.setLayout(new BorderLayout());
        jPanelContainer.add(messages, BorderLayout.SOUTH);
        messages.setForeground(Color.red);
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
        content.setBorder(emptyLineBorder);
        jPanelContainer.add(content, BorderLayout.CENTER);
        jPanelSelection.getTree().setCellRenderer(treeFactory.createRenderer());
        jPanelSelection.setToolTipRenderer(treeFactory.createTreeToolTipRenderer());
        container.setPreferredSize(new Dimension(700, 550));
        content.setLeftComponent(jPanelSelection);
        content.setRightComponent(container);
        content.setDividerLocation(260);
        Border emptyBorder=BorderFactory.createEmptyBorder(4,4,4,4);
        selectionBorder = BorderFactory.createTitledBorder(emptyBorder, getString("selection") + ":");
        jPanelSelection.setBorder(selectionBorder);
        content.setResizeWeight(0.4);
        jPanelSelection.addChangeListener(this);
    }

	protected Collection<OptionPanel> getPluginOptions() throws RaplaException {
        List<OptionPanel> optionList = new ArrayList<OptionPanel>();
        for ( Provider<PluginOptionPanel> panel: pluginOptionPanel.values())
        {
            final PluginOptionPanel e = panel.get();
            optionList.add(e);
        }
        sort( optionList);
        return optionList;
    }

	public void sort(List<? extends OptionPanel> list) {
        Collections.sort(list, new NamedComparator<OptionPanel>(getRaplaLocale().getLocale()));
    }

    public Collection<UserOptionPanel> getUserOptions() throws RaplaException {
        List<UserOptionPanel> optionList = new ArrayList<UserOptionPanel>();
        final Set<UserOptionPanel> set = userOptionPanel.get();
        for (UserOptionPanel panel : set){
            if(panel.isEnabled())
            {
                optionList.add(panel);
            }
        }
        sort(optionList);
        return optionList;
    }

    public Collection<OptionPanel> getAdminOptions() throws RaplaException {
        List<OptionPanel> optionList = new ArrayList<OptionPanel>();
        final Set<SystemOptionPanel> set = systemOptionPanel.get();
        for (SystemOptionPanel panel : set){
            optionList.add(panel);
        }
        sort(optionList);
        return optionList;
    }

    protected JComponent createInfoComponent() {
        JPanel panel = new JPanel();
        return panel;
    }


    private void setOptionPanel(OptionPanel optionPanel) throws Exception {
        String title = getString("nothing_selected");
        JComponent comp = defaultPanel;
        if ( optionPanel != null ) {
            title = optionPanel.getName( getRaplaLocale().getLocale());
            comp =  (JComponent)optionPanel.getComponent();
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
        if ( preferences.getOwnerRef() == null) {
            messages.setText(getString("restart_options"));
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        if ( preferences.getOwnerRef() != null) {
            Collection<? extends Named> elements = getUserOptions();
            for (Named element:elements) {
                root.add(  treeFactory.newNamedNode( element));
            }
        } else {
            {
                Collection<? extends Named> elements = getAdminOptions();
                DefaultMutableTreeNode adminRoot = new DefaultMutableTreeNode("admin-options");
                for (Named element:elements) {

                    adminRoot.add( treeFactory.newNamedNode( element));
                }
                root.add( adminRoot );
            }
            {
                Collection<? extends Named> elements = getPluginOptions();
                DefaultMutableTreeNode pluginRoot = new DefaultMutableTreeNode("plugins");
                for (Named element:elements)
                {
                    pluginRoot.add( treeFactory.newNamedNode( element));
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
            dialogUiFactory.showException(ex,new SwingPopupContext(getComponent(), null));
        }
    }

    public Object getSelectedElement() {
        return jPanelSelection.getSelectedElement();
    }


    public JComponent getComponent() {
        return jPanelContainer;
    }

}



