package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.facade.ClassifiableFilter;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;

import org.springframework.beans.factory.annotation.Autowired;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class FilterEditButton extends RaplaGUIComponent
{
    protected RaplaArrowButton filterButton;
    JWindow popup;
    ClassifiableFilterEdit ui;
    private boolean popupWasVisibleAtPress;

    private FilterEditButton(final ClientFacade facade, final RaplaResources i18n, final RaplaLocale raplaLocale,
            final TreeFactory treeFactory, final ClassifiableFilter filter, final ChangeListener listener,
            final DateFieldFactory dateFieldFactory, final BooleanFieldFactory booleanFieldFactory, final DialogUiFactoryInterface dialogUiFactory,
            final boolean isResourceSelection, final TextFieldFactory textFieldFactory, final LongFieldFactory longFieldFactory)
    {
        super(facade, i18n, raplaLocale);
        filterButton = new RaplaArrowButton('v');
        filterButton.setText(getString("filter"));
        filterButton.setSize(80,18);
        final PopupContext popupContext = dialogUiFactory.createPopupContext(null);
        // Snapshot popup visibility at mouse-press time. On Windows the
        // popup's windowLostFocus can dispatch before the button's action
        // listener and dismiss the popup first — the action listener then
        // sees popup == null and would reopen. The press-time snapshot
        // captures the true "was open" state regardless of event order.
        filterButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent ev) {
                popupWasVisibleAtPress = (popup != null);
            }
        });
        filterButton.addActionListener(e -> {

            if ( popup != null)
            {
                dismissPopup();
                return;
            }
            if (popupWasVisibleAtPress)
            {
                popupWasVisibleAtPress = false;
                return;
            }
            try {
                if ( ui != null && listener != null)
                {
                    ui.removeChangeListener( listener);
                }
                ui = new ClassifiableFilterEdit( facade, i18n, raplaLocale, treeFactory, isResourceSelection,  dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
                if ( listener != null)
                {
                    ui.addChangeListener(listener);
                }
                ui.setFilter( filter);
                final Point locationOnScreen = filterButton.getLocationOnScreen();
                // Place the popup immediately to the RIGHT of the button,
                // aligned with the button's top edge. On Linux/WSLg the
                // popup window's invisible hit-test margin leaks past its
                // painted left edge back over the button — so anchoring
                // beside the button (instead of below it) leaves most of
                // the button clickable. Only the rightmost few pixels of
                // the button may sit under the popup's input region.
                final int y = locationOnScreen.y;
                final int x = locationOnScreen.x + filterButton.getWidth();
                if ( popup == null)
                {
                    Component ownerWindow = DialogUI.getOwnerWindow(filterButton);
                    if ( ownerWindow instanceof Frame)
                    {
                        popup = new JWindow((Frame)ownerWindow);
                    }
                    else if ( ownerWindow instanceof Dialog)
                    {
                        popup = new JWindow((Dialog)ownerWindow);
                    }
                    if ( popup != null)
                    {
                        installDismissHandlers(popup);
                    }
                }
                JComponent content = ui.getComponent();
                popup.setContentPane(content );
                popup.setSize( content.getPreferredSize());
                popup.setLocation( x, y);
                //.getSharedInstance().getPopup( filterButton, ui.getComponent(), x, y);
                popup.setVisible(true);
                // Heavyweight JWindow can be stacked below its owner by some
                // Linux compositors (WSLg in particular). toFront() restacks
                // and avoids the "click hits invisible popup over button" trap.
                popup.toFront();
                filterButton.setChar('^');
            } catch (Exception ex) {
                dialogUiFactory.showException(ex, popupContext);
            }
        });
        
    }
    
    /**
     * Hide and forget the popup. Idempotent.
     */
    private void dismissPopup()
    {
        if (popup != null)
        {
            popup.setVisible(false);
            popup = null;
            filterButton.setChar('v');
        }
    }

    /**
     * Wire the dismiss-on-focus-loss and Escape behaviour. Independent of
     * platform z-order bugs — gives users a way to close the popup even
     * when the filter button is unclickable (e.g. WSLg compositor's
     * input-region bug where the JWindow's hit-test rectangle extends
     * past its painted content and swallows clicks on the button below).
     */
    private void installDismissHandlers(final JWindow popup)
    {
        popup.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                // Defer: if the focus loss was caused by a click on the
                // filter button, the button's action listener should run
                // first and toggle the popup. Deferring with invokeLater
                // means by the time this fires, popup is already null and
                // we no-op. For any other focus loss (click outside, app
                // switch), the popup is still alive and we dismiss it.
                SwingUtilities.invokeLater(FilterEditButton.this::dismissPopup);
            }
        });
        JComponent root = popup.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "filter.dismiss");
        root.getActionMap().put("filter.dismiss", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ae) { dismissPopup(); }
        });
    }

    public ClassifiableFilterEdit getFilterUI()
    {
    	return ui;
    }
    
    public RaplaArrowButton getButton()
    {
        return filterButton;
    }

    public void refresh()
    {
        filterButton.refreshChar();
    }

    public void setFiltered(boolean filtered) {
        //filterButton.setBackground(filtered ? Color.red: null);
        final ImageIcon icon = RaplaImages.getIcon("/org/rapla/gui/images/eclipse-icons/filter_small.gif");
        filterButton.setAdditionalIcon(filtered ? icon: null);
        filterButton.refreshChar();
    }

    @org.springframework.stereotype.Service
    public static class FilterEditButtonFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final TreeFactory treeFactory;

        private final DateFieldFactory dateFieldFactory;
        private final BooleanFieldFactory booleanFieldFactory;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final TextFieldFactory textFieldFactory;
        private final LongFieldFactory longFieldFactory;

        @Autowired
        public FilterEditButtonFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, TreeFactory treeFactory,
                  DateFieldFactory dateFieldFactory,
                BooleanFieldFactory booleanFieldFactory, DialogUiFactoryInterface dialogUiFactory, TextFieldFactory textFieldFactory,
                LongFieldFactory longFieldFactory)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.treeFactory = treeFactory;
            this.dateFieldFactory = dateFieldFactory;
            this.booleanFieldFactory = booleanFieldFactory;
            this.dialogUiFactory = dialogUiFactory;
            this.textFieldFactory = textFieldFactory;
            this.longFieldFactory = longFieldFactory;
        }

        public FilterEditButton create(ClassifiableFilter filter,boolean isResourceSelection,ChangeListener listener)
        {
            return new FilterEditButton(facade, i18n, raplaLocale, treeFactory, filter, listener,  dateFieldFactory, booleanFieldFactory,
                    dialogUiFactory, isResourceSelection, textFieldFactory, longFieldFactory);
        }
    }
    
}