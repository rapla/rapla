package org.rapla.components.util.undo;

import org.rapla.scheduler.Promise;

public interface CommandUndo<T extends Exception> {
    Promise<Void> execute();
    Promise<Void> undo();
    String getCommandoName();
}
