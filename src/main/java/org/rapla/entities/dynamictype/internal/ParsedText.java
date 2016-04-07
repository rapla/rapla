/**
 *
 */
package org.rapla.entities.dynamictype.internal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.MultiLanguageNamed;
import org.rapla.entities.Named;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.PermissionController;

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
            if (formatString != null)
            {
                return formatString;
            }
            else
            {
                return "";
            }
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

        if (variableName.equals("true"))
        {
            return new BooleanVariable(true);
        }
        if (variableName.equals("false"))
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

            @Override public Object eval(EvalContext context)
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

            @Override public Object eval(EvalContext context)
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

        @Override public Function resolveVariableFunction(final String variableName) throws IllegalAnnotationException
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

        @Override public FunctionFactory getFunctionFactory(String functionName)
        {
            return context.getFunctionFactory(functionName);
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

        @Override public Object eval(EvalContext context)
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
        String[] split = functionName.split(":");

        String namespace;
        String name;
        if (split.length > 1)
        {
            namespace = split[0];
            name = split[1];
        }
        else
        {
            namespace = StandardFunctions.NAMESPACE;
            name = split[0];
        }
        final FunctionFactory functionFactory = context.getFunctionFactory(namespace);
        if (functionFactory != null)
        {
            return functionFactory.createFunction(name, args);
        }
        else
        {
            throw new IllegalAnnotationException("Unknown function '" + functionName + "'");
        }
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

        @Override public String getRepresentation(ParseContext context)
        {
            return getName();
        }
    }

    /** we need proxies to pass additional information to the evalToStringMethod*/
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
            final boolean multiselect = multiSelectConstraint != null && parseBoolean(multiSelectConstraint);
            if (!multiselect)
            {
                return result.iterator().next();
            }
        }
        return result;
    }

    private static boolean parseBoolean(final Object multiSelectConstraint)
    {
        try
        {
            return Boolean.parseBoolean(multiSelectConstraint.toString());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** */
    public static class CategoryProxy implements Category
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

        public Date getCreateDate()
        {
            return parent.getCreateDate();
        }

        @Override public Class<Category> getTypeClass()
        {
            return parent.getTypeClass();
        }

        public MultiLanguageName getName()
        {
            return parent.getName();
        }

        public String getAnnotation(String key)
        {
            return parent.getAnnotation(key);
        }

        public ReferenceInfo<User> getLastChangedBy()
        {
            return parent.getLastChangedBy();
        }

        public boolean isIdentical(Entity id2)
        {
            return parent.isIdentical(id2);
        }

        public String getAnnotation(String key, String defaultValue)
        {
            return parent.getAnnotation(key, defaultValue);
        }

        public String[] getAnnotationKeys()
        {
            return parent.getAnnotationKeys();
        }

        @Override public ReferenceInfo getReference()
        {
            return parent.getReference();
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

        @Override public Iterable<Category> getCategoryList()
        {
            return parent.getCategoryList();
        }

        public int compareTo(Object o)
        {
            return parent.compareTo(o);
        }

        @Override public Category clone()
        {
            throw new IllegalStateException();
        }

        @Override public void setAnnotation(String key, String annotation) throws IllegalAnnotationException
        {
            throw new IllegalStateException();

        }

        @Override public void addCategory(Category category)
        {
            throw new IllegalStateException();
        }

        @Override public void setKey(String key)
        {
            throw new IllegalStateException();
        }

        public Category getRootCategory()
        {
            final Category constraint = (Category) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            return constraint;
        }

        @Override public boolean equals(Object obj)
        {
            return parent.equals(obj);
        }

        @Override public int hashCode()
        {
            return parent.hashCode();
        }

    }

    // filter(resources(),u->equals("test", getAttribute(u,"name")))
    public static boolean evalBoolean(final Function condition, EvalContext context)
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

    public static Long guessLong(Object obj)
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
        catch (NumberFormatException ex)
        {
            return null;
        }

    }

    public static DynamicType guessType(Object object)
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

    class IntVariable extends Variable
    {
        Long l;

        public IntVariable(Long l)
        {
            super("long");
            this.l = l;
        }

        @Override public Long eval(EvalContext context)
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

        @Override public String eval(EvalContext context)
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
            super(value ? "true" : "false");
            this.value = value;
        }

        @Override public Boolean eval(EvalContext context)
        {
            return value;
        }

    }

    static class AttributeValue
    {
        Attribute attribute;
        Object value;
    }

    public static String evalToString(Object object, EvalContext context)
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
                rootCategory = ((CategoryProxy) object).getRootCategory();
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
            Classification classification;
            boolean readable = true;
            User user = context.getUser();
            PermissionController permissionController = context.getPermissionController();

            if (object instanceof Classification)
            {
                classification = (Classification) object;
            }
            else
            {
                final Classifiable classifiable = (Classifiable) object;
                if (user != null)
                {
                    if (classifiable instanceof PermissionContainer)
                    {
                        readable = permissionController.canRead((Allocatable) classifiable, user);
                    }
                }
                classification = classifiable.getClassification();
            }
            if (!readable)
            {
                return "???";
            }

            final String annotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT;
            final List<Object> contextObjects = Collections.singletonList(object);
            EvalContext contextClone = new EvalContext(locale, annotationName, permissionController, user, contextObjects, callStackDepth + 1);
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
            final String format = raplaObject.getName().getName(DateTools.getLang(locale));
            return format;
        }
        else if (object instanceof Named)
        {
            return ((Named) object).getName(locale);
        }

        return object.toString();
    }

    @Override public String toString()
    {
        return formatString;
    }

}
