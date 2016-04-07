package org.rapla.entities.extensionpoints;

import java.util.Collections;
import java.util.List;

import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.dynamictype.internal.ParseContext;


public abstract class Function
{
    protected final String name;
    protected final List<Function> args;

    public Function(String name, List<Function> args)
    {
        this.name = name;
        this.args = Collections.unmodifiableList(args);
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
