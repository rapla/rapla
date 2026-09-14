/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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

package org.rapla.components.calendarview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** This strategy groups all blocks in a single group */
public class BestFitStrategy extends AbstractGroupStrategy {
    protected Collection<List<Block>> group(List<Block> blockList) {
        List<List<Block>> singleGroup = new ArrayList<>();
        singleGroup.add(blockList);
        return singleGroup;
    }
}

