// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.rapla.rest.gwtjsonrpc.server;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.DependencyException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.JSONParserWrapper;
import org.rapla.rest.gwtjsonrpc.common.JsonConstants;
import org.rapla.rest.jsonpatch.mergepatch.server.JsonMergePatch;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

/**
 * Forward JSON based RPC requests onto services.
 * <b>JSON-RPC 1.1</b><br>
 * Calling conventions match the JSON-RPC 1.1 working draft from 7 August 2006
 * (<a href="http://json-rpc.org/wd/JSON-RPC-1-1-WD-20060807.html">draft</a>).
 * Only positional parameters are supported.
 * <p>
 * <b>JSON-RPC 2.0</b><br>
 * Calling conventions match the JSON-RPC 2.0 specification.
 * <p>
 * When supported by the browser/client, the "gzip" encoding is used to compress
 * the resulting JSON, reducing transfer time for the response data.
 */
public class JsonServlet {
    public final static String JSON_METHOD = "method";

    static final Object[] NO_PARAMS = {};
    Class class1;
    private Map<String, MethodHandle> myMethods;
    Logger logger;

    public JsonServlet(final Logger logger, final Class class1) throws RaplaException {
        this.class1 = class1;
        this.logger = logger;
        myMethods = methods(class1);
        if (myMethods.isEmpty()) {
            throw new RaplaException("No public service methods declared in " + class1 + " Did you forget the javax.jws.WebService annotation?");
        }
    }

    public Class getInterfaceClass() {
        return class1;
    }

    /** Create a GsonBuilder to parse a request or return a response. */
    protected GsonBuilder createGsonBuilder() {
        return JSONParserWrapper.defaultGsonBuilder();
    }

    /**
     * Lookup a method implemented by this servlet.
     * 
     * @param methodName
     *            name of the method.
     * @return the method handle; null if the method is not declared.
     */
    protected MethodHandle lookupMethod(final String methodName) {
        return myMethods.get(methodName);
    }

    /** @return maximum size of a JSON request, in bytes */
    protected int maxRequestSize() {
        // Our default limit of 100 MB should be sufficient for nearly any
        // application. It takes a long time to format this on the client
        // or to upload it.
        //
        return 100 * 1024 * 1024;
    }

    public void service(final HttpServletRequest req, final HttpServletResponse resp, ServletContext servletContext, final Object service) throws IOException {
        ActiveCall call = new ActiveCall(req, resp);

        call.noCache();
        // if (!acceptJSON(call)) {
        // textError(call, SC_BAD_REQUEST, "Must Accept " +
        // JsonConstants.JSON_TYPE);
        // return;
        // }

        boolean isPatch = req.getMethod().equals("PATCH");
        doService(service, call);
        if (  isPatch && !call.hasFailed())
        {
            Object result = call.result;
            call = new ActiveCall(req, resp);
            try
            {
                final Gson gs = createGsonBuilder().create();
                JsonElement unpatchedObject = gs.toJsonTree(result);
                String patchBody = readBody(call);
                JsonElement patchElement = new JsonParser().parse( patchBody);
                final JsonMergePatch patch = JsonMergePatch.fromJson(patchElement);
                final JsonElement patchedObject = patch.apply(unpatchedObject);
                Object patchMethod = req.getAttribute("patchMethod");
                if ( patchMethod == null )
                {
                    throw new RaplaException("request attribute patchMethod or patchParameter is missing.");
                }
                req.setAttribute("method", patchMethod);
                String patchedObjectToString =gs.toJson( patchedObject);
                req.setAttribute("postBody", patchedObjectToString);
                doService(service, call);
            } 
            catch (Exception ex)
            {
                call.externalFailure = ex;
            }
        }
        final String out = formatResult(call);
        if (class1 != null  && call.method != null)
        {
            String childLoggerName = class1.getName() + "." + call.method.getName() + ".result";
            Logger childLogger = logger.getChildLogger(childLoggerName);
            if ( childLogger.isDebugEnabled() )
            {
                childLogger.debug(  out);
            }
        }
        RPCServletUtils.writeResponse(servletContext, call.httpResponse, out, out.length() > 256 && RPCServletUtils.acceptsGzipEncoding(call.httpRequest));
    }

