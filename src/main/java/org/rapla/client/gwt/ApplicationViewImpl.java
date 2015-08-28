package org.rapla.client.gwt;

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
import org.rapla.framework.RaplaException;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RootPanel;

public class ApplicationViewImpl implements ApplicationView<IsWidget>
{
    private static final String MENU_ACTION = "RAPLA_MENU_ACTION";
    private final RaplaResources i18n;
    private final NavbarCollapse menu = new NavbarCollapse();
    private final NavbarNav navbarNav = new NavbarNav();
    private final Div applicationContent = new Div();
    private Presenter presenter;

    @Inject
    public ApplicationViewImpl(final RaplaResources i18n) throws RaplaException
    {
        this.i18n = i18n;
        final RootPanel root = RootPanel.get();
        { // menu 
            final Navbar navbar = new Navbar();
            menu.addDomHandler(new ClickHandler()
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    Element target = event.getNativeEvent().getEventTarget().cast();
                    final String action = findAction(target);
                    menu.hide();
                }

                private String findAction(Element target)
                {
                    final String action = target.getAttribute(MENU_ACTION);
                    if (action != null && !action.isEmpty())
                    {
                        return action;
                    }
                    if (target == menu.getElement())
                    {
                        return null;
                    }
                    return findAction(target.getParentElement());
                }
            }, ClickEvent.getType());
            final String collapseableMenuId = "menuCollapse";
            menu.setId(collapseableMenuId);
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
        root.add(applicationContent);
    }

    @Override
    public void setLoggedInUser(final String loggedInUser)
    {
        final NavbarText user = new NavbarText();
        user.setPull(Pull.RIGHT);
        user.setMarginRight(25);
        user.add(new Text(loggedInUser));
        menu.add(user);

    }

    @Override
    public void updateMenu()
    {
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
    }

    public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

    @Override
    public void updateContent(IsWidget w)
    {
        this.applicationContent.clear();
        this.applicationContent.add(w);
    }

}