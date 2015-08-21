package org.rapla.client.gwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.gwtbootstrap3.client.ui.Navbar;
import org.gwtbootstrap3.client.ui.NavbarCollapse;
import org.gwtbootstrap3.client.ui.NavbarCollapseButton;
import org.gwtbootstrap3.client.ui.NavbarHeader;
import org.gwtbootstrap3.client.ui.NavbarText;
import org.gwtbootstrap3.client.ui.base.helper.StyleHelper;
import org.gwtbootstrap3.client.ui.constants.DeviceSize;
import org.gwtbootstrap3.client.ui.gwt.HTMLPanel;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.client.ApplicationView;
import org.rapla.client.base.CalendarPlugin;
import org.rapla.client.gwt.components.DropDownInputField;
import org.rapla.client.gwt.components.DropDownInputField.DropDownItem;
import org.rapla.client.gwt.components.DropDownInputField.DropDownValueChanged;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;

import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Float;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;

public class ApplicationViewImpl implements ApplicationView<IsWidget>
{

    private Presenter presenter;

    private final Div content = new Div();
    private final Div drawingContent = new Div();
    private final Div calendarSelection = new Div();

    @Inject
    public ApplicationViewImpl(ClientFacade facade, BundleManager bundleManager) throws RaplaException
    {
        final RootPanel root = RootPanel.get();
        final Div resources = new Div();
        final Div completeApplication = resources;
        completeApplication.getElement().getStyle().setDisplay(Display.TABLE);
        {
            final Div spanDiv = new Div();
            spanDiv.getElement().getStyle().setDisplay(Display.TABLE_CELL);
            completeApplication.add(spanDiv);
        }
        root.add(completeApplication);
        final Locale locale = bundleManager.getLocale();
        // left side resources navigation whenever medium is medium or large size
        {
            final Div resourcesDiv = new Div();
            resourcesDiv.setVisibleOn(DeviceSize.MD_LG);
            final Style style = resourcesDiv.getElement().getStyle();
            style.setFloat(Float.LEFT);
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
        final Div containerDiv = new Div();
        containerDiv.getElement().getStyle().setDisplay(Display.TABLE_CELL);
        containerDiv.getElement().getStyle().setWidth(100, Unit.PCT);
        completeApplication.add(containerDiv);
        // header navigation
        {
            final Div headerDiv = new Div();
            containerDiv.add(headerDiv);
            { // menu 
                final Navbar navbar = new Navbar();
                final NavbarCollapse menu = new NavbarCollapse();
                final String collapseableMenuId = "menuCollapse";
                menu.setId(collapseableMenuId);
                {
                    final NavbarText profile = new NavbarText();
                    profile.add(new HTMLPanel("Profil"));
                    menu.add(profile);
                }
                {
                    final NavbarText profile = new NavbarText();
                    profile.add(new HTMLPanel("Logout"));
                    menu.add(profile);
                }
                {
                    final NavbarText profile = new NavbarText();
                    profile.add(new HTMLPanel("ResourceTree"));
                    profile.setHiddenOn(DeviceSize.MD_LG);
                    menu.add(profile);
                }
                navbar.getElement().getStyle().setRight(10, Unit.PX);
                navbar.getElement().getStyle().setPosition(Position.ABSOLUTE);
                navbar.getElement().getStyle().setFloat(Float.RIGHT);
                final NavbarHeader navbarHeader = new NavbarHeader();
                final NavbarCollapseButton collapseButton = new NavbarCollapseButton();
                collapseButton.setDataTarget("#"+collapseableMenuId);
                navbarHeader.add(collapseButton);
                navbar.add(navbarHeader);
                navbar.add(menu);
                headerDiv.add(navbar);
            }
            {// calendar selection
                headerDiv.add(calendarSelection);
                calendarSelection.add(new HTML("calendar drop down"));
                final Div dateSelectionDiv = new Div();
                headerDiv.add(dateSelectionDiv);
                dateSelectionDiv.setVisibleOn(DeviceSize.SM_MD_LG);
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
        StyleHelper.setVisibleOn(dropDownInputField, DeviceSize.SM_MD_LG);
        calendarSelection.add(dropDownInputField);
    }

    @Override
    public void replaceContent(CalendarPlugin<IsWidget> contentProvider)
    {
        drawingContent.clear();
        drawingContent.add(contentProvider.provideContent());
    }
}