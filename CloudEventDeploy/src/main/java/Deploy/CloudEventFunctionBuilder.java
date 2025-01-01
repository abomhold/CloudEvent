package Deploy;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigResponse;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyResponse;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Demonstrates a simple builder pattern to create:
 *   1) A CloudWatch Log Group
 *   2) A Lambda function (Java 21) with given memory and timeout
 *   3) A Function URL with no auth
 *
 * This code uses only the AWS Lambda & CloudWatch Logs SDKs (AWS SDK for Java v2).
 */
public class CloudEventFunctionBuilder {

     /* ========================================================= */
    /*   The create() method that does all the AWS calls         */
    /* ========================================================= */

    public void create() throws IOException {

        // 1) Create or update the Log Group (CloudWatch Logs)
        createLogGroup();

        // 2) Create the Lambda function
        CreateFunctionResponse functionResponse = createLambdaFunction();

        // 3) Create the function URL config
        CreateFunctionUrlConfigResponse urlConfigResponse = createFunctionUrl(functionResponse.functionArn());

        // Display the results
        System.out.println("Created Lambda function ARN: " + functionResponse.functionArn());
        System.out.println("Lambda Function URL:        " + urlConfigResponse.functionUrl());
    }

    private CreateFunctionResponse createLambdaFunction() throws IOException {
        LambdaClient lambdaClient = LambdaClient.create();
        byte[] functionCode = Files.readAllBytes(Paths.get(codeZipFilePath));

        CreateFunctionRequest request = CreateFunctionRequest.builder()
                .functionName(functionName)
                .description(description)
                .role(roleArn)
                .handler(handler)
                .runtime(runtime)
                .architectures(architecture)
                .memorySize(memorySize)
                .timeout(timeout)
                .code(builder -> builder.zipFile(SdkBytes.fromByteArray(functionCode)))
                .build();

        return lambdaClient.createFunction(request);
    }

    private CreateFunctionUrlConfigResponse createFunctionUrl(String functionArn) {
        LambdaClient lambdaClient = LambdaClient.create();

        CreateFunctionUrlConfigRequest urlRequest = CreateFunctionUrlConfigRequest.builder()
                .functionName(functionName)
                .authType(FunctionUrlAuthType.NONE) // AuthType: NONE
                .build();

        return lambdaClient.createFunctionUrlConfig(urlRequest);
    }

    private void createLogGroup() {
        if (logGroupName == null || logGroupName.isEmpty()) {
            // If you want a default name, you can do so here
            this.logGroupName = "/aws/" + functionName;
        }

        CloudWatchLogsClient logsClient = CloudWatchLogsClient.create();

        // 1) Create the log group
        try {
            CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder()
                    .logGroupName(logGroupName)
                    .build();

            CreateLogGroupResponse createLogGroupResponse = logsClient.createLogGroup(createLogGroupRequest);
            System.out.println("Created Log Group: " + logGroupName);
        } catch (Exception e) {
            // If LogGroup already exists, you might get a ResourceAlreadyExistsException.
            // For simplicity, just printing.
            System.out.println("Log Group creation skipped or already exists: " + e.getMessage());
        }

        // 2) Set the retention policy
        try {
            PutRetentionPolicyRequest putRetentionRequest = PutRetentionPolicyRequest.builder()
                    .logGroupName(logGroupName)
                    .retentionInDays(retentionDays)
                    .build();

            PutRetentionPolicyResponse putRetentionResponse = logsClient.putRetentionPolicy(putRetentionRequest);
            System.out.println("Set retention policy for Log Group: " + logGroupName + " to " + retentionDays + " days.");
        } catch (Exception e) {
            System.out.println("Unable to set retention policy: " + e.getMessage());
        }
    }

    /* ========================================================= */
    /*                 Example usage (main)                      */
    /* ========================================================= */
    public static void main(String[] args) throws IOException {
        // Just an example usage. You must update the IAM role ARN and
        // the path to your .zip file containing the compiled Java code.

        String exampleIamRoleArn = "arn:aws:iam::123456789012:role/YourLambdaRole";
        String exampleZipPath = "/path/to/your/lambda-code.zip";

        new CloudEventFunctionBuilder()
            .functionName("my-sample-lambda")
            .description("Sample Lambda function - Java 21")
            .roleArn(exampleIamRoleArn)
            .handler("lambda.Entry::handleRequest")
            .runtime(Runtime.JAVA21)
            .architecture(Architecture.X86_64)
            .memorySize(1792)
            .timeout(900)
            .logGroupName("/aws/my-sample-lambda")
            .retentionDays(14)
            .codeZipFilePath(exampleZipPath)
            .create();
    }
}