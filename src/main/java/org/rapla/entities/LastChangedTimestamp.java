package org.rapla.entities;

import jsinterop.annotations.JsType;

import java.util.Date;

@JsType
public interface LastChangedTimestamp {

    /** returns the date of last change of the object. */
    Date getLastChanged();

}