
# Create Trip Workflow — Temporal Saga Pattern

## Overview

`CreateTripWorkFowImpl` is the **Temporal Workflow implementation** responsible for coordinating the trip creation process.

The trip creation process involves multiple operations, potentially across different microservices:

```text
Create Trip
     ↓
Book Hotel
     ↓
Book Transportation
```

The important problem is:

> What happens if one of these operations succeeds but a later operation fails?

For example:

```text
Create Trip       ✓
     ↓
Book Hotel        ✓
     ↓
Book Transportation ❌
```

Now we cannot simply roll everything back using a normal database transaction because these operations may belong to different microservices and databases.

This is where the **Saga Pattern** is useful.

We execute the operations forward, and if something fails, we execute **compensation actions** to undo the previously completed operations.

```text
Forward operations
───────────────────────────────>

Create Trip       ✓
Book Hotel        ✓
Book Transport    ❌
                         ↓
                  Compensation
                         ↓
Cancel Hotel      ✓
Cancel Trip       ✓
```

Temporal is responsible for reliably executing and coordinating this Workflow.

---

# Overall Architecture

The complete architecture can be understood as:

```text
                         Client / API
                              |
                              | Start Workflow
                              ↓
                    ┌─────────────────────┐
                    │  Workflow Client    │
                    └──────────┬──────────┘
                               |
                               ↓
                    ┌─────────────────────┐
                    │  Temporal Service   │
                    └──────────┬──────────┘
                               |
                         Task Queue
                       "CREATE_TRIP"
                               |
                               ↓
                    ┌─────────────────────┐
                    │   Temporal Worker   │
                    └──────────┬──────────┘
                               |
                               ↓
                 ┌───────────────────────────┐
                 │ CreateTripWorkFlowImpl    │
                 │                           │
                 │       Workflow            │
                 └────────────┬──────────────┘
                              |
               ┌──────────────┼───────────────┐
               ↓              ↓               ↓
        Create Trip       Book Hotel     Book Transport
          Activity          Activity         Activity
               |              |               |
               ↓              ↓               ↓
        Trip Service      Hotel Service   Transport Service
```

If one of the operations fails:

```text
                    Workflow
                       |
                       ↓
                Activity Failure
                       |
                       ↓
               Saga Compensation
                 /           \
                ↓             ↓
        Cancel Hotel      Cancel Trip
```

---

# Workflow Implementation

```java
@Service
@Slf4j
public class CreateTripWorkFowImpl
        implements CreateTripWorkFlowInterface {
```

This class is the **Workflow implementation**.

The Workflow is responsible for **orchestrating the business process**.

It should decide:

* Which Activity should execute
* In which order
* Which Activity options should be used
* What should happen when something fails
* Which compensation actions should run
* How failures should be reported

The Workflow is the **orchestrator**.

It does not normally perform the external business operations itself.

Instead, it calls Activities.

```text
Workflow
   |
   ├── Activity 1
   ├── Activity 2
   └── Activity 3
```

---

# Workflow vs Activity

This distinction is extremely important.

## Workflow

The Workflow defines the **business process and orchestration**.

Example:

```text
Create Trip
     ↓
Book Hotel
     ↓
Book Transportation
```

## Activity

The Activity performs the **actual external work**.

For example:

```text
Book Hotel Activity
       ↓
Call Hotel Microservice
       ↓
Save / update hotel booking
```

Therefore:

> **Workflow = What should happen and in what order?**

> **Activity = Actually perform the operation.**

---

# Search Attributes

The Workflow defines:

```java
private final SearchAttributeKey<String> COMPENSATION_STATUS =
        SearchAttributeKey.forKeyword("compensation");

private final SearchAttributeKey<String> FAILED_CUSTOMER_ID =
        SearchAttributeKey.forKeyword("customerId");
```

These are **Temporal Search Attributes**.

They allow us to store information about a Workflow that can later be used to **search/filter Workflow executions**.

For example, if compensation fails, we set:

```text
compensation = RequireHumanReview
customerId = ...
```

Then we can identify Workflows that require human attention.

Conceptually:

```text
Temporal Workflows
       |
       ├── compensation = Completed
       ├── compensation = RequireHumanReview
       ├── compensation = Completed
       └── compensation = RequireHumanReview
```

We can then search for:

```text
compensation = RequireHumanReview
```

and find the Workflows that need manual investigation.

---

# Why Search Attributes Are Useful

Imagine we have thousands of Workflow executions.

One Workflow fails compensation.

We don't want an engineer to manually inspect every Workflow.

Instead, we can mark it:

```text
compensation = RequireHumanReview
```

Then our operational team can search for those Workflow executions.

