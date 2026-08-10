# Temporal Configuration

## Overview

This configuration class creates the main Temporal components that our Spring Boot application needs.

The main purpose is to configure the connection between our **Spring Boot application** and the **Temporal Server**, and then create the objects required to run Temporal Workflows and Activities.

The overall flow is:

```text
Spring Boot Application
        |
        ↓
WorkflowServiceStubs
        |
        ↓
WorkflowClient
        |
        ↓
WorkerFactory
        |
        ↓
Workers
        |
        ↓
Workflows + Activities
        |
        ↓
Temporal Server
```

Each component has a different responsibility.

---

# Complete Configuration

```java
@org.springframework.context.annotation.Configuration
public class Configuration {

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newInstance();
    }

    @Bean
    public WorkflowClient workflowClient(
            WorkflowServiceStubs workflowServiceStubs) {

        return WorkflowClient.newInstance(workflowServiceStubs);
    }

    @Bean
    public WorkerFactory workerFactory(
            WorkflowClient workflowClient) {

        return WorkerFactory.newInstance(workflowClient);
    }
}
```

---

# Why do we need this configuration?

Temporal provides several Java SDK objects that our application needs.

Instead of creating these objects manually every time we need them, we configure them as **Spring Beans**.

Spring will create and manage these objects for us.

For example:

```java
@Bean
public WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newInstance();
}
```

Spring creates the `WorkflowServiceStubs` object and keeps it available inside the application context.

Then another bean can request it:

```java
public WorkflowClient workflowClient(
        WorkflowServiceStubs workflowServiceStubs)
```

Spring sees that `WorkflowServiceStubs` is already a bean and automatically provides it.

This is **Dependency Injection**.

---

# 1. `@Configuration`

```java
@org.springframework.context.annotation.Configuration
public class Configuration {
```

`@Configuration` tells Spring:

> "This class contains configuration information and defines objects that Spring should manage."

In other words, this class is where we tell Spring how to create our Temporal infrastructure.

Without this configuration, we would have to manually create these objects ourselves.

---

# 2. `@Bean`

We use `@Bean` on methods whose returned objects should be managed by Spring.

Example:

```java
@Bean
public WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newInstance();
}
```

This tells Spring:

> "Call this method, take the returned object, and register it inside the Spring Application Context."

After that, other Spring-managed classes can request the object through dependency injection.

---

# 3. `WorkflowServiceStubs`

```java
@Bean
public WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newInstance();
}
```

## What is it?

`WorkflowServiceStubs` provides the **underlying communication mechanism between our Java application and the Temporal Service**.

It is the lower-level service connection used by the Temporal SDK.

Think of it as the communication layer that allows our application to reach Temporal.

```text
Our Application
      |
      ↓
WorkflowServiceStubs
      |
      ↓
Temporal Service
```

## Why do we create it?

Because the Temporal SDK needs a way to communicate with the Temporal Service.

We create it once as a Spring Bean so that other Temporal components can use the same configured connection.

---

# 4. `WorkflowClient`

```java
@Bean
public WorkflowClient workflowClient(
        WorkflowServiceStubs workflowServiceStubs) {

    return WorkflowClient.newInstance(workflowServiceStubs);
}
```

## What is it?

`WorkflowClient` is the higher-level API that our application uses to **interact with Temporal Workflows**.

It uses `WorkflowServiceStubs` underneath to communicate with the Temporal Service.

So:

```text
WorkflowClient
      |
      ↓
WorkflowServiceStubs
      |
      ↓
Temporal Service
```

## Why do we create it?

Our application will eventually need to perform operations such as:

* Starting a Workflow
* Sending a Signal to a Workflow
* Querying a Workflow
* Getting a Workflow handle
* Interacting with an existing Workflow

The `WorkflowClient` provides the APIs we use for these operations.

For example:

```java
WorkflowClient workflowClient =
        WorkflowClient.newInstance(workflowServiceStubs);
```

The important thing to understand is:

> `WorkflowServiceStubs` provides the underlying service communication, while `WorkflowClient` provides the application-level API for interacting with Workflows.

