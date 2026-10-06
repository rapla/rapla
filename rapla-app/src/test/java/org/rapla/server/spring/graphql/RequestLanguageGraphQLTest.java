package org.rapla.server.spring.graphql;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.Category;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.rapla.server.spring.web.OAuthTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PRD 124 OQ3 (variant A, ruling 2026-10-06): the schema is the same for everyone, the server delivers
 * names and view labels in the REQUESTING user's language (raplaLocale cookie, else user preference,
 * else server language).
 */
@Tag("e2e")
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class RequestLanguageGraphQLTest extends IsolatedDefaultDatasetTest
{
    private static final String TYPE_KEY = "i18nroom";
    private static final String CAT_KEY = "i18ncat";
    private static final String LIST_KEY = "i18nlist";
    private static final String EVENT_TYPE_KEY = "i18nevent";
    private static final String GROUP_KEY = "i18ngroup";

    private org.rapla.entities.dynamictype.Attribute listAttribute(String key, String en, String de, Category root) throws Exception
    {
        var a = facade.newAttribute(org.rapla.entities.dynamictype.AttributeType.CATEGORY);
        a.setKey(key);
        a.getName().setName("en", en);
        a.getName().setName("de", de);
        a.setConstraint(org.rapla.entities.dynamictype.ConstraintIds.KEY_ROOT_CATEGORY, root);
        return a;
    }

    @Autowired MockMvc mockMvc;
    @Autowired RaplaFacade facade;
    @Autowired RaplaLocale raplaLocale;
    @Autowired ViewCatalogService views;

    String token;

    @BeforeEach
    void seed() throws Exception
    {
        token = OAuthTestSupport.loginAs(mockMvc, "admin", "");
        if (java.util.Arrays.stream(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)).noneMatch(t -> TYPE_KEY.equals(t.getKey())))
        {
            Category root = facade.edit(facade.getSuperCategory());
            Category cat = facade.newCategory();
            cat.setKey(CAT_KEY);
            cat.getName().setName("en", "Winter term");
            cat.getName().setName("de", "Wintersemester");
            root.addCategory(cat);
            Category list = facade.newCategory();
            list.setKey(LIST_KEY);
            root.addCategory(list);
            for (String[] c : new String[][] {{"winter", "Winter term", "Wintersemester"}, {"summer", "Summer term", "Sommersemester"}})
            {
                Category child = facade.newCategory();
                child.setKey(c[0]);
                child.getName().setName("en", c[1]);
                child.getName().setName("de", c[2]);
                list.addCategory(child);
            }
            facade.store(root);
            Category storedList = facade.getSuperCategory().getCategory(LIST_KEY);

            Category groups = facade.edit(facade.getSuperCategory().getCategory(org.rapla.entities.domain.Permission.GROUP_CATEGORY_KEY));
            Category group = facade.newCategory();
            group.setKey(GROUP_KEY);
            group.getName().setName("en", "Lab staff");
            group.getName().setName("de", "Laborpersonal");
            groups.addCategory(group);
            facade.store(groups);

            DynamicType type = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
            type.setKey(TYPE_KEY);
            type.getName().setName("en", "Lecture hall");
            type.getName().setName("de", "Hörsaal");
            type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
            type.addAttribute(listAttribute("enum", "Term", "Semester", storedList));
            facade.store(type);

            DynamicType eventType = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            eventType.setKey(EVENT_TYPE_KEY);
            eventType.getName().setName("en", "Lecture");
            eventType.addAttribute(listAttribute("term", "Term", "Semester", storedList));
            eventType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{term}");
            facade.store(eventType);
            var classification = facade.getDynamicType(EVENT_TYPE_KEY).newClassification();
            classification.setValue("term", storedList.getCategory("winter"));
            var reservation = facade.newReservation(classification, facade.getUser("admin"));
            reservation.addAppointment(facade.newAppointmentWithUser(
                    java.time.LocalDateTime.of(2030, 1, 5, 10, 0), java.time.LocalDateTime.of(2030, 1, 5, 11, 0),
                    facade.getUser("admin")));
            facade.store(reservation);
        }
    }

    private String acceptLanguage;

    private JsonNode graphql(String query, String variables, String lang) throws Exception
    {
        var req = post("/api/graphql").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(java.util.Map.of("query", query)));
        if (acceptLanguage != null) req.header("Accept-Language", acceptLanguage);
        if (lang != null) req.cookie(new Cookie("raplaLocale", lang));
        return JsonMapper.builder().build()
                .readTree(mockMvc.perform(req).andReturn().getResponse().getContentAsString());
    }

    private String typeNameOf(String lang) throws Exception
    {
        JsonNode r = graphql("{ type(key: \"" + TYPE_KEY + "\") { name } }", null, lang);
        String n = r.at("/data/type/name").asString();
        if (n == null || n.isEmpty()) throw new AssertionError(r.toString());
        return n;
    }

    private String categoryName(String lang) throws Exception
    {
        JsonNode r = graphql("{ category(path: \"" + CAT_KEY + "\") { name } }", null, lang);
        return r.at("/data/category/name").asString();
    }

    @Test
    void typeAndCategoryNamesFollowTheRequestLanguage() throws Exception
    {
        assertEquals("Hörsaal", typeNameOf("de"));
        assertEquals("Lecture hall", typeNameOf("en"));
        assertEquals("Wintersemester", categoryName("de"));
        assertEquals("Winter term", categoryName("en"));
    }

    @Test
    void attributeNamesFollowTheRequestLanguageWithSdlSpelledKeys() throws Exception
    {
        String q = "{ type(key: \"" + TYPE_KEY + "\") { attributeNames { key name values { key name } } } }";
        for (String[] e : new String[][] {{"de", "Semester", "Wintersemester", "Sommersemester"}, {"en", "Term", "Winter term", "Summer term"}})
        {
            JsonNode names = graphql(q, null, e[0]).at("/data/type/attributeNames");
            JsonNode attr = null;
            for (JsonNode n : names) if ("enum_".equals(n.path("key").asString())) attr = n;
            assertNotNull(attr, "reserved attribute key 'enum' is spelled enum_ like the SDL field: " + names);
            assertEquals(e[1], attr.path("name").asString());
            assertEquals("winter", attr.at("/values/0/key").asString());
            assertEquals(e[2], attr.at("/values/0/name").asString());
            assertEquals(e[3], attr.at("/values/1/name").asString());
            for (JsonNode n : names) if ("name".equals(n.path("key").asString())) assertTrue(n.path("values").isNull());
        }
    }

    @Test
    void appointmentBlockNameFollowsTheRequestLanguage() throws Exception
    {
        String q = "{ appointmentBlocks(filter: {from: \"2030-01-05T00:00\", to: \"2030-01-06T00:00\", ownerIn: [\"" + facade.getUser("admin").getId() + "\"]}) { name } }";
        assertEquals("Wintersemester", graphql(q, null, "de").at("/data/appointmentBlocks/0/name").asString());
        assertEquals("Winter term", graphql(q, null, "en").at("/data/appointmentBlocks/0/name").asString());
    }

    @Test
    void browserLanguageDecidesWhenNothingIsStored() throws Exception
    {
        acceptLanguage = "de-DE,de;q=0.9,en;q=0.8";
        assertEquals("Hörsaal", typeNameOf(null));
        acceptLanguage = "xx";
        assertEquals("Lecture hall", typeNameOf(null));
        acceptLanguage = "xx,de;q=0.9";
        assertEquals("Hörsaal", typeNameOf(null));
        acceptLanguage = "de";
        assertEquals("Lecture hall", typeNameOf("en"));
    }

    private void withSystemLocale(String locale, org.rapla.facade.RaplaFacade f, Runnable body) throws Exception
    {
        org.rapla.entities.configuration.Preferences edit = f.edit(f.getSystemPreferences());
        edit.putEntry(org.rapla.framework.internal.AbstractRaplaLocale.LOCALE, locale);
        f.store(edit);
        try
        {
            body.run();
        }
        finally
        {
            org.rapla.entities.configuration.Preferences reset = f.edit(f.getSystemPreferences());
            reset.removeEntry(org.rapla.framework.internal.AbstractRaplaLocale.LOCALE.getId());
            try { f.store(reset); } catch (Exception e) { throw new IllegalStateException(e); }
        }
    }

    @Test
    void configuredServerLanguageBeatsTheBrowser() throws Exception
    {
        withSystemLocale("de_DE", facade, () -> {
            try
            {
                acceptLanguage = "en";
                assertEquals("Hörsaal", typeNameOf(null));
                assertEquals("Lecture hall", typeNameOf("en"));
            }
            catch (Exception e) { throw new IllegalStateException(e); }
        });
        acceptLanguage = "en";
        assertEquals("Lecture hall", typeNameOf(null));
    }

    @Test
    void userPreferenceBeatsTheBrowser() throws Exception
    {
        User admin = facade.getUser("admin");
        org.rapla.entities.configuration.Preferences edit = facade.edit(facade.getPreferences(admin));
        edit.putEntry(RaplaLocale.LANGUAGE_ENTRY, "de");
        facade.store(edit);
        try
        {
            acceptLanguage = "en";
            assertEquals("Hörsaal", typeNameOf(null));
        }
        finally
        {
            org.rapla.entities.configuration.Preferences reset = facade.edit(facade.getPreferences(admin));
            reset.removeEntry(RaplaLocale.LANGUAGE_ENTRY.getId());
            facade.store(reset);
        }
    }

    @Test
    void catalogueAndMeFollowTheSameResolution() throws Exception
    {
        for (String path : new String[] {"/api/locale/spa", "/api/auth/me"})
        {
            JsonNode r = JsonMapper.builder().build().readTree(mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                            .header("Authorization", "Bearer " + token).header("Accept-Language", "de"))
                    .andReturn().getResponse().getContentAsString());
            assertEquals("de", r.path("language").asString(), path);
        }
    }

    @Test
    void withoutCookieOrPreferenceTheServerLanguageApplies() throws Exception
    {
        org.rapla.entities.configuration.Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(RaplaLocale.LANGUAGE_ENTRY, "de");
        facade.store(edit);
        try
        {
            assertEquals("Hörsaal", typeNameOf(null));
        }
        finally
        {
            org.rapla.entities.configuration.Preferences reset = facade.edit(facade.getSystemPreferences());
            reset.removeEntry(RaplaLocale.LANGUAGE_ENTRY.getId());
            facade.store(reset);
        }
    }

    @Test
    void builtinViewTitleIsTranslatedInTheRequestLanguage() throws Exception
    {
        assertEquals("Termine", listedTitle("rapla_appointments", "de"));
        assertEquals("Appointments", listedTitle("rapla_appointments", "en"));
        JsonNode meta = viewMeta("rapla_appointments", "de");
        assertEquals("Termine", meta.path("title").asString());
        assertEquals("Termin|Termine", meta.path("rowLabel").asString());
        assertEquals("Von", meta.at("/columns/0/header").asString());
        JsonNode en = viewMeta("rapla_appointments", "en");
        assertEquals("Appointments", en.path("title").asString());
        assertEquals("From", en.at("/columns/0/header").asString());
    }

    @Test
    void storedViewWithoutPrefixKeepsItsTitle() throws Exception
    {
        User admin = facade.getUser("admin");
        String q = "query ausleihen($filter: ReservationFilter!) @view(title: \"Ausleihen\") { reservations(filter: $filter) { name } }";
        assertEquals(java.util.List.of(), views.saveView("ausleihen", q, true, java.util.List.of(), null, admin));
        assertEquals("Ausleihen", listedTitle("ausleihen", "en"));
        assertEquals("Ausleihen", listedTitle("ausleihen", "de"));
    }

    private String listedTitle(String name, String lang) throws Exception
    {
        JsonNode r = graphql("{ listViews { name title } }", null, lang);
        for (JsonNode v : r.at("/data/listViews")) if (name.equals(v.path("name").asString())) return v.path("title").asString();
        throw new AssertionError(r.toString());
    }

    private JsonNode viewMeta(String name, String lang) throws Exception
    {
        String body = "{\"operationName\":\"" + name + "\",\"query\":\"{__typename}\","
                + "\"extensions\":{\"storedView\":true},\"variables\":{}}";
        var req = post("/api/graphql").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (lang != null) req.cookie(new Cookie("raplaLocale", lang));
        JsonNode json = JsonMapper.builder().build()
                .readTree(mockMvc.perform(req).andReturn().getResponse().getContentAsString());
        return json.at("/extensions/view");
    }

    @Test
    void groupNamesFollowTheRequestLanguage() throws Exception
    {
        assertEquals("Laborpersonal", groupName("de"));
        assertEquals("Lab staff", groupName("en"));
    }

    private String groupName(String lang) throws Exception
    {
        JsonNode groups = graphql("{ groups { key name } }", null, lang).at("/data/groups");
        for (JsonNode g : groups)
        {
            if (GROUP_KEY.equals(g.at("/key").asString())) return g.at("/name").asString();
        }
        throw new AssertionError("group not listed: " + groups);
    }
}
