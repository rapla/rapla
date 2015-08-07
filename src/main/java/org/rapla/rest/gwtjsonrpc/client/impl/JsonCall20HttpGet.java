// Copyright 2009 Gert Scholten
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

package org.rapla.rest.gwtjsonrpc.client.impl;

import org.rapla.rest.gwtjsonrpc.common.JsonConstants;

import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.URL;

/** JsonCall implementation for JsonRPC version 2.0 over HTTP POST */
public class JsonCall20HttpGet<T> extends JsonCall<T> {
  private String encodedRequestParams;

  public JsonCall20HttpGet(AbstractJsonProxy abstractJsonProxy,
      String methodName, String requestParams,
      ResultDeserializer<T> resultDeserializer) {
    super(abstractJsonProxy, methodName, requestParams, resultDeserializer);
    encodedRequestParams = URL.encodeQueryString(encodeBase64(requestParams));
  }

  @Override
  protected void send() {
    requestId = ++lastRequestId;
    final StringBuilder url = new StringBuilder(proxy.getServiceEntryPoint());
    url.append("?jsonrpc=2.0&method=").append(methodName);
    url.append("&params=").append(encodedRequestParams);
    url.append("&id=").append(requestId);

    final RequestBuilder rb;
    rb = new RequestBuilder(RequestBuilder.GET, url.toString());
    rb.setHeader("Content-Type", JsonConstants.JSONRPC20_REQ_CT);
    rb.setHeader("Accept", JsonConstants.JSONRPC20_ACCEPT_CTS);
    rb.setCallback(this);

    send(rb);
  }

  /**
   * Javascript base64 encoding implementation from.
   *
   * @see http://ecmanaut.googlecode.com/svn/trunk/lib/base64.js
   */
  private static native String encodeBase64(String data)
  /*-{
    var out = "", c1, c2, c3, e1, e2, e3, e4;
    for (var i = 0; i < data.length; ) {
      c1 = data.charCodeAt(i++);
      c2 = data.charCodeAt(i++);
      c3 = data.charCodeAt(i++);
      e1 = c1 >> 2;
      e2 = ((c1 & 3) << 4) + (c2 >> 4);
      e3 = ((c2 & 15) << 2) + (c3 >> 6);
      e4 = c3 & 63;
      if (isNaN(c2))
        e3 = e4 = 64;
      else if (isNaN(c3))
        e4 = 64;
      out += tab.charAt(e1) + tab.charAt(e2) + tab.charAt(e3) + tab.charAt(e4);
    }
    return out;
  }-*/;
}
