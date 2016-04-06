package org.rapla.components.util.undo;

import org.rapla.scheduler.Promise;

//Erstellt von Dominick Krickl-Vorreiter
public interface CommandUndo<T extends Exception> {
	// Der Rückgabewert signalisiert, ob alles korrekt ausgeführt wurde
    Promise<Void> execute();
    Promise<Void> undo();
    String getCommandoName();
}
