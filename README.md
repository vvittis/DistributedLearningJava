# Distributed Machine Learning
Homogeneous Ensemble learning, implementing Hoeffding Trees (Random Forest) under Concept Drift Conditions

### DistributedLearningJava.class


#### Passing user's parameters
Notes:
 ``` java
 /** How many Hoeffding Trees we have in our Ensemble **/

int number_of_hoeffding_trees = Integer.parseInt(params.get("number_of_HT"));
```
``` java
/** Combination function can take 3 values. 
 * 1 => Majority Voting 
 * 2 => Weighted Voting with threshold-θ => ε (0,1)
 * 3 => Weighted Voting with top-k       => ε (1,number_of_hoeffding_trees). 
 **/

int combination_function = Integer.parseInt(params.get("combination_function"));
 ```
 ``` java
/* Given combination_function = 2, weighted voting option can take 2 values. 1 => threshold-θ, 2 => top-k */
int weighted_voting_option = Integer.parseInt(params.get("weighted_voting_option"));
``` 
``` java
/** Age_of_maturity indicates after how many instances seen, we will start accepting testing instances. 
 * Aka how long our model is in a stable phase.
 **/
int age_of_maturity = Integer.parseInt(params.get("age_of_maturity"));
```
``` java
/* Drift Detection Method used */
int drift_detection_method_id = Integer.parseInt(params.get("drift_detection_method_id"));
```
#### Kafka Input Stream

**_@info:_** Reading from Kafka Source the input stream. For more information:  https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/kafka.html

#### Online Bagging/ Data Distribution
**_@param_** input_stream: <br>

The input stream is structured as follows (field_1,...,field_n,true_label,purpose_id, instance_id). The number of fields in a particular has to be the same across the input data and it is irrelevant to the Population Function. But, the number of times each incoming instance will be populated is user-defined and therefore the program has to work with any value.

#### Apache Flink Visualizer Plan

Print the execution Plan: https://flink.apache.org/visualizer/

