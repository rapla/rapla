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
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class FilterEditButton extends RaplaGUIComponent
{
    protected RaplaArrowButton filterButton;
    JWindow popup;
    ClassifiableFilterEdit ui;

    private FilterEditButton(final ClientFacade facade, final RaplaResources i18n, final RaplaLocale raplaLocale, final Logger logger,
            final TreeFactory treeFactory, final ClassifiableFilter filter, final ChangeListener listener,
            final DateFieldFactory dateFieldFactory, final BooleanFieldFactory booleanFieldFactory, final DialogUiFactoryInterface dialogUiFactory,
            final boolean isResourceSelection, final TextFieldFactory textFieldFactory, final LongFieldFactory longFieldFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        filterButton = new RaplaArrowButton('v');
        filterButton.setText(getString("filter"));
        filterButton.setSize(80,18);
        final PopupContext popupContext = dialogUiFactory.createPopupContext(null);
        filterButton.addActionListener(e -> {

            if ( popup != null)
            {
                popup.setVisible(false);
                popup= null;
                filterButton.setChar('v');
                return;
            }
            try {
                if ( ui != null && listener != null)
                {
                    ui.removeChangeListener( listener);
                }
                ui = new ClassifiableFilterEdit( facade, i18n, raplaLocale, logger, treeFactory, isResourceSelection,  dateFieldFactory, dialogUiFactory, booleanFieldFactory, textFieldFactory, longFieldFactory);
                if ( listener != null)
                {
                    ui.addChangeListener(listener);
                }
                ui.setFilter( filter);
                final Point locationOnScreen = filterButton.getLocationOnScreen();
                final int y = locationOnScreen.y + 18;
                final int x = locationOnScreen.x;
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
                }
                JComponent content = ui.getComponent();
                popup.setContentPane(content );
                popup.setSize( content.getPreferredSize());
                popup.setLocation( x, y);
                //.getSharedInstance().getPopup( filterButton, ui.getComponent(), x, y);
                popup.setVisible(true);
                filterButton.setChar('^');
            } catch (Exception ex) {
                dialogUiFactory.showException(ex, popupContext);
            }
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

    @Singleton
    public static class FilterEditButtonFactory
    {
        private final ClientFacade facade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Logger logger;
        private final TreeFactory treeFactory;

        private final DateFieldFactory dateFieldFactory;
        private final BooleanFieldFactory booleanFieldFactory;
        private final DialogUiFactoryInterface dialogUiFactory;
        private final TextFieldFactory textFieldFactory;
        private final LongFieldFactory longFieldFactory;

        @Inject
        public FilterEditButtonFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory,
                  DateFieldFactory dateFieldFactory,
                BooleanFieldFactory booleanFieldFactory, DialogUiFactoryInterface dialogUiFactory, TextFieldFactory textFieldFactory,
                LongFieldFactory longFieldFactory)
        {
            super();
            this.facade = facade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.logger = logger;
            this.treeFactory = treeFactory;
            this.dateFieldFactory = dateFieldFactory;
            this.booleanFieldFactory = booleanFieldFactory;
            this.dialogUiFactory = dialogUiFactory;
            this.textFieldFactory = textFieldFactory;
            this.longFieldFactory = longFieldFactory;
        }

        public FilterEditButton create(ClassifiableFilter filter,boolean isResourceSelection,ChangeListener listener)
        {
            return new FilterEditButton(facade, i18n, raplaLocale, logger, treeFactory, filter, listener,  dateFieldFactory, booleanFieldFactory,
                    dialogUiFactory, isResourceSelection, textFieldFactory, longFieldFactory);
        }
    }
    
}