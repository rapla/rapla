/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.gui.internal;
import java.awt.Component;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;

import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.MenuContext;
import org.rapla.gui.MenuFactory;
import org.rapla.gui.ObjectMenuFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.action.DynamicTypeAction;
import org.rapla.gui.internal.action.RaplaObjectAction;
import org.rapla.gui.internal.action.user.PasswordChangeAction;
import org.rapla.gui.internal.action.user.UserAction;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.MenuInterface;
import org.rapla.gui.toolkit.RaplaMenuItem;
import org.rapla.gui.toolkit.RaplaSeparator;

public class MenuFactoryImpl extends RaplaGUIComponent implements MenuFactory
{
    public void addReservationWizards( MenuInterface menu, MenuContext context, String afterId ) throws RaplaException
    {
        if (canCreateReservation())
        {
        	addNewMenus(menu,  afterId);		        
        }
    }

        
            


    /**
	 * @param model
	 * @param startDate
	 * @return
	 */
	protected Date getEndDate( CalendarModel model,Date startDate) {
		Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
		Date endDate = null;
    	if ( markedIntervals.size() > 0)
    	{
    		TimeInterval first = markedIntervals.iterator().next();
    		endDate = first.getEnd();
    	}
    	if ( endDate != null)
    	{
    		return endDate;
    	}
		return new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
	}

	protected Date getStartDate(CalendarModel model) {
		Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
		Date startDate = null;
    	if ( markedIntervals.size() > 0)
    	{
    		TimeInterval first = markedIntervals.iterator().next();
    		startDate = first.getStart();
    	}
    	if ( startDate != null)
    	{
    		return startDate;
    	}
    	
		
		Date selectedDate = model.getSelectedDate();
		if ( selectedDate == null)
		{
			selectedDate = getQuery().today();
		}
		Date time = new Date (DateTools.MILLISECONDS_PER_MINUTE * getCalendarOptions().getWorktimeStartMinutes());
		startDate = getRaplaLocale().toDate(selectedDate,time);
		return startDate;
	}

    
    private void addNewMenus(MenuInterface menu, String afterId) throws RaplaException
    {
    	 boolean canAllocateSelected = canAllocateSelected();
         if ( canAllocateSelected  ) 
         {
			Collection<IdentifiableMenuEntry> wizards = getContainer().lookupServicesFor( RaplaClientExtensionPoints.RESERVATION_WIZARD_EXTENSION);
			
			Map<String,IdentifiableMenuEntry> sortedMap = new TreeMap<String, IdentifiableMenuEntry>();
			for (IdentifiableMenuEntry entry:wizards)
			{
				sortedMap.put(entry.getId(), entry);
			}
			for ( IdentifiableMenuEntry wizard: sortedMap.values())
			{
			    MenuElement menuElement = wizard.getMenuElement();
			    if ( menuElement != null)
			    {
			    	menu.insertAfterId(menuElement.getComponent(), afterId);
			    }
			}
        }
//        else
//        {
//        	JMenuItem cantAllocate = new JMenuItem(getString("permission.denied"));
//        	cantAllocate.setEnabled( false);
//	        menu.insertAfterId(cantAllocate, afterId);
//	    }
    }





	protected boolean canAllocateSelected() throws RaplaException {
		User user = getUser();
          Date today = getQuery().today();        
          boolean canAllocate = false;
          CalendarSelectionModel model = getService(CalendarSelectionModel.class);
          Collection<Allocatable> selectedAllocatables = model.getMarkedAllocatables();
          Date start = getStartDate( model);
          Date end = getEndDate( model, start);
          for ( Allocatable alloc: selectedAllocatables) {
              if (alloc.canAllocate( user, start, end, today))
                  canAllocate = true;
          }
          boolean canAllocateSelected = canAllocate || (selectedAllocatables.size() == 0 && canUserAllocateSomething( getUser()));
		return canAllocateSelected;
	}
    
  

    public MenuFactoryImpl(RaplaContext sm) {
       super(sm);
    }

    public MenuInterface addNew( MenuInterface menu, MenuContext context,String afterId) throws RaplaException
    {
    	return addNew(menu, context, afterId, false);
    }
    
