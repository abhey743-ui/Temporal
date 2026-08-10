
@Service
@Slf4j
public class CreateTripWorkFowImpl implements CreateTripWorkFlowInterface {

    private final SearchAttributeKey<String> COMPENSATION_STATUS =
            SearchAttributeKey.forKeyword("compensation");
    private final SearchAttributeKey<String> FAILED_CUSTOMER_ID
             = SearchAttributeKey.forKeyword("customerId");

/** 

 The ActivityOptions is used to define the business rules for a particular activity that  should 
  be followed. We can configure different ActivityOptions for different services and actions
  depending upon business requirements.
*/
    private ActivityOptions activityOptions(){
           return  ActivityOptions.newBuilder()
                   .setStartToCloseTimeout(Duration.ofSeconds(10))
                   .setScheduleToCloseTimeout(Duration.ofSeconds(30))
                   .setRetryOptions(RetryOptions.newBuilder()
                           .setBackoffCoefficient(2)
                           .setMaximumAttempts(3)
                           .setInitialInterval(Duration.ofSeconds(2))
                           .setDoNotRetry(IllegalArgumentException.class.getName(),
                                   HttpServerErrorException.InternalServerError.class.getName()).build())

                   .build();

    }
    private ActivityOptions activityOptionsCompensation(){

           return ActivityOptions.newBuilder()
                   .setStartToCloseTimeout(Duration.ofSeconds(10))
                   .setRetryOptions(RetryOptions
                           .newBuilder()
                           .setBackoffCoefficient(3)
                           .setMaximumAttempts(15)
                           .setInitialInterval(Duration.ofSeconds(5))
                           .build())
                   .build();
    }

    private ActivityOptions alertCompensationFailure(){
         return ActivityOptions.newBuilder()
                 .setStartToCloseTimeout(Duration.ofSeconds(10))
                 .setScheduleToCloseTimeout(Duration.ofSeconds(30))
                 .setRetryOptions(RetryOptions.newBuilder().setBackoffCoefficient(5)
                         .setMaximumAttempts(10)
                         .build())
                 .build();

    }
    @Override
    public void makeTrip(MakeTripDto makeTripDto) {
        
      /** 
          Here we define the workflow logic where we feed the ActivityInterface.class
          and the its business loic logic we defined above for retries, startToCloseTimeout,
           scheduleToCloseTimeout and so that by using which we can execute the process with defined
            business rules for a task. We can define the different workflows with different business 
            requirement as discussed such as retries, startToCloseTimeout,
           scheduleToCloseTimeout
     */
           CreateTripInterface activities = Workflow.newActivityStub(CreateTripInterface.class,
                           activityOptions());
           CreateTripInterface compensation = Workflow.newActivityStub(CreateTripInterface.class
                   ,activityOptionsCompensation());

        FailureCompensationInterface failureCompensationInterface = Workflow
                .newActivityStub(FailureCompensationInterface.class,alertCompensationFailure());
       /**
              Saga.Options is used to define the rules for the saga pattern such as 
              .setParallelCompensation(false) it means it does the compensation call one by one not
              in parallel
         */
      
        Saga.Options sagaOption = new Saga.Options.Builder()
                .setParallelCompensation(false)
                .build();
        Saga saga = new Saga(sagaOption);


      /**
                    we always write the business logic in try and catch so that on error 
                    it can compensate. If there is any bunisness error for any microservice
                    then the compensation runs for all the 
                    calls before it.
      */

                        
      
           try{
               activities.createTrip(makeTripDto);
               saga.addCompensation(()-> compensation.cancelTrip(makeTripDto));

               activities.bookHotel(makeTripDto);
               saga.addCompensation(()-> compensation.cancelHotel(makeTripDto));

               activities.bookTransportation(makeTripDto);
               saga.addCompensation(()-> compensation.cancelTransportation(makeTripDto));
           } catch (RuntimeException e) {


             /**
                     Assume if the compensation call fails and reaches to the the max retry
                     limit then we need to notify the team and set it in the failure information
                     in the temporal server and the we can send the failure request to a particular 
                     microservices depending on the business logic.
            */
                 try{
                           saga.compensate();
                 } catch (Exception ex) {

                   
                       log.error("Failed to compensate the request");
                         Workflow.upsertTypedSearchAttributes(
                                 COMPENSATION_STATUS.valueSet("RequireHumanReview")
                         ,FAILED_CUSTOMER_ID.valueSet("SKLJDSLDKJWJDLDJDJL"));

                    failureCompensationInterface.updateLog(makeTripDto);


                 }
                throw e;

           }

    }
}
