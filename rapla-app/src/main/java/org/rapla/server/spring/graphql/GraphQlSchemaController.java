package org.rapla.server.spring.graphql;

import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.idl.SchemaPrinter;
import org.rapla.rest.GraphQlSchemaService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** See {@link GraphQlSchemaService}. Replaces {@code spring.graphql.schema.printer}. */
@RestController
public class GraphQlSchemaController implements GraphQlSchemaService
{
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final HotSwappableGraphQlSource source;
    private final SchemaPrinter printer = new SchemaPrinter(
            SchemaPrinter.Options.defaultOptions().setComparators(GraphqlTypeComparatorRegistry.AS_IS_REGISTRY));

    public GraphQlSchemaController(HotSwappableGraphQlSource source)
    {
        this.source = source;
    }

    @Override
    public ResponseEntity<String> schema()
    {
        return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(printer.print(source.schema()));
    }
}
