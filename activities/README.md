
# Temporal Activities — Create Trip

## Overview

In Temporal, an **Activity** contains the actual business or technical work that needs to be performed.

Our `CreateTripWorkFlowImpl` is responsible for **orchestrating** the trip process, while `CreateTripImplementation` is responsible for **performing the actual operations**.

The architecture is:

```text
Workflow
   |
   | calls Activity
   ↓
Activity Interface
   |
   ↓
Activity Implementation
   |
   ↓
External Microservice / Database / API
```

For our trip example:

```text
CreateTrip Workflow
        |
        ├── createTrip()
        |
        ├── bookHotel()
        |
        └── bookTransportation()
```

If something fails:

```text
CreateTrip Workflow
        |
        ↓
Saga Compensation
        |
        ├── cancelTrip()
        ├── cancelHotel()
        └── cancelTransportation()
```

---

# 1. Activity Interface

```java
@ActivityInterface
public interface CreateTripInterface {

    @ActivityMethod
    public void createTrip(MakeTripDto makeTripDto);

    @ActivityMethod
    public void bookHotel(MakeTripDto makeTripDto);

    @ActivityMethod
    public void bookTransportation(MakeTripDto makeTripDto);

    @ActivityMethod
    void cancelTrip(MakeTripDto makeTripDto);

    @ActivityMethod
    void cancelHotel(MakeTripDto makeTripDto);

    @ActivityMethod
    void cancelTransportation(MakeTripDto makeTripDto);
}
```

## What is the Activity Interface?

The Activity Interface defines the **operations that can be executed as Temporal Activities**.

It is essentially the contract between the Workflow and the Activity implementation.

For example:

```java
@ActivityMethod
public void bookHotel(MakeTripDto makeTripDto);
```

means:

> "There is an Activity called `bookHotel` that the Workflow can execute."

The interface defines **what operations are available**, but it does not contain the actual business implementation.

---

# Why use an Activity Interface?

The Workflow should not directly depend on the implementation details of the business operation.

Instead, the Workflow interacts with the Activity through the interface.

```text
Workflow
    |
    ↓
CreateTripInterface
    |
    ↓
CreateTripImplementation
```

This gives us a clean separation between:

### Workflow

Responsible for:

```text
Orchestration
Order
Retries
Timeouts
Compensation
Failure handling
```

### Activity

Responsible for:

```text
Actual business/technical operation
API calls
Database operations
Microservice communication
External system communication
```

---

# 2. `@ActivityInterface`

```java
@ActivityInterface
public interface CreateTripInterface
```

`@ActivityInterface` tells the Temporal SDK:

> "This interface defines Temporal Activities."

Temporal can then use this interface when creating an Activity Stub:

```java
CreateTripInterface activities =
        Workflow.newActivityStub(
                CreateTripInterface.class,
                activityOptions()
        );
```

The Workflow can then call:

```java
activities.createTrip(makeTripDto);
```

---

# 3. `@ActivityMethod`

Each method that should be exposed as a Temporal Activity is marked with:

```java
@ActivityMethod
```

For example:

```java
@ActivityMethod
public void createTrip(MakeTripDto makeTripDto);
```

This tells Temporal that this method represents an Activity operation.

Our interface therefore contains two groups of operations.

### Forward operations

```text
createTrip()
bookHotel()
bookTransportation()
```

These perform the actual trip creation process.

### Compensation operations

```text
cancelTrip()
cancelHotel()
cancelTransportation()
```

These undo previously successful operations when the Workflow needs to compensate.

---

# 4. Activity Implementation

```java
@Service
public class CreateTripImplementation
        implements CreateTripInterface {
```

This class contains the **actual implementation of the Activity methods**.

For example:

```java
@Override
public void createTrip(MakeTripDto makeTripDto) {

    // Actual business logic
}
```

The Workflow does not directly call this Java method as an ordinary local method.

Instead, the Workflow calls the Activity through a Temporal Activity Stub.

