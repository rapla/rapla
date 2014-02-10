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


final class ServletCookieAccess {
  /**
   * Get the value of a cookie.
   *
   * @param name the name of the cookie.
   * @return the cookie's value; or null.
   */
  static String get(final String name) {
      return JsonServlet.<ActiveCall> getCurrentCall().getCookie(name);
  }

  /**
   * Get the text of a signed token which is stored in a cookie.
   * <p>
   * <b>Warning: This method does not validate the token.</b>
   * <p>
   * To validate the cookie hasn't been forged, use SignedToken.getCookieText.
   *
   * @param cookieName the name of the cookie to get the text from.
   * @return the signed text; null if the cookie is not set.
   */
  static String getTokenText(final String cookieName) {
    final String v = get(cookieName);
    if (v == null) {
      return null;
    }
    final int s = v.indexOf('$');
    return s >= 0 ? v.substring(s + 1) : null;
  }

  private ServletCookieAccess() {
  }
}
