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

        /* @info: Reading from Kafka Source the input stream. For more information:  https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/kafka.html */


        DataStream<String> stream = env
                .addSource(new FlinkKafkaConsumer010<>("testSource002", new SimpleStringSchema(), properties).setStartFromEarliest()).name("Kafka Input Source");

        /*
         * @info: The above FlatMapFunction refers to the PopulateInputStream function to the <Section> on my thesis.
         * It servers the purpose as its name dictates.
         * It takes as an input the input stream is of String type
         */

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

                for (int i = 1; i <= Integer.parseInt(params.get("number_of_HT")); i++) {
                    out.collect(new Tuple2<>(input_stream.trim(), i));
                }
            }
        }).name("Populate Input Stream");

        // STATE
        SingleOutputStreamOperator<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>> partial_result =
                structured_stream.
                        keyBy(1).
                        flatMap(new StatefulMap(Integer.parseInt(params.get("combination_function")), Integer.parseInt(params.get("age_of_maturity")))).name("Machine Learning Model");

        // COMBINATION FUNCTION
        // Majority Voting
        if (Integer.parseInt(params.get("combination_function")) == 1) {
            System.out.println("Majority Voting");
            partial_result.keyBy(0).
                    countWindow(Integer.parseInt(params.get("number_of_HT"))).
                    aggregate(new MyMajorityVoting());
        }
        // Weighted Voting with threshold θ or top-k
        else if (Integer.parseInt(params.get("combination_function")) == 2) {

            /* weighted_voting_option = 1 for Weighted Voting using threshold θ(theta)
             * weighted_voting_option = 2 for Weighted Voting using top-k
             * */
            System.out.println("Weighted Voting with threshold theta");
            partial_result.keyBy(0).
                    countWindow(Integer.parseInt(params.get("number_of_HT"))).
                    aggregate(new MyWeightedAvg(Integer.parseInt(params.get("number_of_HT")), Integer.parseInt(params.get("weighted_voting_option")), Double.parseDouble(params.get("weighted_voting_parameter")))).name("Combination Function");
        }
        // Weighted Voting with DES-P
        else if (Integer.parseInt(params.get("combination_function")) == 3) {
            // weighted_voting_option = 3 for DES-P
            partial_result.keyBy(0).
                    countWindow(Integer.parseInt(params.get("number_of_HT"))).
                    aggregate(new MyWeightedAvg(Integer.parseInt(params.get("number_of_HT")), Integer.parseInt(params.get("weighted_voting_option")), Double.parseDouble(params.get("weighted_voting_parameter"))));
        }
        // Weighted Voting with KNORA-U
        else if (Integer.parseInt(params.get("combination_function")) == 4) {
            // weighted_voting_option = 4 for KNORA-U
            partial_result.keyBy(0).
                    countWindow(Integer.parseInt(params.get("number_of_HT"))).
                    aggregate(new MyWeightedAvg(Integer.parseInt(params.get("number_of_HT")), Integer.parseInt(params.get("weighted_voting_option")), Double.parseDouble(params.get("weighted_voting_parameter"))));
        }

        partial_result.addSink(new FlinkKafkaProducer010<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>("localhost:9092", "my-topic2222", new SerializationSchema<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>>() {

//            @Override
//            public byte[] serialize(Object o) {
//                return new byte[0];
//            }

            @Override
            public byte[] serialize(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> element) {
                return  (element.getField(5).toString()+ ","+element.getField(4).toString()+","+element.getField(0).toString()).getBytes();
            }
        })).name("Visualizing Performance Metrics");

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
        private transient ValueState<Boolean> empty_state;
        private transient ValueState<Integer> age_of_maturity;
        public int combination_function = 0;
        public int age_of_maturity_input = 0;

        public StatefulMap(int combination_function, int age_of_maturity_input) {
            this.combination_function = combination_function;
            this.age_of_maturity_input = age_of_maturity_input;
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

//            System.out.println("Input Stream " + input_stream.getField(0));
            if (empty_state.value()) {
                age_of_maturity.update(0);
                /* The state will be initialized only by the first instance of the stream */
                /* Changing the status of the state to false */
                empty_state.update(false);
                System.out.println("State is empty... starting to Create the Hoeffding Tree with id: " + input_stream.getField(1));
                /* If state is empty, we have to Create a new HoeffdingTree */
                HoeffdingTree hoeffdingTree = new HoeffdingTree();
                hoeffdingTree.CreateHoeffdingTree(13, 14, 10, 0.9, 0.15, combination_function);
                hoeffdingTree.print_m_features();
                hoeffdingTreeValueState.update(hoeffdingTree);
                System.out.println("...HT created");
            }
            /* We have adopted the "age of maturity" notion which provides to the HT to have a slack in order to stabilize its performance.
             * This logic is very important for the concept drift
             *
             * */
            if (age_of_maturity.value() >= age_of_maturity_input) {
//                System.out.println("We are now in an STABLE phase. Age of maturity:" + age_of_maturity.value() +" instance id "+ instance_id);


                if (purpose_id == -10 || purpose_id == -5) {
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    int prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id);
                    /* Despite the fact that we only test the Hoeffding Tree and we do not update it calling UpdateHoeffdingTree function,
                     * we have to update the state because internally TestHoeffdingTree function sets the HT's weight.
                     * Otherwise, we would have had the same weight throughout the streaming passage.
                     * */
                    hoeffdingTreeValueState.update(ht);
                    /* In case of instances which are fet to the system for prediction, we do not know its true label.
                     * Therefore, our need for a homogeneous output from the state, leads with no other choice of assigning
                     * an identifier in the true_label position (aka 3rd Integer in the collector)
                     * */
                    System.out.println("Testing HT with id " + hoeffding_tree_id + " which has weight " + ht.getWeight() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                    if (purpose_id == -5) {
                        collector.collect(new Tuple6<>(instance_id, prediction, true_label, purpose_id, ht.getWeight(), hoeffding_tree_id));
                    } else {
                        collector.collect(new Tuple6<>(instance_id, prediction, -1, purpose_id, ht.getWeight(), hoeffding_tree_id));
                    }
                } else if (purpose_id == 5) {
//                    System.out.println("Training instance " + instance_id + " Hoeffding Id" + hoeffding_tree_id);
                    /* We have adopted the test-then-train method which has been proven to be more effective */
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    int prediction = ht.TestHoeffdingTree(ht.root, features, purpose_id);
                    ht.UpdateHoeffdingTree(ht.root, features);
//                    System.out.println("Training HT with id " + hoeffding_tree_id + " which has weight " + ht.getWeight() + " predicts " + prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
                    hoeffdingTreeValueState.update(ht);
                }
                age_of_maturity.update(age_of_maturity.value() + 1);
            } else {
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
//                System.out.println("We are now in an unstable phase. Age of maturity:" + age_of_maturity.value()+" instance id "+ instance_id +" Hoeffding Id "+ hoeffding_tree_id);
                if (purpose_id == 5) {
                    HoeffdingTree ht = hoeffdingTreeValueState.value();
                    ht.UpdateHoeffdingTree(ht.root, features);
                    hoeffdingTreeValueState.update(ht);
                }

            }

        }

        @Override
        public void open(Configuration conf) {

            /*Hoeffding Tree*/
            ValueStateDescriptor<HoeffdingTree> descriptor = new ValueStateDescriptor<HoeffdingTree>("hoeffdingTreeValueState", HoeffdingTree.class);
            hoeffdingTreeValueState = getRuntimeContext().getState(descriptor);
            System.out.println("this is a stop point");
            /*Empty State*/
            ValueStateDescriptor<Boolean> descriptor1 = new ValueStateDescriptor<Boolean>("empty_state", TypeInformation.of(Boolean.TYPE), true);
            empty_state = getRuntimeContext().getState(descriptor1);
            /*Age of Maturity*/
            ValueStateDescriptor<Integer> descriptor2 = new ValueStateDescriptor<Integer>("age_of_maturity", TypeInformation.of(Integer.TYPE), 0);
            age_of_maturity = getRuntimeContext().getState(descriptor2);

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
    public static class MyWeightedAvgAccum {
        public int instance_id = 0;
        public int true_class = 0;
        public int instances_seen = 0;
        public double class0_weight = 0;
        public double class1_weight = 0;
        double[][] prediction_weight = new double[5][4];
        public int selected_predictions = 0;
        public double[] best_prediction = {0.0, 0.0}; //the first index contains the prediction and the second index its weight
    }

    /**
     * Weighted Average user-defined aggregate function.
     */
    public static class MyWeightedAvg implements AggregateFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>, MyWeightedAvgAccum, Double> {

        public int selected_method = 0;
        // This is a universal parameter.
        // In case of selected method = 1 which means that we have Weighted Voting using threshold θ then
        // this parameter represents the value of the used threshold θ.
        // In case of selected method = 2 which means that we have Weighted Voting using top-k then
        // this parameter represents the value of the used k for the top-k
        public double parameter = 0.0;
        public int ht_population;

        public MyWeightedAvg(int ht_population, int wv, double parameter) {
            this.selected_method = wv;
            this.parameter = parameter;

        }

        @Override
        public MyWeightedAvgAccum createAccumulator() {
            return new MyWeightedAvgAccum();
        }

        /* The merge method is called when two windows are merged.
         * This applies to session windows, which are merged whenever two sessions are collapsed into one
         * by the arrival of an event that bridges the gap between the sessions.
         * When this occurs, the aggregated results-to-date of both sessions are combined by calling merge.
         * */
        public MyWeightedAvgAccum merge(MyWeightedAvgAccum a, MyWeightedAvgAccum b) {
            a.class1_weight += b.class1_weight;
            return a;
        }

        public MyWeightedAvgAccum add(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> value, MyWeightedAvgAccum acc) {
            // value.f0 = instance id
            // value.f1 = prediction
            // value.f2 = true class
            // value.f3 = purpose id
            // value.f4 = weight
            // value.f5 = hoeffding tree id
            acc.instance_id = value.f0;
            acc.true_class = value.f2;
            acc.instances_seen++;

            // Weighted Voting using threshold θ
            if (this.selected_method == 1) {
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
                    System.out.println("Hoeffding Tree " + value.f5 + " for instance " + acc.instance_id + " has weight of " + value.f4 + " < " + this.parameter);
                }
            }
            // Weighted Voting using top-k
            else if (this.selected_method == 2) {
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
            // Weighted Voting using DESP-K
            else if (this.selected_method == 3) {
                if (value.f4 > 0) {
                    acc.selected_predictions++;
                    if (value.f1 == 1) {
                        acc.class1_weight += value.f4;
                    } else {
                        acc.class0_weight += value.f4;
                    }
                }
            }
            // Weighted Voting using KNORA_U
            else if (this.selected_method == 4) {
                if (value.f1 == 1) {
                    acc.class1_weight += value.f4;
                } else {
                    acc.class0_weight += value.f4;
                }
            }
            return acc;

        }

        public Double getResult(MyWeightedAvgAccum acc) {
            System.out.println(acc.instance_id+" => "+acc.instances_seen);
            if (acc.instances_seen == 5) {
                acc.instances_seen = 0;
                // Weighted Voting using threshold θ
                if (this.selected_method == 1) {

                    if (acc.class1_weight > acc.class0_weight) {
                        System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else if (acc.class0_weight > acc.class1_weight) {
                        System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    } else {
                        System.out.println("(Both class counters are 0. None of the predictions surpassed the given threshold)\nWe return the best returned one. Weighted Voting Method: " + acc.instance_id + " P: " + acc.best_prediction[0] + " , T: " + acc.true_class);
                        return acc.best_prediction[0];
                    }
                }
                // Weighted Voting using top-k
                else if (this.selected_method == 2) {
                    System.out.println("hi");
                    System.out.println(acc.class0_weight+" "+acc.class1_weight);
                    Arrays.sort(acc.prediction_weight, Comparator.comparingDouble(o -> o[1]));
//                    System.out.println(Arrays.deepToString(acc.prediction_weight));
                    for (int i = 0; i <= this.parameter; i++) {
                        if (acc.prediction_weight[i][0] == 1) {
                            acc.class1_weight += acc.prediction_weight[i][1];
                        } else if (acc.prediction_weight[i][0] == 0) {
                            acc.class0_weight += acc.prediction_weight[i][1];
                        }
                    }
                    if (acc.class1_weight > acc.class0_weight) {
                        System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else if (acc.class0_weight > acc.class1_weight) {
                        System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                    return 0.0;
                }
                // Weighted Voting using DES-P
                else if (this.selected_method == 3) {
                    if (acc.selected_predictions > 0) {
                        if (acc.class1_weight > acc.class0_weight) {
                            System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                            return 1.0;
                        } else if (acc.class0_weight > acc.class1_weight) {
                            System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                            //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                            return 0.0;
                        }
                    } else {
                        return -1.0;
                    }
                }
                // Weighted Voting using KNORA-U
                else if (this.selected_method == 4) {
                    if (acc.class1_weight > acc.class0_weight) {
                        System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 1, T: " + acc.true_class);
                        return 1.0;
                    } else if (acc.class0_weight > acc.class1_weight) {
                        System.out.println("Weighted Voting method:" + selected_method + " ID: " + acc.instance_id + " C0: " + acc.class0_weight + " C1: " + acc.class1_weight + " ... P: 0, T: " + acc.true_class);
                        //                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                        return 0.0;
                    }
                }
            }
            return 0.0;
        }

    }


    /**
     * Accumulator for Majority Voting.
     * MyMajorityVoting has three fields.
     * 1. instances_seen is the value that ensures that the getResult will return the final prediction for a given instance only if
     * the accumulated instances are equal to the number of hoeffding trees
     * 2. class_counter0 variable is for the first class which represents the non-negative class
     * 3. class_counter1 variable is for the second class which represents the negative class
     */
    public static class MyMajorityVotingAcc {
        public int instance_id = 0;
        public int true_class = 0;
        public int instances_seen = 0;
        public int class_counter0 = 0;
        public int class_counter1 = 0;
    }

    /**
     * Majority Voting user-defined aggregate function.
     */
    public static class MyMajorityVoting implements AggregateFunction<Tuple6<Integer, Integer, Integer, Integer, Double, Integer>, MyMajorityVotingAcc, Double> {

        @Override
        public MyMajorityVotingAcc createAccumulator() {
            return new MyMajorityVotingAcc();
        }

        /* The merge method is called when two windows are merged.
         * This applies to session windows, which are merged whenever two sessions are collapsed into one
         * by the arrival of an event that bridges the gap between the sessions.
         * When this occurs, the aggregated results-to-date of both sessions are combined by calling merge.
         * */
        public MyMajorityVotingAcc merge(MyMajorityVotingAcc a, MyMajorityVotingAcc b) {
            return a;
        }

        public MyMajorityVotingAcc add(Tuple6<Integer, Integer, Integer, Integer, Double, Integer> value, MyMajorityVotingAcc acc) {
            acc.instance_id = value.f0;
            acc.instances_seen++;
            acc.true_class = value.f2;
            if (value.f1 != -1) {
                if (value.f1 == 1) {
                    acc.class_counter1 += value.f4;
                } else {
                    acc.class_counter0 += value.f4;
                }
            } else {
                acc.class_counter0 = -1;
                acc.class_counter1 = -1;
            }
            return acc;
        }

        public Double getResult(MyMajorityVotingAcc acc) {
            if (acc.instances_seen == 3 && acc.class_counter0 != -1) {
                acc.instances_seen = 0;
                if (acc.class_counter1 > acc.class_counter0) {
                    System.out.println("ID: " + acc.instance_id + " C0: " + acc.class_counter0 + " C1: " + acc.class_counter1 + " ... P: 1, T: " + acc.true_class);
                    return 1.0;
                } else {
                    System.out.println("ID: " + acc.instance_id + " C0: " + acc.class_counter0 + " C1: " + acc.class_counter1 + " ... P: 0, T: " + acc.true_class);
//                    System.out.println("Instance id " + acc.instance_id + " has acc.class0_weight " + acc.class0_weight + " from one class and acc.class1_weight " + acc.class1_weight + " ... so final prediction is 0");
                    return 0.0;
                }
            } else {
                System.out.println("Can not predict ID: " + acc.instance_id + " C0: " + acc.class_counter0 + " C1: " + acc.class_counter1 + " ... P: 0, T: " + acc.true_class);
                return -1.0;
            }
        }
    }
}


//SwingUtilities.invokeLater(LineGraph::doRun);