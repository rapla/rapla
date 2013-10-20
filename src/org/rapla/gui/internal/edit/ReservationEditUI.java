/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit;

import java.util.ArrayList;

import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/
class ReservationEditUI  extends AbstractEditUI<Reservation>  {
    ClassificationField<Reservation> classificationField;
    public ReservationEditUI(RaplaContext sm) {
        super(sm);
        ArrayList<EditField> fields = new ArrayList<EditField>();
        classificationField = new ClassificationField<Reservation>(sm);
        fields.add( classificationField);
        setFields(fields);
    }

    public void mapToObjects() throws RaplaException {
        classificationField.mapTo( objectList);
        if ( getName(objectList).length() == 0)
            throw new RaplaException(getString("error.no_name"));

    }

    protected void mapFromObjects() throws RaplaException {
        classificationField.mapFrom( objectList);
    }


}
