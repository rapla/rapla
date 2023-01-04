package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;

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
        if ( functionName.equals(DurationCompareFunction.name))
        {
            return new DurationCompareFunction(args);
        }
        return null;
    }

    private long calcDuration(EventTimeModel eventTimeModel, Object obj)
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
                sum += calcDuration(eventTimeModel, item);
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

    class DurationFunction extends Function
    {
        static public final String name = "duration";
        Function arg;

        public DurationFunction( List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,name, args);
            assertArgs(0,1);
            if ( args.size() > 0)
            {
                arg = args.get( 0);
            }

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
            User user =context.getUser();
            EventTimeModel eventTimeModel = factory.getEventTimeModel(user);
            final long l = calcDuration(eventTimeModel,obj);
            String result = eventTimeModel.format( l);
            return result;
        }

    }

    class DurationCompareFunction extends Function {
        static public final String name = "durationCompare";
        Function durationArg;
        Function compareDurationArg;

        public DurationCompareFunction(List<Function> args) throws IllegalAnnotationException {
            super(NAMESPACE, name, args);
            assertArgs(0, 2);
            if (args.size() > 1) {
                durationArg = args.get(0);
                compareDurationArg = args.get(1);
            }
        }

        @Override
        public Long eval(EvalContext context) {
            final Object obj;
            if (durationArg != null) {
                obj = durationArg.eval(context);
            } else {
                obj = context.getFirstContextObject();
            }
            User user = context.getUser();
            EventTimeModel eventTimeModel = factory.getEventTimeModel(user);
            final long l = calcDuration(eventTimeModel, obj);
            Object eval = compareDurationArg.eval(context);
            if ( eval != null ){
                try {
                    Long expectedHours =Long.valueOf(eval.toString());
                    long expectedMinutes = eventTimeModel.calcMinutes(expectedHours);
                    return  l - expectedMinutes;
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        }
    }
}

