package org.rapla.plugin.rightsreport.client.swing;

import io.reactivex.functions.BiFunction;
import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.menu.MenuFactoryImpl;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.*;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.plugin.rightsreport.client.AdminUserUserGroupsView;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;


@DefaultImplementation(of=AdminUserUserGroupsView.class,context = InjectionContext.swing)
public class SwingUserGroupsView extends RaplaGUIComponent implements
		 RaplaWidget ,AdminUserUserGroupsView {

	// definition of different panels
	JPanel mainPanel;
	JPanel northPanel;
	JPanel centerPanel;
	JComboBox cbView;
	View view = View.USERGROUPS;

	// hierarchical
	RaplaTree selectionTreeTable;
	final Logger logger;

	// list for the presentation of the assigned elements
	DefaultListModel assignedElementsListModel;
	JList assignedElementsList;
	JScrollPane assignedElementsScrollPane;

	RaplaButton okButton;
	JLabel viewLabel = new JLabel();
	private final MenuFactory menuFactory;

	// text field for filter
	JTextField filterTextField;
	Collection<Object> notAllList = new HashSet<Object>();

    private final TreeFactory treeFactory;

    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade raplaFacade;


	@Inject
	public SwingUserGroupsView(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, MenuFactory menuFactory, TreeFactory treeFactory, DialogUiFactoryInterface dialogUiFactory) throws
			RaplaInitializationException {
		super(facade, i18n, raplaLocale, logger);
		this.logger = logger;
		raplaFacade = facade.getRaplaFacade();
		this.menuFactory = menuFactory;
		this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;

		// creation of different panels
		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		northPanel = new JPanel();
		northPanel.setLayout(new GridLayout(0, 1));
		centerPanel = new JPanel();
		double pre = TableLayout.PREFERRED;
		double fill = TableLayout.FILL;
		// rows = 8 columns = 4
		centerPanel.setLayout( new TableLayout(new double[][] {{0.5, 15, 0.5}, {pre,fill}}));
		JPanel buttonPanel = new JPanel();
		// add northPanel and centerPanel to the mainPanel - using BoarderLayout
		mainPanel.add(northPanel, BorderLayout.NORTH);
		mainPanel.add(centerPanel, BorderLayout.CENTER);
		okButton = new RaplaButton(i18n.getString("close"), RaplaButton.DEFAULT);
		buttonPanel.add(okButton);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		// creation of the ComboBox to choose one of the views
		// adding the ComboBox to the northPanel
		@SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(new String[] {getString("groups"), getString("users")});
		cbView = jComboBox;
		cbView.addItemListener((evt)->itemStateChanged());
		northPanel.add(cbView);


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
		centerPanel.add(filterTextField,"0,0");
		centerPanel.add(viewLabel, "2,0");
		// creation of the tree
		selectionTreeTable = new RaplaTree();
		selectionTreeTable.setMultiSelect( true);
		selectionTreeTable.getTree().setCellRenderer(treeFactory.createRenderer());
		selectionTreeTable.getTree().addTreeSelectionListener((e) ->selectionChanged());
		// including the tree in ScrollPane and adding this to the GUI
		centerPanel.add(selectionTreeTable, "0,1");
		selectionTreeTable.addPopupListener(new PopupListener() {
			@Override
			public void showPopup(PopupEvent evt) {
				Point p = evt.getPoint();
				Object selectedObject = evt.getSelectedObject();
				Collection<?> selectedElements = selectionTreeTable.getSelectedElements();
                SwingPopupContext popupContext = new SwingPopupContext(getComponent(), p);
				final SelectionMenuContext menuContext = new SelectionMenuContext(selectedObject, popupContext);
				menuContext.setSelectedObjects(selectedElements);
				showTreePopup(popupContext, menuContext);
			}
		});

		// creation of the list for the assigned elements
		assignedElementsList = new JList();
		assignedElementsListModel = new DefaultListModel();
		assignedElementsList.setCellRenderer(new CategoryListCellRenderer());
		// including the list in ScrollPaneListe and adding this to the GUI
		assignedElementsScrollPane = new JScrollPane(assignedElementsList);
		centerPanel.add(assignedElementsScrollPane,"2,1");

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
			menuFactory.addNew(newMenu, swingMenuContext, null);
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
		view = View.USERGROUPS;
		filterTextField.setText("");
		loadView();
		return new ResolvedPromise<>(this);
	}

	// change of the ComboBox -> new view has been chosen
	private void itemStateChanged() {
		JComboBox cbViewSelection = cbView;
		// definition of the internal variable for storing the view
		if (cbViewSelection.getSelectedIndex() == 0)
			view = View.USERGROUPS;
		else if (cbViewSelection.getSelectedIndex() == 1)
			view = View.USERS;

		// build of the screen according to the view
		loadView();
	}

	private void selectionChanged() {
		try
		{
			List<Object> selectedElements = selectionTreeTable.getSelectedElements();
			assignedElementsListModel.clear();
			Set<Object> commonElements = new TreeSet<Object>();
			notAllList.clear();
			User[] allUsers = getQuery().getUsers();
			boolean first = true;
			for ( Object sel:selectedElements)
			{
				List<Object> elementsToAdd = new ArrayList<Object>();
				if ( sel instanceof User)
				{
					User user= (User) sel;
					for ( Category cat:user.getGroupList())
					{
						elementsToAdd.add( cat);
					}
				}
				if ( sel instanceof Category)
				{
					Category category = (Category) sel;
					for (User user : allUsers) {
						if (user.belongsTo(category))
							elementsToAdd.add(user);
					}
				}
				for ( Object toAdd: elementsToAdd)
				{
					if (!first && !commonElements.contains(toAdd))
					{
						notAllList.add( toAdd);
					}
					commonElements.add( toAdd);
				}
				first = false;
			}
			for (Object element : commonElements) {
				assignedElementsListModel.addElement(element);
			}
			assignedElementsList.setModel(assignedElementsListModel);
		} catch (RaplaException ex) {
		    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
		}
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
		List<Category> categories = new ArrayList<Category>();
		Locale locale = getLocale();

		// loop all child of rootCategory
		for (Category category : rootCategory.getCategories()) {
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
	private List<User> searchUserName(String pattern) throws RaplaException {
		List<User> users = new ArrayList<User>();
		Locale locale = getLocale();
		// get all users
		for (User user : getQuery().getUsers()) {
			// does the specified pattern matches with the the username?
			if (Pattern.matches(pattern.toLowerCase(locale), user.getUsername()
					.toLowerCase(locale)))
				users.add(user);

		}
		Collections.sort(users, (u1,u2)-> u1.getUsername().compareTo( u2.getUsername()));
		return users;
	}

	@Override
	public void updateView() {
		// add regular expressions to filter pattern
			String pattern = ".*" + filterTextField.getText() + ".*";
			try {
				selectionTreeTable.getTree().clearSelection();
				TreeModel selectionModel = null;
				switch (view) {
				case USERGROUPS:
					// search all categories for the specified pattern and add
					// them to the list
					Category rootCategory = raplaFacade.getUserGroupsCategory();
					List<Category> categoriesToMatch  = searchCategoryName(rootCategory,pattern);
					selectionModel = treeFactory.createModel(categoriesToMatch, false);
					viewLabel.setText(getI18n().getString("users"));
					break;
				case USERS:
					User[] users  =searchUserName(pattern).toArray( User.USER_ARRAY);

					DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
                    viewLabel.setText(getI18n().getString("user-groups"));
					for (User user:users)
					{
						root.add(treeFactory.newNamedNode(user));
					}
					selectionModel = new DefaultTreeModel(root);
					// change the name of the root node in "user"
					((DefaultMutableTreeNode) (selectionModel.getRoot())).setUserObject(getString("users"));
					break;
				}
				selectionTreeTable.getTree().setModel(selectionModel);
				assignedElementsListModel.clear();
			} catch (RaplaException ex) {
			    dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
			}
	}


	public enum View {
		USERS, USERGROUPS
	}
	class CategoryListCellRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(JList list, Object value,
													  int index, boolean isSelected, boolean cellHasFocus) {
			Object userObject = value;
			if (value != null && value instanceof Named) {
				// if element in cell is a category: set the name of the
				// category
				value = ((Named) value).getName(getLocale());
			}
			Component component = super.getListCellRendererComponent(list, value, index, isSelected,
					cellHasFocus);

			Font f;
			if (notAllList.contains( userObject))
			{
				f =component.getFont().deriveFont(Font.ITALIC);
			}
			else
			{
				f =component.getFont().deriveFont(Font.BOLD);
			}
			component.setFont(f);
			return component;
		}
	}

}
