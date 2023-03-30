package org.rapla.entities.dynamictype.internal;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.User;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.plugin.eventtimecalculator.DurationFunctions;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbfile.tests.FileOperatorTest.MyFileIO;
import org.rapla.test.util.DefaultPermissionControllerSupport;
import org.rapla.test.util.RaplaTestCase;

import java.util.*;

@RunWith(JUnit4.class)
public class ParsedTextTest 
{
    DynamicTypeImpl type1;
    DynamicTypeImpl type2 ;

    AttributeImpl attribute1;
    AttributeImpl attribute2;

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

        type1 = new DynamicTypeImpl();
        type1.setResolver(cache);
        type1.setOperator(operator);
        type1.setKey("test");
        type1.setId("id1");


        type2 = new DynamicTypeImpl();
        type2.setResolver(cache);
        type2.setOperator(operator);
        type2.setKey("type2");
        type2.setId("id2");

        attribute1 = new AttributeImpl();
        attribute1.setKey("a1");
        attribute1.setId("a1");
        attribute1.setType(AttributeType.CATEGORY);
        attribute1.setResolver(cache);
        attribute1.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, c1);
        type1.addAttribute(attribute1);


        attribute2 = new AttributeImpl();
        attribute2.setKey("SollStunden");
        attribute2.setId("sollstunden");
        attribute2.setType(AttributeType.INT);
        attribute2.setResolver(cache);
        type1.addAttribute(attribute2);

        cache.put(type1);

        Category constraint = (Category) attribute1.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
        Assert.assertEquals(c1,constraint);
        type1.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{a1}");

    }



    @Test
    public void testDurationAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{name(a1,\"de\")}";
        type1.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type1.newClassification();
        classification.setValueForAttribute(attribute1, c2);
        final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController, new HashMap<>(),user, Collections.singletonList(classification));
        final ParsedText parsedAnnotation = type1.getParsedAnnotation(annoName);
        final String formatName = parsedAnnotation.formatName(evalContext);

        Assert.assertEquals("Welt", formatName);
        Assert.assertEquals(annotationContent, type1.getAnnotation(annoName));
    }

    @Test
    public void testCategoryAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{name(a1,\"de\")}";
        type1.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type1.newClassification();
        classification.setValueForAttribute(attribute1, c2);
        final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController,new HashMap<>(),user, Collections.singletonList(classification));
        final ParsedText parsedAnnotation = type1.getParsedAnnotation(annoName);
        final String formatName = parsedAnnotation.formatName(evalContext);
        
        Assert.assertEquals("Welt", formatName);
        Assert.assertEquals(annotationContent, type1.getAnnotation(annoName));
    }

    @Test
    public void testList() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->name(p)}";
        type1.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        final ParsedText parsedAnnotation = type1.getParsedAnnotation(annoName);
        final String externalRepresentation = type1.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController,new HashMap<>(),user,Collections.singletonList(classifications));
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
            type1.setAnnotation(annoName, annotationContent);
            Assert.fail("Unknown namespace error expected");
        }
        catch (IllegalAnnotationException ex)
        {
        }
        final String annotationContent = "{p->" + DurationFunctions.NAMESPACE + ":duration(p)}";
        type1.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        final String externalRepresentation = type1.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
    }

    @Test
    public void testBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";

        ParsedText parsedAnnotation;
        final String annotationContentWithoutParanth = "{p->name(attribute(p,\"a1\"),\"de\")}";
        {
            type1.setAnnotation(annoName, annotationContentWithoutParanth);
            parsedAnnotation = type1.getParsedAnnotation(annoName);
            final String externalRepresentation = type1.getAnnotation(annoName);
            Assert.assertEquals(annotationContentWithoutParanth, externalRepresentation);
        }
        {
            final String annotationContent = "{(p)->name(attribute(p,\"a1\"),\"de\")}";
            type1.setAnnotation(annoName, annotationContent);
            parsedAnnotation = type1.getParsedAnnotation(annoName);
            final String externalRepresentation = type1.getAnnotation(annoName);
            Assert.assertEquals(annotationContentWithoutParanth, externalRepresentation);
        }
        type1.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type1.newClassification();
        classification.setValueForAttribute(attribute1, c2);
        {
            final EvalContext evalContext = new EvalContext(locale, annoName,permissionController,new HashMap<>(), user, Collections.singletonList(classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("Welt", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController,new HashMap<>(), user, Collections.emptyList());
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("", formatName);
        }
    }
    
    @Test
    public void testMultiBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{(p,u)->concat(name(attribute(p,\"a1\"),\"de\"),name(u))}";
        type1.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        Locale locale = Locale.GERMANY;
        Classification classification = type1.newClassification();
        classification.setValueForAttribute(attribute1, c2);
        final ParsedText parsedAnnotation = type1.getParsedAnnotation(annoName);
        final String externalRepresentation = type1.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName,permissionController, new HashMap<>(),user, Collections.singletonList(classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("Welt", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController, new HashMap<>(),user,Arrays.asList(classification, classification));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("WeltWelt", formatName);
        }
    }

    @Test
    public void testInnerBoundAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(p,u->equals(substring(name(attribute(u,\"a1\"),\"de\"),0,4),\"Welt\"))}";
        type2.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        final ParsedText parsedAnnotation = type2.getParsedAnnotation(annoName);
        final String externalRepresentation = type2.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController, new HashMap<>(),user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welt, Welten", formatName);
    }
    
    @Test
    public void testInnerBoundWithOuterReferenceAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(p,u->equals(attribute(u,\"a1\"),attribute(index(p,1),\"a1\")))}";
        type2.setAnnotation(annoName, annotationContent);
        type1.setReadOnly();
        final ParsedText parsedAnnotation = type2.getParsedAnnotation(annoName);
        final String externalRepresentation = type2.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController,new HashMap<>(), user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welten", formatName);
    }

    @Test
    public void testCalculationPlugin() throws IllegalAnnotationException {
        final String annoName = EventTimeCalculatorPlugin.EVENTIME_CONDITION_ANNOTATION_NAME;
        final String annotationContent = "{p->org.rapla.eventtimecalculator:durationCompare(p,SollStunden)}";
        type1.setAnnotation( annoName, annotationContent);

        final ParsedText parsedAnnotation = type1.getParsedAnnotation(annoName);
        final String externalRepresentation = type1.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);

        type1.setReadOnly();

        Classification classification = type1.newClassification();
        classification.setValueForAttribute(attribute2, "1");
        Locale locale = Locale.GERMANY;

        ReservationImpl reservation = new ReservationImpl( new Date(), new Date());
        reservation.setClassification( classification ) ;

        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController,new HashMap<>(), user,Collections.singletonList(reservation));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("-60", formatName);

    }

    @Test
    public void testSortAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->sort(p,(u,v)->reverse(stringComparator(u,v)))}";
        type2.setAnnotation(annoName, annotationContent);
        type1.setAnnotation(annoName,"{substring(a1,0,5)}");
        final ParsedText parsedAnnotation = type2.getParsedAnnotation(annoName);
        final String externalRepresentation = type2.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        Locale locale = Locale.GERMANY;
        type1.setReadOnly();
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c4);
            classifications.add(classification);
        }
        {
            final EvalContext evalContext = new EvalContext(locale, annoName, permissionController, new HashMap<>(),user, Collections.singletonList(classifications));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("Welte, Welt, Rapla", formatName);
        }
        {
            final EvalContext evalContext = new EvalContext(locale, null, permissionController, new HashMap<>(),user, Collections.singletonList(classifications));
            final String formatName = parsedAnnotation.formatName(evalContext);
            Assert.assertEquals("Welten, Welt, Rapla", formatName);
        }
    }
    
    @Test
    public void testFilterSortAnnotation() throws IllegalAnnotationException
    {
        final String annoName = "myanno";
        final String annotationContent = "{p->filter(sort(p,(p,q)->reverse(stringComparator(p,q))),q->equals(substring(attribute(q,\"a1\"),0,3),\"Wel\"))}";
        type2.setAnnotation(annoName, annotationContent);
        final ParsedText parsedAnnotation = type2.getParsedAnnotation(annoName);
        final String externalRepresentation = type2.getAnnotation(annoName);
        Assert.assertEquals(annotationContent, externalRepresentation);
        type1.setReadOnly();
        Locale locale = Locale.GERMANY;
        List<Classification>classifications= new ArrayList<Classification>();
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c2);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c3);
            classifications.add(classification);
        }
        {
            Classification classification = type1.newClassification();
            classification.setValueForAttribute(attribute1, c4);
            classifications.add(classification);
        }
        final EvalContext evalContext = new EvalContext(locale,  annoName, permissionController,new HashMap<>(),user,Collections.singletonList(classifications));
        final String formatName = parsedAnnotation.formatName(evalContext);
        Assert.assertEquals("Welten, Welt", formatName);
    }
    
   
}
