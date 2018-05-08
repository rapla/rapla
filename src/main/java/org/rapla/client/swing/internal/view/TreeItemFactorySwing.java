package org.rapla.client.swing.internal.view;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.internal.TreeItemFactory;
import org.rapla.entities.Named;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Locale;

@DefaultImplementation(of=TreeItemFactory.class,context = InjectionContext.swing)
public class TreeItemFactorySwing implements TreeItemFactory
{
    private  final RaplaResources i18n;

    @Inject
    public TreeItemFactorySwing(RaplaResources i18n)
    {
        this.i18n = i18n;
    }

    @Override
    public RaplaTreeNode createNode(Object userObject)
    {
        Locale locale= i18n.getLocale();
        return new NamedNodeImpl(userObject, locale);
    }

    static public class NamedNodeImpl extends DefaultMutableTreeNode implements RaplaTreeNode
    {
        private static final long serialVersionUID = 1L;

        Locale locale;

        NamedNodeImpl(Object obj, Locale locale)
        {
            super(obj);
            this.locale = locale;
        }

        public String toString()
        {
            final Object obj = getUserObject();
            if (obj != null)
            {
                if (obj instanceof Classifiable)
                {
                    Classification classification = ((Classifiable) obj).getClassification();
                    if (classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING) != null)
                    {
                        return classification.format(locale, DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING);
                    }
                }
                if ( obj instanceof Named)
                {
                    Named named = (Named) obj;
                    String name = named.getName(locale);
                    return name;
                }
            }
            return super.toString();
        }

        public void add(RaplaTreeNode node) {
            super.add( (DefaultMutableTreeNode)node );
        }

        public RaplaTreeNode getChild(int index) {
            return (RaplaTreeNode)getChildAt( index);
        }

        public void remove(RaplaTreeNode child) {
            super.remove((DefaultMutableTreeNode)child);
        }
    }

}