    public void serviceError(final HttpServletRequest req, final HttpServletResponse resp, ServletContext servletContext, final Throwable ex) throws IOException {
        final ActiveCall call = new ActiveCall(req, resp);
        call.versionName = "jsonrpc";
        call.versionValue = new JsonPrimitive("2.0");
        call.noCache();
        call.onInternalFailure(ex);
        final String out = formatResult(call);
        RPCServletUtils.writeResponse(servletContext, call.httpResponse, out, out.length() > 256 && RPCServletUtils.acceptsGzipEncoding(call.httpRequest));
    }

    // private boolean acceptJSON(final CallType call) {
    // final String accepts = call.httpRequest.getHeader("Accept");
    // if (accepts == null) {
    // // A really odd client, it didn't send us an accept header?
    // //
    // return false;
    // }
    //
    // if (JsonConstants.JSON_TYPE.equals(accepts)) {
    // // Common case, as our JSON client side code sets only this
    // //
    // return true;
    // }
    //
    // // The browser may take JSON, but also other types. The popular
    // // Opera browser will add other accept types to our AJAX requests
    // // even though our AJAX handler wouldn't be able to actually use
    // // the data. The common case for these is to start with our own
    // // type, then others, so we special case it before we go through
    // // the expense of splitting the Accepts header up.
    // //
    // if (accepts.startsWith(JsonConstants.JSON_TYPE + ",")) {
    // return true;
    // }
    // final String[] parts = accepts.split("[ ,;][ ,;]*");
    // for (final String p : parts) {
    // if (JsonConstants.JSON_TYPE.equals(p)) {
    // return true;
    // }
    // }
    //
    // // Assume the client is busted and won't take JSON back.
    // //
    // return false;
    // }

    private void doService(final Object service, final ActiveCall call) throws IOException {
        try {
            try {
                String httpMethod = call.httpRequest.getMethod();
                if ("GET".equals(httpMethod) || "PATCH".equals(httpMethod)) {
                    parseGetRequest(call);
                } else if ("POST".equals(httpMethod)) {
                    parsePostRequest(call);

                } else {
                    call.httpResponse.setStatus(SC_BAD_REQUEST);
                    call.onFailure(new Exception("Unsupported HTTP method"));
                    return;
                }
            } catch (JsonParseException err) {
                if (err.getCause() instanceof NoSuchRemoteMethodException) {
                    // GSON seems to catch our own exception and wrap it...
                    //
                    throw (NoSuchRemoteMethodException) err.getCause();
                }
                call.httpResponse.setStatus(SC_BAD_REQUEST);
                call.onFailure(new Exception("Error parsing request " + err.getMessage(), err));
                return;
            }
        } catch (NoSuchRemoteMethodException err) {
            call.httpResponse.setStatus(SC_NOT_FOUND);
            call.onFailure(new Exception("No such service method: " + err.getMessage())  );
            return;
        }

        if (!call.isComplete()) {
            call.method.invoke(service, call.params, call);
        }
    }

    private void parseGetRequest(final ActiveCall call) {
        final HttpServletRequest req = call.httpRequest;
        
        if ("2.0".equals(req.getParameter("jsonrpc"))) {
            final JsonObject d = new JsonObject();
            d.addProperty("jsonrpc", "2.0");
            d.addProperty("method", req.getParameter("method"));
            d.addProperty("id", req.getParameter("id"));
            try {
                String parameter = req.getParameter("params");
                final byte[] params = parameter.getBytes("ISO-8859-1");
                JsonElement parsed;
                try {
                    parsed = new JsonParser().parse(parameter);
                } catch (JsonParseException e) {
                    final String p = new String(Base64.decodeBase64(params), "UTF-8");
                    parsed = new JsonParser().parse(p);
                }
                d.add("params", parsed);
            } catch (UnsupportedEncodingException e) {
                throw new JsonParseException("Cannot parse params", e);
            }

            try {
                final GsonBuilder gb = createGsonBuilder();
                gb.registerTypeAdapter(ActiveCall.class, new CallDeserializer(call, this));
                gb.create().fromJson(d, ActiveCall.class);
            } catch (JsonParseException err) {
                call.method = null;
                call.params = null;
                throw err;
            }
        } else { /* JSON-RPC 1.1 or GET REST API */
            String body = (String)req.getAttribute("postBody");
            mapRequestToCall(call, req, body);
        }
        
        String childLoggerName = class1.getName() + "." + call.method.getName() + ".arguments";
        Logger childLogger = logger.getChildLogger(childLoggerName);
        if ( childLogger.isDebugEnabled() )
        {
            childLogger.debug(  req.getQueryString());
        }        

    }

