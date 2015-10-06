package org.rapla.entities.dynamictype.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.storage.LocalCache;

import junit.framework.TestCase;

public class ParsedTextTest extends TestCase
{
    DynamicTypeImpl type ;
    AttributeImpl attribute;
    CategoryImpl c2;
    CategoryImpl c3;
    CategoryImpl c4;
    @Override
    protected void setUp() throws Exception
    {

        LocalCache cache = new LocalCache();
        CategoryImpl c1 = new CategoryImpl();
        c1.setKey("c1");
        c1.getName().setName("de", "Hallo");
        c1.setId("c1");
        
        c2 = new CategoryImpl();
        c2.setKey("c2");
        c2.setId("c2");
        c2.getName().setName("de", "Welt");
        c1.addCategory(c2);
        
        c3 = new CategoryImpl();
        c3.setKey("c3");
        c3.setId("c3");
        c3.getName().setName("de", "Welten");
        c1.addCategory(c3);
        
        c4 = new CategoryImpl();
        c4.setKey("c4");
        c4.setId("c4");
        c4.getName().setName("de", "Rapla");
        c1.addCategory(c4);

        cache.put(c1);
        
        type = new DynamicTypeImpl();
        type.setResolver(cache);
        type.setKey("test");
        type.setId("d1");
        
        
        attribute = new AttributeImpl();
        attribute.setKey("a1");
        attribute.setId("a1");
        attribute.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, c1);
        attribute.setType(AttributeType.CATEGORY);
        attribute.setResolver(cache);
        type.addAttribute(attribute);
        
        type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{a1}");
        cache.put(type);
    }

    public void testCategoryAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{name(a1,\"de\")}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValue(attribute, c2);
        final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.singletonList(classification));
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String formatName = parsedAnnotation.formatName(evalContext);
        
        assertEquals("Welt", formatName);
        assertEquals(annotationContent, type.getAnnotation(annoName));
    }
    
    public void testBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";

        ParsedText parsedAnnotation;
        final String annotationContentWithoutParanth = "{p->name(attribute(p,\"a1\"),\"de\")}";
        {
            type.setAnnotation(annoName, annotationContentWithoutParanth);
            parsedAnnotation = type.getParsedAnnotation(annoName);
            final String externalRepresentation = type.getAnnotation(annoName);
            assertEquals(annotationContentWithoutParanth, externalRepresentation);
        }
        {
            final String annotationContent = "{(p)->name(attribute(p,\"a1\"),\"de\")}";
            type.setAnnotation(annoName, annotationContent);
            parsedAnnotation = type.getParsedAnnotation(annoName);
            final String externalRepresentation = type.getAnnotation(annoName);
            assertEquals(annotationContentWithoutParanth, externalRepresentation);
        }
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValue(attribute, c2);
        {
            final EvalContext evalContext = new EvalContext(locale, annoName, Collections.singletonList(classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            assertEquals("Welt", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.emptyList());
            final String formatName = parsedAnnotation.formatName(evalContext);
            assertEquals("", formatName);
        }
        
        

        
    }
    
    public void testMultiBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{(p,u)->concat(name(attribute(p,\"a1\"),\"de\"),name(u))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValue(attribute, c2);
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        assertEquals(annotationContent, externalRepresentation);
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.singletonList(classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            assertEquals("Welt", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName, Arrays.asList(new Classification[] { classification, classification}));
            final String formatName = parsedAnnotation.formatName(evalContext);
            assertEquals("WeltWelt", formatName);
        }
        

        
    }

    public void testInnerBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(p,u->equals(substring(name(attribute(u,\"a1\"),\"de\"),0,4),\"Welt\"))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c4);
            classifications.add(classification);
        }
        
        
        final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        assertEquals("Welt, Welten", formatName);
    }
    
    public void testInnerBoundWithOuterReferenceAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(p,u->equals(attribute(u,\"a1\"),attribute(index(p,1),\"a1\")))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c4);
            classifications.add(classification);
        }
        
        
        final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        assertEquals("Welten", formatName);
    }
    
    public void testSortAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->sort(p,(u,v)->reverse(stringComparator(u,v)))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c4);
            classifications.add(classification);
        }
        
        
        final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        assertEquals("Welten, Welt, Rapla", formatName);
    }
    
    public void testFilterSortAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(sort(p,(p,q)->reverse(stringComparator(p,q))),q->equals(substring(attribute(q,\"a1\"),0,3),\"Wel\"))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValue(attribute, c4);
            classifications.add(classification);
        }
        
        
        final EvalContext evalContext = new EvalContext(locale,  annoName, Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        assertEquals("Welten, Welt", formatName);
    }
    
   
}
