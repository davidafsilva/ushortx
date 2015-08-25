package pt.davidafsilva.ushortx.http;

import org.apache.commons.validator.routines.UrlValidator;

import java.util.Optional;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.Message;
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

  // the default salt value - should not be used!!!
  private static final String DEFAULT_SALT = "Please change me!! I'll make you a sandwich!";

  // the url format
  private static final String URL_REDIRECT_FORMAT = "http://%s/%s";

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
    final int port = config().getInteger("http_port", 8080);
    server = vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen();

    System.out.printf("http server listening at port %d%n", port);
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

    // reverse the hash
    final Optional<Long> id = Hash.reverse(config().getString("salt", DEFAULT_SALT), hash);
    if (!id.isPresent()) {
      // fail with a 404
      context.response().setStatusCode(404).end();
      return;
    }

    // query the persistence for the hash
    vertx.eventBus().send("ushortx-persistence-findById",
        // the request data
        new JsonObject().put("id", id.get()),
        // the result callback
        (AsyncResult<Message<JsonObject>> result) -> {
          if (result.succeeded()) {
            // extract the json data
            final JsonObject json = result.result().body();

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
    vertx.eventBus().send("ushortx-persistence-save",
        // the request data
        new JsonObject().put("url", url),
        // the result callback
        (AsyncResult<Message<JsonObject>> result) -> {
          if (result.succeeded()) {
            // extract the json data
            final JsonObject json = result.result().body();

            // get the id
            final long id = json.getLong("id");

            // generate an hash for the identifier
            final String hash = Hash.generate(config().getString("salt", DEFAULT_SALT), id);

            // write the response
            context.response().setStatusCode(200).write(
                new JsonObject()
                    .put("original", url)
                    .put("shortened", String.format(URL_REDIRECT_FORMAT,
                        context.request().localAddress().toString(), hash))
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
