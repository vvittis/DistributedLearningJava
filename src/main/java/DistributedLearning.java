import ConceptDriftDetector.ConceptDriftDetector;
import ConceptDriftDetector.ConceptDriftFactory;
import HoeffdingTree.HoeffdingTree;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;


public class DistributedLearning
{

    public static void main(String[] args) throws Exception
    {

        /* @info StreamExecutionEnvironment: This  function is for both local and cluster execution.
         *
         *  Flink Version in the Cluster is 1.10.0 with Scala 2.12.
         *
         *  The path which the project have to be submitted is /usr/local/flink.
         *
         *  The Web Front End of clink is on: http://clu01.softnet.tuc.gr:8081.
         */
        // String arguments = readFile("/home/vvittis/DistributedLearningJava/.properties");

        final ParameterTool params = ParameterTool.fromArgs(args);
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().setGlobalJobParameters(params);
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "clu02.softnet.tuc.gr:6667,clu03.softnet.tuc.gr:6667,clu04.softnet.tuc.gr:6667,clu06.softnet.tuc.gr:6667");
        properties.setProperty("format", "raw");

        // PASSING USER'S PARAMETERS
        int number_of_hoeffding_trees = Integer.parseInt(params.get("number_of_HT"));
        int combination_function = Integer.parseInt(params.get("combination_function"));
        double weighted_voting_parameter = Double.parseDouble(params.get("weighted_voting_parameter"));
        int age_of_maturity = Integer.parseInt(params.get("age_of_maturity"));
        int drift_detection_method_id = Integer.parseInt(params.get("drift_detection_method_id"));


        //KAFKA INPUT SOURCE
        /* Topics: health_dataset_topic SeaDriftTopic SineTopic */
        DataStream<String> stream = env
                                    .addSource(new FlinkKafkaConsumer<>("vvittis_Agrawal_3M", new SimpleStringSchema(), properties).setStartFromEarliest())
                                    .name("Kafka Input Source");


        // INPUT POPULATION
        DataStream<Tuple3<String, Integer, Integer>> structured_stream =
            stream.flatMap(new FlatMapFunction<String, Tuple3<String, Integer, Integer>>()
        {
            @Override
            public void flatMap(String input_stream, Collector<Tuple3<String, Integer, Integer>> out)
            {

                String[] split_input_stream = input_stream.toString().split(",");
                int purpose_id = Integer.parseInt(split_input_stream[split_input_stream.length - 2]);
                int instance_id = Integer.parseInt(split_input_stream[split_input_stream.length - 1]);

                if (purpose_id == -5)
                {
                    // Testing
                    for (int i = 1; i <= number_of_hoeffding_trees; i++)
                    {
                        out.collect(new Tuple3<>(input_stream.trim(), i, 0));
                    }
                }
                else if (purpose_id == 5)
                {
                    // Training
                    PoissonDistribution poisson = new PoissonDistribution(1);
                    for (int i = 1; i <= number_of_hoeffding_trees; i++)
                    {
                        int valueOfPoisson = poisson.sample();

                        if (valueOfPoisson > 0)
                        {
                            out.collect(new Tuple3<>(input_stream.trim(), i, valueOfPoisson));
                        }
                        else
                        {
                            //                                    System.out.println("Instance id " + instance_id + " did not go to HoeffdingTree: " + i);
                        }

                    }
                }
                else if (purpose_id == -1)
                {
                    //Last Line
                    for (int i = 1; i <= number_of_hoeffding_trees; i++)
                    {
                        out.collect(new Tuple3<>(input_stream.trim(), i, 0));
                    }
                }
            }
        }).name("Online Bagging and Data Distribution");

        // STATE
        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> partial_result =
            structured_stream.
            keyBy(1).
            flatMap(new StatefulMap(combination_function, age_of_maturity, drift_detection_method_id))
            .name("Machine Learning Model");

        // FILTERING/ KEEPING ONLY TESTING
        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> testing_results =
            partial_result.
            filter((FilterFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>) input_stream -> input_stream.f3 == -5)
            .name("Filtering (keeping only Tests)");


        // COMBINATION FUNCTION
        testing_results.keyBy(0).
        countWindow(number_of_hoeffding_trees).
        aggregate(new CombinationFunction(number_of_hoeffding_trees, combination_function, weighted_voting_parameter))
        .name("Combination Function");


        // PERFORMANCE (ERROR-RATE) MONITORING SINK
        // partial_result.addSink(new FlinkKafkaProducer<>("clu02.softnet.tuc.gr:6667", "vvittis_visualize_topic_agrawal2",
        //                        (SerializationSchema<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>)
        //                        element -> (element.getField(5).toString() + "," + element.getField(4).toString() + "," + element.getField(0).toString()).getBytes()))
        // .name("Visualizing Performance Metrics");

