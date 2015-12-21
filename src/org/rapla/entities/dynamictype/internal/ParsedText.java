/**
 *
 */
package org.rapla.entities.dynamictype.internal;

import java.io.Serializable;
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

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.MultiLanguageNamed;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
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

    Function parseFunctions(final ParseContext context, String content) throws IllegalAnnotationException
    {
        // {p->name}
        //{p->name(attribute(p,\"a1\"),\"de\")}
        //{(p,u)->concat(name(attribute(p,\"a1\"),\"de\"),name(u))}
        //{name(p->name(attribute(p,\"a1\"),\"de\"), \"en\")}
        content = content.trim();
        StringBuffer parsed = new StringBuffer();
        final ArrayList<String> boundParameters = new ArrayList<String>();
        final int indexOfBoundOperator = content.indexOf("->");
        if (indexOfBoundOperator >= 0)
        {
            int indexOfFirstOpenPh = content.indexOf('(');
            String boundExpression = null;
            if (indexOfFirstOpenPh > indexOfBoundOperator || indexOfFirstOpenPh < 0)
            {
                // Fall 1
                if (indexOfBoundOperator == 0)
                {
                    throw new IllegalAnnotationException("Function paramter missing before ->");
                }
                boundExpression = content.substring(0, indexOfBoundOperator);
            }
            else if (indexOfFirstOpenPh == 0)
            {
                int indexOfFirstClosePh = content.indexOf(')');
                if (content.indexOf('(', 1) < indexOfFirstClosePh)
                {
                    throw new IllegalAnnotationException("Illegal bound syntax " + content.substring(0, indexOfFirstClosePh + 1));
                }
                boundExpression = content.substring(1, indexOfFirstClosePh);
            }
            if (boundExpression != null)
            {
                String[] tokens = boundExpression.split(",");
                for (String token : tokens)
                {
                    token = token.trim();
                    if (!Tools.isKey(token))
                    {
                        throw new IllegalAnnotationException("no valid key " + token);
                    }
                    boundParameters.add(token);
                    // remember the local scope

                }
                content = content.substring(indexOfBoundOperator + 2);
            }
        }
        for (int i = 0; i < content.length(); i++)
        {//{p->f1(...)}
         // parsed will contain the name of the function. parameters will be parsed later.
            char c = content.charAt(i);
            if (c == '(')
            {
                int depth = 0;
                for (int j = i + 1; j < content.length(); j++)
                {
                    char c2 = content.charAt(j);

                    if (c2 == ')')
                    {
                        if (depth == 0)
                        {
                            String functionName = parsed.toString().trim();
                            if (functionName.length() == 0)
                            {
                                throw new IllegalAnnotationException("Function name missing");
                            }
                            // all parameters for the function not parsed yet
                            String recursiveContent = content.substring(i + 1, j);
                            final ParseContext innerContext;
                            if (boundParameters.size() > 0)
                            {
                                innerContext = new BoundParseContext(boundParameters, context);
                            }
                            else
                            {
                                innerContext = context;
                            }
                            final Function function = parseFunctionWithArguments(innerContext, functionName, recursiveContent);
                            if (boundParameters.isEmpty())
                                return function;
                            else
                                return new BoundFunction(boundParameters, function);

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
                parsed.append(c);
            }
        }
        String variableName = parsed.toString().trim();
        if (variableName.startsWith("'") && (variableName.endsWith("'"))
                || (variableName.startsWith("\"") && variableName.endsWith("\"")) && variableName.length() > 1)
        {
            String constant = variableName.substring(1, variableName.length() - 1);
            return new StringVariable(constant);
        }
        
        if ( variableName.equals("true") )
        {
            return new BooleanVariable(true);
        }
        if ( variableName.equals("false") )
        {
            return new BooleanVariable(false);
        }
        try
        {
            Long l = Long.parseLong(variableName.trim());
            return new IntVariable(l);
        }
        catch (NumberFormatException ex)
        {
        }
        Function varFunction = context.resolveVariableFunction(variableName);
        if (varFunction != null)
        {
            return varFunction;
        }
        else
        {

            //        	try
            //        	{
            //        		Double d = Double.parseDouble( variableName);
            //        	} catch (NumberFormatException ex)
            //        	{
            //        	}
            throw new IllegalAnnotationException("Attribute for key '" + variableName + "' not found. You have probably deleted or renamed the attribute. ");
        }
    }

    private static final class BoundParseContext implements ParseContext
    {

        private static final class BoundParameterFunction extends Function
        {
            private final String variableName;
            private final int indexOf;

            private BoundParameterFunction(String variableName, int indexOf)
            {
                super(variableName, emptyList());
                this.variableName = variableName;
                this.indexOf = indexOf;
            }

            @Override
            public Object eval(EvalContext context)
            {
                final Object contextObject = context.getContextObject(indexOf);
                return contextObject;
            }

            public String getRepresentation(ParseContext context)
            {
                return variableName;
            }
        }

        private static final class ParentParameterFunction extends Function
        {
            final Function parentFunction;
            final private String variableName;

            private ParentParameterFunction(Function parentFunction, String variableName)
            {
                super(variableName, emptyList());
                this.parentFunction = parentFunction;
                this.variableName = variableName;

            }

            @Override
            public Object eval(EvalContext context)
            {
                final EvalContext parent = context.getParent();
                if (parent != null)
                {
                    final Object eval = parentFunction.eval(parent);
                    return eval;
                }
                return null;
            }

            public String getRepresentation(ParseContext context)
            {
                return variableName;
            }
        }

        private final ArrayList<String> boundParameters;
        private final ParseContext context;

        private BoundParseContext(ArrayList<String> boundParameters, ParseContext parent)
        {
            this.boundParameters = boundParameters;
            this.context = parent;
        }

        @Override
        public Function resolveVariableFunction(final String variableName) throws IllegalAnnotationException
        {
            final int indexOf = boundParameters.indexOf(variableName);
            if (indexOf >= 0)
            {
                return new BoundParameterFunction(variableName, indexOf);
            }

            final Function resolvedParentFunction = context.resolveVariableFunction(variableName);
            if (resolvedParentFunction != null)
            {
                final ParentParameterFunction parentParameterFunction = new ParentParameterFunction(resolvedParentFunction, variableName);
                return parentParameterFunction;
            }
            else
            {
                return null;
            }
        }
    }

    private static class BoundFunction extends Function
    {

        private Function next;

        public BoundFunction(ArrayList<String> boundParameters, Function next)
        {
            super(createString(boundParameters), Collections.singletonList(next));
            this.next = next;
        }

        public String getRepresentation(ParseContext context)
        {
            StringBuffer buf = new StringBuffer();

            buf.append(getName());
            for (int i = 0; i < args.size(); i++)
            {
                if (i > 0)
                {
                    buf.append(",");
                }
                buf.append(args.get(i).getRepresentation(context));
            }
            return buf.toString();
        }

        private static String createString(ArrayList<String> boundParameters)
        {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            if (boundParameters.size() > 1)
            {
                sb.append("(");
            }
            for (String string : boundParameters)
            {
                if (!first)
                {
                    sb.append(",");
                }
                first = false;
                sb.append(string);
            }
            if (boundParameters.size() > 1)
            {
                sb.append(")");
            }
            sb.append("->");
            return sb.toString();
        }

        @Override
        public Object eval(EvalContext context)
        {
            return next.eval(context);
        }

    }

    private Function parseFunctionWithArguments(ParseContext context, String functionName, String content) throws IllegalAnnotationException
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

        if (functionName.equals("number"))
        {
            return new AppointmentBlockFunction(args);
        }

        if (functionName.equals("key"))
        {
            return new KeyFunction(args);
        }
        else if (functionName.equals("name"))
        {
            return new NameFunction(args);
        }
        else if (functionName.equals("parent"))
        {
            return new ParentFunction(args);
        }
        else if (functionName.equals("type"))
        {
            return new TypeFunction(args);
        }
        else if (functionName.equals("substring"))
        {
            return new SubstringFunction(args);
        }
        else if (functionName.equals("if"))
        {
            return new IfFunction(args);
        }
        else if (functionName.equals("concat"))
        {
            return new ConcatFunction(args);
        }
        else if (functionName.equals("lastchanged"))
        {
            return new LastChangedFunction(args);
        }
        else if (functionName.equals("equals"))
        {
            return new EqualsFunction(args);
        }
        else if (functionName.equals("index"))
        {
            return new IndexFunction(args);
        }
        else if (functionName.equals("attribute"))
        {
            return new AttributeFunction(args);
        }
        else if (functionName.equals("filter"))
        {
            return new FilterFunction(args);
        }
        else if (functionName.equals("not"))
        {
            return new NotFunction(args);
        }
        else if (functionName.equals("and"))
        {
            return new AndFunction(args);
        }
        else if (functionName.equals("or"))
        {
            return new OrFunction(args);
        }
        else if (functionName.equals("isPerson"))
        {
            return new IsPerson(args);
        }
        else if (functionName.equals("appointments"))
        {
            return new AppointmentsFunction(args);
        }
        else if (functionName.equals("appointmentBlocks"))
        {
            return new AppointmentBlocksFunction(args);
        }
        else if (functionName.equals("events"))
        {
            return new EventsFunction(args);
        }
        else if (functionName.equals("resources"))
        {
            return new ResourcesFunction(args);
        }
        else if (functionName.equals("start"))
        {
            return new AppointmentStartFunction(args);
        }
        else if (functionName.equals("end"))
        {
            return new AppointmentEndFunction(args);
        }
        else if (functionName.equals("date"))
        {
            return new DateFunction(args);
        }
        else if (functionName.equals("intervall"))
        {
            return new IntervallFunction(args);
        }
        else if (functionName.equals("stringComparator"))
        {
            return new StringComparatorFunction(args);
        }
        else if (functionName.equals("sort"))
        {
            return new SortFunction(args);
        }
        else if (functionName.equals("reverse"))
        {
            return new ReverseFunction(args);
        }
        else
        {
            throw new IllegalAnnotationException("Unknown function '" + functionName + "'");
        }
        //return new SubstringFunction(functionName, args);
    }

    static List<Function> emptyList()
    {
        return Collections.emptyList();
    }
    
    static public abstract class Variable extends Function
    {
        public Variable(String string)
        {
            super(string, emptyList());
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

        protected void assertArgs(int size) throws IllegalAnnotationException
        {
            assertArgs(size, size);
        }

        protected void assertArgs(int minSize, int maxSize) throws IllegalAnnotationException
        {
            if (args == null)
            {
                if (minSize > 0)
                {
                    throw new IllegalAnnotationException(name + " function expects at least " + minSize + " arguments");
                }
                return;
            }
            final int argSize = args.size();
            if (minSize == maxSize && argSize != minSize)
            {
                throw new IllegalAnnotationException(name + " function expects " + minSize + " arguments");
            }
            if (argSize < minSize)
            {
                throw new IllegalAnnotationException(name + " function expects at least " + minSize + " arguments");
            }
            if (argSize > maxSize)
            {
                throw new IllegalAnnotationException(name + " function expects no more then " + maxSize + " arguments");
            }
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

    class IsPerson extends Function
    {
        private String valueClassificationType;
        private Function subFunction;

        public IsPerson(List<Function> args) throws IllegalAnnotationException
        {
            super("isPerson", args);
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
            Classification classification = guessClassification(obj);
            if (classification != null)
            {
                final String classificationType = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                return classificationType != null && classificationType.equals(valueClassificationType);
            }
            return Boolean.FALSE;
        }

    }

    class NotFunction extends Function
    {
        private Function subFunction;

        public NotFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("not", args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override
        public Boolean eval(EvalContext context)
        {
            boolean result = evalBoolean(subFunction, context);
            return !result;
        }
    }

    class AndFunction extends Function
    {
        private List<Function> subFunctions;

        public AndFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("and", args);
            assertArgs(2, Integer.MAX_VALUE);
            subFunctions = args;
        }

        @Override
        public Boolean eval(EvalContext context)
        {
            for (Function func : subFunctions)
            {
                boolean result = evalBoolean(func, context);
                if (!result)
                {
                    return false;
                }
            }
            return true;
        }
    }

    class OrFunction extends Function
    {
        private List<Function> subFunctions;

        public OrFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("or", args);
            assertArgs(2, Integer.MAX_VALUE);
            subFunctions = args;
        }

        @Override
        public Boolean eval(EvalContext context)
        {
            for (Function func : subFunctions)
            {
                boolean result = evalBoolean(func, context);
                if (result)
                {
                    return true;
                }
            }
            return false;
        }
    }

    static Object getProxy(Classification classification, final Attribute attribute)
    {
        final Collection<Object> values = classification.getValues(attribute);
        Collection<Object> result;
        if (attribute.getType() == AttributeType.CATEGORY)
        {
            Collection<Object> wrapperList = new ArrayList<Object>();
            for (Object value : values)
            {
                if (value instanceof Category)
                {
                    value = new CategoryProxy((Category) value, attribute);
                }
                wrapperList.add(value);
            }
            result = wrapperList;
        }
        else
        {
            result = values;
        }
        if (values.size() == 1)
        {
            final Object multiSelectConstraint = attribute.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
            final boolean multiselect = multiSelectConstraint != null && Boolean.getBoolean(multiSelectConstraint.toString());
            if (!multiselect)
            {
                return result.iterator().next();
            }
        }
        return result;
    }

    static class CategoryProxy implements Category
    {
        private final Category parent;
        private final Attribute attribute;

        public CategoryProxy(Category parent, Attribute attribute)
        {
            super();
            this.parent = parent;
            this.attribute = attribute;
        }

        public Date getLastChanged()
        {
            return parent.getLastChanged();
        }

        public String getName(Locale locale)
        {
            return parent.getName(locale);
        }

        public String getId()
        {
            return parent.getId();
        }

        public Date getCreateTime()
        {
            return parent.getCreateTime();
        }

        public MultiLanguageName getName()
        {
            return parent.getName();
        }

        public String getAnnotation(String key)
        {
            return parent.getAnnotation(key);
        }

        public Date getLastChangeTime()
        {
            return parent.getLastChangeTime();
        }

        public User getLastChangedBy()
        {
            return parent.getLastChangedBy();
        }

        public boolean isIdentical(Entity id2)
        {
            return parent.isIdentical(id2);
        }

        public boolean isPersistant()
        {
            return parent.isPersistant();
        }

        public String getAnnotation(String key, String defaultValue)
        {
            return parent.getAnnotation(key, defaultValue);
        }

        public String[] getAnnotationKeys()
        {
            return parent.getAnnotationKeys();
        }

        public RaplaType<Category> getRaplaType()
        {
            return parent.getRaplaType();
        }

        public void removeCategory(Category category)
        {
            parent.removeCategory(category);
        }

        public Category[] getCategories()
        {
            return parent.getCategories();
        }

        public Category getCategory(String key)
        {
            return parent.getCategory(key);
        }

        public Category findCategory(Category copy)
        {
            return parent.findCategory(copy);
        }

        public Category getParent()
        {
            return parent.getParent();
        }

        public boolean hasCategory(Category category)
        {
            return parent.hasCategory(category);
        }

        public boolean isReadOnly()
        {
            return parent.isReadOnly();
        }

        public String getKey()
        {
            return parent.getKey();
        }

        public boolean isAncestorOf(Category category)
        {
            return parent.isAncestorOf(category);
        }

        public String getPath(Category rootCategory, Locale locale)
        {
            return parent.getPath(rootCategory, locale);
        }

        public int getDepth()
        {
            return parent.getDepth();
        }

        public int getRootPathLength()
        {
            return parent.getRootPathLength();
        }

        public int compareTo(Object o)
        {
            return parent.compareTo(o);
        }

        @Override
        public Category clone()
        {
            throw new IllegalStateException();
        }

        @Override
        public void setAnnotation(String key, String annotation) throws IllegalAnnotationException
        {
            throw new IllegalStateException();

        }

        @Override
        public void addCategory(Category category)
        {
            throw new IllegalStateException();

        }

        @Override
        public void setKey(String key)
        {
            throw new IllegalStateException();
        }

        public Category getRootCategory()
        {
            final Category constraint = (Category) ((AttributeImpl) attribute).getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            return constraint;
        }

        @Override
        public boolean equals(Object obj)
        {
            return parent.equals(obj);
        }

        @Override
        public int hashCode()
        {
            return parent.hashCode();
        }

    }

    // {p,v->concat(attribute(p,"key"), attribute(v,"key")}
    class AttributeFunction extends Function
    {
        private Function objectFunction;
        private Function keyFunction;

        public AttributeFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("attribute", args);
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
            Classification classification = guessClassification(obj);
            if (classification == null)
            {
                return null;
            }
            DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
            final Attribute attribute = findAttribute(type, key);
            return getProxy(classification, attribute);

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

    class KeyFunction extends Function
    {
        Function arg;

        public KeyFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("key", args);
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

    // filter(resources(),u->equals("test", getAttribute(u,"name")))
    private static boolean evalBoolean(final Function condition, EvalContext context)
    {
        Object resultCond = condition.eval(context);
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
        return isTrue;
    }

    static private Long guessLong(Object obj)
    {
        if (obj == null)
        {
            return null;
        }
        if (obj instanceof Number)
        {
            return ((Number) obj).longValue();
        }
        try
        {
            final long result = Long.parseLong(obj.toString());
            return result;
        }
        catch ( NumberFormatException ex)
        {
            return null;
        }
        
    }

    static private DynamicType guessType(Object object)
    {
        if (object instanceof DynamicType)
        {
            return (DynamicType) object;
        }
        if (object instanceof Attribute)
        {
            return ((Attribute) object).getDynamicType();
        }
        final Classification guessClassification = guessClassification(object);
        if (guessClassification != null)
        {
            return guessClassification.getType();
        }
        return null;
    }

    static public Classification guessClassification(Object contextObject)
    {
        if (contextObject instanceof Classification)
        {
            return (Classification) contextObject;
        }
        if (contextObject instanceof Classifiable)
        {
            return ((Classifiable) contextObject).getClassification();
        }
        if (contextObject instanceof Appointment)
        {
            return ((Appointment) contextObject).getReservation().getClassification();
        }
        if (contextObject instanceof AppointmentBlock)
        {
            return ((AppointmentBlock) contextObject).getAppointment().getReservation().getClassification();
        }
        return null;
    }

    class NameFunction extends Function
    {
        Function objectFunction;
        Function languageFunction;

        public NameFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("name", args);
            assertArgs(0, 2);
            if ( args.size() >0)
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
            Object obj ;
            Locale locale = context.getLocale();
            if ( objectFunction != null)
            {
                obj = objectFunction.eval(context);
                if (languageFunction != null)
                {
                    String language = ParsedText.this.evalToString(languageFunction.eval(context), context);
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
            if ( obj instanceof AppointmentBlock)
            {
                obj = ((AppointmentBlock)obj).getAppointment().getReservation();
            }
            else if ( obj instanceof Appointment)
            {
                obj = ((Appointment)obj).getReservation();
            }
            
            if ( obj instanceof MultiLanguageName)
            {
                MultiLanguageNamed raplaObject = (MultiLanguageNamed) obj;
                final String format = raplaObject.getName().getName(locale);
                return format;                
            }
            else if ( obj instanceof Named)
            {
                return ((Named) obj).getName(locale);
            }

            return evalToString(obj, context);
        }
    }

    class IntVariable extends Variable
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

    class StringVariable extends Variable
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
    
    class BooleanVariable extends Variable
    {
        boolean value;

        public BooleanVariable(boolean value)
        {
            super(value ? "true": "false");
            this.value = value;
        }

        @Override
        public Boolean eval(EvalContext context)
        {
            return value;
        }

        

    }

    class ParentFunction extends Function
    {
        Function arg;

        public ParentFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("parent", args);
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

    class TypeFunction extends Function
    {
        Function subFunction;

        TypeFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("type", args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        public DynamicType eval(EvalContext context)
        {
            Object obj = subFunction.eval(context);
            DynamicType type = guessType(obj);
            return type;
        }

    }

    class IndexFunction extends Function
    {
        Function list;
        Function index;

        public IndexFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("index", args);
            assertArgs(2);

            list = args.get(0);
            index = args.get(1);
            //testMethod();
        }

        @Override
        public Object eval(EvalContext context)
        {
            Object evalResult1 = list.eval(context);
            Long i = guessLong(index.eval(context));
            if (i == null)
            {
                return null;
            }

            if (evalResult1 instanceof List)
            {
                return ((List) evalResult1).get(i.intValue());
            }
            if (evalResult1 instanceof Iterable)
            {
                Iterable collection = (Iterable) evalResult1;
                final Iterator iterator = collection.iterator();
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

    class SubstringFunction extends Function
    {
        Function content;
        Function start;
        Function end;

        public SubstringFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("substring", args);
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
            String stringResult = ParsedText.this.evalToString(result, context);
            if (stringResult == null)
            {
                return stringResult;
            }

            final Object firstObject = start.eval(context);
            final Object lastObject = end.eval(context);
            Long firstIndex = guessLong(firstObject);
            Long lastIndex = guessLong(lastObject);
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
            boolean isTrue = evalBoolean(condition, context);
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
                Object evalResult = arg.eval(context);
                String string = ParsedText.this.evalToString(evalResult, context);
                result.append(string);
            }
            return result.toString();
        }
    }

    static class AttributeValue
    {
        Attribute attribute;
        Object value;
    }

    class EqualsFunction extends Function
    {
        Function arg1;
        Function arg2;

        public EqualsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("equals", args);
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

    class FilterFunction extends Function
    {
        Function arg1;
        Function arg2;

        public FilterFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("filter", args);
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

    class SortFunction extends Function
    {
        Function arg1;
        Function arg2;

        public SortFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("sort", args);
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
        public Collection eval(final EvalContext context)
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
                    final Long longResult = guessLong(evalResult);
                    return longResult.intValue();
                }
            });
            return result;
        }
    }

    class ReverseFunction extends Function
    {
        private final Function innerFunction;

        public ReverseFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("reverse", args);
            assertArgs(1);
            this.innerFunction = args.get(0);
        }

        @Override
        public Object eval(EvalContext context)
        {
            final Object innerResult = innerFunction.eval(context);
            final Long guessLong = guessLong(innerResult);
            if(guessLong != null)
            {
                return -guessLong.longValue();
            }
            final String evalToString = evalToString(innerResult, context);
            if ( evalToString != null)
            {
                final String reverse = new StringBuilder(evalToString).reverse().toString();
                return reverse;
            }
            return evalToString;
        }
    }

    class StringComparatorFunction extends Function
    {

        private final Function a;
        private final Function b;

        public StringComparatorFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("stringComparator", args);
            assertArgs(2);
            a = args.get(0);
            b = args.get(1);
        }

        @Override
        public Object eval(EvalContext context)
        {
            final String aAsString = evalToString(a.eval(context), context);
            final String bAsString = evalToString(b.eval(context), context);
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

    class LastChangedFunction extends Function
    {
        private Function subFunction;

        LastChangedFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("lastchanged", args);
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

    class AppointmentsFunction extends Function
    {
        private Function subFunction;

        AppointmentsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("appointments", args);
            assertArgs(0,1);
            if ( args.size() > 0)
            {
                subFunction = args.get(0);
            }
        }

        @Override
        public Collection<Appointment> eval(EvalContext context)
        {
            Object object;
            if ( subFunction != null)
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
                final CalendarModel calendarModel = (CalendarModel)object;
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
                for ( Reservation event:reservations){
                    result.addAll(event.getSortedAppointments());
                }
                result.sort( new AppointmentStartComparator());
                return result;
            }
            
            return Collections.emptyList();
        }
    }
    
    class EventsFunction extends Function
    {
        private Function subFunction;

        EventsFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("events", args);
            assertArgs(0,1);
            if ( args.size() > 0)
            {
                subFunction = args.get(0);
            }
        }

        @Override
        public Collection<Reservation> eval(EvalContext context)
        {
            Object object;
            if ( subFunction != null)
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
                final CalendarModel calendarModel = (CalendarModel)object;
                Reservation[] reservations;
                try
                {
                    reservations = calendarModel.getReservations();
                }
                catch (RaplaException e)
                {
                    return null;
                }
                return Arrays.asList( reservations);
            }
            return Collections.emptyList();
        }
    }
    
    class AppointmentBlocksFunction extends Function
    {
        private Function subFunction;
        private Function startFunction;
        private Function endFunction;

        AppointmentBlocksFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("appointmentBlocks", args);
            assertArgs(0,3);
            if ( args.size() > 0)
            {
                subFunction = args.get(0);
            }
            if ( args.size() > 1)
            {
                startFunction = args.get(1);
            }
            if ( args.size() > 2)
            {
                endFunction = args.get(2);
            }
        }

        @Override
        public Collection<AppointmentBlock> eval(EvalContext context)
        {
            Object object;
            Date start =null;
            Date end = null;
            if ( subFunction != null)
            {
                object = subFunction.eval(context);
            }
            else
            {
                object = context.getFirstContextObject();
            }
            if ( startFunction != null)
            {   
                Object value = startFunction.eval( context);
                if ( value instanceof Date)
                {
                    start = (Date) value;
                }
            }
            if ( endFunction != null)
            {   
                Object value = endFunction.eval( context);
                if ( value instanceof Date)
                {
                    start = (Date) value;
                }
            }
            if (object instanceof Reservation)
            {
                Reservation reservation = ((Reservation) object);
                List<AppointmentBlock> blocks= new ArrayList<AppointmentBlock>();
                for ( Appointment appointment:reservation.getSortedAppointments()){
                    appointment.createBlocks(start, end, blocks);;
                }
                Collections.sort( blocks);
                return blocks;
            }
            if (object instanceof Appointment)
            {
                Appointment appointment = ((Appointment) object);
                Collection<AppointmentBlock> blocks= new ArrayList<AppointmentBlock>();
                appointment.createBlocks(start, end, blocks);;
                return blocks;
            }
            if (object instanceof AppointmentBlock)
            {
                return Collections.singletonList((AppointmentBlock) object);
            }
            if (object instanceof CalendarModel)
            {
                final CalendarModel calendarModel = (CalendarModel)object;
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
    

    class AppointmentStartFunction extends Function
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
            if ( object == null)
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

    class ResourcesFunction extends Function
    {
        private Function subFunction;

        ResourcesFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("resources", args);
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
            if ( subFunction != null)
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

    class AppointmentEndFunction extends Function
    {
        private Function subFunction;

        AppointmentEndFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("end", args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override
        public Date eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if ( object == null)
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

    class DateFunction extends Function
    {
        private Function subFunction;

        DateFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("date", args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override
        public Date eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if ( object == null)
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

    class IntervallFunction extends Function
    {
        private Function subFunction;

        IntervallFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("intervall", args);
            assertArgs(1);
            subFunction = args.get(0);
        }

        @Override
        public TimeInterval eval(EvalContext context)
        {
            Object object = subFunction.eval(context);
            if ( object == null)
            {
                return null;
            }
            if (object instanceof AppointmentBlock)
            {
                AppointmentBlock block = (AppointmentBlock) object;
                return new TimeInterval(new Date(block.getStart()),new Date(block.getEnd()));
            }
            else if (object instanceof Appointment)
            {
                Appointment appointment = (Appointment) object;
                return new TimeInterval(appointment.getStart(),appointment.getEnd());
            }
            else if (object instanceof Reservation)
            {
                Reservation reservation = (Reservation) object;
                return new TimeInterval(reservation.getFirstDate(),reservation.getMaxEnd());
            }
            else if (object instanceof CalendarModel)
            {
                CalendarModel calendarModel = (CalendarModel) object;
                return new TimeInterval( calendarModel.getStartDate(), calendarModel.getEndDate());
            }
            return null;
        }

    }

    
    class AppointmentBlockFunction extends Function
    {
        Function subFunction;

        AppointmentBlockFunction(List<Function> args) throws IllegalAnnotationException
        {
            super("number", args);
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

    private String evalToString(Object object, EvalContext context)
    {

        final int callStackDepth = context.getCallStackDepth();
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
        else if (object instanceof Category)
        {
            Category rootCategory;
            if (object instanceof CategoryProxy)
            {
                rootCategory = (Category) ((CategoryProxy) object).getRootCategory();
            }
            else
            {
                rootCategory = null;
            }
            String valueAsString = ((Category) object).getPath(rootCategory, locale);
            return valueAsString;
        }
        else if (object instanceof Attribute)
        {
            Attribute attribute = (Attribute) object;
            return attribute.getName(locale);
        }
        else if (object instanceof Classifiable || object instanceof Classification)
        {
            User user = context != null ? context.user : null; 
            Classification classification;
            boolean readable= true;
            if (object instanceof Classification)
            {
                classification = (Classification) object;
            }
            else
            {
                final Classifiable classifiable = (Classifiable) object;
                if ( user != null)
                {
                    if ( classifiable instanceof Allocatable)
                    {
                        readable = PermissionContainer.Util.canRead((Allocatable)classifiable, user);
                    }
                    if ( classifiable instanceof Reservation)
                    {
                        readable = PermissionContainer.Util.canRead((Reservation)classifiable, user);
                    }
                }
                classification = classifiable.getClassification();
            }
            if ( !readable)
            {
                return "???";
            }
            final String annotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
            final List<Object> contextObjects = Collections.singletonList(object);
            EvalContext contextClone = new EvalContext(locale, annotationName, contextObjects, user, callStackDepth + 1);
            final DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
            ParsedText parsedAnnotation;
            //parsedAnnotation = type.getParsedAnnotation(contextClone.getAnnotationName());
            //if (parsedAnnotation == null)
            {
                parsedAnnotation = type.getParsedAnnotation(annotationName);
            }
            if (parsedAnnotation != null)
            {
                String format = parsedAnnotation.formatName(contextClone);
                return format;
            }
            else
            {
                ((Named) object).getName(locale);
            }
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

    public interface ParseContext
    {
        Function resolveVariableFunction(String variableName) throws IllegalAnnotationException;
    }

    final static public class EvalContext implements Cloneable
    {
        private int callStackDepth;
        private Locale locale;
        private String annotationName;
        private List<?> contextObjects;
        private EvalContext parent;
        User user;
        
        public EvalContext(Locale locale, String annotationName, List contextObjects)
        {
            this(locale, annotationName, contextObjects, null);
        }

        public EvalContext(Locale locale, String annotationName, List contextObjects, User user)
        {
            this(locale, annotationName, contextObjects, user,0);
        }

        private EvalContext(Locale locale, String annotationName, List contextObjects, User user,int callStackDepth)
        {
            this.locale = locale;
            this.user = user;
            this.callStackDepth = callStackDepth;
            this.annotationName = annotationName;
            this.contextObjects = contextObjects;
        }

        private EvalContext(List<Object> contextObjects, EvalContext parent)
        {
            this.locale = parent.locale;
            this.user = parent.user;
            this.callStackDepth = parent.callStackDepth + 1;
            this.annotationName = parent.annotationName;
            this.contextObjects = contextObjects;
            this.parent = parent;
        }

        EvalContext getParent()
        {
            return parent;
        }

        private EvalContext clone(Locale locale)
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
            clone.contextObjects = contextObjects;
            clone.locale = locale;
            clone.user = user;
            clone.callStackDepth = callStackDepth;
            return clone;
        }

        

        public Object getContextObject(int pos)
        {
            if (pos < contextObjects.size())
            {
                return contextObjects.get(pos);
            }
            return null;
        }

        public List<?> getContextObjects()
        {
            return contextObjects;
        }

        public Object getFirstContextObject()
        {
            if (contextObjects.size() > 0)
            {
                return contextObjects.get(0);
            }
            return null;
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

    }
    
    @Override
    public String toString()
    {
        return formatString;
    }

}