    public void mapRequestToCall(final ActiveCall call, final HttpServletRequest req, String body) {
        final Gson gs = createGsonBuilder().create();
        String methodName = (String) req.getAttribute(JSON_METHOD);
        if (methodName != null) {
            call.versionName = "jsonrpc";
            call.versionValue = new JsonPrimitive("2.0");
        } else {
            methodName = req.getParameter("method");
            call.versionName = "version";
            call.versionValue = new JsonPrimitive("1.1");
        }
        call.method = lookupMethod(methodName);
        if (call.method == null) {
            throw new NoSuchRemoteMethodException(getInterfaceClass() + "."  + methodName);
        }
        final Type[] paramTypes = call.method.getParamTypes();
        String[] paramNames = call.method.getParamNames();

        final Object[] r = new Object[paramTypes.length];
        for (int i = 0; i < r.length; i++) {
            Type type = paramTypes[i];
            String name = paramNames[i];
            if (name == null && !call.versionName.equals("jsonrpc")) {
                name = "param" + i;
            }
            {
                // First search in the request attributes
                Object attribute = req.getAttribute(name);
                Object paramValue;
                if ( attribute != null)
                {
                    paramValue =attribute;
                    Class attributeClass = attribute.getClass();
                    // we try to convert string and jsonelements to the parameter type (if the parameter type is not string or jsonelement)
                    if ( attributeClass.equals(String.class) && !type.equals(String.class) )
                    {
                        JsonParser parser = new JsonParser();
                        JsonElement parsed = parser.parse((String)attribute);
                        paramValue = gs.fromJson(parsed, type);
                        
                    } 
                    else if (JsonElement.class.isAssignableFrom(attributeClass) && !type.equals(JsonElement.class))
                    {
                        JsonElement parsed = (JsonElement) attribute;
                        paramValue = gs.fromJson(parsed, type);
                    }
                    else
                    {
                        paramValue =attribute;
                    }
                }
                // then in request parameters
                else 
                {
                    String v = null;
                    v = req.getParameter(name);
                    // if not found in request use body
                    if ( v == null && body != null && !body.isEmpty())
                    {
                        v = body;
                    }
                    if (v == null) {
                        paramValue = null;
                    } else if (type == String.class) {
                        paramValue = v;
                    } else if (type == Date.class) {
                        // special case for handling date parameters with the
                        // ':' char i it
                        try {
                            paramValue = SerializableDateTimeFormat.INSTANCE.parseTimestamp(v);
                        } catch (ParseDateException e) {
                            throw new JsonSyntaxException(v, e);
                        }
                    } else if (type instanceof Class<?> && ((Class<?>) type).isPrimitive()) {
                        // Primitive type, use the JSON representation of that
                        // type.
                        //
                        paramValue = gs.fromJson(v, type);
                    } else {
                        // Assume it is like a java.sql.Timestamp or something
                        // and treat
                        // the value as JSON string.
                        //
                        JsonParser parser = new JsonParser();
                        JsonElement parsed = parser.parse(v);
                        paramValue = gs.fromJson(parsed, type);
                    }
                }
                r[i] = paramValue;
            }
        }
        call.params = r;
    }

