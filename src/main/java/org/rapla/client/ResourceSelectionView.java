package org.rapla.client;

import java.util.Collection;

import org.rapla.entities.domain.Allocatable;

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
