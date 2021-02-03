package io.georocket.http;

import io.georocket.ServerAPIException;
import io.georocket.util.HttpException;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static io.georocket.util.ThrowableHelper.throwableToCode;
import static io.georocket.util.ThrowableHelper.throwableToMessage;

/**
 * Base interface for HTTP endpoints
 * @author Michel Kraemer
 * @since 1.2.0
 */
public interface Endpoint {
  /**
   * Create a router that handles HTTP requests for this endpoint
   * @param vertx the current Vert.x instance
   * @return the router
   */
  Router createRouter();

  /**
   * Get absolute data store path from request
   * @param context the current routing context
   * @return the absolute path (never null, default: "/")
   */
  static String getEndpointPath(RoutingContext context) {
    String path = context.normalisedPath();
    String routePath = context.mountPoint();
    String result = null;
    if (routePath.length() < path.length()) {
      result = path.substring(routePath.length());
    }
    if (result == null || result.isEmpty()) {
      return "/";
    }
    if (result.charAt(0) != '/') {
      result = "/" + result;
    }
    return result;
  }

  /**
   * Let the request fail by setting the correct HTTP error code and an error
   * description in the body
   * @param response the response object
   * @param throwable the cause of the error
   */
  static void fail(HttpServerResponse response, Throwable throwable) {
    response
      .setStatusCode(throwableToCode(throwable))
      .end(throwableToJsonResponse(throwable).toString());
  }

  /**
   * Generate the JSON error response for a failed request
   * @param throwable the cause of the error
   * @return the json string
   */
  static JsonObject throwableToJsonResponse(Throwable throwable) {
    String msg = throwableToMessage(throwable, "");

    try {
      return new JsonObject(msg);
    } catch (Exception e) {
      if (throwable instanceof ReplyException) {
        return ServerAPIException.toJson(ServerAPIException.GENERIC_ERROR, msg);
      }

      if (throwable instanceof HttpException) {
        return ServerAPIException.toJson(ServerAPIException.HTTP_ERROR, msg);
      }

      return ServerAPIException.toJson(ServerAPIException.GENERIC_ERROR, msg);
    }
  }
}
