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
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditComponent;
import org.rapla.gui.EditController;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationController;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.DisposingTool;

public class EditDialog<T extends Entity> extends RaplaGUIComponent implements ModificationListener,Disposable {
    DialogUI dlg;
    boolean bSaving = false;
    EditComponent<T> ui;
    boolean modal;
    private Collection<T> originals;

    public EditDialog(RaplaContext sm,EditComponent<T> ui,boolean modal){
        super( sm);
        this.ui = ui;
        this.modal = modal;
    }

    public EditDialog(RaplaContext sm,EditComponent<T> ui) {
        this(sm,ui,true);
    }

    final private EditControllerImpl getPrivateEditDialog() {
        return (EditControllerImpl) getService(EditController.class);
    }

    public int start(Collection<T> editObjects,String title,Component owner)
        throws
            RaplaException
    {
            // sets for every object in this array an edit item in the logfile
            originals= new ArrayList<T>();
            Map<T, T> persistant = getModification().getPersistant( editObjects);
            for (T entity : editObjects) 
            {
              
                getLogger().debug("Editing Object: " + entity);
                @SuppressWarnings("unchecked")
                Entity<T> mementable = persistant.get( entity);
                if ( mementable != null)
                {
                	if ( originals == null)
                	{
                		throw new RaplaException("You cannot edit persistant and new entities in one operation");
                	}
                	originals.add( mementable.clone() );
                }
                else
                {
                	if ( originals != null && !originals.isEmpty())
                	{
                		throw new RaplaException("You cannot edit persistant and new entities in one operation");
                	}
                	originals = null;
                }
            }

            List<T> toEdit = new ArrayList<T>(editObjects);
            ui.setObjects(toEdit);

            JComponent editComponent = ui.getComponent();
            JPanel panel = new JPanel();
            panel.setLayout( new BorderLayout());
            panel.add( editComponent, BorderLayout.CENTER);
            editComponent.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
            dlg = DialogUI.create(getContext(),owner,modal,panel,new String[] {
                getString("save")
                ,getString("cancel")
            });
            
            dlg.setAbortAction(new AbortAction());
            dlg.getButton(0).setAction(new SaveAction());
            dlg.getButton(1).setAction(new AbortAction());
            dlg.getButton(0).setIcon(getIcon("icon.save"));
            dlg.getButton(1).setIcon(getIcon("icon.cancel"));
            dlg.setTitle(getI18n().format("edit.format",title));
            getUpdateModule().addModificationListener(this);
            dlg.addWindowListener(new DisposingTool(this));
            dlg.start();
            if (modal) 
            {
                return dlg.getSelectedIndex();
            }
            else
            {
            	getPrivateEditDialog().addEditDialog( this );
            	// to avoid java compiler error 
            	EditComponent test = ui;
            	if ( test instanceof CategoryEditUI)
            	{
            		((CategoryEditUI)test).processCreateNew();
            	}
            	return -1;
            }
    }

    

    protected boolean shouldCancelOnModification(ModificationEvent evt) {
        List<T> objects = ui.getObjects();
        for (T o:objects )
        {
            // TODO include timestamps in preferencepatches
            if ( o instanceof Preferences && ((Preferences)o).getOwner() != null)
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

    public void dataChanged(ModificationEvent evt) throws RaplaException {
        if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
            return;
        if (shouldCancelOnModification(evt)) {
            getLogger().warn("Object has been changed outside.");
            DialogUI warning =
                DialogUI.create(getContext()
                                ,ui.getComponent()
                                ,true
                                ,getString("warning")
                                ,getI18n().format("warning.update",ui.getObjects())
                                );
            warning.start();
        	getPrivateEditDialog().removeEditDialog( this );
            dlg.close();
        }
    }

    class SaveAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        public void actionPerformed(ActionEvent evt) {
        	try {
				ui.mapToObjects();
				bSaving = true;

				// object which is processed by EditComponent
				List<T> saveObjects = ui.getObjects();
				Collection<T> entities = new ArrayList<T>();
                entities.addAll(saveObjects);
                boolean canUndo = true;
                Boolean isReservation = null;
                for ( T obj: saveObjects)
                {
                   if ( obj instanceof Preferences || obj instanceof DynamicType || obj instanceof Category)
                   {
                       canUndo = false;
                   }
                   if ( obj instanceof Reservation )
                   {
                       if ( isReservation == null)
                       {
                           isReservation = true;
                       }
                   }
                   else
                   {
                       isReservation = false;
                   }
                }
                if ( isReservation != null && isReservation)
                {
                    @SuppressWarnings("unchecked")
                    Collection<Reservation> castToReservation = (Collection<Reservation>) saveObjects;
                    Component mainComponent = getMainComponent();
                    ReservationController reservationController = getReservationController();
                    if (!reservationController.save( castToReservation, mainComponent))
                    {
                        return;
                    }
                }
                else if ( canUndo)
                {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    SaveUndo<T> saveCommand = new SaveUndo(getContext(), entities,  originals);
    		        CommandHistory commandHistory = getModification().getCommandHistory();
                    commandHistory.storeAndExecute(saveCommand);
                } 
                else 
                {
                    getModification().storeObjects( saveObjects.toArray( new Entity[] {}));
                }

		        getPrivateEditDialog().removeEditDialog(EditDialog.this);
				dlg.close();
        	} catch (IllegalAnnotationException ex) {
        	    showWarning(ex.getMessage(), dlg);
        	} catch (RaplaException ex) {
				showException(ex, dlg);
			}
        }
    }

    class AbortAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        public void actionPerformed(ActionEvent evt) {
        	getPrivateEditDialog().removeEditDialog( EditDialog.this );
        	dlg.close();
        }
    }

    public void dispose() {
        getUpdateModule().removeModificationListener(this);
    }
}

