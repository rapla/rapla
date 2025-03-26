package org.rapla.entities.dynamictype.internal;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.MultiLanguageNamed;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.Timestamp;
import org.rapla.entities.domain.*;
import org.rapla.entities.dynamictype.*;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;


@Extension(provides = FunctionFactory.class, id=StandardFunctions.NAMESPACE)
public class StandardFunctions implements FunctionFactory
{
    public static final String NAMESPACE = "org.rapla";

    private final RaplaLocale raplaLocale;

    @Inject
    public StandardFunctions(RaplaLocale raplaLocale)
    {
        this.raplaLocale = raplaLocale;
    }

    @Override public Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException
    {
        switch (functionName)
        {
            case IsPerson.ID: return new IsPerson(args);
            case IsLocation.ID: return new IsLocation(args);
            case NotFunction.ID: return new NotFunction(args);
            case AndFunction.ID: return new AndFunction(args);
            case OrFunction.ID: return new OrFunction(args);
            case AppointmentBlockFunction.ID: return new AppointmentBlockFunction(args);
            case AppointmentBlocksFunction.ID: return new AppointmentBlocksFunction(args);
            case AppointmentEndFunction.ID: return new AppointmentEndFunction(args);
            case AppointmentStartFunction.ID: return new AppointmentStartFunction(args);
            case AppointmentsFunction.ID: return new AppointmentsFunction(args);
            case AttributeFunction.ID: return new AttributeFunction(args);
            case KeyFunction.ID: return new KeyFunction(args);
            case EnvironmentFunction.ID: return new EnvironmentFunction(args);
            case NameFunction.ID: return new NameFunction(args, raplaLocale);
            case ConcatFunction.ID: return new ConcatFunction(args);
            case EqualsFunction.ID: return new EqualsFunction(args);
            case FilterFunction.ID: return new FilterFunction(args);
            case DateFunction.ID: return new DateFunction(args);
            case IntervallFunction.ID: return new IntervallFunction(args);
            case IfFunction.ID: return new IfFunction(args);
            case SortFunction.ID: return new SortFunction(args);
            case IndexFunction.ID: return new IndexFunction(args);
            case FormatFunction.ID: return new FormatFunction(args);
            case SubstringFunction.ID: return new SubstringFunction(args);
            case ReverseFunction.ID: return new ReverseFunction(args);
            case StringComparatorFunction.ID: return new StringComparatorFunction(args);
            case LastChangedFunction.ID: return new LastChangedFunction(args);
            case EventsFunction.ID: return new EventsFunction(args);
            case ResourcesFunction.ID: return new ResourcesFunction(args);
            case ParentFunction.ID: return new ParentFunction(args);
            case TypeFunction.ID: return new TypeFunction(args);
            case AppointmentTimesFunction.ID: return new AppointmentTimesFunction(args, raplaLocale);
        }
        return null;
    }

    public static class IsPerson extends Function
    {
        public static final String ID = "isPerson";
        private final String valueClassificationType;
        private Function subFunction;

        public IsPerson(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            if (args.size() > 0)
            {
                subFunction = args.get(0);
            }
            this.valueClassificationType = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;
            // filter( resources(), new function(equals(key(type()),"room")))
            // attribute("name"),filter( resources(), if(key(type()),"room"),attribute("roomName"),attribute("name"))
            // filter( resources(), u->kladeradatsch)
            //if(key(type(filter(persons(), v -> equals(type(u), "master"))),"room"),attribute(u,"roomName"),attribute(u,"name")
            // filter( resources(this), u->equals()
            //List<Function> olderUsers = args.stream().filter(u -> u.name.equals("bla")).collect(Collectors.toList());
        }

        @Override public Boolean eval(EvalContext context)
        {
            final Object obj;
            if (subFunction != null)
            {
                obj = subFunction.eval(context);
            }
            else
            {
                obj = context.getFirstContextObject();
            }
            Classification classification = ParsedText.guessClassification(obj);
            if (classification != null)
            {
                final String classificationType = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                return classificationType != null && classificationType.equals(valueClassificationType);
            }
            return Boolean.FALSE;
        }
    }

    public static class IsLocation extends Function
    {
        public static final String ID = "isLocation";
        private Function subFunction;

