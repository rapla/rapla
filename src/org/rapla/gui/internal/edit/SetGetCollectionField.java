package org.rapla.gui.internal.edit;

import java.util.Collection;

public interface SetGetCollectionField<T> {
    void setValues(Collection<T> values);
    Collection<T> getValues();
}
