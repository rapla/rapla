package org.rapla.client.internal.admin.client.swing;

import io.reactivex.functions.BiFunction;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.admin.client.CategoryMenuContext;
import org.rapla.client.internal.admin.client.DynamicTypeMenuContext;
import org.rapla.client.internal.admin.client.TypeCategoryView;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.swing.internal.view.RaplaTreeNode;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.Category;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;


@DefaultImplementation(of=TypeCategoryView.class,context = InjectionContext.swing)
public class SwingTypeCategoryView extends RaplaGUIComponent implements
		 RaplaWidget ,TypeCategoryView {

	// definition of different panels
	JPanel mainPanel;
	JPanel centerPanel;
	JComboBox cbView;
	View view = View.RESOURCE_TYPE;

	// hierarchical
	RaplaTree selectionTreeTable;
	final Logger logger;


	RaplaButton okButton;
	JLabel viewLabel = new JLabel();
	private final MenuFactory menuFactory;

	// text field for filter
	JTextField filterTextField;

    private final TreeFactory treeFactory;

    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade raplaFacade;
    private final TreeCellRenderer treeCellRenderer;


	@Inject
	public SwingTypeCategoryView(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, MenuFactory menuFactory, TreeFactory treeFactory, DialogUiFactoryInterface dialogUiFactory, TreeCellRenderer treeCellRenderer) throws
			RaplaInitializationException {
		super(facade, i18n, raplaLocale, logger);
		this.logger = logger;
		raplaFacade = facade.getRaplaFacade();
		this.menuFactory = menuFactory;
		this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;
		this.treeCellRenderer = treeCellRenderer;

		// creation of different panels
		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		centerPanel = new JPanel();
		centerPanel.setLayout(new BorderLayout());
		// rows = 8 columns = 4
		JPanel buttonPanel = new JPanel();
		// add northPanel and centerPanel to the mainPanel - using BoarderLayout
		mainPanel.add(centerPanel, BorderLayout.CENTER);
		okButton = new RaplaButton(i18n.getString("close"), RaplaButton.DEFAULT);
		buttonPanel.add(okButton);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		// creation of the ComboBox to choose one of the views
		// adding the ComboBox to the northPanel
		@SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(new String[] {getString("resource_types"), getString("person_types"), getString("reservation_type"),getString("categories")});
		cbView = jComboBox;
		cbView.addItemListener((evt)->itemStateChanged());

		mainPanel.add(cbView, BorderLayout.NORTH);


		// creation of the filter-text field and adding this object (this) as
		// DocumentListener
		// note: DocumentListener registers all changes
		filterTextField = new JTextField();

        filterTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateView();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateView();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {

            }
        });
		centerPanel.add(filterTextField,BorderLayout.NORTH);
		mainPanel.setPreferredSize(new Dimension(500,400));
		// creation of the tree
		selectionTreeTable = new RaplaTree();
		selectionTreeTable.setMultiSelect( true);
		selectionTreeTable.getTree().setCellRenderer(treeCellRenderer);
		// including the tree in ScrollPane and adding this to the GUI
		centerPanel.add(selectionTreeTable.getTree(), BorderLayout.CENTER);
		selectionTreeTable.addPopupListener(evt -> {
            Point p = evt.getPoint();
            Object selectedObject = evt.getSelectedObject();
            Collection<?> selectedElements = selectionTreeTable.getSelectedElements();
			SwingPopupContext popupContext = new SwingPopupContext(getComponent(), p);
			final  SelectionMenuContext menuContext;
			final String classificationType = getClassificationType(view);
			if ( classificationType != null)
			{
				 menuContext= new DynamicTypeMenuContext(selectedObject, popupContext).setClassificationType(classificationType);
			}
			else
			{
				final Category superCategory = getQuery().getSuperCategory();
				menuContext= new CategoryMenuContext(selectedObject, popupContext, superCategory);
			}
			menuContext.setSelectedObjects(selectedElements);
            showTreePopup(popupContext, menuContext);
        });
	}

	private void showTreePopup(SwingPopupContext popupContext,  SelectionMenuContext swingMenuContext)
	{
		RaplaResources i18n = getI18n();
		try
		{
			RaplaPopupMenu menu = new RaplaPopupMenu(popupContext);
			RaplaMenu newMenu = new RaplaMenu("new");
			newMenu.setText(i18n.getString("new"));
			// TODO extract interface
			menuFactory.addNewMenu(newMenu, swingMenuContext, null);
			menuFactory.addObjectMenu(menu, swingMenuContext, "EDIT_BEGIN");
			newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
			menu.insertAfterId(newMenu, "EDIT_BEGIN");
            Component component = popupContext.getParent();
			final Point p = popupContext.getPoint();
			menu.show(component, p.x, p.y);
		}
		catch (RaplaException ex)
		{
			dialogUiFactory.showException(ex, popupContext);
		}
	}

	public JComponent getComponent() {
		return mainPanel;
	}

	@Override
	public Promise<RaplaWidget> init(BiFunction<Object, Object, Promise<Void>> moveFunction, Runnable closeCmd) {
		// set default values
		if (moveFunction != null) {
			selectionTreeTable.addDragAndDrop(moveFunction);
		}
		okButton.addActionListener((evt)->closeCmd.run());
		view = View.RESOURCE_TYPE;
		filterTextField.setText("");
		loadView();
		return new ResolvedPromise<>(this);
	}

	// change of the ComboBox -> new view has been chosen
	private void itemStateChanged() {
		JComboBox cbViewSelection = cbView;
		// definition of the internal variable for storing the view
		final int selectedIndex = cbViewSelection.getSelectedIndex();
		if (selectedIndex == 0)
			view = View.RESOURCE_TYPE;
		else if (selectedIndex == 1)
			view = View.PERSON_TYPE;
		else if (selectedIndex == 2)
			view = View.RESERVATION_TYPE;
		else if (selectedIndex == 3)
			view = View.CATEGORY;
		// build of the screen according to the view
		loadView();
	}


	// this method builds the current screen (GUI) according to the variable
	// "view"
	private void loadView() {
		// reset the filter
		filterTextField.setText("");
		updateView();
	}

	// search categories with specified pattern in categoryname (only child of
	// rootCategory)
	private List<Category> searchCategoryName(Category rootCategory,
			String pattern) {
		List<Category> categories = new ArrayList<>();
		Locale locale = getLocale();
		Category userGroupsCategory = null;
		try {
			userGroupsCategory = getQuery().getUserGroupsCategory();
		} catch (RaplaException e) {
			logger.error(e.getMessage(),e);
		}
		// loop all child of rootCategory
		for (Category category : rootCategory.getCategories()) {
			if ( userGroupsCategory.equals( category))
			{
				continue;
			}
			// is category a leaf?
			if (category.getCategories().length == 0) {
				// does the specified pattern matches with the the categoryname?
				final String name = category.getName(locale);
				if (Pattern.matches(pattern.toLowerCase(locale),
						name.toLowerCase(locale)))
					categories.add(category);
			} else {
				// get all child with a matching name (recursive)
				categories.addAll(searchCategoryName(category, pattern));
			}
		}
		Collections.sort(categories);
		return categories;
	}


	// search users with specified pattern in username
	private List<DynamicType> searchTypes(String pattern, String classificationType) throws RaplaException {
		List<DynamicType> types = new ArrayList<>();
		Locale locale = getLocale();
		// get all types
		for (DynamicType user : getQuery().getDynamicTypes(classificationType)) {
			// does the specified pattern matches with the the username?
			if (Pattern.matches(pattern.toLowerCase(locale), user.getName(locale)
					.toLowerCase(locale)))
				types.add(user);

		}
		Collections.sort(types, new NamedComparator<>(locale));
		return types;
	}

	@Override
	public void updateView() {
		// add regular expressions to filter pattern
			String pattern = ".*" + filterTextField.getText() + ".*";
			try {
				selectionTreeTable.getTree().clearSelection();
				TreeModel selectionModel = null;
				String valueClassificationTypeResource  = getClassificationType( view);
				switch (view) {
					case CATEGORY: {
						// search all categories for the specified pattern and add
						// them to the list
						Category rootCategory = raplaFacade.getSuperCategory();
						List<Category> categoriesToMatch = searchCategoryName(rootCategory, pattern);
						selectionModel = new RaplaSwingTreeModel(treeFactory.createModel(categoriesToMatch, false));
						viewLabel.setText(getI18n().getString("users"));
						break;
					}
					case RESOURCE_TYPE: {
						final String title = getI18n().getString("resource_types");
						selectionModel = updateTypes(pattern, valueClassificationTypeResource, title);
						break;
					}
					case PERSON_TYPE: {
						final String title = getI18n().getString("person_types");
						selectionModel = updateTypes(pattern, valueClassificationTypeResource, title);
						break;
					}
					case RESERVATION_TYPE: {
						final String title = getI18n().getString("reservation_type");
						selectionModel = updateTypes(pattern, valueClassificationTypeResource, title);
						break;
					}
				}
				selectionTreeTable.getTree().setModel(selectionModel);
			} catch (RaplaException ex) {
			    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
			}
	}

	public String getClassificationType( View view)
	{
		switch (view) {
			case RESOURCE_TYPE: {
				return DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE;
			}
			case PERSON_TYPE: {
				return DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;
			}
			case RESERVATION_TYPE: {
				return DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION;
			}
		}
		return null;
	}

	@NotNull
	public TreeModel updateTypes(String pattern, String valueClassificationTypeResource, String title) throws RaplaException {
		TreeModel selectionModel;DynamicType[] types  = searchTypes(pattern, valueClassificationTypeResource).toArray( DynamicType.DYNAMICTYPE_ARRAY);
		RaplaTreeNode root = treeFactory.newStringNode("");
		viewLabel.setText(title);
		for (DynamicType user:types)
        {
            root.add(treeFactory.newNamedNode(user));
        }
		selectionModel = new RaplaSwingTreeModel(root);
		// change the name of the root node in "user"
		((DefaultMutableTreeNode) (selectionModel.getRoot())).setUserObject(title);
		return selectionModel;
	}


	public enum View {
		RESOURCE_TYPE, PERSON_TYPE, RESERVATION_TYPE, CATEGORY
	}


}
