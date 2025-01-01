package saaf;

import com.amazonaws.services.lambda.runtime.Context;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;

public class CloudEventInspector extends Inspector {
    long counter;
    Context context;

    public CloudEventInspector(Context context) {
        super();
        this.context = context;
        counter = 0;
        addTimeStamp("initial");
    }

    public void addTimeStamp(String key, String data) {
        super.addTimeStamp(key);
        CloudEventData eventData = new CloudEventData() {
            @Override
            public byte[] toBytes() {
                return data.getBytes(Charset.defaultCharset());
            }
        };
        createCloudEvent(key, eventData);
    }

    @Override
    public void addTimeStamp(String key) {
        super.addTimeStamp(key);
        createCloudEvent(key, null);
    }

    //Uses a different clock call than Inspector does
    //response logs might differ from cloud logs slightly
    private void createCloudEvent(String key, CloudEventData data) {
        String cloudEvent = CloudEventBuilder
                .v1()
                .withId(context.getAwsRequestId() + ":" + counter++)
                .withSource(URI.create("lambda.Main::requestHandler"))
                //.withSource(getURI())
                .withType(key)
                .withTime(OffsetDateTime.now())
                .withData(data)
                .build()
                .toString();

        context.getLogger().log(cloudEvent + System.lineSeparator());
    }

    private URI getURI(){
        String functionName = context.getFunctionName();
        System.out.println(functionName);
        String region = System.getenv("AWS_REGION");
        System.out.println(region);
        String url = String.format("https://%s.lambda-url.%s.on.aws/", functionName, region);
        System.out.println(url);
        return URI.create(url);
    }

}

