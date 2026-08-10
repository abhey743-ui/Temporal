
# CreateTripWorkflowStarter

## Purpose

`CreateTripWorkflowStarter` is responsible for **preparing a Temporal Workflow instance** that will be used to create a trip.

The main flow is:

```text
Spring Boot Application
        ↓
WorkflowServiceStubs
        ↓
WorkflowClient
        ↓
Workflow Stub
        ↓
CreateTripWorkflow
        ↓
Temporal Task Queue: CREATE_TRIP
```

---

# Code

```java
@Component
@AllArgsConstructor
public class CreateTripWorkFlowStarter {

    private WorkflowServiceStubs workflowServiceStub;

    public void createTrip(MakeTripDto makeTripDto) {

        WorkflowClient workflowClient =
                WorkflowClient.newInstance(workflowServiceStub);

        CreateTripWorkFlowInterface createTripWorkFlowInterface =
                workflowClient.newWorkflowStub(
                        CreateTripWorkFlowInterface.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(
                                        "MAKE_TRIP" + makeTripDto.getEmail()
                                )
                                .setTaskQueue("CREATE_TRIP")
                                .build()
                );
    }
}
```

---

# 1. `WorkflowServiceStubs`

```java
private WorkflowServiceStubs workflowServiceStub;
```

`WorkflowServiceStubs` provides the **underlying communication with the Temporal Service**.

This object was created earlier in our Temporal configuration.

We inject it into this class because we need it to create the `WorkflowClient`.

The relationship is:

```text
WorkflowServiceStubs
        ↓
WorkflowClient
```

---

# 2. Creating `WorkflowClient`

```java
WorkflowClient workflowClient =
        WorkflowClient.newInstance(workflowServiceStub);
```

Here we create a `WorkflowClient` using our existing `WorkflowServiceStubs`.

The `WorkflowClient` is the object our application uses to **interact with Temporal Workflows**.

In this class, we need it because we want to create a Workflow Stub.

So:

```text
WorkflowServiceStubs
        ↓
   WorkflowClient
```

Remember:

> `WorkflowServiceStubs` provides the underlying Temporal communication, while `WorkflowClient` provides the APIs we use to interact with Workflows.

---

# 3. `newWorkflowStub()`

```java
workflowClient.newWorkflowStub(
        CreateTripWorkFlowInterface.class,
        WorkflowOptions...
);
```

This is an important part of the code.

`newWorkflowStub()` creates a **client-side Workflow Stub** for our `CreateTripWorkFlowInterface`.

The stub acts like a local Java object that represents the Temporal Workflow.

We can use this stub to interact with the Workflow without manually dealing with the low-level Temporal communication.

Think of it as:

```text
Our Java Code
      ↓
Workflow Stub
      ↓
Temporal
      ↓
Actual Workflow Execution
```

---

# 4. `CreateTripWorkFlowInterface.class`

```java
CreateTripWorkFlowInterface.class
```

This tells Temporal **which Workflow interface we want the stub to represent**.

For example, our Workflow interface might look something like:

```java
@WorkflowInterface
public interface CreateTripWorkFlowInterface {

    @WorkflowMethod
    void createTrip(MakeTripDto makeTripDto);
}
```

The important point is that we are not creating the Workflow implementation directly here.

We are creating a **Workflow Stub** for the Workflow interface.

---

# 5. `WorkflowOptions`

```java
WorkflowOptions.newBuilder()
```

`WorkflowOptions` contains the configuration that Temporal needs when creating/starting a Workflow execution.

In this example, we configure two important things:

```text
WorkflowOptions
      |
      ├── Workflow ID
      |
      └── Task Queue
```

---

# 6. Workflow ID

```java
.setWorkflowId(
    "MAKE_TRIP" + makeTripDto.getEmail()
)
```

The **Workflow ID uniquely identifies a Workflow execution** within the relevant Temporal namespace.

Here we are creating the ID using:

```text
MAKE_TRIP + email
```

For example:

```text
MAKE_TRIPjohn@gmail.com
```

This gives the Workflow a business-specific identifier.

### Why is Workflow ID important?

It allows us to identify a particular Workflow execution.

It can also help us prevent accidentally starting multiple Workflow executions with the same ID, depending on the configured Workflow ID reuse/conflict behavior.

### Important design consideration

Using an email directly as the Workflow ID may not always be the best design.

A dedicated trip ID or business identifier is often safer:

```text
MAKE_TRIP_<tripId>
```

For example:

```text
MAKE_TRIP_12345
```

The correct choice depends on the business requirement.

---

# 7. Task Queue

```java
.setTaskQueue("CREATE_TRIP")
```

The **Task Queue** determines where Temporal places tasks for this Workflow so that the appropriate Worker can pick them up.

Here we use:

```text
CREATE_TRIP
```

Our Worker must listen to the **same Task Queue**.

For example:

