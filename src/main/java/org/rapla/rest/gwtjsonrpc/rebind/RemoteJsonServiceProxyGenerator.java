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

package org.rapla.rest.gwtjsonrpc.rebind;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;

/**
 * Generates proxy implementations of RemoteJsonService.
 */
public class RemoteJsonServiceProxyGenerator extends Generator {
  @Override
  public String generate(final TreeLogger logger, final GeneratorContext ctx,
      final String requestedClass) throws UnableToCompleteException {

    final TypeOracle typeOracle = ctx.getTypeOracle();
    assert (typeOracle != null);

    final JClassType remoteService = typeOracle.findType(requestedClass);
    if (remoteService == null) {
      logger.log(TreeLogger.ERROR, "Unable to find metadata for type '"
          + requestedClass + "'", null);
      throw new UnableToCompleteException();
    }

//    WebService annotation = remoteService.getAnnotation( javax.jws.WebService.class);
//    if (annotation == null)
//    {
//    	return null;
//    }
    if (remoteService.isInterface() == null) {
      logger.log(TreeLogger.ERROR, remoteService.getQualifiedSourceName()
          + " is not an interface", null);
      throw new UnableToCompleteException();
    }

    ProxyCreator proxyCreator = new ProxyCreator(remoteService);

    TreeLogger proxyLogger =
        logger.branch(TreeLogger.DEBUG,
            "Generating client proxy for remote service interface '"
                + remoteService.getQualifiedSourceName() + "'", null);

    return proxyCreator.create(proxyLogger, ctx);
  }
  
}
