package org.rapla.client;

import java.util.Collection;

import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.entities.domain.Allocatable;

public interface ResourceSelectionView
{

    interface Presenter
    {

        void selectionChanged(Collection<Allocatable> selected);
    }

    RaplaWidget provideContent();

    void setPresenter(Presenter presenter);

    void updateContent(Allocatable[] entries, Collection<Allocatable> selected);
}
