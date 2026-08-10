
/**
    This is the implementation of the interface Activity which defines business logic to process the request.
*/


@Service
public class CreateTripImplementation implements CreateTripInterface{

    @Override
    public void createTrip(MakeTripDto makeTripDto) {


      
        /** 
        Task token is the unique identifier of the a particular workflow and it can be used to Ack if the task is success
       or not in the called service and then the line "activityExecutionContext.doNotCompleteOnReturn()" makes sure that the 
       temporal server waits for the response from the service. This is used most in Asynchronous communication.
        */   

        ActivityExecutionContext activityExecutionContext = Activity.getExecutionContext();
        byte [] taskToken = activityExecutionContext.getTaskToken();
        String workFlowId = Workflow.getInfo().getWorkflowId();

       /**
            Business logic to send request to a targated microservice.
         */

      
        activityExecutionContext.doNotCompleteOnReturn();
    }

    @Override
    public void bookHotel(MakeTripDto makeTripDto) {

    }

    @Override
    public void bookTransportation(MakeTripDto makeTripDto) {

    }

    @Override
    public void cancelTrip(MakeTripDto makeTripDto) {

    }

    @Override
    public void cancelHotel(MakeTripDto makeTripDto) {

    }

    @Override
    public void cancelTransportation(MakeTripDto makeTripDto) {

    }
}
