package org.rapla.rest;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.NameBinding;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Experimatal support of the PATCH method
 * <ul>
 * <li>A PATCH method must contain one post param, the patch body.</li>
 * <li>Only one PATCH method is allowed per resource.</li>
 * <li>if you annotate a method with PATCH the resource must also contain a matching method with GET
 * A get method is matching if its return type is assignable to the post param of the patch body
 * and its paths and pathParmeters matches the PATCH method.
 * </li>
 * <li>all queryParams, headerParams and pathParams of the matching GET method must be string or primitive</li>
 * <li>context params and other injected params are not supported in the matching GET method</li>
 * </ul>
 */

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("PATCH")
@Documented
@NameBinding
public @interface PATCH {
}

