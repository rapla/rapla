package org.rapla.components.util.undo;


//Erstellt von Dominick Krickl-Vorreiter
public interface CommandUndo<T extends Exception> {
	// Der Rückgabewert signalisiert, ob alles korrekt ausgeführt wurde
    boolean execute() throws T;
    boolean undo() throws T;
    String getCommandoName();
}
