/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.gui.internal.view;

import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.InverseComparator;
import org.rapla.components.util.iterator.FilterIterable;
import org.rapla.entities.Category;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Named;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationStartComparator;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.toolkit.TreeToolTipRenderer;
import org.rapla.storage.StorageOperator;

public class TreeFactoryImpl extends RaplaGUIComponent implements TreeFactory {
    public TreeFactoryImpl(RaplaContext sm) {
        super(sm);
    }
   
    class DynamicTypeComperator  implements Comparator<DynamicType>
    {
    	public int compare(DynamicType o1,DynamicType o2)
    	{
    		int rang1 = getRang(o1);
    		int rang2 = getRang(o2);
    		if ( rang1 < rang2)
    		{
    			return -1;
    		}
    		if ( rang1 > rang2)
    		{
    			return 1;
    		}
    		return compareIds((DynamicTypeImpl)o1, (DynamicTypeImpl)o2);
    	}

		private int compareIds(DynamicTypeImpl o1, DynamicTypeImpl o2) {
			return o1.compareTo( o2);
		}

		private int getRang(DynamicType o1) {
			String t2 = o1.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
			if ( t2 != null && t2.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
			{
				return 1;
			}
			if ( t2 != null && t2.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
			{
				return 2;
			}
			else
			{
				return 3;
			}
		}
    }
    
    public TreeModel createClassifiableModel(Reservation[] classifiables) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Comparator<Classifiable> comp = new InverseComparator(new ReservationStartComparator(getLocale()));
        return createClassifiableModel( classifiables, comp,false);
    }
   
    public TreeModel createClassifiableModel(Allocatable[] classifiables, boolean useCategorizations) {
        @SuppressWarnings({ "rawtypes" })
        Comparator<Classifiable> comp = new SortedClassifiableComparator(getLocale());
        return createClassifiableModel( classifiables, comp, useCategorizations);
    }
    
    class SortedClassifiableComparator implements Comparator<Classifiable>
    {
        Locale locale;
        NamedComparator comp;
        public SortedClassifiableComparator(Locale locale) {
            this.locale = locale;
            comp = new NamedComparator(locale);
        }

        @Override
        public int compare(Classifiable o1, Classifiable o2) 
        {
            Classification classification1 = o1.getClassification();
            Classification classification2 = o2.getClassification();
            if ( classification1 == classification2)
            {
                return 0;
            }
            DynamicType type1= classification1.getType();
            DynamicType type2= classification2.getType();
            if ( type1.equals( type2) )
            {
                for (Attribute attribute:type1.getAttributeIterable())
                {
                    String sorting = attribute.getAnnotation( AttributeAnnotations.KEY_SORTING);
                    if ( sorting != null)
                    {
                        int order = 0;
                        if ( sorting.equals(AttributeAnnotations.VALUE_SORTING_ASCENDING))
                        {
                            order = 1;
                        }
                        if ( sorting.equals(AttributeAnnotations.VALUE_SORTING_DESCENDING))
                        {
                            order = -1;
                        }
                        if ( order != 0)
                        {
                            Object value1 = classification1.getValue( attribute);
                            Object value2 = classification2.getValue( attribute);
                            if ( value1 != null )
                            {
                                if ( value2 == null)
                                {
                                    return order;
                                }
                                if ( value1 instanceof Comparable && value2 instanceof Comparable)
                                {
                                    Comparable comparable = (Comparable) value1;
                                    @SuppressWarnings("unchecked")
                                    int result = order * comparable.compareTo( value2);
                                    if ( result != 0)
                                    {
                                        return result;
                                    }
                                } 
                                // TODO if field is allocatable link, then we need to sort with this comparator 
                                // what todo with links to allocatables that could cause infinite recursion
                            }
                            else if ( value2 != null)
                            {
                                return -order;
                            }
                        }
                    }
                }
            }
            return comp.compare(classification1, classification2);
        }
        
    }
    
    public TreeModel createClassifiableModel(Allocatable[] classifiables) {
    	boolean useCategorizations = true;
    	return createClassifiableModel( classifiables, useCategorizations);
    }
   
