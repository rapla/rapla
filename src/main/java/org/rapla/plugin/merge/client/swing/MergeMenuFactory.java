package org.rapla.plugin.merge.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.event.StartActivityEvent;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.swing.MenuContext;
import org.rapla.client.swing.SwingActivityController;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;

import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

@Singleton
@Extension(provides = ObjectMenuFactory.class, id="merge")
public class MergeMenuFactory implements ObjectMenuFactory
{
    private final RaplaImages raplaImages;
    private final RaplaResources raplaResources;
    private final DialogUiFactory dialogUiFactory;
    private final EventBus eventBus;
//    private final MergeDialogFactory mergeDialogFactory;
//    private final EditController editController;

    @Inject
    public MergeMenuFactory(RaplaResources raplaResources, RaplaImages raplaImages, DialogUiFactory dialogUiFactory, EventBus eventBus)
    {
        this.raplaResources = raplaResources;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.eventBus = eventBus;
    }
    
    @Override
    public RaplaMenuItem[] create(final MenuContext menuContext, RaplaObject focusedObject)
    {
        final Collection<?> selectedObjects = menuContext.getSelectedObjects();
        if(selectedObjects != null && selectedObjects.size()<=1)
        {
            return new RaplaMenuItem[0];    
        }
        Iterator<?> it = selectedObjects.iterator();
        Object last = it.next();
        while (it.hasNext())
        {
            final Object next = it.next();
            if (!next.getClass().equals(last.getClass()))
            {
                return new RaplaMenuItem[0];
            }
            if (next instanceof Allocatable)
            {
                if (!((Allocatable) next).getClassification().getType().equals(((Allocatable) last).getClassification().getType()))
                {
                    return new RaplaMenuItem[0];
                }
            }
            else
            {
                return new RaplaMenuItem[0];
            }
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
                  StringBuilder ids = new StringBuilder();
                  boolean first = true;
                  for (Object object : selectedObjects)
                  {
                      if(first)
                      {
                          first  =false;
                      }
                      else
                      {
                          ids.append(",");
                      }
                      ids.append(((Entity)object).getId());
                  }
                    eventBus.fireEvent(new StartActivityEvent(SwingActivityController.MERGE_ALLOCATABLES, ids.toString()));
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
