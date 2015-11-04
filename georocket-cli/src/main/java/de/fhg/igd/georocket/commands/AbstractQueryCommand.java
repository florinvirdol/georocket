package de.fhg.igd.georocket.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Abstract base class for commands that need to export data
 * @author Michel Kraemer
 */
public abstract class AbstractQueryCommand extends AbstractGeoRocketCommand {
  private static Logger log = LoggerFactory.getLogger(AbstractQueryCommand.class);
  
  /**
   * Export using a search query and a layer
   * @param query the search query (may be null)
   * @param layer the layer to export (may be null)
   * @param out the writer to write the results to
   * @param handler the handler that should be called when all
   * chunks have been exported
   * @throws IOException if the query or the layer was invalid
   */
  protected void export(String query, String layer, PrintWriter out,
      Handler<Integer> handler) throws IOException {
    if (layer == null || layer.isEmpty()) {
      layer = "/";
    }
    if (!layer.endsWith("/")) {
      layer += "/";
    }
    if (!layer.startsWith("/")) {
      layer = "/" + layer;
    }
    
    String urlQuery = "";
    if (query != null && !query.isEmpty()) {
      urlQuery = "?search=" + URLEncoder.encode(query, "UTF-8");
    }
    
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest request = client.get(63074, "localhost", "/store" + layer + urlQuery);
    request.exceptionHandler(t -> {
      error(t.getMessage());
      log.error("Could not query store", t);
      client.close();
      handler.handle(1);
    });
    request.handler(response -> {
      if (response.statusCode() != 200) {
        error(response.statusMessage());
        client.close();
        handler.handle(1);
      } else {
        response.handler(buf -> {
          out.write(buf.toString());
        });
        response.endHandler(v -> {
          client.close();
          handler.handle(0);
        });
      }
    });
    request.end();
  }
}
