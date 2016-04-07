package org.rapla.plugin.eventtimecalculator;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.inject.Extension;

@Extension(provides = FunctionFactory.class, id=DurationFunctions.NAMESPACE)
public class DurationFunctions implements FunctionFactory
{
    static final public String NAMESPACE = "org.rapla.eventtimecalculator";

    EventTimeCalculatorFactory factory;
    public @Inject DurationFunctions( EventTimeCalculatorFactory factory)
    {
        this.factory = factory;
    }



    @Override public Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException
    {
        if ( functionName.equals(DurationFunction.name))
        {
            return new DurationFunction(args);
        }
        return null;
    }

    class DurationFunction extends Function
    {
        static public final String name = "duration";
        Function arg;
        final EventTimeModel eventTimeModel;

        public DurationFunction( List<Function> args) throws IllegalAnnotationException
        {
            super(name, args);
            assertArgs(0,1);
            if ( args.size() > 0)
            {
                arg = args.get( 0);
            }
            eventTimeModel = factory.getEventTimeModel();

        }

        @Override public String eval(EvalContext context)
        {
            final Object obj;
            if ( arg != null)
            {
                obj = arg.eval( context);
            }
            else
            {
                obj = context.getFirstContextObject();
            }
            final long l = calcDuration(obj);
            String result = eventTimeModel.format( l);
            return result;
        }

        private long calcDuration(Object obj)
        {
            final long l;

            if ( obj instanceof AppointmentBlock)
            {
                l = eventTimeModel.calcDuration((AppointmentBlock) obj);
            }
            else if (obj instanceof Appointment)
            {
                l= eventTimeModel.calcDuration(new Appointment[] { (Appointment) obj });
            }
            else if (obj instanceof Reservation)
            {
                l = eventTimeModel.calcDuration((Reservation) obj);
            }
            else if ( obj instanceof Collection)
            {
                long sum= 0;
                for (Object item:((Collection)obj))
                {
                    sum += calcDuration( item);
                }
                if ( sum<0)
                {
                    sum = -1;
                }
                l = sum;
            }
            else
            {
                l = -1;
            }
            return l;
        }
    }

}