---

# 5. Why is `WorkflowServiceStubs` passed into `WorkflowClient`?

This part is very important:

```java
WorkflowClient.newInstance(workflowServiceStubs);
```

We are basically saying:

> "Create a WorkflowClient and use this Temporal service connection to communicate with Temporal."

So the relationship is:

```text
WorkflowServiceStubs
       ↓
Provides communication with Temporal
       ↓
WorkflowClient
       ↓
Provides APIs for interacting with Workflows
```

This is why `WorkflowClient` needs `WorkflowServiceStubs`.

---

# 6. `WorkerFactory`

```java
@Bean
public WorkerFactory workerFactory(
        WorkflowClient workflowClient) {

    return WorkerFactory.newInstance(workflowClient);
}
```

## What is a WorkerFactory?

`WorkerFactory` is responsible for creating and managing **Temporal Workers**.

A Worker is the component that actually executes our:

* Workflows
* Activities

For example, we might eventually create a Worker like:

```java
Worker worker =
        workerFactory.newWorker("TRIP_TASK_QUEUE");
```

Then we register our Workflow and Activities with that Worker.

---

# Why do we need a Worker?

Temporal does not execute your Java Workflow code simply because you created a Workflow class.

Your application needs a **Worker** that listens for tasks from Temporal and executes the corresponding Workflow or Activity code.

Think about it like this:

```text
Temporal Service
      |
      | "Here is a Workflow task"
      ↓
Task Queue
      |
      ↓
Worker
      |
      ↓
Your Java Workflow Code
```

The Worker is therefore the component that connects Temporal's work to your actual Java implementation.

---

# Why does `WorkerFactory` need `WorkflowClient`?

We create it like this:

```java
WorkerFactory.newInstance(workflowClient);
```

The `WorkerFactory` uses the `WorkflowClient` to communicate with the Temporal Service.

The relationship becomes:

```text
WorkflowServiceStubs
        ↓
   WorkflowClient
        ↓
   WorkerFactory
        ↓
      Worker
        ↓
Workflow + Activities
```

Each layer has a specific responsibility.

---

# Complete Mental Model

This is the most important diagram to remember:

```text
                    Temporal Service
                           ↑
                           |
                  WorkflowServiceStubs
                           ↑
                           |
                    WorkflowClient
                           ↑
                           |
                    WorkerFactory
                           |
                    creates Workers
                           |
                           ↓
                        Worker
                       /      \
                      /        \
                     ↓          ↓
                Workflow     Activity
```

---

# Why use Spring Beans?

We could technically create these objects manually.

For example:

```java
WorkflowServiceStubs service =
        WorkflowServiceStubs.newInstance();

WorkflowClient client =
        WorkflowClient.newInstance(service);

WorkerFactory factory =
        WorkerFactory.newInstance(client);
```

But in a Spring Boot application, we normally don't want to manually manage these objects everywhere.

Instead, we let Spring manage them:

```java
@Bean
public WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newInstance();
}
```

Then:

```java
@Bean
public WorkflowClient workflowClient(
        WorkflowServiceStubs workflowServiceStubs) {

    return WorkflowClient.newInstance(workflowServiceStubs);
}
```

Spring understands the dependency relationship.

---

# Understanding Dependency Injection Here

Look at this method:

```java
public WorkflowClient workflowClient(
        WorkflowServiceStubs workflowServiceStubs)
```

We didn't write:

```java
new WorkflowServiceStubs(...)
```

inside the method.

Instead, we ask Spring for:

```java
WorkflowServiceStubs workflowServiceStubs
```

Spring says:

> "I already have a `WorkflowServiceStubs` Bean. I'll give it to you."

This is Dependency Injection.

The same thing happens here:

```java
public WorkerFactory workerFactory(
        WorkflowClient workflowClient)
```

Spring already created the `WorkflowClient`, so it provides that object to the `workerFactory()` method.

---

# Creation Order

The dependencies form a chain.

Spring needs to create them in a logical order:

```text
1. WorkflowServiceStubs
          ↓
2. WorkflowClient
          ↓
3. WorkerFactory
```

Why?

Because:

```text
WorkflowClient needs WorkflowServiceStubs
```

and:

```text
WorkerFactory needs WorkflowClient
```

So Spring resolves the dependencies automatically.

---

# Simple Analogy

Imagine building a company office.

### `WorkflowServiceStubs`

This is like the **telephone/network connection** to an external company.

```text
Your Office ───── Network ───── External Company
```

### `WorkflowClient`

This is like the **person using that network to communicate with the external company**.

### `WorkerFactory`

This is like the **manager responsible for creating workers**.

### Worker

The worker is the person who actually performs the assigned work.

So:

```text
Network
   ↓
Communication Client
   ↓
Worker Manager
   ↓
Workers
   ↓
Actual Work
```

This analogy isn't technically exact, but it is useful for remembering the responsibilities.

---

# Why not create everything in every class?

We don't want this:

```java
public class CreateTripWorkflowStarter {

    public void createTrip() {

        WorkflowServiceStubs service =
                WorkflowServiceStubs.newInstance();

        WorkflowClient client =
                WorkflowClient.newInstance(service);

        // ...
    }
}
```

If many classes do this, we could end up with duplicated configuration and unnecessary object creation.

Instead, we centralize the configuration:

```text
Configuration
     |
     ├── WorkflowServiceStubs
     |
     ├── WorkflowClient
     |
     └── WorkerFactory
```

Then other classes simply receive the required dependency.

---

# How another class can use `WorkflowClient`

For example:

```java
@Service
public class CreateTripWorkflowStarter {

    private final WorkflowClient workflowClient;

    public CreateTripWorkflowStarter(
            WorkflowClient workflowClient) {

        this.workflowClient = workflowClient;
    }

    public void createTrip(MakeTripDto makeTripDto) {

        // Use workflowClient to interact with Temporal
    }
}
```

We don't create the `WorkflowClient` here.

Spring gives it to us.

This keeps our business code focused on its actual responsibility.

---

# Important distinction

Do not think of all three classes as doing the same thing.

### `WorkflowServiceStubs`

**Purpose:**

> Provides the underlying service communication with Temporal.

### `WorkflowClient`

**Purpose:**

> Provides application-level APIs to interact with Temporal Workflows.

### `WorkerFactory`

**Purpose:**

> Creates and manages Workers that execute Workflow and Activity code.

Remember:

```text
WorkflowServiceStubs
        ↓
"How do I communicate with Temporal?"

WorkflowClient
        ↓
"How do I interact with Workflows?"

WorkerFactory
        ↓
"How do I create/manage Workers?"
```

---

# One Important Note About `newInstance()`

Your code has:

```java
WorkflowServiceStubs.newInstance();
```

This creates the service stubs using the SDK's default configuration.

In a real application, the Temporal Service may be running somewhere other than your local machine, and you may need to configure things such as:

* Temporal server address
* Namespace
* TLS/security
* Authentication
* Other connection settings

So when you see:

```java
WorkflowServiceStubs.newInstance();
```

remember:

> **This is the point where the Temporal service connection is created/configured.**

The exact configuration depends on where your Temporal Service is running.

---

# Final Summary

The entire configuration exists to build the Temporal infrastructure for our Spring Boot application.

```text
@Configuration
     |
     |-- @Bean
     ↓
WorkflowServiceStubs
     |
     | provides communication with Temporal
     ↓
WorkflowClient
     |
     | provides APIs to interact with Workflows
     ↓
WorkerFactory
     |
     | creates/manages Workers
     ↓
Worker
     |
     | executes
     ↓
Workflows + Activities
```

### Easy way to remember

> **ServiceStubs → connection to Temporal**
> **WorkflowClient → interact with Workflows**
> **WorkerFactory → create/manage Workers**
> **Worker → execute Workflows and Activities**

This configuration is essentially the **foundation that allows our Spring Boot application to use Temporal**.

