
@Component
@AllArgsConstructor
public class CreateTripWorkFlowStarter {
    private WorkflowServiceStubs workflowServiceStub;

    public void createTrip(MakeTripDto makeTripDto ){
        WorkflowClient workflowClient = WorkflowClient.newInstance(workflowServiceStub);

        CreateTripWorkFlowInterface createTripWorkFlowInterface = workflowClient
                .newWorkflowStub(CreateTripWorkFlowInterface.class, WorkflowOptions
                        .newBuilder().setWorkflowId("MAKE_TRIP"+ makeTripDto.getEmail())
                        .setTaskQueue("CREATE_TRIP")
                        .build()
                );
    }
}
