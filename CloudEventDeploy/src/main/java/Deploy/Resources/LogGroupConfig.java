package Deploy.Resources;

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyResponse;
import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.Runtime;


    }


    private void createLogGroup() {
        if (logGroupName == null || logGroupName.isEmpty()) {
            // If you want a default name, you can do so here
            this.logGroupName = "/aws/" + functionName;
        }

        CloudWatchLogsClient logsClient = CloudWatchLogsClient.create();

        // 1) Create the log group
        try {
            CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder().logGroupName(logGroupName).build();

            CreateLogGroupResponse createLogGroupResponse = logsClient.createLogGroup(createLogGroupRequest);
            System.out.println("Created Log Group: " + logGroupName);
        } catch (Exception e) {
            // If LogGroup already exists, you might get a ResourceAlreadyExistsException.
            // For simplicity, just printing.
            System.out.println("Log Group creation skipped or already exists: " + e.getMessage());
        }

        // 2) Set the retention policy
        try {
            PutRetentionPolicyRequest putRetentionRequest = PutRetentionPolicyRequest.builder().logGroupName(logGroupName).retentionInDays(retentionDays).build();

            PutRetentionPolicyResponse putRetentionResponse = logsClient.putRetentionPolicy(putRetentionRequest);
            System.out.println("Set retention policy for Log Group: " + logGroupName + " to " + retentionDays + " days.");
        } catch (Exception e) {
            System.out.println("Unable to set retention policy: " + e.getMessage());
        }
    }
}
