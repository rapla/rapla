package org.rapla.client;

import org.rapla.entities.domain.Allocatable;

import java.util.Collection;

public interface ResourceSelectionView<W>
{

    interface Presenter
    {

        void selectionChanged(Collection<Allocatable> selected);
    }

    W provideContent();

    void setPresenter(Presenter presenter);

    void updateContent(Allocatable[] entries, Collection<Allocatable> selected);
}