    private TreeModel createClassifiableModel(Classifiable[] classifiables, Comparator<Classifiable> comp,boolean useCategorizations) {
        Set<DynamicType> typeSet = new LinkedHashSet<DynamicType>();
    	for (Classifiable classifiable: classifiables)
    	{
    		DynamicType type = classifiable.getClassification().getType();
			typeSet.add( type);
    	}
    	List<DynamicType> typeList = new ArrayList<DynamicType>(typeSet);
    	Collections.sort(typeList, new DynamicTypeComperator());
        Map<DynamicType,DefaultMutableTreeNode> nodeMap = new HashMap<DynamicType,DefaultMutableTreeNode>();
        for (DynamicType type: typeList) {
        	DefaultMutableTreeNode node = new NamedNode(type);
            nodeMap.put(type, node);
        }
        
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
        Set<Classifiable> sortedClassifiable = new TreeSet<Classifiable>(comp);
        sortedClassifiable.addAll(Arrays.asList(classifiables));
        addClassifiables(nodeMap, sortedClassifiable, useCategorizations);
        int count = 0;
        for (DynamicType type: typeList) {
            DefaultMutableTreeNode typeNode =  nodeMap.get(type);
            root.insert(typeNode, count++);
        }
        return new DefaultTreeModel(root);
    }

    
	private Map<Classifiable, Collection<NamedNode>> addClassifiables(Map<DynamicType, DefaultMutableTreeNode > nodeMap,Collection<? extends Classifiable> classifiables,boolean useCategorizations) 
	{
		Map<DynamicType,Map<Object,DefaultMutableTreeNode>> categorization = new LinkedHashMap<DynamicType, Map<Object,DefaultMutableTreeNode>>();
        Map<Classifiable, Collection<NamedNode>> childMap = new HashMap<Classifiable, Collection<NamedNode>>();
		Map<DynamicType,Collection<NamedNode>> uncategorized = new LinkedHashMap<DynamicType, Collection<NamedNode>>();
        for ( DynamicType type: nodeMap.keySet())
        {
        	categorization.put( type, new LinkedHashMap<Object, DefaultMutableTreeNode>());
        	uncategorized.put( type, new ArrayList<NamedNode>());
        }
		for (Iterator<? extends Classifiable> it = classifiables.iterator(); it.hasNext();) {
            Classifiable classifiable =  it.next();
            Classification classification = classifiable.getClassification();
            Collection<NamedNode> childNodes = new ArrayList<NamedNode>();
            childMap.put( classifiable, childNodes);
            DynamicType type = classification.getType();
            Assert.notNull(type);
            DefaultMutableTreeNode typeNode = nodeMap.get(type);
            // type not found, could be because type is not visible
            if ( typeNode == null)
            {
                continue;
            }
            DefaultMutableTreeNode parentNode = typeNode;
            Attribute categorizationAtt = getCategorizationAttribute(classification);
            if (useCategorizations && categorizationAtt != null && classification.getValues(categorizationAtt).size() > 0)
            {
            	Collection<Object> values = classification.getValues(categorizationAtt);
            	for ( Object value:values)
            	{
            		NamedNode childNode = new NamedNode((Named) classifiable);
                    childNodes.add( childNode);
                    Map<Object, DefaultMutableTreeNode> map = categorization.get(type);
            		parentNode = map.get( value);
            		if ( parentNode == null)
            		{
            			String name = getName( value);
            			parentNode = new DefaultMutableTreeNode(new Categorization(name));
            			map.put( value, parentNode);
            		}
                	parentNode.add(childNode);
            	}
            }
            else
            {	
            	NamedNode childNode = new NamedNode((Named) classifiable);
                childNodes.add( childNode);
                Assert.notNull(typeNode);
                uncategorized.get(type).add( childNode);
            }
        }
		for ( DynamicType type:categorization.keySet())
		{
			DefaultMutableTreeNode parentNode = nodeMap.get( type);
			//Attribute categorizationAtt = type.getAttribute("categorization");
            Map<Object, DefaultMutableTreeNode> map = categorization.get( type);
			Collection<Object> sortedCats = getSortedCategorizations(map.keySet());
			for ( Object cat: sortedCats)
			{
				DefaultMutableTreeNode childNode = map.get(cat);
				parentNode.add(childNode);
			}
		}
		
		for ( DynamicType type: uncategorized.keySet())
		{
			DefaultMutableTreeNode parentNode = nodeMap.get( type);
			for (NamedNode node:uncategorized.get( type))
			{
				parentNode.add(node);
			}
		}
		return childMap;
	}

    protected Attribute getCategorizationAttribute(Classification classification) {
        for (Attribute attribute: classification.getType().getAttributeIterable())
        {
            String annotation = attribute.getAnnotation(AttributeAnnotations.KEY_CATEGORIZATION);
            if  ( annotation != null && annotation.equals("true"))
            {
                return attribute;
            }
        }
        return null;
    }

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Collection<Object> getSortedCategorizations(Collection<Object> unsortedCats) {
		ArrayList<Comparable> sortableCats = new ArrayList<Comparable>();
		ArrayList<Object> unsortableCats = new ArrayList<Object>();
		// All attribute values should implement Comparable but for the doubts we test if value is not comparable
		for ( Object cat: unsortedCats)
		{
			if ( cat instanceof Comparable)
			{					
				sortableCats.add( (Comparable<?>) cat);
			}
			else
			{
				unsortableCats.add( cat);
			}
		}
		Collections.sort( sortableCats);
		List<Object> allCats = new ArrayList<Object>( sortableCats);
		allCats.addAll( unsortableCats);
		return allCats;
	}
	
