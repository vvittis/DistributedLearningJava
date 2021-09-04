import ConceptDriftDetector.ConceptDriftDetector;
import ConceptDriftDetector.ConceptDriftFactory;
import HoeffdingTree.HoeffdingTree;
import Utilities.DefaultValues;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.accumulators.IntCounter;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple6;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Collector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.OutputTag;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.Random;

public class DistributedLearning {

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();

        /* @info StreamExecutionEnvironment: This  function is for both local and cluster execution.
         *
         *  Flink Version in the Cluster is 1.10.0 with Scala 2.12.
         *
         *  The path which the project have to be submitted is /usr/local/flink.
         *
         *  The Web Front End of clink is on: http://clu01.softnet.tuc.gr:8081.
         */
        // String arguments = readFile("/home/vvittis/DistributedLearningJava/.properties");

//        final ParameterTool params = ParameterTool.fromArgs(args);
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//        env.getConfig().setGlobalJobParameters(params);
        env.getConfig().enableForceAvro();
        env.setParallelism(8);
        Properties properties = new Properties();
//        properties.put("bootstrap.servers", "clu02.softnet.tuc.gr:6667,clu03.softnet.tuc.gr:6667,clu04.softnet.tuc.gr:6667,clu06.softnet.tuc.gr:6667");
        properties.put("bootstrap.servers", "localhost:9092");

        // PASSING USER'S PARAMETERS
        DefaultValues df = new DefaultValues();
        int number_of_hoeffding_trees = df.getNumber_of_hoeffding_trees();
        int combination_function = df.getCombination_function();
        double weighted_voting_parameter = df.getWeighted_voting_parameter();
        int age_of_maturity = df.getAge_of_maturity();
        int drift_detection_method_id = df.getDrift_detection_method_id();
        int parallelism = df.getParallelism();



        /* Topics: health_dataset_topic SeaDriftTopic SineTopic */
        // Test 1 - data_source_SINE_sine1_w_50_n_0.1_103.csv
        // Test 2 - data_source_SINE_sine_dataset_3M_full_abrupt
        // Test 3 - data_source_SINE_sine_3M_2_drifts_500000_2000000
        // Test 4 - data_source_SINE_sine_3M_2_drifts_500000_2000000_a90
        // Test 5 - data_source_AGRAWAL_agrawal_dataset_3M_abrupt
        // Test 6 - data_source_AGRAWAL_agrawal_dataset_100k
        // Test 7 - data_source_RBF_rbf_1kk
        // Test 8 - data_source_SINE_sine_2_changes
        // Test 9 - data_source_SINE_sine1_w_50_n_0.1_103_2.4M
        // Test 10- data_source_SINE_sine_gradual_3M
        // Test 11- data_source_SINE_sine_gradual_3M_more.
        // Test 12- data_source_HYPERPLANE_hyperplane
        // Test 13- data_source_SEA_sea
        // Test 14- data_source_SEA_sea_new
        // Test 15- data_source_SEA_sea_next
        // Test 16- data_source_RBF_RBF_1M_abrupt
        // Test 17- ONE
        // Test 17- ONE1
        // Test 19 - data_source_SINE_sine1_w_50_n_0


        //KAFKA INPUT SOURCE
        DataStream<String> stream = env
                .addSource(new FlinkKafkaConsumer<>("sine_3M_2_drifts_500000_2000000", new SimpleStringSchema(), properties).setStartFromEarliest())
                .name("Kafka Input Source").setParallelism(parallelism).setMaxParallelism(parallelism);


        // INPUT POPULATION
        DataStream<Tuple3<String, Integer, Integer>> structured_stream =
                stream.flatMap(new FlatMapFunction<String, Tuple3<String, Integer, Integer>>() {
                    @Override
                    public void flatMap(String input_stream, Collector<Tuple3<String, Integer, Integer>> out) {

                        String[] split_input_stream = input_stream.split(",");
                        int purpose_id = Integer.parseInt(split_input_stream[split_input_stream.length - 2]);

                        if (purpose_id == 5) {
                            // Training Tuples
                            PoissonDistribution poisson = new PoissonDistribution(1);
                            for (int i = 1; i <= number_of_hoeffding_trees; i++) {
                                int valueOfPoisson = poisson.sample();
                                if (valueOfPoisson > 0) {
                                    out.collect(new Tuple3<>(input_stream, i, valueOfPoisson));
                                }
                            }
                        } else if (purpose_id == -5) {
                            // Testing Tuples
                            for (int i = 1; i <= number_of_hoeffding_trees; i++) {
                                out.collect(new Tuple3<>(input_stream, i, 0));
                            }
                        } else if (purpose_id == -1) {
                            //Last Line
                            for (int i = 1; i <= number_of_hoeffding_trees; i++) {
                                out.collect(new Tuple3<>(input_stream, i, 0));
                            }
                        }
                    }
                }).name("Online Bagging and Data Distribution").setParallelism(parallelism).setMaxParallelism(parallelism);


