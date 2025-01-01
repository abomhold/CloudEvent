package lambda;

import com.amazonaws.services.lambda.runtime.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import saaf.CloudEventInspector;
import saaf.Response;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * uwt.lambda_test::handleRequest
 *
 * @author Wes Lloyd
 * @author Robert Cordingly
 */
public class Entry implements RequestHandler<HashMap<String, Object>, HashMap<String, Object>> {

    static final List<String> requestBodyKeys = List.of("name");

    public HashMap<String, Object> handleRequest(HashMap<String, Object> request, Context context) {
        //Collect inital data
        CloudEventInspector inspector = new CloudEventInspector(context);
        inspector.inspectContainer();
        inspector.inspectCPU();
        inspector.inspectLinux();
        inspector.inspectMemory();
        inspector.addTimeStamp("inspection");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            inspector.addTimeStamp("error", e + "::" + Arrays.toString(e.getStackTrace()));
        }

        inspector.addTimeStamp("sleep");


        if (request.get("body") != null) {
            parseBody(request);
            inspector.addTimeStamp("parse");
        }


        Response response = new Response();
        response.setValue("Hello " + request.get("name")
                + "! This is from a response object!");
        inspector.consumeResponse(response);
        inspector.addTimeStamp("response", response.toString());

        inspector.inspectAllDeltas();
        inspector.addTimeStamp("inspection");
        return inspector.finish();
    }

    private static void parseBody(HashMap<String, Object> request) {
        ObjectMapper objectMapper = new ObjectMapper();
        Object body = request.get("body");
        String req = body.toString();
        Map<String, String> map = null;

        try {
            map = objectMapper.readValue(req, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        for (String key : Entry.requestBodyKeys) {
            request.put(key, map.get(key));
        }

        request.remove("body");
    }
}
