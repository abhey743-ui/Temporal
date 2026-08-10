# Temporal Components Used in Configuration

This configuration creates three important Temporal components:

```text
WorkflowServiceStubs
        ↓
WorkflowClient
        ↓
WorkerFactory
```

Each one has a different responsibility.

---

## 1. WorkflowServiceStubs

```java
WorkflowServiceStubs workflowServiceStubs =
        WorkflowServiceStubs.newInstance();
```

### What is it?

`WorkflowServiceStubs` provides the **underlying communication connection between our application and the Temporal Service**.

Our Java application needs this communication layer to send requests to and receive responses from Temporal.

### Why do we need it?

Without this connection, our application cannot communicate with the Temporal Service.

It is the foundation used by other Temporal components such as `WorkflowClient`.

Think of it as:

```text
Java Application
       ↓
WorkflowServiceStubs
       ↓
Temporal Service
```

### Remember

> **WorkflowServiceStubs = underlying communication with Temporal**

---

# 2. WorkflowClient

```java
WorkflowClient workflowClient =
        WorkflowClient.newInstance(workflowServiceStubs);
```

### What is it?

`WorkflowClient` is the **higher-level API that our application uses to interact with Temporal Workflows**.

It uses the `WorkflowServiceStubs` underneath to communicate with the Temporal Service.

### Why do we need it?

When our application wants to work with a Workflow, we need a `WorkflowClient`.

For example, we can use it to:

* Start a Workflow
* Get a Workflow handle
* Send a Signal
* Query a Workflow
* Interact with an existing Workflow

Example:

```text
Application
     ↓
WorkflowClient
     ↓
WorkflowServiceStubs
     ↓
Temporal Service
     ↓
Workflow
```

### Remember

> **WorkflowClient = the API our application uses to interact with Workflows**

---

# 3. WorkerFactory

```java
WorkerFactory workerFactory =
        WorkerFactory.newInstance(workflowClient);
```

### What is it?

`WorkerFactory` is responsible for **creating and managing Temporal Workers**.

A Worker is responsible for actually executing our Workflow and Activity code.

### Why do we need it?

Defining a Workflow or Activity class does not mean Temporal will automatically execute it.

We need a Worker to:

1. Listen for tasks from Temporal.
2. Receive Workflow or Activity tasks.
3. Execute the corresponding Java code.
4. Send the result back to Temporal.

The `WorkerFactory` is what we use to create those Workers.

For example:

```java
Worker worker =
        workerFactory.newWorker("TRIP_TASK_QUEUE");
```

Then we can register our Workflow and Activities with that Worker.

---

# How They Work Together

These three components form a chain:

```text
WorkflowServiceStubs
        ↓
      provides
   communication
        ↓
WorkflowClient
        ↓
   interacts with
     Workflows
        ↓
WorkerFactory
        ↓
    creates
     Workers
        ↓
Workflows + Activities
```

A more practical view:

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
                         ↓
                       Worker
                    /          \
                   ↓            ↓
              Workflow       Activity
```

---

# The Important Difference

It is easy to confuse these classes because all of them are related to communicating with Temporal.

The simplest way to remember them is:

### WorkflowServiceStubs

**"How does my application communicate with Temporal?"**

```text
Application ↔ Temporal Service
```

### WorkflowClient

**"How does my application interact with Workflows?"**

```text
Application → WorkflowClient → Workflow
```

### WorkerFactory

**"How do I create and manage Workers that execute my Temporal code?"**

```text
WorkerFactory → Worker → Workflow / Activity
```

---

# Real Example: Trip Creation

Suppose our application has a `CreateTrip` Workflow.

A user sends:

```text
POST /trips
```

Our application may use the `WorkflowClient` to start the Workflow:

```text
User
 ↓
Spring Boot API
 ↓
WorkflowClient
 ↓
Temporal Service
 ↓
Trip Workflow
```

The Workflow might then need to execute Activities:

```text
Create Trip Workflow
        ↓
Create Booking Activity
        ↓
Payment Activity
        ↓
Notification Activity
```

The Worker executes these Workflow and Activity implementations.

```text
Temporal Service
       ↓
   Task Queue
       ↓
     Worker
       ↓
Workflow / Activities
```

The `WorkerFactory` is responsible for creating and managing that Worker.

---

# Quick Reference

| Component              | Main Responsibility                                 |
| ---------------------- | --------------------------------------------------- |
| `WorkflowServiceStubs` | Provides the underlying communication with Temporal |
| `WorkflowClient`       | Allows the application to interact with Workflows   |
| `WorkerFactory`        | Creates and manages Workers                         |
| `Worker`               | Executes Workflows and Activities                   |

### One-line memory trick

```text
ServiceStubs → Connect
Client       → Interact
WorkerFactory → Create Workers
Worker        → Execute
```

This is the main reason we create these three Temporal components in our application.
