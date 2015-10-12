package org.rapla.client.swing.internal.edit.fields;

import java.util.Collection;

public interface SetGetCollectionField<T> {
    void setValues(Collection<T> values);
    Collection<T> getValues();
}
