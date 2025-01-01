package Deploy.Resources;

import software.amazon.awssdk.services.lambda.model.Architecture;
import software.amazon.awssdk.services.lambda.model.Runtime;

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
        // Factory method with some default values
        public static FunctionConfig withDefaults(
                String functionName,
                String roleArn,
                String handler,
                String codeZipFilePath
        ) {
            return new FunctionConfig(
                    functionName,
                    roleArn,
                    handler,
                    "Default description",
                    codeZipFilePath,
                    Runtime.JAVA21,
                    Architecture.X86_64,
                    512,
                    30,
                    "/aws/lambda/" + functionName,
                    14
            );
        }
    }
