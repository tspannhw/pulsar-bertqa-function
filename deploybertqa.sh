bin/pulsar-admin functions stop --name BertQA --namespace default --tenant public
bin/pulsar-admin functions delete --name BertQA --namespace default --tenant public
bin/pulsar-admin functions run --auto-ack true --jar /opt/demo/pulsar-bertqa-function/target/bertqa-1.0.0.jar --classname "dev.pulsarfunction.bertqa.QAFunction" --dead-letter-topic "persistent://public/default/bertqadead" --inputs "persistent://public/default/chat2" --log-topic "persistent://public/default/bertqalog" --name BertQA --namespace default --tenant public --max-message-retries 5
