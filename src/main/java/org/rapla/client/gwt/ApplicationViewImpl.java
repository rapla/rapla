package org.rapla.client.gwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.gwtbootstrap3.client.ui.AnchorListItem;
import org.gwtbootstrap3.client.ui.Navbar;
import org.gwtbootstrap3.client.ui.NavbarCollapse;
import org.gwtbootstrap3.client.ui.NavbarCollapseButton;
import org.gwtbootstrap3.client.ui.NavbarHeader;
import org.gwtbootstrap3.client.ui.NavbarNav;
import org.gwtbootstrap3.client.ui.NavbarText;
import org.gwtbootstrap3.client.ui.constants.DeviceSize;
import org.gwtbootstrap3.client.ui.constants.IconPosition;
import org.gwtbootstrap3.client.ui.constants.IconType;
import org.gwtbootstrap3.client.ui.constants.NavbarPosition;
import org.gwtbootstrap3.client.ui.constants.Pull;
import org.gwtbootstrap3.client.ui.html.Div;
import org.gwtbootstrap3.client.ui.html.Text;
import org.rapla.RaplaResources;
import org.rapla.client.ApplicationView;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.gwt.components.DropDownInputField;
import org.rapla.client.gwt.components.DropDownInputField.DropDownItem;
import org.rapla.client.gwt.components.DropDownInputField.DropDownValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;

public class ApplicationViewImpl implements ApplicationView<IsWidget>
{
    private static final String MENU_ACTION = "RAPLA_MENU_ACTION";
    private Presenter presenter;

    private final Div content = new Div();
    private final Div drawingContent = new Div();
    private final Div calendarSelection = new Div();

