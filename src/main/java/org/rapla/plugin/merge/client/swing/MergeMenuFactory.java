package org.rapla.plugin.merge.client.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.MenuContext;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.entities.RaplaObject;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;

@Singleton
@Extension(provides = ObjectMenuFactory.class, id="merge")
public class MergeMenuFactory implements ObjectMenuFactory
{
    private final RaplaImages raplaImages;
    private final RaplaResources raplaResources;
    private final DialogUiFactory dialogUiFactory;
//    private final MergeDialogFactory mergeDialogFactory;
//    private final EditController editController;

    @Inject
    public MergeMenuFactory(RaplaResources raplaResources, RaplaImages raplaImages, DialogUiFactory dialogUiFactory)//, MergeDialogFactory mergeDialogFactory, EditController editController)
    {
        this.raplaResources = raplaResources;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
//        this.mergeDialogFactory = mergeDialogFactory;
//        this.editController = editController;
    }
    
    @Override
    public RaplaMenuItem[] create(final MenuContext menuContext, RaplaObject focusedObject)
    {
        final Collection<?> selectedObjects = menuContext.getSelectedObjects();
        if(selectedObjects != null && selectedObjects.size()<=1)
        {
            return new RaplaMenuItem[0];    
        }
        RaplaMenuItem[] menuItem = new RaplaMenuItem[1];
        menuItem[0] = new RaplaMenuItem("merge");
        final String title = raplaResources.getString("merge");
        menuItem[0].setText(title);
        final ImageIcon icon = raplaImages.getIconFromKey("icon.merge");
        menuItem[0].setIcon(icon);
        menuItem[0].addActionListener( new ActionListener()
        {
            public void actionPerformed( ActionEvent e )
            {
                try 
                {
//                    final MergeDialog mergeDialog = mergeDialogFactory.create(editController);
//                    EditCallback callback = null;
//                    PopupContext popupContext = menuContext.getPopupContext();
//                    mergeDialog.start(selectedObjects, title, popupContext, false, callback);
                }
                catch (RaplaException ex )
                {
                    dialogUiFactory.showException( ex, menuContext.getPopupContext());
                } 
            }
         });

        return menuItem;
    }

}
