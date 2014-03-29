// Copyright 2009 Google Inc.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Specify the json rpc protocol version and transport mechanism to be used for
 * a service.
 * <p>
 * Default is version 1.1 over HTTP POST.
 * <p>
 * <b>Note: if you use the generated (servlet), only version 1.1 over HTTP POST
 * is supported</b>.
 */
@Target(ElementType.TYPE)
public @interface RpcImpl {
  /**
   * JSON-RPC protocol versions.
   */
  public enum Version {
    /**
     * Version 1.1.
     *
     * @see <a
     *      href="http://groups.google.com/group/json-rpc/web/json-rpc-1-1-wd">Spec</a>
     */
    V1_1,
    /**
     * Version 2.0.
     *
     * @see <a
     *      href="http://groups.google.com/group/json-rpc/web/json-rpc-1-2-proposal">Spec</a>
     */
    V2_0
  }
  /**
   * Supported transport mechanisms.
   */
  public enum Transport {
    HTTP_POST, HTTP_GET
  }

  /**
   * Specify the JSON-RPC version. Default is version 1.1.
   */
  Version version() default Version.V1_1;

  /**
   * Specify the transport protocol used to make the RPC call. Default is HTTP
   * POST.
   */
  Transport transport() default Transport.HTTP_POST;
}
