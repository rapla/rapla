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

package com.google.gwtjsonrpc.common;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation permitting a cross-site request without the XSRF token.
 * <p>
 * This annotation should only be placed on {@link RemoteJsonService} methods
 * which are read-only (change no server state), which expose no private
 * information about the browser's user, and which the server wants to export
 * for general query use by any mash-up sort of application.
 * <p>
 * <b>Methods tagged with this annotation should only be on publicly known data,
 * e.g. data which is more readily available through sources other than this
 * based JSON service method.</b>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AllowCrossSiteRequest {
}