        public IsLocation(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            if (args.size() > 0)
            {
                subFunction = args.get(0);
            }
            // filter( resources(), new function(equals(key(type()),"room")))
            // attribute("name"),filter( resources(), if(key(type()),"room"),attribute("roomName"),attribute("name"))
            // filter( resources(), u->kladeradatsch)
            //if(key(type(filter(persons(), v -> equals(type(u), "master"))),"room"),attribute(u,"roomName"),attribute(u,"name")
            // filter( resources(this), u->equals()
            //List<Function> olderUsers = args.stream().filter(u -> u.name.equals("bla")).collect(Collectors.toList());
        }

        @Override public Boolean eval(EvalContext context)
        {
            final Object obj;
            if (subFunction != null)
            {
                obj = subFunction.eval(context);
            }
            else
            {
                obj = context.getFirstContextObject();
            }
            Classification classification = ParsedText.guessClassification(obj);
            if (classification != null)
            {
                final String classificationType = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_LOCATION);
                return classificationType != null && classificationType.equals("true");
            }
            return Boolean.FALSE;
        }
    }

    public static class NotFunction extends Function
    {

        public static final String ID = "not";
        private final Function subFunction;

        public NotFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override public Boolean eval(EvalContext context)
        {
            boolean result = ParsedText.evalBoolean(subFunction, context);
            return !result;
        }
    }

    public static class AndFunction extends Function
    {

        public static final String ID = "and";
        private final List<Function> subFunctions;

        public AndFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2, Integer.MAX_VALUE);
            subFunctions = args;
        }

        @Override public Boolean eval(EvalContext context)
        {
            for (Function func : subFunctions)
            {
                boolean result = ParsedText.evalBoolean(func, context);
                if (!result)
                {
                    return false;
                }
            }
            return true;
        }
    }

    public static class OrFunction extends Function
    {

        public static final String ID = "or";
        private final List<Function> subFunctions;

        public OrFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2, Integer.MAX_VALUE);
            subFunctions = args;
        }

        @Override public Boolean eval(EvalContext context)
        {
            for (Function func : subFunctions)
            {
                boolean result = ParsedText.evalBoolean(func, context);
                if (result)
                {
                    return true;
                }
            }
            return false;
        }
    }

    public static class AppointmentBlockFunction extends Function
    {
        public static final String ID = "number";
        private final Function subFunction;

        AppointmentBlockFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override public String eval(EvalContext context)
        {
            final Object object = subFunction.eval(context);
            if (object instanceof AppointmentBlock)
            {
                final AppointmentBlock block = ((AppointmentBlock) object);
                if (block != null)
                {
                    final int appointmentNumber = getAppointmentNumber(block);
                    return "" + appointmentNumber;
                }
            }
            return "";
        }

        private int getAppointmentNumber(AppointmentBlock appointmentBlock)
        {
            final long blockStart = appointmentBlock.getEnd();
            final Date end = new Date(blockStart);
            final Appointment appointment = appointmentBlock.getAppointment();
            final Reservation reservation = appointment.getReservation();
            final Date start = reservation.getFirstDate();
            SortedSet<AppointmentBlock> blocks = new TreeSet<>();
            for (Appointment app : reservation.getAppointments())
            {
                app.createBlocks(start, end, blocks);
            }
            final SortedSet<AppointmentBlock> headSet = blocks.headSet(appointmentBlock);
            final int size = headSet.size();
            //            final long appoimtmentStart = reservation.getFirstDate().getTime();
            //            if (appoimtmentStart ==  start)
            //            {
            //                return 1;
            //            }
            return size + 1;
        }
    }

    public static class AppointmentBlocksFunction extends Function
    {
        public static final String ID = "appointmentBlocks";
        private Function subFunction;
        private Function startFunction;
        private Function endFunction;

        AppointmentBlocksFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(0, 3);
            if (args.size() > 0)
            {
                subFunction = args.get(0);
            }
            if (args.size() > 1)
            {
                startFunction = args.get(1);
            }
            if (args.size() > 2)
            {
                endFunction = args.get(2);
            }
        }

        @Override public Collection<AppointmentBlock> eval(EvalContext context)
        {
            Object object;
            Date start = null;
            Date end = null;
            if (subFunction != null)
            {
                object = subFunction.eval(context);
            }
            else
            {
                object = context.getFirstContextObject();
            }
            if (startFunction != null)
            {
                Object value = startFunction.eval(context);
                if (value instanceof Date)
                {
                    start = (Date) value;
                }
            }
            if (endFunction != null)
            {
                Object value = endFunction.eval(context);
                if (value instanceof Date)
                {
                    start = (Date) value;
                }
            }
            if (object instanceof Reservation)
            {
                Reservation reservation = ((Reservation) object);
                List<AppointmentBlock> blocks = new ArrayList<>();
                for (Appointment appointment : reservation.getSortedAppointments())
                {
                    appointment.createBlocks(start, end, blocks);
                }
                Collections.sort(blocks);
                return blocks;
            }
            if (object instanceof Appointment)
            {
                Appointment appointment = ((Appointment) object);
                Collection<AppointmentBlock> blocks = new ArrayList<>();
                appointment.createBlocks(start, end, blocks);
                return blocks;
            }
            if (object instanceof AppointmentBlock)
            {
                return Collections.singletonList((AppointmentBlock) object);
            }
            /* TODO implement build in methods in tables for resources and appointments
            blocks = blocks(appointments)
            events = events(appointments)
            if (object instanceof CalendarModel)
            {
                final CalendarModel calendarModel = (CalendarModel) object;
                List<AppointmentBlock> blocks;
                try
                {
                    blocks = calendarModel.getBlocks();
                }
                catch (RaplaException e)
                {
                    return null;
                }
                return blocks;
            }
            */
            return Collections.emptyList();
        }

    }

    public static class AppointmentEndFunction extends Function
    {

        public static final String ID = "end";
        private final Function subFunction;

        AppointmentEndFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override public Date eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if (object == null)
            {
                return null;
            }
            if (object instanceof AppointmentBlock)
            {
                AppointmentBlock block = (AppointmentBlock) object;
                return new Date(block.getEnd());
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                return appointment.getEnd();

            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                return reservation.getMaxEnd();
            }
            else if (object instanceof CalendarModel)
            {
                CalendarModel reservation = (CalendarModel) object;
                return reservation.getEndDate();
            }
            return null;
        }
    }

    public static class AppointmentsFunction extends Function
    {

        public static final String ID = "appointments";
        private Function subFunction;

        AppointmentsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(0, 1);
            if (args.size() > 0)
            {
                subFunction = args.get(0);
            }
        }

        @Override public Collection<Appointment> eval(EvalContext context)
        {
            Object object;
            if (subFunction != null)
            {
                object = subFunction.eval(context);
            }
            else
            {
                object = context.getFirstContextObject();
            }
            if (object instanceof Reservation)
            {
                Reservation reservation = ((Reservation) object);
                List<Appointment> asList = Arrays.asList(reservation.getAppointments());
                return asList;
            }
            if (object instanceof Appointment)
            {
                return Collections.singletonList((Appointment) object);
            }
            /* TODO implement build in methods in tables for resources and appointments
            blocks = blocks(appointments)
            events = events(appointments)
            if (object instanceof CalendarModel)
            {
                final CalendarModel calendarModel = (CalendarModel) object;
                Collection<Appointment> appointments;
                try
                {
                    appointments = calendarModel.queryAppointments(calendarModel.getTimeIntervall());
                }
                catch (RaplaException e)
                {
                    return null;
                }
                List<Appointment> result = new ArrayList<Appointment>();
                result.addAll(appointments);
                Collections.sort(result, new AppointmentStartComparator());
                return result;
            }
            */

            return Collections.emptyList();
        }
    }

    public static class AppointmentStartFunction extends Function
    {

        public static final String ID = "start";
        private final Function subFunction;

        AppointmentStartFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            if (args.size() != 1)
            {
                throw new IllegalAnnotationException("appointment function expects 1 argument!");
            }
            subFunction = args.get(0);
        }

        @Override public Date eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if (object == null)
            {
                return null;
            }
            if (object instanceof AppointmentBlock)
            {
                AppointmentBlock block = (AppointmentBlock) object;
                return new Date(block.getStart());
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                return appointment.getStart();
            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                return reservation.getFirstDate();
            }
            else if (object instanceof CalendarModel)
            {
                CalendarModel reservation = (CalendarModel) object;
                return reservation.getStartDate();
            }
            return null;
        }

    }

    public static class AppointmentTimesFunction extends Function
    {

        public static final String ID = "times";
        private final Function subFunction;
        RaplaLocale raplaLocale;

        AppointmentTimesFunction(List<Function> args, RaplaLocale raplaLocale) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            this.raplaLocale = raplaLocale;
            if (args.size() != 1)
            {
                throw new IllegalAnnotationException("appointment function expects 1 argument!");
            }
            subFunction = args.get(0);
        }

        @Override public String eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if (object == null)
            {
                return null;
            }
            TimeInterval interval = null;
            if (object instanceof AppointmentBlock)
            {
                final AppointmentBlock block = (AppointmentBlock) object;
                long start = block.getStart();
                long end = block.getEnd();
                interval = new TimeInterval( new Date(start),new Date(end));
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                final Date start = appointment.getStart();
                final Date end = appointment.getEnd();
                interval = new TimeInterval(start, end);
            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                Date start =  reservation.getFirstDate();
                Date end = reservation.getMaxEnd();
                interval = new TimeInterval(start, end);
            }
            else if (object instanceof CalendarModel)
            {
                CalendarModel model = (CalendarModel) object;
                final Date endDate = model.getEndDate();
                final Date startDate = model.getStartDate();
                interval = new TimeInterval(startDate, endDate);
            }
            return format(interval);
        }

        public String format(TimeInterval timeInterval)
        {
            if ( timeInterval == null)
            {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            final Date start = timeInterval.getStart();
            if ( start != null)
            {
                builder.append( raplaLocale.formatTime(start));
                builder.append(" - ");
            }
            final Date end = timeInterval.getEnd();

            if ( end != null)
            {
                builder.append(raplaLocale.formatTime(end));

            }
            return builder.toString();
        }

    }


    public static class AttributeFunction extends Function
    {

        public static final String ID = "attribute";
        private final Function objectFunction;
        private final Function keyFunction;

        public AttributeFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2);
            objectFunction = args.get(0);
            keyFunction = args.get(1);
        }

        @Override public Object eval(EvalContext context)
        {
            Object obj = objectFunction.eval(context);
            Object keyObj = keyFunction.eval(context);
            if (keyObj == null)
            {
                return null;
            }
            String key = keyObj.toString();
            Classification classification = ParsedText.guessClassification(obj);
            if (classification == null)
            {
                return null;
            }
            DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
            final Attribute attribute = findAttribute(type, key);
            return ParsedText.getProxy(classification, attribute);
        }

        public Attribute findAttribute(DynamicTypeImpl type, String key)
        {
            Attribute attribute = type.getAttribute(key);
            return attribute;
        }
    }

    public static class KeyFunction extends Function
    {

        public static final String ID = "key";
        Function arg;

        public KeyFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            arg = args.get(0);
        }

        private void testMethod() throws IllegalAnnotationException
        {
            java.lang.reflect.Method method;
            try
            {
                Class<? extends Function> class1 = arg.getClass();
                method = class1.getMethod("eval", EvalContext.class);
            }
            catch (Exception e)
            {
                String message = e.getMessage();
                throw new IllegalAnnotationException("Could not parse method for internal error : " + message);
            }
            if (!method.getReturnType().isAssignableFrom(Attribute.class))
            {
                if (!method.getReturnType().isAssignableFrom(Category.class))
                {
                    throw new IllegalAnnotationException("Key Function expects an attribute variable or a function which returns a category");
                }
            }
        }

        @Override public String eval(EvalContext context)
        {
            Object obj = arg.eval(context);
            if (obj == null || !(obj instanceof RaplaObject))
            {
                return "";
            }
            RaplaObject raplaObject = (RaplaObject) obj;
            Class raplaType = raplaObject.getTypeClass();
            if (raplaType == Category.class)
            {
                Category category = (Category) raplaObject;
                String key = category.getKey();
                return key;

            }
            else if (raplaType == DynamicType.class)
            {
                DynamicType type = (DynamicType) raplaObject;
                String key = type.getKey();
                return key;

            }
            return "";
        }
    }

    public static class EnvironmentFunction extends Function
    {

        public static final String ID = "env";
        Function arg;

        public EnvironmentFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            arg = args.get(0);
        }

        @Override public Object eval(EvalContext context)
        {
            Object obj = arg.eval(context);
            if (obj == null || !(obj instanceof String))
            {
                return null;
            }
            Map<String, Object> environment = context.getEnvironment();
            if ( environment == null) {
                return null;
            }
            Object entry = environment.get(obj);
            return entry;
        }
    }

    public static class NameFunction extends Function
    {

        public static final String ID = "name";
        Function objectFunction;
        Function languageFunction;
        private final RaplaLocale raplaLocale;

        public NameFunction(List<Function> args, final RaplaLocale raplaLocale) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            this.raplaLocale = raplaLocale;
            assertArgs(0, 2);
            if (args.size() > 0)
            {
                objectFunction = args.get(0);
            }
            languageFunction = args.size() == 2 ? args.get(1) : null;
            testMethod();
        }

        private void testMethod() throws IllegalAnnotationException
        {
            if (objectFunction == null)
            {
                return;
            }
            java.lang.reflect.Method method;
            try
            {
                Class<? extends Function> class1 = objectFunction.getClass();
                method = class1.getMethod("eval", EvalContext.class);
            }
            catch (Exception e)
            {
                String message = e.getMessage();
                throw new IllegalAnnotationException("Could not parse method for internal error : " + message);
            }
            if (!method.getReturnType().isAssignableFrom(Attribute.class))
            {
                if (!method.getReturnType().isAssignableFrom(Category.class))
                {
                    throw new IllegalAnnotationException("Key Function expects an attribute variable or a function which returns a category");
                }
            }
        }

        @Override public String eval(EvalContext context)
        {
            Object obj;
            Locale locale = context.getLocale();
            if (objectFunction != null)
            {
                obj = objectFunction.eval(context);
                if (languageFunction != null)
                {
                    String language = ParsedText.evalToString(languageFunction.eval(context), context);
                    if (language != null && language != DateTools.getLang(locale))
                    {
                        final String country = DateTools.getCountry(locale);
                        locale = raplaLocale.newLocale(language, country);
                        context = context.clone(locale);
                    }
                }
            }
            else
            {
                obj = context.getFirstContextObject();
            }
            if (obj instanceof MultiLanguageName)
            {
                MultiLanguageNamed raplaObject = (MultiLanguageNamed) obj;
                final String format = raplaObject.getName().getName(locale);
                return format;
            }
            else if (obj instanceof Named)
            {
                return ((Named) obj).getName(locale);
            }

            return ParsedText.evalToString(obj, context);
        }
    }

    public static class ConcatFunction extends Function
    {

        public static final String ID = "concat";
        List<Function> args;

        public ConcatFunction(List<Function> args)
        {
            super(NAMESPACE,ID, args);
            this.args = args;
        }

        @Override public Object eval(EvalContext context)
        {
            StringBuilder result = new StringBuilder();
            for (Function arg : args)
            {
                Object evalResult = arg.eval(context);
                String string = ParsedText.evalToString(evalResult, context);
                result.append(string);
            }
            return result.toString();
        }
    }

    public static class EqualsFunction extends Function
    {
        public static final String ID = "equals";
        Function arg1;
        Function arg2;

        public EqualsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2);
            arg1 = args.get(0);
            arg2 = args.get(1);
            testMethod();
        }

        @SuppressWarnings("unused") private void testMethod() throws IllegalAnnotationException
        {

        }

        @Override public Boolean eval(EvalContext context)
        {
            Object evalResult1 = arg1.eval(context);
            Object evalResult2 = arg2.eval(context);
            if (evalResult1 == null || evalResult2 == null)
            {
                return evalResult1 == evalResult2;
            }
            return evalResult1.equals(evalResult2);
        }
    }

    public static class FilterFunction extends Function
    {

        public static final String ID = "filter";
        Function arg1;
        Function arg2;

        public FilterFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2);
            arg1 = args.get(0);
            arg2 = args.get(1);
            testMethod();
        }

        @SuppressWarnings("unused") private void testMethod() throws IllegalAnnotationException
        {

        }

        @Override public Collection eval(EvalContext context)
        {
            Object evalResult1 = arg1.eval(context);
            Iterable collection;
            if (evalResult1 instanceof Iterable)
            {
                collection = (Iterable) evalResult1;
            }
            else
            {
                collection = Collections.singleton(evalResult1);
            }
            Collection<Object> result = new ArrayList<>();
            for (Object obj : collection)
            {
                //                EvalContext subContext = context.clone(Collections.singletonList(obj));
                // bound new objects
                EvalContext subContext = new EvalContext(Collections.singletonList(obj), context);
                Object evalResult = arg2.eval(subContext);
                if (!(evalResult instanceof Boolean) || !((Boolean) evalResult))
                {
                    continue;
                }
                result.add(obj);
            }
            return result;
        }
    }

    public static class DateFunction extends Function
    {

        public static final String ID = "date";
        private final Function subFunction;

        DateFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override public Date eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if (object == null)
            {
                return null;
            }
            if (object instanceof AppointmentBlock)
            {
                AppointmentBlock block = (AppointmentBlock) object;
                return new Date(DateTools.cutDate(block.getStart()));
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                return DateTools.cutDate(appointment.getStart());

            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                return DateTools.cutDate(reservation.getFirstDate());
            }
            else if (object instanceof CalendarModel)
            {
                CalendarModel reservation = (CalendarModel) object;
                return reservation.getSelectedDate();
            }
            return null;
        }
    }

    public static class IntervallFunction extends Function
    {

        public static final String ID = "intervall";
        private final Function subFunction;

        IntervallFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override public TimeInterval eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if (object == null)
            {
                return null;
            }
            if (object instanceof AppointmentBlock)
            {
                AppointmentBlock block = (AppointmentBlock) object;
                return new TimeInterval(new Date(block.getStart()), new Date(block.getEnd()));
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                return new TimeInterval(appointment.getStart(), appointment.getEnd());
            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                return new TimeInterval(reservation.getFirstDate(), reservation.getMaxEnd());
            }
            else if (object instanceof CalendarModel)
            {
                CalendarModel calendarModel = (CalendarModel) object;
                return new TimeInterval(calendarModel.getStartDate(), calendarModel.getEndDate());
            }
            return null;
        }

    }

    public static class IfFunction extends Function
    {

        public static final String ID = "if";
        Function condition;
        Function conditionTrue;
        Function conditionFalse;

        public IfFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(3);
            condition = args.get(0);
            conditionTrue = args.get(1);
            conditionFalse = args.get(2);
            testMethod();
        }

        /**
         * @throws IllegalAnnotationException
         */
        private void testMethod() throws IllegalAnnotationException
        {
        }

        @Override public Object eval(EvalContext context)
        {
            boolean isTrue = ParsedText.evalBoolean(condition, context);
            Function resultFunction = isTrue ? conditionTrue : conditionFalse;
            Object result = resultFunction.eval(context);
            return result;
        }
    }

    public static class SortFunction extends Function
    {

        public static final String ID = "sort";
        private final Function arg1;
        private final Function arg2;

        public SortFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2);
            arg1 = args.get(0);
            arg2 = args.get(1);
            testMethod();
        }

        @SuppressWarnings("unused") private void testMethod() throws IllegalAnnotationException
        {

        }

        @Override public Collection<?> eval(final EvalContext context)
        {
            Object evalResult1 = arg1.eval(context);
            Iterable<?> collection;
            if (evalResult1 instanceof Iterable)
            {
                collection = (Iterable<?>) evalResult1;
            }
            else
            {
                collection = Collections.singleton(evalResult1);
            }
            List<Object> result = new ArrayList<>();
            for (Object obj : collection)
            {
                result.add(obj);
            }
            Collections.sort(result, (o1, o2) -> {
                List<Object> objects = new ArrayList<>();
                objects.add(o1);
                objects.add(o2);
                EvalContext subContext = new EvalContext(objects, context);
                Object evalResult = arg2.eval(subContext);
                final Long longResult = ParsedText.guessLong(evalResult);
                return longResult.intValue();
            });
            return result;
        }
    }

    public static class IndexFunction extends Function
    {

        public static final String ID = "index";
        private final Function list;
        private final Function index;

        public IndexFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2);

            list = args.get(0);
            index = args.get(1);
        }

        @Override public Object eval(EvalContext context)
        {
            Object evalResult1 = list.eval(context);
            Long i = ParsedText.guessLong(index.eval(context));
            if (i == null)
            {
                return null;
            }

            if (evalResult1 instanceof List)
            {
                return ((List<?>) evalResult1).get(i.intValue());
            }
            if (evalResult1 instanceof Iterable)
            {
                Iterable<?> collection = (Iterable<?>) evalResult1;
                final Iterator<?> iterator = collection.iterator();
                for (int in = 0; in < i && iterator.hasNext(); in++)
                {
                    iterator.next();
                }
                if (iterator.hasNext())
                    return iterator.next();
            }
            else
            {
                if (i == 0)
                    return evalResult1;
            }
            return null;
        }
    }

    public static class SubstringFunction extends Function
    {

        public static final String ID = "substring";
        private final Function content;
        private final Function start;
        private final Function end;

        public SubstringFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(3);

            content = args.get(0);
            start = args.get(1);
            end = args.get(2);
            testMethod();
        }

        private void testMethod() throws IllegalAnnotationException
        {
            {
                java.lang.reflect.Method method;
                try
                {
                    Class<? extends Function> class1 = start.getClass();
                    method = class1.getMethod("eval", EvalContext.class);
                }
                catch (Exception e)
                {
                    throw new IllegalAnnotationException("Could not parse method for internal error : " + e.getMessage());
                }
                if (!method.getReturnType().isAssignableFrom(Long.class))
                {
                    throw new IllegalAnnotationException("Substring method expects a Long parameter as second argument");
                }
            }
            {
                java.lang.reflect.Method method;
                try
                {
                    Class<? extends Function> class1 = end.getClass();
                    method = class1.getMethod("eval", EvalContext.class);
                }
                catch (Exception e)
                {
                    throw new IllegalAnnotationException("Could not parse method for internal error : " + e.getMessage());
                }
                if (!method.getReturnType().isAssignableFrom(Long.class))
                {
                    throw new IllegalAnnotationException("Substring method expects a Long parameter as third argument");
                }
            }
        }

        @Override public String eval(EvalContext context)
        {
            Object result = content.eval(context);
            String stringResult = ParsedText.evalToString(result, context);
            if (stringResult == null)
            {
                return stringResult;
            }

            final Object firstObject = start.eval(context);
            final Object lastObject = end.eval(context);
            Long firstIndex = ParsedText.guessLong(firstObject);
            Long lastIndex = ParsedText.guessLong(lastObject);
            if (firstIndex == null)
            {
                return null;
            }
            else if (lastIndex == null)
            {
                return stringResult.substring(Math.min(firstIndex.intValue(), stringResult.length()));
            }
            else
            {
                return stringResult.substring(Math.min(firstIndex.intValue(), stringResult.length()), Math.min(lastIndex.intValue(), stringResult.length()));
            }

        }
    }

    public static class FormatFunction extends Function
    {

        public static final String ID = "format";
        private final Function format;
        public FormatFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2, Integer.MAX_VALUE);

            format = args.get(0);
            testMethod();
        }

        private void testMethod() throws IllegalAnnotationException
        {
        }

        @Override public String eval(EvalContext context)
        {
            Object formatObject = format.eval(context);
            String formatResult = ParsedText.evalToString(formatObject, context);
            if (formatResult == null)
            {
                return null;
            }
            Object[] formatArgs = new Object[args.size() - 1];
            boolean isNull = true   ;
            for  (int i = 1; i < args.size(); i++) {
                formatArgs[i-1] = args.get(i).eval(context);
                if (formatArgs[i-1] != null)
                {
                    isNull = false;
                }
            }
            String result = isNull ? "" : String.format(formatResult, formatArgs);
            return result;

        }
    }

    public static class ReverseFunction extends Function
    {

        public static final String ID = "reverse";
        private final Function innerFunction;

        public ReverseFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            this.innerFunction = args.get(0);
        }

        @Override public Object eval(EvalContext context)
        {
            final Object innerResult = innerFunction.eval(context);
            final Long guessLong = ParsedText.guessLong(innerResult);
            if (guessLong != null)
            {
                return -guessLong.longValue();
            }
            final String evalToString = ParsedText.evalToString(innerResult, context);
            if (evalToString != null)
            {
                final String reverse = new StringBuilder(evalToString).reverse().toString();
                return reverse;
            }
            return evalToString;
        }
    }

    public static class StringComparatorFunction extends Function
    {

        public static final String ID = "stringComparator";

        private final Function a;
        private final Function b;

        public StringComparatorFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(2);
            a = args.get(0);
            b = args.get(1);
        }

        @Override public Object eval(EvalContext context)
        {
            final String aAsString = ParsedText.evalToString(a.eval(context), context);
            final String bAsString = ParsedText.evalToString(b.eval(context), context);
            if (aAsString == null)
            {
                if (bAsString == null)
                {
                    return 0;
                }
                return 1;
            }
            if (bAsString == null)
            {
                return -1;
            }
            return aAsString.compareTo(bAsString);
        }
    }

    public static class LastChangedFunction extends Function
    {
        public static final String ID = "lastchanged";
        private final Function subFunction;

        LastChangedFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override public Date eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if (object instanceof AppointmentBlock)
            {
                AppointmentBlock block = (AppointmentBlock) object;
                return block.getAppointment().getReservation().getLastChanged();
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                return appointment.getReservation().getLastChanged();
            }
            if (object instanceof Timestamp)
            {
                Timestamp timestamp = (Timestamp) object;
                return timestamp.getLastChanged();
            }
            return null;
        }

    }

    public static class EventsFunction extends Function
    {
        public static final String ID = "events";
        private Function subFunction;

        EventsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(0, 1);
            if (args.size() > 0)
            {
                subFunction = args.get(0);
            }
        }

        @Override public Collection<Reservation> eval(EvalContext context)
        {
            Object object;
            if (subFunction != null)
            {
                object = subFunction.eval(context);
            }
            else
            {
                object = context.getFirstContextObject();
            }
            if (object instanceof Reservation)
            {
                return Collections.singletonList((Reservation) object);
            }
            /* TODO implement build in methods in tables for resources and appointments
            blocks = blocks(appointments)
            events = events(appointments)
            if (object instanceof CalendarModel)
            {
                final CalendarModel calendarModel = (CalendarModel) object;
                Collection<Reservation> reservations;
                try
                {
                    reservations = calendarModel.queryReservations(calendarModel.getTimeIntervall());
                }
                catch (RaplaException e)
                {
                    return null;
                }
                return reservations;
            }
            */
            return Collections.emptyList();
        }
    }

    public static class ResourcesFunction extends Function
    {
        public static final String ID = "resources";
        private Function subFunction;

        ResourcesFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(0, 1);
            if (args.size() > 0)
            {
                subFunction = args.get(0);
            }
        }

        @Override public Collection<Allocatable> eval(EvalContext context)
        {
            Object object;
            if (subFunction != null)
            {
                object = subFunction.eval(context);
            }
            else
            {
                object = context.getFirstContextObject();
            }
            if (object instanceof AppointmentBlock)
            {
                object = ((AppointmentBlock) object).getAppointment();
            }
            if (object instanceof Appointment)
            {
                Appointment appointment = ((Appointment) object);
                final Reservation reservation = appointment.getReservation();
                List<Allocatable> asList = reservation.getAllocatablesFor(appointment).collect(Collectors.toList());
                return asList;
            }
            if (object instanceof CalendarModel)
            {
                CalendarModel model = ((CalendarModel) object);
                List<Allocatable> asList;
                try
                {
                    asList = model.getSelectedAllocatablesSorted();
                }
                catch (RaplaException e)
                {
                    return null;
                }
                return asList;
            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                List<Allocatable> asList = Arrays.asList(reservation.getAllocatables());
                return asList;
            }
            return Collections.emptyList();
        }
    }

    public static class ParentFunction extends Function
    {

        public static final String ID = "parent";
        Function arg;

        public ParentFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            arg = args.get(0);
            testMethod();
        }

        private void testMethod() throws IllegalAnnotationException
        {
            java.lang.reflect.Method method;
            try
            {
                Class<? extends Function> class1 = arg.getClass();
                method = class1.getMethod("eval", EvalContext.class);
            }
            catch (Exception e)
            {
                throw new IllegalAnnotationException("Could not parse method for internal error : " + e.getMessage());
            }
            if (!method.getReturnType().isAssignableFrom(Attribute.class))
            {
                if (!method.getReturnType().isAssignableFrom(Category.class))
                {
                    throw new IllegalAnnotationException("Parent Function expects an attribute variable or a function which returns a category");
                }
            }
        }

        @Override public Category eval(EvalContext context)
        {
            Object obj = arg.eval(context);
            if (obj == null || !(obj instanceof RaplaObject))
            {
                return null;
            }
            RaplaObject raplaObject = (RaplaObject) obj;
            Class raplaType = raplaObject.getTypeClass();
            if (raplaType == Category.class)
            {
                Category category = (Category) raplaObject;
                return category.getParent();
            }
            return null;

        }


    }

    public static class TypeFunction extends Function
    {

        public static final String ID = "type";
        Function subFunction;

        TypeFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,ID, args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        public DynamicType eval(EvalContext context)
        {
            Object obj = subFunction.eval(context);
            DynamicType type = ParsedText.guessType(obj);
            return type;
        }

    }

}
