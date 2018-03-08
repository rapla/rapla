package org.rapla.plugin.setowner.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingMenuContext;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.Entity;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.setowner.SetOwnerResources;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Extension(provides = ObjectMenuFactory.class, id="setowner")
public class SetOwnerMenuFactory implements ObjectMenuFactory
{
    SetOwnerResources setOwnerI18n;
    RaplaResources i18n;
    RaplaFacade facade;
    TreeFactory treeFactory;
    RaplaGUIComponent old;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    @Inject
    public SetOwnerMenuFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, SetOwnerResources setOwnerI18n, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory)
    {
        this.setOwnerI18n = setOwnerI18n;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        old = new RaplaGUIComponent(facade, i18n, raplaLocale, logger);
        this.i18n = i18n;
        this.facade = facade.getRaplaFacade();
        this.treeFactory = treeFactory;
    }

    public RaplaMenuItem[] create( final SwingMenuContext menuContext, final RaplaObject focusedObject )
    {
    	if (!old.isAdmin())
    	{
    		return RaplaMenuItem.EMPTY_ARRAY;
    	}
    	
    	Collection<Object> selectedObjects = new HashSet<Object>();
    	Collection<?> selected = menuContext.getSelectedObjects();
    	if ( selected.size() != 0)
    	{
    		selectedObjects.addAll( selected);
    	}
    	if ( focusedObject != null)
    	{
    		selectedObjects.add( focusedObject);
    	}
    		
    	final Collection<Entity<? extends Entity>> ownables = new HashSet<Entity<? extends Entity>>();
    	for ( Object obj: selectedObjects)
    	{
    		final Entity<? extends Entity> ownable;
    		if  ( obj instanceof AppointmentBlock)
    		{
    			ownable = ((AppointmentBlock) obj).getAppointment().getReservation(); 
    		}
    		else if ( obj instanceof Entity )
    		{
    			Class<? extends  Entity> raplaType = ((Entity)obj).getTypeClass();
    	    	if ( raplaType == Appointment.class )
    	        {
    	    		Appointment appointment = (Appointment) obj;
    	    	    		ownable = appointment.getReservation();
    	        }
    	    	else if ( raplaType ==  Reservation.class)
    	    	{
    	    		ownable = (Reservation) obj;
    	    	}
    	    	else if ( raplaType ==  Allocatable.class)
    	    	{
    	    		ownable = (Allocatable) obj;
    	    	}
    	    	else
    	    	{
    	    		ownable = null;
    	    	}
    		}
    		else
    		{
    			ownable  = null;
    		}
    		if ( ownable != null)
    		{
    			ownables.add( ownable);
    		}
    	}
    	
    	if ( ownables.size() == 0 )
    	{
    		return RaplaMenuItem.EMPTY_ARRAY;
    	}
        
        // createInfoDialog the menu entry
        final RaplaMenuItem setOwnerItem = new RaplaMenuItem("SETOWNER");
        setOwnerItem.setText(setOwnerI18n.getString("changeowner"));
        ImageIcon icon = raplaImages.getIconFromKey( "icon.tree.persons");
        setOwnerItem.setIcon(icon);
        setOwnerItem.addActionListener( new ActionListener()
        {
            public void actionPerformed( ActionEvent e )
            {
                showAddDialog().thenCompose((newOwner)->
                    (newOwner != null) ?
                        facade.editListAsync(ownables).thenApply((editableOwnables) ->
                                editableOwnables.values().stream().map((editableOwnable) -> {
                                    ((Ownable) editableOwnable).setOwner(newOwner);
                                    return editableOwnable;
                                }).collect(Collectors.toList())).thenCompose((toStore) -> {dialogUiFactory.busy(i18n.getString("save"));
                                return facade.dispatch(toStore, Collections.emptyList());
                                })
                            :ResolvedPromise.VOID_PROMISE
                ).exceptionally((ex)->dialogUiFactory.showException( ex, menuContext.getPopupContext())).finally_(()->dialogUiFactory.idle());
            }
         });
        return new RaplaMenuItem[] {setOwnerItem };
    }
    
    private Promise<User> showAddDialog() {
        final DialogInterface dialog;
        final RaplaTree treeSelection = new RaplaTree();
        try {
            treeSelection.setMultiSelect(true);
            treeSelection.getTree().setCellRenderer(treeFactory.createRenderer());

            DefaultMutableTreeNode userRoot = new DefaultMutableTreeNode("ROOT");
            //DefaultMutableTreeNode userRoot = TypeNode(User.TYPE, getString("users"));
            User[] userList = facade.getUsers();
            for (final User user : sorted(userList)) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode();
                node.setUserObject(user);
                userRoot.add(node);
            }

            treeSelection.exchangeTreeModel(new DefaultTreeModel(userRoot));
            treeSelection.setMinimumSize(new java.awt.Dimension(300, 200));
            treeSelection.setPreferredSize(new java.awt.Dimension(400, 260));
            final PopupContext popupContext = new SwingPopupContext(old.getMainComponent(), null);
            dialog = dialogUiFactory.createContentDialog(
                    popupContext
                    ,
                    treeSelection
                    , new String[]{i18n.getString("apply"), i18n.getString("cancel")});
            dialog.setTitle(setOwnerI18n.getString("changeownerto"));
            dialog.getAction(0).setEnabled(false);

            final JTree tree = treeSelection.getTree();
            tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            tree.addMouseListener(new MouseAdapter() {
                // End dialog when a leaf is double clicked
                public void mousePressed(MouseEvent e) {
                    TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                    if (selPath != null && e.getClickCount() == 2) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf()) {
                            dialog.getAction(0).execute();
                            return;
                        }
                    } else if (selPath != null && e.getClickCount() == 1) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf()) {
                            dialog.getAction(0).setEnabled(true);
                            return;
                        }
                    }
                    tree.removeSelectionPath(selPath);
                }
            });
        } catch (RaplaException ex)
        {
            return new ResolvedPromise<>(ex);
        }
        return dialog.start(true).thenApply((index)->
            index == 0 ? (User) treeSelection.getSelectedElement():null
            );
    }
    
    private <T extends Named> Collection<T> sorted(T[] allocatables) {
        TreeSet<T> sortedList = new TreeSet<T>(new NamedComparator<T>(old.getLocale()));
        sortedList.addAll(Arrays.asList(allocatables));
        return sortedList;
    }
    
}
