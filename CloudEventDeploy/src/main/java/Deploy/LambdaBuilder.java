package Deploy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.*;

import java.util.Map;
import java.util.Objects;

/*
todo:
 Delete and redeploy or update?
    Delete lambda before creation
    Update labda code and config if exists
    Lambda url sticks around after lambda deletion?
 Teardown
 */

public class LambdaBuilder {
    final Region region;
    String iamRoleName = "LambdaBasicExecutionRole";
    String logGroupName = "/aws/TestingTrace";
    int logGroupRetention = 30;
    LambdaClient lambdaClient;
    CloudWatchLogsClient cloudWatchLogsClient;
    IamClient iamClient;
    String roleArn;
    FunctionUrlAuthType urlAuthType = FunctionUrlAuthType.NONE;

    public LambdaBuilder(Region region) {
        this.region = region;
        lambdaClient = LambdaClient.builder().region(region).build();
        cloudWatchLogsClient = CloudWatchLogsClient.builder().region(region).build();
        iamClient = IamClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
        roleArn = createLambdaRole();
        createLambdaLogGroup();
    }

    public static void main(String[] args) {
        LambdaBuilder awsBuilder = new LambdaBuilder(Region.US_EAST_1);
        String functionArn = awsBuilder.buildDefaultFunction();
        System.out.println(functionArn);
        String functionUrl = awsBuilder.createFunctionUrl(functionArn);
        System.out.println(functionUrl);

    }

    public String buildDefaultFunction() {
        return buildFunction(
                "tempLambdaFunction1",
                "lambda.Entry:handleRequest",
                "Default description",
                "lambda-trace.jar",
                Runtime.JAVA21,
                Architecture.X86_64,
                1800,
                900
        );
    }

    public String buildFunction(
            String functionName,
            String handler,
            String description,
            String codeZipFilePath,
            Runtime runtime,
            Architecture architecture,
            int memorySize,
            int timeout
    ) {
        String functionArn;
        try (var fileStream = getClass().getClassLoader().getResourceAsStream(codeZipFilePath)) {
            CreateFunctionRequest createFunctionRequest = CreateFunctionRequest.builder()
                    .functionName(functionName)
                    .description(description)
                    .role(roleArn)
                    .handler(handler)
                    .runtime(runtime)
                    .architectures(architecture)
                    .memorySize(memorySize)
                    .timeout(timeout)
                    .code(codeBuilder -> codeBuilder.zipFile(
                            SdkBytes.fromInputStream(Objects.requireNonNull(fileStream)))
                    )
                    .build();

            CreateFunctionResponse createFnResponse = lambdaClient.createFunction(createFunctionRequest);
            functionArn = createFnResponse.functionArn();
        } catch (ResourceConflictException e) {
            DeleteFunctionRequest dfr = DeleteFunctionRequest
                    .builder()
                    .functionName(functionName)
                    .build();
            lambdaClient.deleteFunction(dfr);
            functionArn = buildFunction(
                    functionName,
                    handler,
                    description,
                    codeZipFilePath,
                    runtime,
                    architecture,
                    memorySize,
                    timeout
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return functionArn;
    }

    public String createFunctionUrl(String functionName) {
        String functionUrl;
        try {
            CreateFunctionUrlConfigRequest urlRequest = CreateFunctionUrlConfigRequest.builder()
                    .functionName(functionName)
                    .authType(urlAuthType)
                    .build();

            CreateFunctionUrlConfigResponse urlResponse = lambdaClient.createFunctionUrlConfig(urlRequest);
            functionUrl = urlResponse.functionUrl();
        } catch (ResourceConflictException e) {
            GetFunctionUrlConfigRequest fur = GetFunctionUrlConfigRequest.builder().functionName(functionName).build();
            functionUrl = lambdaClient.getFunctionUrlConfig(fur).functionUrl();
        }
        return functionUrl;
    }

    //<editor-fold desc="Resource methods">
    private void createLambdaLogGroup() {
        try {
            CreateLogGroupResponse logGroupResponse = createLogGroup();
            putRetentionPolicy();
        } catch (ResourceAlreadyExistsException e) {
            System.out.println("Log group already exists: " + e);
        }
    }

    private CreateLogGroupResponse createLogGroup() {
        CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder()
                .logGroupName(logGroupName)
                .build();
        return cloudWatchLogsClient.createLogGroup(createLogGroupRequest);
    }

    public String createLambdaRole() {
        var roleRequest = GetRoleRequest.builder()
                .roleName(iamRoleName)
                .build();

        try {
            return iamClient.getRole(roleRequest).role().arn();
        } catch (NoSuchEntityException e) {
            // If the role does NOT exist, create it and attach the policy
            IamResponse roleResponse = createIamRoleRequest();

            //TODO: handle failure of putPolicy
            PutRolePolicyResponse policyResponse = putIamRolePolicyRequest();

            return roleResponse.getValueForField("Arn", String.class)
                    .orElseThrow(() -> new RuntimeException("Unable to retrieve ARN for newly created role."));
        }
    }

    public IamResponse createIamRoleRequest() {
        CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                .roleName("LambdaBasicExecutionRole")
                .assumeRolePolicyDocument(createTrustPolicy())
                .description("Role for AWS Lambda to assume")
                .build();
        System.out.println("Creating IAM role request: " + createRoleRequest);
        return iamClient.createRole(createRoleRequest);
    }

    public PutRolePolicyResponse putIamRolePolicyRequest() {
        PutRolePolicyRequest putRolePolicyRequest = PutRolePolicyRequest.builder()
                .roleName("LambdaBasicExecutionRole")
                .policyName("LambdaBasicExecutionPolicy")
                .policyDocument(createLambdaPolicy())
                .build();
        return iamClient.putRolePolicy(putRolePolicyRequest);
    }

    private void putRetentionPolicy() {
        PutRetentionPolicyRequest putRetentionRequest = PutRetentionPolicyRequest.builder()
                .logGroupName(logGroupName)
                .retentionInDays(logGroupRetention)
                .build();
        cloudWatchLogsClient.putRetentionPolicy(putRetentionRequest);
    }

    public String createTrustPolicy() {
        Map<String, Object> policyMap = Map.of(
                "Version", "2012-10-17",
                "Statement", Map.of(
                        "Effect", "Allow",
                        "Principal", Map.of("Service", "lambda.amazonaws.com"),
                        "Action", "sts:AssumeRole"
                )
        );
        try {
            return new ObjectMapper().writeValueAsString(policyMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting trust policy to JSON", e);
        }
    }

    public String createLambdaPolicy() {
        Map<String, Object> policyMap = Map.of(
                "Version", "2012-10-17",
                "Statement", Map.of(
                        "Effect", "Allow",
                        "Action", new String[]{
                                "logs:CreateLogGroup",
                                "logs:CreateLogStream",
                                "logs:PutLogEvents"
                        },
                        "Resource", "*"
                )
        );
        try {
            return new ObjectMapper().writeValueAsString(policyMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting Lambda policy to JSON", e);
        }
    }
    //</editor-fold>

}