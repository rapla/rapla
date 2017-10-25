package org.rapla.entities.dynamictype.internal;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.plugin.eventtimecalculator.DurationFunctions;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbfile.tests.FileOperatorTest.MyFileIO;
import org.rapla.test.util.DefaultPermissionControllerSupport;
import org.rapla.test.util.RaplaTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RunWith(JUnit4.class)
public class ParsedTextTest 
{
    DynamicTypeImpl type ;
    AttributeImpl attribute;
    CategoryImpl c2;
    CategoryImpl c3;
    CategoryImpl c4;
    PermissionController permissionController;
    User user;
    @Before
    public void setUp() throws Exception
    {
        Logger logger = RaplaTestCase.initLoger();
        String file = "/testdefault.xml";
        String resolvedPath = RaplaTestCase.getTestDataFile(file);
        final RaplaFacade facadeWithFile = RaplaTestCase.createFacadeWithFile(logger, resolvedPath, new MyFileIO(resolvedPath, logger));
        StorageOperator operator = facadeWithFile.getOperator();
        permissionController = DefaultPermissionControllerSupport.getController(operator);
        this.user = null;
        LocalCache cache = new LocalCache(permissionController);
        CategoryImpl c1 = new CategoryImpl();
        c1.setKey("c1");
        c1.getName().setName("de", "Hallo");
        c1.setId("c1");
        c1.setResolver( cache);
        
        c2 = new CategoryImpl();
        c2.setKey("c2");
        c2.setId("c2");
        c2.getName().setName("de", "Welt");
        c2.setResolver( cache);
        c1.addCategory(c2);

        c3 = new CategoryImpl();
        c3.setKey("c3");
        c3.setId("c3");
        c3.getName().setName("de", "Welten");
        c3.setResolver( cache);
        c1.addCategory(c3);
        
        c4 = new CategoryImpl();
        c4.setKey("c4");
        c4.setId("c4");
        c4.getName().setName("de", "Rapla");
        c4.setResolver( cache);
        c1.addCategory(c4);


        cache.put(c1);
        cache.put( c2);
        cache.put( c3);
        cache.put( c4);

        type = new DynamicTypeImpl();
        type.setResolver(cache);
        type.setOperator(operator);
        type.setKey("test");
        type.setId("d1");
        
        
        attribute = new AttributeImpl();
        attribute.setKey("a1");
        attribute.setId("a1");
        attribute.setType(AttributeType.CATEGORY);
        attribute.setResolver(cache);
        attribute.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, c1);
        type.addAttribute(attribute);
        cache.put(type);

        Category constraint = (Category) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
        Assert.assertEquals(c1,constraint);
        type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{a1}");

    }



    @Test
    public void testDurationAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{name(a1,\"de\")}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValueForAttribute(attribute, c2);
        final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController, user, Collections.singletonList(classification));
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String formatName = parsedAnnotation.formatName(evalContext);

        Assert.assertEquals("Welt", formatName);
        Assert.assertEquals(annotationContent, type.getAnnotation(annoName));
    }

    @Test
    public void testCategoryAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{name(a1,\"de\")}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValueForAttribute(attribute, c2);
        final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController, user, Collections.singletonList(classification));
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String formatName = parsedAnnotation.formatName(evalContext);
        
        Assert.assertEquals("Welt", formatName);
        Assert.assertEquals(annotationContent, type.getAnnotation(annoName));
    }

    @Test
    public void testList() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->name(p)}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController,user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welt, Welten, Rapla", formatName);
    }

    @Test
    public void testMissingNamespace() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        try
        {
            final String annotationContent = "{p->duration(p)}";
            type.setAnnotation(annoName, annotationContent);
            Assert.fail("Unknown namespace error expected");
        }
        catch (IllegalAnnotationException ex)
        {
        }
        final String annotationContent = "{p->" + DurationFunctions.NAMESPACE + ":duration(p)}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
    }

    @Test
    public void testBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";

        ParsedText parsedAnnotation;
        final String annotationContentWithoutParanth = "{p->name(attribute(p,\"a1\"),\"de\")}";
        {
            type.setAnnotation(annoName, annotationContentWithoutParanth);
            parsedAnnotation = type.getParsedAnnotation(annoName);
            final String externalRepresentation = type.getAnnotation(annoName);
            Assert.assertEquals(annotationContentWithoutParanth, externalRepresentation);
        }
        {
            final String annotationContent = "{(p)->name(attribute(p,\"a1\"),\"de\")}";
            type.setAnnotation(annoName, annotationContent);
            parsedAnnotation = type.getParsedAnnotation(annoName);
            final String externalRepresentation = type.getAnnotation(annoName);
            Assert.assertEquals(annotationContentWithoutParanth, externalRepresentation);
        }
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValueForAttribute(attribute, c2);
        {
            final EvalContext evalContext = new EvalContext(locale, annoName,permissionController, user, Collections.singletonList(classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("Welt", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController, user, Collections.emptyList());
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("", formatName);
        }
    }
    
    @Test
    public void testMultiBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{(p,u)->concat(name(attribute(p,\"a1\"),\"de\"),name(u))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type.newClassification();
        classification.setValueForAttribute(attribute, c2);
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController, user, Collections.singletonList(classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("Welt", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController, user,Arrays.asList(classification, classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("WeltWelt", formatName);
        }
    }

    @Test
    public void testInnerBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(p,u->equals(substring(name(attribute(u,\"a1\"),\"de\"),0,4),\"Welt\"))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController, user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welt, Welten", formatName);
    }
    
    @Test
    public void testInnerBoundWithOuterReferenceAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(p,u->equals(attribute(u,\"a1\"),attribute(index(p,1),\"a1\")))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController, user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welten", formatName);
    }


    @Test
    public void testSortAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->sort(p,(u,v)->reverse(stringComparator(u,v)))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController,user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welten, Welt, Rapla", formatName);
    }
    
    @Test
    public void testFilterSortAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(sort(p,(p,q)->reverse(stringComparator(p,q))),q->equals(substring(attribute(q,\"a1\"),0,3),\"Wel\"))}";
        type.setAnnotation(annoName, annotationContent);
        type.setReadOnly();
        final ParsedText parsedAnnotation = type.getParsedAnnotation(annoName);
        final String externalRepresentation = type.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type.newClassification();
            classification.setValueForAttribute(attribute, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController, user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welten, Welt", formatName);
    }
    
   
}
