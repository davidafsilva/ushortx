package pt.davidafsilva.ushortx.persistence;

/*
 * #%L
 * ushortx-persistence
 * %%
 * Copyright (C) 2015 David Silva
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.util.Optional;
import java.util.function.BiFunction;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

/**
 * The persistence verticle with the URL mappings
 *
 * @author David Silva
 */
public class DatabaseVerticle extends AbstractVerticle {

  // the logger
  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseVerticle.class);

  // the findById query
  private static final String FIND_BY_ID_QUERY = "SELECT id,url FROM urls WHERE id=?";
  // the findByUrl query
  private static final String FIND_BY_URL_QUERY = "SELECT id,url FROM urls WHERE url=?";
  // the insertUrl update statement
  private static final String INSERT_URL_STATEMENT = "INSERT INTO urls VALUES(?)";
  // the create table statement
  private static final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS urls(" +
      "id BIGINT AUTO_INCREMENT, url VARCHAR(255))";

  // the findById | findByUrl query result handler
  private static final BiFunction<Message<JsonObject>, SQLConnection, Handler<AsyncResult<ResultSet>>>
      FIND_QUERY_RESULT_HANDLER =
      (message, connection) -> dbResult -> {
        try {
          if (dbResult.succeeded()) {
            LOGGER.debug("find query results: " + dbResult.result().getRows());
            if (dbResult.result().getNumRows() == 1) {
              message.reply(dbResult.result().getRows().get(0));
            } else {
              message.fail(4, "url not found");
            }
          } else {
            message.fail(3, "internal database error");
          }
        } finally {
          connection.close();
        }
      };

  // the insertUrl query result handler
  private static final BiFunction<Message<JsonObject>, SQLConnection, Handler<AsyncResult<UpdateResult>>>
      INSERT_URL_RESULT_HANDLER =
      (message, connection) -> dbResult -> {
        try {
          if (dbResult.succeeded()) {
            LOGGER.debug("save statement result: " + dbResult.result().toJson());
            if (dbResult.result().getUpdated() == 1) {
              // get the saved identifier
              final JsonArray queryParams = new JsonArray().add(message.body().getString("url"));
              connection.queryWithParams(FIND_BY_URL_QUERY, queryParams,
                  FIND_QUERY_RESULT_HANDLER.apply(message, connection));
            } else {
              message.fail(4, "insert error");
            }
          } else {
            //TODO: if already exists error (due to concurrent requests) -> findByUrl
            message.fail(3, "internal database error");
          }
        } finally {
          connection.close();
        }
      };

  // the database client
  private JDBCClient client;

  @Override
  public void start() throws Exception {
    // create the database client
    client = JDBCClient.createShared(vertx,
        new JsonObject()
            .put("url", config().getString("url", "jdbc:h2:mem:ushortx?DB_CLOSE_DELAY=-1"))
            .put("driver_class", config().getString("driver_class", "org.h2.Driver"))
            .put("user", config().getString("user", "ushortx"))
            .put("password", config().getString("password", "shall-not-be-used"))
            .put("max_pool_size", config().getInteger("max_pool_size", 20))
        , "ushortx-ds");

    // create the table
    createTableStructure(r -> {
      // register the event bus consumers
      LOGGER.info("registering event consumers..");
      vertx.eventBus().consumer("ushortx-persistence-findById", this::findById);
      vertx.eventBus().consumer("ushortx-persistence-save", this::saveUrl);
    });
  }

  /**
   * Connects to the database and calls the specified handlers, accordingly.
   *
   * @param successHandler the success handler
   * @param errorHandler   the error handler
   */
  private void connect(final Handler<SQLConnection> successHandler,
      final Optional<Handler<Throwable>> errorHandler) {
    client.getConnection(result -> {
      if (result.succeeded()) {
        successHandler.handle(result.result());
      } else {
        LOGGER.error("unable to obtain a database connection", result.cause());
        errorHandler.ifPresent(h -> h.handle(result.cause()));
      }
    });
  }

  /**
   * Creates the necessary data structure (tables) that are required for the verticle execution.
   * An {@link IllegalStateException} might be thrown if we reach an state of no recovery.
   *
   * @param readyHandler the handler that shall be called whenever the data structure is created
   */
  private void createTableStructure(final Handler<Void> readyHandler) {
    connect(connection -> {
      LOGGER.info("creating database structure..");
      // create the table
      connection.execute(CREATE_TABLE_STATEMENT, dbResult -> {
        if (dbResult.failed()) {
          LOGGER.error("unable to create database structure", dbResult.cause());
          return;
        }

        // call the callback
        readyHandler.handle(null);
      });
    }, Optional.empty());
  }

  /**
   * Queries the database for an url entry with the identifier specified in the message
   *
   * @param message the message from where to extract the identifier and to reply from
   */
  private void findById(final Message<JsonObject> message) {
    LOGGER.info("incoming find request: " + message.body());
    connect(connection -> {
      // validate the identifier
      final Optional<Long> id = Optional.ofNullable(message.body().getLong("id"));
      if (!id.isPresent()) {
        connection.close();
        message.fail(2, "invalid identifier");
        return;
      }

      // create the query parameters
      final JsonArray queryParams = new JsonArray().add(id.get());

      // execute the query
      connection.queryWithParams(FIND_BY_ID_QUERY, queryParams,
          FIND_QUERY_RESULT_HANDLER.apply(message, connection));
    }, Optional.of(cause -> message.fail(1, "unavailable resources")));
  }

  /**
   * Saves at the database the url specified in the message, if non-existent. Otherwise the same
   * entry is used.
   *
   * @param message the message from where to extract the url data and to reply from
   */
  private void saveUrl(final Message<JsonObject> message) {
    LOGGER.info("incoming save request: " + message.body());
    connect(connection -> {
      // validate the url
      final Optional<String> url = Optional.ofNullable(message.body().getString("url"));
      if (!url.isPresent()) {
        connection.close();
        message.fail(2, "invalid url");
        return;
      }

      // create the update parameters
      final JsonArray updateParams = new JsonArray().add(url.get());

      // execute the update
      connection.updateWithParams(INSERT_URL_STATEMENT, updateParams,
          INSERT_URL_RESULT_HANDLER.apply(message, connection));
    }, Optional.of(cause -> message.fail(1, "unavailable resources")));
  }

  @Override
  public void stop() throws Exception {
    // close the client
    client.close();
  }
}
