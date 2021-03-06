# ushortx [![Build Status](https://jenkins-davidafsilva.rhcloud.com/job/ushortx/badge/icon)](https://jenkins-davidafsilva.rhcloud.com/job/ushortx/)
A *demo* URL Shortening application using [vertx.io](http://vertx.io).

This application contains two decoupled modules modeled as [Verticles](http://vertx.io/docs/vertx-core/java/#_verticles),
which can be independently deployed.

The [http](https://github.com/davidafsilva/ushortx/tree/master/http) module contains the http server 
and Rest API implementation.<br/>
The [persistence](https://github.com/davidafsilva/ushortx/tree/master/persistence) module contains
the database (persistence/retrieval of the data) abstraction implementation.<br/>
Both modules communicate with each other via the [Event bus](http://vertx.io/docs/vertx-core/java/#event_bus).

## Usage

The modules can be run via command line with either `vertx` or `java -jar` commands:
```
vertx run pt.davidafsilva.ushortx.http.RestVerticle -cp ushortx-http.jar:lib/ -cluster
vertx run pt.davidafsilva.ushortx.persistence.DatabaseVerticle -cp ushortx-persistence.jar:lib/ -cluster
```
```
java -jar ushortx-http.jar -cluster
java -jar ushortx-persistence.jar -cluster
```

The `-cluster` option is required for the local pub-sub event communication between the two verticles.

## Configuration

You can specify a configuration file for each module with the `-conf <json_file>` option.

The http module supports the following JSON properties:

| Property  | Possible values  | Default | Notes                                   |
|-----------|------------------|---------|-----------------------------------------|
| http_port | `1`...`65535`    | `8080`  | Beware that 1-1023 are privileged ports |

The persistence module supports:

| Property      | Possible values                              | Default                                  | Notes                              |
|---------------|----------------------------------------------|------------------------------------------|------------------------------------|
| url           | any JDBC valid url                           | `jdbc:h2:mem:ushortx?DB_CLOSE_DELAY=-1`  |                                    |
| driver_class  | the fully qualified name of the driver class | `org.h2.Driver`      | be sure to include the jar at runtime (h2 already included) |
| user          | the user for db auth                         | `ushortx`            | heh :)                                     |
| password      | the password for the db user                 | `shall-not-be-used`  | heh :)                                     |
| max_pool_size | 1..N                                         | `30`                 | the max. number of connections at the pool |