```java
Worker worker =
        workerFactory.newWorker("CREATE_TRIP");
```

The relationship is:

```text
Workflow
    ↓
Task Queue: CREATE_TRIP
    ↓
Worker listening to CREATE_TRIP
    ↓
Workflow / Activities execute
```

If the Workflow uses:

```text
CREATE_TRIP
```

but the Worker listens to:

```text
PAYMENT
```

the Worker will not receive the Workflow task.

Therefore:

> **The Workflow's Task Queue and the Worker's Task Queue must match.**

---

# 8. `build()`

```java
.build()
```

After setting the Workflow options, `build()` creates the final `WorkflowOptions` object.

So this:

```java
WorkflowOptions.newBuilder()
        .setWorkflowId(...)
        .setTaskQueue(...)
        .build()
```

means:

> Create the configuration for this Workflow using the specified Workflow ID and Task Queue.

---

# Complete Flow

Let's put everything together.

When `createTrip()` is called:

```text
1. Receive MakeTripDto
          ↓
2. Create WorkflowClient
          ↓
3. Create Workflow Stub
          ↓
4. Configure Workflow ID
          ↓
5. Configure Task Queue
          ↓
6. Build Workflow Options
```

Conceptually:

```text
createTrip(makeTripDto)
          |
          ↓
WorkflowServiceStubs
          |
          ↓
WorkflowClient
          |
          ↓
newWorkflowStub()
          |
          ↓
CreateTripWorkFlowInterface
          |
          ↓
WorkflowOptions
      /          \
     ↓            ↓
Workflow ID    Task Queue
                 |
                 ↓
          "CREATE_TRIP"
```

---

# Important: Does This Code Actually Start the Workflow?

**No.**

This is a very important point.

The code:

```java
CreateTripWorkFlowInterface createTripWorkFlowInterface =
        workflowClient.newWorkflowStub(...);
```

creates a **Workflow Stub**, but it does not necessarily start the Workflow execution by itself.

The stub is the object we use to interact with the Workflow.

For example, if the Workflow interface contains:

```java
@WorkflowMethod
void createTrip(MakeTripDto makeTripDto);
```

we would typically invoke the Workflow method through the stub to start the Workflow:

```java
createTripWorkFlowInterface.createTrip(makeTripDto);
```

The exact behavior also depends on whether we use a normal Workflow stub or an asynchronous start pattern.

---

# Why Do We Use a Workflow Stub?

Without the stub, our application would have to manually deal with lower-level Temporal APIs to identify and communicate with the Workflow.

Instead, Temporal gives us a Java interface:

```java
CreateTripWorkFlowInterface
```

and creates a client-side implementation/proxy for it.

We can then interact with it like a normal Java object:

```java
createTripWorkFlowInterface.createTrip(makeTripDto);
```

Behind the scenes, the Temporal SDK handles the communication with the Temporal Service.

This is one of the nice parts of the Temporal programming model.

---

# Example Mental Model

Think of the Workflow Stub as a **remote proxy**.

Your application has:

```text
CreateTripWorkFlowInterface
```

but the actual Workflow runs in a Temporal Worker.

```text
Spring Boot Application
        |
        | calls
        ↓
Workflow Stub
        |
        | Temporal communication
        ↓
Temporal Service
        |
        ↓
Task Queue
        |
        ↓
Temporal Worker
        |
        ↓
CreateTripWorkflow Implementation
```

Your application doesn't need to directly call the Workflow implementation.

The Temporal SDK handles the communication.

---

# Key Things to Remember

### `WorkflowServiceStubs`

Provides the underlying communication with Temporal.

### `WorkflowClient`

Provides the APIs our application uses to interact with Temporal.

### `newWorkflowStub()`

Creates a client-side stub/proxy for a specific Workflow interface.

### `WorkflowOptions`

Defines configuration for the Workflow, such as:

* Workflow ID
* Task Queue
* Other Workflow execution options

### Workflow ID

Identifies the Workflow execution.

```java
.setWorkflowId(...)
```

### Task Queue

Determines which Worker should receive the Workflow task.

```java
.setTaskQueue("CREATE_TRIP")
```

### Workflow Stub

Allows our application to interact with the Workflow through its interface.

---

# Final Mental Model

The easiest way to remember this entire class:

```text
WorkflowServiceStubs
        ↓
"Connect to Temporal"

WorkflowClient
        ↓
"Give my application access to Temporal APIs"

Workflow Stub
        ↓
"Represent this specific Workflow in my application"

WorkflowOptions
        ↓
"Tell Temporal how this Workflow should be configured"

Task Queue
        ↓
"Tell Temporal where the Worker is listening"

Workflow
        ↓
"Actually execute the business process"
```

So the responsibility of `CreateTripWorkFlowStarter` is essentially:

> **Create a Temporal Workflow client, create a typed stub for the Create Trip Workflow, and configure that Workflow with its identity and Task Queue so our application can interact with it.**
