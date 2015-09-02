package org.rapla.client.gwt;

import java.util.Collection;
import java.util.Locale;

import javax.inject.Inject;

import org.rapla.client.ResourceSelectionView;
import org.rapla.client.ResourceSelectionView.Presenter;
import org.rapla.client.base.AbstractView;
import org.rapla.client.gwt.components.TreeComponent;
import org.rapla.client.gwt.components.TreeComponent.SelectionChangeHandler;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Allocatable;

import com.google.gwt.user.client.ui.IsWidget;

public class ResourceSelectionViewImpl extends AbstractView<Presenter>implements ResourceSelectionView<IsWidget>
{
    private final TreeComponent tree;

    @Inject
    public ResourceSelectionViewImpl(BundleManager bundleManager)
    {
        Locale locale = bundleManager.getLocale();
        tree = new TreeComponent(locale, new SelectionChangeHandler()
        {
            @Override
            public void selectionChanged(Collection<Allocatable> selected)
            {
                getPresenter().selectionChanged(selected);
            }
        });
    }
    
    
    @Override
    public IsWidget provideContent()
    {
        return tree;
    }

    @Override
    public void updateContent(Allocatable[] entries, Collection<Allocatable> selected)
    {
        tree.updateData(entries, selected);
    }

}
