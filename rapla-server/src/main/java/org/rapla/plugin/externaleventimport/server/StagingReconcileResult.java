package org.rapla.plugin.externaleventimport.server;

public record StagingReconcileResult(boolean skipped, String skipReason, int created, int changed,
                                     int deleted, int unchanged, String error)
{
    public static StagingReconcileResult skipped(String reason)
    {
        return new StagingReconcileResult(true, reason, 0, 0, 0, 0, null);
    }

    public static StagingReconcileResult failed(String error)
    {
        return new StagingReconcileResult(false, null, 0, 0, 0, 0, error);
    }

    public int stored()
    {
        return created + changed;
    }
}