    public MenuInterface addNew( MenuInterface menu, MenuContext context,String afterId, boolean addNewReservationMenu ) throws RaplaException
    {
        // Do nothing if the user can't allocate anything
        User user = getUser();
        Component parent = context.getComponent();
        Object focusedObject = context.getFocusedObject();
        Point p = context.getPoint();
     
		if (canUserAllocateSomething( user) )
		{
     	    if ( addNewReservationMenu)
            {
    			addReservationWizards(menu, context, afterId);
            }
		}
        boolean allocatableType = false;
        boolean reservationType = false;
        DynamicType type = null;
        if ( focusedObject instanceof DynamicType)
        {
            type = (DynamicType) focusedObject;
        }
        else if ( focusedObject instanceof Classifiable)
        {
            type = ((Classifiable) focusedObject).getClassification().getType();
        }

        if ( type != null)
        {
            String classificationType = type.getAnnotation( DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE );
            allocatableType = classificationType.equals(  DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON ) || classificationType.equals(  DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE );
            reservationType = classificationType.equals(  DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION );
        }

            
        boolean allocatableNodeContext = allocatableType || focusedObject instanceof Allocatable  || focusedObject == CalendarModelImpl.ALLOCATABLES_ROOT;
        if ( isRegisterer(type) || isAdmin()) {
            if ( allocatableNodeContext)
            {
                menu.addSeparator();
                addAllocatableMenuNew( menu, parent,p, focusedObject);                
            }
        }
        if ( isAdmin()  )
        {
            boolean reservationNodeContext =  reservationType || (focusedObject!= null && focusedObject.equals( getString("reservation_type" )));
            boolean userNodeContext = focusedObject instanceof User || (focusedObject  != null &&  focusedObject.equals( getString("users")));
            boolean periodNodeContext = focusedObject instanceof Period || (focusedObject  != null &&  focusedObject.equals( getString("periods")));
            boolean categoryNodeContext = focusedObject instanceof Category || (focusedObject  != null &&  focusedObject.equals( getString("categories")));
            if (userNodeContext || allocatableNodeContext || reservationNodeContext || periodNodeContext || categoryNodeContext )
            {
                if ( allocatableNodeContext || addNewReservationMenu)
                {
                	menu.addSeparator();
                }
            }
            if ( userNodeContext)
            {
                addUserMenuNew( menu , parent, p);
            }
           
            if (allocatableNodeContext)
            {
                addTypeMenuNew(menu, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,parent, p);
                addTypeMenuNew(menu, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON,parent, p);

            }
            if ( periodNodeContext)
            {
                addPeriodMenuNew(  menu , parent, p );
            }
            if ( categoryNodeContext )
            {
                addCategoryMenuNew(  menu , parent, p, focusedObject );
            }
            if ( reservationNodeContext)
            {
                addTypeMenuNew(menu, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION,parent, p);
            }
            /*
             */
        }
        return menu;
    }

    public MenuInterface addObjectMenu( MenuInterface menu, MenuContext context) throws RaplaException 
    {
        return addObjectMenu( menu, context, "EDIT_BEGIN");
    }

    public MenuInterface addObjectMenu( MenuInterface menu, MenuContext context, String afterId ) throws RaplaException
    {
        Component parent = context.getComponent();
        Object focusedObject = context.getFocusedObject();
        Point p = context.getPoint();
        
        
        Collection<Entity<?>> list = new LinkedHashSet<Entity<?>>();
        if ( focusedObject != null && (focusedObject instanceof Entity)) 
        {
        	Entity<?> obj = (Entity<?>) focusedObject;
		 	list.add(  obj );
			addAction(menu, parent, p, afterId).setView(obj);
		}
        
        for ( Object obj:  context.getSelectedObjects())
        {
    	   if ( obj instanceof Entity)
    	   {
    		   list.add( (Entity<?>) obj);
    	   }
       	}
       	
       
        {
	        List<Entity<?>> deletableObjects = getDeletableObjects(list);
	    	if ( deletableObjects.size() > 0)
	    	{
	       		addAction(menu,parent,p, afterId).setDeleteSelection(deletableObjects);
	    	}
	   	}
       
        List<Entity<?>> editableObjects = getEditableObjects(list);
        Collection<Entity<?>> editObjects = getObjectsWithSameType( editableObjects );
        if ( editableObjects.size() == 1 )
        {
            Entity<?> first = editObjects.iterator().next();
            addAction(menu, parent, p, afterId).setEdit(first);
        }
        else if  (isMultiEditSupported( editableObjects ))
        {
        
            addAction(menu, parent, p, afterId).setEditSelection(editObjects);
        }
        if ( editableObjects.size() == 1 )
    	{
    		RaplaObject next = editableObjects.iterator().next();
    		if  ( next.getRaplaType() ==  User.TYPE)
    		{
		 		addUserMenuEdit( menu , parent, p, (User) next , afterId);
		 	}
	     }

        Iterator<ObjectMenuFactory> it = getContainer().lookupServicesFor( RaplaClientExtensionPoints.OBJECT_MENU_EXTENSION).iterator();
        while (it.hasNext())
        {
            ObjectMenuFactory objectMenuFact =  it.next();
            RaplaObject obj = focusedObject instanceof RaplaObject ? (RaplaObject) focusedObject : null;
  	        RaplaMenuItem[] items = objectMenuFact.create( context, obj);
            for ( int i =0;i<items.length;i++)
            {
                RaplaMenuItem item =  items[i];
                menu.insertAfterId( item, afterId);
            }
        }
        

        return menu;
    }

