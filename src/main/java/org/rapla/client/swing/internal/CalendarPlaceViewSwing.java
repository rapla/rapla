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

package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.CalendarPlaceView;
import org.rapla.client.RaplaWidget;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@DefaultImplementation(of = CalendarPlaceView.class, context = InjectionContext.swing) final public class CalendarPlaceViewSwing
        implements CalendarPlaceView<Component>
{
    Presenter presenter;
    JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    final JToolBar minimized;
    final JToolBar templatePanel;
    final JPanel left;
    JPanel jp = new JPanel();
    GridBagConstraints c = new GridBagConstraints();
    RaplaResources i18n;

    @Inject public CalendarPlaceViewSwing(RaplaResources i18n)
    {
        this.i18n = i18n;

        left = new JPanel(new GridBagLayout());
        c.gridheight = 1;
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 0;
        c.weighty = 0;
        c.anchor = GridBagConstraints.EAST;
        final JButton max = new JButton();
        final JButton tree = new JButton();
        tree.setEnabled(false);
        minimized = new JToolBar(JToolBar.VERTICAL);
        minimized.setFloatable(false);
        minimized.add(max);
        minimized.add(tree);

        max.setIcon(UIManager.getDefaults().getIcon("InternalFrame.maximizeIcon"));
        tree.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.tree")));
        JButton min = new RaplaButton(RaplaButton.SMALL);
        ActionListener minmaxAction = e -> presenter.minmaxPressed();
        min.addActionListener(minmaxAction);
        max.addActionListener(minmaxAction);
        tree.addActionListener(minmaxAction);

        templatePanel = new JToolBar(JToolBar.VERTICAL);
        templatePanel.setFloatable(false);
        final JButton exitTemplateEdit = new JButton();
        //exitTemplateEdit.setIcon(raplaImages.getIconFromKey("icon.close"));
        exitTemplateEdit.setText(i18n.getString("close-template"));
        templatePanel.add(exitTemplateEdit);
        exitTemplateEdit.addActionListener(e -> presenter.closeTemplate());

        Icon icon = UIManager.getDefaults().getIcon("InternalFrame.minimizeIcon");
        min.setIcon(icon);
        //left.add(min, c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridy = 1;
        jp.setLayout(new BorderLayout());

        templatePanel.setVisible(false);
        jp.add(templatePanel, BorderLayout.WEST);
        JToolBar mintb = new JToolBar();
        mintb.setFloatable(false);
        // mintb.add( min);
        min.setAlignmentY(JButton.TOP);
        jp.add(min, BorderLayout.EAST);
        left.add(jp, c);
    }

    private Component savedViewsComponent;

    @Override public void addSavedViews(RaplaWidget<Component> savedViews)
    {
        savedViewsComponent = savedViews.getComponent();
        jp.add(savedViewsComponent, BorderLayout.CENTER);

    }

    private Component conflictsSummaryViewComponent;

    @Override public void addSummaryView(RaplaWidget<Component> conflictsSummaryView)
    {
        c.weighty = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.gridy = 4;
        c.anchor = GridBagConstraints.WEST;
        conflictsSummaryViewComponent = conflictsSummaryView.getComponent();
        left.add(conflictsSummaryViewComponent, c);
    }

    private Component conflictsViewComponent;

    @Override public void addConflictsView(RaplaWidget<Component> conflictsView)
    {
        c.weighty = 1.0;
        c.gridy = 3;
        conflictsViewComponent = conflictsView.getComponent();
        left.add(conflictsViewComponent, c);
    }

    @Override public void addCalendarView(RaplaWidget<Component> calendarView)
    {
        content.setRightComponent(calendarView.getComponent());
    }

    @Override public void addResourceSelectionView(RaplaWidget<Component> resourceSelectionView)
    {
        c.fill = GridBagConstraints.BOTH;
        c.gridy = 2;
        c.weightx = 1;
        c.weighty = 2.5;
        left.add(resourceSelectionView.getComponent(), c);
    }

    //    public void initForPlace(AbstractActivityController.Place place)
    //    {
    //        // keep current date   in mind
    //        final Date tmpDate = model.getSelectedDate();
    //        // keep in mind if current model had saved date
    //
    //        String tmpModelHasStoredCalenderDate = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
    //        if(tmpModelHasStoredCalenderDate == null)
    //            tmpModelHasStoredCalenderDate = "false";
    //        final String file = place.getInfo();
    //        model.load(file);
    //        closeFilter();
    //        // check if new model had stored date
    //        String newModelHasStoredCalenderDate = model.getOption(CalendarModel.SAVE_SELECTED_DATE);
    //        if(newModelHasStoredCalenderDate == null)
    //            newModelHasStoredCalenderDate = "false";
    //        if ("false".equals(newModelHasStoredCalenderDate)) {
    //
    //            if ("false".equals(tmpModelHasStoredCalenderDate))
    //            // if we are switching from a model with saved date to a model without date we reset to current date
    //            {
    //                model.setSelectedDate(tmpDate);
    //            } else {
    //                model.setSelectedDate(new Date());
    //            }
    //        }
    //
    //        savedViews.updateActions();
    //
    //        Entity preferences = getQuery().getPreferences( getUser());
    //        ModificationEventImpl modificationEvt = new ModificationEventImpl();
    //        // FIXME what is deserved here?
    //        //modificationEvt.addOperation( new UpdateResult.Change(preferences, preferences));
    //        modificationEvt.addChanged(preferences);
    //        resourceSelectionPresenter.dataChanged(modificationEvt);
    //        calendarContainer.update(modificationEvt);
    //        calendarContainer.getSelectedCalendar().scrollToStart();
    //    }

    @Override public void updateView(boolean showConflicts, boolean showSelection, boolean templateMode)
    {
        boolean showConflictsFull = showConflicts && !templateMode;
        boolean showConflictsMin = !showConflicts && !templateMode;
        boolean showTemplatePanel = templateMode;
        boolean showSavedViews = !templateMode;

        conflictsViewComponent.setVisible(showConflictsFull);
        conflictsSummaryViewComponent.setVisible(showConflictsMin);
        //        if ( templateMode)
        //        {
        //        	if ( content.getLeftComponent() != templatePanel)
        //        	{
        //        		content.setLeftComponent( templatePanel);
        //				content.setDividerSize(0);
        //        	}
        //        }
        //        else
        //        {
        if (showSelection)
        {
            savedViewsComponent.setVisible(showSavedViews);
            templatePanel.setVisible(showTemplatePanel);
            //resourceSelectionPresenter.getFilterButton().setVisible( !templateMode );
            if (content.getLeftComponent() != left)
            {
                content.setLeftComponent(left);
                content.setDividerSize(5);
                content.setDividerLocation(285);
            }
        }
        else if (content.getLeftComponent() != minimized)
        {
            content.setLeftComponent(minimized);
            content.setDividerSize(0);
        }
        //        }
    }

    public JComponent getComponent()
    {
        return content;
    }

    @Override public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

}
