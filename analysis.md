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

flink@clu04
flink@clu09
flink@clu13
flink@clu16

HT1  =>
HT2  =>
HT3  =>
HT4  => flink@clu04
HT5  =>
HT6  =>
HT7  =>
HT8  =>
HT9  =>
HT10 =>
HT11 =>
HT12 =>
HT13 => flink@clu04
HT14 =>
HT15 =>
HT16 =>
HT17 => flink@clu04
HT18 => flink@clu04
HT19 =>
HT20 => flink@clu04
HT21 =>
HT22 =>
