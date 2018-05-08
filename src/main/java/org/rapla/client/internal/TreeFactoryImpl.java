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
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.client.TreeFactory;
import org.rapla.client.RaplaTreeNode;
import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.FilterIterable;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.SortedClassifiableComparator;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@DefaultImplementation(of = TreeFactory.class, context = InjectionContext.client)
public class TreeFactoryImpl extends RaplaComponent implements TreeFactory
{
    final ClientFacade clientFacade;
    final TreeItemFactory treeItemFactory;
    @Inject
    public TreeFactoryImpl(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeItemFactory treeItemFactory)
    {
        super(clientFacade.getRaplaFacade(), i18n, raplaLocale, logger);
        this.clientFacade = clientFacade;
        this.treeItemFactory = treeItemFactory;
    }

    class DynamicTypeComperator implements Comparator<DynamicType>
    {
        public int compare(DynamicType o1, DynamicType o2)
        {
            int rang1 = getRang(o1);
            int rang2 = getRang(o2);
            if (rang1 < rang2)
            {
                return -1;
            }
            if (rang1 > rang2)
            {
                return 1;
            }
            return compareIds((DynamicTypeImpl) o1, (DynamicTypeImpl) o2);
        }

        private int compareIds(DynamicTypeImpl o1, DynamicTypeImpl o2)
        {
            return o1.compareTo(o2);
        }

