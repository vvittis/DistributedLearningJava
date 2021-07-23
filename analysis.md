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



Οπότε ή έχεις άπειρο χρόνο, άπειρα δεδομένα και απλά το σταματάς, 

ή έχεις σταθερά δεδομένα και μετράς χρόνο μέχρι να επεξεργαστεί τα δεδομένα,

 ή έχεις δεδομένα άπερια και σταθερό χρόνο και μετράς πόσο δεδομένα πέρασαν. Και η μετρικές που παίρνω είναι records Received/Duration

Αυτά στο τι κάνω εγώ. 

1.  0.Source__Kafka.KafkaConsumer.bytes-consumed-rate δεν κάνει receive κάνει μονο Send, 
2. κοίτα το records received από το δεύτερο κουτάκι. 



./bin/flink run -d -p 1 -m yarn-cluster -yid application_1614183653371_0143 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1



0.Machine_Learning_Model.numRecordsInPerSecond

22/7



WriteStreamToARFFFile -s (ConceptDriftStream -s (generators.AgrawalGenerator -f 3 -b) -d (ConceptDriftStream -s (generators.AgrawalGenerator -f 5 -b) -d (ConceptDriftStream -s (generators.AgrawalGenerator -f 9 -b) -d (ConceptDriftStream -s (generators.AgrawalGenerator -b) -d (ConceptDriftStream -s (generators.AgrawalGenerator -f 3 -b) -d (generators.AgrawalGenerator -f 5 -b) -p 2750000 -w 10000) -p 2000000 -w 10000) -p 1500000 -w 10000) -p 750000 -w 10000) -p 500000 -w 10000) -f C:\Users\kryst\Desktop\agrawal_dataset.arff 

-m 10000

EvaluatePrequential -l bayes.NaiveBayes -s (ConceptDriftStream -s generators.AgrawalGenerator -d (ConceptDriftStream -s (generators.AgrawalGenerator -f 2) -d (ConceptDriftStream -s generators.AgrawalGenerator -d (generators.AgrawalGenerator -f 4) -p 25000 -w 1) -p 25000 -w 1) -p 25000 -w 1) -i 100000 -f 1000



@relation 'generators.AgrawalGenerator -f 3 -b'

@attribute salary numeric
@attribute commission numeric
@attribute age numeric

@attribute elevel { level0, => 0
                    level1, => 1
                    level2, => 2
                    level3, => 3
                    level4} => 4


Agrawal Dataset
@relation 'generators.AgrawalGenerator -f 3 -b'
@attribute salary numeric
@attribute commission numeric
@attribute age numeric
@attribute elevel {0,1,2,3,4}
@attribute car {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20}
@attribute zipcode {1,2,3,4,5,6,7,8,9}
@attribute hvalue numeric
@attribute hyears numeric
@attribute loan numeric
@attribute class {1,0}
@data


./bin/flink run -d -p 1 -m yarn-cluster -yid application_1614183653371_0143 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1

23/7 

Make sure that the project is in a good shape

Rerun the experiment with and without DDM.


'application_1614183653371_0147'.


http://clu09.softnet.tuc.gr:36124

### Original Sine datset
Run Without DDM

./bin/flink run -d -p 3 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 0

Rum With DDM

./bin/flink run -d -p 3 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1

Results are around the same.


### MOA Sine dataset

First of all, you will understand if there are drifts in the plot without drift detector.

See the resutled plot and maybe you will need to change the drift intervals from gradual to a more abrupt drift.

There are 4 attributes and we select 2 out of them. So there are 6 possible combinations. Therefore, our trees will be 6.

We are expecting 11.376.000 instances to pass.

Run Without DDM

./bin/flink run -d -p 3 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 0

With DDM

./bin/flink run -d -p 6 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 1 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 


Things did not go as expected.

See sine_datset_3M.png

I made a 3M sine with abrupt without luck


Let's make a smaller one about 100k and see what is going on

WriteStreamToARFFFile -s (ConceptDriftStream -s (generators.SineGenerator -b) -d (ConceptDriftStream -s (generators.SineGenerator -f 2 -b) -d (ConceptDriftStream -s (generators.SineGenerator -f 3 -b) -d (ConceptDriftStream -s (generators.SineGenerator -f 4 -b) -d (ConceptDriftStream -s (generators.SineGenerator -f 2 -b) -d (generators.SineGenerator -f 3 -b) -p 80000 -w 1) -p 60000 -w 100) -p 40000 -w 1 -r 2) -p 20000 -w 1 -r 3) -p 10000 -w 1) -f C:\Users\kryst\Desktop\sine_dataset_100k.arff -m 100000 -h


So, testing with 100k everything went as planned, the drift detector managed to decrease the error-rate.
The reason why DDM didn't show the expected behavior is because the expectation was wrong.

As I saw in the RDDM paper in DDM detector, a great number of errors are required in order to drigger either the warning signal or the drift signal.

So, when I was running the experiments with 3million instances, the fluctuation of the error was not significant enough in order to enter into a drift phase.


Hence, my experiments were fine but DDM could not cope with the datastream

Also, MOA creates correctly the datastreams BUT 10.000 width during the drift is way to many and I think I have to rewrite all the datasets.


So, I have to make a decision. Should I write RDDM or just test the scalability only with DDM.

The most computational cost effective is the parrallel training of both the main tree and the background tree. So, IF THE DDM enters in mine WARNING PHASE, it is ok to test the scalability as such.