	class Categorization implements Comparable<Categorization>
	{
	    String cat;
	    public Categorization(String cat) {
	        this.cat = cat.intern();
	    }
	    
	    public String toString()
	    {
	        return cat;
	    }
	    
	    public boolean equals( Object obj)
	    {
	        return cat.equals( obj.toString());
	    }

	    public int hashCode() {
	        return cat.hashCode();
	    }

	    public int compareTo(Categorization o) 
        {
            return cat.compareTo( o.cat);
        }
	    
	}

    public TreeCellRenderer createConflictRenderer() {
        return new ConflictTreeCellRenderer();
    }

    private boolean isInFilter(ClassificationFilter[] filter, Classifiable classifiable) {
        if (filter == null)
            return true;
        for (int i = 0; i < filter.length; i++) {
            if (filter[i].matches(classifiable.getClassification())) {
                return true;
            }
        }
        return false;
    }

    private boolean isInFilter(ClassificationFilter[] filter, DynamicType type) {
        if (filter == null)
            return true;
        for (int i = 0; i < filter.length; i++) {
            if (filter[i].getType().equals(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRulesFor(ClassificationFilter[] filter, DynamicType type) {
        if (filter == null)
            return false;
        for (int i = 0; i < filter.length; i++) {
            if (filter[i].getType().equals(type) && filter[i].ruleSize() > 0) {
                return true;
            }
        }
        return false;
    }

  

    /**
     * Returns the Resources root
     * 
     * @param filter
     * @param selectedUser
     * @return
     * @throws RaplaException
     */
    public TypeNode createResourcesModel(ClassificationFilter[] filter) throws RaplaException {
        TypeNode treeNode = new TypeNode(Allocatable.TYPE, CalendarModelImpl.ALLOCATABLES_ROOT, getString("resources"));
        Map<DynamicType,DefaultMutableTreeNode> nodeMap = new HashMap<DynamicType, DefaultMutableTreeNode>();

        boolean resourcesFiltered = false;

        DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        for (int i = 0; i < types.length; i++) {
            DynamicType type = types[i];
            if (hasRulesFor(filter, type)) {
                resourcesFiltered = true;
            }
            if (!isInFilter(filter, type)) {
                resourcesFiltered = true;
                continue;
            }

            NamedNode node = new NamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }

        // creates typ folders
        types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
        for (int i = 0; i < types.length; i++) {
            DynamicType type = types[i];
            if (hasRulesFor(filter, type)) {
                resourcesFiltered = true;
            }
            if (!isInFilter(filter, type)) {
                resourcesFiltered = true;
                continue;
            }

            NamedNode node = new NamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }

        treeNode.setFiltered(resourcesFiltered);

        // adds elements to typ folders
        Allocatable[] allocatables = getQuery().getAllocatables();
        Collection<Allocatable> filtered = new ArrayList<Allocatable>();
        for (Allocatable classifiable: allocatables) {
            if (!isInFilter(filter, classifiable)) {
                continue;
            }
            filtered.add( classifiable);
        }
        Collection<Allocatable> sorted = sorted(filtered, new SortedClassifiableComparator(getLocale()));
		addClassifiables(nodeMap, sorted, true);

// CK we disable the feature that we dont show empty resource nodes. Resource nodes visibility can be regulated by types
//        for (Map.Entry<DynamicType, DefaultMutableTreeNode> entry: nodeMap.entrySet())
//        {
//            DynamicType key = entry.getKey();
//        	MutableTreeNode value = entry.getValue();
//        	if  (value.getChildCount() == 0 && (!isAdmin() && !isRegisterer(key)))
//        	{
//        		treeNode.remove( value);
//        	}
//        }
        return treeNode;
    }


    private <T extends Named> Collection<T> sorted(Collection<T> allocatables) {
        TreeSet<T> sortedList = new TreeSet<T>(new NamedComparator<T>(getLocale()));
        sortedList.addAll(allocatables);
        return sortedList;
    }
    
    private <T extends Classifiable> Collection<T> sorted(Collection<T> allocatables, Comparator<Classifiable> comp) {
        TreeSet<T> sortedList = new TreeSet<T>(comp);
        sortedList.addAll(allocatables);
        return sortedList;
    }
    

    public TypeNode createReservationsModel() throws RaplaException {
        TypeNode treeNode = new TypeNode(Reservation.TYPE, getString("reservation_type"));

        // creates typ folders
        DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        for (int i = 0; i < types.length; i++) {
            DynamicType type = types[i];
            
            
            NamedNode node = new NamedNode(type);
            treeNode.add(node);
        }
        treeNode.setFiltered(false);
        return treeNode;
    }

    @SuppressWarnings("deprecation")
	public DefaultTreeModel createModel(ClassificationFilter[] filter) throws RaplaException 
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");

        // Resources and Persons
        //  Add the resource types
        //   Add the resources
        //  Add the person types
        //    Add the persons
        TypeNode resourceRoot = createResourcesModel(filter);
        root.add(resourceRoot);
       
        if (isAdmin()) 
        {
            // If admin
            // Eventtypes
            // Add the event types
            // Users
            // Add the users
            // Categories (the root category)
            // Add the periods

            DefaultMutableTreeNode userRoot = new TypeNode(User.TYPE, getString("users"));
            User[] userList = getQuery().getUsers();
            SortedSet<User> sorted = new TreeSet<User>( User.USER_COMPARATOR);
            sorted.addAll( Arrays.asList( userList));
            for (final User user: sorted) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode();
                node.setUserObject( user);
                userRoot.add(node);
            }
            root.add(userRoot);

            TypeNode reservationsRoot = createReservationsModel();
            root.add(reservationsRoot);
            
            NamedNode categoryRoot = createRootNode( Collections.singleton(getQuery().getSuperCategory()),true);
            root.add(categoryRoot);

            // set category root name
            MultiLanguageName multiLanguageName = (MultiLanguageName)getQuery().getSuperCategory().getName();
            // TODO try to replace hack
			multiLanguageName.setNameWithoutReadCheck(getI18n().getLang(), getString("categories"));
            
            // Add the periods    
            DefaultMutableTreeNode periodRoot = new TypeNode(Period.TYPE, getString("periods"));
            DynamicType periodType = getQuery().getDynamicType(StorageOperator.PERIOD_TYPE);
            
            Allocatable[] periodList = getQuery().getAllocatables(periodType.newClassificationFilter().toArray());
            for (final Allocatable period: sorted(Arrays.asList(periodList))) {
                NamedNode node = new NamedNode(period);
                periodRoot.add(node);
            }
            root.add(periodRoot);
        }
        return new DefaultTreeModel(root);
    }
    
    private class ConflictRoot
    {
        final String text;
        int conflictNumber = 0;
        

        ConflictRoot(String text)
        {
            this.text = text;
        }

        public void setConflictNumber(int conflictNumber) {
            this.conflictNumber = conflictNumber;
        }

        
        @Override
        public String toString() {
            String result =getI18n().format(text, conflictNumber);
            return result;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((text == null) ? 0 : text.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConflictRoot other = (ConflictRoot) obj;
//            if (conflictNumber != other.conflictNumber)
//                return false;
            if (text == null) {
                if (other.text != null)
                    return false;
            } else if (!text.equals(other.text))
                return false;
            return true;
        }

       
        
    }
    public DefaultTreeModel createConflictModel(Collection<Conflict> conflicts ) throws RaplaException {
	    DefaultMutableTreeNode rootNode = new TypeNode(Conflict.TYPE, "root");
	    ConflictRoot conflictRootObj = new ConflictRoot("conflictUC");
		DefaultMutableTreeNode treeNode = new TypeNode(Conflict.TYPE, conflictRootObj);
		rootNode.add( treeNode );
        if ( conflicts != null )
        {
            {
                Iterable<Conflict> filteredConflicts = filter(conflicts, true);
                int conflict_number = addConflicts(filteredConflicts, treeNode);
                conflictRootObj.setConflictNumber( conflict_number);
            }
            {
                Iterable<Conflict> filteredConflicts = filter(conflicts, false);
                ConflictRoot conflictDisabledRootObj = new ConflictRoot("disabledConflictUC");
                DefaultMutableTreeNode treeNode2 = new TypeNode(Conflict.TYPE, conflictDisabledRootObj);
                int conflict_number = addConflicts(filteredConflicts, treeNode2);
                if ( conflict_number > 0 )
                {
                    conflictDisabledRootObj.setConflictNumber(conflict_number);
                    rootNode.add( treeNode2);
                }
            } 
        }
        return new DefaultTreeModel(rootNode);
    }

    private Iterable<Conflict> filter(Iterable<Conflict> conflicts, final boolean enabledState) {
        return new FilterIterable<Conflict>( conflicts) {
            protected boolean isInIterator(Object obj) {
                boolean inIterator = ((Conflict)obj).checkEnabled() == enabledState;
                return inIterator;
//                return true;
            }
        };
    }

    private int addConflicts(Iterable<Conflict> conflicts, DefaultMutableTreeNode treeNode) throws RaplaException {
        int conflictsAdded = 0;
        Map<DynamicType,DefaultMutableTreeNode> nodeMap = new LinkedHashMap<DynamicType, DefaultMutableTreeNode>();
        DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        for (int i = 0; i < types.length; i++) {
            DynamicType type = types[i];
            NamedNode node = new NamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }

        // creates typ folders
        types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
        for (int i = 0; i < types.length; i++) {
            DynamicType type = types[i];
            NamedNode node = new NamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }
        Collection<Allocatable> allocatables = new LinkedHashSet<Allocatable>();
        for (Iterator<Conflict> it = conflicts.iterator(); it.hasNext();) {
            Conflict conflict = it.next();
            Allocatable allocatable = conflict.getAllocatable();
            allocatables.add( allocatable );
        }
        Collection<Allocatable> sorted = sorted(allocatables, new SortedClassifiableComparator(getLocale()));
        Map<Classifiable, Collection<NamedNode>> childMap = addClassifiables(nodeMap, sorted, true);
        for (Iterator<Conflict> it = conflicts.iterator(); it.hasNext();) {
            Conflict conflict = it.next();
            conflictsAdded++;
            Allocatable allocatable = conflict.getAllocatable();
            for(NamedNode allocatableNode : childMap.get( allocatable))
            {
                allocatableNode.add(new NamedNode( conflict));
            }
        }
        for (Map.Entry<DynamicType, DefaultMutableTreeNode> entry: nodeMap.entrySet())
        {
            MutableTreeNode value = entry.getValue();
            if  (value.getChildCount() == 0 )
            {
                treeNode.remove( value);
            }
        }
        return conflictsAdded;
    }

    class TypeNode extends DefaultMutableTreeNode {
        private static final long serialVersionUID = 1L;

        boolean filtered;
        RaplaType type;
        String title;

        TypeNode(RaplaType type, Object userObject, String title) {
            this.type = type;
            this.title = title;
            setUserObject(userObject);
        }

        TypeNode(RaplaType type, Object userObject) {
            this(type, userObject, null);
        }

        public RaplaType getType() {
            return type;
        }

        public boolean isFiltered() {
            return filtered;
        }

        public void setFiltered(boolean filtered) {
            this.filtered = filtered;
        }

        public Object getTitle() {
            if (title != null) {
                return title;
            } else {
                return userObject.toString();
            }
        }

    }

    public DefaultMutableTreeNode newNamedNode(Named element) {
        return new NamedNode(element);
    }

    public TreeModel createModel(Category category) {
        return createModel(Collections.singleton(category), true );
    }
    
    public TreeModel createModel(Collection<Category> categories, boolean includeChildren)
    {
    	DefaultMutableTreeNode rootNode = createRootNode(categories,includeChildren);
		return new DefaultTreeModel( rootNode);
    }

	protected NamedNode createRootNode(
			Collection<Category> categories, boolean includeChildren) {
		Map<Category,NamedNode> nodeMap = new HashMap<Category, NamedNode>();
    	Category superCategory = null;
    	{
	    	Category persistantSuperCategory = getQuery().getSuperCategory();
	    	for ( Category cat:categories)
	    	{
	    		if ( persistantSuperCategory.equals( cat))
	    		{
	    			superCategory = cat;
	    		}
	    	}
	    	if (superCategory == null)
	    	{
	    		superCategory = persistantSuperCategory;
	    	}
    	}
    	nodeMap.put( superCategory, new NamedNode(superCategory));
    	LinkedHashSet<Category> uniqueCategegories = new LinkedHashSet<Category>( );
    	for ( Category cat:categories)
    	{
    		if ( includeChildren)
    		{
    	  		for( Category child:getAllChildren( cat))
        		{
        			uniqueCategegories.add( child);
        		}
    		}
    		uniqueCategegories.add( cat);
    	}
    	LinkedList<Category> list = new LinkedList<Category>();
		list.addAll( uniqueCategegories);
		while ( !list.isEmpty())
		{
			Category cat = list.pop();
			NamedNode node = nodeMap.get( cat);
			if (node == null)
			{
				node = new NamedNode( cat);
				nodeMap.put( cat , node);
			}
			Category parent = cat.getParent();
			if ( parent != null)
			{
				NamedNode parentNode = nodeMap.get( parent);
				if ( parentNode == null)
				{
					parentNode = new NamedNode( parent);
					nodeMap.put( parent , parentNode);
					list.push( parent);
				}
				parentNode.add( node);
			}
		}
		NamedNode rootNode = nodeMap.get( superCategory);
		while ( true)
		{
			int childCount = rootNode.getChildCount();
			if ( childCount <= 0 || childCount >1)
			{
				break;
			}
			Category cat = (Category) rootNode.getUserObject();
			if ( categories.contains( cat))
			{
				break;
			}
			NamedNode firstChild = (NamedNode)rootNode.getFirstChild();
			rootNode.remove( firstChild);
			rootNode = firstChild;
		
		}
		return rootNode;
	}

    private Collection<Category> getAllChildren(Category cat) {
    	ArrayList<Category> result = new ArrayList<Category>();
    	for ( Category child:  cat.getCategories())
    	{
    		result.add( child);
    		Collection<Category> childsOfChild = getAllChildren( child);
			result.addAll(childsOfChild);
    	}
    	return result;
	}

	public TreeModel createModelFlat(Named[] element) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        for (int i = 0; i < element.length; i++) {
            root.add(new NamedNode(element[i]));
        }
        return new DefaultTreeModel(root);
    }

    public TreeToolTipRenderer createTreeToolTipRenderer() {
        return new RaplaTreeToolTipRenderer();
    }

    public TreeCellRenderer createRenderer() {
        return new ComplexTreeCellRenderer();
    }

    public class NamedNode extends DefaultMutableTreeNode {
        private static final long serialVersionUID = 1L;

        NamedNode(Named obj) {
            super(obj);
        }
        
        public String toString() {
            Named obj = (Named) getUserObject();
            if (obj != null) {
                Locale locale = getI18n().getLocale();
            	if ( obj instanceof Classifiable)
            	{
            		Classification classification = ((Classifiable)obj).getClassification();
					if ( classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING) != null)
					{
						return classification.format(locale, DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING);
					}
            	}
				String name = obj.getName(locale);
				return name;
            } else {
                return super.toString();
            }
        }
		
        public int getIndexOfUserObject(Object object) {
        	if (children == null)
        	{
        		return -1;
        	}
	        for (int i=0;i<children.size();i++) {
	            if (((DefaultMutableTreeNode)children.get(i)).getUserObject().equals(object))
	                return i;
	        }
	        return -1;
	    }
		    
	    public TreeNode findNodeFor( Object obj ) {
	       return findNodeFor( this, obj);
	    }
	    
	    private TreeNode findNodeFor( DefaultMutableTreeNode node,Object obj ) {
	        Object userObject = node.getUserObject();
	        if ( userObject != null && userObject.equals( obj ) )
	            return node;
	        @SuppressWarnings("rawtypes")
			Enumeration e = node.children();
	        while (e.hasMoreElements())
	        {
				TreeNode result = findNodeFor((DefaultMutableTreeNode) e.nextElement(), obj );
	            if ( result != null ) {
	                return result;
	            }
	        }
	        return null;
	    }
		
    }
    
   

    Icon bigFolderUsers = getIcon("icon.big_folder_users");
    Icon bigFolderPeriods = getIcon("icon.big_folder_periods");
    Icon bigFolderResourcesFiltered = getIcon("icon.big_folder_resources_filtered");
    Icon bigFolderResourcesUnfiltered = getIcon("icon.big_folder_resources");
    Icon bigFolderEvents = getIcon("icon.big_folder_events");
    Icon bigFolderCategories = getIcon("icon.big_folder_categories");
    Icon bigFolderConflicts = getIcon("icon.big_folder_conflicts");
    Icon defaultIcon = getIcon("icon.tree.default");
    Icon personIcon = getIcon("icon.tree.persons");
    Icon folderClosedIcon =getIcon("icon.folder");
    Icon folderOpenIcon = getIcon("icon.folder");
    Icon forbiddenIcon = getIcon("icon.no_perm");
    Font normalFont = UIManager.getFont("Tree.font");
    Font bigFont =  normalFont.deriveFont(Font.BOLD, (float) (normalFont.getSize() * 1.2));
    
    class ComplexTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;
        
        Border nonIconBorder = BorderFactory.createEmptyBorder(1, 0, 1, 0);
        Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);
        Date today;

