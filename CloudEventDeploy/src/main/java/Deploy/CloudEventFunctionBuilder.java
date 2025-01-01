package Deploy;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.IamException;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;

import java.util.Map;

/// * ========================================================= */
/// *                 Example usage (main)                      */
/// * ========================================================= */
//public static void main(String[] args) throws IOException {
//    // Just an example usage. You must update the IAM role ARN and
//    // the path to your .zip file containing the compiled Java code.
//
//    String exampleIamRoleArn = "arn:aws:iam::123456789012:role/YourLambdaRole";
//    String exampleZipPath = "/path/to/your/lambda-code.zip";
//
//    new CloudEventFunctionBuilder()
//            .functionName("my-sample-lambda")
//            .description("Sample Lambda function - Java 21")
//            .roleArn(exampleIamRoleArn)
//            .handler("lambda.Entry::handleRequest")
//            .runtime(Runtime.JAVA21)
//            .architecture(Architecture.X86_64)
//            .memorySize(1792)
//            .timeout(900)
//            .logGroupName("/aws/my-sample-lambda")
//            .retentionDays(14)
//            .codeZipFilePath(exampleZipPath)
//            .create();
//}
//

public class CloudEventFunctionBuilder {

    public record Policy(String version, Map<String, Object> statement) {

        public static String createLambdaIamRole(String roleName) {
            Region region = Region.AWS_GLOBAL; // IAM is global
            try (IamClient iamClient = IamClient.builder()
                    .region(region)
                    .credentialsProvider(ProfileCredentialsProvider.create())
                    .build()) {

                // 1. Create the role (trust policy)
                CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                        .roleName(roleName)
                        .assumeRolePolicyDocument(trustPolicy)
                        .description("Role for AWS Lambda to assume")
                        .build();

                CreateRoleResponse createRoleResponse = iamClient.createRole(createRoleRequest);
                String roleArn = createRoleResponse.role().arn();
                System.out.println("Created role with ARN: " + roleArn);

                // 2. Attach an inline policy (basic Lambda execution policy)
                PutRolePolicyRequest putRolePolicyRequest = PutRolePolicyRequest.builder()
                        .roleName(roleName)
                        .policyName("LambdaBasicExecutionPolicy")
                        .policyDocument(inlinePolicy)
                        .build();

                iamClient.putRolePolicy(putRolePolicyRequest);
                System.out.println("Attached inline policy for logs.");

                return roleArn;
            } catch (IamException e) {
                System.err.println("Error creating IAM role: " + e.awsErrorDetails().errorMessage());
                throw e;
            }
        }

        // Example usage:
        public static void main(String[] args) {
            String roleName = "MyLambdaRoleDemo";
            String newRoleArn = createLambdaIamRole(roleName);
            System.out.println("Role ARN is: " + newRoleArn);
        }

        public class LambdaRoleCreator {
        }
    }
}