```text
Workflow
   |
   ↓
Activity Stub
   |
   ↓
Temporal Service
   |
   ↓
Task Queue
   |
   ↓
Worker
   |
   ↓
CreateTripImplementation
```

This is important because Temporal can then provide Activity features such as:

* Retries
* Timeouts
* Failure handling
* Task scheduling
* Durable execution

---

# 5. `createTrip()` Activity

```java
@Override
public void createTrip(MakeTripDto makeTripDto) {

}
```

This method contains the business logic required to create the trip.

For example, it might eventually communicate with another microservice:

```text
Temporal Worker
       ↓
CreateTripImplementation
       ↓
Trip Microservice
       ↓
Create Trip
```

The Activity is the appropriate place for this kind of external communication.

---

# Workflow vs Activity

This distinction is extremely important.

Suppose we have:

```java
activities.createTrip(makeTripDto);
```

This call happens from the Workflow.

The Workflow is saying:

> "I need the Create Trip operation to be performed."

Temporal schedules the Activity.

The Worker eventually executes:

```java
CreateTripImplementation.createTrip(...)
```

So:

```text
Workflow
   |
   | "Execute createTrip"
   ↓
Temporal
   |
   ↓
Worker
   |
   ↓
CreateTripImplementation
   |
   ↓
External Service
```

---

# 6. Why External API Calls Belong in Activities

Suppose `createTrip()` needs to call:

```text
Trip Microservice
```

We should put that communication inside the Activity.

For example:

```java
@Override
public void createTrip(MakeTripDto makeTripDto) {

    tripClient.createTrip(makeTripDto);

}
```

The Workflow should not directly call:

```java
tripClient.createTrip(...)
```

because the Workflow code must follow Temporal's Workflow execution/determinism model.

Activities are designed for operations that interact with external systems.

Examples include:

```text
REST API
Database
File system
External service
Payment provider
Email service
Message broker
```

---

# 7. Activity Execution Context

Inside the Activity we have:

```java
ActivityExecutionContext activityExecutionContext =
        Activity.getExecutionContext();
```

`ActivityExecutionContext` provides information and functionality related to the **currently executing Activity**.

It allows the Activity implementation to interact with the Temporal Activity execution context.

For example, we can use it to obtain:

```text
Task Token
```

and control Activity completion behavior.

---

# 8. Task Token

Your code contains:

```java
byte[] taskToken =
        activityExecutionContext.getTaskToken();
```

A **Task Token identifies a particular Activity Task execution**.

It can be used when an Activity is completed asynchronously from outside the original Activity method execution.

Conceptually:

```text
Temporal
   |
   ↓
Activity Task
   |
   ↓
Task Token
   |
   ↓
External System
```

The token allows the external completion mechanism to identify **which Activity execution should be completed**.

---

# Why would we need a Task Token?

Normally, an Activity works like this:

```text
Activity starts
     ↓
Business logic executes
     ↓
Method returns
     ↓
Temporal receives completion
```

For example:

```java
@Override
public void createTrip(MakeTripDto makeTripDto) {

    callMicroservice();

}
```

When the method returns normally, Temporal knows that the Activity completed successfully.

But sometimes we want the Activity to remain open while another system completes the operation later.

For example:

```text
Temporal Activity
       ↓
Send request to another service
       ↓
Activity method returns
       ↓
External service processes request
       ↓
External service sends response later
       ↓
Activity completed
```

This is an **asynchronous Activity completion** scenario.

---

# 9. `doNotCompleteOnReturn()`

Your code contains:

```java
activityExecutionContext.doNotCompleteOnReturn();
```

This is the key part of the asynchronous Activity pattern.

Normally, when the Activity method returns, Temporal considers the Activity execution complete.

But calling:

```java
doNotCompleteOnReturn();
```

tells Temporal:

> **"Do not consider this Activity completed just because this method returns. I will complete it later."**

So instead of:

```text
Activity starts
     ↓
Method returns
     ↓
Activity completed
```

