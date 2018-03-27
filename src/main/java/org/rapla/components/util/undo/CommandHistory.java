package org.rapla.components.util.undo;

import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


public class  CommandHistory {
	private List<CommandUndo<?>> history = new ArrayList<>();
	private int current = -1;
	private int maxSize = 100;
	
	private Vector<CommandHistoryChangedListener> listenerList = new Vector<>();
	
	private void fireChangeEvent() {
		for (CommandHistoryChangedListener listener: listenerList.toArray(new CommandHistoryChangedListener[] {}))
		{
			listener.historyChanged();
		}
	}
	
	 public <T extends Exception> Promise<Void> storeAndExecute(CommandUndo<T> cmd)  {
		while (!history.isEmpty() && (current < history.size() - 1)) {
			history.remove(history.size() - 1);
		}
		
		while (history.size() >= maxSize) {
			history.remove(0);
			current--;
		}
		 final Promise<Void> execute = cmd.execute();
		 execute.thenRun(()->
			 {
				 history.add(cmd);
				 current++;
				 fireChangeEvent();
			 }
		 );
		 return  execute;
	}

	public Promise<Void> undo()  {
		if (!history.isEmpty() && (current >= 0)) {
			final Promise<Void> undo = history.get(current).undo();
			undo.thenRun(()->
				{
					current--;
				}
			).finally_(()-> fireChangeEvent());
			return undo;
		}
		return ResolvedPromise.VOID_PROMISE;
	}

	public Promise<Void> redo()  {
		if (!history.isEmpty() && (current < history.size() - 1)) {
			final Promise<Void> execute = history.get(current + 1).execute();
			execute.thenRun(() -> {	current++;
			}).finally_(() -> fireChangeEvent());
			return execute;
		}
		return ResolvedPromise.VOID_PROMISE;
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
