/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.swing.internal.edit.fields;

import org.rapla.RaplaResources;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class AllocatableListField extends ListField<Allocatable>
{
    DynamicType dynamicTypeConstraint;

    public AllocatableListField(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, DynamicType dynamicTypeConstraint)
            throws RaplaException
    {
        super(clientFacade, i18n, raplaLocale, logger, true);
        this.dynamicTypeConstraint = dynamicTypeConstraint;
        ClassificationFilter filter = dynamicTypeConstraint.newClassificationFilter();
        ClassificationFilter[] filters = new ClassificationFilter[] { filter };
        Allocatable[] allocatables = raplaFacade.getAllocatablesWithFilter(filters);
        Set<Allocatable> list = new TreeSet<>(new NamedComparator<>(raplaLocale.getLocale()));
        list.addAll(Arrays.asList(allocatables));
        setVector(list);
    }

}
