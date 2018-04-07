package org.rapla.rest.client.resteasy;

import org.rapla.rest.client.swing.JsonRemoteConnector;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ResteasyRemoteConnector implements JsonRemoteConnector {
    final Client client = null;//new org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder().useAsyncHttpEngine().build();

    public static JsonRemoteConnector createRestEasyClient() {
        return new ResteasyRemoteConnector();
    }

    @Override
    public CallResult sendCallWithString(String requestMethod, URL methodURL, String body, String authenticationToken, String contentType, Map<String, String> additionalHeaders) throws IOException {
        final WebTarget target;
        try {
            target = client.target(methodURL.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        Invocation.Builder builder = target.request(contentType).header("Authorization", authenticationToken);
        for (Map.Entry<String, String> additionalHeader : additionalHeaders.entrySet()) {
            builder = builder.header(additionalHeader.getKey(), additionalHeader.getValue());
        }
        builder.accept(contentType);
        final Response response;
        if (requestMethod.equals("GET")) {
            response = builder.get();
        } else {
            javax.ws.rs.client.Entity entity = javax.ws.rs.client.Entity.entity(body, contentType);
            final Future<Response> post = builder.async().post(entity);
            try {
                response = post.get();
            } catch (InterruptedException e) {
                throw new IOException(e);
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
            int responseCode5 = response.getStatus();
        }
        final int responseCode = response.getStatus();
        final String resultString = response.readEntity(String.class);
        return new CallResult(resultString, responseCode);
    }
}
