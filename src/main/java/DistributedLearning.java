import ConceptDriftDetector.ConceptDriftDetector;
import ConceptDriftDetector.ConceptDriftFactory;
import HoeffdingTree.HoeffdingTree;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple6;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer010;
import org.apache.flink.util.Collector;
import org.apache.flink.configuration.Configuration;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;


public class DistributedLearning {

    public static void main(String[] args) throws Exception {

        /* @info StreamExecutionEnvironment: This  function is for both local and cluster execution.
         *
         *  Flink Version in the Cluster is 1.10.0 with Scala 2.12.
         *
         *  The path which the project have to be submitted is /usr/local/flink.
         *
         *  The Web Front End of clink is on: http://clu01.softnet.tuc.gr:8081.
         */
        // Find what variables you have to change in order to run on SoftNet cluster
        final ParameterTool params = ParameterTool.fromArgs(args);
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4);
        // The above lines serves the purpose of passing parameters to flink job
        env.getConfig().setGlobalJobParameters(params);
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("format", "raw");
        // PASSING USER'S PARAMETERS
        /* How many Hoeffding Trees we have in our Ensemble*/
        int number_of_hoeffding_trees = Integer.parseInt(params.get("number_of_HT"));
        /* Combination function can take 4 values. 1 => Majority Voting, 2 => Weighted Voting(th-θ/top-k), 3 => DES-P, 4 => KNORA-U*/
        int combination_function = Integer.parseInt(params.get("combination_function"));
        /* Given combination_function = 2, weighted voting option can take 2 values. 1 => threshold-θ, 2 => top-k*/
        int weighted_voting_option = Integer.parseInt(params.get("weighted_voting_option"));
        /* Given combination_function = 2 and weighted voting either 1 or 2, we have to give the parameter a value.
         *  When weighted_voting_option = 1 then => weighted_voting_parameter ε (0,1)
         *  When weighted_voting_option = 2 then => weighted_voting_parameter ε (1,number_of_hoeffding_trees). Ιn case when weighted_voting_parameter = 1 we select the best performing member.*/
        double weighted_voting_parameter = Double.parseDouble(params.get("weighted_voting_parameter"));
        /* Age_of_maturity indicates after how many instances seen, we will start accepting testing instances. Aka how long our model is in a stable phase.*/
        int age_of_maturity = Integer.parseInt(params.get("age_of_maturity"));
        /* Drift Detection Method used */
        int drift_detection_method_id = Integer.parseInt(params.get("drift_detection_method_id"));

        //KAFKA INPUT SOURCE
        /* @info: Reading from Kafka Source the input stream. For more information:  https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/kafka.html */
        DataStream<String> stream = env
                .addSource(new FlinkKafkaConsumer010<>("SineTopic1", new SimpleStringSchema(), properties).setStartFromEarliest()).name("Kafka Input Source");

        /*health_dataset_topic SeaDriftTopic SineTopic
         * @info: The above FlatMapFunction refers to the PopulateInputStream function to the <Section> on my thesis.
         * It servers the purpose as its name dictates.
         * It takes as an input the input stream is of String type
         */
        // INPUT POPULATION
        DataStream<Tuple2<String, Integer>> structured_stream = stream.flatMap(new FlatMapFunction<String, Tuple2<String, Integer>>() {
            /**
             * @param input_stream: The input stream is structured as follows (field_1,...,field_n,true_label,purpose_id, instance_id)
             *                      The number of fields in a particular has to be the same across the input data and
             *                      it is irrelevant to the Population Function.
             *                      But, the number of times each incoming instance will be populated is user-defined and
             *                      therefore the program has to work with any value.
             */
            @Override
            public void flatMap(String input_stream, Collector<Tuple2<String, Integer>> out) {

                for (int i = 1; i <= number_of_hoeffding_trees; i++) {
                    out.collect(new Tuple2<>(input_stream.trim(), i));
                }
            }
        }).name("Populate Input Stream");

        // STATE
        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> partial_result =
                structured_stream.
                        keyBy(1).
                        flatMap(new StatefulMap(combination_function, age_of_maturity,drift_detection_method_id)).name("Machine Learning Model");


        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> testing_results = partial_result.filter((FilterFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>) input_stream -> input_stream.f3 == -5).name("Filtering (keeping only Tests)");


        // COMBINATION FUNCTION
        testing_results.keyBy(0).
                countWindow(number_of_hoeffding_trees).
                aggregate(new CombinationFunction(number_of_hoeffding_trees, combination_function, weighted_voting_option, weighted_voting_parameter)).name("Combination Function");


