/**
 *
 */
package org.rapla.entities.dynamictype.internal;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageNamed;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.rest.GwtIncompatible;

/** 
 * Enables text replacement of variables like {name} {email} with corresponding attribute values
 * Also some functions like {substring(name,1,2)} are available for simple text processing  
 *
 */
public class ParsedText implements Serializable
{
    private static final long serialVersionUID = 1;

    /** the terminal format elements*/
    transient List<String> nonVariablesList;
    /** the variable format elements*/
    transient List<Function> variablesList;
    // used for fast storage of text without variables
    transient private String first = "";

    String formatString;

    ParsedText()
    {
    }

    public ParsedText(String formatString)
    {
        this.formatString = formatString;
    }

    public void init(ParseContext context) throws IllegalAnnotationException
    {
        variablesList = new ArrayList<Function>();
        nonVariablesList = new ArrayList<String>();
        int pos = 0;
        int length = formatString.length();
        List<String> variableContent = new ArrayList<String>();
        while (pos < length)
        {
            int start = formatString.indexOf('{', pos) + 1;
            if (start < 1)
            {
                nonVariablesList.add(formatString.substring(pos, length));
                break;
            }
            int end = formatString.indexOf('}', start);
            if (end < 1)
                throw new IllegalAnnotationException("Closing bracket } missing! in " + formatString);

            nonVariablesList.add(formatString.substring(pos, start - 1));

            String key = formatString.substring(start, end).trim();
            variableContent.add(key);

            pos = end + 1;
        }
        for (String content : variableContent)
        {
            Function func = parseFunctions(context, content);
            variablesList.add(func);
        }
        first = "";
        if (variablesList.isEmpty())
        {
            if (nonVariablesList.size() > 0)
            {
                first = nonVariablesList.iterator().next();
            }
            variablesList = null;
            nonVariablesList = null;
        }
    }

    public void updateFormatString(ParseContext context)
    {
        formatString = getExternalRepresentation(context);
    }

