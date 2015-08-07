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

package org.rapla.rest.gwtjsonrpc.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Utility to handle writing JSON-RPC responses, possibly compressed. */
public class RPCServletUtils {
  public static boolean acceptsGzipEncoding(HttpServletRequest request) {
    String accepts = request.getHeader("Accept-Encoding");
    return accepts != null && accepts.indexOf("gzip") != -1;
  }

  public static void writeResponse(ServletContext ctx, HttpServletResponse res,
      String responseContent, boolean encodeWithGzip) throws IOException {
    byte[] data = responseContent.getBytes("UTF-8");
    if (encodeWithGzip) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream(data.length);
      GZIPOutputStream gz = new GZIPOutputStream(buf);
      try {
        gz.write(data);
        gz.finish();
        gz.flush();
        res.setHeader("Content-Encoding", "gzip");
        data = buf.toByteArray();
      } catch (IOException e) {
        ctx.log("Unable to compress response", e);
        res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      } finally {
        gz.close();
      }
    }

    res.setContentLength(data.length);
    res.setContentType("application/json; charset=utf-8");
    res.setStatus(HttpServletResponse.SC_OK);
    res.setHeader("Content-Disposition", "attachment");
    res.getOutputStream().write(data);
  }

  private RPCServletUtils() {
  }
}
