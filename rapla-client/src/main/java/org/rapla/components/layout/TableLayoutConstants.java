package org.rapla.components.layout;



/**
 * Constants used by TableLayout.
 *
 * @author  Daniel E. Barbalace
 */

public interface TableLayoutConstants
{



/** Indicates that the component is left justified in its cell */
int LEFT = 0;

/** Indicates that the component is top justified in its cell */
int TOP = 0;

/** Indicates that the component is centered in its cell */
int CENTER = 1;

/** Indicates that the component is full justified in its cell */
int FULL = 2;

/** Indicates that the component is bottom justified in its cell */
int BOTTOM = 3;

/** Indicates that the component is right justified in its cell */
int RIGHT = 3;

/** Indicates that the row/column should fill the available space */
double FILL = -1.0;

/** Indicates that the row/column should be allocated just enough space to
    accomidate the preferred size of all components contained completely within
    this row/column. */
double PREFERRED = -2.0;

/** Indicates that the row/column should be allocated just enough space to
    accomidate the minimum size of all components contained completely within
    this row/column. */
double MINIMUM = -3.0;

/** Minimum value for an alignment */
int MIN_ALIGN = 0;

/** Maximum value for an alignment */
int MAX_ALIGN = 3;



}
