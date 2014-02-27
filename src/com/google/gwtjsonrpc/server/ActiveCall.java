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

package com.google.gwtjsonrpc.server;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonElement;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.JsonConstants;

/** An active RPC call. */
public class ActiveCall implements AsyncCallback<Object> {
  protected final HttpServletRequest httpRequest;
  protected final HttpServletResponse httpResponse;
  JsonElement id;
  String versionName;
  JsonElement versionValue;
  SignedToken xsrf;
  String xsrfKeyIn;
  String xsrfKeyOut;
  boolean xsrfValid;
  MethodHandle method;
  String callback;
  Object[] params;
  Object result;
  Throwable externalFailure;
  Throwable internalFailure;
  private Map<String, String> cookies;

  /**
   * Create a new call.
   *
   * @param req the request.
   * @param resp the response.
   */
  public ActiveCall(final HttpServletRequest req, final HttpServletResponse resp) {
    httpRequest = req;
    httpResponse = resp;
  }

  /**
   * Get the HTTP request that is attempting this RPC call.
   *
   * @return the original servlet HTTP request.
   */
  public HttpServletRequest getHttpServletRequest() {
    return httpRequest;
  }

  /**
   * Get the HTTP response that will be returned from this call.
   *
   * @return the original servlet HTTP response.
   */
  public HttpServletResponse getHttpServletResponse() {
    return httpResponse;
  }

  /**
   * Get the value of a cookie.
   *
   * @param name the name of the cookie.
   * @return the cookie's value; or null.
   */
  public String getCookie(final String name) {
    if (cookies == null) {
      cookies = new HashMap<String, String>();
      final Cookie[] all = httpRequest.getCookies();
      if (all != null) {
        for (final Cookie c : all) {
          cookies.put(c.getName(), c.getValue());
        }
      }
    }
    return cookies.get(name);
  }

  /**
   * Get the value of a cookie, verifying its signature.
   *
   * @param name the name of the cookie.
   * @return the cookie's value; or null.
   */
  public ValidToken getCookie(final String name, final SignedToken sig) {
    final String tokstr = getCookie(name);
    try {
      return sig.checkToken(tokstr, null);
    } catch (XsrfException e) {
      return null;
    }
  }

  /** Remove a cookie from the browser cookie store. */
  public void removeCookie(final String name) {
    final Cookie c = new Cookie(name, "");
    c.setMaxAge(0);
    c.setPath(getHttpServletRequest().getContextPath());
    httpResponse.addCookie(c);
  }

  /**
   * Set the value of a cookie.
   * <p>
   * The cookie is scope to the context path used by this web application.
   *
   * @param name name of the cookie.
   * @param value the value of the cookie.
   * @param age the age (in seconds) before it expires.
   */
  public void setCookie(final String name, final String value, final int age) {
    final Cookie c = new Cookie(name, value);
    c.setMaxAge(age);
    c.setPath(getHttpServletRequest().getContextPath());
    httpResponse.addCookie(c);
  }

  /**
   * Set the value of a cookie to a signed token.
   * <p>
   * The cookie value is actually set to the signed token which both includes
   * (and protects via an HMAC signature) <code>value</code>.s
   *
   * @param name name of the cookie.
   * @param value the data value of the cookie.
   * @param sig a signature to protect the cookie value.
   */
  public void setCookie(final String name, final String value,
      final SignedToken sig) {
    try {
      setCookie(name, sig.newToken(value), sig.getMaxAge());
    } catch (XsrfException e) {
    }
  }

  /**
   * Get the user specific token to protect per-user XSRF keys.
   * <p>
   * By default this method uses <code>getRemoteUser()</code>. Services may
   * override this method to acquire a different property of the request, such
   * as data from an HTTP cookie or an extended HTTP header.
   *
   * @return the user identity; null if the user is anonymous.
   */
  public String getUser() {
    return httpRequest.getRemoteUser();
  }

  /**
   * Get the method this request is asking to invoke.
   *
   * @return the requested method handle.
   */
  public MethodHandle getMethod() {
    return method;
  }

  /**
   * Get the actual parameter values to be supplied to the method.
   *
   * @return the parameter array; never null but may be 0-length if the method
   *         takes no parameters.
   */
  public Object[] getParams() {
    return params;
  }

  public void setXsrfSignedToken(final SignedToken t) {
    xsrf = t;
  }

  public final String getXsrfKeyIn() {
    return xsrfKeyIn;
  }

  public final void setXsrfKeyOut(final String out) {
    xsrfKeyOut = out;
  }

  public final String getXsrfKeyOut() {
    return xsrfKeyOut;
  }

  public final boolean isXsrfValid() {
    return xsrfValid;
  }

  public final boolean requireXsrfValid() {
    if (isXsrfValid()) {
      return true;
    } else {
      onFailure(new Exception(JsonConstants.ERROR_INVALID_XSRF));
      return false;
    }
  }

  /**
   * Verify the XSRF token submitted is valid.
   * <p>
   * By default this method validates the token, and refreshes it with a new
   * token for the currently authenticated user.
   *
   * @return true if the token was supplied and is valid; false otherwise.
   * @throws XsrfException the token could not be validated due to an error that
   *         the client cannot recover from.
   */
  public boolean xsrfValidate() throws XsrfException {
    final String username = getUser();
    final StringBuilder b = new StringBuilder();
    if (username != null) {
      b.append("user/");
      b.append(username);
    } else {
      b.append("anonymous");
    }
    final String userpath = b.toString();
    final ValidToken t = xsrf.checkToken(getXsrfKeyIn(), userpath);
    if (t == null || t.needsRefresh()) {
      setXsrfKeyOut(xsrf.newToken(userpath));
    }
    return t != null;
  }

  /**
   * @return true if this call has something to send to the client; false if the
   *         call still needs to be computed further in order to come up with a
   *         success return value or a failure
   */
  public final boolean isComplete() {
    return result != null || externalFailure != null || internalFailure != null;
  }

  @Override
  public final void onSuccess(final Object result) {
    this.result = result;
    this.externalFailure = null;
    this.internalFailure = null;
  }

  @Override
  public void onFailure(final Throwable error) {
    this.result = null;
    this.externalFailure = error;
    this.internalFailure = null;
  }

  public final void onInternalFailure(final Throwable error) {
    this.result = null;
    this.externalFailure = null;
    this.internalFailure = error;
  }

  /** Mark the response to be uncached by proxies and browsers. */
  public void noCache() {
    httpResponse.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    httpResponse.setHeader("Pragma", "no-cache");
    httpResponse.setHeader("Cache-Control", "no-cache, must-revalidate");
  }
}
