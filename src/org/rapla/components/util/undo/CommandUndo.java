package org.rapla.components.util.undo;


//Erstellt von Dominick Krickl-Vorreiter
public interface CommandUndo<T extends Exception> {
	// Der Rückgabewert signalisiert, ob alles korrekt ausgeführt wurde
	public boolean execute() throws T;
    public boolean undo() throws T;
    public String getCommandoName();
}
