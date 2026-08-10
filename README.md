# Temporal

## What is Temporal?

**Temporal** is a workflow orchestration platform used to build reliable, long-running, and distributed applications.

It helps us write business logic as **workflows** and execute that logic reliably even when things fail.

For example, imagine an order-processing system:

```text
Order Created
     ↓
Payment
     ↓
Reserve Inventory
     ↓
Create Shipment
     ↓
Send Confirmation Email
```

Normally, if the application crashes after payment but before inventory is reserved, we need to figure out what happened and how to continue.

Temporal is designed to handle these kinds of situations.

---

## Why do we need Temporal?

In a distributed system, many things can go wrong:

* Application crashes
* Server restarts
* Network failures
* Database failures
* Third-party API failures
* Temporary service unavailability
* Long-running operations
* Scheduled operations
* Retry requirements

Without a workflow engine, we may need to manually implement:

* Retry logic
* State management
* Failure recovery
* Scheduling
* Timeouts
* Compensation logic
* Tracking workflow progress

Temporal provides these capabilities as part of the platform.

---

# Core Concepts

Temporal mainly has a few important concepts that I need to remember:

```text
Client
  ↓
Workflow
  ↓
Activities
  ↓
External Systems
```

## 1. Workflow

A **Workflow** defines the overall business process.

It describes **what should happen and in what order**.

Example:

```java
public class OrderWorkflowImpl implements OrderWorkflow {

    private final PaymentActivity payment =
            Workflow.newActivityStub(PaymentActivity.class);

    private final InventoryActivity inventory =
            Workflow.newActivityStub(InventoryActivity.class);

    @Override
    public void processOrder(String orderId) {

        payment.processPayment(orderId);

        inventory.reserveInventory(orderId);
    }
}
```

The workflow should mainly contain the orchestration/business flow.

---

## 2. Activity

An **Activity** performs the actual external or non-deterministic work.

Examples:

* Calling a REST API
* Saving data to a database
* Sending an email
* Charging a payment
* Calling another microservice
* Uploading a file

Example:

```java
public class PaymentActivityImpl implements PaymentActivity {

    @Override
    public void processPayment(String orderId) {

        // Call payment service
        // Save payment information
        // Handle external API communication
    }
}
```

A useful way to remember this:

> **Workflow = What should happen?**
> **Activity = Actually perform the work.**

---

# Workflow vs Activity

| Workflow                               | Activity                     |
| -------------------------------------- | ---------------------------- |
| Defines business process               | Performs actual work         |
| Orchestrates activities                | Executes external operations |
| Must be deterministic                  | Can perform external I/O     |
| Should not directly call external APIs | Can call APIs                |
| Should not directly access database    | Can access database          |
| Maintains workflow state               | Performs individual tasks    |

---

# Worker

A **Worker** is the process that executes Temporal Workflows and Activities.

The Worker:

1. Connects to Temporal
2. Polls a Task Queue
3. Receives workflow/activity tasks
4. Executes the corresponding code
5. Reports the result back to Temporal

Conceptually:

```text
                Temporal Server
                      |
                Task Queue
                      |
                  Worker
                 /      \
          Workflow     Activity
```

---

# Task Queue

A **Task Queue** is used to route work to Workers.

For example:

```text
Order Task Queue
        ↓
Order Worker
        ↓
Order Workflow
```

Multiple Workers can listen to the same Task Queue.

This allows us to scale workers horizontally.

```text
                 Task Queue
                /    |    \
               /     |     \
         Worker 1 Worker 2 Worker 3
```

---

# Temporal Server

The Temporal Server is responsible for managing workflow execution and maintaining the workflow's durable state/history.

Our application communicates with Temporal rather than manually managing all workflow state.

Conceptually:

```text
Spring Boot Application
          |
          | Start Workflow
          ↓
   Temporal Server
          |
          ↓
      Task Queue
          |
          ↓
        Worker
          |
          ↓
       Activity
          |
          ↓
 External Service / DB / API
```

---

# Why Temporal is different from normal asynchronous processing

Suppose we use a normal message queue:

```text
Service A
   ↓
Message Queue
   ↓
Service B
```

We still need to think about:

* What happened to the message?
* Did the operation complete?
* Should it retry?
* How many times?
* What happens after a server crash?
* Where is the current state?
* How do we resume a long-running process?

Temporal provides a programming model specifically designed around these workflow concerns.

---

# Retries

One of the useful features of Temporal is automatic retry handling for Activities.

For example:

```text
Activity
   ↓
Failure
   ↓
Retry
   ↓
Failure
   ↓
Retry
   ↓
Success
```

Instead of implementing all retry logic manually, we can configure retry policies.

Example:

```java
ActivityOptions options = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(5))
        .setRetryOptions(
                RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .build()
        )
        .build();
```

The exact retry configuration should depend on the business requirement.

---

# Timeouts

Temporal supports different types of timeouts.

For example, an Activity may be allowed to run for a specific amount of time.

```java
ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(5))
        .build();
```

This prevents an Activity from running indefinitely.

---

# Long-Running Workflows

Temporal is especially useful when a process takes a long time.

For example:

```text
Application / Order
        ↓
Payment
        ↓
Wait for approval
        ↓
Wait 2 days
        ↓
Check approval
        ↓
Ship order
```

We don't want to keep a Java thread running for two days.

Temporal allows the workflow to wait without requiring us to keep a normal application thread occupied for the entire period.

---

# Signals

A **Signal** allows an external application to send information to a running Workflow.

For example:

```text
Customer
   ↓
Approve Order
   ↓
Signal
   ↓
Running Workflow
```

The Workflow can react to that signal and continue processing.