This is especially useful for:

* Operational monitoring
* Troubleshooting
* Human intervention
* Finding failed business processes
* Building operational dashboards

---

# Activity Options

The Workflow defines:

```java
private ActivityOptions activityOptions()
```

This method creates the Activity execution rules for normal business operations.

Activity Options control things such as:

* How long an Activity can run
* How long it can remain pending
* How many times it should retry
* How long to wait between retries
* Which exceptions should not be retried

---

# Normal Activity Options

```java
private ActivityOptions activityOptions() {

    return ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setScheduleToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(
                    RetryOptions.newBuilder()
                            .setBackoffCoefficient(2)
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setDoNotRetry(
                                    IllegalArgumentException.class.getName(),
                                    HttpServerErrorException.InternalServerError.class.getName()
                            )
                            .build()
            )
            .build();
}
```

This configuration is used for the normal business Activities.

---

# `StartToCloseTimeout`

```java
.setStartToCloseTimeout(Duration.ofSeconds(10))
```

This defines the maximum amount of time allowed for an Activity execution **once it starts**.

In this example:

```text
Activity starts
     ↓
Maximum execution time = 10 seconds
```

If the Activity does not complete within the configured time, Temporal treats it as timed out according to the Activity's timeout/retry configuration.

---

# `ScheduleToCloseTimeout`

```java
.setScheduleToCloseTimeout(Duration.ofSeconds(30))
```

This controls the maximum amount of time from when the Activity is scheduled until the Activity execution completes, including retry attempts.

Conceptually:

```text
Activity scheduled
       |
       ↓
Waiting / execution / retries
       |
       ↓
Maximum total time = 30 seconds
```

This gives us an overall time limit for the Activity execution.

---

# Retry Options

```java
.setRetryOptions(
    RetryOptions.newBuilder()
```

This defines how Temporal should retry an Activity when it fails.

Instead of manually writing:

```java
try {
    // call service
} catch (...) {
    // wait
    // retry
    // wait
    // retry
}
```

Temporal can handle the retry behavior for us.

---

# Initial Retry Interval

```java
.setInitialInterval(Duration.ofSeconds(2))
```

The first retry waits approximately:

```text
Failure
   ↓
Wait 2 seconds
   ↓
Retry
```

---

# Backoff Coefficient

```java
.setBackoffCoefficient(2)
```

This controls how the retry interval grows.

Conceptually:

```text
Initial interval = 2 seconds

Retry 1 → wait approximately 2 seconds
Retry 2 → wait approximately 4 seconds
Retry 3 → wait approximately 8 seconds
```

This is called **exponential backoff**.

The exact behavior can also be affected by other retry configuration such as maximum interval.

---

# Maximum Attempts

```java
.setMaximumAttempts(3)
```

This limits the number of Activity attempts.

Conceptually:

```text
Attempt 1
   ↓ failure
Attempt 2
   ↓ failure
Attempt 3
   ↓ failure
STOP
```

This prevents an Activity from retrying forever.

---

# Exceptions That Should Not Be Retried

```java
.setDoNotRetry(
    IllegalArgumentException.class.getName(),
    HttpServerErrorException.InternalServerError.class.getName()
)
```

Not every failure should be retried.

For example, if an input is invalid:

```text
Invalid input
     ↓
Retry?
     ↓
No
```

Retrying the same invalid request will usually produce the same failure.

Therefore, certain exceptions are explicitly configured as non-retryable.

The exact list should always be based on the application's business and technical requirements.

---

# Compensation Activity Options

The Workflow also has:

```java
private ActivityOptions activityOptionsCompensation()
```

These options are specifically for **compensation Activities**.

```java
.setStartToCloseTimeout(Duration.ofSeconds(10))
```

The compensation operation can run for up to the configured Activity execution timeout.

The retry policy is:

```java
.setBackoffCoefficient(3)
.setMaximumAttempts(15)
.setInitialInterval(Duration.ofSeconds(5))
```

This is intentionally more tolerant than the normal Activity retry policy.

---

# Why More Retries for Compensation?

Compensation is important because it is trying to undo something that has already happened.

For example:

```text
Create Trip ✓
Book Hotel ✓
Book Transportation ❌

Now:

Cancel Hotel
Cancel Trip
```

If cancellation temporarily fails because the Hotel Service is unavailable, we generally don't want to give up immediately.

Therefore:

```text
Cancel Hotel
     ↓
Failure
     ↓
Retry
     ↓
Failure
     ↓
Retry
     ↓
...
     ↓
Success
```

The business requirement determines how aggressive the retry policy should be.

---

# Compensation Failure Alert Options