        // PERFORMANCE (ERROR-RATE) MONITORING SINK
        partial_result.addSink(new FlinkKafkaProducer010<>("localhost:9092", "visualize_topic010",
                (SerializationSchema<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>)
                        element -> (element.getField(5).toString() + "," + element.getField(4).toString() + "," + element.getField(0).toString()).getBytes())).name("Visualizing Performance Metrics");

        /* Print the execution Plan
         *  https://flink.apache.org/visualizer/
         */

        System.out.println(env.getExecutionPlan());
        env.execute("Simple Kafka Read");

    }


    /**
     * Function StatefulMap
     * Input Tuple2 <String input_stream, Integer Hoeffding Tree ID>.
     * 1. The string is exactly the same with the input stream at the beginning of the job
     * 2. The integer is the Hoeffding Tree identifier with which the keyBy is performed
     * <p>
     * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
     * <p>
     * Output Tuple6 <Integer, Integer, Integer, Integer, Double, Integer>.
     * 0. Integer: Key of the instance
     * 1. Integer: Prediction
     * 2. Integer: True label
     * 3. Integer: Purpose ID
     * 4. Double:  Hoeffding Tree Weight
     * 5. Integer: Hoeffding Tree ID
     */

    static class StatefulMap extends RichFlatMapFunction<Tuple2<String, Integer>, Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> {

        private transient ValueState<HoeffdingTree> hoeffdingTreeValueState;
        private transient ValueState<HoeffdingTree> background_hoeffdingTreeValueState;
        private transient ValueState<ConceptDriftDetector> ConceptDriftDetectorValueState;
        private transient ValueState<Boolean> empty_state;
        private transient ValueState<Boolean>  empty_background_state;
        private transient ValueState<Integer> age_of_maturity;
        public int combination_function;
        public int age_of_maturity_input;
        public int drift_detection_method_id;
        public StatefulMap(int combination_function, int age_of_maturity_input,int drift_detection_method_id) {
            this.combination_function      = combination_function;
            this.age_of_maturity_input     = age_of_maturity_input;
            this.drift_detection_method_id = drift_detection_method_id;
        }

        @Override
        public void flatMap(Tuple2<String, Integer> input_stream, Collector<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> collector) throws Exception {
            /* We have to do some work to the input stream.
             * We extract the set of features, purpose ID, the a-priori known true label and the instance ID of a given input instance.
             * */
            String[] split_input_stream = input_stream.getField(0).toString().split(",");
            String[] features = Arrays.copyOfRange(split_input_stream, 0, split_input_stream.length - 2);
            int purpose_id = Integer.parseInt(split_input_stream[split_input_stream.length - 2]);
            int true_label = Integer.parseInt(split_input_stream[split_input_stream.length - 3]);
            int instance_id = Integer.parseInt(split_input_stream[split_input_stream.length - 1]);
            int hoeffding_tree_id = input_stream.getField(1);
            int prediction = -1;
            double error_rate = 1;


            /* The state will be initialized only by the first instance of the stream */
            /* Changing the status of the state to false */
            if (empty_state.value()) {
                age_of_maturity.update(0);
                empty_state.update(false);
                /* If state is empty, we have to Create a new HoeffdingTree.HoeffdingTree. */
                HoeffdingTree hoeffdingTree = new HoeffdingTree();
                hoeffdingTree.CreateHoeffdingTree(2, 2, 200, 0.1, 0.05, this.combination_function, hoeffding_tree_id);
                hoeffdingTreeValueState.update(hoeffdingTree);
                /* Also we create the a new ConceptDriftDetector.ConceptDriftDetector */
//                ConceptDriftDetector conceptDriftDetector = ConceptDriftFactory.createConceptDriftDetector(drift_detection_method_id);
//                ConceptDriftDetectorValueState.update(conceptDriftDetector);

            }
            /* We have adopted the "age of maturity" notion which provides to the HT to have a slack in order to stabilize its performance.
             * This logic is very important for the concept drift
             *
             * */
            if (age_of_maturity.value() >= age_of_maturity_input) {
                if (purpose_id == 5) {
                    /* We have adopted the Test-Then-Train method which has been proven to be more effective */
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id);
                    ht.UpdateHoeffdingTree(ht.root, features);
                    error_rate = ht.getErrorRate();
                    hoeffdingTreeValueState.update(ht);

//                    ConceptDriftDetector conceptDriftDetector = ConceptDriftDetectorValueState.value();
//                    conceptDriftDetector.FindConceptDrift(ht.getErrorRate());
//                    ConceptDriftDetectorValueState.update(conceptDriftDetector);
//                    int current_drift_state = conceptDriftDetector.getCurrentDriftStatus();
//                    /* The point of this if state is that there is no point to create a Background tree if the */
//                    if(current_drift_state == 1 && empty_background_state.value()){
//                        empty_background_state.update(false);
//                        HoeffdingTree background_ht = new HoeffdingTree();
//                    }



                } else if (purpose_id == -10 || purpose_id == -5) {
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id);
                    /* Despite the fact that we only test the Hoeffding Tree and we do not update it calling UpdateHoeffdingTree function,
                     * we have to update the state because internally TestHoeffdingTree function sets the HT's weight.
                     * Otherwise, we would have had the same weight throughout the streaming passage.
                     * */
                    hoeffdingTreeValueState.update(ht);
                    /* In case of instances which are fet to the system for prediction, we do not know its true label.
                     * Therefore, our need for a homogeneous output from the state, leads with no other choice of assigning
                     * an identifier in the true_label position (aka 3rd Integer in the collector)
                     * */
//                    System.out.println("Testing HT with id " + hoeffding_tree_id + " which has weight " + ht.getWeight() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                    if (purpose_id == -5) {
                        collector.collect(new Tuple6<>(instance_id, prediction, true_label, purpose_id, ht.getErrorRate(), hoeffding_tree_id));
                    } else {
                        collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, ht.getErrorRate(), hoeffding_tree_id));
                    }
                }
                age_of_maturity.update(age_of_maturity.value() + 1);
            } else if (age_of_maturity.value() <= age_of_maturity_input) {
                /* In the period where "age of maturity" (aka our machine learning model is immature we have to do two tasks in order to guarantee
                 * the proper use of this notion
                 * First task: You have to consider only the training instances
                 * Second task: Keep the testing and prediction instance (in case they exist) and feed them in the model after the pass of age of maturity.
                 *
                 * Disclaimer: In order to be realistic, in a continuous streaming context, the input streaming is endless and unbounded,
                 * therefore there is not need to keep some instances for testing or prediction. But for demonstration purposes and only for that
                 * we have to consider those because the total number of instances is limited.
                 */
                age_of_maturity.update(age_of_maturity.value() + 1);
                if (purpose_id == 5) {
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    ht.UpdateHoeffdingTree(ht.root, features);
                    hoeffdingTreeValueState.update(ht);
                }
            }
            collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, 1.0 - error_rate, hoeffding_tree_id));
        }

        @Override
        public void open(Configuration conf) {
            /* Hoeffding Tree */
            ValueStateDescriptor<HoeffdingTree> descriptor_hoeffding_tree = new ValueStateDescriptor<HoeffdingTree>("hoeffdingTreeValueState", HoeffdingTree.class);
            hoeffdingTreeValueState = getRuntimeContext().getState(descriptor_hoeffding_tree);
            /* Background Hoeffding Tree */
            ValueStateDescriptor<HoeffdingTree> descriptor_background_hoeffding_tree = new ValueStateDescriptor<HoeffdingTree>("background_hoeffdingTreeValueState", HoeffdingTree.class);
            background_hoeffdingTreeValueState = getRuntimeContext().getState(descriptor_background_hoeffding_tree);
            /* Concept Drift Detector */
            ValueStateDescriptor<ConceptDriftDetector> descriptor_concept_drift_detector= new ValueStateDescriptor<ConceptDriftDetector>("ConceptDriftDetectorValueState", ConceptDriftDetector.class);
            ConceptDriftDetectorValueState = getRuntimeContext().getState(descriptor_concept_drift_detector);
            /* Empty State */
            ValueStateDescriptor<Boolean> descriptor_empty_state = new ValueStateDescriptor<Boolean>("empty_state", TypeInformation.of(Boolean.TYPE), true);
            empty_state = getRuntimeContext().getState(descriptor_empty_state);
            /* Empty Background HT State */
            ValueStateDescriptor<Boolean> descriptor_empty_background_state = new ValueStateDescriptor<Boolean>("empty_background_state", TypeInformation.of(Boolean.TYPE), true);
            empty_background_state = getRuntimeContext().getState(descriptor_empty_background_state);
            /* Age of Maturity */
            ValueStateDescriptor<Integer> descriptor_age_of_maturity = new ValueStateDescriptor<Integer>("age_of_maturity", TypeInformation.of(Integer.TYPE), 0);
            age_of_maturity = getRuntimeContext().getState(descriptor_age_of_maturity);
            ValueStateDescriptor<Integer> descriptor_previous_instance_id = new ValueStateDescriptor<Integer>("previous_instance_id", TypeInformation.of(Integer.TYPE), 0);
        }
    }


    /**
     * Accumulator for WeightedAvg.
     * MyWeightedAvgAccum has three fields.
     * 1. instances_seen is the value that ensures that the getResult will return the final prediction for a given instance only if
     * the accumulated instances are equal to the number of hoeffding trees
     * 2. class0_weight variable is for the first class which represents the non-negative class
     * 3. class1_weight variable is for the second class which represents the negative class
     */
    public static class MyCombinationFunctionAccum {
        public int instance_id = 0;
        public int true_class = 0;
        public int instances_seen = 0;
        public double class0_weight = 0;
        public double class1_weight = 0;
        double[][] prediction_weight;
        public int selected_predictions = 0;
        public double[] best_prediction = {0.0, 0.0}; //the first index contains the prediction and the second index its weight
    }

    /**
     * Weighted Average user-defined aggregate function.
     */
    public static class CombinationFunction implements AggregateFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>, MyCombinationFunctionAccum, Double> {

        public int weighted_voting_option;
        // This is a universal parameter.
        // In case of selected method = 1 which means that we have Weighted Voting using threshold θ then
        // this parameter represents the value of the used threshold θ.
        // In case of selected method = 2 which means that we have Weighted Voting using top-k then
        // this parameter represents the value of the used k for the top-k
        public double parameter;
        public int ht_population;
        public int combination_function;

        public CombinationFunction(int ht_population, int combination_function, int weighted_voting_option, double parameter) {
            this.combination_function = combination_function;
            this.ht_population = ht_population;
            this.weighted_voting_option = weighted_voting_option;
            this.parameter = parameter;
        }

        @Override
        public MyCombinationFunctionAccum createAccumulator() {
            return new MyCombinationFunctionAccum();
        }

        /* The merge method is called when two windows are merged.
         * This applies to session windows, which are merged whenever two sessions are collapsed into one
         * by the arrival of an event that bridges the gap between the sessions.
         * When this occurs, the aggregated results-to-date of both sessions are combined by calling merge.
         * */
        public MyCombinationFunctionAccum merge(MyCombinationFunctionAccum a, MyCombinationFunctionAccum b) {
            a.class1_weight += b.class1_weight;
            return a;
        }

        public MyCombinationFunctionAccum add(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> value, MyCombinationFunctionAccum acc) {
            // value.f0 = instance id
            // value.f1 = prediction
            // value.f2 = true class
            // value.f3 = purpose id
            // value.f4 = weight
            // value.f5 = hoeffding tree id
            acc.instance_id = value.f0;
            acc.true_class = value.f2;
            acc.instances_seen++;
            if (acc.instances_seen == 1) {
                acc.prediction_weight = new double[this.ht_population][4];
            }
            // Majority Voting
            if (this.combination_function == 1) {
                if (value.f1 != -1) {
                    if (value.f1 == 1) {
                        acc.class1_weight += 1;
                    } else {
                        acc.class0_weight += 1;
                    }
                } else {
                    acc.class0_weight = -1;
                    acc.class1_weight = -1;
                }
            }
            // Weighted Voting with (threshold-θ, top-k)
            else if (this.combination_function == 2) {
                // Weighted Voting using threshold θ
                if (this.weighted_voting_option == 1) {
                    if (value.f4 > acc.best_prediction[0]) {
                        acc.best_prediction[0] = value.f1;
                        acc.best_prediction[1] = value.f4;
                    }
                    /* Here we want to cut all the classifiers whose weight is below a given threshold θ.*/
                    if (value.f4 > this.parameter) {
                        if (value.f1 == 1) {
                            acc.class1_weight += value.f4;
                        } else {
                            acc.class0_weight += value.f4;
                        }
                    } else {
                        /* There is a high chance of not selecting anyone. Thus, in that case we give the prediction of the best one. (highest weight)*/
//                        System.out.println("Hoeffding Tree " + value.f5 + " for instance " + acc.instance_id + " has weight of " + value.f4 + " < " + this.parameter);
                    }
                }
                // Weighted Voting using top-k
                else if (this.weighted_voting_option == 2) {
//                System.out.println("Just came Hoeffding Tree "+value.f5 + " predicting for instance "+value.f0+" that it is "+value.f1+" when it is "+value.f2);
                    // Just collect all the prediction with their respective weights
                    // UNORDERED LIST
                    // + ------------ + ------- + ----------- + ----- +
                    // | prediction 1 | weight1 | instance_id | HT_id |
                    // | prediction 2 | weight2 | instance_id | HT_id |
                    // | prediction 3 | weight3 | instance_id | HT_id |
                    // + ------------ + ------- + ----------- + ----- +
                    acc.prediction_weight[acc.instances_seen - 1][0] = value.f1;
                    acc.prediction_weight[acc.instances_seen - 1][1] = value.f4;
                    acc.prediction_weight[acc.instances_seen - 1][2] = value.f0;
                    acc.prediction_weight[acc.instances_seen - 1][3] = value.f5;
                }
            }
            // Weighted Voting using DESP-K
            else if (this.combination_function == 3) {
                if (value.f4 > 0) {
                    acc.selected_predictions++;
                    if (value.f1 == 1) {
                        acc.class1_weight += (value.f4 - 0.5);
                    } else {
                        acc.class0_weight += (value.f4 - 0.5);
                    }
                }
            }
            // Weighted Voting using KNORA_U
            else if (this.combination_function == 4) {
                if (value.f1 == 1) {
                    acc.class1_weight += (value.f4 * value.f0);
                } else {
                    acc.class0_weight += (value.f4 * value.f0);
                }
            }
            return acc;

        }

        public Double getResult(MyCombinationFunctionAccum acc) {
//            System.out.println(acc.instance_id + " => " + acc.instances_seen);
            if (acc.instances_seen == this.ht_population) {
                acc.instances_seen = 0;
                //Majority Voting
                if (this.combination_function == 1) {
                    if (acc.class1_weight > acc.class0_weight) {
//                        System.out.println("ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else {
                        System.out.println("ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
//                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                }
                // Weighted Voting with (threshold-θ, top-k)
                else if (combination_function == 2) {
                    // Weighted Voting using threshold θ
                    if (this.weighted_voting_option == 1) {
                        if (acc.class1_weight > acc.class0_weight) {
//                            System.out.println("Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                            return 1.0;
                        } else if (acc.class0_weight > acc.class1_weight) {
//                            System.out.println("Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                            //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                            return 0.0;
                        } else {
                            System.out.println("(Both class counters are 0. None of the predictions surpassed the given threshold)\nWe return the best returned one. Weighted Voting Method: " + acc.instance_id + " P: " + acc.best_prediction[0] + " , T: " + acc.true_class);
                            return acc.best_prediction[0];
                        }
                    }
                    // Weighted Voting using top-k
                    else if (this.weighted_voting_option == 2) {
                        Arrays.sort(acc.prediction_weight, Comparator.comparingDouble(o -> o[1]));
//                    System.out.println(Arrays.deepToString(acc.prediction_weight));
                        int i;
                        for (i = 0; i < this.parameter; i++) {
                            if (acc.prediction_weight[i][0] == 1) {
                                acc.class1_weight += acc.prediction_weight[i][1];
                            } else if (acc.prediction_weight[i][0] == 0) {
                                acc.class0_weight += acc.prediction_weight[i][1];
                            }
                        }
                        if (acc.class1_weight > acc.class0_weight) {
                            System.out.println("We are " + i + " Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                            return 1.0;
                        } else if (acc.class0_weight > acc.class1_weight) {
                            System.out.println("We are " + 1231241 + " Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                            //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                            return 0.0;
                        }
                        return 0.0;
                    }
                }
                // Weighted Voting using DES-P
                else if (this.combination_function == 3) {
                    if (acc.selected_predictions > 0) {
                        if (acc.class1_weight > acc.class0_weight) {
//                            System.out.println("Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                            return 1.0;
                        } else if (acc.class0_weight > acc.class1_weight) {
//                            System.out.println("Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                            //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                            return 0.0;
                        }
                    } else {
                        return -1.0;
                    }
                }
                // Weighted Voting using KNORA-U
                else if (this.combination_function == 4) {
                    if (acc.class1_weight > acc.class0_weight) {
//                        System.out.println("Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else if (acc.class0_weight > acc.class1_weight) {
//                        System.out.println("Weighted Voting method:" + this.weighted_voting_option + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                }
            }
            return 0.0;
        }

    }

}