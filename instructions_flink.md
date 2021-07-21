instructions_flink.md

### Secure Connection:

VPN ON
ssh vvittis@clu04.softnet.tuc.gr -p 22
p@r@thyr!42

### Personal Folder

cd /home/vvittis/; ls -la

### Write to a Apache Kafka Topic (Using KafkaProducer)

java -jar /home/vvittis/SimpleProducer/target/SimpleProducer-1.0-SNAPSHOT.jar

### Start Yarn application

cd /usr/local/flink

./bin/yarn-session.sh -tm 5120 -s 3

Take the application id: application_1614183653371_0140 (Mark & Right Click)

### Apache Flink Run

cd /usr/local/flink;

./bin/flink run -d -p 3	-m yarn-cluster	-yid application_1614183653371_0140 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.0-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 3

./bin/flink run -d -p 3 -m yarn-cluster -yid application_1614183653371_0140 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.0-SNAPSHOT.jar 

### Kill Yarn Application

CTR+C or
yarn application -kill  application_1614183653371_0140 

### Make an Apache Kafka Consumer

cd /usr/hdp/current/kafka-broker/bin

./kafka-console-consumer.sh --bootstrap-server clu02.softnet.tuc.gr:6667,clu03.softnet.tuc.gr:6667,clu04.softnet.tuc.gr:6667,clu06.softnet.tuc.gr:6667 --topic vvittis_visualize_topic9 --from-beginning

### See Apache Kafka Topics 

cd /usr/hdp/current/kafka-broker/bin;

./kafka-topics.sh --list --zookeeper clu01.softnet.tuc.gr:2182

### Delete Kafka Topic

cd /usr/hdp/current/kafka-broker/bin

./kafka-topics.sh --delete --zookeeper clu01.softnet.tuc.gr:2182 --topic vvittis_visualize_topic12


Hadoop All applications: http://clu01.softnet.tuc.gr:8188

Hadoop Yarn Cluster manager: http://clu01.softnet.tuc.gr:8189/ui2/

Spark History Server: http://clu01.softnet.tuc.gr:18081/