    public String getExternalRepresentation(ParseContext context)
    {
        if (nonVariablesList == null)
        {
            return first;
        }

        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < nonVariablesList.size(); i++)
        {
            buf.append(nonVariablesList.get(i));
            if (i < variablesList.size())
            {
                Function variable = variablesList.get(i);
                String representation = variable.getRepresentation(context);
                buf.append("{");
                buf.append(representation);
                buf.append("}");
            }
        }
        return buf.toString();
    }

    public String formatName(EvalContext context)
    {
        if (nonVariablesList == null && variablesList == null)
        {
            return first;
        }
        StringBuffer buf = new StringBuffer();
        if (variablesList != null && (nonVariablesList == null || nonVariablesList.size() == 0))
        {
            for (int i = 0; i < variablesList.size(); i++)
            {
                Function function = variablesList.get(i);
                Object result = function.eval(context);
                String stringResult = evalToString(result, context);
                buf.append(stringResult);
            }
        }
        else if (nonVariablesList != null)
        {
            for (int i = 0; i < nonVariablesList.size(); i++)
            {
                buf.append(nonVariablesList.get(i));
                if (i < variablesList.size())
                {
                    Function function = variablesList.get(i);
                    Object result = function.eval(context);
                    String stringResult = evalToString(result, context);
                    buf.append(stringResult);
                }
            }
        }

        String string = buf.toString();
        string = string.replaceAll("\\\\n", "\n");
        return string;
    }

    Function parseFunctions(ParseContext context, String content) throws IllegalAnnotationException
    {
        StringBuffer functionName = new StringBuffer();
        for (int i = 0; i < content.length(); i++)
        {
            char c = content.charAt(i);
            if (c == '(')
            {
                int depth = 0;
                for (int j = i + 1; j < content.length(); j++)
                {
                    if (functionName.length() == 0)
                    {
                        throw new IllegalAnnotationException("Function name missing");
                    }
                    char c2 = content.charAt(j);

                    if (c2 == ')')
                    {
                        if (depth == 0)
                        {
                            String recursiveContent = content.substring(i + 1, j);
                            String function = functionName.toString().trim();
                            return parseArguments(context, function, recursiveContent);
                        }
                        else
                        {
                            depth--;
                        }
                    }
                    if (c2 == '(')
                    {
                        depth++;
                    }
                }
            }
            else if (c == ')')
            {
                throw new IllegalAnnotationException("Opening parenthis missing.");
            }
            else
            {
                functionName.append(c);
            }
        }
        String variableName = functionName.toString().trim();
        if (variableName.startsWith("'") && (variableName.endsWith("'"))
                || (variableName.startsWith("\"") && variableName.endsWith("\"")) && variableName.length() > 1)
        {
            String constant = variableName.substring(1, variableName.length() - 1);
            return new StringVariable(constant);
        }
        Function varFunction = context.resolveVariableFunction(variableName);
        if (varFunction != null)
        {
            return varFunction;
        }
        else
        {
            try
            {
                Long l = Long.parseLong(variableName.trim());
                return new IntVariable(l);
            }
            catch (NumberFormatException ex)
            {
            }
            //        	try
            //        	{
            //        		Double d = Double.parseDouble( variableName);
            //        	} catch (NumberFormatException ex)
            //        	{
            //        	}
            throw new IllegalAnnotationException("Attribute for key '" + variableName + "' not found. You have probably deleted or renamed the attribute. ");
        }
    }

    private Function parseArguments(ParseContext context, String functionName, String content) throws IllegalAnnotationException
    {
        int depth = 0;
        List<Function> args = new ArrayList<Function>();
        StringBuffer currentArg = new StringBuffer();
        for (int i = 0; i < content.length(); i++)
        {
            char c = content.charAt(i);
            if (c == '(')
            {
                depth++;
            }
            else if (c == ')')
            {
                depth--;
            }
            if (c != ',' || depth > 0)
            {
                currentArg.append(c);
            }
            if (depth == 0)
            {
                if (c == ',' || i == content.length() - 1)
                {
                    String arg = currentArg.toString();
                    Function function = parseFunctions(context, arg);
                    args.add(function);
                    currentArg = new StringBuffer();
                }
            }

        }
        if (functionName.equals("key"))
        {
            return new KeyFunction(args);
        }
        if (functionName.equals("name"))
        {
            return new NameFunction(args);
        }
        if (functionName.equals("parent"))
        {
            return new ParentFunction(args);
        }
        if (functionName.equals("type"))
        {
            return new TypeFunction(args);
        }
        if (functionName.equals("substring"))
        {
            return new SubstringFunction(args);
        }
        if (functionName.equals("if"))
        {
            return new IfFunction(args);
        }
        if (functionName.equals("concat"))
        {
            return new ConcatFunction(args);
        }
        if (functionName.equals("equals"))
        {
            return new EqualsFunction(args);
        }
        if (functionName.equals("filter"))
        {
            return new FilterFunction(args);
        }
        else if (functionName.equals("isResource"))
        {
            return new ClassificationFunction(args, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE, "isResource");
        }
        else if (functionName.equals("isPerson"))
        {
            return new ClassificationFunction(args, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON, "isPerson");
        }
        else
        {
            throw new IllegalAnnotationException("Unknown function '" + functionName + "'");
        }
        //return new SubstringFunction(functionName, args);
    }

    static public abstract class Variable extends Function
    {
        public Variable(String string)
        {
            super(string);
        }

        @Override
        public String getRepresentation(ParseContext context)
        {
            return getName();
        }
    }

    static public abstract class Function
    {
        String name;
        List<Function> args;

        public Function(String name, List<Function> args)
        {
            this.name = name;
            this.args = args;
        }

        public Function(String name)
        {
            this.name = name;
            this.args = Collections.emptyList();
        }

        protected String getName()
        {
            return name;
        }

        public abstract Object eval(EvalContext context);

        public String getRepresentation(ParseContext context)
        {
            StringBuffer buf = new StringBuffer();

            buf.append(getName());
            buf.append("(");
            for (int i = 0; i < args.size(); i++)
            {
                if (i > 0)
                {
                    buf.append(",");
                }
                buf.append(args.get(i).getRepresentation(context));
            }
            buf.append(")");
            return buf.toString();
        }

        public String toString()
        {
            StringBuffer buf = new StringBuffer();
            buf.append(getName());
            for (int i = 0; i < args.size(); i++)
            {
                if (i == 0)
                {
                    buf.append("(");
                }
                else
                {
                    buf.append(", ");
                }
                buf.append(args.get(i).toString());
                if (i == args.size() - 1)
                {
                    buf.append(")");
                }
            }
            return buf.toString();
        }
    }

    class ClassificationFunction extends Function
    {

        String valueClassificationType;

        public ClassificationFunction(List<Function> args, String valueClassificationType, String functionName)
        {
            super(functionName, args);
            this.valueClassificationType = valueClassificationType;
        }

        @Override
        public Boolean eval(EvalContext context)
        {
            final Classification classification = context.getClassification();
            if (classification != null)
            {
                final String classificationType = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                return classificationType != null && classificationType.equals(valueClassificationType);
            }
            return Boolean.FALSE;
        }

    }

    class KeyFunction extends Function
    {
        Function arg;

        public KeyFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("key", args);
            if (args.size() != 1)
            {
                throw new IllegalAnnotationException("Key Function expects one argument!");
            }
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
            else if (raplaType == Attribute.TYPE)
            {
                Classification classification = context.getClassification();
                Object result = classification.getValue((Attribute) raplaObject);
                if (result instanceof Category)
                {
                    String key = ((Category) result).getKey();
                    return key;
                }
            }

            return "";
        }
    }

    class NameFunction extends Function
    {
        Function objectFunction;
        Function languageFunction;

        public NameFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("name", args);
            final int argSize = args.size();
            if (argSize > 2)
            {
                throw new IllegalAnnotationException("Name Function expects max two argument!");
            }
            objectFunction = args.size() > 0 ? args.get(0) : null;
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
            if (languageFunction != null)
            {
                String language = ParsedText.this.evalToString(languageFunction.eval(context), context);
                Locale locale = context.getLocale();
                if ( language != null && language != locale.getLanguage())
                {
                    final String country = context.getLocale().getCountry();
                    locale = new Locale(language, country);
                    context = context.clone(context.getClassification());
                    context.setLocale( locale);
                }
            }
            final Object obj;
            if (objectFunction != null)
            {
                obj = objectFunction.eval(context);
            }
            else
            {
                obj  = context.getClassification();
            }
            return evalToString(obj, context);
        }
    }

    class IntVariable extends Function
    {
        Long l;

        public IntVariable(Long l)
        {
            super("long");
            this.l = l;
        }

        @Override
        public Long eval(EvalContext context)
        {
            return l;
        }

        public String getRepresentation(ParseContext context)
        {
            return l.toString();
        }

        public String toString()
        {
            return l.toString();
        }

    }

    class StringVariable extends Function
    {
        String s;

        public StringVariable(String s)
        {
            super("string");
            this.s = s;
        }

        @Override
        public String eval(EvalContext context)
        {
            return s;
        }

        public String getRepresentation(ParseContext context)
        {
            return "\"" + s.toString() + "\"";
        }

        public String toString()
        {
            return s.toString();
        }

    }

    class ParentFunction extends Function
    {
        Function arg;

        public ParentFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("parent", args);
            if (args.size() != 1)
            {
                throw new IllegalAnnotationException("Parent Function expects one argument!");
            }
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
            else if (raplaType == Attribute.TYPE)
            {
                Classification classification = context.getClassification();
                Object result = classification.getValue((Attribute) raplaObject);
                if (result instanceof Category)
                {
                    return ((Category) result).getParent();
                }
            }
            return null;

        }
    }

    class TypeFunction extends Function
    {
        TypeFunction(List<Function> args)
        {
            super("type", args);
        }

        public DynamicType eval(EvalContext context)
        {
            DynamicTypeImpl type = (DynamicTypeImpl) context.getClassification().getType();
            return type;
        }

    }

    class SubstringFunction extends Function
    {
        Function content;
        Function start;
        Function end;

        public SubstringFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("substring", args);
            if (args.size() != 3)
            {
                throw new IllegalAnnotationException("Substring Function expects 3 argument!");
            }
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
            String stringResult = ParsedText.this.evalToString(result, context);
            if (stringResult == null)
            {
                return stringResult;
            }
            Long firstIndex = (Long) start.eval(context);
            Long lastIndex = (Long) end.eval(context);
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

    class IfFunction extends Function
    {
        Function condition;
        Function conditionTrue;
        Function conditionFalse;

        public IfFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("if", args);
            if (args.size() != 3)
            {
                throw new IllegalAnnotationException("if function expects 3 argument!");
            }
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
            Object condResult = condition.eval(context);
            Object resultCond = ParsedText.this.getValueForIf(condResult, context);
            boolean isTrue;
            if (resultCond != null)
            {
                String string = resultCond.toString();
                isTrue = !string.equalsIgnoreCase("false") && string.length() > 0;
            }
            else
            {
                isTrue = false;
            }
            Function resultFunction = isTrue ? conditionTrue : conditionFalse;
            Object result = resultFunction.eval(context);
            return result;
        }
    }

    class ConcatFunction extends Function
    {
        List<Function> args;

        public ConcatFunction(List<Function> args)
        {
            super("concat", args);
            this.args = args;
        }

        @Override
        public Object eval(EvalContext context)
        {
            StringBuilder result = new StringBuilder();
            for (Function arg : args)
            {
                Object condResult = arg.eval(context);
                String string = ParsedText.this.evalToString(condResult, context);
                result.append(string);
            }
            return result.toString();
        }
    }

    class EqualsFunction extends Function
    {
        Function arg1;
        Function arg2;

        public EqualsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("equals", args);
            if (args.size() != 2)
            {
                throw new IllegalAnnotationException("equals function expects 2 argument!");
            }
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

    class FilterFunction extends Function
    {
        Function arg1;
        Function arg2;

        public FilterFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("filter", args);
            if (args.size() != 2)
            {
                throw new IllegalAnnotationException("filter function expects 2 argument!");
            }
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
            Collection<Classifiable> result = new ArrayList<Classifiable>();
            for (Object obj : collection)
            {
                if (!(obj instanceof Classifiable))
                {
                    continue;
                }
                Classifiable classifiable = (Classifiable) obj;
                Classification classification = classifiable.getClassification();
                EvalContext subContext = new EvalContext(context.getLocale(), context.getCallStackDepth() + 1, context.getAnnotationName(), classification);
                Object evalResult = arg2.eval(subContext);
                if (!(evalResult instanceof Boolean) || !((Boolean) evalResult))
                {
                    continue;
                }
                result.add(classifiable);
            }
            return result;
        }
    }

    private Object getValueForIf(Object result, EvalContext context)
    {
        if (result instanceof Attribute)
        {
            Attribute attribute = (Attribute) result;
            Classification classification = context.getClassification();
            return classification.getValue(attribute);
        }
        return result;
    }

    private String evalToString(Object object, EvalContext context)
    {
     
        int callStackDepth = context.getCallStackDepth();
        if (callStackDepth > 6)
        {
            return "ErrorSelfReferenceCausesNameOverflow";
        }
        if (object == null)
        {
            return "";
        }
        Locale locale = context.getLocale();
        if (object instanceof Collection)
        {
            StringBuffer buf = new StringBuffer();
            Collection<?> collection = (Collection<?>) object;
            int i = 0;
            for (Object element : collection)
            {
                if (i > 0)
                {
                    buf.append(", ");
                }
                buf.append(evalToString(element, context));
                i++;
            }
            return buf.toString();
        }
        else if (object instanceof TimeInterval)
        {
            Date start = ((TimeInterval) object).getStart();
            Date end = ((TimeInterval) object).getEnd();
            if (DateTools.cutDate(end).equals(end))
            {
                end = DateTools.subDay(end);
            }
            StringBuffer buf = new StringBuffer();
            buf.append(DateTools.formatDate(start, locale));

            if (end != null && end.after(start))
            {
                buf.append("-");
                buf.append(DateTools.formatDate(end, locale));
            }
            return buf.toString();
        }
        else if (object instanceof Date)
        {
            Date date = (Date) object;
            String formatDate = DateTools.formatDateTime(date, locale);
            return formatDate;
        }
        else if (object instanceof Boolean)
        {
            String booleanTranslation = AttributeImpl.getBooleanTranslation(locale, (Boolean) object);
            return booleanTranslation;
        }
        else if (object instanceof Attribute)
        {
            Attribute attribute = (Attribute) object;
            return getAttributeValueAsString(attribute, locale, context);
        }
        else if (object instanceof Classifiable || object instanceof Classification)
        {
            Classification classification;
            if (object instanceof Classification)
            {
                classification = (Classification) object;
            }
            else
            {
                classification = (Classification) ((Classifiable) object).getClassification();
            }
            EvalContext contextClone = context.clone(classification);

            final DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
            ParsedText parsedAnnotation = type.getParsedAnnotation(contextClone.getAnnotationName());
            if (parsedAnnotation == null)
            {
                parsedAnnotation = type.getParsedAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT);
            }
            String format = parsedAnnotation.formatName(contextClone);
            return format;
        }
        else if (object instanceof MultiLanguageNamed)
        {
            MultiLanguageNamed raplaObject = (MultiLanguageNamed) object;
            final String format = raplaObject.getName().getName(locale.getLanguage());
            return format;
        }
        else if (object instanceof Named)
        {
            return ((Named) object).getName(locale);
        }
        
        return object.toString();
    }
    
    private String getAttributeValueAsString(Attribute attribute, Locale locale, EvalContext context)
    {
        Classification classification = context.getClassification();
        if (classification == null)
        {
            return "";
        }
        Collection values = classification.getValues(attribute);
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (Object value : values)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                buf.append(", ");
            }
            String valueAsString;
            if (value instanceof Classifiable)
            {
                valueAsString = evalToString( value, context);
            }
            else
            {
                if (value instanceof Category) {
                    Category rootCategory = (Category) ((AttributeImpl) attribute).getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                    valueAsString =  ((Category) value).getPath(rootCategory, locale);
                }
                else
                {
                    valueAsString = evalToString( value, context);
                }
            }
            buf.append(valueAsString);
        }
        String result = buf.toString();
        return result;
    }

    public interface ParseContext
    {
        Function resolveVariableFunction(String variableName) throws IllegalAnnotationException;
    }

    static public class EvalContext implements Cloneable
    {
        private int callStackDepth;
        private Locale locale;
        private String annotationName;
        private Classification classification;

        public EvalContext(Locale locale)
        {
            this.locale = locale;
            callStackDepth = 0;
            annotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
        }

        public void setLocale(Locale locale)
        {
            this.locale = locale;
        }

        public EvalContext(Locale locale, int callStackDepth, String annotationName, Classification classification)
        {
            this.locale = locale;
            this.callStackDepth = callStackDepth;
            this.annotationName = annotationName;
            this.classification = classification;
        }

        /**  override this method if you can return a classification object in the context. The use of attributes is possible*/
        public Classification getClassification()
        {
            return classification;
        }

        public String getAnnotationName()
        {
            return annotationName;
        }

        public int getCallStackDepth()
        {
            return callStackDepth;
        }

        public Locale getLocale()
        {
            return locale;
        }

        public EvalContext clone(Classification classifictaion)
        {
            EvalContext clone;
            try
            {
                clone = (EvalContext) super.clone();
            }
            catch (CloneNotSupportedException e)
            {
                throw new IllegalStateException(e);
            }
            clone.classification = classifictaion;
            clone.callStackDepth = callStackDepth + 1;
            return clone;
        }

    }

}