        // STATE
        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> partial_result =
                structured_stream.keyBy(1).
                        flatMap(new StatefulMap(combination_function, age_of_maturity, drift_detection_method_id, start))
                        .name("Machine Learning Model").setParallelism(parallelism).setMaxParallelism(parallelism);


        // FILTERING/ KEEPING ONLY TESTING
        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> testing_tuples =
                partial_result.
                        filter((FilterFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>) input_stream -> input_stream.f3 == -5)
                        .name("Filtering (keeping only Tests)").setMaxParallelism(parallelism).setParallelism(parallelism);


        // COMBINATION FUNCTION
        testing_tuples.keyBy(0).
                countWindow(number_of_hoeffding_trees).
                aggregate(new CombinationFunction(number_of_hoeffding_trees, combination_function, weighted_voting_parameter))
                .name("Combination Function").setParallelism(parallelism).setMaxParallelism(parallelism);


        // PERFORMANCE (ERROR-RATE) MONITORING SINK
        partial_result.addSink(new FlinkKafkaProducer<>("localhost:9092", "sine_3M_2_drifts_500000_2000000_without_prop",
                (SerializationSchema<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>)
                        element -> (element.getField(5).toString() + "," + element.getField(4).toString() + "," + element.getField(0).toString()).getBytes()))
                .name("Visualizing Performance Metrics").setParallelism(parallelism);


        // TERMINATING THE PROGRAM FOR TESTING PURPOSES
        partial_result.filter(new FilterFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>() {
            @Override
            public boolean filter(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> input) throws Exception {
                return input.f0 == -1 && input.f1 == -1 && input.f2 == -1 && input.f3 == -1;
            }
        }).keyBy(0).countWindow(number_of_hoeffding_trees).aggregate(new ExitStream(number_of_hoeffding_trees, start)).setParallelism(parallelism).setMaxParallelism(parallelism);

