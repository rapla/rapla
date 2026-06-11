/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.internal.ResourceSelectionView;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.client.swing.internal.FilterEditButton;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.menu.MenuFactoryImpl;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.internal.view.ComplexTreeCellRenderer;
import org.rapla.client.swing.internal.view.DelegatingTreeSelectionModel;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.swing.internal.view.RaplaTreeToolTipRenderer;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.edit.search.NameSearchMatcher;
import org.rapla.client.sidebar.ResourceSelectionState;
import org.rapla.client.swing.toolkit.*;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.*;

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class ResourceSelectionViewSwing implements ResourceSelectionView
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceSelectionViewSwing.class);
    private final ClientFacade facade;
    protected JPanel content = new JPanel();
    public RaplaTree treeSelection = new RaplaTree();
    TableLayout tableLayout;
    protected JPanel buttonsPanel = new JPanel();

    Listener listener = new Listener();

    protected FilterEditButton filterEdit;
    protected final JTextField nameSearchField = new JTextField();
    protected final JLabel hiddenSelectionStatus = new JLabel(" ");
    protected final JPanel topPanel = new JPanel();
    private String nameSearchTerm = "";
    /** Search term the current tree model was generated with — a state refresh
     *  only needs the expensive full rebuild when this differs (search prunes
     *  nodes); pure selection changes reuse the existing model. */
    private String appliedSearchTerm = null;
    private int hiddenSelectedCount = 0;
    private ClassificationFilter[] lastFilter;
    private Collection<Object> lastSelectedObjects = Collections.emptyList();
    /** User objects rendered in the tree after the last generateTree() call.
     *  Drives the hidden-count: any selected object NOT in this set is hidden. */
    private java.util.Set<Object> visibleUserObjects = java.util.Collections.emptySet();
    private final TreeFactory treeFactory;
    private final RaplaResources i18n;
    private final RaplaMenuBarContainer menuBar;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final FilterEditButtonFactory filterEditButtonFactory;
    private boolean selectionFromProgram = false;
    private Presenter presenter;
    private final MenuFactory menuFactory;
    ComplexTreeCellRenderer treeCellRenderer;

    @Autowired
    public ResourceSelectionViewSwing(RaplaMenuBarContainer menuBar, RaplaResources i18n,
                                      TreeFactory treeFactory, MenuFactory menuFactory, InfoFactory infoFactory,
                                      DialogUiFactoryInterface dialogUiFactory, FilterEditButtonFactory filterEditButtonFactory,
                                      final ComplexTreeCellRenderer renderer,
                                      final ClientFacade facade
                                      ) throws RaplaInitializationException
    {

        this.treeCellRenderer = renderer;
        this.facade = facade;
        this.menuBar = menuBar;
        this.i18n = i18n;
        this.treeFactory = treeFactory;
        this.menuFactory = menuFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.filterEditButtonFactory = filterEditButtonFactory;
        /*double[][] sizes = new double[][] { { TableLayout.FILL }, { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.FILL } };
        tableLayout = new TableLayout(sizes);*/
        content.setLayout(new BorderLayout());

        content.add(treeSelection);
        // content.setPreferredSize(new Dimension(260,400));
        content.setBorder(BorderFactory.createRaisedBevelBorder());

        topPanel.setLayout(new BorderLayout());
        topPanel.add(buttonsPanel, BorderLayout.NORTH);
        topPanel.add(hiddenSelectionStatus, BorderLayout.SOUTH);
        content.add(topPanel, BorderLayout.NORTH);

        buttonsPanel.setLayout(new BorderLayout());

        nameSearchField.setToolTipText(i18n.getString("search"));
        buttonsPanel.add(nameSearchField, BorderLayout.CENTER);
        nameSearchField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { onSearchTermChanged(); }
            public void removeUpdate(DocumentEvent e) { onSearchTermChanged(); }
            public void changedUpdate(DocumentEvent e) { onSearchTermChanged(); }
        });

        hiddenSelectionStatus.setFont(hiddenSelectionStatus.getFont().deriveFont(hiddenSelectionStatus.getFont().getSize2D() - 1f));
        hiddenSelectionStatus.setForeground(java.awt.Color.GRAY);
        hiddenSelectionStatus.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        hiddenSelectionStatus.setVisible(false);

        treeSelection.setToolTipRenderer(new RaplaTreeToolTipRenderer(infoFactory));
        treeSelection.setMultiSelect(true);
        treeSelection.getTree().setSelectionModel(   new DelegatingTreeSelectionModel(this::isSelectable));

        treeSelection.getTree().setCellRenderer(renderer);

        treeSelection.addChangeListener(listener);
        treeSelection.addPopupListener(listener);
        treeSelection.addDoubleclickListeners(listener);
        treeSelection.getTree().addFocusListener(listener);
        treeSelection.addChangeListener((evt) ->
        {
            if(selectionFromProgram)
            {
                return;
            }
            getPresenter().treeSelectionChanged();
        });
        javax.swing.ToolTipManager.sharedInstance().registerComponent(treeSelection.getTree());
    }

    public boolean isSelectable(TreePath treePath)
    {
        Object lastPathComponent = treePath.getLastPathComponent();
        Object object = TreeFactoryImpl.getUserObject(lastPathComponent);
        return !(object instanceof TreeFactoryImpl.Categorization);
    }
    

    private final ResourceSelectionState.Listener stateListener = s -> refreshFromState();

    @Override
    public void setPresenter(Presenter presenter) {
        if (this.presenter != null && this.presenter.getState() != null) {
            this.presenter.getState().removeListener(stateListener);
        }
        this.presenter = presenter;
        if (presenter != null && presenter.getState() != null) {
            presenter.getState().addListener(stateListener);
            refreshFromState();
        }
    }

    protected Presenter getPresenter() {
        return presenter;
    }

    private void refreshFromState()
    {
        if (presenter == null) return;
        ResourceSelectionState s = presenter.getState();
        this.nameSearchTerm = s.searchTerm();
        if (!nameSearchField.getText().equals(s.searchTerm()))
        {
            nameSearchField.setText(s.searchTerm());
        }
        // Canonical selection lives in the state — not lastSelectedObjects,
        // which only updates on the dataChanged path and is stale during a
        // click→state→listener cycle.
        Collection<Object> canonical = new java.util.ArrayList<>(s.selected());
        this.lastSelectedObjects = canonical;
        if (lastFilter != null)
        {
            try
            {
                if (!nameSearchTerm.equals(appliedSearchTerm))
                {
                    // search term changed — the model must be regenerated (pruning)
                    updateTree(lastFilter, canonical);
                }
                else
                {
                    // pure selection change — reuse the tree model (a full rebuild is
                    // O(resource count)) and don't expand collapsed branches, so the
                    // tree stays exactly where the user clicked
                    try
                    {
                        selectionFromProgram = true;
                        treeSelection.select(canonical, false);
                    }
                    finally
                    {
                        selectionFromProgram = false;
                    }
                }
            }
            catch (RaplaException ex)
            {
                LOGGER.error("Failed to refresh from state", ex);
            }
        }
        updateHiddenSelectionStatus();
    }
    
    @Override
    public boolean hasFocus()
    {
        return treeSelection.getTree().hasFocus();
    }
    
    @Override
    public void update(ClassificationFilter[] filter, ClassifiableFilter model, Collection<Object> selectedObjects)
    {
        SwingUtilities.invokeLater(() -> {
            try
            {
                if (filterEdit == null)
                {
                    filterEdit = filterEditButtonFactory.create(model, true, listener);
                    buttonsPanel.add(filterEdit.getButton(), BorderLayout.EAST);
                }
                //boolean defaultFilter = model.isDefaultResourceTypes();
                //filterEdit.setFiltered( ! defaultFilter );
                this.lastFilter = filter;
                this.lastSelectedObjects = selectedObjects == null ? Collections.emptyList() : selectedObjects;
                updateTree(filter, selectedObjects);
                updateSelection(selectedObjects);
                updateHiddenSelectionStatus();
            }
            catch (RaplaException e)
            {
                PopupContext popupContext = new SwingPopupContext(getComponent(), null);
                dialogUiFactory.showException(e, popupContext);
            }
        });
    }

    private void onSearchTermChanged()
    {
        if (presenter == null) return;
        // Write through canonical state; the state listener handles tree
        // rebuild + status-line refresh.
        presenter.getState().setSearchTerm(nameSearchField.getText());
    }

    private void updateHiddenSelectionStatus()
    {
        if (nameSearchTerm == null || nameSearchTerm.isBlank()
                || lastSelectedObjects == null || lastSelectedObjects.isEmpty())
        {
            hiddenSelectedCount = 0;
            hiddenSelectionStatus.setVisible(false);
            return;
        }
        // Hidden = selected user objects that are NOT in the post-prune tree.
        // This catches allocatables whose name didn't match AND parent folders
        // (DynamicType / categorization / etc.) that got pruned because they
        // ended up empty after the search.
        int hidden = 0;
        for (Object obj : lastSelectedObjects)
        {
            if (!visibleUserObjects.contains(obj))
            {
                hidden++;
            }
        }
        hiddenSelectedCount = hidden;
        if (hidden == 0)
        {
            hiddenSelectionStatus.setVisible(false);
        }
        else
        {
            hiddenSelectionStatus.setText(i18n.format("search.hidden_status", hidden));
            hiddenSelectionStatus.setVisible(true);
        }
    }

    public RaplaArrowButton getFilterButton()
    {
        return filterEdit.getButton();
    }

    public RaplaTree getTreeSelection()
    {
        return treeSelection;
    }

    final protected TreeFactory getTreeFactory()
    {
        return treeFactory;
    }

    boolean treeListenersEnabled = true;

    /*
     * (non-Javadoc)
     * 
     * @see org.rapla.client.swing.gui.internal.view.ITreeFactory#createClassifiableModel(org.rapla.entities.dynamictype.Classifiable[], org.rapla.entities.dynamictype.DynamicType[])
     */
    protected void updateTree(final ClassificationFilter[] filter, final Collection<Object> selectedObjects) throws RaplaException
    {

        treeSelection.getTree().setRootVisible(false);
        final JTree tree = treeSelection.getTree();
        tree.setShowsRootHandles(true);
        DefaultTreeModel treeModel = generateTree(filter);
        appliedSearchTerm = nameSearchTerm;
        try
        {
            treeListenersEnabled = false;
            treeSelection.exchangeTreeModel(treeModel);
            updateSelection(selectedObjects);
        }
        catch (Exception ex)
        {
            LOGGER.error(ex.getMessage(), ex);
        }
        finally
        {
            treeListenersEnabled = true;
        }

    }
    
    

    protected DefaultTreeModel generateTree(ClassificationFilter[] filter) throws RaplaException
    {
        final TreeFactory treeFactory =  getTreeFactory();
        final TreeFactory.AllocatableNodes allocatableNodes = treeFactory.createAllocatableModel(filter);
        boolean filtered = allocatableNodes.filtered;
        if (nameSearchTerm != null && !nameSearchTerm.isBlank())
        {
            String[] preparedTerms = NameSearchMatcher.prepare(nameSearchTerm);
            if (preparedTerms.length > 0)
            {
                pruneByName(allocatableNodes.allocatableNode, preparedTerms, i18n.getLocale());
                filtered = true;
            }
        }
        filterEdit.setFiltered( filtered );
        treeCellRenderer.setFiltered( filtered );
        final RaplaTreeNode raplaTreeNode = treeFactory.newRootNode();
        raplaTreeNode.add( allocatableNodes.allocatableNode);

        RaplaTreeNode usersNode = treeFactory.newUsersNode();
        raplaTreeNode.add(usersNode);

        java.util.Set<Object> visible = new java.util.HashSet<>();
        collectVisibleUserObjects(raplaTreeNode, visible);
        this.visibleUserObjects = visible;

        DefaultTreeModel treeModel = new RaplaSwingTreeModel(raplaTreeNode);
        return treeModel;
    }

    private static void collectVisibleUserObjects(RaplaTreeNode node, java.util.Set<Object> visible)
    {
        Object userObj = node.getUserObject();
        if (userObj != null)
        {
            visible.add(userObj);
        }
        for (int i = 0; i < node.getChildCount(); i++)
        {
            collectVisibleUserObjects(node.getChild(i), visible);
        }
    }

    private static void pruneByName(RaplaTreeNode parent, String[] preparedTerms, java.util.Locale locale)
    {
        java.util.List<RaplaTreeNode> toRemove = new java.util.ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++)
        {
            RaplaTreeNode child = parent.getChild(i);
            pruneByName(child, preparedTerms, locale);
            Object userObj = child.getUserObject();
            if (userObj instanceof Allocatable)
            {
                String name = ((Allocatable) userObj).getName(locale);
                if (!NameSearchMatcher.matchesPrepared(name, preparedTerms))
                {
                    toRemove.add(child);
                }
            }
            else if (userObj != null && child.getChildCount() == 0)
            {
                toRemove.add(child);
            }
        }
        for (RaplaTreeNode r : toRemove)
        {
            parent.remove(r);
        }
    }

    protected void updateSelection(Collection<Object> selectedObjects)
    {
        try
        {
            selectionFromProgram = true;
            treeSelection.select(selectedObjects);
        }
        finally
        {
            selectionFromProgram = false;
        }
    }

    public JComponent getComponent()
    {
        return content;
    }

    public void showMenu(RaplaPopupMenu menu, SelectionMenuContext swingMenuContext)
    {
        final SwingPopupContext popupContext = (SwingPopupContext)swingMenuContext.getPopupContext();
        Component component = popupContext.getParent();
        final Point p = popupContext.getPoint();
        menu.show(component, p.x, p.y);
    }
    
    @Override
    public void closeFilterButton()
    {
        if (getFilterButton().isOpen())
        {
            getFilterButton().doClick();
        }
    }
    
    class Listener implements PopupListener, ChangeListener, ActionListener, FocusListener
    {

        public void showPopup(PopupEvent evt)
        {
            Point p = evt.getPoint();
            Object selectedObject = evt.getSelectedObject();
            Collection<?> selectedElements = treeSelection.getSelectedElements();
            JComponent component = (JComponent) evt.getSource();
            PopupContext popupContext = new SwingPopupContext(component, p);
            final SelectionMenuContext menuContext = new SelectionMenuContext(selectedObject, popupContext);
            menuContext.setSelectedObjects(selectedElements);

            showTreePopup(popupContext, selectedObject, menuContext);
        }

        public void showTreePopup(PopupContext popupContext, Object selectedObject, SelectionMenuContext selectionMenuContext)
        {
            try
            {
                boolean addNewReservationMenu = selectedObject instanceof Allocatable || selectedObject instanceof DynamicType;
                MenuItemFactory menuItemFactory;
                RaplaPopupMenu menu = new RaplaPopupMenu(selectionMenuContext.getPopupContext());
                RaplaMenu newMenu = new RaplaMenu("new");
                newMenu.setText(i18n.getString("new"));
                // TODO extract interface
                ((MenuFactoryImpl) menuFactory).addNew(newMenu, selectionMenuContext, null, addNewReservationMenu);
                menuFactory.addObjectMenu(menu, selectionMenuContext, "EDIT_BEGIN");
                newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
                menu.insertAfterId(newMenu, "EDIT_BEGIN");
                showMenu(menu, selectionMenuContext);
            }
            catch (RaplaException ex)
            {
                dialogUiFactory.showException(ex, popupContext);
            }
        }



        public void actionPerformed(ActionEvent evt)
        {
            Object focusedObject = evt.getSource();
            getPresenter().selectResource(focusedObject);
        }

        public void stateChanged(ChangeEvent evt)
        {
            if (!treeListenersEnabled)
            {
                return;
            }
            try
            {
                Object source = evt.getSource();
                ClassifiableFilterEdit filterUI = filterEdit.getFilterUI();
                if (filterUI != null && source == filterUI)
                {
                    final ClassificationFilter[] filters = filterUI.getFilters();
                    getPresenter().updateFilters(filters);
                }
                else if (source == treeSelection)
                {
                    updateChange();
                }
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
            }
        }

        public void focusGained(FocusEvent e)
        {
            getPresenter().mouseOverResourceSelection();
        }

        public void focusLost(FocusEvent e)
        {
        }

    }

    @Override public void updateMenu(Collection<?> list, Object focusedObject) throws RaplaException
    {
        RaplaMenu editMenu = menuBar.getEditMenu();
        RaplaMenu newMenu = menuBar.getNewMenu();
        editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
        newMenu.removeAll();
        PopupContext popupContext = new SwingPopupContext(getComponent(), null);
        SelectionMenuContext menuContext = new SelectionMenuContext(focusedObject,popupContext);
        menuContext.setSelectedObjects(list);
        if (hasFocus())
        {
            menuFactory.addObjectMenu(editMenu, menuContext, "EDIT_BEGIN");
        }
        ((MenuFactoryImpl) menuFactory).addNew(newMenu, menuContext, null, true);
        newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
    }

    public void setFiltered(boolean filtered) {
        filterEdit.setFiltered( filtered );
    }

    public void updateChange()
    {
        final Collection<Object> elements = treeSelection.getSelectedElements();
        getPresenter().updateSelectedObjects(elements);
    }
}
