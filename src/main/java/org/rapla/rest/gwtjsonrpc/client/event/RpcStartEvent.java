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

import org.rapla.rest.gwtjsonrpc.client.JsonUtil;
import org.rapla.rest.gwtjsonrpc.client.impl.JsonCall;

/** Event received by {@link RpcStartHandler} */
public class RpcStartEvent extends BaseRpcEvent<RpcStartHandler> {
  private static Type<RpcStartHandler> TYPE;
  private static RpcStartEvent INSTANCE;

  /**
   * Fires a RpcStartEvent.
   * <p>
   * For internal use only.
   * 
   * @param eventData
   */
  @SuppressWarnings("rawtypes")
  public static void fire(Object eventData) {
    assert eventData instanceof JsonCall : "For internal use only";
    if (TYPE != null) { // If we have a TYPE, we have an INSTANCE.
      INSTANCE.call = (JsonCall) eventData;
      JsonUtil.fireEvent(INSTANCE);
    }
  }

  /**
   * Gets the type associated with this event.
   * 
   * @return returns the event type
   */
  public static Type<RpcStartHandler> getType() {
    if (TYPE == null) {
      TYPE = new Type<RpcStartHandler>();
      INSTANCE = new RpcStartEvent();
    }
    return TYPE;
  }

  private RpcStartEvent() {
    // Do nothing
  }

  @Override
  public Type<RpcStartHandler> getAssociatedType() {
    return TYPE;
  }

  @Override
  protected void dispatch(final RpcStartHandler handler) {
    handler.onRpcStart(this);
  }

  @Override
  protected void kill() {
    super.kill();
    call = null;
  }
}
