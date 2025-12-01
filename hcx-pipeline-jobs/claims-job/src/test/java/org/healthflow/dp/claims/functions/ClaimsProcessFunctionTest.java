package org.healthflow.dp.claims.functions;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.util.OneInputStreamOperatorTestHarness;
import org.apache.flink.streaming.util.ProcessFunctionTestHarnesses;
import org.junit.Test;
import org.healthflow.dp.claims.task.ClaimsConfig;
import org.healthflow.fixture.EventFixture;

import java.util.HashMap;
import java.util.Map;

public class ClaimsProcessFunctionTest {

    Config config  = ConfigFactory.load("claims-test.conf");
    ClaimsConfig claimsConfig = new ClaimsConfig(config,"ClaimsTestJob");

    //instantiate created user defined function
    ClaimsProcessFunction processFunction = new ClaimsProcessFunction(claimsConfig);
    //ObjectMapper objMapper = new ObjectMapper();
    Gson gson = new Gson();


    @Test
    public void testClaimsJob() throws Exception {
        // wrap user defined function into the corresponding operator
        OneInputStreamOperatorTestHarness<Map<String, Object>, Map<String, Object>> harness = ProcessFunctionTestHarnesses.forProcessFunction(processFunction);

        //StreamRecord object with the sample data
        Map<String, Object> eventMap = (HashMap<String,Object>) gson.fromJson(EventFixture.SAMPLE_EVENT(),HashMap.class);
        StreamRecord<Map<String, Object>> testData  = new StreamRecord<>(eventMap);
        harness.processElement(testData);
        harness.close();

    }

}
