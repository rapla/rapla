package org.rapla.components.util.undo;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


/**
 * This is where all the committed actions are saved.
 * A list will be initialized, every action is an item of this list.
 * There is a list for the calendar view, and one for the edit view.
 * @author Jens Fritz
 *
 */

//Erstellt von Dominick Krickl-Vorreiter
public class  CommandHistory {
	private List<CommandUndo<?>> history = new ArrayList<CommandUndo<?>>();
	private int current = -1;
	private int maxSize = 100;
	
	private Vector<CommandHistoryChangedListener> listenerList = new Vector<CommandHistoryChangedListener>();
	
	private void fireChangeEvent() {
		for (CommandHistoryChangedListener listener: listenerList.toArray(new CommandHistoryChangedListener[] {}))
		{
			listener.historyChanged();
		}
	}
	
	 public <T extends Exception> boolean storeAndExecute(CommandUndo<T> cmd) throws T {
		while (!history.isEmpty() && (current < history.size() - 1)) {
			history.remove(history.size() - 1);
		}
		
		while (history.size() >= maxSize) {
			history.remove(0);
			current--;
		}
		
		if (cmd.execute()) {
			history.add(cmd);
			current++;
			fireChangeEvent();
			return true;
		}
		else
		{
		    return false;
		}

	}
	
	public void undo() throws Exception {
		if (!history.isEmpty() && (current >= 0)) {
			if (history.get(current).undo())
			{
				current--;
			}
			fireChangeEvent();
		}
	}
	
	public void redo() throws Exception {
		if (!history.isEmpty() && (current < history.size() - 1)) {
			if (history.get(current + 1).execute()) 
			{
				current++;
			}
			fireChangeEvent();
		}
	}
	
	public void clear() {
		history.clear();
		current = -1;
		
		fireChangeEvent();
	}
	
	public int size() {
		return history.size();
	}
	
	public int getCurrent() {
		return current;
	}
	
	public int getMaxSize() {
		return maxSize;
	}
	
	public void setMaxSize(int maxSize) {
		if (maxSize > 0) {
			this.maxSize = maxSize;
		}
	}
	
	public boolean canUndo() {
		return current >= 0;
	}
	
	public boolean canRedo() {
		return current < history.size() - 1;
	}
	
	public String getRedoText()
	{
		if ( !canRedo())
		{
			return "";
		}
		else
		{
			return history.get(current + 1).getCommandoName();
		}
	}

	public String getUndoText()
	{
		if ( !canUndo())
		{
			return "";
		}
		else
		{
			return history.get(current ).getCommandoName();
		}
	}

	
	public void addCommandHistoryChangedListener(CommandHistoryChangedListener actionListener) {
		this.listenerList.add( actionListener);
	}
	
	
	public void removeCommandHistoryChangedListener(CommandHistoryChangedListener actionListener) {
		this.listenerList.remove( actionListener);
	}
	
	public String toString() {
	    StringBuilder builder = new StringBuilder();
	    builder.append("Undo=[");
	    for (int i=current;i>0;i--)
	    {
	        CommandUndo<?> command = history.get(i);
            builder.append(command.getCommandoName());
	        if ( i > 0)
	        {
	            builder.append(", ");
	        }
	    }
	    builder.append("]");
	    builder.append(", ");
	    builder.append("Redo=[");
        for (int i=current+1;i<history.size();i++)
        {
            CommandUndo<?> command = history.get(i);
            builder.append(command.getCommandoName());
            if ( i < history.size() -1)
            {
                builder.append(", ");
            }
        }
        builder.append("]");
	    return builder.toString();
	}
}