        public ComplexTreeCellRenderer() {
            setLeafIcon(defaultIcon);
            today = getQuery().today();
        }

        public void setLeaf(Object object) {
            Icon icon = null;
            if (object instanceof Allocatable) {
                Allocatable allocatable = (Allocatable) object;
                try {
    				User user = getUser();
    				if ( !allocatable.canAllocate(user,  today))
    				{
    					icon = forbiddenIcon;
    				}
    				else
    				{
    					 if (allocatable.isPerson()) {
    						 icon = personIcon;
    					 } else {
    						 icon = defaultIcon;
    					 }
    				}
                } catch (RaplaException ex) {
                }
               
            } else if (object instanceof DynamicType) {
                DynamicType type = (DynamicType) object;
                String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(classificationType)) {
                    setBorder(conflictBorder);
                } else {
                    icon = folderClosedIcon;
                }
            }
            if (icon == null) {
                setBorder(nonIconBorder);
            }
            setLeafIcon(icon);
        }
        
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            setBorder(null);
            setFont(normalFont);
            if (value != null && value instanceof TypeNode) {
                TypeNode typeNode = (TypeNode) value;
                Icon bigFolderIcon;
                if (typeNode.getType().equals(User.TYPE)) {
                    bigFolderIcon = bigFolderUsers;
                } else if (typeNode.getType().equals(Period.TYPE)) {
                    bigFolderIcon = bigFolderPeriods;
                } else if (typeNode.getType().equals(Reservation.TYPE)) {
                    bigFolderIcon = bigFolderEvents;
                } else {
                    if (typeNode.isFiltered()) {
                        bigFolderIcon = bigFolderResourcesFiltered;
                    } else {
                        bigFolderIcon = bigFolderResourcesUnfiltered;
                    }
                }
                setClosedIcon(bigFolderIcon);
                setOpenIcon(bigFolderIcon);
                setLeafIcon(bigFolderIcon);
                setFont(bigFont);
                value = typeNode.getTitle();
            } else {
                Object nodeInfo = getUserObject(value);
                if (nodeInfo instanceof Category && ((Category)nodeInfo).getParent() == null) {
                    setClosedIcon(bigFolderCategories);
                    setOpenIcon(bigFolderCategories);
                    setFont(bigFont);
                }
                else
                {
	                setClosedIcon(folderClosedIcon);
	                setOpenIcon(folderOpenIcon);
	                if (leaf) {
	                    setLeaf(nodeInfo);
	                }
                }
            }
            Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            return result;
        }

    }
    
    class ConflictTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;
        Border nonIconBorder = BorderFactory.createEmptyBorder(1, 0, 1, 0);
        Border conflictBorder = BorderFactory.createEmptyBorder(2, 0, 2, 0);
        public ConflictTreeCellRenderer() {
            setFont(normalFont);
            setLeafIcon(null);
            setBorder(conflictBorder);
        }

        protected String getText( Conflict conflict) {
            StringBuffer buf = new StringBuffer();
            buf.append("<html>");
            Date startDate = conflict.getStartDate();
            RaplaLocale raplaLocale = getRaplaLocale();
            buf.append( raplaLocale.formatDate(startDate) );
            if (!DateTools.cutDate(startDate).equals(startDate))
            {
                buf.append(' ');
                buf.append( raplaLocale.formatTime(startDate) );
            }
//            buf.append( getAppointmentFormater().getSummary(conflict.getAppointment1()));
            buf.append( "<br>" );
            buf.append( conflict.getReservation1Name() );
            buf.append( ' ' );
            buf.append( getString("with"));
            buf.append( '\n' );
            buf.append( "<br>" );
            buf.append( conflict.getReservation2Name() );
            // TOD add the rest of conflict
//            buf.append( ": " );
//            buf.append( " " );
//            buf.append( getRaplaLocale().formatTime(conflict.getAppointment1().getStart()));
////            buf.append( " - ");
////            buf.append( getRaplaLocale().formatTime(conflict.getAppointment1().getEnd()));
//            buf.append( "<br>" );
//            buf.append( getString("reservation.owner") + " ");
//            buf.append( conflict.getUser2().getUsername());
            buf.append("</html>");
            String result = buf.toString();
            return result;
        }
        
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (value != null && value instanceof TypeNode) {
                TypeNode typeNode = (TypeNode) value;
                setFont(bigFont);
                value = typeNode.getTitle();
                setIcon(bigFolderConflicts);
                setClosedIcon(bigFolderConflicts);
                setOpenIcon(bigFolderConflicts);
                leaf = false;
            } else {
                setClosedIcon(folderClosedIcon);
                setOpenIcon(folderOpenIcon);
                Object nodeInfo = getUserObject(value);
                setFont(normalFont);
                if (nodeInfo instanceof Conflict) {
                    Conflict conflict = (Conflict) nodeInfo;
                    String text = getText(conflict);
                    value = text;
                }
                else if (nodeInfo instanceof Allocatable) {
                    Allocatable allocatable = (Allocatable) nodeInfo;
                    Icon icon;
                    if (allocatable.isPerson()) {
                        icon = personIcon;
                    } else {
                        icon = defaultIcon;
                    }
                    setClosedIcon(icon);
                    setOpenIcon(icon);
                    // can't be null because nodeInfo is not null
                    @SuppressWarnings("null")
                    String text = value.toString() ;
                    if ( value instanceof TreeNode)
                    {
                        text+= " (" + getRecursiveChildCount(((TreeNode) value)) +")";
                    }
                    value = text;
                }
                else
                {
                    String text = TreeFactoryImpl.this.getName( nodeInfo);
                    if ( value instanceof TreeNode)
                    {
                        //text+= " (" + getRecursiveChildCount(((TreeNode) value)) +")";
                        text+= " (" + getRecursiveList(((TreeNode) value)).size() +")";
                        
                    }
                    value = text;
                                           
                }
            }
            Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            return result;
        }

        private int getRecursiveChildCount(TreeNode treeNode)
        {
            int count = 0;
            int children= treeNode.getChildCount();
            if ( children == 0)
            {
                return 1;
            }
            for ( int i=0;i<children;i++)
            {
                TreeNode child = treeNode.getChildAt(i);
                count+= getRecursiveChildCount( child);
            }
            return count;
        }
        
        private Set<Conflict> getRecursiveList(TreeNode treeNode)
        {
            int children= treeNode.getChildCount();
            if ( children == 0)
            {
                return Collections.emptySet();
            }
            HashSet<Conflict> set = new HashSet<Conflict>();
            for ( int i=0;i<children;i++)
            {
                TreeNode child = treeNode.getChildAt(i);
                Object userObject = ((DefaultMutableTreeNode)child).getUserObject();
                if ( userObject != null && userObject instanceof Conflict)
                {
                	set.add((Conflict)userObject);
                }
                else
                {
                	set.addAll(getRecursiveList( child));
                }
            }
            return set;
        }

    }
    
    public TreeSelectionModel createComplexTreeSelectionModel()
    {
        return new DelegatingTreeSelectionModel()
        {
            private static final long serialVersionUID = 1L;

            boolean isSelectable(TreePath treePath)
            {
                Object lastPathComponent = treePath.getLastPathComponent();
                Object object = getUserObject( lastPathComponent);
                if ( object instanceof Categorization)
                {
                    return false;
                }
                return true;
            }
        };
    }
    
    public TreeSelectionModel createConflictTreeSelectionModel()
    {
        return new DelegatingTreeSelectionModel()
        {
            private static final long serialVersionUID = 1L;
            boolean isSelectable(TreePath treePath)
            {
                Object lastPathComponent = treePath.getLastPathComponent();
                Object object = getUserObject( lastPathComponent);
                if ( object instanceof Conflict)
                {
                    return true;
                }
                if ( object instanceof Allocatable)
                {
                    return true;
                }
                if ( object instanceof DynamicType)
                {
                    return true;
                }
                return false;
            }
        };
    }
    
    
    private abstract class DelegatingTreeSelectionModel extends DefaultTreeSelectionModel {
        abstract boolean isSelectable(TreePath treePath);
        private static final long serialVersionUID = 1L;

        private TreePath[] getSelectablePaths(TreePath[] pathList) {
            List<TreePath> result = new ArrayList<TreePath>(pathList.length);
            for (TreePath treePath : pathList) {
                if (isSelectable(treePath)) {
                    result.add(treePath);
                }
            }
            return result.toArray(new TreePath[result.size()]);
        }

        @Override
        public void setSelectionPath(TreePath path) {
            if (isSelectable(path)) {
                super.setSelectionPath(path);
            }
        }

        @Override
        public void setSelectionPaths(TreePath[] paths) {
            paths = getSelectablePaths(paths);
            super.setSelectionPaths(paths);
        }

        @Override
        public void addSelectionPath(TreePath path) {
            if (isSelectable(path)) {
                super.addSelectionPath(path);
            }
        }

        @Override
        public void addSelectionPaths(TreePath[] paths) {
            paths = getSelectablePaths(paths);
            super.addSelectionPaths(paths);
        }

    }

    private static Object getUserObject(Object node) {
        if (node instanceof DefaultMutableTreeNode)
            return ((DefaultMutableTreeNode) node).getUserObject();
        return node;
    }

    class RaplaTreeToolTipRenderer implements TreeToolTipRenderer {
        public String getToolTipText(JTree tree, int row) {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            Object value = getUserObject(node);
            if (value instanceof Conflict) {
                return null;
            }
            return getInfoFactory().getToolTip(value);
        }
    }

}
