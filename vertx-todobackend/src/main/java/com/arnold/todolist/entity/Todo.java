package com.arnold.todolist.entity;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.atomic.AtomicInteger;

@DataObject(generateConverter = true)
public class Todo {

    private static final AtomicInteger acc = new AtomicInteger(0);

    private int id;
    private String title;
    private Boolean completed;
    private Integer order;
    private String url;

    public Todo() {
    }

    public Todo(String jsonStr) {
        TodoConverter.fromJson(new JsonObject(jsonStr), this);
    }

    public Todo(JsonObject obj) {
        TodoConverter.fromJson(obj, this);
    }

    public Todo(int id, String title, Boolean completed, Integer order, String url) {
        this.id = id;
        this.title = title;
        this.completed = completed;
        this.order = order;
        this.url = url;
    }

    public static int getIncId() {
        return acc.get();
    }

    public static void setIncIdWith(int n){
        acc.set(n);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean isCompleted() {
        return getOrElse(completed, false);
    }

    private <T> T getOrElse(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Todo todo = (Todo) o;

        return new org.apache.commons.lang3.builder.EqualsBuilder()
                .append(id, todo.id)
                .append(title, todo.title)
                .append(completed, todo.completed)
                .append(order, todo.order)
                .append(url, todo.url)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new org.apache.commons.lang3.builder.HashCodeBuilder(17, 37)
                .append(id)
                .append(title)
                .append(completed)
                .append(order)
                .append(url)
                .toHashCode();
    }

    public void setIncId() {
        this.id = acc.incrementAndGet();
    }

    public Object merge(Todo newTodo) {
        return new Todo(id,
                getOrElse(newTodo.title, title),
                getOrElse(newTodo.completed,completed),
                getOrElse(newTodo.order,order),
                url);
    }
}
