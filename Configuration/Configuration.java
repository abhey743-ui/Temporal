


@org.springframework.context.annotation.Configuration
public class Configuration {


  /**
      We need to define the configurtion these configuration beans for the temporal setup and this is well discussed in the README.md
*/    
  
  @Bean
    public WorkflowServiceStubs workflowServiceStubs(){
          return WorkflowServiceStubs.newInstance();

    }
    public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs){
         return WorkflowClient.newInstance(workflowServiceStubs);
    }
    public WorkerFactory workerFactory(WorkflowClient workflowClient){
           return WorkerFactory.newInstance(workflowClient);

    }
}
