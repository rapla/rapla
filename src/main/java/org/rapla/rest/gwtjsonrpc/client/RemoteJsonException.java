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

package org.rapla.rest.gwtjsonrpc.client;

import com.google.gwt.json.client.JSONValue;

/**
 * Exception given to
 * {@link org.rapla.rest.gwtjsonrpc.common.AsyncCallback#onFailure(Throwable)}.
 * <p>
 * This exception is used if the remote JSON server has returned a well-formed
 * JSON error response.
 */
public class RemoteJsonException extends Exception {
  private static final long serialVersionUID = 1L;
  private int code;
  private JSONValue data;

  /**
   * Construct a new exception representing a well formed JSON error response.
   *
   * @param message A String value that provides a short description of the
   *        error
   * @param code A number that indicates the actual error that occurred
   * @param data A JSON value instance that carries custom and
   *        application-specific error information
   */
  public RemoteJsonException(final String message, int code, JSONValue data) {
    super(message);
    this.code = code;
    this.data = data;
  }

  /**
   * Creates a new RemoteJsonException with code 999 and no data.
   *
   * @param message A String value that provides a short description of the
   *        error
   */
  public RemoteJsonException(final String message) {
    this(message, 999, null);
  }

  /**
   * Gets the error code.
   * <p>
   * Note that the JSON-RPC 1.1 draf does not define error codes yet.
   *
   * @return A number that indicates the actual error that occurred.
   */
  public int getCode() {
    return code;
  }

  /**
   * Same as getData.
   *
   * @return the error data, or <code>null</code> if none was specified
   * @see #getData
   */
  public JSONValue getError() {
    return data;
  }

  /**
   * Gets the extra error information supplied by the service.
   *
   * @return the error data, or <code>null</code> if none was specified
   */
  public JSONValue getData() {
    return data;
  }
}
