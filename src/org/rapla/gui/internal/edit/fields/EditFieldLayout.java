package org.rapla.gui.internal.edit.fields;

public class EditFieldLayout 
{
    private boolean block;
    private boolean variableSized;
    
    public boolean isBlock() {
        return block;
    }
    
    public void setBlock(boolean block) {
        this.block = block;
    }
    public boolean isVariableSized() {
        return variableSized;
    }
    public void setVariableSized(boolean variableSized) {
        this.variableSized = variableSized;
    }
}
