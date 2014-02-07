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

package org.rapla.server.jsonrpc;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.framework.logger.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.JsonConstants;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.server.MapDeserializer;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.SqlDateDeserializer;
import com.google.gwtjsonrpc.server.SqlTimestampDeserializer;
import com.google.gwtjsonrpc.server.XsrfException;


/**
 * Basic HTTP servlet to forward JSON based RPC requests onto services.
 * <p>
 * Implementors of a JSON-RPC service should extend this servlet and implement
 * any interface(s) that extend from {@link RemoteJsonService}. Clients may
 * invoke methods declared in any implemented interface.
 * <p>
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
@SuppressWarnings("serial")
public class JsonServlet<CallType extends ActiveCall>  {
  /** Pattern that any safe JSON-in-script callback conforms to. */
  public static final Pattern SAFE_CALLBACK =
      Pattern.compile("^([A-Za-z0-9_$.]|\\[|\\])+$");

  private static final ThreadLocal<ActiveCall> perThreadCall;

  static {
    perThreadCall = new ThreadLocal<ActiveCall>();
  }

  /** Get the <code>ActiveCall</code> object for the current call. */
  @SuppressWarnings("unchecked")
  public static <CallType extends ActiveCall> CallType getCurrentCall() {
    return (CallType) perThreadCall.get();
  }

  /** Create a default GsonBuilder with some extra types defined. */
  public static GsonBuilder defaultGsonBuilder() {
    final GsonBuilder gb = new GsonBuilder();
    gb.registerTypeAdapter(java.util.Set.class,
        new InstanceCreator<java.util.Set<Object>>() {
          @Override
          public Set<Object> createInstance(final Type arg0) {
            return new HashSet<Object>();
          }
        });
    gb.registerTypeAdapter(java.util.Map.class, new MapDeserializer());
    gb.registerTypeAdapter(ReferenceHandler.class, new ReferenceHandlerDeserializer());
    gb.registerTypeAdapter(java.sql.Date.class, new SqlDateDeserializer());
    gb.registerTypeAdapter(java.sql.Timestamp.class,
        new SqlTimestampDeserializer());
    return gb;
  }

  static final Object[] NO_PARAMS = {};
  private static final String ENC = "UTF-8";

  private Map<String, MethodHandle> myMethods;
  private SignedToken xsrf;
  Logger logger;
  public  JsonServlet(final Logger logger,    final RemoteJsonService impl) throws ServletException {
    //super.init(config);
	 this.logger = logger;

    myMethods = methods(impl);
    if (myMethods.isEmpty()) {
      throw new ServletException("No service methods declared");
    }

    try {
      xsrf = createXsrfSignedToken();
    } catch (XsrfException e) {
      throw new ServletException("Cannot initialize XSRF", e);
    }
  }

  /**
   * Initialize the XSRF state for this service.
   * <p>
   * By default this method creates a unique XSRF key for this service. Service
   * implementors may wish to override this method to use a pooled instance that
   * relies upon a stable private key.
   *
   * @return new XSRF implementation. Null if the caller has overridden all
   *         relevant XSRF methods and is implementing their own XSRF protection
   *         algorithm.
   * @throws XsrfException the XSRF utility could not be created.
   */
  protected SignedToken createXsrfSignedToken() throws XsrfException {
    return new SignedToken(4 * 60 * 60 /* seconds */);
  }

  /** Create a GsonBuilder to parse a request or return a response. */
  protected GsonBuilder createGsonBuilder() {
    return defaultGsonBuilder();
  }

  /**
   * Verify the XSRF token submitted is valid.
   * <p>
   * By default this method validates the token, and refreshes it with a new
   * token for the currently authenticated user.
   *
   * @param call current RPC being processed.
   * @return true if the token was supplied and is valid; false otherwise.
   * @throws XsrfException the token could not be validated due to an error that
   *         the client cannot recover from.
   */
  protected boolean xsrfValidate(final CallType call) throws XsrfException {
    return call.xsrfValidate();
  }

  /**
   * Lookup a method implemented by this servlet.
   *
   * @param methodName name of the method.
   * @return the method handle; null if the method is not declared.
   */
  protected MethodHandle lookupMethod(final String methodName) {
    return myMethods.get(methodName);
  }

  /**
   * Create a new call structure for the active HTTP request.
   *
   * @param req the incoming request.
   * @param resp the response to return to the client.
   * @return the new call wrapping both.
   */
  @SuppressWarnings("unchecked")
  protected CallType createActiveCall(final HttpServletRequest req,
      final HttpServletResponse resp) {
    return (CallType) new ActiveCall(req, resp);
  }

  /** @return maximum size of a JSON request, in bytes */
  protected int maxRequestSize() {
    // Our default limit of 10 MB should be sufficient for nearly any
    // application. It takes a long time to format this on the client
    // or to upload it.
    //
    return 10 * 1024 * 1024;
  }

  /**
   * Invoked just before the service method is invoked.
   * <p>
   * Subclasses may override this method to perform additional checks, such as
   * per-method application level security validation. An override of this
   * method should take the following form:
   *
   * <pre>
   * protected void preInvoke(final CallType call) {
   *   super.preInvoke(call);
   *   if (call.isComplete()) {
   *     return;
   *   }
   *   // your logic here
   * }
   * </pre>
   * <p>
   * If either the {@link ActiveCall#onFailure(Throwable)} or
   * {@link ActiveCall#onInternalFailure(Throwable)} method is invoked with a
   * non-null exception argument the method call itself will be bypassed and the
   * error response will be returned to the client instead.
   *
   * @param call the current call information.
   */
  protected void preInvoke(final CallType call) {
  }

  public void service(final HttpServletRequest req,
      final HttpServletResponse resp, ServletContext servletContext) throws IOException {
    try {
      final CallType call = createActiveCall(req, resp);
      call.xsrf = xsrf;

      call.noCache();
//      if (!acceptJSON(call)) {
//        textError(call, SC_BAD_REQUEST, "Must Accept " + JsonConstants.JSON_TYPE);
//        return;
//      }

      perThreadCall.set(call);
      doService(call);

	
      final String out = formatResult(call);
      RPCServletUtils.writeResponse(servletContext, call.httpResponse,
          out, call.callback == null
              && out.length() > 256
              && RPCServletUtils.acceptsGzipEncoding(call.httpRequest));
    } finally {
      perThreadCall.set(null);
    }
  }

  private boolean acceptJSON(final CallType call) {
    final String accepts = call.httpRequest.getHeader("Accept");
    if (accepts == null) {
      // A really odd client, it didn't send us an accept header?
      //
      return false;
    }

    if (JsonConstants.JSON_TYPE.equals(accepts)) {
      // Common case, as our JSON client side code sets only this
      //
      return true;
    }

    // The browser may take JSON, but also other types. The popular
    // Opera browser will add other accept types to our AJAX requests
    // even though our AJAX handler wouldn't be able to actually use
    // the data. The common case for these is to start with our own
    // type, then others, so we special case it before we go through
    // the expense of splitting the Accepts header up.
    //
    if (accepts.startsWith(JsonConstants.JSON_TYPE + ",")) {
      return true;
    }
    final String[] parts = accepts.split("[ ,;][ ,;]*");
    for (final String p : parts) {
      if (JsonConstants.JSON_TYPE.equals(p)) {
        return true;
      }
    }

    // Assume the client is busted and won't take JSON back.
    //
    return false;
  }

  private void doService(final CallType call) throws IOException {
    try {
      try {
        if ("GET".equals(call.httpRequest.getMethod())) {
          parseGetRequest(call);
          if (!call.method.allowCrossSiteRequest()) {
            // Flat out refuse to service a method that requires XSRF
            // protection when requested over a GET method. Even if we
            // somehow managed to populate the XSRF token this is a very
            // insecure request against what must be a secure service method.
            //
            call.onFailure(new Exception(JsonConstants.ERROR_INVALID_XSRF));
            return;
          }

        } else if ("POST".equals(call.httpRequest.getMethod())) {
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
        call.onFailure(new Exception("Error parsing request", err));
        return;
      }
    } catch (NoSuchRemoteMethodException err) {
      call.httpResponse.setStatus(SC_NOT_FOUND);
      call.onFailure(new Exception("No such service method"));
      return;
    }

    if (call.callback != null
        && !SAFE_CALLBACK.matcher(call.callback).matches()) {
      call.httpResponse.setStatus(SC_BAD_REQUEST);
      call.onFailure(new Exception("Unsafe name in 'callback' property"));
      return;
    }

    try {
      call.xsrfValid = xsrfValidate(call);
    } catch (XsrfException e) {
      //call.httpRequest.getServletContext().log("Unexpected XSRF validation error", e);
      call.xsrfValid = false;
    }
    if (!call.method.allowCrossSiteRequest() && !call.requireXsrfValid()) {
      return;
    }

    preInvoke(call);
    if (!call.isComplete()) {
      call.method.invoke(call.params, call);
    }
  }

  private void parseGetRequest(final CallType call) {
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
       
        try 
        {
           	parsed = new JsonParser().parse(parameter);
        }
        catch (JsonParseException e)
        {
            final String p = new String(Base64.decodeBase64(params), "UTF-8");
           	parsed = new JsonParser().parse(p);
        }
		d.add("params", parsed);
      } catch (UnsupportedEncodingException e) {
        throw new JsonParseException("Cannot parse params", e);
      }

      try {
        final GsonBuilder gb = createGsonBuilder();
        gb.registerTypeAdapter(ActiveCall.class, //
            new CallDeserializer<CallType>(call, this));
        gb.create().fromJson(d, ActiveCall.class);
      } catch (JsonParseException err) {
        call.method = null;
        call.params = null;
        throw err;
      }

    } else { /* JSON-RPC 1.1 or REST API*/
      final Gson gs = createGsonBuilder().create();
      String classAndMethodName = (String) req.getAttribute("jsonmethod");
      String methodName;
      if ( classAndMethodName != null)
      {
     	  call.versionName = "jsonrpc";
    	  call.versionValue = new JsonPrimitive("2.0");
    	  int indexRole = classAndMethodName.indexOf( "/" );
    	  //String interfaceNameNonFinal = classAndMethodName.substring( 0, indexRole );
    	  methodName = classAndMethodName.substring( indexRole + 1 );
  	  }	
      else
  	  {
    	  methodName = req.getParameter("method");
    	  call.versionName = "version";
    	  call.versionValue = new JsonPrimitive("1.1");
    	  call.callback = req.getParameter("callback");
  	  }
      call.method = lookupMethod(methodName);
      if (call.method == null) {
        throw new NoSuchRemoteMethodException();
      }
      final Type[] paramTypes = call.method.getParamTypes();
      String[] paramNames = call.method.getParamNames();
      
      final Object[] r = new Object[paramTypes.length];
      for (int i = 0; i < r.length; i++) 
      {
    	  Type type = paramTypes[i];
    	  String name = paramNames[i];
    	  if (name == null || classAndMethodName == null )
    	  {
     		 name = "param" + i;
    	  }
    	  {
        	final String v = req.getParameter(name);
	        if (v == null) {
	          r[i] = null;
	        } else if (type == String.class) {
	        	r[i] = v;
	        } else if (type instanceof Class<?> && ((Class<?>) type).isPrimitive()) {
				  // Primitive type, use the JSON representation of that type.
				  //
				  r[i] = gs.fromJson(v, type);
	        } else {
				  // Assume it is like a java.sql.Timestamp or something and treat
				  // the value as JSON string.
				  //
	        	JsonElement parsed = new JsonParser().parse(v);
	        	r[i] = gs.fromJson(parsed, type);
			}
         }
      }
      call.params = r;
      
    }
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
        return d.decode(ByteBuffer.wrap(body)).toString();
      } catch (CharacterCodingException e) {
        throw new JsonParseException("Invalid Request Not UTF-8", e);
      }
    } finally {
      in.close();
    }
  }

  private void parsePostRequest(final CallType call)
      throws UnsupportedEncodingException, IOException {
    try {
      final GsonBuilder gb = createGsonBuilder();
      gb.registerTypeAdapter(ActiveCall.class, new CallDeserializer<CallType>(
          call, this));
      gb.create().fromJson(readBody(call), ActiveCall.class);
    } catch (JsonParseException err) {
      call.method = null;
      call.params = null;
      throw err;
    }
  }

  private String formatResult(final ActiveCall call)
      throws UnsupportedEncodingException, IOException {
    final GsonBuilder gb = createGsonBuilder();
    gb.registerTypeAdapter(call.getClass(), new JsonSerializer<ActiveCall>() {
      @Override
      public JsonElement serialize(final ActiveCall src, final Type typeOfSrc,
          final JsonSerializationContext context) {
    	  if (call.internalFailure != null) {
    	        final String msg = "Error in " + call.method.getName();
    	        logger.error(msg, call.internalFailure);
    	  }

    	  
    	Throwable failure = src.externalFailure != null ? src.externalFailure : src.internalFailure;
		if (call.callback != null) {
          if (failure != null) {
            return new JsonNull();
          }
          return context.serialize(src.result);
        }

        final JsonObject r = new JsonObject();
        r.add(src.versionName, src.versionValue);
        if (src.id != null) {
          r.add("id", src.id);
        }
        if (src.xsrfKeyOut != null) {
          r.addProperty("xsrfKey", src.xsrfKeyOut);
        }
        if (failure != null) {
          final JsonObject error = getError(src, failure);
          r.add("error", error);
        } else {
          r.add("result", context.serialize(src.result));
        }
        return r;
      }

	public JsonObject getError(final ActiveCall src, Throwable failure) {
		final JsonObject error = new JsonObject();
          String message = failure.getMessage();
          if ( message == null)
          {
        	  message = failure.toString();
          }
		  if ("jsonrpc".equals(src.versionName)) {
            final int code = to2_0ErrorCode(src);
            error.addProperty("code", code);
            error.addProperty("message", message);
			JsonArray stackTrace = new JsonArray();
			for ( StackTraceElement el: failure.getStackTrace())
			{
				stackTrace.add( new JsonPrimitive(el.toString()));
			}
            error.add("data", stackTrace);
          } else {
            error.addProperty("name", "JSONRPCError");
            error.addProperty("code", 999);
            error.addProperty("message", message);
          }
		return error;
	}
    });

    final StringWriter o = new StringWriter();
    if (call.callback != null) {
      o.write(call.callback);
      o.write("(");
    }
    Gson create = gb.create();
	create.toJson(call, o);
    if (call.callback != null) {
      o.write(");");
    }
    o.close();
    return o.toString();
  }

  private int to2_0ErrorCode(final ActiveCall src) {
    final Throwable e = src.externalFailure;
    final Throwable i = src.internalFailure;
    if (e instanceof NoSuchRemoteMethodException
        || i instanceof NoSuchRemoteMethodException) {
      return -32601 /* Method not found. */;
    }
    if (e instanceof IllegalArgumentException
            || i instanceof IllegalArgumentException) {
          return -32602 /* Invalid paramters. */;
        }
    if (e instanceof JsonParseException || i instanceof JsonParseException) {
      return -32700 /* Parse error. */;
    }

    return -32603 /* Internal error. */;
  }

  private static void textError(final ActiveCall call, final int status,
      final String message) throws IOException {
    final HttpServletResponse r = call.httpResponse;
    r.setStatus(status);
    r.setContentType("text/plain; charset=" + ENC);

    final Writer w = new OutputStreamWriter(r.getOutputStream(), ENC);
    try {
      w.write(message);
    } finally {
      w.close();
    }
  }

  private static Map<String, MethodHandle> methods(final RemoteJsonService impl) {
    final Class<? extends RemoteJsonService> d = findInterface(impl.getClass());
    if (d == null) {
      return Collections.<String, MethodHandle> emptyMap();
    }

    final Map<String, MethodHandle> r = new HashMap<String, MethodHandle>();
    for (final Method m : d.getMethods()) {
      if (!Modifier.isPublic(m.getModifiers())) {
        continue;
      }

      if (m.getReturnType() != Void.TYPE) {
        continue;
      }

      final Class<?>[] params = m.getParameterTypes();
      if (params.length < 1) {
        continue;
      }

      if (!params[params.length - 1].isAssignableFrom(AsyncCallback.class)) {
        continue;
      }

      final MethodHandle h = new MethodHandle(impl, m);
      r.put(h.getName(), h);
    }
    return Collections.unmodifiableMap(r);
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends RemoteJsonService> findInterface(Class<?> c) {
    while (c != null) {
      if (c.isInterface() && RemoteJsonService.class.isAssignableFrom(c)) {
        return (Class<RemoteJsonService>) c;
      }
      for (final Class<?> i : c.getInterfaces()) {
        final Class<? extends RemoteJsonService> r = findInterface(i);
        if (r != null) {
          return r;
        }
      }
      c = c.getSuperclass();
    }
    return null;
  }
}
