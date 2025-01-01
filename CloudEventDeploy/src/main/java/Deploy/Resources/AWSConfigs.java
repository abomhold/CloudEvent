package Deploy.Resources;

import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.Runtime;
interface AWSConfigs {
    record FunctionConfig(
            String functionName,
            String roleArn,
            String handler,
            String description,
            String codeZipFilePath,
            software.amazon.awssdk.services.lambda.model.Runtime runtime,
            Architecture architecture,
            int memorySize,
            int timeout,
            String logGroupName,
            int retentionDays
    ) {
        public static FunctionConfig withDefaults(
        ) {
            return new FunctionConfig(
                    "a",
                    "b",
                    "c",
                    "Default description",
                    "e",
                    Runtime.JAVA21,
                    Architecture.X86_64,
                    512,
                    30,
                    "/aws/lambda/",
                    14
            );
        }
    }

    record LogGroupConfig(
            String logGroupName,
            String functionName,
            long retentionDays
    ) {
        public static LogGroupConfig withDefaults() {
            return new LogGroupConfig(
                    "a",
                    "b",
                    14
            );
        }
    }
}
