package saaf;

import com.amazonaws.services.lambda.runtime.Context;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.Arrays;

public class CloudEventInspector extends Inspector {
    long counter;
    Context context;

    public CloudEventInspector(Context context) {
        super();
        this.context = context;
        counter = 0;
        addTimeStamp("initial");
    }

    // Haven't tested data encoding
    public void addTimeStamp(String key, String data) {
        super.addTimeStamp(key);
        CloudEventData eventData = () -> data.getBytes(Charset.defaultCharset());
        createCloudEvent(key, eventData);
    }

    @Override
    public void addTimeStamp(String key) {
        super.addTimeStamp(key);
        createCloudEvent(key, null);
    }

    // Uses a different clock call than Inspector does
    //  response logs might differ from cloud logs slightly
    private void createCloudEvent(String key, CloudEventData data) {
        String cloudEvent = CloudEventBuilder
                .v1()
                .withId(context.getAwsRequestId() + ":" + counter++)
                .withSource(getURI())
                .withType(key)
                .withTime(OffsetDateTime.now())
                .withData(data)
                .build()
                .toString();
        context.getLogger().log(cloudEvent + System.lineSeparator());
    }

    // For the stack trace to work effectively the method searched for
    //  must be the method that contains the timestamps calls
    private URI getURI() {
        String handleRequestTrace = Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(elem -> elem.getMethodName().equals("handleRequest"))
                .findFirst()
                .map(StackTraceElement::toString)
                .orElse(context.getLogGroupName() + ":" + context.getLogStreamName());
        return URI.create("urn:" + context.getInvokedFunctionArn() + ":" + handleRequestTrace);
    }


}