        private int getRang(DynamicType o1)
        {
            String t2 = o1.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (t2 != null && t2.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
            {
                return 1;
            }
            if (t2 != null && t2.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
            {
                return 2;
            }
            else
            {
                return 3;
            }
        }
    }

    public RaplaTreeNode createClassifiableModel(Allocatable[] classifiables, boolean useCategorizations)
    {
        @SuppressWarnings({ "rawtypes" })
        Comparator<Classifiable> comp = new SortedClassifiableComparator(getLocale());
        return createClassifiableModel(classifiables, comp, useCategorizations);
    }

    private RaplaTreeNode createClassifiableModel(Classifiable[] classifiables, Comparator<Classifiable> comp, boolean useCategorizations)
    {
        Set<DynamicType> typeSet = new LinkedHashSet<>();
        for (Classifiable classifiable : classifiables)
        {
            DynamicType type = classifiable.getClassification().getType();
            typeSet.add(type);
        }
        List<DynamicType> typeList = new ArrayList<>(typeSet);
        Collections.sort(typeList, new DynamicTypeComperator());
        Map<DynamicType, RaplaTreeNode> nodeMap = new HashMap<>();
        for (DynamicType type : typeList)
        {
            RaplaTreeNode node = newNamedNode(type);
            nodeMap.put(type, node);
        }

        Set<Classifiable> sortedClassifiable = new TreeSet<>(comp);
        sortedClassifiable.addAll(Arrays.asList(classifiables));
        addClassifiables(nodeMap, sortedClassifiable, useCategorizations);
        RaplaTreeNode root = newRootNode();
        for (DynamicType type : typeList)
        {
            RaplaTreeNode typeNode = nodeMap.get(type);
            root.add(typeNode);
        }
        return root;
    }

    private Map<Classifiable, Collection<RaplaTreeNode>> addClassifiables(Map<DynamicType, RaplaTreeNode> nodeMap,
                                                                          Collection<? extends Classifiable> classifiables, boolean useCategorizations)
    {
        Map<DynamicType, Map<Object, RaplaTreeNode>> categorization = new LinkedHashMap<>();
        Map<Classifiable, Collection<RaplaTreeNode>> childMap = new HashMap<>();
        Map<Classifiable, Collection<Classifiable>> belongsToMap = new HashMap<>();
        Map<Classifiable, RaplaTreeNode> objectToNamedNode = new HashMap<>();
        Map<DynamicType, Collection<RaplaTreeNode>> uncategorized = new LinkedHashMap<>();
        for (DynamicType type : nodeMap.keySet())
        {
            categorization.put(type, new LinkedHashMap<>());
            uncategorized.put(type, new ArrayList<>());
        }
        for (Iterator<? extends Classifiable> it = classifiables.iterator(); it.hasNext(); )
        {
            Classifiable classifiable = it.next();
            Classification classification = classifiable.getClassification();
            Collection<RaplaTreeNode> childNodes = new ArrayList<>();
            childMap.put(classifiable, childNodes);
            DynamicType type = classification.getType();
            Assert.notNull(type);
            RaplaTreeNode typeNode = nodeMap.get(type);
            // type not found, could be because type is not visible
            if (typeNode == null)
            {
                continue;
            }
            Attribute categorizationAtt = getCategorizationAttribute(classification);
            Attribute belongsAtt = ((DynamicTypeImpl) classification.getType()).getBelongsToAttribute();
            if (belongsAtt != null && classification.getValueForAttribute(belongsAtt) != null)
            {
                Classifiable parent = (Classifiable) classification.getValueForAttribute(belongsAtt);
                Collection<Classifiable> parts = belongsToMap.get(parent);
                if (parts == null)
                {
                    parts = new ArrayList<>();
                    belongsToMap.put(parent, parts);
                }
                parts.add(classifiable);
            }
            if (useCategorizations && categorizationAtt != null && classification.getValues(categorizationAtt).size() > 0)
            {
                Collection<Object> values = classification.getValues(categorizationAtt);
                for (Object value : values)
                {
                    RaplaTreeNode childNode = newNamedNode(classifiable);
                    childNodes.add(childNode);
                    Map<Object, RaplaTreeNode> map = categorization.get(type);
                    RaplaTreeNode parentNode = map.get(value);
                    if (parentNode == null)
                    {
                        String name = getName(value);
                        parentNode = newNamedNode(new Categorization(name));
                        map.put(value, parentNode);
                    }
                    parentNode.add(childNode);
                }
            }
            else
            {
                RaplaTreeNode childNode = newNamedNode( classifiable);
                objectToNamedNode.put(classifiable, childNode);
                childNodes.add(childNode);
                Assert.notNull(typeNode);
                uncategorized.get(type).add(childNode);
                if (useCategorizations)
                {
                    fillPackages(classification, childNode);
                }
            }

        }
        for (DynamicType type : categorization.keySet())
        {
            RaplaTreeNode parentNode = nodeMap.get(type);
            //Attribute categorizationAtt = type.getAttribute("categorization");
            Map<Object, RaplaTreeNode> map = categorization.get(type);
            Collection<Object> sortedCats = getSortedCategorizations(map.keySet());
            for (Object cat : sortedCats)
            {
                RaplaTreeNode childNode = map.get(cat);
                parentNode.add(childNode);
            }
        }

        for (DynamicType type : uncategorized.keySet())
        {
            RaplaTreeNode parentNode = nodeMap.get(type);
            for (RaplaTreeNode node : uncategorized.get(type))
            {
                parentNode.add(node);
            }
        }
        if (useCategorizations)
        {
            for (Classifiable classifiable : classifiables)
            {
                final RaplaTreeNode node = objectToNamedNode.get(classifiable);
                if (node != null)
                {
                    addBelongsToNodes(belongsToMap, classifiable, node);
                }
            }
        }
        return childMap;
    }

    private void fillPackages(Classification classification, RaplaTreeNode node)
    {
        final Attribute packagesAttribute = ((DynamicTypeImpl) classification.getType()).getPackagesAttribute();
        if (packagesAttribute != null)
        {
            final Collection<Object> values = classification.getValues(packagesAttribute);
            for (Object target : values)
            {
                if (target instanceof Classifiable)
                {
                    final Classifiable classifiable = (Classifiable) target;
                    RaplaTreeNode childNode = newNamedNode((Named) classifiable);
                    node.add(childNode);
                    final Classification childClassification = classifiable.getClassification();
                    fillPackages(childClassification, childNode);
                }
            }
        }
    }

    private void addBelongsToNodes(Map<Classifiable, Collection<Classifiable>> belongsToMap, Classifiable classifiable, final RaplaTreeNode node)
    {
        final Collection<Classifiable> parts = belongsToMap.get(classifiable);
        if (parts != null)
        {
            for (Classifiable belongsTo : parts)
            {
                final RaplaTreeNode newChildNode = newNamedNode(belongsTo);
                node.add(newChildNode);
                addBelongsToNodes(belongsToMap, belongsTo, newChildNode);
            }
        }
    }

    protected Attribute getCategorizationAttribute(Classification classification)
    {
        for (Attribute attribute : classification.getType().getAttributeIterable())
        {
            String annotation = attribute.getAnnotation(AttributeAnnotations.KEY_CATEGORIZATION);
            if (annotation != null && annotation.equals("true"))
            {
                return attribute;
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Collection<Object> getSortedCategorizations(Collection<Object> unsortedCats)
    {
        ArrayList<Comparable> sortableCats = new ArrayList<>();
        ArrayList<Object> unsortableCats = new ArrayList<>();
        // All attribute values should implement Comparable but for the doubts we test if value is not comparable
        for (Object cat : unsortedCats)
        {
            if (cat instanceof Comparable)
            {
                sortableCats.add((Comparable<?>) cat);
            }
            else
            {
                unsortableCats.add(cat);
            }
        }
        Collections.sort(sortableCats);
        List<Object> allCats = new ArrayList<>(sortableCats);
        allCats.addAll(unsortableCats);
        return allCats;
    }

    public class Categorization implements Comparable<Categorization>,Named
    {
        String cat;

        public Categorization(String cat)
        {
            this.cat = cat.intern();
        }

        public String toString()
        {
            return cat;
        }

        public boolean equals(Object obj)
        {
            return cat.equals(obj.toString());
        }

        public int hashCode()
        {
            return cat.hashCode();
        }

        public int compareTo(Categorization o)
        {
            return cat.compareTo(o.cat);
        }

        @Override
        public String getName(Locale locale) {
            return cat;
        }
    }


    private boolean isInFilter(ClassificationFilter[] filter, DynamicType type)
    {
        if (filter == null)
            return true;
        for (int i = 0; i < filter.length; i++)
        {
            if (filter[i].getType().equals(type))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasRulesFor(ClassificationFilter[] filter, DynamicType type)
    {
        if (filter == null)
            return false;
        for (int i = 0; i < filter.length; i++)
        {
            if (filter[i].getType().equals(type) && filter[i].ruleSize() > 0)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the Resources root
     *
     * @param filter
     * @return
     * @throws RaplaException
     */

    @Override
    public AllocatableNodes createAllocatableModel(ClassificationFilter[] filter) throws RaplaException
    {
        RaplaTreeNode treeNode = newNode(CalendarModelImpl.ALLOCATABLES_ROOT);
        Map<DynamicType, RaplaTreeNode> nodeMap = new HashMap<>();
        boolean resourcesFiltered = false;

        DynamicType[] types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        for (int i = 0; i < types.length; i++)
        {
            DynamicType type = types[i];
            if (hasRulesFor(filter, type))
            {
                resourcesFiltered = true;
            }
            if (!isInFilter(filter, type))
            {
                resourcesFiltered = true;
                continue;
            }

            RaplaTreeNode node = newNamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }

        // creates typ folders
        types = getQuery().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
        for (int i = 0; i < types.length; i++)
        {
            DynamicType type = types[i];
            if (hasRulesFor(filter, type))
            {
                resourcesFiltered = true;
            }
            if (!isInFilter(filter, type))
            {
                resourcesFiltered = true;
                continue;
            }

            RaplaTreeNode node = newNamedNode(type);
            treeNode.add(node);
            nodeMap.put(type, node);
        }
        // adds elements to typ folders
        Allocatable[] filtered = getQuery().getAllocatablesWithFilter(filter);
        Collection<Allocatable> sorted = sorted(filtered, new SortedClassifiableComparator(getLocale()));
        addClassifiables(nodeMap, sorted, true);
        return new AllocatableNodes( treeNode, resourcesFiltered);
    }

    static private Collection<Allocatable> sorted(Allocatable[] allocatables, Comparator<Classifiable> comp)
    {

        Map<String, Set<Allocatable>> typeSet = new LinkedHashMap<>();
        for (Allocatable alloc : allocatables)
        {
            final String key = alloc.getClassification().getType().getKey();
            Set<Allocatable> sortedSet = typeSet.get(key);
            if (sortedSet == null)
            {
                sortedSet = new TreeSet<>(comp);
                typeSet.put(key, sortedSet);
            }
            sortedSet.add(alloc);
        }
        LinkedHashSet<Allocatable> sortedList = new LinkedHashSet<>();
        for (Set<Allocatable> sortedSubset : typeSet.values())
        {
            sortedList.addAll(sortedSubset);
        }
        return sortedList;
    }


    public class ConflictRoot
    {
        final String text;
        int conflictNumber = 0;

        ConflictRoot(String text, int conflictNumber)
        {
            this.text = text;
            this.conflictNumber = conflictNumber;
        }

        public void setConflictNumber(int conflictNumber)
        {
            this.conflictNumber = conflictNumber;
        }

        @Override
        public String toString()
        {
            String result = getI18n().format(text, conflictNumber);
            return result;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((text == null) ? 0 : text.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConflictRoot other = (ConflictRoot) obj;
            //            if (conflictNumber != other.conflictNumber)
            //                return false;
            if (text == null)
            {
                if (other.text != null)
                    return false;
            }
            else if (!text.equals(other.text))
                return false;
            return true;
        }

    }

    public RaplaTreeNode
    createConflictModel(Collection<Conflict> conflicts) throws RaplaException
    {
        RaplaTreeNode rootNode = newRootNode();
        if (conflicts == null)
        {
            return rootNode;
        }
        {
            List<RaplaTreeNode> conflictList = new ArrayList<>();
            int conflict_number = addConflicts(filter(conflicts, true), conflictList);
            ConflictRoot conflictRootObj = new ConflictRoot("conflictUC", conflict_number);
            RaplaTreeNode treeNode = newNode(conflictRootObj);
            conflictList.forEach(treeNode::add);
            rootNode.add(treeNode);
        }
        List<RaplaTreeNode> disableConflictList = new ArrayList<>();
        int conflict_disabled_number = addConflicts(filter(conflicts, false), disableConflictList);
        if (conflict_disabled_number > 0)
        {
            ConflictRoot conflictDisabledRootObj = new ConflictRoot("disabledConflictUC", conflict_disabled_number);
            RaplaTreeNode treeNode = newNode( conflictDisabledRootObj);
            disableConflictList.forEach(treeNode::add);
            rootNode.add(treeNode);
        }
        return rootNode;
    }


    private Iterable<Conflict> filter(Iterable<Conflict> conflicts, final boolean enabledState)
    {
        return new FilterIterable<Conflict>(conflicts)
        {
            protected boolean isInIterator(Object obj)
            {
                boolean inIterator = ((Conflict) obj).checkEnabled() == enabledState;
                return inIterator;
            }
        };
    }

    private int addConflicts(Iterable<Conflict> conflicts, List<RaplaTreeNode> toAdd) throws RaplaException
    {
        int conflictsAdded = 0;
        Map<DynamicType, RaplaTreeNode> nodeMap = new LinkedHashMap<>();

        String[] classificationTypes = new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON};
        for (String classificationType: classificationTypes) {
            final DynamicType[] dynamicTypes = getQuery().getDynamicTypes(classificationType);
            for (DynamicType type: dynamicTypes) {
                RaplaTreeNode node = newNamedNode(type);
                nodeMap.put(type, node);
            }
        }

        Collection<Allocatable> allocatables = new LinkedHashSet<>();
        for (Iterator<Conflict> it = conflicts.iterator(); it.hasNext(); )
        {
            Conflict conflict = it.next();
            Allocatable allocatable = conflict.getAllocatable();
            allocatables.add(allocatable);
        }
        Collection<Allocatable> sorted = sorted(allocatables.toArray(new Allocatable[] {}), new SortedClassifiableComparator(getLocale()));
        Map<Classifiable, Collection<RaplaTreeNode>> childMap = addClassifiables(nodeMap, sorted, true);
        for (Iterator<Conflict> it = conflicts.iterator(); it.hasNext(); )
        {
            Conflict conflict = it.next();
            conflictsAdded++;
            Allocatable allocatable = conflict.getAllocatable();
            for (RaplaTreeNode allocatableNode : childMap.get(allocatable))
            {
                allocatableNode.add(newNamedNode(conflict));
            }
        }
        for ( RaplaTreeNode node:nodeMap.values())
        {
            if ( node.getChildCount() > 0) {
                toAdd.add(node);
            }
        }
        return conflictsAdded;
    }

    @Override
    public RaplaTreeNode createModel(Category rootCategory, Predicate<Category> pattern)
    {
        if ( pattern == null)
        {
            pattern = (cat)->true;
        }
        Map<Category, RaplaTreeNode> nodeMap = new HashMap<>();
        Category superCategory = null;
        {
            Category persistantSuperCategory = getQuery().getSuperCategory();
            if (persistantSuperCategory.equals(rootCategory))
            {
                superCategory = rootCategory;
            }
            if (superCategory == null)
            {
                superCategory = persistantSuperCategory;
            }
        }
        nodeMap.put(superCategory, newNamedNode(superCategory));
        //uniqueCategegories.add( rootCategory);
        Category userGroupsCategory = superCategory.getCategory(Permission.GROUP_CATEGORY_KEY);
        final Stream<Category> categoryStream = Stream.of(rootCategory.getCategories()).filter((cat) -> !cat.equals(userGroupsCategory));
        final Stream<Category> allChildren = categoryStream.flatMap(this::getAllChildren);
        LinkedList<Category> matchedCategories = allChildren.filter( pattern).collect(Collectors.toCollection(LinkedList::new));
        for (Category cat : matchedCategories)
        {
            RaplaTreeNode node = nodeMap.get(cat);
            if (node == null)
            {
                node = newNamedNode(cat);
                nodeMap.put(cat, node);
            }
        }

        LinkedList<Category> list = new LinkedList<>(matchedCategories);
        list.add(rootCategory);
        // build tree until all are connected to super category
        while (!list.isEmpty())
        {
            Category cat = list.pop();
            RaplaTreeNode node = nodeMap.get(cat);
            if (node == null)
            {
                node = newNamedNode(cat);
                nodeMap.put(cat, node);
            }
            if ( cat.equals( rootCategory))
            {
                continue;
            }
            Category parent = cat.getParent();
            if (parent != null)
            {
                RaplaTreeNode parentNode = nodeMap.get(parent);
                if (parentNode == null)
                {
                    parentNode = newNamedNode(parent);
                    nodeMap.put(parent, parentNode);
                    if ( !parent.equals(rootCategory))
                    {
                        list.push(parent);
                    }
                }
                parentNode.add(node);

            }

        }
        RaplaTreeNode rootNode = nodeMap.get(rootCategory);
        return rootNode;
    }

    private Stream<Category> getAllChildren(Category cat)
    {
        return Stream.concat(Stream.of( cat), Stream.of(cat.getCategories()).flatMap( this::getAllChildren));
    }

    private RaplaTreeNode newNode(Object element)
    {
        return treeItemFactory.createNode(element);
    }


    @Override
    public RaplaTreeNode newStringNode(String s) {
        return newNode( s);
    }

    @Override
    public RaplaTreeNode newNamedNode(Named named) {
        return newNode( named);
    }

    @Override
    public RaplaTreeNode newRootNode() {
        return newNode( "");
    }

    public static Object getUserObject(Object node)
    {
        if (node instanceof RaplaTreeNode)
            return ((RaplaTreeNode) node).getUserObject();
        return node;
    }

}
