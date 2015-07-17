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

package org.rapla.rest.gwtjsonrpc.common;

/** Shared constants between client and server implementations. */
public class JsonConstants {
  /** Proper Content-Type header value for JSON encoded data. */
  public static final String JSON_TYPE = "application/json";

  /** Character encoding preferred for JSON text. */
  public static final String JSON_ENC = "UTF-8";

  /** Request Content-Type header for JSON data. */
  public static final String JSON_REQ_CT = JSON_TYPE + "; charset=utf-8";

  /** Json-rpc 2.0: Proper Content-Type header value for JSON encoded data. */
  public static final String JSONRPC20_TYPE = "application/json-rpc";

  /** Json-rpc 2.0: Request Content-Type header for JSON data. */
  public static final String JSONRPC20_REQ_CT = JSON_TYPE + "; charset=utf-8";

  /** Json-rpc 2.0: Content types that we SHOULD accept as being valid */
  public static final String JSONRPC20_ACCEPT_CTS =
      JSON_TYPE + ",application/json,application/jsonrequest";

  /** Error message when xsrfKey in request is missing or invalid. */
  public static final String ERROR_INVALID_XSRF = "Invalid xsrfKey in request";
}
