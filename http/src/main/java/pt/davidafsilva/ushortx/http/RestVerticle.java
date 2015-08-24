package pt.davidafsilva.ushortx.http;

import org.apache.commons.validator.routines.UrlValidator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * The REST API for our ushortx service
 *
 * @author David Silva
 */
public class RestVerticle extends AbstractVerticle {

  // the URL validator
  private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http", "https"});

  // the http server
  private HttpServer server;

  @Override
  public void start() throws Exception {
    // create the routing configuration
    final Router router = Router.router(vertx);

    // default handler
    router.route().handler(BodyHandler.create());
    // GET /<hash>
    router.get("/:hash").handler(this::redirectUrlRequest);
    // POST /s/<url>
    router.post("/s/:url").handler(this::shortenUrlRequest);

    // create the actual http server
    server = vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(config().getInteger("http_port", 8080));
  }

  /**
   * Redirects to the actual URL of the specified hash inside the GET request
   *
   * @param context the routing context of the request
   */
  private void redirectUrlRequest(final RoutingContext context) {
    // extract the hash
    final String hash = context.request().getParam("hash");
    // basic hash validation
    if (hash == null || hash.isEmpty()) {
      context.response().setStatusCode(400).end();
      return;
    }

    // query the persistence for the hash
    vertx.eventBus().send("ushortx-persistence-getUrl",
        // the request data
        new JsonObject().put("hash", hash),
        // the result callback
        result -> {
          if (result.succeeded()) {
            // extract the json data
            final JsonObject json = (JsonObject) result.result().body();

            // redirect to the url
            context.response()
                .setStatusCode(302)
                .putHeader("Location", json.getString("url"))
                .end();
          } else {
            // fail with a 404 - assume bad hash
            context.response().setStatusCode(404).end();
          }
        });
  }

  /**
   * Shortens an arbitrary URL specified inside the POST request
   *
   * @param context the routing context of the request
   */
  private void shortenUrlRequest(final RoutingContext context) {
    // extract the url
    final String url = context.request().getParam("url");
    // url validation
    if (url == null || !URL_VALIDATOR.isValid(url)) {
      context.response().setStatusCode(400).end();
      return;
    }

    // send the request or fail the request
    vertx.eventBus().send("ushortx-persistence-shortenerUrl",
        // the request data
        new JsonObject().put("url", url),
        // the result callback
        result -> {
          if (result.succeeded()) {
            // extract the json data
            final JsonObject json = (JsonObject) result.result().body();

            // write the response
            context.response().setStatusCode(200).write(
                new JsonObject()
                    .put("original", url)
                    .put("shortened", "http://" + context.request().localAddress().toString() +
                        "/" + json.getString("hash"))
                    .encode()
            ).end();
          } else {
            // fail with an internal error
            context.response().setStatusCode(500).end();
          }
        });
  }

  @Override
  public void stop() throws Exception {
    // stop the server
    server.close();
  }
}
