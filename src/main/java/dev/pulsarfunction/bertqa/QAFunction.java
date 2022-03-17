package dev.pulsarfunction.bertqa;

import ai.djl.Application;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.qa.QAInput;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.client.impl.schema.JSONSchema;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Function;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * BERT QA Function
 **/
public class QAFunction implements Function<byte[], Void> {

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
        if (input == null || context == null) {
            return null;
        }

        if (context.getLogger() != null && context.getLogger().isDebugEnabled()) {
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

        String outputTopic = "persistent://public/default/bertqaresults";
        try {
            Chat chat = parseMessage(new String(input));

            if (chat != null) {
                if (context.getLogger() != null && context.getLogger().isDebugEnabled()) {
                    context.getLogger().debug("Receive message JSON:" + chat);
                }
                String paragraph = null;

                // @TODO:   Fix.  maybe pass in or reference an online database or REST?
                try {
                    paragraph = convertInputStream(QAFunction.class.getClassLoader().getResourceAsStream("apachepulsar.txt"));
                }
                catch(Throwable t) {
                    t.printStackTrace();
                    paragraph = "Apache Pulsar is awesome";
                }

//                DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/mxnet/bertqa/vocab.json", "/mxnet/bertqa/vocab.json", new ProgressBar());

                QAInput qainput = new QAInput(chat.getComment(), paragraph);

                if (context.getLogger() != null && context.getLogger().isDebugEnabled()) {
                     context.getLogger().debug("Question: {}", qainput.getQuestion());
                }
                System.out.println(Engine.getDefaultEngineName());
                Criteria<QAInput, String> criteria =
                        Criteria.builder()
                       .optApplication(Application.NLP.QUESTION_ANSWER)
                        .setTypes(QAInput.class, String.class)
                        .optFilter("backbone", "bert")
                        .optEngine(Engine.getDefaultEngineName())
                        .optProgress(new ProgressBar())
                        .build();

                /**
                 * optApplication(Application.NLP.QUESTION_ANSWER)
                 *                         .setTypes(QAInput.class, String.class)
                 *                         .optEngine("MXNet").optFilter("backbone", "bert")
                 *                         .build();
                 */
                String prediction = "";
                try (ZooModel<QAInput, String> model = criteria.loadModel()) {
                    try (Predictor<QAInput, String> predictor = model.newPredictor()) {
                        prediction = predictor.predict(qainput);
                    }
                }
                chat.setPrediction(prediction);

                context.newOutputMessage(outputTopic, JSONSchema.of(Chat.class))
                .key(UUID.randomUUID().toString())
                .property("language", "Java")
                .value(chat)
                .send();
            }
        } catch (Throwable e) {
            if (context.getLogger() != null) {
                context.getLogger().error("ERROR:" + e.getLocalizedMessage());
            }
        }
        return null;
    }
}
