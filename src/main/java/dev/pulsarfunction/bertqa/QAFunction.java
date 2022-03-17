package dev.pulsarfunction.bertqa;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.ModelException;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.qa.QAInput;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.client.impl.schema.JSONSchema;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Function;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.functions.*;
import org.apache.pulsar.functions.api.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/**
 * BERT QA Function
 **/
public class QAFunction implements Function<byte[], Void> {

    private static final String STATIC_PULSAR_TEXT = "Apache Pulsar is a multi-tenant, high-performance solution for server-to-server messaging. Apache Pulsar was originally developed by Yahoo, it is under the stewardship of the Apache Software Foundation.Native support for multiple clusters in a Pulsar instance, with seamless geo-replication of messages across clusters.Very low publish and end-to-end latency.Seamless scalability to over a million topics.A simple client API with bindings for Java, Go, Python and C++.Multiple subscription modes (exclusive, shared, and failover) for topics.Guaranteed message delivery with persistent message storage provided by Apache BookKeeper.A serverless light-weight computing framework Pulsar Functions offers the capability for stream-native data processing.A serverless connector framework Pulsar IO, which is built on Pulsar Functions, makes it easier to move data in and out of Apache Pulsar.";

    /**
     * parse Chat JSON Message into Class
     *
     * todo  Make a schema and find how to attach to websockets
     * @param message String of message
     * @return Chat Message
     */
    private Chat parseMessage(String message) {
        Chat chatMessage = new Chat();
        if ( message == null) {
            return chatMessage;
        }

        try {
            if ( message.trim().length() > 0) {
                ObjectMapper mapper = new ObjectMapper();
                chatMessage = mapper.readValue(message, Chat.class);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (chatMessage == null) {
            chatMessage = new Chat();
        }
        return chatMessage;
    }

    // retrieve input stream from data
    private String convertInputStream(InputStream is) {
        if (is == null ) { return null; }
        StringBuffer outBuffer = new StringBuffer(65000);
        try {
            try (InputStreamReader streamReader =
                         new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(streamReader)) {

                String line = null;
                while ((line = reader.readLine()) != null) {
                    outBuffer.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return outBuffer.toString();
    }



    /**
     * PROCESS
     *
     * TODO:   add function to websocket javascript front page
     *
     */
    @Override
    public Void process(byte[] input, Context context) {
        if (input == null ) {
            return null;
        }

        if (context != null && context.getLogger() != null && context.getLogger().isDebugEnabled()) {
            context.getLogger().debug("LOG:" + input.toString());

            System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");

            context.getLogger().debug("Available processors (cores): " +
                    Runtime.getRuntime().availableProcessors());

            /* Total amount of free memory available to the JVM */
            context.getLogger().debug("Free memory (bytes): " +
                    Runtime.getRuntime().freeMemory());

            /* This will return Long.MAX_VALUE if there is no preset limit */
            long maxMemory = Runtime.getRuntime().maxMemory();

            /* Maximum amount of memory the JVM will attempt to use */
            context.getLogger().debug("Maximum memory (bytes): " +
                    (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory));

            /* Total memory currently available to the JVM */
            context.getLogger().debug("Total memory available to JVM (bytes): " +
                    Runtime.getRuntime().totalMemory());
        }

        // @TODO:  Fix.  maybe pass in
        String outputTopic = "persistent://public/default/bertqaresults";

        try {
            Chat chat = parseMessage(new String(input));

            if (chat != null) {
                if (context != null && context.getLogger() != null && context.getLogger().isDebugEnabled()) {
                    context.getLogger().debug("Receive message JSON:" + chat);
                }
                String paragraph = null;

                // @TODO:   Fix.  maybe pass in or reference an online database or REST?
                try {
                  //  paragraph = convertInputStream(QAFunction.class.getClassLoader().getResourceAsStream("apachepulsar.txt"));
                    paragraph = STATIC_PULSAR_TEXT;
                }
                catch(Throwable t) {
                    t.printStackTrace();
                    paragraph = "Apache Pulsar is awesome";
                }

                QAInput qainput = new QAInput(chat.getComment(), paragraph);

                if (context != null && context.getLogger() != null && context.getLogger().isDebugEnabled()) {
                     context.getLogger().debug("Question: {}", qainput.getQuestion());
                     context.getLogger().debug("Engine: {}", Engine.getDefaultEngineName());
                }
                else {
                    System.out.println("chat:" + chat.toString());
                    System.out.println("q:" + qainput.getQuestion());
                    System.out.println("e:" + Engine.getDefaultEngineName());
                    System.out.println("p:" + paragraph);
                }

                Criteria<QAInput, String> criteria =
                        Criteria.builder()
                       .optApplication(Application.NLP.QUESTION_ANSWER)
                        .setTypes(QAInput.class, String.class)
                        .optFilter("backbone", "bert")
                        .optEngine(Engine.getDefaultEngineName())
                        .optProgress(new ProgressBar())
                        .build();

                String prediction = "";
                try (ZooModel<QAInput, String> model = criteria.loadModel()) {
                    try (Predictor<QAInput, String> predictor = model.newPredictor()) {
                        prediction = predictor.predict(qainput);
                    }
                } catch(Throwable e) {
                    if (context != null  && context.getLogger() != null) {
                        context.getLogger().error("ERROR:" + e.getLocalizedMessage());
                    }
                    else {
                        e.printStackTrace();
                    }
                }

                // add prediction to the results
                chat.setPrediction(prediction);

                if ( context != null && context.getTenant() != null ) {
                    context.newOutputMessage(outputTopic, JSONSchema.of(Chat.class))
                            .key(UUID.randomUUID().toString())
                            .property("language", "Java")
                            .property("processor", "bertqa")
                            .value(chat)
                            .send();
                }
                else {
                    System.out.println("Pred:" + prediction);
                    System.out.println("Null context, assuming local test run. " + chat.toString());
                }
            }
        } catch (Throwable e) {
            if (context != null  && context.getLogger() != null) {
                context.getLogger().error("ERROR:" + e.getLocalizedMessage());
            }
            else {
                e.printStackTrace();
            }
        }
        return null;
    }
}