package org.rapla.plugin.rightsreport.client.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;


public class RaplaRightsReport extends RaplaGUIComponent implements
		ItemListener, TreeSelectionListener,
		DocumentListener {
	final Category rootCategory;

	// definition of different panels
	JPanel mainPanel;
	JPanel northPanel;
	JPanel centerPanel;
	JComboBox cbView;
	View view = View.USERGROUPS;

	// hierarchical tree
	RaplaTree selectionTreeTable;

	// list for the presentation of the assigned elements
	DefaultListModel assignedElementsListModel;
	JList assignedElementsList;
	JScrollPane assignedElementsScrollPane;

	// text field for filter
	JTextField filterTextField;
	Collection<Object> notAllList = new HashSet<Object>();

    private final TreeFactory treeFactory;

    private final DialogUiFactoryInterface dialogUiFactory;

	@Inject
	public RaplaRightsReport(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, DialogUiFactoryInterface dialogUiFactory) throws
			RaplaInitializationException {
		super(facade, i18n, raplaLocale, logger);
		try
		{
			rootCategory = facade.getRaplaFacade().getUserGroupsCategory();
		}
		catch (RaplaException e)
		{
			throw new RaplaInitializationException(e);
		}
		this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;

		// creation of different panels
		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		northPanel = new JPanel();
		northPanel.setLayout(new GridLayout(0, 1));
		centerPanel = new JPanel();
		centerPanel.setLayout(new GridLayout(0, 2));

		// add northPanel and centerPanel to the mainPanel - using BoarderLayout
		mainPanel.add(northPanel, BorderLayout.NORTH);
		mainPanel.add(centerPanel, BorderLayout.CENTER);

		// creation of the ComboBox to choose one of the views
		// adding the ComboBox to the northPanel
		@SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(new String[] {getString("groups"), getString("users")});
		cbView = jComboBox;
		cbView.addItemListener(this);
		northPanel.add(cbView);

		// creation of the filter-text field and adding this object (this) as
		// DocumentListener
		// note: DocumentListener registers all changes
		filterTextField = new JTextField();
		filterTextField.getDocument().addDocumentListener(this);
		northPanel.add(filterTextField);

		// creation of the tree
		selectionTreeTable = new RaplaTree();
		selectionTreeTable.setMultiSelect( true);
		selectionTreeTable.getTree().setCellRenderer(treeFactory.createRenderer());
		selectionTreeTable.getTree().addTreeSelectionListener(this);
		// including the tree in ScrollPane and adding this to the GUI
		centerPanel.add(selectionTreeTable);

		// creation of the list for the assigned elements
		assignedElementsList = new JList();
		assignedElementsListModel = new DefaultListModel();
		setRenderer();
		// including the list in ScrollPaneListe and adding this to the GUI
		assignedElementsScrollPane = new JScrollPane(assignedElementsList);
		centerPanel.add(assignedElementsScrollPane);

	}

	@SuppressWarnings("unchecked")
	private void setRenderer() {
		assignedElementsList.setCellRenderer(new CategoryListCellRenderer());
	}

	public JComponent getComponent() {
		return mainPanel;
	}

	public void show() {
		// set default values
		view = View.USERGROUPS;
		filterTextField.setText("");
		loadView();
	}

	// change of the ComboBox -> new view has been chosen
	public void itemStateChanged(ItemEvent e) {
		JComboBox cbViewSelection = (JComboBox) e.getSource();

		// definition of the internal variable for storing the view
		if (cbViewSelection.getSelectedIndex() == 0)
			view = View.USERGROUPS;
		else if (cbViewSelection.getSelectedIndex() == 1)
			view = View.USERS;

		// build of the screen according to the view
		loadView();
	}

	// selection of one or more elements in the tree
	@SuppressWarnings("unchecked")
	public void valueChanged(TreeSelectionEvent event) {
		try
		{
			assignedElementsListModel.clear();
			List<Object> selectedElements = selectionTreeTable.getSelectedElements();
			
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
		filter();
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
				if (Pattern.matches(pattern.toLowerCase(locale),
						category.getName(locale).toLowerCase(locale)))
					categories.add(category);
			} else {
				// get all child with a matching name (recursive)
				categories.addAll(searchCategoryName(category, pattern));
			}
		}
		sortCategories(categories);
		return categories;
	}

	@SuppressWarnings("unchecked")
	public void sortCategories(List<Category> categories) {
		Collections.sort(categories);
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
		sort(users);
		return users;
	}

	@SuppressWarnings("unchecked")
	public void sort(List<User> users) {
		Collections.sort(users);
	}
	
	private void filter() {

			// add regular expressions to filter pattern
			String pattern = ".*" + filterTextField.getText() + ".*";
			try {
				selectionTreeTable.getTree().clearSelection();
				TreeModel selectionModel = null;
				switch (view) {
				case USERGROUPS:
					// search all categories for the specified pattern and add
					// them to the list
					List<Category> categoriesToMatch  = searchCategoryName(rootCategory,pattern);
					selectionModel = treeFactory.createModel(categoriesToMatch, false);
					break;
				case USERS:
					User[] users  =searchUserName(pattern).toArray( User.USER_ARRAY);
					selectionModel = treeFactory.createModelFlat(	users);
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

	public void changedUpdate(DocumentEvent e) {
	}

	public void insertUpdate(DocumentEvent e) {
		// filter elements
		filter();
	}

	public void removeUpdate(DocumentEvent e) {
		// filter elements
		filter();
	}

	public enum View {
		USERS, USERGROUPS
	}

	// ListCellRenderer to display the right name of categories based on locale
	class CategoryListCellRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			Object userObject = value;
			if (value != null && value instanceof Category) {
				// if element in cell is a category: set the name of the
				// category
				value = ((Category) value).getName(getLocale());
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
