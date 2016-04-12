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
package org.rapla.client.swing.toolkit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.RaplaResources;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.layout.TableLayout;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;

/** displays a wizard dialog with four buttons and a HTML help.

*/
public class WizardDialog extends DialogUI {
    private static final long serialVersionUID = 1L;
    
    protected WizardPanel wizardPanel;
    protected HTMLView helpView;

    static public String[] options = new String[] {
        WizardPanel.ABORT
        ,WizardPanel.PREV
        ,WizardPanel.NEXT
        ,WizardPanel.FINISH
    };


    protected WizardDialog(RaplaResources i18n, RaplaImages raplaImages, BundleManager bundleManager, FrameControllerList frameList, Dialog owner) throws
            RaplaInitializationException {
        super(i18n, raplaImages, bundleManager, frameList,owner);
    }

    protected WizardDialog(RaplaResources i18n, RaplaImages raplaImages, BundleManager bundleManager, FrameControllerList frameList, Frame owner) throws RaplaInitializationException {
        super(i18n, raplaImages, bundleManager, frameList,owner);
    }

    private void init(boolean modal) {
        super.init(modal, new JPanel(), options);
        content.setLayout(new BorderLayout());
        helpView = new HTMLView();
        helpView.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0,0,3,0)
                ,   BorderFactory.createCompoundBorder(
                                                              BorderFactory.createEtchedBorder()
                                                              ,BorderFactory.createEmptyBorder(4,4,4,4)
                                                              )
                           )
                           );
        helpView.setOpaque(true);
        content.add(helpView,BorderLayout.WEST);
        helpView.setPreferredSize(new Dimension(220,300));
        packFrame=false;
    }

    protected JComponent createButtonPanel() {
        TableLayout tableLayout = new TableLayout(new double[][] {
            {10,0.2,10,0.2,5,0.4,10,0.2,10}
            ,{5,TableLayout.PREFERRED,5}
        });
        JPanel jPanelButtons = new JPanel();
        jPanelButtons.setLayout(tableLayout);
        jPanelButtons.add(buttons[0],"1,1,l,c");
        jPanelButtons.add(buttons[1],"3,1,r,c");
        jPanelButtons.add(buttons[2],"5,1,l,c");
        jPanelButtons.add(buttons[3],"7,1");
        return jPanelButtons;
    }

    public WizardPanel getActivePanel() {
        return wizardPanel;
    }

    public void start(WizardPanel newPanel) {
        if (!isVisible())
            start(true);

        if (wizardPanel != null)
            content.remove((Component)wizardPanel.getComponent());
        wizardPanel = newPanel;
        if (wizardPanel == null)
            close();

        content.add((Component)wizardPanel.getComponent(),BorderLayout.CENTER);
        ((JComponent)wizardPanel.getComponent()).setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
        if (wizardPanel.getHelp() != null)
            helpView.setBody(wizardPanel.getHelp());
        String defaultAction = wizardPanel.getDefaultAction();
        content.revalidate();
        content.repaint();

        // set actions
        ActionMap actionMap = wizardPanel.getActionMap();
        getButton(0).setAction(actionMap.get(WizardPanel.ABORT));
        getButton(0).setActionCommand(WizardPanel.ABORT);
        if (defaultAction.equals(WizardPanel.ABORT))
            setDefault(0);
        getButton(1).setAction(actionMap.get(WizardPanel.PREV));
        getButton(1).setActionCommand(WizardPanel.PREV);
        if (defaultAction.equals(WizardPanel.PREV))
            setDefault(1);
        getButton(2).setAction(actionMap.get(WizardPanel.NEXT));
        getButton(2).setActionCommand(WizardPanel.NEXT);
        if (defaultAction.equals(WizardPanel.NEXT))
            setDefault(2);
        getButton(3).setAction(actionMap.get(WizardPanel.FINISH));
        getButton(3).setActionCommand(WizardPanel.FINISH);
        if (defaultAction.equals(WizardPanel.FINISH))
            setDefault(3);
    }
    
    @Singleton
    public static class WizardDialogFactory
    {
        private final RaplaResources i18n;
        private final RaplaImages raplaImages;
        private final BundleManager bundleManager;
        private final FrameControllerList frameList;

        @Inject
        public WizardDialogFactory(RaplaResources i18n, RaplaImages raplaImages, BundleManager bundleManager, FrameControllerList frameList)
        {
            this.i18n = i18n;
            this.raplaImages = raplaImages;
            this.bundleManager = bundleManager;
            this.frameList = frameList;
        }

        public WizardDialog createWizard(Component owner, boolean modal) throws RaplaException
        {
            WizardDialog dlg;
            Component topLevel = getOwnerWindow(owner);
            if (topLevel instanceof Dialog)
                dlg = new WizardDialog(i18n, raplaImages, bundleManager, frameList, (Dialog) topLevel);
            else
                dlg = new WizardDialog(i18n, raplaImages, bundleManager, frameList, (Frame) topLevel);
            dlg.init(modal);
            return dlg;
        }
    }
}
