package org.rapla.components.util.undo;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


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
	
	 public <T extends Exception> Promise storeAndExecute(CommandUndo<T> cmd) throws T {
		while (!history.isEmpty() && (current < history.size() - 1)) {
			history.remove(history.size() - 1);
		}
		
		while (history.size() >= maxSize) {
			history.remove(0);
			current--;
		}
		 final Promise<Object, T, Object> execute = cmd.execute();
		 execute.done(new DoneCallback<Object>()
		 {
			 @Override public void onDone(Object result)
			 {
				 history.add(cmd);
				 current++;
				 fireChangeEvent();
			 }
		 });
		 return  execute;
	}

	static final Promise<Object, Object, Object> EMPTY_PROMISE;
	static
	{
		final DeferredObject<Object, Object, Object> objectObjectObjectDeferredObject = new DeferredObject<>();
		objectObjectObjectDeferredObject.resolve(null);
		EMPTY_PROMISE = objectObjectObjectDeferredObject.promise();
	}


	public Promise undo() throws Exception {
		if (!history.isEmpty() && (current >= 0)) {
			final Promise<Object, ?, Object> undo = history.get(current).undo();
			undo.done(new DoneCallback<Object>()
			{
				@Override public void onDone(Object result)
				{
					current--;

				}
			});
			undo.always(new AlwaysCallback()
			{
				@Override public void onAlways(Promise.State state, Object resolved, Object rejected)
				{
					fireChangeEvent();
				}
			});
			return undo;
		}
		return EMPTY_PROMISE;
	}

	public Promise redo() throws Exception {
		if (!history.isEmpty() && (current < history.size() - 1)) {
			final Promise<Object, ?, Object> execute = history.get(current + 1).execute();
			execute.done(new DoneCallback<Object>()
			{
				@Override public void onDone(Object result)
				{
					current++;
				}
			});
			execute.always(new AlwaysCallback()
			{
				@Override public void onAlways(Promise.State state, Object resolved, Object rejected)
				{
					fireChangeEvent();
				}
			});
			return execute;
		}
		return EMPTY_PROMISE;
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
