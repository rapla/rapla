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
import org.rapla.client.MenuContext;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.internal.ResourceSelectionView;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.FilterEditButton;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.internal.MenuFactoryImpl;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.internal.view.TreeFactoryImpl;
import org.rapla.client.swing.toolkit.PopupEvent;
import org.rapla.client.swing.toolkit.PopupListener;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Collection;

@DefaultImplementation(context=InjectionContext.swing, of=ResourceSelectionView.class)
public class ResourceSelectionViewSwing implements ResourceSelectionView
{
    protected JPanel content = new JPanel();
    public RaplaTree treeSelection = new RaplaTree();
    TableLayout tableLayout;
    protected JPanel buttonsPanel = new JPanel();

    Listener listener = new Listener();

    protected FilterEditButton filterEdit;
    private final TreeFactory treeFactory;
    private final Logger logger;
    private final RaplaResources i18n;
    private final RaplaMenuBarContainer menuBar;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final FilterEditButtonFactory filterEditButtonFactory;
    private boolean selectionFromProgram = false;
    private Presenter presenter;
    private final MenuFactory menuFactory;

    @Inject
    public ResourceSelectionViewSwing(RaplaMenuBarContainer menuBar, RaplaResources i18n, Logger logger,
                                      TreeFactory treeFactory, MenuFactory menuFactory, InfoFactory infoFactory, RaplaImages raplaImages,
                                      DialogUiFactoryInterface dialogUiFactory, FilterEditButtonFactory filterEditButtonFactory) throws RaplaInitializationException
    {

        this.menuBar = menuBar;
        this.i18n = i18n;
        this.logger = logger;
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

        content.add(buttonsPanel, BorderLayout.NORTH);

        buttonsPanel.setLayout(new BorderLayout());

        treeSelection.setToolTipRenderer(getTreeFactory().createTreeToolTipRenderer());
        treeSelection.setMultiSelect(true);
        treeSelection.getTree().setSelectionModel(((TreeFactoryImpl) getTreeFactory()).createComplexTreeSelectionModel());
        final TreeCellRenderer renderer = getTreeFactory().createRenderer();
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
    

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
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
                updateTree(filter, selectedObjects);
                updateSelection(selectedObjects);
            }
            catch (RaplaException e)
            {
                PopupContext popupContext = new SwingPopupContext(getComponent(), null);
                dialogUiFactory.showException(e, popupContext);
            }
        });
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
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);
        tree.setDropTarget(new DropTarget(tree, TransferHandler.MOVE, new DropTargetAdapter()
        {
            private final Rectangle _raCueLine = new Rectangle();
            private final Color _colorCueLine = Color.blue;
            private TreePath lastPath = null;

            @Override
            public void dragOver(DropTargetDragEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                TreePath sourcePath = selectionPath.getParentPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                Graphics2D g2 = (Graphics2D) tree.getGraphics();
                final Point dropLocation = dtde.getLocation();
                TreePath path = tree.getClosestPathForLocation(dropLocation.x, dropLocation.y);
                if(isDropAllowed(sourcePath, path, selectedNode))
                {
                    if (lastPath == null || !lastPath.equals(path))
                    {
                        if(lastPath != null)
                        {
                            drawLine(g2, lastPath, Color.white);
                        }
                        lastPath = path;
                        drawLine(g2, path, _colorCueLine);
                    }
                }
                else
                {
                    if(lastPath != null)
                    {
                        drawLine(g2, lastPath, Color.white);
                    }
                    lastPath = null;
                }
            }

            private void drawLine(Graphics2D g2, TreePath path, Color color)
            {
                Rectangle raPath = tree.getPathBounds(path);
                _raCueLine.setRect(0, raPath.y, tree.getWidth(), 2);
                g2.setColor(color);
                g2.fill(_raCueLine);
            }

            @Override
            public void dragEnter(DropTargetDragEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                if (!(selectedNode.getUserObject() instanceof Category))
                {
                    dtde.rejectDrag();
                    return;
                }
                dtde.acceptDrag(DnDConstants.ACTION_MOVE);
            }

            @Override
            public void drop(DropTargetDropEvent dtde)
            {
                {
                    TreePath selectionPath = tree.getSelectionPath();
                    TreePath sourcePath = selectionPath.getParentPath();
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                    Point dropLocation = dtde.getLocation();
                    TreePath targetPath = tree.getClosestPathForLocation(dropLocation.x, dropLocation.y);
                    if (isDropAllowed(sourcePath, targetPath, selectedNode))
                    {
                        DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode) targetPath.getLastPathComponent();
                        final Category categoryToMove = (Category) selectedNode.getUserObject();
                        final Category targetCategory = (Category) targetParentNode.getUserObject();
                        getPresenter().moveCategory(categoryToMove, targetCategory).thenRun(() ->{
                            dtde.dropComplete(true);
                            updateTree(filter, selectedObjects);
                        }).exceptionally( (ex)->
                        {
                            dtde.rejectDrop();
                            dialogUiFactory.showException(ex, null);
                            dtde.dropComplete(false);
                            logger.error("Error performing drag and drop operation: "+ex.getMessage(), ex);
                            return Promise.VOID;
                        });
                    }
                    else
                    {
                        dtde.rejectDrop();
                        dtde.dropComplete(false);
                    }
                }

            }

            private boolean isDropAllowed(TreePath sourcePath, TreePath targetPath, DefaultMutableTreeNode selectedNode)
            {
                if (selectedNode.getUserObject() instanceof Category
                        && ((DefaultMutableTreeNode) targetPath.getLastPathComponent()).getUserObject() instanceof Category)
                {
                    Category targetCategory = (Category) ((DefaultMutableTreeNode) targetPath.getLastPathComponent()).getUserObject();
                    if(targetCategory.getId().equals(Category.SUPER_CATEGORY_REF.getId()))
                    {
                        return false;
                    }
                    return true;
                }
                return false;
            }

        }));
        DefaultTreeModel treeModel = generateTree(filter);
        try
        {
            treeListenersEnabled = false;
            treeSelection.exchangeTreeModel(treeModel);
            updateSelection(selectedObjects);
        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(), ex);
        }
        finally
        {
            treeListenersEnabled = true;
        }

    }
    
    

    protected DefaultTreeModel generateTree(ClassificationFilter[] filter) throws RaplaException
    {
        final TreeFactoryImpl treeFactoryImpl = (TreeFactoryImpl) getTreeFactory();
        DefaultTreeModel treeModel = treeFactoryImpl.createModel(filter);
        return treeModel;
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

    public void showMenu(RaplaPopupMenu menu, SwingMenuContext swingMenuContext)
    {
        JComponent component = swingMenuContext.getComponent();
        final Point p = swingMenuContext.getPoint();
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
            PopupContext popupContext = new SwingPopupContext(null, p);
            final SwingMenuContext menuContext = new SwingMenuContext(selectedObject, popupContext, component, p);
            menuContext.setSelectedObjects(selectedElements);

            showTreePopup(popupContext, selectedObject, menuContext);
        }

        public void showTreePopup(PopupContext popupContext, Object selectedObject, MenuContext menuContext)
        {
            try
            {
                boolean addNewReservationMenu = selectedObject instanceof Allocatable || selectedObject instanceof DynamicType;
                RaplaPopupMenu menu = new RaplaPopupMenu();
                RaplaMenu newMenu = new RaplaMenu("new");
                newMenu.setText(i18n.getString("new"));
                // TODO extract interface
                SwingMenuContext swingMenuContext = ((SwingMenuContext) menuContext);
                ((MenuFactoryImpl) menuFactory).addNew(newMenu, swingMenuContext, null, addNewReservationMenu);
                menuFactory.addObjectMenu(menu, swingMenuContext, "EDIT_BEGIN");
                newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
                menu.insertAfterId(newMenu, "EDIT_BEGIN");
                showMenu(menu, swingMenuContext);
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
        SwingMenuContext menuContext = new SwingMenuContext(focusedObject);
        menuContext.setSelectedObjects(list);
        if (hasFocus())
        {
            menuFactory.addObjectMenu(editMenu, menuContext, "EDIT_BEGIN");
        }
        ((MenuFactoryImpl) menuFactory).addNew(newMenu, menuContext, null, true);
        newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
    }

    public void updateChange() throws RaplaException
    {
        final Collection<Object> elements = treeSelection.getSelectedElements();
        getPresenter().updateSelectedObjects(elements);
    }
}