    @Inject
    public ApplicationViewImpl(ClientFacade facade, BundleManager bundleManager, RaplaResources i18n) throws RaplaException
    {
        final RootPanel root = RootPanel.get();
        { // menu 
            final Navbar navbar = new Navbar();
            final NavbarNav navbarNav = new NavbarNav();
            final NavbarCollapse menu = new NavbarCollapse();
            menu.addDomHandler(new ClickHandler( )
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    Element target = event.getNativeEvent().getEventTarget().cast();
                    final String action = findAction(target);
                    Window.alert("Clicked on " + action);
                }

                private String findAction(Element target)
                {
                    final String action = target.getAttribute(MENU_ACTION);
                    if(action!=null && !action.isEmpty()){
                        return action;
                    }
                    if(target == menu.getElement() )
                    {
                        return null;
                    }
                    return findAction(target.getParentElement());
                }
            }, ClickEvent.getType());
            final String collapseableMenuId = "menuCollapse";
            menu.setId(collapseableMenuId);
            {
                final AnchorListItem menuEntry = new AnchorListItem();
                menuEntry.setText(i18n.getString("modify-preferences"));
                menuEntry.getElement().setAttribute(MENU_ACTION, "preferences");
                menuEntry.setIcon(IconType.GEAR);
                menuEntry.setIconPosition(IconPosition.LEFT);
                menuEntry.setIconSpin(true);
                navbarNav.add(menuEntry);
            }
            {
                final AnchorListItem menuEntry = new AnchorListItem();
                menuEntry.setText("ResourceTree");
                menuEntry.getElement().setAttribute(MENU_ACTION, "resources");
                menuEntry.setIcon(IconType.TREE);
                menuEntry.setHiddenOn(DeviceSize.MD_LG);
                navbarNav.add(menuEntry);
            }
            {
                final AnchorListItem menuEntry = new AnchorListItem();
                menuEntry.setText("Date selection");
                menuEntry.getElement().setAttribute(MENU_ACTION, "date selection");
                menuEntry.setIcon(IconType.CALENDAR);
                menuEntry.setVisibleOn(DeviceSize.XS);
                navbarNav.add(menuEntry);
            }
            {
                final AnchorListItem menuEntry = new AnchorListItem();
                menuEntry.getElement().setAttribute(MENU_ACTION, "exit");
                menuEntry.setIcon(IconType.CLOSE);
                menuEntry.setText(i18n.getString("exit"));
                navbarNav.add(menuEntry);
            }
            {
                final String loginUser = facade.getUser().getName(bundleManager.getLocale());
                final NavbarText user = new NavbarText();
                user.setPull(Pull.RIGHT);
                user.setMarginRight(25);
                user.add(new Text(loginUser));
                menu.add(user);
            }
            menu.add(navbarNav);
            final NavbarHeader navbarHeader = new NavbarHeader();
            final NavbarCollapseButton collapseButton = new NavbarCollapseButton();
            collapseButton.setDataTarget("#" + collapseableMenuId);
            navbarHeader.add(collapseButton);
            navbar.add(navbarHeader);
            navbar.add(menu);
            navbar.setPosition(NavbarPosition.FIXED_TOP);
            root.add(navbar);
            final Div spacerDiv = new Div();
            spacerDiv.getElement().getStyle().setWidth(100, Unit.PCT);
            spacerDiv.getElement().getStyle().setHeight(50, Unit.PX);
            root.add(spacerDiv);
        }

        final Div resources = new Div();
        final Div completeApplication = resources;
        completeApplication.getElement().getStyle().setDisplay(Display.TABLE);
        root.add(completeApplication);
        final Locale locale = bundleManager.getLocale();
        // left side resources navigation whenever medium is medium or large size
        {
            final Div resourcesDiv = new Div();
            resourcesDiv.setVisibleOn(DeviceSize.MD_LG);
            final Style style = resourcesDiv.getElement().getStyle();
            style.setWidth(300, Unit.PX);
            style.setOverflow(Overflow.AUTO);
            completeApplication.add(resourcesDiv);
            final Tree resourcesTree = new Tree();
            resourcesDiv.add(resourcesTree);
            final Allocatable[] allocatables = facade.getAllocatables();
            for (Allocatable allocatable : allocatables)
            {
                final String name = allocatable.getName(locale);
                final TreeItem treeItem = new TreeItem(new HTML(name));
                resourcesTree.addItem(treeItem);
            }
        }
        {// Empty div for span over
            final Div spanDiv = new Div();
            spanDiv.getElement().getStyle().setDisplay(Display.TABLE_CELL);
            completeApplication.add(spanDiv);
        }
        final Div containerDiv = new Div();
        containerDiv.getElement().getStyle().setDisplay(Display.TABLE_CELL);
        containerDiv.getElement().getStyle().setWidth(100, Unit.PCT);
        completeApplication.add(containerDiv);
        // header navigation
        {
            final Div headerDiv = new Div();
            containerDiv.add(headerDiv);
            {// calendar selection
                headerDiv.add(calendarSelection);
                calendarSelection.add(new HTML("calendar drop down"));
                calendarSelection.getElement().getStyle().setMarginTop(20, Unit.PX);
                final Div dateSelectionDiv = new Div();
                headerDiv.add(dateSelectionDiv);
            }
        }
        content.setVisibleOn(DeviceSize.XS_SM_MD_LG);
        containerDiv.add(drawingContent);
    }

    public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

    public void show(List<String> viewNames, final List<String> calendarNames)
    {
        calendarSelection.clear();
        Collection<DropDownItem> dropDownEntries = new ArrayList<DropDownItem>();
        int i = 0;
        for (String calendarName : calendarNames)
        {
            final DropDownItem item = new DropDownItem(calendarName, i + "");
            i++;
            dropDownEntries.add(item);
        }

        DropDownInputField dropDownInputField = new DropDownInputField("calendar", new DropDownValueChanged()
        {
            @Override
            public void valueChanged(String newValue)
            {
                presenter.changeCalendar(calendarNames.get(Integer.parseInt(newValue)));
            }
        }, dropDownEntries, "0");
        calendarSelection.add(dropDownInputField);
    }

    @Override
    public void replaceContent(CalendarPlugin<IsWidget> contentProvider)
    {
        drawingContent.clear();
        drawingContent.add(contentProvider.provideContent());
    }
}