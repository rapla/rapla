package org.rapla.client.gwt.view;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

public class RaplaPopups
{

    public static void showWarning(String title, String text)
    {
        final PopupPanel warningPopup = new PopupPanel(true, true);
        final VerticalPanel layout = new VerticalPanel();
        layout.add(new HTML(title));
        layout.add(new HTML(text));
        warningPopup.add(layout);
        warningPopup.center();
        warningPopup.show();
    }

    public static RootPanel getPopupPanel()
    {
        return RootPanel.get("raplaPopup");
    }

    public static PopupPanel createNewPopupPanel()
    {
        final WindowBox raplaPopup = new WindowBox(false, false);
        raplaPopup.setResizable(true);
        raplaPopup.setAutoHideOnHistoryEventsEnabled(false);
        raplaPopup.addStyleName("raplaPopup");
        return raplaPopup;
    }

}
