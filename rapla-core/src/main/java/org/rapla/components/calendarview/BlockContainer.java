package org.rapla.components.calendarview;

@FunctionalInterface
public interface BlockContainer
{
    /** Adds a block. You can optionaly specify a slot, if the day-view supports multiple slots (like in the weekview).
     *  If the selected slot does not exist it will be created. This method is usually called by the builders.
    */
    void addBlock(Block bl, int column, int slot);

}