---

# Queries

A **Query** allows us to retrieve information from a running Workflow.

For example:

```text
Client
  ↓
"What is the current order status?"
  ↓
Temporal Workflow
  ↓
"Waiting for payment"
```

Queries are useful when we need to inspect workflow state.

---

# Timers

Temporal Workflows can use timers to wait until a specific duration has passed.

Example:

```java
Workflow.sleep(Duration.ofDays(2));
```

This can be useful for business processes such as:

```text
Order Created
      ↓
Wait 2 days
      ↓
Send Reminder
```

The important idea is that we don't need to manually manage a scheduled job for every individual workflow execution.

---

# Durability

One of the most important ideas behind Temporal is **durable execution**.

If a Worker crashes while processing a workflow, the workflow can continue from its recorded history when another Worker takes over.

Conceptually:

```text
Workflow
   ↓
Step 1 ✓
   ↓
Step 2 ✓
   ↓
Worker crashes ❌
   ↓
New Worker
   ↓
Continue Workflow
   ↓
Step 3
```

This is one of the main reasons to use Temporal.

---

# Temporal with Spring Boot

Temporal can be integrated into a Spring Boot application.

A typical architecture might look like:

```text
                    ┌─────────────────┐
                    │  Spring Boot    │
                    │    Service      │
                    └────────┬────────┘
                             │
                             │ Start Workflow
                             ↓
                    ┌─────────────────┐
                    │ Temporal Server │
                    └────────┬────────┘
                             │
                        Task Queue
                             │
                             ↓
                    ┌─────────────────┐
                    │ Temporal Worker │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    ↓                 ↓
              Workflow            Activities
                                      │
                         ┌────────────┼────────────┐
                         ↓            ↓            ↓
                       DB         REST API      Message
```

---

# When should I use Temporal?

Temporal can be a good choice when the application has:

* Complex business workflows
* Long-running processes
* Multiple steps that must execute reliably
* Retry requirements
* External API calls
* Human approval processes
* Scheduled processes
* Compensation/rollback workflows
* Distributed transactions
* Processes that must survive application crashes

Examples:

### Payment workflow

```text
Create Order
   ↓
Process Payment
   ↓
Reserve Inventory
   ↓
Create Shipment
   ↓
Send Email
```

### Loan approval

```text
Application
   ↓
Credit Check
   ↓
Document Verification
   ↓
Manual Approval
   ↓
Loan Creation
```

### User onboarding

```text
Create Account
   ↓
Send Verification Email
   ↓
Wait for Verification
   ↓
Create Profile
   ↓
Send Welcome Email
```

---

# When might I NOT need Temporal?

Temporal is not something I should automatically add to every Spring Boot application.

For a simple operation like:

```text
HTTP Request
    ↓
Database Save
    ↓
HTTP Response
```

Temporal may be unnecessary.

I should first understand whether I actually have a workflow/reliability problem that requires workflow orchestration.

---

# Important Rule: Workflow Determinism

One of the most important things to remember when writing Temporal Workflows is **determinism**.

A Workflow may be replayed from its history.

Therefore, Workflow code must behave consistently when replayed.

I should be careful about directly using things such as:

```java
new Random()
System.currentTimeMillis()
UUID.randomUUID()
```

or making direct external calls from Workflow code.

Instead, Temporal provides APIs and mechanisms designed for these situations.

External operations should generally be performed through **Activities**.

---

# Simple Mental Model

The easiest way I remember Temporal is:

```text
Workflow
    =
Business Process / Orchestration

Activity
    =
Actual Work

Worker
    =
Executes Workflow + Activities

Task Queue
    =
Routes Work to Workers

Temporal Server
    =
Manages Durable Workflow Execution
```

Or even simpler:

> **Temporal helps me write reliable business workflows that can survive failures, retries, restarts, and long waiting periods.**

---

# My Quick Reference

When I encounter a requirement like:

> "This process has many steps, can fail, needs retries, may take a long time, and must continue even if my application crashes."

I should consider **Temporal**.

Basic flow:

```text
Client
  ↓
Start Workflow
  ↓
Temporal
  ↓
Task Queue
  ↓
Worker
  ↓
Workflow
  ↓
Activities
  ↓
External Systems
```

---

# Things I Should Remember

* Temporal is a **workflow orchestration platform**.
* A Workflow defines the business process.
* Activities perform external/non-deterministic work.
* Workers execute Workflows and Activities.
* Task Queues distribute work to Workers.
* Temporal provides durable execution.
* Activities can be retried automatically.
* Workflows can be long-running.
* Workflows can wait using timers.
* Signals can send information to running Workflows.
* Queries can retrieve Workflow information.
* Workflow code must be deterministic.
* External API/database operations should generally happen in Activities.
* Temporal is useful for complex, reliable, distributed business processes.
* It is not necessary for every simple CRUD operation.

---

# Example Scenario to Remember

Imagine an e-commerce order:

```text
                  Order Workflow
                        │
          ┌─────────────┼─────────────┐
          ↓             ↓             ↓
     Payment        Inventory      Shipping
     Activity        Activity       Activity
          │             │             │
          ↓             ↓             ↓
    Payment API       Database      Shipping API
```

If the Shipping API temporarily fails:

```text
Shipping Activity
       ↓
     Failure
       ↓
      Retry
       ↓
      Retry
       ↓
    Success
```

If the Worker crashes:

```text
Worker
  ↓
Crash ❌
  ↓
Another Worker
  ↓
Workflow continues
```

This is the main value of Temporal: **I can focus on writing the business workflow instead of manually building all the infrastructure required to make that workflow reliable.**