we can have:

```text
Activity starts
     ↓
Send request to external system
     ↓
Method returns
     ↓
Activity remains incomplete
     ↓
External system completes Activity later
     ↓
Temporal marks Activity completed
```

---

# Important: Task Token + `doNotCompleteOnReturn()`

These two lines are related:

```java
byte[] taskToken =
        activityExecutionContext.getTaskToken();

activityExecutionContext.doNotCompleteOnReturn();
```

The idea is:

```text
Task Token
    ↓
Identifies the Activity execution

doNotCompleteOnReturn()
    ↓
Tells Temporal that completion will happen later
```

The external completion mechanism can use the Task Token to identify the Activity that needs to be completed.

---

# Asynchronous Activity Example

Imagine our Activity sends a request to another microservice.

```text
Temporal
   |
   ↓
CreateTrip Activity
   |
   | taskToken
   ↓
Trip Microservice
   |
   | processes request
   |
   ↓
Response later
   |
   ↓
Complete Activity
   |
   ↓
Temporal
```

The Activity can remain open while the external system processes the request.

This is useful when the external system doesn't respond immediately or when completion happens through another asynchronous mechanism.

---

# Important Correction About the Task Token

The Task Token is **not the Workflow ID**.

These are different things.

### Workflow ID

Identifies a Workflow execution.

Example:

```text
MAKE_TRIP_12345
```

### Activity Task Token

Identifies a particular Activity Task execution and can be used for asynchronous Activity completion.

Conceptually:

```text
Workflow ID
    ↓
Identifies the Workflow

Task Token
    ↓
Identifies the Activity Task
```

So don't document the Task Token as:

> "The unique identifier of the Workflow."

A better definition is:

> **Task Token identifies a particular Activity Task execution and can be used to complete that Activity asynchronously.**

---

# Workflow ID vs Task Token

```text
┌─────────────────────────────────────┐
│             Workflow                │
│                                     │
│ Workflow ID: MAKE_TRIP_123          │
│                                     │
│   ┌─────────────────────────────┐   │
│   │ Activity                    │   │
│   │                             │   │
│   │ Task Token: <token>         │   │
│   └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

The Workflow ID identifies the Workflow.

The Task Token identifies the Activity Task.

---

# `Workflow.getInfo().getWorkflowId()`

Your Activity also contains:

```java
String workFlowId =
        Workflow.getInfo().getWorkflowId();
```

This should be treated carefully.

The Workflow API is normally used from Workflow code, while an Activity should use its Activity execution context / Activity information APIs when it needs information about the current Activity/Workflow execution.

If the goal is simply to obtain the current Workflow ID from inside the Activity, use the Activity execution context's information rather than relying on `Workflow.getInfo()` from Activity code.

For example, depending on the Temporal SDK version:

```java
String workflowId =
        activityExecutionContext.getInfo().getWorkflowId();
```

This keeps the code aligned with the Activity execution context.

---

# Forward Activities

These methods represent the normal business process:

```java
@ActivityMethod
public void createTrip(MakeTripDto makeTripDto);

@ActivityMethod
public void bookHotel(MakeTripDto makeTripDto);

@ActivityMethod
public void bookTransportation(MakeTripDto makeTripDto);
```

The Workflow executes them in order:

```text
Create Trip
     ↓
Book Hotel
     ↓
Book Transportation
```

---

# Compensation Activities

These methods undo successful operations when something later fails:

```java
@ActivityMethod
void cancelTrip(MakeTripDto makeTripDto);

@ActivityMethod
void cancelHotel(MakeTripDto makeTripDto);

@ActivityMethod
void cancelTransportation(MakeTripDto makeTripDto);
```

For example:

```text
Create Trip ✓
Book Hotel ✓
Book Transportation ✗
        ↓
Cancel Hotel
        ↓
