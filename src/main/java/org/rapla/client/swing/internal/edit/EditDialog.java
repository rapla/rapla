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

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.JComponent;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.EditController.EditCallback;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.EditDialogFactoryInterface;
import org.rapla.client.dialog.EditDialogInterface;
import org.rapla.client.internal.SaveUndo;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

public class EditDialog<T extends Entity> extends AbstractDialog<T> implements ModificationListener, Disposable, EditDialogInterface<T>
{
    private final EditControllerImpl editController;
    private final ReservationController reservationController;
    protected final Map<String, Provider<EditComponent>> editUiProvider;

    public EditDialog(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Map<String, Provider<EditComponent>> editUiProvider, EditController editController, ReservationController reservationController, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger, dialogUiFactory);
        this.editController = (EditControllerImpl)editController;
        this.reservationController = reservationController;
        this.editUiProvider = editUiProvider;
    }

    final private EditControllerImpl getPrivateEditDialog()
    {
        return editController;
    }

    @Override
    public void start(Collection<T> editObjects, String title, PopupContext popupContext,  EditController.EditCallback<List<T>> callback)
            throws RaplaException
    {
        start(editObjects, getI18n().format("edit.format", title), popupContext,  callback, getString("save"), getString("cancel"), new SaveAction(callback, reservationController));

        List<T> toEdit = new ArrayList<T>(editObjects);
        setObjects(toEdit);
        {
            getPrivateEditDialog().addEditDialog(this);
            // to avoid java compiler error
            EditComponent test = ui;
        }
    }
    
    @SuppressWarnings("unchecked")
    protected <T extends Entity> EditComponent<T,JComponent> createUI(T obj) throws RaplaException {
        final Class typeClass = obj.getTypeClass();
        final String id = typeClass.getName();
        final Provider<EditComponent> editComponentProvider = editUiProvider.get(id);
        if ( editComponentProvider != null)
        {
            EditComponent<T,JComponent> ui = (EditComponent<T,JComponent>)editComponentProvider.get();
            return ui;
        }
        else
        {
            throw new RuntimeException("Can't edit objects of type " + typeClass.toString());
        }
    }
    
    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        super.dataChanged(evt);
        if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
            return;
        if (shouldCancelOnModification(evt))
        {
            getPrivateEditDialog().removeEditDialog(this);
        }
    }
    
    @Override
    protected void cleanupAfterClose()
    {
        getPrivateEditDialog().removeEditDialog(EditDialog.this);
    }

    class SaveAction implements Runnable
    {
        private static final long serialVersionUID = 1L;
        private final ReservationController reservationController;
        private final EditCallback<List<T>> callback;

        public SaveAction(EditController.EditCallback<List<T>> callback, ReservationController reservationController)
        {
            this.callback = callback;
            this.reservationController = reservationController;
        }

        public void run()
        {
            try
            {
                ui.mapToObjects();
                bSaving = true;

                // object which is processed by EditComponent
                List<T> saveObjects = ui.getObjects();
                Collection<T> entities = new ArrayList<T>();
                entities.addAll(saveObjects);
                boolean canUndo = true;
                Boolean isReservation = null;
                for (T obj : saveObjects)
                {
                    if (obj instanceof Preferences || obj instanceof DynamicType || obj instanceof Category)
                    {
                        canUndo = false;
                    }
                    if (obj instanceof Reservation)
                    {
                        if (isReservation == null)
                        {
                            isReservation = true;
                        }
                    }
                    else
                    {
                        isReservation = false;
                    }
                }
                if (isReservation != null && isReservation)
                {
                    @SuppressWarnings("unchecked") Collection<Reservation> castToReservation = (Collection<Reservation>) saveObjects;
                    Component mainComponent = getMainComponent();
                    PopupContext popupContext = createPopupContext(mainComponent, null);
                    if (!reservationController.save(castToReservation, popupContext))
                    {
                        return;
                    }
                }
                else if (canUndo)
                {
                    @SuppressWarnings({ "unchecked", "rawtypes" }) SaveUndo<T> saveCommand = new SaveUndo(getFacade(), getI18n(), entities, originals);
                    CommandHistory commandHistory = getUpdateModule().getCommandHistory();
                    Promise promise = commandHistory.storeAndExecute(saveCommand);
                    promise.done(new DoneCallback()
                    {
                        @Override public void onDone(Object o)
                        {
                            getPrivateEditDialog().removeEditDialog(EditDialog.this);
                            dlg.close();
                        }
                    });
                }
                else
                {
                    getFacade().storeObjects(saveObjects.toArray(new Entity[] {}));
                }
                org.jdeferred.Promise promise;
                getPrivateEditDialog().removeEditDialog(EditDialog.this);
                dlg.close();
                if ( callback != null)
                {
                    callback.onSuccess(saveObjects);
                }
            }
            catch (IllegalAnnotationException ex)
            {
                dialogUiFactory.showWarning(ex.getMessage(), new SwingPopupContext((Component)dlg, null));
                if ( callback != null)
                {
                    callback.onFailure(ex);
                }
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext((Component)dlg, null));
                if ( callback != null)
                {
                    callback.onFailure(ex);
                }
            }
        }
    }

    public void dispose()
    {
        getUpdateModule().removeModificationListener(this);
    }

    @Singleton
    @DefaultImplementation(context = InjectionContext.swing, of = EditDialogFactoryInterface.class)
    public static class EditDialogFactory implements EditDialogFactoryInterface
    {
        private final Map<String, Provider<EditComponent>> editUiProviders;
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final ReservationController reservationController;
        private final DialogUiFactoryInterface dialogUiFactory;

        @Inject
        public EditDialogFactory(Map<String, Provider<EditComponent>> editUiProviders, ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale,
                Logger logger, ReservationController reservationController, DialogUiFactoryInterface dialogUiFactory)
        {
            super();
            this.editUiProviders = editUiProviders;
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.reservationController = reservationController;
            this.dialogUiFactory = dialogUiFactory;
        }

        @Override
        public EditDialogInterface create(EditController editController)
        {
            return new EditDialog(facade, i18n, raplaLocale, logger, editUiProviders, editController, reservationController, dialogUiFactory);
        }
    }
}

