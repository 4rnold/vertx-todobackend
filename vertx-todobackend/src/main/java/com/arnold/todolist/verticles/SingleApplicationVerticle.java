package com.arnold.todolist.verticles;

import com.arnold.todolist.Constants;
import com.arnold.todolist.entity.Todo;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SingleApplicationVerticle extends AbstractVerticle {

    private static final String HTTP_HOST = "0.0.0.0";
    private static final String REDIS_HOST = "10.0.0.2";
    private static final int HTTP_PORT = 8082;
    private static final int REDIS_PORT = 6379;

    private RedisClient redis;


    @Override
    public void start(Future<Void> startFuture) throws Exception {
        initData();
        Router router = Router.router(vertx);

        // CORS support
        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("x-requested-with");
        allowHeaders.add("Access-Control-Allow-Origin");
        allowHeaders.add("origin");
        allowHeaders.add("Content-Type");
        allowHeaders.add("accept");
        Set<HttpMethod> allowMethods = new HashSet<>();
        allowMethods.add(HttpMethod.GET);
        allowMethods.add(HttpMethod.POST);
        allowMethods.add(HttpMethod.DELETE);
        allowMethods.add(HttpMethod.PATCH);


        router.route().handler(CorsHandler.create("*").allowedHeaders(allowHeaders).allowedMethods(allowMethods));
        router.route().handler(BodyHandler.create());


        // routes  注意和cors的顺序
        router.get(Constants.API_GET).handler(this::handleGetTodo);
        router.get(Constants.API_LIST_ALL).handler(this::handleGetAll);
        router.post(Constants.API_CREATE).handler(this::handleCreateTodo);
        router.patch(Constants.API_UPDATE).handler(this::handleUpdateTodo);
        router.delete(Constants.API_DELETE).handler(this::handleDeleteOne);
        router.delete(Constants.API_DELETE_ALL).handler(this::handleDeleteAll);

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(HTTP_PORT, HTTP_HOST, result -> {
                    if (result.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(result.cause());
                    }
                });

    }

    private void handleDeleteAll(RoutingContext routingContext) {
        redis.del(Constants.REDIS_TODO_KEY, res -> {
            if (res.succeeded())
                routingContext.response().setStatusCode(204).end();
            else
                sendError(503, routingContext.response());
        });
    }

    private void handleDeleteOne(RoutingContext routingContext) {
        String todoID = routingContext.request().getParam("todoId");
        redis.hdel(Constants.REDIS_TODO_KEY, todoID, res -> {
            if (res.succeeded())
                routingContext.response().setStatusCode(204).end();
            else
                sendError(503, routingContext.response());
        });
    }

    private void handleUpdateTodo(RoutingContext routingContext) {
        try {
            String todoId = routingContext.request().getParam("todoId");
            Todo newTodo = new Todo(routingContext.getBodyAsString());

            if (todoId == null || newTodo == null) {
                sendError(400, routingContext.response());
                return;
            }

            redis.hget(Constants.REDIS_TODO_KEY, todoId, x -> {
                if (x.succeeded()) {
                    String result = x.result();
                    if (result == null) {
                        sendError(400, routingContext.response());
                    } else {
                        //string 转为 todo
                        Todo oldTodo = new Todo(result);
                        String response = Json.encodePrettily(oldTodo.merge(newTodo));
                        redis.hset(Constants.REDIS_TODO_KEY, todoId, response, res -> {
                            if (res.succeeded()) {
                                routingContext.response()
                                        .putHeader("content-type", "application/json")
                                        .end(response);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            sendError(400, routingContext.response());
        }
    }

    private void handleCreateTodo(RoutingContext routingContext) {
        try {
            Todo todo = wrapObject(new Todo(routingContext.getBodyAsString()), routingContext);
            String encoded = Json.encodePrettily(todo);
            redis.hset(Constants.REDIS_TODO_KEY, String.valueOf(todo.getId()), encoded, res -> {
                if (res.succeeded()) {
                    routingContext.response()
                            .setStatusCode(201)
                            .putHeader("content-type", "application/json")
                            .end(encoded);
                } else {
                    sendError(503, routingContext.response());
                }
            });
        } catch (DecodeException e) {
            sendError(400, routingContext.response());
        }

    }

    private Todo wrapObject(Todo todo, RoutingContext context) {
        int id = todo.getId();
        if (id > Todo.getIncId()) {
            //手动设置了一个id，id > acc自增的值，则将acc设置为id，从id值开始加
            Todo.setIncIdWith(id);
        } else if (id == 0)
            todo.setIncId();
        todo.setUrl(context.request().absoluteURI() + "/" + todo.getId());
        return todo;
    }

    private void handleGetAll(RoutingContext routingContext) {
        redis.hvals(Constants.REDIS_TODO_KEY, res -> {
            if (res.succeeded()) {
                //不能直接将返回的JsonArray写入response
                //先转为todo对象，再json编码
                System.out.println(res.result().toString());
                String encoded = Json.encodePrettily(res.result().stream()
                        .map(x -> new Todo((String) x))//“归约”成List<Todo>
                        .collect(Collectors.toList()));
                System.out.println(encoded);
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .end(encoded);
            } else {
                sendError(503, routingContext.response());
            }
        });
    }

    private void handleGetTodo(RoutingContext routingContext) {
        String todoID = routingContext.request().getParam("todoId");
        if (todoID == null) {
            sendError(400, routingContext.response());
        } else {
            redis.hget(Constants.REDIS_TODO_KEY, todoID, x -> {
                //从redis中获取到了
                if (x.succeeded()) {
                    String result = x.result();
                    if (result == null) {
                        sendError(400, routingContext.response());
                    } else {
                        routingContext.response()
                                .putHeader("content-type", "application/json")
                                .end(result);
                    }
                } else {
                    sendError(503, routingContext.response());
                }
            });
        }
    }


    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }


    private void initData() {
        RedisOptions config = new RedisOptions()
                .setHost(config().getString("redis.host", REDIS_HOST)) // redis host
                .setPort(config().getInteger("redis.port", REDIS_PORT)); // redis port

        this.redis = RedisClient.create(vertx, config); // create redis client

        redis.hset(Constants.REDIS_TODO_KEY, "24", Json.encodePrettily( // test connection
                new Todo(24, "Something to do...", false, 1, "todo/ex")), res -> {
            if (res.failed()) {
                //LOGGER.error("Redis service is not running!");
                res.cause().printStackTrace();
            }
        });
    }
}
