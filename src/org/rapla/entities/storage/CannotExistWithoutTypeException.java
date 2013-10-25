package org.rapla.entities.storage;

import org.rapla.framework.RaplaException;

public class CannotExistWithoutTypeException extends RaplaException {

    public CannotExistWithoutTypeException() {
        super("This object cannot exist without a dynamictype. Type cannot be removed.");
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

}
