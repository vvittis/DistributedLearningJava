19/7/2021

Latest Flink Job: http://clu25.softnet.tuc.gr:51894/#/overview

already a kafka topic vvittis_SineTopic

./bin/flink run -d -p 12 -m yarn-cluster -yid application_1614183653371_0140 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.0-SNAPSHOT.jar --number_of_HT 22  --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 3 --drift_detection_method_id 3



### Expectations 

Given the topic and the dataset 100.000 where the training instances are 99.844 of equal number of instances per class.
The testing instances are in total 116 (class0:65 and class1:51)

In the absence of the Online Bagging, the number of instances fed to the state would be 2,196.568.

The _"problem"_ with Poisson Distribution is that it is impossible to predict the exact number of instances that will cutted off. The probability of a Possion Distribution to take the value 0 is 0.368. 

**Is the 0.368x 2,196.568 the number of incoming instances in our case?**
Approximated expectation : 808.337024
Real answer              : 1,391,228 

Those instances will hopefully divided into 12 workers (never!). By printing the HT and the total used features we will see the split of HTs to the available workers.

Also, we are expecting 2.552 predictions. 116 tests with 22 predictions each.

### Data Division on Cluster Nodes



Cluster Node Name 	| Bytes Received| Records Received | Records Sent |

flink@clu15       	| 	   	16.5 MB |    	   443,750 |          812 |
flink@clu02		  	| 	   	14.1 MB | 		   379,888 | 		  696 |
flink@clu19 	  	| 		11.7 MB |	       316,300 |		  580 | 
flink@clu12		  	| 		9.39 MB |	       253,142 | 	      464 |

HT1  =>
HT2  =>
HT3  => flink@clu19
HT4  =>  flink@clu19
HT5  =>
HT6  =>
HT7  => flink@clu19
HT8  => flink@clu02
HT9  => flink@clu15
HT10 => flink@clu02
HT11 => flink@clu02
HT12 => flink@clu15
HT13 => flink@clu19
HT14 => flink@clu15
HT15 => flink@clu02
HT16 => flink@clu19
HT17 => flink@clu15
HT18 => flink@clu15
HT19 =>
HT20 => flink@clu15
HT21 => flink@clu02
HT22 => flink@clu15


20/7

application_1614183653371_0142

Latest Apache Flink Job: http://clu02.softnet.tuc.gr:32870

./bin/flink run -d -p 3 -m yarn-cluster -yid application_1614183653371_0142 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.0-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 3

already a kafka topic vvittis_SineTopic

1) Trying make a properties file with inputs 

--number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 3

Not done yet


2) Implement DDM inside State

I did it and uploaded the chart comparisons in Github.
They seem all good. Except the fact that my version (Hoeffding Tree) scores better both with and without a concept drift detector. 

There are two explenations for that. Either my base learner is just better, or the datasets are different.

The latter is a little of because both datasets in papaer has been produced by the same generator.

Let's assume that CHVT is better that C4.5


How i did the testing. I implemented a mechanism in which when the user assigns to the variable drift_detection_method_id the value of 0 then they dictate that they do not want to use any dirft detection algorithm.

Therefore, I changed the code of my pycharm script and I added two kafkaconnector listenning to two different kafka topics.

I ran DistributedLearningJava without a drift detection method (by assigning 0) writing to kafka topic and then I reran my project with (option 1  aka DDM) writing it to a different kafka topic. The script reads from both of them and merges the plots into one.

21/7


'application_1614183653371_0143'.

Latest Apache Flink Job http://clu02.softnet.tuc.gr:33418

cd /usr/local/flink;

./bin/flink run -d -p 3 -m yarn-cluster -yid application_1614183653371_0143 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 3

already a kafka topic vvittis_SineTopic