It may sound paradox but as worsen the DDM is and therefore the more computation requires then better for scalability.

So, after all that I am gonna grab a coffee and I will run the scalability tests.

The questions are: 

Does it matter if the HTs have the same features to be trained?

Do I want to make a graph with all HTs error-rates with and without drift detector?


First of all, I have to make a 100k Agrawal dataset in order to see if my only Hoeffding Tree can handle such a dadaset. (because I transformed some categorical to numerical attributes.)


If YES, I will test with the 3M. If NO, then just be positive. As Kontaxakis said "I dont care about if it is right or wrong but if it scalable."

The 100k AGRAWAL will be only for educational purposes.


You have to find another solution than RDDM, in order to tackle the same problem AND combine it with the diversity solution that you thought. See the survey paper about diversity techniques. I believe that there are many solutions that you dont aknoweldge right now.

Testing Agrawal 100k...

Everyting good enough. The only thing that consernes me is the reason why DDM version of my project has an initial unusual high error-rate.

[Plot] Figures/agrawal_dataset_100k_1HT_7_out_of_9.png

Going through my first scalability tests with Agrawal. 

Reasons? 1) It has 9 features and by selecting 7 out of them, there are 36 possible combinations.

The testing set-up will consists of 

32 Trees, 7 features out of 9, 200 instances nmnin, 0.0001 as confidence level.

=================================================================================================
**Today = Test 1 will be with parallelism of 1, five times and take the mean**

./bin/flink run -d -p 1 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1


HT 2 Selected Features: 0 1 2 3 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 3 Selected Features: 1 2 3 4 5 6 7 
Hi ConceptDriftDetector.DDM constructor
HT 5 Selected Features: 0 1 2 3 4 5 8 
Hi ConceptDriftDetector.DDM constructor
HT 6 Selected Features: 0 1 2 3 5 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 7 Selected Features: 0 1 2 4 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 8 Selected Features: 0 2 3 4 5 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 12 Selected Features: 1 2 3 4 5 6 7 
Hi ConceptDriftDetector.DDM constructor
HT 13 Selected Features: 1 2 3 4 5 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 14 Selected Features: 0 1 2 3 4 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 16 Selected Features: 1 3 4 5 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 17 Selected Features: 0 1 2 3 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 20 Selected Features: 0 1 2 4 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 21 Selected Features: 0 1 2 4 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 23 Selected Features: 0 1 2 4 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 24 Selected Features: 0 1 2 4 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 26 Selected Features: 1 3 4 5 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 27 Selected Features: 0 1 3 4 5 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 28 Selected Features: 0 2 4 5 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 29 Selected Features: 0 1 2 3 4 5 7 
Hi ConceptDriftDetector.DDM constructor
HT 31 Selected Features: 0 1 3 4 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 32 Selected Features: 0 1 4 5 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 4 Selected Features: 0 1 2 4 5 6 7 
Hi ConceptDriftDetector.DDM constructor
HT 9 Selected Features: 0 1 3 4 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 10 Selected Features: 2 3 4 5 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 11 Selected Features: 0 1 2 4 5 6 7 
Hi ConceptDriftDetector.DDM constructor
HT 15 Selected Features: 0 1 2 4 5 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 19 Selected Features: 2 3 4 5 6 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 25 Selected Features: 0 1 2 3 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 30 Selected Features: 0 1 2 3 4 5 6 
Hi ConceptDriftDetector.DDM constructor
HT 1 Selected Features: 0 1 2 3 5 7 8 
Hi ConceptDriftDetector.DDM constructor
HT 22 Selected Features: 0 1 2 4 5 6 8 
Hi ConceptDriftDetector.DDM constructor
HT 18 Selected Features: 0 1 2 3 4 5 6 
Hi ConceptDriftDetector.DDM constructor







**Today = Test 2 will be with parallelism of 2, five times and take the mean**

./bin/flink run -d -p 2 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1

**Today = Test 3 will be with parallelism of 4, five times and take the mean**

./bin/flink run -d -p 4 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1

**Today = Test 4 will be with parallelism of 6, five times and take the mean**

./bin/flink run -d -p 6 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1

**Today = Test 5 will be with parallelism of 8, five times and take the mean**


./bin/flink run -d -p 8 -m yarn-cluster -yid application_1614183653371_0147 /home/vvittis/DistributedLearningJava/target/DistributedLearningJava-1.1-SNAPSHOT.jar --number_of_HT 32 --age_of_maturity 1000 --combination_function 3 --weighted_voting_parameter 1 --drift_detection_method_id 1

=================================================================================================
Test 6 will be with parallelism of 12, five times and take the mean
Test 7 will be with parallelism of 16, five times and take the mean
Test 7 will be with parallelism of 20, five times and take the mean
Test 7 will be with parallelism of 24, five times and take the mean
Test 7 will be with parallelism of 28, five times and take the mean
Test 7 will be with parallelism of 32, five times and take the mean

All results will be stored in [Experimental Evaluation](https://docs.google.com/spreadsheets/d/1x4PSFXFCQBgqCdMY_xfALaL9l58V37r0noectH89Hg4/edit?usp=sharing)

Also, I have to see the ML metrics RecordsInPerSecond 

Machine_Learning_Model.numRecordsInPerSecond


I am expecting 3.000.000 x 32 x 0.632 = 60.672.000 instances to pass to the ML task from the 96.000.000