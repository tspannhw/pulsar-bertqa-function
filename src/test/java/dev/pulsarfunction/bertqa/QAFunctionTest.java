package dev.pulsarfunction.bertqa;

import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.functions.LocalRunner;
import org.apache.pulsar.functions.api.Context;
import org.junit.Test;

import java.util.Collections;
import static org.mockito.Mockito.mock;

import dev.pulsarfunction.bertqa.QAFunction;
import dev.pulsarfunction.bertqa.Chat;
import org.apache.pulsar.common.functions.ConsumerConfig;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.common.io.SourceConfig;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.functions.LocalRunner;
import org.junit.Assert;
import org.junit.Test;
import org.apache.pulsar.functions.api.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class QAFunctionTest {

        protected Context ctx;

        protected void init(Context ctx) {
            this.ctx = ctx;
        }

        public static String JSON_STRING = "{\"userInfo\":\"Tim Spann\",\"contactInfo\":\"Tim Spann, Developer Advocate @ StreamNative\",\"comment\":\"What is Apache Pulsar?\"}";
        /**
         *
         * @param msg
         */
        protected void log(String msg) {
            if (ctx != null && ctx.getLogger() != null) {
                ctx.getLogger().error(String.format("Function: [%s, id: %s, instanceId: %d of %d] %s",
                        ctx.getFunctionName(), ctx.getFunctionId(), ctx.getInstanceId(), ctx.getNumInstances(), msg));
            }
        }

        @Test
        public void testFunction() {
            QAFunction func = new QAFunction();
            func.process(JSON_STRING.getBytes(), mock(Context.class));
        }

        /**
         * @param args   string arguments
         * @throws Exception
         */
        public static void main(String[] args) throws Exception {
            FunctionConfig functionConfig = FunctionConfig.builder()
                    .className(QAFunction.class.getName())
                    .inputs(Collections.singleton("persistent://public/default/chat2"))
                    .name("Chat5")
                    .runtime(FunctionConfig.Runtime.JAVA)
                    .autoAck(true)
                    .build();

            LocalRunner localRunner = LocalRunner.builder()
                    .brokerServiceUrl("pulsar://pulsar1:6650")
                    .functionConfig(functionConfig)
                    .build();

            localRunner.start(true);

//            localRunner.start(false);
//            Thread.sleep(30000);
//            localRunner.stop();
//            System.exit(0);
        }
    }