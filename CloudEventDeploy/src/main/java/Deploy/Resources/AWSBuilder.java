package Deploy.Resources;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyResponse;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionUrlConfigResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/*
todo:
 Check for policies / roles / logGroup before creation
 Teardown

 */

public class AWSBuilder implements AWSConfigs {
    LambdaClient lambdaClient;
    CloudWatchLogsClient cloudWatchLogsClient;
    IamClient iamClient;
    String iamRoleName;
    String roleArn;

    public AWSBuilder(Region region) {
        lambdaClient = LambdaClient.builder().region(region).build();
        cloudWatchLogsClient = CloudWatchLogsClient.builder().region(region).build();
        iamClient = IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

    }

    public void createStackResources() {
        // 1. Create IAM Role
        // 2. Attach IAM policy to the newly created role
        // 3. Create a CloudWatch log group for the Lambda
        // 4. Put retention policy on the log group

        CreateLogGroupResponse logGroupResponse = createLogGroup(fnCfg);
        System.out.println("Created log group: " + logGroupResponse);
        PutRetentionPolicyResponse retentionPolicyResponse = putRetentionPolicy(fnCfg);
        System.out.println("Retention policy set: " + retentionPolicyResponse);

        if (!doesRoleExist(iamRoleName)) {
            IamResponse roleResponse = createIamRoleRequest();
            System.out.println("Role created: " + roleResponse);
            PutRolePolicyResponse policyResponse = putIamRolePolicyRequest();
            System.out.println("Policy created: " + policyResponse);

            // 3. Attach the policy to the role
            attachPolicyToRole(role.roleName(), createdPolicy.arn());
        } else {
            // Role already exists; check if the policy is attached
            System.out.println("Role [" + roleName + "] already exists. Checking policy attachment...");
            Role existingRole = getRole(roleName); // fetch the role object

            boolean policyAttached = isPolicyAttachedToRole(existingRole.roleName(), policyName);

            if (!policyAttached) {
                System.out.println("Policy [" + policyName + "] not attached to role. Checking if it exists.");
                // Check if the policy exists (by name)
                Optional<Policy> policyOpt = findPolicyByName(policyName);

                Policy policy;
                if (policyOpt.isEmpty()) {
                    // Policy does not exist -> create it
                    System.out.println("Policy [" + policyName + "] does NOT exist. Creating...");
                    policy = createManagedPolicy(policyName, policyDoc);
                } else {
                    // Policy exists already
                    policy = policyOpt.get();
                    System.out.println("Policy [" + policyName + "] already exists at ARN: " + policy.arn());
                }

                // Attach the policy
                attachPolicyToRole(existingRole.roleName(), policy.arn());
            } else {
                System.out.println("Policy [" + policyName + "] is already attached to role [" + roleName + "].");
            }
        }
    }

    private boolean doesRoleExist(String roleName) {
        try {
            iamClient.getRole(GetRoleRequest.builder().roleName(roleName).build());
            return true;
        } catch (NoSuchEntityException e) {
            return false;
        }
    }

    public void createLambdaRole() {

        IamResponse roleResponse = createIamRoleRequest();
        System.out.println("Role created: " + roleResponse);
        PutRolePolicyResponse policyResponse = putIamRolePolicyRequest();
        System.out.println("Policy created: " + policyResponse);
    }

    public void buildFunction(FunctionConfig fnCfg) {
        System.out.println("Creating Lambda function: " + fnCfg.functionName());
        try {
            CreateFunctionResponse createFnResponse = createLambdaFunction(fnCfg);
            System.out.println("Created Lambda function ARN: " + createFnResponse.functionArn());
        } catch (IOException e) {
            System.err.println("Error creating the Lambda function:");
            e.printStackTrace();
        }

        // 6. Create a function URL
        System.out.println("Creating function URL for: " + fnCfg.functionName());
        CreateFunctionUrlConfigResponse urlResponse = createFunctionUrl(fnCfg);
        System.out.println("Function URL created: " + urlResponse.functionUrl());
    }


    public IamResponse createIamRoleRequest() {
        CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                .roleName("LambdaBasicExecutionRole")
                .assumeRolePolicyDocument(AWSConfigs.createTrustPolicy())
                .description("Role for AWS Lambda to assume")
                .build();
        return iamClient.createRole(createRoleRequest);
    }

    public PutRolePolicyResponse putIamRolePolicyRequest() {
        PutRolePolicyRequest putRolePolicyRequest = PutRolePolicyRequest.builder()
                .roleName("LambdaBasicExecutionRole")
                .policyName("LambdaBasicExecutionPolicy")
                .policyDocument(AWSConfigs.createLambdaPolicy())
                .build();
        return iamClient.putRolePolicy(putRolePolicyRequest);
    }

    private CreateFunctionResponse createLambdaFunction(FunctionConfig fnCfg) throws IOException {
        byte[] functionCode = Files.readAllBytes(Paths.get(fnCfg.codeZipFilePath()));
        CreateFunctionRequest request = CreateFunctionRequest.builder()
                .functionName(fnCfg.functionName())
                .description(fnCfg.description())
                .role(roleArn)
                .handler(fnCfg.handler())
                .runtime(fnCfg.runtime())
                .architectures(fnCfg.architecture())
                .memorySize(fnCfg.memorySize())
                .timeout(fnCfg.timeout())
                .code(builder -> builder.zipFile(SdkBytes.fromByteArray(functionCode)))
                .build();
        return lambdaClient.createFunction(request);
    }

    private CreateFunctionUrlConfigResponse createFunctionUrl(FunctionConfig fnCfg) {
        CreateFunctionUrlConfigRequest urlRequest = CreateFunctionUrlConfigRequest.builder()
                .functionName(fnCfg.functionName())
                .authType(fnCfg.urlAuthType())
                .build();
        return lambdaClient.createFunctionUrlConfig(urlRequest);
    }

    private CreateLogGroupResponse createLogGroup(FunctionConfig fnCfg) {
        CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder()
                .logGroupName(fnCfg.logGroupName())
                .build();
        return cloudWatchLogsClient.createLogGroup(createLogGroupRequest);
    }

    private PutRetentionPolicyResponse putRetentionPolicy(FunctionConfig fnCfg) {
        PutRetentionPolicyRequest putRetentionRequest = PutRetentionPolicyRequest.builder()
                .logGroupName(fnCfg.logGroupName())
                .retentionInDays(fnCfg.retentionDays())
                .build();
        return cloudWatchLogsClient.putRetentionPolicy(putRetentionRequest);
    }

}