    private boolean isMultiEditSupported(List<Entity<?>> editableObjects) {
		if ( editableObjects.size() > 0  )
		{
			RaplaType raplaType = editableObjects.iterator().next().getRaplaType();
			if ( raplaType ==  Allocatable.TYPE || raplaType ==  User.TYPE || raplaType == Reservation.TYPE)
			{
				return true;
			}
		}
    	return false;
	}


    private void addAllocatableMenuNew(MenuInterface menu,Component parent,Point p,Object focusedObj) throws RaplaException {
    	RaplaObjectAction newResource = addAction(menu,parent,p).setNew( Allocatable.TYPE );
    	if (focusedObj != CalendarModelImpl.ALLOCATABLES_ROOT) 
    	{
	        if (focusedObj instanceof DynamicType) 
	        {
	        	if (((DynamicType) focusedObj).getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE).equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)) 
	        	{
	        		newResource.setPerson(true);
	        	}
	        	newResource.changeObject( (DynamicType)focusedObj );
	        } 
	        if (focusedObj instanceof Allocatable) 
	        {
	        	if (((Allocatable) focusedObj).isPerson()) 
	        	{
	        		newResource.setPerson(true);
	        	}
	         	newResource.changeObject( (Allocatable)focusedObj );
	     	   
	        }
	        DynamicType[] types = newResource.guessTypes();
	        if (types.length == 1)	//user has clicked on a resource/person type
	        {
	            DynamicType type = types[0];
	            newResource.putValue(Action.NAME,type.getName( getLocale() ));
	            return;
	        }
    	} 
    	else 
    	{
	        //user has clicked on top "resources" folder : 
	        //add an entry to create a new resource and another to create a new person
	        DynamicType[] resourceType= getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE );
	        if ( resourceType.length == 1)
	        {
	            newResource.putValue(Action.NAME,resourceType[0].getName( getLocale() ));            
	        }
	        else
	        {
	            newResource.putValue(Action.NAME,getString("resource"));   
	        }
	
	        RaplaObjectAction newPerson = addAction(menu,parent,p).setNew( Allocatable.TYPE );
	        newPerson.setPerson( true );
	        DynamicType[] personType= getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON );
	        if ( personType.length == 1)
	        {
	            newPerson.putValue(Action.NAME,personType[0].getName( getLocale()));
	        }
	        else
	        {
	            newPerson.putValue(Action.NAME,getString("person"));
	        }
    	}
    }

    private void addTypeMenuNew(MenuInterface menu,String classificationType,Component parent,Point p) {
            DynamicTypeAction newReservationType = newDynamicTypeAction(parent,p);
            menu.add(new JMenuItem(newReservationType));
            newReservationType.setNewClassificationType(classificationType);
            newReservationType.putValue(Action.NAME,getString(classificationType + "_type"));
    }

    private void addUserMenuEdit(MenuInterface menu,Component parent,Point p,User obj,String afterId) {

        menu.insertAfterId( new RaplaSeparator("sep1"), afterId);
        menu.insertAfterId( new RaplaSeparator("sep2"), afterId);
        PasswordChangeAction passwordChangeAction = new PasswordChangeAction(getContext(),parent);
        passwordChangeAction.changeObject( obj );
        menu.insertAfterId( new JMenuItem( passwordChangeAction ), "sep2");

        UserAction switchUserAction = newUserAction(parent,p);
        switchUserAction.setSwitchToUser();
        switchUserAction.changeObject( obj );
        menu.insertAfterId( new JMenuItem( switchUserAction ), "sep2");
    }

    private void addUserMenuNew(MenuInterface menu,Component parent,Point p) {
        UserAction newUserAction = newUserAction(parent,p);
        newUserAction.setNew();
        menu.add( new JMenuItem( newUserAction ));
    }

    private void addCategoryMenuNew(MenuInterface menu, Component parent, Point p, Object obj)  {
    	RaplaObjectAction newAction = addAction(menu,parent,p).setNew( Category.TYPE );
    	if ( obj instanceof Category)
    	{
    		newAction.changeObject((Category)obj);
    	}
    	else if ( obj != null &&  obj.equals( getString("categories")))
    	{
    		newAction.changeObject(getQuery().getSuperCategory());
    	}
      	newAction.putValue(Action.NAME,getString("category"));
    }

    private void addPeriodMenuNew(MenuInterface menu, Component parent, Point p) {
        Action newAction = addAction(menu,parent,p).setNew( Period.TYPE );
        newAction.putValue(Action.NAME,getString("period"));

    }

    private RaplaObjectAction addAction(MenuInterface menu, Component parent,Point p) {
        RaplaObjectAction action = newObjectAction(parent,p);
        menu.add(new JMenuItem(action));
        return action;
    }

    private RaplaObjectAction addAction(MenuInterface menu, Component parent,Point p,String id) {
        RaplaObjectAction action = newObjectAction(parent,p);
        menu.insertAfterId( new JMenuItem(action), id);
        return action;
    }

    private RaplaObjectAction newObjectAction(Component parent,Point point) {
        RaplaObjectAction action = new RaplaObjectAction(getContext(),parent, point);
        return action;
    }


    private DynamicTypeAction newDynamicTypeAction(Component parent,Point point) {
        DynamicTypeAction action = new DynamicTypeAction(getContext(),parent,point);
        return action;
    }



    private UserAction newUserAction(Component parent,Point point) {
        UserAction action = new UserAction(getContext(),parent,point);
        return action;
    }


    // This will exclude DynamicTypes and non editable Objects from the list
    private List<Entity<?>> getEditableObjects(Collection<?> list) {
        Iterator<?> it = list.iterator();
        ArrayList<Entity<?>> editableObjects = new ArrayList<Entity<?>>();
        while (it.hasNext()) {
            Object o = it.next();
            if (canModify(o) )
                editableObjects.add((Entity<?>)o);
        }
        return editableObjects;
    }
    
    private List<Entity<?>> getDeletableObjects(Collection<?> list) {
        Iterator<?> it = list.iterator();
        Category superCategory = getQuery().getSuperCategory();
        ArrayList<Entity<?>> deletableObjects = new ArrayList<Entity<?>>();
        while (it.hasNext()) {
            Object o = it.next();
			if (canAdmin(o) && !o.equals( superCategory) )
                deletableObjects.add((Entity<?>)o);
        }
        return deletableObjects;
    }



 	// method for filtering a selection(Parameter: list) of similar RaplaObjekte
 	// (from type raplaType)
 	// criteria: RaplaType: isPerson-Flag
 	private <T extends RaplaObject> List<T> getObjectsWithSameType(Collection<T> list,
 			RaplaType raplaType, boolean isPerson) {
 		ArrayList<T> objects = new ArrayList<T>();

 		for (RaplaObject o : list) {
 			// element will be added if it is from the stated RaplaType...
 			if (raplaType != null && (o.getRaplaType() == raplaType))
	        {
 			    // ...furthermore the flag isPerson at allocatables has to
 			    // be conform, because person and other resources aren't
 			    // able to process at the same time
 			    if (raplaType!=Allocatable.TYPE || ((Allocatable) o).isPerson() == isPerson)
 			    {
     				@SuppressWarnings("unchecked")
                    T casted = (T)o;
                    objects.add(casted);
 			    }
	        }

 		}
 		return objects;
 	}

 	private <T extends RaplaObject> Collection<T> getObjectsWithSameType(Collection<T> list) 
 	{
 			Iterator<T> iterator = list.iterator();
			if ( !iterator.hasNext())
			{
				return list;
			}
			RaplaObject obj = iterator.next();
			RaplaType raplaType = obj.getRaplaType();
 			boolean isPerson = raplaType == Allocatable.TYPE && ((Allocatable) obj).isPerson();
			return getObjectsWithSameType(list, raplaType, isPerson);
 	}

}









