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
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.EditDialogInterface;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.entities.Entity;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public abstract class AbstractDialog<T extends Entity> extends RaplaGUIComponent implements ModificationListener, Disposable, EditDialogInterface<T>
{
    protected DialogInterface dlg;
    protected boolean bSaving = false;
    protected Collection<T> originals;
    protected final DialogUiFactoryInterface dialogUiFactory;
    protected EditComponent<T, JComponent> ui;

    public AbstractDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
    }

    protected void start(Collection<T> editObjects, String title, PopupContext popupContext,  final String confirm, final String cancel, Runnable confirmAction)
            throws RaplaException
    {
        ui = createUI(editObjects.iterator().next());
        // sets for every object in this array an edit item in the logfile
        originals = new ArrayList<T>();
        Map<T, T> persistant = getFacade().getPersistant(editObjects);
        for (T entity : editObjects)
        {

            @SuppressWarnings("unchecked") Entity<T> mementable = persistant.get(entity);
            if (mementable != null)
            {
                if (originals == null)
                {
                    throw new RaplaException("You cannot edit persistant and new entities in one operation");
                }
                originals.add(mementable.clone());
            }
            else
            {
                if (originals != null && !originals.isEmpty())
                {
                    throw new RaplaException("You cannot edit persistant and new entities in one operation");
                }
                originals = null;
            }
        }

        JComponent editComponent = ui.getComponent();
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(editComponent, BorderLayout.CENTER);
        editComponent.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        boolean modal = false;
        dlg = dialogUiFactory
                .create(popupContext, modal, panel, new String[] { confirm, cancel });

        final AbortAction action = new AbortAction();
        dlg.setAbortAction(action);
        dlg.getAction(0).setRunnable(confirmAction);
        dlg.getAction(1).setRunnable(action);
        dlg.getAction(0).setIcon("icon.save");
        dlg.getAction(1).setIcon("icon.cancel");
        dlg.setTitle(title);
        getUpdateModule().addModificationListener(this);
        dlg.addWindowListener(this);
        dlg.start(true);
    }
    
    @Override
    public DialogInterface getDialog()
    {
        return dlg;
    }
    
    protected final void setObjects(List<T> objects) throws RaplaException
    {
        ui.setObjects(objects);
    }
    
    @Override
    public List<?> getObjects()
    {
        if(ui == null)
        {
            return null;
        }
        return ui.getObjects();
    }

    protected abstract <T extends Entity> EditComponent<T,JComponent> createUI(T obj) throws RaplaException ;

    protected boolean shouldCancelOnModification(ModificationEvent evt)
    {
        List<T> objects = ui.getObjects();
        for (T o : objects)
        {
            // TODO include timestamps in preferencepatches
            if (o instanceof Preferences && ((Preferences) o).getOwnerRef() != null)
            {
                continue;
            }
            if (evt.hasChanged(o))
            {
                return true;
            }
        }
        return false;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
            return;
        if (shouldCancelOnModification(evt))
        {
            getLogger().warn("Object has been changed outside.");
            final Component component = ui.getComponent();
            DialogInterface warning = dialogUiFactory.create(new SwingPopupContext(component, null), true, getString("warning"), getI18n().format("warning.update", ui.getObjects()));
            warning.start(true);
            dlg.close();
        }
    }

    class AbortAction implements Runnable
    {
        private static final long serialVersionUID = 1L;
        public AbortAction()
        {
        }

        public void run()
        {
            dlg.close();
            cleanupAfterClose();
        }
    }

    public void dispose()
    {
        getUpdateModule().removeModificationListener(this);
    }

    protected abstract void cleanupAfterClose();
}