The third Activity configuration is:

```java
private ActivityOptions alertCompensationFailure()
```

This is used for the Activity responsible for handling/reporting a compensation failure.

It has its own retry policy:

```java
.setBackoffCoefficient(5)
.setMaximumAttempts(10)
```

The idea is:

> If the system cannot successfully compensate a failed business process, we must make a serious attempt to notify or record that failure.

---

# Why Separate Activity Options?

We have three different Activity option configurations:

```text
activityOptions()
        ↓
Normal business Activities

activityOptionsCompensation()
        ↓
Compensation Activities

alertCompensationFailure()
        ↓
Failure notification / recording
```

We don't necessarily want the same retry behavior for all of them.

For example:

```text
Normal operation
→ 3 attempts

Compensation
→ 15 attempts

Failure notification
→ 10 attempts
```

The retry policy is part of the **business/operational requirement**.

---

# Creating the Normal Activity Stub

Inside the Workflow:

```java
CreateTripInterface activities =
        Workflow.newActivityStub(
                CreateTripInterface.class,
                activityOptions()
        );
```

This creates a typed Activity Stub for the normal Activities.

The Workflow can now use:

```java
activities.createTrip(...)
activities.bookHotel(...)
activities.bookTransportation(...)
```

The important thing is that these are **Activity calls**, not direct method calls to external services.

Temporal manages the Activity execution.

---

# Creating the Compensation Activity Stub

```java
CreateTripInterface compensation =
        Workflow.newActivityStub(
                CreateTripInterface.class,
                activityOptionsCompensation()
        );
```

Notice that we use the **same Activity interface**, but with different `ActivityOptions`.

Why?

Because normal operations and compensation operations have different retry requirements.

```text
Normal Activities
       ↓
activityOptions()

Compensation Activities
       ↓
activityOptionsCompensation()
```

This allows the same Activity implementation to be executed with different operational policies.

---

# Failure Compensation Activity

```java
FailureCompensationInterface failureCompensationInterface =
        Workflow.newActivityStub(
                FailureCompensationInterface.class,
                alertCompensationFailure()
        );
```

This Activity is responsible for handling the situation where **compensation itself fails**.

For example:

```text
Book Hotel ✓
Book Transport ❌

Compensate:
Cancel Hotel ❌
Cancel Hotel ❌
Cancel Hotel ❌
...
Maximum retries reached
```

At this point, the system needs to escalate the problem.

This Activity can be responsible for things such as:

* Recording the failure
* Sending an alert
* Updating another service
* Creating an operational ticket
* Notifying another microservice

The exact behavior depends on the business requirement.

---

# Saga Pattern

The Workflow creates:

```java
Saga.Options sagaOption =
        new Saga.Options.Builder()
                .setParallelCompensation(false)
                .build();

Saga saga = new Saga(sagaOption);
```

The Saga Pattern is used to manage distributed business transactions.

Because our operations may happen across different microservices, we cannot rely on one database transaction to roll everything back.

Instead, we define **compensating actions**.

Example:

```text
Forward:

Create Trip
     ↓
Book Hotel
     ↓
Book Transportation


Compensation:

Cancel Transportation
     ↓
Cancel Hotel
     ↓
Cancel Trip
```

---

# `setParallelCompensation(false)`

```java
.setParallelCompensation(false)
```

This means compensation actions are executed **sequentially rather than in parallel**.

For example:

```text
Cancel Transportation
        ↓
Cancel Hotel
        ↓
Cancel Trip
```

rather than:

```text
       ┌── Cancel Transportation
       │
       ├── Cancel Hotel
       │
       └── Cancel Trip
```

Sequential compensation can be useful when the order of rollback operations matters.

The correct choice depends on the dependencies between the business operations.

---

# Adding Compensation

After successfully completing an operation, we register its compensation.

Example:

```java
activities.createTrip(makeTripDto);

saga.addCompensation(
        () -> compensation.cancelTrip(makeTripDto)
);
```

This means:

> "If something later fails and the Saga needs to compensate, execute `cancelTrip()`."

This is important:

**We register the compensation only after the forward operation succeeds.**

Why?

Because if `createTrip()` fails, there is nothing to cancel.

---

# Complete Forward Flow

The Workflow executes:

```java
activities.createTrip(makeTripDto);

saga.addCompensation(
        () -> compensation.cancelTrip(makeTripDto)
);

activities.bookHotel(makeTripDto);

saga.addCompensation(
        () -> compensation.cancelHotel(makeTripDto)
);

activities.bookTransportation(makeTripDto);

saga.addCompensation(
        () -> compensation.cancelTransportation(makeTripDto)
);
```