    private static boolean isBodyJson(final ActiveCall call) {
        String type = call.httpRequest.getContentType();
        if (type == null) {
            return false;
        }
        int semi = type.indexOf(';');
        if (semi >= 0) {
            type = type.substring(0, semi).trim();
        }
        return JsonConstants.JSON_TYPE.equals(type);
    }

    private static boolean isBodyUTF8(final ActiveCall call) {
        String enc = call.httpRequest.getCharacterEncoding();
        if (enc == null) {
            enc = "";
        }
        return enc.toLowerCase().contains(JsonConstants.JSON_ENC.toLowerCase());
    }

    private String readBody(final ActiveCall call) throws IOException {
        if (!isBodyJson(call)) {
            throw new JsonParseException("Invalid Request Content-Type");
        }
        if (!isBodyUTF8(call)) {
            throw new JsonParseException("Invalid Request Character-Encoding");
        }

        final int len = call.httpRequest.getContentLength();
        if (len < 0) {
            throw new JsonParseException("Invalid Request Content-Length");
        }
        if (len == 0) {
            throw new JsonParseException("Invalid Request POST Body Required");
        }
        if (len > maxRequestSize()) {
            throw new JsonParseException("Invalid Request POST Body Too Large");
        }

        final InputStream in = call.httpRequest.getInputStream();
        if (in == null) {
            throw new JsonParseException("Invalid Request POST Body Required");
        }

        try {
            final byte[] body = new byte[len];
            int off = 0;
            while (off < len) {
                final int n = in.read(body, off, len - off);
                if (n <= 0) {
                    throw new JsonParseException("Invalid Request Incomplete Body");
                }
                off += n;
            }

            final CharsetDecoder d = Charset.forName(JsonConstants.JSON_ENC).newDecoder();
            d.onMalformedInput(CodingErrorAction.REPORT);
            d.onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                ByteBuffer wrap = ByteBuffer.wrap(body);
                CharBuffer decode = d.decode(wrap);
                return decode.toString();
            } catch (CharacterCodingException e) {
                throw new JsonParseException("Invalid Request Not UTF-8", e);
            }
        } finally {
            in.close();
        }
    }

    private void parsePostRequest(final ActiveCall call) throws UnsupportedEncodingException, IOException {
        HttpServletRequest request = call.httpRequest;
        String postBody = readBody(call);
        parseRequest(call, request, postBody);
        
    }

    private void parseRequest(final ActiveCall call, HttpServletRequest request, String postBody) {
    
        String attribute = (String)request.getAttribute(JSON_METHOD);
        try
        {
            final GsonBuilder gb = createGsonBuilder();
            if ( attribute != null)
            {
                mapRequestToCall(call, request, postBody);
            }
            else
            {
                gb.registerTypeAdapter(ActiveCall.class, new CallDeserializer(call, this));
                Gson mapper = gb.create();
                mapper.fromJson(postBody, ActiveCall.class);
            }
            String childLoggerName = class1.getName() + "." + call.method.getName() + ".arguments";
            Logger childLogger = logger.getChildLogger(childLoggerName);
            if ( childLogger.isDebugEnabled() )
            {
                childLogger.debug(  postBody );
            }
        } catch (JsonParseException err) {
            call.method = null;
            call.params = null;
            throw err;
        }
    }

    private String formatResult(final ActiveCall call) throws UnsupportedEncodingException, IOException {
        final GsonBuilder gb = createGsonBuilder();
       
        gb.registerTypeAdapter(call.getClass(), new JsonSerializer<ActiveCall>() {
            @Override
            public JsonElement serialize(final ActiveCall src, final Type typeOfSrc, final JsonSerializationContext context) {
                if (call.externalFailure != null) {
                    final String msg;
                    if (call.method != null) {
                        msg = "Error  in " + call.method.getName();
                    } else {
                        msg = "Error";
                    }
                    logger.error(msg, call.externalFailure);
                }

                Throwable failure = src.externalFailure != null ? src.externalFailure : src.internalFailure;
                Object result = src.result;
                if (result instanceof FutureResult) {
                    try {
                        result = ((FutureResult) result).get();
                    } catch (Exception e) {
                        failure = e;
                    }
                }

                final JsonObject r = new JsonObject();
                if (src.versionName == null || src.versionValue == null)
                {
                    r.add("jsonrpc", new JsonPrimitive("2.0"));
                }
                else
                {
                    r.add(src.versionName, src.versionValue);
                }
                if (src.id != null) {
                    r.add("id", src.id);
                }
                if (failure != null) {
                    final int code = to2_0ErrorCode(src);
                    final JsonObject error = getError(src.versionName, code, failure, gb);
                    r.add("error", error);
                } else {
                    r.add("result", context.serialize(result));
                }
                return r;
            }

        });
        Gson create = gb.create();
        final StringWriter o = new StringWriter();
       
        create.toJson(call, o);
        o.close();
        String string = o.toString();
        return string;
    }

    private int to2_0ErrorCode(final ActiveCall src) {
        final Throwable e = src.externalFailure;
        final Throwable i = src.internalFailure;
        if (e instanceof RaplaSecurityException || i instanceof RaplaSecurityException) {
            return -32000 /* Security Exception. */;
        }
        if (e instanceof NoSuchRemoteMethodException || i instanceof NoSuchRemoteMethodException) {
            return -32601 /* Method not found. */;
        }
        if (e instanceof IllegalArgumentException || i instanceof IllegalArgumentException) {
            return -32602 /* Invalid paramters. */;
        }
        if (e instanceof JsonParseException || i instanceof JsonParseException) {
            return -32700 /* Parse error. */;
        }

        return -32603 /* Internal error. */;
    }

    // private static void textError(final ActiveCall call, final int status,
    // final String message) throws IOException {
    // final HttpServletResponse r = call.httpResponse;
    // r.setStatus(status);
    // r.setContentType("text/plain; charset=" + ENC);
    //
    // final Writer w = new OutputStreamWriter(r.getOutputStream(), ENC);
    // try {
    // w.write(message);
    // } finally {
    // w.close();
    // }
    // }

    public static JsonObject getError(String version, int code, Throwable failure, GsonBuilder gb) {
        final JsonObject error = new JsonObject();
        String message = failure.getMessage();
        if (message == null) {
            message = failure.toString();
        }
        Gson gson = gb.create();
        if ("jsonrpc".equals(version)) {
            error.addProperty("code", code);
            error.addProperty("message", message);

            JsonObject errorData = new JsonObject();
            errorData.addProperty("exception", failure.getClass().getName());

            // FIXME Replace with generic solution for exception param
            // serialization
            if (failure instanceof DependencyException) {
                JsonArray params = new JsonArray();
                for (String dep : ((DependencyException) failure).getDependencies()) {
                    params.add(new JsonPrimitive(dep));
                }
                errorData.add("params", params);
            }

            JsonArray stackTrace = new JsonArray();
            for (StackTraceElement el : failure.getStackTrace()) {
                JsonElement jsonRep = gson.toJsonTree(el);
                stackTrace.add( jsonRep);
            }
            errorData.add("stacktrace", stackTrace);

            error.add("data", errorData);
        } else {
            error.addProperty("name", "JSONRPCError");
            error.addProperty("code", 999);
            error.addProperty("message", message);
        }
        return error;
    }

    private static Map<String, MethodHandle> methods(Class class1) {
        final Class d = findInterface(class1);
        if (d == null) {
            return Collections.<String, MethodHandle> emptyMap();
        }

        final Map<String, MethodHandle> r = new HashMap<String, MethodHandle>();
        for (final Method m : d.getMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }

            final MethodHandle h = new MethodHandle(m);
            r.put(h.getName(), h);
        }
        return Collections.unmodifiableMap(r);
    }

    private static Class findInterface(Class<?> c) {
        while (c != null) {
            if ( c.getAnnotation(WebService.class) != null) {
                return c;
            }
            for (final Class<?> i : c.getInterfaces()) {
                final Class r = findInterface(i);
                if (r != null) {
                    return r;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
    
    public String toString()
    {
        if ( class1 != null)
        {
            return "JsonServlet for " + class1.getName();
        }
        else
        {
            return super.toString();
        }
    }

}
