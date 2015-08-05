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


/**
 * Indicates the remote JSON server is not available.
 * <p>
 * Usually supplied to {@link org.rapla.rest.gwtjsonrpc.common.AsyncCallback#onFailure(Throwable)} when the
 * remote host isn't answering, such as if the HTTP server is restarting and has
 * temporarily stopped accepting new connections.
 */
@SuppressWarnings("serial")
public class ServerUnavailableException extends Exception {
  public static final String MESSAGE = "Server Unavailable";

  public ServerUnavailableException() {
    super(MESSAGE);
  }
}