        System.out.println(env.getExecutionPlan());
        JobExecutionResult result = env.execute("Distributed Random Forest vvittis");


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

    static class StatefulMap extends RichFlatMapFunction<Tuple3<String, Integer, Integer>, Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>
    {

        private transient ValueState<HoeffdingTree> hoeffdingTreeValueState;
        private transient ValueState<HoeffdingTree> background_hoeffdingTreeValueState;
        private transient ValueState<ConceptDriftDetector> ConceptDriftDetectorValueState;
        private transient ValueState<Boolean> empty_state;
        private transient ValueState<Boolean> empty_background_state;
        private transient ValueState<Integer> age_of_maturity;
        public int combination_function;
        public int age_of_maturity_input;
        public int drift_detection_method_id;

        public StatefulMap(int combination_function, int age_of_maturity_input, int drift_detection_method_id)
        {
            this.combination_function = combination_function;
            this.age_of_maturity_input = age_of_maturity_input;
            this.drift_detection_method_id = drift_detection_method_id;
        }

        @Override
        public void flatMap(Tuple3<String, Integer, Integer> input_stream, Collector<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> collector) throws Exception
        {
            /* We have to do some work to the input stream.
             * We extract the set of features, purpose ID, the a-priori known true label and the instance ID of a given input instance.
             * */
            String[] split_input_stream = input_stream.getField(0).toString().split(",");
            String[] features = Arrays.copyOfRange(split_input_stream, 0, split_input_stream.length - 2);
            int true_label = Integer.parseInt(split_input_stream[split_input_stream.length - 3]);
            int purpose_id = Integer.parseInt(split_input_stream[split_input_stream.length - 2]);
            int instance_id = Integer.parseInt(split_input_stream[split_input_stream.length - 1]);
            int hoeffding_tree_id = input_stream.getField(1);
            int instance_weight = input_stream.getField(2);
            int prediction = -1;
            double error_rate = 1;


            /* The state will be initialized only by the first instance of the stream */
            /* Changing the status of the state to false */
            if (empty_state.value())
            {
                age_of_maturity.update(0);
                empty_state.update(false);
                /* If state is empty, we have to Create a new HoeffdingTree.HoeffdingTree. */
                HoeffdingTree hoeffdingTree = new HoeffdingTree();
                hoeffdingTree.CreateHoeffdingTree(7, 9, 200, 0.01, 0.05, this.combination_function, hoeffding_tree_id, 0);
                hoeffdingTreeValueState.update(hoeffdingTree);
                hoeffdingTree.print_m_features();
                /* Also we create the a new ConceptDriftDetector.ConceptDriftDetector */

                ConceptDriftDetector conceptDriftDetector = ConceptDriftFactory.createConceptDriftDetector(drift_detection_method_id);
                ConceptDriftDetectorValueState.update(conceptDriftDetector);


            }
            /* We have adopted the "age of maturity" notion which provides to the HT to have a slack in order to stabilize its performance.
             * This logic is very important for the concept drift
             *
             * */
            if (age_of_maturity.value() >= age_of_maturity_input)
            {
                if (purpose_id == 5)
                {
                    ConceptDriftDetector conceptDriftDetector = ConceptDriftDetectorValueState.value();
                    int current_stream_status = conceptDriftDetector.getCurrentDriftStatus();

                    /* We have adopted the Test-Then-Train method which has been proven to be more effective */
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id);

                    conceptDriftDetector.FindConceptDrift(ht.getErrorRate());
                    int current_signal = conceptDriftDetector.getSignal();

                    conceptDriftDetector.updateCurrentDriftStatus();
                    int updated_stream_status = conceptDriftDetector.getCurrentDriftStatus();

                    // System.out.println("Current status before testing: " + current_stream_status + " Signal after testing " + current_signal + " makes the updated status " + updated_stream_status + " for " + instance_id);

                    // System.out.println("Training tree " + ht.hoeffding_tree_id + " in phase " + ht.tree_phase + " id " + instance_id + " with error-rate " + ht.getErrorRate());
                    ConceptDriftDetectorValueState.update(conceptDriftDetector);
                    //                        System.out.println("SP          phase: instance id " + instance_id + " Error Rate " + ht.getErrorRate());
                    /*Like Oza and Russell's Online Bagging Algorithm. Authors mention that:
                    "As each training example is presented ot our algorithm, for each base model, choose the example K - Poisson(1) times and update the base model accordingly." */
                    for (int i = 0; i < instance_weight; i++)
                    {
                        ht.UpdateHoeffdingTree(ht.root, features);
                    }
                    //error_rate = ht.getErrorRate();
                    hoeffdingTreeValueState.update(ht);

                    if (drift_detection_method_id != 0)
                    {
                        if (updated_stream_status == 1)
                        {
                            // WARNING PHASE

                            if (empty_background_state.value())
                            {
                                // System.out.println("===================================Warning Phase===================================");
                                // System.out.println("Background Tree " + ht.tree_phase + " Just Created ");
                                empty_background_state.update(false);
                                //                                System.out.println("WS          Signal: instance id " + instance_id);
                                // Warning Signal. Create & Train the Background Tree
                                HoeffdingTree background_hoeffdingTree = new HoeffdingTree();
                                background_hoeffdingTree.CreateHoeffdingTree(7, 9, 200, 0.01, 0.05, this.combination_function, hoeffding_tree_id, 1);
                                // background_hoeffdingTree.print_m_features();
                                background_hoeffdingTreeValueState.update(background_hoeffdingTree);
                            }

                            HoeffdingTree background_hoeffdingTree = background_hoeffdingTreeValueState.value();

                            background_hoeffdingTree.TestHoeffdingTree(background_hoeffdingTree.root, features, purpose_id);
                            // System.out.println("Also background training background tree " + background_hoeffdingTree.hoeffding_tree_id + " in phase " + background_hoeffdingTree.tree_phase + " id " + instance_id + " with error-rate " + background_hoeffdingTree.getErrorRate());

                            //                            System.out.println("WP          phase: instance id " + instance_id + " Error Rate " + background_hoeffdingTree.getErrorRate());
                            for (int i = 0; i < instance_weight; i++)
                            {
                                background_hoeffdingTree.UpdateHoeffdingTree(background_hoeffdingTree.root, features);
                            }
                            background_hoeffdingTreeValueState.update(background_hoeffdingTree);
                        }
                        else if (current_stream_status == 1 && updated_stream_status == 0)
                        {
                            //                            System.out.println("DS          Signal: instance id " + instance_id);
                            if (current_signal == 2)
                            {
                                // System.out.println("=============================Stable Phase/ Drift===================================");
                                // System.out.println("Stable phase after a Drift Signal");
                                // Drift Signal. Do the Switch
                                HoeffdingTree background_tree = background_hoeffdingTreeValueState.value();
                                hoeffdingTreeValueState.update(background_tree);
                                empty_background_state.update(true);
                                // System.out.println("Making the switch and resetting the Drift Detector");
                                //RESET EVERYTHING
                                conceptDriftDetector.ResetConceptDrift();
                            }
                            else if (current_signal == -1)
                            {
                                // System.out.println("=========================Stable Phase/ False Alarm=================================");
                                // System.out.println("Stable phase after a false alarm");
                                //                            System.out.println("FAS         False Alarm Signal: instance id " + instance_id);
                                background_hoeffdingTreeValueState.clear();
                                empty_background_state.update(true);
                                // False Alarm Signal. Delete Background Tree

                            }
                            //System.out.println("Training HT with id " + hoeffding_tree_id + " which has error-rate " + ht.getErrorRate() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                        }
                    }

                    collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, ht.getErrorRate(), hoeffding_tree_id));
                }
                else if (purpose_id == -10 || purpose_id == -5)
                {
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
                    //System.out.println("Testing HT with id " + hoeffding_tree_id + " which has error-rate " + ht.getErrorRate() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                    if (purpose_id == -5)
                    {
                        collector.collect(new Tuple6<>(instance_id, prediction, true_label, purpose_id, ht.getErrorRate(), hoeffding_tree_id));
                    }
                    else
                    {
                        collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, ht.getErrorRate(), hoeffding_tree_id));
                    }
                }
                else if(purpose_id == -1)
                {
                    System.out.println(" End of Stream message");
                    throw new Exception("End of Stream message");
                }
                age_of_maturity.update(age_of_maturity.value() + 1);
            }
            else if (age_of_maturity.value() <= age_of_maturity_input)
            {
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
                if (purpose_id == 5)
                {
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    /* Online Ba*/
                    for (int i = 0; i < instance_weight; i++)
                    {
                        ht.UpdateHoeffdingTree(ht.root, features);
                    }
                    hoeffdingTreeValueState.update(ht);
                }
            }

        }

        @Override
        public void open(Configuration conf)
        {
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
    public static class MyCombinationFunctionAccum
    {
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
    public static class CombinationFunction implements AggregateFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>, MyCombinationFunctionAccum, Double>
    {

        // This is a universal parameter.
        // In case of selected method = 1 which means that we have Weighted Voting using threshold θ then
        // this parameter represents the value of the used threshold θ.
        // In case of selected method = 2 which means that we have Weighted Voting using top-k then
        // this parameter represents the value of the used k for the top-k
        public double parameter;
        public int ht_population;
        public int combination_function;

        public CombinationFunction(int ht_population, int combination_function, double parameter)
        {
            this.combination_function = combination_function;
            this.ht_population = ht_population;
            this.parameter = parameter;
        }

        @Override
        public MyCombinationFunctionAccum createAccumulator()
        {
            return new MyCombinationFunctionAccum();
        }

        /* The merge method is called when two windows are merged.
         * This applies to session windows, which are merged whenever two sessions are collapsed into one
         * by the arrival of an event that bridges the gap between the sessions.
         * When this occurs, the aggregated results-to-date of both sessions are combined by calling merge.
         * */
        public MyCombinationFunctionAccum merge(MyCombinationFunctionAccum a, MyCombinationFunctionAccum b)
        {
            a.class1_weight += b.class1_weight;
            return a;
        }

        public MyCombinationFunctionAccum add(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> value, MyCombinationFunctionAccum acc)
        {
            // value.f0 = instance id
            // value.f1 = prediction
            // value.f2 = true class
            // value.f3 = purpose id
            // value.f4 = weight
            // value.f5 = hoeffding tree id
            acc.instance_id = value.f0;
            acc.true_class = value.f2;
            acc.instances_seen++;
            if (acc.instances_seen == 1)
            {
                acc.prediction_weight = new double[this.ht_population][4];
            }
            // Majority Voting
            if (this.combination_function == 1)
            {
                if (value.f1 != -1)
                {
                    if (value.f1 == 1)
                    {
                        acc.class1_weight += 1;
                    }
                    else
                    {
                        acc.class0_weight += 1;
                    }
                }
                else
                {
                    acc.class0_weight = -1;
                    acc.class1_weight = -1;
                }
            }
            // Weighted Voting with (threshold-θ, top-k)
            else if (this.combination_function == 2)
            {
                // Weighted Voting using threshold θ

                if (value.f4 > acc.best_prediction[0])
                {
                    acc.best_prediction[0] = value.f1;
                    acc.best_prediction[1] = value.f4;
                }
                /* Here we want to cut all the classifiers whose weight is below a given threshold θ.*/
                if (value.f4 > this.parameter)
                {
                    if (value.f1 == 1)
                    {
                        acc.class1_weight += value.f4;
                    }
                    else
                    {
                        acc.class0_weight += value.f4;
                    }
                }
                else
                {
                    /* There is a high chance of not selecting anyone. Thus, in that case we give the prediction of the best one. (highest weight)*/
                    //                        System.out.println("Hoeffding Tree " + value.f5 + " for instance " + acc.instance_id + " has weight of " + value.f4 + " < " + this.parameter);
                }
            }
            // Weighted Voting using top-k
            else if (this.combination_function == 3)
            {
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

        public Double getResult(MyCombinationFunctionAccum acc)
        {
            //            System.out.println(acc.instance_id + " => " + acc.instances_seen);
            if (acc.instances_seen == this.ht_population)
            {
                acc.instances_seen = 0;
                //Majority Voting
                if (this.combination_function == 1)
                {
                    if (acc.class1_weight > acc.class0_weight)
                    {
                        //                        System.out.println("ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    }
                    else
                    {
                        System.out.println("ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                }
                // Weighted Voting with (threshold-θ, top-k)
                else if (combination_function == 2)
                {
                    // Weighted Voting using threshold θ

                    if (acc.class1_weight > acc.class0_weight)
                    {
                        //                            System.out.println("Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    }
                    else if (acc.class0_weight > acc.class1_weight)
                    {
                        //                            System.out.println("Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                    else
                    {
                        //                        System.out.println("(Both class counters are 0. None of the predictions surpassed the given threshold)\nWe return the best returned one. Weighted Voting Method: " + acc.instance_id + " P: " + acc.best_prediction[0] + " , T: " + acc.true_class);
                        return acc.best_prediction[0];
                    }
                }
                // Weighted Voting using top-k
                else if (this.combination_function == 3)
                {
                    Arrays.sort(acc.prediction_weight, Comparator.comparingDouble(o -> o[1]));
                    //                    System.out.println(Arrays.deepToString(acc.prediction_weight));
                    int i;
                    for (i = 0; i < this.parameter; i++)
                    {
                        if (acc.prediction_weight[i][0] == 1)
                        {
                            acc.class1_weight += acc.prediction_weight[i][1];
                        }
                        else if (acc.prediction_weight[i][0] == 0)
                        {
                            acc.class0_weight += acc.prediction_weight[i][1];
                        }
                    }
                    //                    System.out.println("We are " + i + " Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);

                    if (acc.class1_weight > acc.class0_weight)
                    {
                        // System.out.println("We are " + i + " 456 Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    }
                    else if (acc.class0_weight > acc.class1_weight)
                    {
                        // System.out.println("We are " + i + " 123 Weighted Voting method:" + this.combination_function + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                    else
                    {
                        //                        System.out.println("(Both class counters are 0. None of the predictions surpassed the given threshold)");
                        return 0.0;
                    }

                }
            }
            return 0.0;
        }
    }
}