The successful flow is:

```text
                Create Trip
                    ✓
                    ↓
             Register Cancel Trip
                    ↓
               Book Hotel
                    ✓
                    ↓
             Register Cancel Hotel
                    ↓
          Book Transportation
                    ✓
                    ↓
                  Done
```

If everything succeeds, no compensation is needed.

---

# What Happens If an Activity Fails?

Suppose:

```text
Create Trip       ✓
Book Hotel        ✓
Book Transportation ❌
```

The `catch` block executes.

```text
Book Transportation
        ↓
      FAILED
        ↓
   catch block
        ↓
saga.compensate()
```

The Saga executes the registered compensation actions.

Because compensation is configured as non-parallel, they execute sequentially.

Conceptually:

```text
Book Transportation ❌
        ↓
Compensation starts
        ↓
Cancel Hotel
        ↓
Cancel Trip
```

The failed Activity itself does not get a compensation action because its forward operation did not successfully complete.

---

# Why Compensation Is Added After Success

This is a very important design principle.

We do:

```java
activities.bookHotel(makeTripDto);

saga.addCompensation(
        () -> compensation.cancelHotel(makeTripDto)
);
```

not:

```java
saga.addCompensation(
        () -> compensation.cancelHotel(makeTripDto)
);

activities.bookHotel(makeTripDto);
```

The reason is simple:

```text
Operation succeeds
       ↓
Register how to undo it
```

If the operation never succeeded, there is nothing that needs to be undone.

---

# Compensation Failure

The most important failure scenario is:

```text
Create Trip       ✓
Book Hotel        ✓
Book Transport    ❌
        ↓
Cancel Hotel      ❌
        ↓
Retry
        ↓
Retry
        ↓
Maximum attempts reached
```

Now the system has a serious problem.

The original operation failed, but the system also failed to completely undo the successful operations.

This is why we have:

```java
try {
    saga.compensate();
} catch (Exception ex) {
    ...
}
```

---

# Human Review Status

If compensation itself fails, we update the Temporal Workflow Search Attributes:

```java
Workflow.upsertTypedSearchAttributes(
        COMPENSATION_STATUS.valueSet("RequireHumanReview"),
        FAILED_CUSTOMER_ID.valueSet("SKLJDSLDKJWJDLDJDJL")
);
```

This marks the Workflow as requiring human attention.

Conceptually:

```text
Workflow
   |
   ↓
Compensation Failed
   |
   ↓
compensation = RequireHumanReview
   |
   ↓
Operations Team
   |
   ↓
Investigate / Resolve
```

This is very useful operationally because the Workflow doesn't simply disappear into logs.

It becomes searchable.

---

# Failure Notification

After marking the Workflow:

```java
failureCompensationInterface.updateLog(makeTripDto);
```

This calls another Activity responsible for handling the failure.

Depending on the business design, this could:

```text
Update failure record
       ↓
Send notification
       ↓
Alert operations team
       ↓
Notify another service
```

The important idea is that **compensation failure becomes an explicit business/operational event**.

---

# Why We Re-Throw the Original Exception

At the end:

```java
throw e;
```

We re-throw the original exception after attempting compensation.

Why?

Because the original Workflow operation failed.

We don't want to hide that failure simply because we attempted compensation.

The system should still know:

```text
Original Workflow
      ↓
FAILED
```

while separately recording:

```text
Compensation
      ↓
SUCCESS / FAILED
```

This gives us two important pieces of information:

1. The original business operation failed.
2. Whether the system successfully compensated it.

---

# Complete Failure Scenario

Let's take the complete example.

### Step 1 — Create Trip

```text
Create Trip ✓
```

Register:

```text
Cancel Trip
```

### Step 2 — Book Hotel

```text
Book Hotel ✓
```

Register:

```text
Cancel Hotel
```

### Step 3 — Book Transportation

```text
Book Transportation ❌
```

Now compensation starts.

### Step 4 — Cancel Hotel

```text
Cancel Hotel
     ↓
Failure
     ↓
Retry
     ↓
Retry
     ↓
Success
```

### Step 5 — Cancel Trip

```text
Cancel Trip
     ↓
Success
```

Final state:

```text
Create Trip       ✓ → Cancelled
Book Hotel        ✓ → Cancelled
Book Transport    ❌
```

The system is now compensated.

---

# What If Compensation Fails?

Example:

```text
Create Trip       ✓
Book Hotel        ✓
Book Transport    ❌
        ↓
Cancel Hotel      ❌
        ↓
15 retries
        ↓
Still failing
```

Now:

```text
saga.compensate()
        ↓
throws Exception
        ↓
Mark Workflow:
compensation = RequireHumanReview
        ↓
Update failure log
        ↓
Re-throw original exception
```

Architecture:

```text
                  Workflow
                     |
                     ↓
             Business Activity
                     |
                   FAIL
                     |
                     ↓
               Saga Compensate
                     |
                ┌────┴────┐
                ↓         ↓
          Compensation  Compensation
             Retry         Retry
                |            |
                └────┬───────┘
                     ↓
                  Failure
                     |
                     ↓
          RequireHumanReview
                     |
                     ↓
             Failure Activity
                     |
                     ↓
             Operations Team
```

---

# Full Workflow Architecture

The complete design can be viewed as:

```text
                         ┌──────────────┐
                         │    Client    │
                         └──────┬───────┘
                                │
                                ↓
                       Start Workflow
                                │
                                ↓
                    ┌─────────────────────┐
                    │ CreateTrip Workflow │
                    └──────────┬──────────┘
                               │
                ┌──────────────┼───────────────┐
                ↓              ↓               ↓
          Create Trip      Book Hotel      Book Transport
           Activity         Activity          Activity
                │              │               │
                ↓              ↓               ↓
          Trip Service     Hotel Service   Transport Service
                │              │               │
                └──────────────┼───────────────┘
                               │
                         Something fails
                               │
                               ↓
                         Saga Compensate
                               │
                ┌──────────────┼───────────────┐
                ↓              ↓               ↓
           Cancel Hotel    Cancel Trip    Other Compensation
                │              │               │
                └──────────────┼───────────────┘
                               │
                     Compensation succeeds?
                          /           \
                        YES            NO
                         |              |
                         ↓              ↓
                       Done      RequireHumanReview
                                        |
                                        ↓
                              Failure Compensation
                                   Activity
                                        |
                                        ↓
                                Alert / Logging
```

---

# Why Temporal Is Useful Here

Without Temporal, we would have to manually implement a lot of infrastructure for:

* Retries
* Retry delays
* Activity timeouts
* Tracking workflow progress
* Failure recovery
* Compensation
* Long-running execution
* Workflow state
* Operational visibility

Temporal provides the workflow execution infrastructure, while our code defines the business process.

So our responsibility becomes:

```text
Our Code
    ↓
Define Business Process
    ↓
Define Activities
    ↓
Define Compensation
    ↓
Define Retry / Timeout Policies
```

Temporal handles the reliable execution of that process.

---

# Important Design Principle

The Workflow should primarily be the **orchestrator**.

It should answer:

```text
What should happen?
In what order?
What should happen if something fails?
How should we compensate?
```

Activities should answer:

```text
How do I actually perform this operation?
```

For example:

```text
Workflow
    ↓
"Book Hotel"

Activity
    ↓
Call Hotel Microservice
```

This separation keeps the architecture clean.

---

# Quick Reference

| Component                       | Responsibility                                        |
| ------------------------------- | ----------------------------------------------------- |
| `CreateTripWorkFowImpl`         | Orchestrates the trip business process                |
| `ActivityOptions`               | Defines Activity execution rules                      |
| `RetryOptions`                  | Defines retry behavior                                |
| `CreateTripInterface`           | Defines business Activities                           |
| `Workflow.newActivityStub()`    | Creates an Activity Stub for the Workflow             |
| `Saga`                          | Coordinates compensation actions                      |
| `addCompensation()`             | Registers how a successful operation should be undone |
| `saga.compensate()`             | Executes registered compensation actions              |
| `SearchAttributeKey`            | Defines searchable Workflow metadata                  |
| `upsertTypedSearchAttributes()` | Updates searchable Workflow information               |
| `FailureCompensationInterface`  | Handles/report compensation failure                   |

---

# Final Mental Model

Remember the Workflow like this:

```text
                WORKFLOW
                   |
                   ↓
             Orchestrates
                   |
        ┌──────────┼──────────┐
        ↓          ↓          ↓
     Activity   Activity   Activity
        ↓          ↓          ↓
     Success    Success     FAIL
        |          |          |
        ↓          ↓          ↓
   Register     Register    Trigger
 Compensation  Compensation  Saga
                              |
                              ↓
                         Compensate
                              |
                    ┌─────────┴─────────┐
                    ↓                   ↓
                 Success              Failure
                    |                   |
                    ↓                   ↓
                   Done          Human Review
                                        |
                                        ↓
                                Failure Activity
```

### The one thing to remember

> **The Workflow orchestrates the business process. Activities perform the actual work. Saga provides compensation when the process fails. ActivityOptions define how each Activity should execute and retry. Search Attributes make important Workflow state searchable for operations and human intervention.**
