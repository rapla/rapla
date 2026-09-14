/*
 * Sun Microsystems grants you ("Licensee") a non-exclusive, royalty
 * free, license to use, modify and redistribute this software in
 * source and binary code form, provided that i) this copyright notice
 * and license appear on all copies of the software; and ii) Licensee
 * does not utilize the software in a manner which is disparaging to
 * Sun Microsystems.
 *
 * The software media is distributed on an "As Is" basis, without
 * warranty. Neither the authors, the software developers nor Sun
 * Microsystems make any representation, or warranty, either express
 * or implied, with respect to the software programs, their quality,
 * accuracy, or fitness for a specific purpose. Therefore, neither the
 * authors, the software developers nor Sun Microsystems shall have
 * any liability to you or any other person or entity with respect to
 * any liability, loss, or damage caused or alleged to have been
 * caused directly or indirectly by programs contained on the
 * media. This includes, but is not limited to, interruption of
 * service, loss of data, loss of classroom time, loss of consulting
 * or anticipatory *profits, or consequential damages from the use of
 * these programs.
*/
package org.rapla.components.treetable;

import javax.swing.tree.TreeModel;

/**
 * TreeTableModel is the model used by a JTreeTable. It extends TreeModel
 * to add methods for getting inforamtion about the set of columns each 
 * node in the TreeTableModel may have. Each column, like a column in 
 * a TableModel, has a name and a type associated with it. Each node in 
 * the TreeTableModel can return a value for each of the columns and 
 * set that value if isCellEditable() returns true. 
 *
 * @author Philip Milne 
 * @author Scott Violet
 */
public interface TreeTableModel extends TreeModel
{
    /**
     * Returns the number ofs available columns.
     */
    int getColumnCount();

    /**
     * Returns the name for column number <code>column</code>.
     */
    String getColumnName(int column);

    /**
     * Returns the type for column number <code>column</code>.
     * Should return TreeTableModel.class for the tree-column.
     */
    Class<?> getColumnClass(int column);

    /**
     * Returns the value to be displayed for node <code>node</code>, 
     * at column number <code>column</code>.
     */
    Object getValueAt(Object node, int column);

    /**
     * Indicates whether the the value for node <code>node</code>, 
     * at column number <code>column</code> is editable.
     */
    boolean isCellEditable(Object node, int column);

    /**
     * Sets the value for node <code>node</code>, 
     * at column number <code>column</code>.
     */
    void setValueAt(Object aValue, Object node, int column);
}
