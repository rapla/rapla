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

package org.rapla.rest.gwtjsonrpc.client.event;

import org.rapla.rest.gwtjsonrpc.client.impl.JsonCall;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

/** Common event for {@link RpcStartEvent}, {@link RpcCompleteEvent}. */
public abstract class BaseRpcEvent<T extends EventHandler> extends GwtEvent<T> {
  JsonCall<?> call;

  /** @return the service instance the remote call occurred on. */
  public Object getService() {
    assertLive();
    return call.getProxy();
  }

  /** @return the service instance the remote call occurred on. */
  public ServiceDefTarget getServiceDefTarget() {
    assertLive();
    return call.getProxy();
  }

  /** @return the method name being invoked on the service. */
  public String getMethodName() {
    assertLive();
    return call.getMethodName();
  }
}
