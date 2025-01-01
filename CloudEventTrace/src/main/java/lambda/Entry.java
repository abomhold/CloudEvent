package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import saaf.CloudEventInspector;
import saaf.Response;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Entry implements RequestHandler<HashMap<String, Object>, HashMap<String, Object>> {

    //    private static void parseBody(HashMap<String, Object> request) {
//        ObjectMapper objectMapper = new ObjectMapper();
//        Object body = request.get("body");
//        String req = body.toString();
//        Map<String, String> map;
//
//        try {
//            map = objectMapper.readValue(req, new TypeReference<>() {
//            });
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//
//        for (String key : map.keySet()) {
//            request.put(key, map.get(key));
//        }
//        request.remove("body");
//    }
    private static void parseBody(Map<String, Object> request) {
        try {
            new ObjectMapper()
                    .readValue(request.remove("body").toString(), new TypeReference<Map<String, String>>() {
                    })
                    .entrySet()
                    .stream()
                    .forEach(e -> request.put(e.getKey(), e.getValue()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

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
        response.setValue("Hello " + request.get("name") + "! This is from a response object!");
        inspector.consumeResponse(response);
        inspector.addTimeStamp("response", response.toString());

        inspector.inspectAllDeltas();
        inspector.addTimeStamp("inspection");
        return inspector.finish();
    }
}
