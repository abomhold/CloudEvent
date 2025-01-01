package Deploy.Resources;


import Deploy.CloudEventFunctionBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.util.Map;


interface AWSConfigs {

    // Define a trust policy that allows Lambda
    // This is the "assume role" policy
    static String createTrustPolicy() {
        return new CloudEventFunctionBuilder.Policy("2012-10-17",
                Map.of("Effect",
                        "Allow",
                        "Pricipal",
                        Map.of("Service", "lambda.amazonaws.com"),
                        "Action",
                        "sts:AssumeRole"))
                .toString();
    }

    // Define an inline policy that allows basic logging, etc.
    // Minimal example grants Lambda permission to write logs to CloudWatch
    // Adjust as needed (e.g., S3 access, DynamoDB, etc.)
    static String createLambdaPolicy() {
        return new CloudEventFunctionBuilder.Policy("2012-10-17",
                Map.of("Effect",
                        "Allow",
                        "Action",
                        new String[]{
                                "logs:CreateLogGroup",
                                "logs:CreateLogStream",
                                "logs:PutLogEvents"},
                        "Resource",
                        "*"))
                .toString();
    }

    static FunctionConfig createDefaultFunction() {
        return new FunctionConfig("tempLambdaFunction",
                "lambda.Entry:requestHandler",
                "Default description",
                "e",
                Runtime.JAVA21,
                Architecture.X86_64,
                512,
                30,
                "/aws/CloudEventDeploy/",
                14,
                FunctionUrlAuthType.NONE,
                "tempLambdaRole");
    }

    static String toJSONString(Record record) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(CloudConfigs.class);
    }

    record FunctionConfig(String functionName,
                          String handler,
                          String description,
                          String codeZipFilePath,
                          Runtime runtime,
                          Architecture architecture,
                          int memorySize,
                          int timeout,
                          String logGroupName,
                          int retentionDays,
                          FunctionUrlAuthType urlAuthType,
                          String iamRoleName) {

    }

    record Policy(String version, Map<String, Object> statement) {
    }
}

