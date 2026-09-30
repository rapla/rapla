package org.rapla.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * The runtime GraphQL SDL (incl. the generated {@code <typeKey>Classification} types) —
 * the GraphQL pendant to the public {@code /v3/api-docs}. Printed in DEFINITION order:
 * the SPA builds its classification forms from the field order of this text, and the
 * generator writes attributes in the DynamicType's order. (Spring GraphQL's built-in
 * printer sorts fields alphabetically, which is why rapla serves this itself.)
 */
@HttpExchange("/api/graphql/schema")
public interface GraphQlSchemaService
{
    @GetExchange
    ResponseEntity<String> schema();
}
