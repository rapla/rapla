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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonElement;
import com.google.gwtjsonrpc.common.AsyncCallback;

/** An active RPC call. */
public class ActiveCall implements AsyncCallback<Object> {
  protected final HttpServletRequest httpRequest;
  protected final HttpServletResponse httpResponse;
  JsonElement id;
  String versionName;
  JsonElement versionValue;
  MethodHandle method;
  String callback;
  Object[] params;
  Object result;
  Throwable externalFailure;
  Throwable internalFailure;
  
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
//  public boolean xsrfValidate() throws XsrfException {
//    final String username = getUser();
//    final StringBuilder b = new StringBuilder();
//    if (username != null) {
//      b.append("user/");
//      b.append(username);
//    } else {
//      b.append("anonymous");
//    }
//    final String userpath = b.toString();
//    final ValidToken t = xsrf.checkToken(getXsrfKeyIn(), userpath);
//    if (t == null || t.needsRefresh()) {
//      setXsrfKeyOut(xsrf.newToken(userpath));
//    }
//    return t != null;
//  }

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