        System.out.println(env.getExecutionPlan());
        env.execute("Distributed Random Forest vvittis");

    }


    /**
     * Function StatefulMap <br><br>
     * <strong> Input Tuple2 (String input_stream, Integer Hoeffding Tree ID).  </strong>
     * <ol>
     * <li> The string is exactly the same with the input stream at the beginning of the job</li>
     * <li> The integer is the Hoeffding Tree identifier with which the keyBy is performed</li>
     * </ol>
     * <strong>Output Tuple6(Integer, Integer, Integer, Integer, Double, Integer)</strong>
     * <ol>
     *   <li>Integer: Key of the instance</li>
     *   <li>Integer: Prediction</li>
     *   <li>Integer: True label</li>
     *   <li>Integer: Purpose ID</li>
     *   <li>Double:  Hoeffding Tree Weight</li>
     *   <li>Integer: Hoeffding Tree ID</li>
     * </ol>
     */

    static class StatefulMap extends RichFlatMapFunction<Tuple3<String, Integer, Integer>, Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> {

        private transient ValueState<HoeffdingTree> hoeffdingTreeValueState;
        private transient ValueState<HoeffdingTree> background_hoeffdingTreeValueState;
        private transient ValueState<ConceptDriftDetector> ConceptDriftDetectorValueState;
        private transient ValueState<Boolean> empty_state;
        private transient ValueState<Boolean> empty_background_state;
        private transient ValueState<Integer> age_of_maturity;
        public int combination_function;
        public int age_of_maturity_input;
        public int drift_detection_method_id;
        public long start_time;

        public StatefulMap(int combination_function, int age_of_maturity_input, int drift_detection_method_id, long start_time) {
            this.combination_function = combination_function;
            this.age_of_maturity_input = age_of_maturity_input;
            this.drift_detection_method_id = drift_detection_method_id;
            this.start_time = start_time;
        }

        @Override
        public void flatMap(Tuple3<String, Integer, Integer> input_stream, Collector<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> collector) throws Exception {
            /* We have to do some work to the input stream.
             * We extract the set of features, purpose ID, the a-priori known true label and the instance ID of a given input instance.
             */
            String[] split_input_stream = input_stream.getField(0).toString().split(",");
            String[] features = Arrays.copyOfRange(split_input_stream, 0, split_input_stream.length - 2);
            int true_label = Integer.parseInt(split_input_stream[split_input_stream.length - 3]);
            int purpose_id = Integer.parseInt(split_input_stream[split_input_stream.length - 2]);
            int instance_id = Integer.parseInt(split_input_stream[split_input_stream.length - 1]);
            int hoeffding_tree_id = input_stream.getField(1);
            int instance_weight = input_stream.getField(2);
            int prediction;
            int prediction1;
//            System.out.println("START "+features[0]+","+features[1]+","+instance_weight+","+purpose_id+","+instance_id);
//            System.out.println(instance_id);

            if (!empty_state.value()) {
                /* We have adopted the "age of maturity" notion which provides to the HT to have a slack
                 * in order to stabilize its performance. This logic is very important for the concept drift.
                 */
                // System.out.println(instance_id);
                if (age_of_maturity.value() >= age_of_maturity_input) {

                    age_of_maturity.update(age_of_maturity.value() + 1);
                    if (purpose_id == 5) {
                        /* We have adopted the Test-Then-Train method which has been proven to be more effective */
                        HoeffdingTree ht = hoeffdingTreeValueState.value();

//                        prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id);
                        HoeffdingTree.Returninfo rn = ht.TestHoeffdingTree(ht.root, features, purpose_id);
                        prediction = rn.getPrediction();

                        /* Like Oza and Russell's Online Bagging Algorithm. Authors mention that:
                         * "As each training example is presented ot our algorithm, for each base model,
                         * choose the example K - Poisson(1) times and update the base model accordingly."
                         */

                        if (instance_id > 10000 && instance_id < 10010) {
                            System.out.println("HT " + hoeffding_tree_id + " Instance " + instance_id + " size " + ht.SizeHT(ht.root) + " accuracy " + ht.getAccuracy());
                        }
                        if (instance_id > 500000 && instance_id < 500010) {
                            System.out.println("HT " + hoeffding_tree_id + " Instance " + instance_id + " size " + ht.SizeHT(ht.root) + " accuracy " + ht.getAccuracy());
                        }

                        if (instance_id > 600000 && instance_id < 600010) {
                            System.out.println("HT " + hoeffding_tree_id + " Instance " + instance_id + " size " + ht.SizeHT(ht.root) + " accuracy " + ht.getAccuracy());
                        }
                        if (instance_id > 1900000 && instance_id < 1900010) {
                            System.out.println("HT " + hoeffding_tree_id + " Instance " + instance_id + " size " + ht.SizeHT(ht.root) + " accuracy " + ht.getAccuracy());
                        }
                        if (instance_id > 2100000 && instance_id < 2100010) {
                            System.out.println("HT " + hoeffding_tree_id + " Instance " + instance_id + " size " + ht.SizeHT(ht.root) + " accuracy " + ht.getAccuracy());
                        }
                        if (instance_id > 2990000 && instance_id < 2990010) {
                            System.out.println("HT " + hoeffding_tree_id + " Instance " + instance_id + " size " + ht.SizeHT(ht.root) + " accuracy " + ht.getAccuracy());
                        }

                        ht.UpdateHoeffdingTree(rn.getNode(), features, instance_weight);

//                        collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, ht.getErrorRate(), 1));

//                        System.out.println(instance_id+" "+ht.getAccuracy());
                        //error_rate = ht.getErrorRate();
                        hoeffdingTreeValueState.update(ht);



                        // Concept Drift Handler
                        if (drift_detection_method_id != 0) {

                            /* Checking the situation before the test*/
                            ConceptDriftDetector conceptDriftDetector = ConceptDriftDetectorValueState.value();
                            int current_stream_status = conceptDriftDetector.getCurrentDriftStatus();
                            /* Add the current error rate to the system */
                            conceptDriftDetector.FindConceptDrift(ht.getErrorRate());
                            int current_signal = conceptDriftDetector.getSignal();
                            conceptDriftDetector.updateCurrentDriftStatus();
                            /* Get the new drift status*/
                            int updated_stream_status = conceptDriftDetector.getCurrentDriftStatus();
                            ConceptDriftDetectorValueState.update(conceptDriftDetector);
                            if (updated_stream_status == 1) {
                                // WARNING PHASE
                                if (!empty_background_state.value()) {
                                    HoeffdingTree background_hoeffdingTree = background_hoeffdingTreeValueState.value();
                                    for (int i = 0; i < instance_weight; i++) {
                                        background_hoeffdingTree.UpdateHoeffdingTree(background_hoeffdingTree.root, features, instance_weight);
                                    }
                                    background_hoeffdingTreeValueState.update(background_hoeffdingTree);
                                } else if (empty_background_state.value()) {
                                    // System.out.println("===================================Warning Phase===================================");
//                                    System.out.println("Background Tree " + instance_id + " Just Created ");
                                    empty_background_state.update(false);
                                    // Warning Signal. Create & Train the Background Tree
                                    HoeffdingTree background_hoeffdingTree = new HoeffdingTree();
                                    background_hoeffdingTree.NEW_CreateHoeffdingTree(4, 4, 200, 0.0001, 0.05, this.combination_function, hoeffding_tree_id, 1);
                                    // background_hoeffdingTree.print_m_features();
                                    background_hoeffdingTreeValueState.update(background_hoeffdingTree);
                                }
                            } else if (current_stream_status == 1 && updated_stream_status == 0) {
                                // System.out.println("DS          Signal: instance id " + instance_id);
                                if (current_signal == 2) {
                                    // System.out.println("=============================Stable Phase/ Drift===================================");
//                                     System.out.println("Stable phase after a Drift Signal");
                                    System.out.println("Do the Switch: " + instance_id + "Background Tree taking over");
                                    // Drift Signal. Do the Switch
                                    HoeffdingTree background_tree = background_hoeffdingTreeValueState.value();
                                    ht.RemoveHoeffdingTree();
                                    hoeffdingTreeValueState.update(background_tree);
                                    empty_background_state.update(true);
                                    // System.out.println("Making the switch and resetting the Drift Detector");
                                    //RESET EVERYTHING
                                    conceptDriftDetector.ResetConceptDrift();
                                } else if (current_signal == -1) {
                                    // System.out.println("=========================Stable Phase/ False Alarm=================================");
//                                     System.out.println("Stable phase after a false alarm");
                                    // System.out.println("FAS         False Alarm Signal: instance id " + instance_id);
                                    HoeffdingTree background_tree = background_hoeffdingTreeValueState.value();
                                    background_tree.RemoveHoeffdingTree();
                                    background_hoeffdingTreeValueState.clear();
                                    empty_background_state.update(true);
                                }
                                //System.out.println("Training HT with id " + hoeffding_tree_id + " which has error-rate " + ht.getErrorRate() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                            }
                        }

                        collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, ht.getErrorRate(), 1));
                    } else if (purpose_id == -5) {
                        HoeffdingTree ht = hoeffdingTreeValueState.value();
                        prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id).getPrediction();
                        /* Despite the fact that we only test the Hoeffding Tree and we do not update it calling UpdateHoeffdingTree function,
                         * we have to update the state because internally TestHoeffdingTree function sets the HT's weight.
                         * Otherwise, we would have had the same weight throughout the streaming passage.
                         * */
                        hoeffdingTreeValueState.update(ht);

                        /* In case of instances which are fet to the system for prediction, we do not know its true label.
                         * Therefore, our need for a homogeneous output from the state, leads with no other choice of assigning
                         * an identifier in the true_label position (aka 3rd Integer in the collector)
                         * */
                        //System.out.println("Testing HT with id " + hoeffding_tree_id + " which has error-rate " + ht.getErrorRate() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                        collector.collect(new Tuple6<>(instance_id, prediction, true_label, purpose_id, ht.getErrorRate(), 1));
                    } else if (instance_id == -1 && true_label == -1 && purpose_id == -1) {
                        HoeffdingTree ht = hoeffdingTreeValueState.value();
                        int size = ht.SizeHT(ht.root);

                        System.out.print(size + "\t" + ht.getAccuracy() + "\t");
//                        System.out.println("Accuracy       : " + ht.getAccuracy());

                        System.out.println("End of Stream message " + hoeffding_tree_id);
                        collector.collect(new Tuple6<>(-1, -1, -1, -1, -1.0, 1));

                    }
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
                        /* Online Bagging*/
//                        if(true_label == 0) {
//                            System.out.println("Training 0 " + features[0] + "," + features[1] + "," + true_label + "," + instance_weight + "," + instance_id);
//                        }
//                        if(true_label == 1) {
//                            System.out.println("                                                    Training 1 " + features[0] + "," + features[1] + "," + true_label + "," + instance_weight + "," + instance_id);
//                        }
                        ht.UpdateHoeffdingTree(ht.root, features, instance_weight);

                        hoeffdingTreeValueState.update(ht);
                    }
                }
            } else if (empty_state.value()) {
                /* The state will be initialized only by the first instance of the stream
                 * Changing the status of the state to false
                 * */
//                System.out.println("Num. of features: " + (features.length - 1));
//                System.out.println("First " + features[0] + "," + features[1] + "," + instance_weight + "," + instance_id);
                age_of_maturity.update(0);
                empty_state.update(false);
                /* If state is empty, we have to Create a new HoeffdingTree.HoeffdingTree. */
                HoeffdingTree hoeffdingTree = new HoeffdingTree();
//                hoeffdingTree.CreateHoeffdingTree(2, 2, 200, 0.0001, 0.05, this.combination_function, hoeffding_tree_id, 0);
                hoeffdingTree.NEW_CreateHoeffdingTree(4, 4, 200, 0.000001, 0.05, this.combination_function, hoeffding_tree_id, age_of_maturity_input);

                hoeffdingTreeValueState.update(hoeffdingTree);
                hoeffdingTree.print_m_features();

                /* Also we create the a new ConceptDriftDetector.ConceptDriftDetector */
                if (drift_detection_method_id != 0) {
//                    System.out.println("Intro to Concept Drift");
                    ConceptDriftDetector conceptDriftDetector = ConceptDriftFactory.createConceptDriftDetector(drift_detection_method_id);
                    ConceptDriftDetectorValueState.update(conceptDriftDetector);
                }
            }

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
            ValueStateDescriptor<ConceptDriftDetector> descriptor_concept_drift_detector = new ValueStateDescriptor<ConceptDriftDetector>("ConceptDriftDetectorValueState", ConceptDriftDetector.class);
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
        }
    }


    /**
     * Accumulator for WeightedAvg.
     * <strong> MyWeightedAvgAccum has three fields.</strong>
     * <ol>
     * <li> <strong>instances_seen</strong> is the value that ensures that the getResult will return the final prediction for a given instance only if the accumulated instances are equal to the number of hoeffding trees</li>
     * <li> <strong>class0_weight</strong> variable is for the first class which represents the non-negative class</li>
     * <li> <strong>class1_weight</strong> variable is for the second class which represents the negative class</li>
     * </ol>
     */

    public static class MyCombinationFunctionAccum {
        public int instance_id = 0;
        public int true_class = 0;
        public int instances_seen = 0;
        public double class0_weight = 0;
        public double class1_weight = 0;
        double[][] prediction_weight;
        public double[] best_prediction = {0.0, 0.0}; //the first index contains the prediction and the second index its weight
    }

    /**
     * Weighted Average user-defined aggregate function.
     */

    public static class CombinationFunction implements AggregateFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>, MyCombinationFunctionAccum, Double> {

        // This is a universal parameter.
        // In case of selected method = 1 which means that we have Weighted Voting using threshold θ then
        // this parameter represents the value of the used threshold θ.
        // In case of selected method = 2 which means that we have Weighted Voting using top-k then
        // this parameter represents the value of the used k for the top-k
        public double parameter;
        public int ht_population;
        public int combination_function;

        public CombinationFunction(int ht_population, int combination_function, double parameter) {
            this.combination_function = combination_function;
            this.ht_population = ht_population;
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

        /**
         * @param value The input contains:
         *              <br>
         *              <ol>
         *              <li>value.f0 = instance id</li>
         *              <li> value.f1 = prediction </li>
         *              <li>value.f2 = true class</li>
         *              <li>value.f3 = purpose id</li>
         *              <li>value.f4 = weight</li>
         *              <li>value.f5 = hoeffding tree id</li>
         *              </ol>
         * @param acc   The stored accumulator
         * @return just a 0 or 1 but is irrelevant in that version
         */
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
                }
            }
            // Weighted Voting using top-k
            else if (this.combination_function == 3) {
                //                System.out.println("Just came Hoeffding Tree "+value.f5 + " predicting for instance "+value.f0+" that it is "+value.f1+" when it is "+value.f2);
                // Just collect all the prediction with their respective weights
                // UNORDERED LIST
                // + ------------ + ------- + ----------- + ----- +
                // | prediction 1 | weight1 | instance_id | HT_id |
                // | prediction 2 | weight2 | instance_id | HT_id |
                // | prediction 3 | weight3 | instance_id | HT_id |
                // + ------------ + ------- + ----------- + ----- +
                acc.prediction_weight[acc.instances_seen - 1][0] = value.f1;
                acc.prediction_weight[acc.instances_seen - 1][1] = (1 - value.f4);
                acc.prediction_weight[acc.instances_seen - 1][2] = value.f0;
                acc.prediction_weight[acc.instances_seen - 1][3] = value.f5;
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
//                        System.out.println("ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        // System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                }
                // Weighted Voting with (threshold-θ, top-k)
                else if (combination_function == 2) {
                    // Weighted Voting using threshold θ
                    if (acc.class1_weight > acc.class0_weight) {
                        // System.out.println("Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else if (acc.class0_weight > acc.class1_weight) {
                        // System.out.println("Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        // System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    } else {
                        // System.out.println("(Both class counters are 0. None of the predictions surpassed the given threshold)\nWe return the best returned one. Weighted Voting Method: " + acc.instance_id + " P: " + acc.best_prediction[0] + " , T: " + acc.true_class);
                        return acc.best_prediction[0];
                    }
                }
                // Weighted Voting using top-k
                else if (this.combination_function == 3) {
                    Arrays.sort(acc.prediction_weight, Comparator.comparingDouble(o -> o[1]));
                    // System.out.println(Arrays.deepToString(acc.prediction_weight));
                    int i;
                    for (i = 0; i < this.parameter; i++) {
                        if (acc.prediction_weight[i][0] == 1) {
                            acc.class1_weight += acc.prediction_weight[i][1];
                        } else if (acc.prediction_weight[i][0] == 0) {
                            acc.class0_weight += acc.prediction_weight[i][1];
                        }
                    }
//                     System.out.println("We are " + i + " Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                    if (acc.class1_weight > acc.class0_weight) {
//                         System.out.println("We are " + i + "  Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else if (acc.class0_weight > acc.class1_weight) {
//                         System.out.println("We are " + i + "  Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
//                         System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    } else {
                        // System.out.println("(Both class counters are 0. None of the predictions surpassed the given threshold)");
                        return 0.0;
                    }
                }
            }
            return 0.0;
        }
    }


    public static class MyExitStreamAcc {

        int counter = 0;
    }

    /**
     * Weighted Average user-defined aggregate function.
     */

    public static class ExitStream implements AggregateFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>, MyExitStreamAcc, Double> {

        public int ht_population;
        public long start_time;

        public ExitStream(int ht_population, long start_time) {
            this.ht_population = ht_population;
            this.start_time = start_time;

        }

        @Override
        public MyExitStreamAcc createAccumulator() {
            return new MyExitStreamAcc();
        }

        /* The merge method is called when two windows are merged.
         * This applies to session windows, which are merged whenever two sessions are collapsed into one
         * by the arrival of an event that bridges the gap between the sessions.
         * When this occurs, the aggregated results-to-date of both sessions are combined by calling merge.
         * */
        public MyExitStreamAcc merge(MyExitStreamAcc a, MyExitStreamAcc b) {
            return a;
        }

        public MyExitStreamAcc add(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> value, MyExitStreamAcc acc) {
            System.out.println("Hoeffding Tree " + value.f5 + " has ended its stream");
            acc.counter++;
            return acc;
        }

        public Double getResult(MyExitStreamAcc acc) {
            if (acc.counter == ht_population) {
                System.out.println("End of Stream from all Hoeffding Trees");
                try {
                    long end = System.nanoTime();
                    long elapsedNanos = end - start_time;
                    System.out.printf("%.3f %n", elapsedNanos / 1000000000d);
                    throw new Exception("End of Stream message");
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            return 0.0;
        }
    }


}