Cancel Trip
```

These methods are the **compensating actions** used by the Saga.

---

# Complete Architecture

The complete design from Workflow to external services looks like this:

```text
                         Client
                           |
                           ↓
                    WorkflowClient
                           |
                           ↓
                   Temporal Service
                           |
                           ↓
                    CREATE_TRIP
                    Task Queue
                           |
                           ↓
                     Temporal Worker
                           |
                           ↓
              CreateTripWorkFlowImpl
                     (Workflow)
                           |
          ┌────────────────┼─────────────────┐
          ↓                ↓                 ↓
    Activity Stub     Activity Stub     Activity Stub
          |                |                 |
          ↓                ↓                 ↓
    createTrip()      bookHotel()     bookTransportation()
          |                |                 |
          ↓                ↓                 ↓
   Trip Service      Hotel Service    Transport Service
```

If something fails:

```text
                    Workflow
                       |
                       ↓
                  Activity fails
                       |
                       ↓
                 Saga Compensation
                       |
          ┌────────────┼────────────┐
          ↓            ↓            ↓
     cancelHotel   cancelTrip   cancelTransport
          |            |            |
          ↓            ↓            ↓
     Microservice   Microservice  Microservice
```

If compensation also fails:

```text
Compensation Failure
        |
        ↓
Retry compensation
        |
        ↓
Maximum attempts reached
        |
        ↓
RequireHumanReview
        |
        ↓
Failure Activity
        |
        ↓
Alert / Log / Manual Intervention
```

---

# Complete Responsibility Breakdown

```text
┌───────────────────────────────────────────────┐
│ Workflow                                      │
│                                               │
│ Decides WHAT happens and WHEN                 │
└───────────────────────┬───────────────────────┘
                        |
                        ↓
┌───────────────────────────────────────────────┐
│ Activity Interface                            │
│                                               │
│ Defines WHICH operations are available        │
└───────────────────────┬───────────────────────┘
                        |
                        ↓
┌───────────────────────────────────────────────┐
│ Activity Implementation                       │
│                                               │
│ Performs the ACTUAL business/technical work   │
└───────────────────────┬───────────────────────┘
                        |
                        ↓
┌───────────────────────────────────────────────┐
│ External Systems                              │
│                                               │
│ Microservices / DB / APIs / External Services │
└───────────────────────────────────────────────┘
```

---

# Simple Mental Model

Remember the three layers:

### Workflow

> **Orchestrates the process.**

```text
Create → Hotel → Transport
```

### Activity Interface

> **Defines the operations available to the Workflow.**

```text
createTrip()
bookHotel()
bookTransportation()
```

### Activity Implementation

> **Performs the actual operation.**

```text
Call Trip Service
Call Hotel Service
Call Transport Service
```

---

# Final Summary

The `CreateTripInterface` defines the contract for our Temporal Activities.

The `CreateTripImplementation` contains the actual business/technical implementation of those Activities.

The Workflow does not directly execute this implementation. Instead, it creates an Activity Stub:

```java
CreateTripInterface activities =
        Workflow.newActivityStub(
                CreateTripInterface.class,
                activityOptions()
        );
```

and calls:

```java
activities.createTrip(makeTripDto);
```

Temporal then schedules the Activity, and a Worker executes the corresponding implementation:

```text
Workflow
    ↓
Activity Stub
    ↓
Temporal Service
    ↓
Task Queue
    ↓
Worker
    ↓
CreateTripImplementation
    ↓
External Microservice
```

For normal Activities, the method returns and Temporal records the Activity completion.

For **asynchronous Activity completion**, we can obtain the Activity Task Token and call:

```java
activityExecutionContext.doNotCompleteOnReturn();
```

This tells Temporal that the Activity will be completed later, allowing an external completion mechanism to use the Task Token to identify the specific Activity execution.

### The key idea

> **Workflow = orchestrates.**
> **Activity Interface = defines the contract.**
> **Activity Implementation = performs the actual work.**
> **Task Token = identifies an Activity Task for asynchronous completion.**
> **`doNotCompleteOnReturn()` = tells Temporal that Activity completion will happen later.**
