package org.rapla.client.swing;

import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.sync.UtilConcurrentCommandScheduler;

import org.springframework.beans.factory.annotation.Autowired;

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Primary
public class SwingSchedulerImpl extends UtilConcurrentCommandScheduler
{
    @Autowired
    public SwingSchedulerImpl(Logger logger)
    {
        super(logger);
    }
}
