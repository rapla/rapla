package org.rapla.entities.dynamictype.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.MultiLanguageNamed;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.extensionpoints.FunctionFactory.Function;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.rest.GwtIncompatible;

public abstract class StandardFunctions
{

    @Extension(id = IsPersonFactory.IS_PERSON_FUNCTION, provides = FunctionFactory.class)
    public static class IsPersonFactory implements FunctionFactory
    {
        public static final String IS_PERSON_FUNCTION = "isPerson";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new IsPerson(args);
        }

        public static class IsPerson extends Function
        {
            private String valueClassificationType;
            private Function subFunction;

            public IsPerson(List<Function> args) throws IllegalAnnotationException
            {
                super(IS_PERSON_FUNCTION, args);
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

            @Override
            public Boolean eval(EvalContext context)
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
    }

    @Extension(id = NotFunctionFactory.NOT_FUNCTION, provides = FunctionFactory.class)
    public static class NotFunctionFactory implements FunctionFactory
    {
        public static final String NOT_FUNCTION = "not";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new NotFunction(args);
        }

        public static class NotFunction extends Function
        {
            private Function subFunction;

            public NotFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(NOT_FUNCTION, args);
                assertArgs(1);
                subFunction = args.get(0);
            }

            @Override
            public Boolean eval(EvalContext context)
            {
                boolean result = ParsedText.evalBoolean(subFunction, context);
                return !result;
            }
        }
    }

    @Extension(id = AndFunctionFactory.AND_FUNCTION, provides = FunctionFactory.class)
    public static class AndFunctionFactory implements FunctionFactory
    {
        public static final String AND_FUNCTION = "and";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AndFunction(args);
        }

        public static class AndFunction extends Function
        {
            private List<Function> subFunctions;

            public AndFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(AND_FUNCTION, args);
                assertArgs(2, Integer.MAX_VALUE);
                subFunctions = args;
            }

            @Override
            public Boolean eval(EvalContext context)
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
    }

    @Extension(id = OrFunctionFactory.OR_FUNCTION, provides = FunctionFactory.class)
    public static class OrFunctionFactory implements FunctionFactory
    {
        public static final String OR_FUNCTION = "or";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new OrFunction(args);
        }

        public static class OrFunction extends Function
        {
            private List<Function> subFunctions;

            public OrFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(OR_FUNCTION, args);
                assertArgs(2, Integer.MAX_VALUE);
                subFunctions = args;
            }

            @Override
            public Boolean eval(EvalContext context)
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
    }

    @Extension(id = AppointmentBlockFunctionFactory.NUMBER_FUNCTION, provides = FunctionFactory.class)
    public static class AppointmentBlockFunctionFactory implements FunctionFactory
    {
        public static final String NUMBER_FUNCTION = "number";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AppointmentBlockFunction(args);
        }

        public static class AppointmentBlockFunction extends Function
        {
            private Function subFunction;

            AppointmentBlockFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(NUMBER_FUNCTION, args);
                assertArgs(1);
                subFunction = args.get(0);
            }

            @Override
            public String eval(EvalContext context)
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
                SortedSet<AppointmentBlock> blocks = new TreeSet<AppointmentBlock>();
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

    }

    @Extension(id = AppointmentBlocksFunctionFactory.APPOINTMENT_BLOCKS_FUNCTION, provides = FunctionFactory.class)
    public static class AppointmentBlocksFunctionFactory implements FunctionFactory
    {
        public static final String APPOINTMENT_BLOCKS_FUNCTION = "appointmentBlocks";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AppointmentBlocksFunction(args);
        }

        public static class AppointmentBlocksFunction extends Function
        {
            private Function subFunction;
            private Function startFunction;
            private Function endFunction;

            AppointmentBlocksFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(APPOINTMENT_BLOCKS_FUNCTION, args);
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

            @Override
            public Collection<AppointmentBlock> eval(EvalContext context)
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
                    List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
                    for (Appointment appointment : reservation.getSortedAppointments())
                    {
                        appointment.createBlocks(start, end, blocks);
                        ;
                    }
                    Collections.sort(blocks);
                    return blocks;
                }
                if (object instanceof Appointment)
                {
                    Appointment appointment = ((Appointment) object);
                    Collection<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
                    appointment.createBlocks(start, end, blocks);
                    ;
                    return blocks;
                }
                if (object instanceof AppointmentBlock)
                {
                    return Collections.singletonList((AppointmentBlock) object);
                }
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

                return Collections.emptyList();
            }

        }
    }

    @Extension(id = AppointmentEndFunctionFactory.END_FUNCTION, provides = FunctionFactory.class)
    public static class AppointmentEndFunctionFactory implements FunctionFactory
    {
        public static final String END_FUNCTION = "end";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AppointmentEndFunction(args);
        }

        public static class AppointmentEndFunction extends Function
        {
            private Function subFunction;

            AppointmentEndFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(END_FUNCTION, args);
                assertArgs(1);
                subFunction = args.get(0);
            }

            @Override
            public Date eval(EvalContext context)
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
    }

    @Extension(id = AppointmentsFunctionFactory.APPOINTMENTS_FUNCTION, provides = FunctionFactory.class)
    public static class AppointmentsFunctionFactory implements FunctionFactory
    {
        public static final String APPOINTMENTS_FUNCTION = "appointments";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AppointmentsFunction(args);
        }

        public static class AppointmentsFunction extends Function
        {
            private Function subFunction;

            AppointmentsFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(APPOINTMENTS_FUNCTION, args);
                assertArgs(0, 1);
                if (args.size() > 0)
                {
                    subFunction = args.get(0);
                }
            }

            @Override
            public Collection<Appointment> eval(EvalContext context)
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
                if (object instanceof CalendarModel)
                {
                    final CalendarModel calendarModel = (CalendarModel) object;
                    Reservation[] reservations;
                    try
                    {
                        reservations = calendarModel.getReservations();
                    }
                    catch (RaplaException e)
                    {
                        return null;
                    }
                    List<Appointment> result = new ArrayList<Appointment>();
                    for (Reservation event : reservations)
                    {
                        result.addAll(event.getSortedAppointments());
                    }
                    result.sort(new AppointmentStartComparator());
                    return result;
                }

                return Collections.emptyList();
            }
        }
    }

    @Extension(id = AppointmentStartFunctionFactory.START_FUNCTION, provides = FunctionFactory.class)
    public static class AppointmentStartFunctionFactory implements FunctionFactory
    {
        public static final String START_FUNCTION = "start";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AppointmentStartFunction(args);
        }

        public static class AppointmentStartFunction extends Function
        {
            private Function subFunction;

            AppointmentStartFunction(List<Function> args) throws IllegalAnnotationException
            {
                super("start", args);
                if (args.size() != 1)
                {
                    throw new IllegalAnnotationException("appointment function expects 1 argument!");
                }
                subFunction = args.get(0);
            }

            @Override
            public Date eval(EvalContext context)
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
    }

    @Extension(id = AttributeFunctionFactory.ATTRIBUTE_FUNCTION, provides = FunctionFactory.class)
    public static class AttributeFunctionFactory implements FunctionFactory
    {
        public static final String ATTRIBUTE_FUNCTION = "attribute";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new AttributeFunction(args);
        }

        public static class AttributeFunction extends Function
        {
            private Function objectFunction;
            private Function keyFunction;

            public AttributeFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(ATTRIBUTE_FUNCTION, args);
                assertArgs(2);
                objectFunction = args.get(0);
                keyFunction = args.get(1);
            }

            @Override
            public Object eval(EvalContext context)
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
                if (attribute != null)
                {
                    return attribute;
                }
                return null;
            }
        }
    }

    @Extension(id = KeyFunctionFactory.KEY_FUNCTION, provides = FunctionFactory.class)
    public static class KeyFunctionFactory implements FunctionFactory
    {
        public static final String KEY_FUNCTION = "key";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new KeyFunction(args);
        }

        public static class KeyFunction extends Function
        {
            Function arg;

            public KeyFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(KEY_FUNCTION, args);
                assertArgs(1);
                arg = args.get(0);
            }

            @GwtIncompatible
            private void testMethod() throws IllegalAnnotationException
            {
                Method method;
                try
                {
                    Class<? extends Function> class1 = arg.getClass();
                    method = class1.getMethod("eval", new Class[] { EvalContext.class });
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

            @Override
            public String eval(EvalContext context)
            {
                Object obj = arg.eval(context);
                if (obj == null || !(obj instanceof RaplaObject))
                {
                    return "";
                }
                RaplaObject raplaObject = (RaplaObject) obj;
                RaplaType raplaType = raplaObject.getRaplaType();
                if (raplaType == Category.TYPE)
                {
                    Category category = (Category) raplaObject;
                    String key = category.getKey();
                    return key;

                }
                else if (raplaType == DynamicType.TYPE)
                {
                    DynamicType type = (DynamicType) raplaObject;
                    String key = type.getKey();
                    return key;

                }
                return "";
            }
        }
    }

    @Extension(id = NameFunctionFactory.NAME_FUNCTION, provides = FunctionFactory.class)
    public static class NameFunctionFactory implements FunctionFactory
    {
        public static final String NAME_FUNCTION = "name";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new NameFunction(args);
        }

        public static class NameFunction extends Function
        {
            Function objectFunction;
            Function languageFunction;

            public NameFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(NAME_FUNCTION, args);
                assertArgs(0, 2);
                if (args.size() > 0)
                {
                    objectFunction = args.get(0);
                }
                languageFunction = args.size() == 2 ? args.get(1) : null;
                //testMethod();
            }

            @GwtIncompatible
            private void testMethod() throws IllegalAnnotationException
            {
                if (objectFunction == null)
                {
                    return;
                }
                Method method;
                try
                {
                    Class<? extends Function> class1 = objectFunction.getClass();
                    method = class1.getMethod("eval", new Class[] { EvalContext.class });
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

            @Override
            public String eval(EvalContext context)
            {
                Object obj;
                Locale locale = context.getLocale();
                if (objectFunction != null)
                {
                    obj = objectFunction.eval(context);
                    if (languageFunction != null)
                    {
                        String language = ParsedText.evalToString(languageFunction.eval(context), context);
                        if (language != null && language != locale.getLanguage())
                        {
                            final String country = context.getLocale().getCountry();
                            locale = new Locale(language, country);
                            context = context.clone(locale);
                        }
                    }
                }
                else
                {
                    obj = context.getFirstContextObject();
                }
                if (obj instanceof AppointmentBlock)
                {
                    obj = ((AppointmentBlock) obj).getAppointment().getReservation();
                }
                else if (obj instanceof Appointment)
                {
                    obj = ((Appointment) obj).getReservation();
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
    }

    @Extension(id = ConcatFunctionFactory.CONCAT_FUNCTION, provides = FunctionFactory.class)
    public static class ConcatFunctionFactory implements FunctionFactory
    {
        public static final String CONCAT_FUNCTION = "concat";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new ConcatFunction(args);
        }

        public static class ConcatFunction extends Function
        {
            List<Function> args;

            public ConcatFunction(List<Function> args)
            {
                super(CONCAT_FUNCTION, args);
                this.args = args;
            }

            @Override
            public Object eval(EvalContext context)
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
    }

    @Extension(id = EqualsFunctionFactory.EQUALS_FUNCTION, provides = FunctionFactory.class)
    public static class EqualsFunctionFactory implements FunctionFactory
    {
        public static final String EQUALS_FUNCTION = "equals";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new EqualsFunction(args);
        }

        public static class EqualsFunction extends Function
        {
            Function arg1;
            Function arg2;

            public EqualsFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(EQUALS_FUNCTION, args);
                assertArgs(2);
                arg1 = args.get(0);
                arg2 = args.get(1);
                testMethod();
            }

            @SuppressWarnings("unused")
            private void testMethod() throws IllegalAnnotationException
            {

            }

            @Override
            public Boolean eval(EvalContext context)
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
    }

    @Extension(id = FilterFunctionFactory.FILTER_FUNCTION, provides = FunctionFactory.class)
    public static class FilterFunctionFactory implements FunctionFactory
    {
        public static final String FILTER_FUNCTION = "filter";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new FilterFunction(args);
        }

        public static class FilterFunction extends Function
        {
            Function arg1;
            Function arg2;

            public FilterFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(FILTER_FUNCTION, args);
                assertArgs(2);
                arg1 = args.get(0);
                arg2 = args.get(1);
                testMethod();
            }

            @SuppressWarnings("unused")
            private void testMethod() throws IllegalAnnotationException
            {

            }

            @Override
            public Collection eval(EvalContext context)
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
                Collection<Object> result = new ArrayList<Object>();
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
    }

    @Extension(id = DateFunctionFactory.DATE_FUNCTION, provides = FunctionFactory.class)
    public static class DateFunctionFactory implements FunctionFactory
    {
        public static final String DATE_FUNCTION = "date";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new DateFunction(args);
        }

        public static class DateFunction extends Function
        {
            private Function subFunction;

            DateFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(DATE_FUNCTION, args);
                assertArgs(1);
                subFunction = args.get(0);
            }

            @Override
            public Date eval(EvalContext context)
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
                    return reservation.getSelectedDate();
                }
                return null;
            }
        }
    }

    @Extension(id = IntervallFunctionFactory.INTERVALL_FUNCTION, provides = FunctionFactory.class)
    public static class IntervallFunctionFactory implements FunctionFactory
    {

        public static final String INTERVALL_FUNCTION = "intervall";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new IntervallFunction(args);
        }

        public static class IntervallFunction extends Function
        {
            private Function subFunction;

            IntervallFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(INTERVALL_FUNCTION, args);
                assertArgs(1);
                subFunction = args.get(0);
            }

            @Override
            public TimeInterval eval(EvalContext context)
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
    }

    @Extension(id = IfFunctionFactory.IF_FUNCTION, provides = Function.class)
    public static class IfFunctionFactory implements FunctionFactory
    {

        public static final String IF_FUNCTION = "if";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new IfFunction(args);
        }

        public static class IfFunction extends Function
        {
            Function condition;
            Function conditionTrue;
            Function conditionFalse;

            public IfFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(IF_FUNCTION, args);
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

            @Override
            public Object eval(EvalContext context)
            {
                boolean isTrue = ParsedText.evalBoolean(condition, context);
                Function resultFunction = isTrue ? conditionTrue : conditionFalse;
                Object result = resultFunction.eval(context);
                return result;
            }
        }
    }

    @Extension(id = SortFunctionFactory.SORT_FUNCTION, provides = Function.class)
    public static class SortFunctionFactory implements FunctionFactory
    {
        public static final String SORT_FUNCTION = "sort";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new SortFunction(args);
        }

        public static class SortFunction extends Function
        {
            private final Function arg1;
            private final Function arg2;

            public SortFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(SORT_FUNCTION, args);
                assertArgs(2);
                arg1 = args.get(0);
                arg2 = args.get(1);
                testMethod();
            }

            @SuppressWarnings("unused")
            private void testMethod() throws IllegalAnnotationException
            {

            }

            @Override
            public Collection<?> eval(final EvalContext context)
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
                List<Object> result = new ArrayList<Object>();
                for (Object obj : collection)
                {
                    result.add(obj);
                }
                Collections.sort(result, new Comparator<Object>()
                {
                    @Override
                    public int compare(Object o1, Object o2)
                    {
                        List<Object> objects = new ArrayList<Object>();
                        objects.add(o1);
                        objects.add(o2);
                        EvalContext subContext = new EvalContext(objects, context);
                        Object evalResult = arg2.eval(subContext);
                        final Long longResult = ParsedText.guessLong(evalResult);
                        return longResult.intValue();
                    }
                });
                return result;
            }
        }
    }

    @Extension(id = IndexFunctionFactory.INDEX_FUNCTION, provides = FunctionFactory.class)
    public static class IndexFunctionFactory implements FunctionFactory
    {
        public static final String INDEX_FUNCTION = "index";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new IndexFunction(args);
        }

        public static class IndexFunction extends Function
        {
            private final Function list;
            private final Function index;

            public IndexFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(INDEX_FUNCTION, args);
                assertArgs(2);

                list = args.get(0);
                index = args.get(1);
                //testMethod();
            }

            @Override
            public Object eval(EvalContext context)
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
    }

    @Extension(id = SubstringFunctionFactory.SUBSTRING_FUNCTION, provides = FunctionFactory.class)
    public static class SubstringFunctionFactory implements FunctionFactory
    {
        public static final String SUBSTRING_FUNCTION = "substring";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new SubstringFunction(args);
        }

        public static class SubstringFunction extends Function
        {
            private final Function content;
            private final Function start;
            private final Function end;

            public SubstringFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(SUBSTRING_FUNCTION, args);
                assertArgs(3);

                content = args.get(0);
                start = args.get(1);
                end = args.get(2);
                //testMethod();
            }

            @GwtIncompatible
            private void testMethod() throws IllegalAnnotationException
            {
                {
                    Method method;
                    try
                    {
                        Class<? extends Function> class1 = start.getClass();
                        method = class1.getMethod("eval", new Class[] { EvalContext.class });
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
                    Method method;
                    try
                    {
                        Class<? extends Function> class1 = end.getClass();
                        method = class1.getMethod("eval", new Class[] { EvalContext.class });
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

            @Override
            public String eval(EvalContext context)
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
                    return stringResult.substring(Math.min(firstIndex.intValue(), stringResult.length()),
                            Math.min(lastIndex.intValue(), stringResult.length()));
                }

            }
        }
    }

    @Extension(id = ReverseFunctionFactory.REVERSE_FUNCTION, provides = FunctionFactory.class)
    public static class ReverseFunctionFactory implements FunctionFactory
    {
        public static final String REVERSE_FUNCTION = "reverse";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new ReverseFunction(args);
        }

        public static class ReverseFunction extends Function
        {
            private final Function innerFunction;

            public ReverseFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(REVERSE_FUNCTION, args);
                assertArgs(1);
                this.innerFunction = args.get(0);
            }

            @Override
            public Object eval(EvalContext context)
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
    }

    @Extension(id = StringComparatorFunctionFactory.STRING_COMPARATOR_FUNCTION, provides = FunctionFactory.class)
    public static class StringComparatorFunctionFactory implements FunctionFactory
    {
        public static final String STRING_COMPARATOR_FUNCTION = "stringComparator";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new StringComparatorFunction(args);
        }

        public static class StringComparatorFunction extends Function
        {

            private final Function a;
            private final Function b;

            public StringComparatorFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(STRING_COMPARATOR_FUNCTION, args);
                assertArgs(2);
                a = args.get(0);
                b = args.get(1);
            }

            @Override
            public Object eval(EvalContext context)
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
    }

    @Extension(id = LastChangedFunctionFactory.LAST_CHANGED_FUNCTION, provides = FunctionFactory.class)
    public static class LastChangedFunctionFactory implements FunctionFactory
    {
        public static final String LAST_CHANGED_FUNCTION = "lastchanged";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new LastChangedFunction(args);
        }

        public static class LastChangedFunction extends Function
        {
            private Function subFunction;

            LastChangedFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(LAST_CHANGED_FUNCTION, args);
                assertArgs(1);
                subFunction = args.get(0);
            }

            @Override
            public Date eval(EvalContext context)
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
    }

    @Extension(id = EventsFunctionFactory.EVENTS_FUNCTION, provides = FunctionFactory.class)
    public static class EventsFunctionFactory implements FunctionFactory
    {
        public static final String EVENTS_FUNCTION = "events";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new EventsFunction(args);
        }

        public static class EventsFunction extends Function
        {
            private Function subFunction;

            EventsFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(EVENTS_FUNCTION, args);
                assertArgs(0, 1);
                if (args.size() > 0)
                {
                    subFunction = args.get(0);
                }
            }

            @Override
            public Collection<Reservation> eval(EvalContext context)
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
                if (object instanceof CalendarModel)
                {
                    final CalendarModel calendarModel = (CalendarModel) object;
                    Reservation[] reservations;
                    try
                    {
                        reservations = calendarModel.getReservations();
                    }
                    catch (RaplaException e)
                    {
                        return null;
                    }
                    return Arrays.asList(reservations);
                }
                return Collections.emptyList();
            }
        }
    }

    @Extension(id = ResourcesFunctionFactory.RESOURCES_FUNCTION, provides = FunctionFactory.class)
    public static class ResourcesFunctionFactory implements FunctionFactory
    {
        public static final String RESOURCES_FUNCTION = "resources";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new ResourcesFunction(args);
        }

        public static class ResourcesFunction extends Function
        {
            private Function subFunction;

            ResourcesFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(RESOURCES_FUNCTION, args);
                assertArgs(0, 1);
                if (args.size() > 0)
                {
                    subFunction = args.get(0);
                }
            }

            @Override
            public Collection<Allocatable> eval(EvalContext context)
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
                    List<Allocatable> asList = Arrays.asList(reservation.getAllocatablesFor(appointment));
                    return asList;
                }
                if (object instanceof CalendarModel)
                {
                    CalendarModel model = ((CalendarModel) object);
                    Allocatable[] selectedAllocatables;
                    try
                    {
                        selectedAllocatables = model.getSelectedAllocatables();
                    }
                    catch (RaplaException e)
                    {
                        return null;
                    }
                    List<Allocatable> asList = Arrays.asList(selectedAllocatables);
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
    }

    @Extension(id = ParentFunctionFactory.PARENT_FUNCTION, provides = FunctionFactory.class)
    public static class ParentFunctionFactory implements FunctionFactory
    {
        public static final String PARENT_FUNCTION = "parent";

        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new ParentFunction(args);
        };

        public static class ParentFunction extends Function
        {
            Function arg;

            public ParentFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(PARENT_FUNCTION, args);
                assertArgs(1);
                arg = args.get(0);
                //testMethod();
            }

            @GwtIncompatible
            private void testMethod() throws IllegalAnnotationException
            {
                Method method;
                try
                {
                    Class<? extends Function> class1 = arg.getClass();
                    method = class1.getMethod("eval", new Class[] { EvalContext.class });
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

            @Override
            public Category eval(EvalContext context)
            {
                Object obj = arg.eval(context);
                if (obj == null || !(obj instanceof RaplaObject))
                {
                    return null;
                }
                RaplaObject raplaObject = (RaplaObject) obj;
                RaplaType raplaType = raplaObject.getRaplaType();
                if (raplaType == Category.TYPE)
                {
                    Category category = (Category) raplaObject;
                    return category.getParent();
                }
                return null;

            }
        }
    }

    @Extension(id = TypeFunctionFactory.TYPE_FUNCTION, provides = FunctionFactory.class)
    public static class TypeFunctionFactory implements FunctionFactory
    {
        public static final String TYPE_FUNCTION = "type";

        @Override
        public Function createFunction(List<Function> args) throws IllegalAnnotationException
        {
            return new TypeFunction(args);
        }

        public static class TypeFunction extends Function
        {
            Function subFunction;

            TypeFunction(List<Function> args) throws IllegalAnnotationException
            {
                super(TYPE_FUNCTION, args);
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
}
