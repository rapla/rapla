package org.rapla.plugin.externaleventimport.server;

public class StagingMeta
{
    private boolean running;
    private long lastStarted;
    private long lastFinished;
    private String lastError;
    private int lastCreated;
    private int lastChanged;
    private int lastDeleted;
    private int lastUnchanged;

    public boolean isRunning() { return running; }

    public void setRunning(boolean running) { this.running = running; }

    public long getLastStarted() { return lastStarted; }

    public void setLastStarted(long lastStarted) { this.lastStarted = lastStarted; }

    public long getLastFinished() { return lastFinished; }

    public void setLastFinished(long lastFinished) { this.lastFinished = lastFinished; }

    public String getLastError() { return lastError; }

    public void setLastError(String lastError) { this.lastError = lastError; }

    public int getLastCreated() { return lastCreated; }

    public void setLastCreated(int lastCreated) { this.lastCreated = lastCreated; }

    public int getLastChanged() { return lastChanged; }

    public void setLastChanged(int lastChanged) { this.lastChanged = lastChanged; }

    public int getLastDeleted() { return lastDeleted; }

    public void setLastDeleted(int lastDeleted) { this.lastDeleted = lastDeleted; }

    public int getLastUnchanged() { return lastUnchanged; }

    public void setLastUnchanged(int lastUnchanged) { this.lastUnchanged = lastUnchanged; }
}
