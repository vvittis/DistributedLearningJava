package HoeffdingTree;

import Utilities.Utilities;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import org.apache.commons.math3.distribution.*;

public class Node implements Serializable {

    //implements Serializable
    public static final long serialVersionUID = 2L;

    // Variables node
    public Integer splitAttr;       // splitting attribute
    public Double splitValue;       // splitting value as returned by calculate_information_gain
    public Integer label;           // the label of the node
    public Double information_gain; // the information gain corresponding to the best attribute and the best value
    HashMap<Integer, Integer> labelCounts = new HashMap<>(); // the counter of each label
    /*
     * ||     index 0     ||     index 1     ||
     * || class 1 counter || class 2 counter ||
     *
     * */
    public int m_features;          // randomly selected subset of features
    public int max_examples_seen;   // the number of examples between checks for growth(n_min)
    public double delta;            // one minus the desired probability of choosing the correct feature at any given node
    public double tie_threshold;    // tie threshold between splitting values of selected features for split
    public Integer nmin;            // Keep tracking the number of samples seen
    public Integer nmin_last_check; // Number of instances from last check
    public ArrayList<HashMap<Integer, ArrayList<Double>>> statistics = new ArrayList<>(); // number of values for each column
    public Integer splitting_counter;
    /*
     *  ||          feature 1        || ... ||      feature N            ||     <- ArrayList <
     *  || ------------------------------------------------------------- ||
     *  ||    class1   |  class2     || ... ||    class1   |  class2     ||     <- HashMap    < Integer ,
     *  ||    ------      ------     || ... ||    ------      ------     ||
     *  || weightSum   | weightSum   || ... || weightSum   | weightSum   ||     <- ArrayList < Double >>>
     *  || mean        | mean        || ... || mean        | mean        ||
     *  || varianceSum | varianceSum || ... || varianceSum | varianceSum ||
     * */
    Node leftNode;                 // leftNode
    Node rightNode;                // rightNode

    public Node() {
    }

    //    <===================================== GETTERS AND SETTERS =====================================>

    public ArrayList<HashMap<Integer, ArrayList<Double>>> getStatistics() {
        return statistics;
    }

    public ArrayList<Double> getStatistics(int index, int class_label) {
        return statistics.get(index).get(class_label);
    }

    public HashMap<Integer, Integer> getLabelCounts() {
        return labelCounts;
    }

    public Integer getNmin() {
        return this.nmin;
    }

    public Integer getSplitting_counter(){
        return this.splitting_counter;
    }
    public int countNode(Node root) {

        //base case
        if (root == null)
            return 0;

        //recursive call to left child and right child and
        // add the result of these with 1 ( 1 for counting the root)
        return 1 + countNode(root.leftNode) + countNode(root.rightNode);
    }

    //    <===================================== CREATE HOEFFDING TREE=====================================>

    /**
     * @param m_features        randomly selected subset of features
     * @param max_examples_seen the number of examples between checks for growth(n_min)
     * @param delta             one minus the desired probability of choosing the correct feature at any given node
     * @param tie_threshold     tie threshold between splitting values of selected features for split
     *                          <p> This function is responsible for Creating the Hoeffding tree.</p>
     */

    public void CreateHT
    (int m_features, int max_examples_seen, double delta, double tie_threshold) {
        InitializeStatisticsAndLabelCounts(m_features);
        this.label = 0;
        this.information_gain = 0.0;
        this.nmin = 0;
        this.nmin_last_check = 0;
        this.leftNode = null;
        this.rightNode = null;
        this.splitAttr = null;
        this.splitValue = null;
        this.splitting_counter=0;
        this.delta = delta;
        this.max_examples_seen = max_examples_seen;
        this.m_features = m_features;
        this.tie_threshold = tie_threshold;
    }

    /**
     * <strong> For the Arraylist of Statistics: </strong>
     * The only necessary statistics are:
     * <ol>
     * <li> <strong>WeightSum</strong> which refers to the sum of weights produced by the Online Bagging </li>
     * <li> <strong>Mean</strong></li>
     * <li> <strong>VarianceSum</strong></li>
     * <li> <strong>Min</strong></li>
     * <li> <strong>Max</strong></li>
     * </ol>
     * <strong>For LabelCounters HashMap: </strong>
     * <p>
     * We know that we have a binary problem (0:Non Fraud, 1:Fraud) so we need only 2 <0,?> and <1,?> in order to keep track the
     * label counts
     *
     * @param number_of_attributes <p> Each node has a Samples HashMap and a LabelCounters HashMap
     *                             in order to add new stuff to them, we have to initialize them
     */

    public void InitializeStatisticsAndLabelCounts(int number_of_attributes) {

        // Initialize HashMap for Samples

        ArrayList<HashMap<Integer, ArrayList<Double>>> statistics = this.getStatistics();
        for (int i = 0; i < number_of_attributes; i++) {

            ArrayList<Double> statistics_class1 = new ArrayList<Double>();

            statistics_class1.add(0, 0.0); // weightSum
            statistics_class1.add(1, 0.0); // mean
            statistics_class1.add(2, 0.1); // varianceSum
            statistics_class1.add(3, 0.0); // min
            statistics_class1.add(4, 0.0); // max

            ArrayList<Double> statistics_class2 = new ArrayList<Double>();

            statistics_class2.add(0, 0.0); // weightSum
            statistics_class2.add(1, 0.0); // mean
            statistics_class2.add(2, 0.0); // varianceSum
            statistics_class2.add(3, Double.MAX_VALUE); // min
            statistics_class2.add(4, 0.0); // max

            HashMap<Integer, ArrayList<Double>> feature_hashmap = new HashMap<>();
            feature_hashmap.put(0, statistics_class1); // Statistics for class 0
            feature_hashmap.put(1, statistics_class2); // Statistics for class 1
            statistics.add(i, feature_hashmap);
        }

        // Initialize HashMap for LabelCounters
        HashMap<Integer, Integer> labelcounters = this.getLabelCounts();
        labelcounters.put(0, 0);
        labelcounters.put(1, 0);
    }

    // <===================================== TEST HOEFFDING TREE =====================================>

    /**
     * @param node   For a given node(root)
     * @param sample An array of values of features which will be use for testing
     *               <p> Traverse the tree using sample and return the label of node at which it ends </p>
     */

    public HoeffdingTree.Returninfo TestHT(Node node, String[] sample) {
        Node updatedNode = TraverseTree(node, sample);
        return new HoeffdingTree.Returninfo(updatedNode,updatedNode.label);

    }

    public Node TraverseTree(Node node, String[] sample) {

        if (node.leftNode == null && node.rightNode == null) {
            return node;
        } else {
            //Left child node
            if (Double.parseDouble(sample[node.splitAttr]) <= node.splitValue) {
                return TraverseTree(node.leftNode, sample);
            }
            //Right child node
            else {
                return TraverseTree(node.rightNode, sample);
            }
        }
    }

    // <===================================== REMOVE HOEFFDING TREE =====================================>

    /**
     * @param node This function clears all the Hoeffding Tree. All it needs is the root
     */

    public void RemoveHT(Node node) {

        if (node == null) {
            return;
        } else {
            RemoveHT(node.leftNode);
            RemoveHT(node.rightNode);
            node.splitAttr = null;
            node.splitValue = null;
            node.label = null;
            node.information_gain = 0.0;
            node.labelCounts = null;
            node.m_features = 0;
            node.max_examples_seen = 0;
            node.delta = 0.0;
            node.tie_threshold = 0.0;
            node.nmin = 0;
            node.nmin_last_check = 0;
            node.leftNode = null;
            node.rightNode = null;
            System.gc();

        }
    }

    // <===================================== UPDATE HOEFFDING TREE =====================================>

    /**
     * <strong>Jobs: It is responsible to update the tree.</strong>
     * <ol>
     *     <li> Checks whether or not a split is needed for that given node</li>
     *     <li> If not then traverse the tree and finds the node where the given example has to be inserted</li>
     *     <li> Inserts the new sample to the node returned from the traversal of the tree.</li>
     * </ol>
     *
     * @param node   For a given node
     * @param sample An array of values of attributes aka sample
     */

    public double[] UpdateHT(Node node, String[] sample, int weight, double[] statistics_entropy_history, double[] statistics_ig_history) {
        Node updatedNode = TraverseTree(node, sample);
        double[] ht_statistics = new double[]{-1.0, -1.0};
        if (NeedForSplit(updatedNode)) {
            ht_statistics = AttemptSplit(updatedNode, sample, weight, statistics_entropy_history, statistics_ig_history);
        } else {
            InsertNewSample(updatedNode, sample, weight);
        }
        return ht_statistics;
    }

    /**
     * <strong>The splitting condition is:</strong>
     * <br>
     * If a have seen more than max_examples_seen and the given node is homogeneous
     *
     * @param node For a given HoeffdingTree.Node
     * @return Whether or not a node needs a split
     */

    public boolean NeedForSplit(Node node) {
        if (node.getNmin() - node.nmin_last_check >= node.max_examples_seen) {
//            System.out.println("Current nmin"+ node.getNmin()+" Last Check nmin"+node.nmin_last_check);
            node.nmin_last_check = node.getNmin();
            return CheckHomogeneity(node);
        }
        return false;
        // return node.getNmin() >= node.max_examples_seen && CheckHomogeneity(node);
    }

    /**
     * <strong>If both counters of labels are not equal to 0 then the given node is not homogeneous</strong>
     * <ul>
     *     <li>(0 AND 0) = 0 none from each class</li>
     *     <li>(0 AND 1) = 0 only from class2  -> homogeneous</li>
     *     <li>(1 AND 0) = 0 only from class1  -> homogeneous</li>
     *     <li>(1 AND 1) = 1 from both classes -> not homogeneous</li>
     * </ul>
     *
     * @param node For a given node
     * @return whether or not a given node is homogeneous or not
     */

    public boolean CheckHomogeneity(Node node) {
        HashMap<Integer, Integer> labels_hash_map = node.getLabelCounts();
        return labels_hash_map.get(0) != 0 && labels_hash_map.get(1) != 0;
    }

    /**
     * @param node For a given node
     *             <p> It attempt to split the node </p>
     *             <p> First, find the best attributes to split the node </p>
     *             <p> Second,if the best attributes satisfy the condition(based on epsilon,tie_threshold) then became the splitting of node </p>
     */

    public double[] AttemptSplit(Node node, String[] sample, int weight, double[] statistics_entropy_history, double[] statistics_ig_history) {

        double[][] G = FindTheBestAttribute(node); // informationGain,splitAttr,splitValue-row
        double G1 = G[0][0]; // highest information gain
        double G2 = G[0][1]; // second-highest information gain
        // Calculate epsilon
        double epsilon = CalculateHoeffdingBound(node);

        // Attempt split
        if ((((G1 - G2) > epsilon) || (G1 - G2) < node.tie_threshold)) {

            double[] ht_statistics = {node.information_gain, G1};
//            System.out.println("H "+node.information_gain+",        Mean " +statistics_entropy_history[0] +" Variance "+ statistics_entropy_history[4] +" Add "+ (statistics_entropy_history[0]+statistics_entropy_history[4]));
            int entropy = Double.compare(node.information_gain, (statistics_entropy_history[0] + statistics_entropy_history[4]));

            int ig = Double.compare(G1, (statistics_ig_history[0] + statistics_ig_history[4]));
            // (entropy > 0) || (ig > 0);
            // true; - for testing
            boolean svfdt_ii_constraints =  (entropy > 0) || (ig > 0);
            if (svfdt_ii_constraints) {
                this.splitting_counter ++;
                double[] values = {G[0][0], G[1][0], G[2][0],G[3][0],G[4][0]};

                SplitFunction(node, values);
                if (Double.parseDouble(sample[node.splitAttr]) <= node.splitValue) {
                    InsertNewSample(node.leftNode, sample, weight);
                } else {
                    InsertNewSample(node.rightNode, sample, weight);
                }
            } else {
                InsertNewSample(node, sample, weight);
            }
            return ht_statistics;
        }
        InsertNewSample(node, sample, weight);

        return new double[]{-1.0, -1.0};
    }

    /**
     * <strong> In FindTheBestAttribute there are two phases</strong>
     * <ol>
     *     <li> We set the Best and Second Best attributes by initially assigning the information gain of the first and second features respectively</li>
     *     <li>
     *         <ol>
     *                  <li> If the new Information Gain is greater than the already best G.
     *                      <ol>
     *                          <li> Downgrade the best feature to second place</li>
     *                          <li> Place the new feature to the first place</li>
     *                      </ol>
     *
     *                  </li>
     *                  <li> If the new Information Gain is greater than the second best. Then keep the first one as it is and replace the second one with the new one.</li>
     *         </ol>
     *     </li>
     * </ol>
     *
     * @param node HoeffdingTree.Node which attempts to split
     * @return <p> Finds the best splitting attribute and splitting value for a given node based on Information Gain</p>
     */

    public double[][] FindTheBestAttribute(Node node) {

        double[][] multiples = new double[5][2]; //informationGain,splitAttr,splitValue(rows of array)
        /*
         * +===+========0========+========1========+
         * | - |       GXa       |       GXb       |
         * | 0 | informationGain | informationGain |
         * | 1 |  splitAttribute |  splitAttribute |
         * | 2 |    splitValue   |    splitValue   |
         * +===+=================+=================+
         * */
        for (int i = 0; i < node.m_features; i++) {

//           double[] splitValues = Utilities.Quartiles(val);

            // Calculate informationGain of node for each value and kept the max
            if (i == 0) {
                // GXa = the best attribute and GXb = the second best attribute
                double[] GXa = InformationGain(node, i); // information gain, spitting value
                double[] GXb = InformationGain(node, i + 1);
                // Place the best attribute to the 0 position and the second best to the 1 position...
                if (GXa[0] >= GXb[0]) {
                    // first place
                    multiples[0][0] = GXa[0];
                    multiples[1][0] = i;
                    multiples[2][0] = GXa[1];
                    multiples[3][0] = GXa[2];
                    multiples[4][0] = GXa[3];
                    // second place
                    multiples[0][1] = GXb[0];
                    multiples[1][1] = i + 1;
                    multiples[2][1] = GXb[1];
                    multiples[3][0] = GXb[2];
                    multiples[4][0] = GXb[3];
                    // ... else to the opposite
                } else {
                    // first place
                    multiples[0][0] = GXb[0];
                    multiples[1][0] = i + 1;
                    multiples[2][0] = GXb[1];
                    multiples[3][0] = GXb[2];
                    multiples[4][0] = GXb[3];
                    // second place
                    multiples[0][1] = GXa[0];
                    multiples[1][1] = i;
                    multiples[2][1] = GXa[1];
                    multiples[3][0] = GXa[2];
                    multiples[4][0] = GXa[3];

                }
                // In case the current attribute is not the first (0) attribute
            } else if (i > 1) {
                // Get the G of the first quartile...
                double[] tempG = InformationGain(node, i);


                if (tempG[0] > multiples[0][0]) {

                    // second place (- moving the existing best feature to the second place...)
                    multiples[0][1] = multiples[0][0];
                    multiples[1][1] = multiples[1][0];
                    multiples[2][1] = multiples[2][0];
                    multiples[3][1] = multiples[3][0];
                    multiples[4][1] = multiples[4][0];

                    // ... and adding the new information gain to the first place
                    multiples[0][0] = tempG[0];
                    multiples[1][0] = i;
                    multiples[2][0] = tempG[1];
                    multiples[3][0] = tempG[2];
                    multiples[4][0] = tempG[3];

                }
                // if the tempG is less from the best attribute BUT greater than the second... we put the tempG to the
                // 1 position and keep the already best to the 0 position
                else if (tempG[0] > multiples[0][1]) {
                    multiples[0][1] = tempG[0];
                    multiples[1][1] = i;
                    multiples[2][1] = tempG[1];
                    multiples[3][1] = tempG[2];
                    multiples[4][1] = tempG[3];
                }
                // else do nothing
            }
        }
        return multiples;
    }

    /**
     * @param node      For a given node
     * @param splitAttr Splitting attribute
     *                  <p> Calculate the Information Gain based on on splitAttr and splitValue </p>
     * @return Information Gain of node based on splitAttr and splitValue
     */

    public double[] InformationGain(Node node, int splitAttr) {

        double max = -1000.0;
        double split_point = 0;
        double dominant_left = -1;
        double dominant_right = -1;
        // Calculate entropy node
        ArrayList<Double> statistics_class0 = node.getStatistics(splitAttr, 0);
        ArrayList<Double> statistics_class1 = node.getStatistics(splitAttr, 1);

        double mean0 = statistics_class0.get(1);
        double std0 = Math.sqrt(getStdDev(statistics_class0.get(0), statistics_class0.get(2)));
        if (isZero(std0)) {
            std0 = 0.01;
        }
        double mean1 = statistics_class1.get(1);
        double std1 = Math.sqrt(getStdDev(statistics_class1.get(0), statistics_class1.get(2)));
        NormalDistribution n0 = new NormalDistribution(mean0, std0);
        if (isZero(std1)) {
            std1 = 0.01;
        }

        NormalDistribution n1 = new NormalDistribution(mean1, std1);
        double class0_estimation = statistics_class0.get(0) / node.nmin;
        double class1_estimation = statistics_class1.get(0) / node.nmin;
        double log0 = Math.log(class0_estimation) / Math.log(2);
        double log1 = Math.log(class1_estimation) / Math.log(2);

        if (isZero(statistics_class0.get(0))) {
            log0 = 0;
        } else if (isZero(statistics_class1.get(0))) {
            log1 = 0;
        }

        double entropyNode = (-1) * (class0_estimation * log0) + (-1) * (class1_estimation * log1);
        node.information_gain = entropyNode;
//        ArrayList<Double> results = new ArrayList<>();
//        String str = statistics_class0.get(1) + "," + std0 + "," + statistics_class1.get(1) + "," + std1 + "," + node.labelCounts.get(0) + "," + node.labelCounts.get(1) + "\n";
//        BufferedWriter writer = null;
//        try {
//            writer = new BufferedWriter(new FileWriter("C://Users//kryst//PycharmProjects//VisualizeNormalDistribution//NormalDistributionSink.txt"));
//            writer.write(str);
//            writer.close();
//        } catch (IOException e) {
//            System.out.println("Problem Writing Statistics");
//        }

        double[] candidate_points = FindRange(node, statistics_class0.get(3), statistics_class0.get(4), statistics_class1.get(3), statistics_class1.get(4));// Calculate splitting value
        for (double v : candidate_points) {
//            String string = v + "\n";
//            BufferedWriter writer1 = null;
//            try {
//                writer1 = new BufferedWriter(new FileWriter("C://Users//kryst//PycharmProjects//VisualizeNormalDistribution//NormalDistributionSink.txt", true));
//                writer1.write(string);
//                writer1.close();
//            } catch (IOException e) {
//                System.out.println("Problem Writing Candidate Point" + v);
//            }


            double lower_tail_class0 = n0.cumulativeProbability(v);
            double lower_tail_class1 = n1.cumulativeProbability(v);
            double upper_tail_class0 = 1 - lower_tail_class0;
            double upper_tail_class1 = 1 - lower_tail_class1;


            // Left Node Entropy
            double entropyLeftNode;
            double totalLeftProbability = lower_tail_class0 + lower_tail_class1;
            double lower_tail_class00 = (lower_tail_class0 / totalLeftProbability);
            double lower_tail_class11 = (lower_tail_class1 / totalLeftProbability);
            log0 = Math.log(lower_tail_class00) / Math.log(2);
            log1 = Math.log(lower_tail_class11) / Math.log(2);
            if (isZero(lower_tail_class0)) {
                log0 = 0;
            } else if (isZero(lower_tail_class1)) {
                log1 = 0;
            }

            if (totalLeftProbability == 0) {
                entropyLeftNode = 0;
            } else {
                entropyLeftNode = (-1) * (lower_tail_class00 * log0) + (-1) * (lower_tail_class11 * log1);
            }

            // Right Node Entropy
            double entropyRightNode;
            double totalRightProbability = upper_tail_class0 + upper_tail_class1;
            double upper_tail_class00 = (upper_tail_class0 / totalRightProbability);
            double upper_tail_class11 = (upper_tail_class1 / totalRightProbability);
            log0 = Math.log(upper_tail_class00) / Math.log(2);
            log1 = Math.log(upper_tail_class11) / Math.log(2);
            if (isZero(upper_tail_class0)) {
                log0 = 0;
            } else if (isZero(upper_tail_class1)) {
                log1 = 0;
            }

            if (totalRightProbability == 0) {
                entropyRightNode = 0;
            } else {
                entropyRightNode = (-1) * (upper_tail_class00 * log0) + (-1) * (upper_tail_class11 * log1);
            }
            double totalProbability = totalLeftProbability + totalRightProbability;

            double weightedEntropy = ((totalLeftProbability / totalProbability) * entropyLeftNode) + ((totalRightProbability / totalProbability) * entropyRightNode);

            double entropyNode1 = entropyNode - weightedEntropy;
//            results.add(entropyNode1);
//            System.out.println(v + " => " + entropyNode1 + "," + max);
            if (Double.compare(entropyNode1, max) > 0) {
                max = entropyNode1;
                split_point = v;
                if( lower_tail_class00 > lower_tail_class11){
                    dominant_left = 0;
                }
                else if( lower_tail_class00 < lower_tail_class11){
                    dominant_left = 1;
                }
                if(upper_tail_class00 > upper_tail_class11){
                    dominant_right = 0;
                }
                else if(upper_tail_class00 < upper_tail_class11){
                    dominant_right = 1;
                }
            }
        }

        double[] returned_values = new double[4];
        returned_values[0] = max;
        returned_values[1] = split_point;
        returned_values[2] = dominant_left;
        returned_values[3] = dominant_right;
        return returned_values;

    }

    public double[] FindRange(Node node, double min_class1, double max_class1, double min_class2, double max_class2) {

        double min = min_class2;
        double max = max_class2;
        if (min_class1 > min_class2) {
            min = min_class1;
        }
        if (max_class1 < max_class2) {
            max = max_class1;
        }

        return Utilities.SplitNormalDistribution(10, min, max);
    }

    public boolean isZero(double value) {
        return value >= -0.00000001 && value <= 0.0000001;
    }

    public double getStdDev(double weightSum, double varianceSum) {
        return varianceSum / (weightSum - 1);
    }

    /**
     * This function calculates the Hoeffding Bound
     * Hoeffding bound states; Given a random variable r whose range is R ( in our case we have
     * a binary classification problem, so R = logc where c = 2 (number of classes)) and n observations
     * of our examples, the true mean differs from the calculated (by this function) mean at most e(epsilon)
     * with a given probability 1-delta
     */

    public double CalculateHoeffdingBound(Node node) {
        double R = 1;
        double n = node.getNmin();
        double ln = Math.log(1.0 / node.delta);
        double numerator = Math.pow(R, 2) * ln;
        double denominator = 2 * n;
        double fraction = numerator / denominator;
        return Math.sqrt(fraction);
    }

    /**
     * @param node   For a given node
     * @param values Correspond to informationGain,splitAttribute,splitValue for node
     *               <p> It is responsible to split the node and create the left and right child-node </p>
     */

    public void SplitFunction(Node node, double[] values) {
        // Generate nodes
        Node child1 = new Node();
        Node child2 = new Node();

        // Initialize parent and child nodes
        node.leftNode = child1;
        node.rightNode = child2;
        node.nmin_last_check = 0;
        node.nmin = 0;

        // Initialize informationGain,splitAttribute,splitValue for node(parent-node)
        node.splitAttr = (int) values[1];
        node.splitValue = values[2];

        // Initialize nmin,information_gain,label for children nodes
        child1.nmin = 0;
        child1.nmin_last_check = 0;
        child2.nmin = 0;
        child2.nmin_last_check = 0;
        child1.information_gain = 0.0;
        child2.information_gain = 0.0;
        child1.label = (int) values[3];
        child2.label = (int) values[4];


        // Initialize m_features,max_examples_seen,delta,tie_threshold for children nodes
        child1.m_features = node.m_features;
        child2.m_features = node.m_features;
        child1.max_examples_seen = node.max_examples_seen;
        child2.max_examples_seen = node.max_examples_seen;
        child1.delta = node.delta;
        child2.delta = node.delta;
        child1.tie_threshold = node.tie_threshold;
        child2.tie_threshold = node.tie_threshold;

        // Initialize samples,label counters for children nodes
        child1.InitializeStatisticsAndLabelCounts(child1.m_features);
        child2.InitializeStatisticsAndLabelCounts(child2.m_features);

        // Clear samples,labelCounts,label_List,setOfAttr on parent node
        node.labelCounts.clear();

    }

    /**
     * <strong>Jobs:</strong
     * <ol>
     *     <li> Add the value of each attribute to the corresponding list of the node</li>
     *     <li> Update the labelCounter given the label which comes with the sample</li>
     *     <li> Update the nmin - aka that you have added another sample to the node</li>
     * </ol>
     *
     * @param node   For a given node
     * @param sample The sample that has to be added in the given node
     */

    public void InsertNewSample(Node node, String[] sample, int weight) {
        ArrayList<HashMap<Integer, ArrayList<Double>>> statistics = node.getStatistics();
        if (node.labelCounts.get(0) == 0 || node.labelCounts.get(1) == 0) {
            for (int k = 0; k <= 1; k++) {
                if (node.labelCounts.get(k) == 0 && (k == Integer.parseInt(sample[sample.length - 1]))) {
                    for (int i = 0; i < node.m_features; i++) {
                        ArrayList<Double> statistics_per_class = statistics.get(i).get(k);

                        statistics_per_class.set(0, (double) weight); // weightSum
                        statistics_per_class.set(1, Double.parseDouble(sample[i])); // mean
                        statistics_per_class.set(2, 0.0); // varianceSum
                        statistics_per_class.set(3, Double.parseDouble(sample[i]));
                        statistics_per_class.set(4, Double.parseDouble(sample[i]));

                    }
                    int label = Integer.parseInt(sample[sample.length - 1]);
                    UpdateLabelCounters(node, label, weight);
                    UpdateNMin(node, weight);
                    return;
                }
            }
        }
        for (int i = 0; i < node.m_features; i++) {
            double value = Double.parseDouble(sample[i]);
            int label = Integer.parseInt(sample[sample.length - 1]);
            ArrayList<Double> local_statistics = statistics.get(i).get(label);
            double weightSum = local_statistics.get(0) + weight;
            local_statistics.set(0, weightSum); // weightedSum;
            double lastMean = local_statistics.get(1);
            double mean = local_statistics.get(1) + ((value - lastMean) / weightSum);
            local_statistics.set(1, mean); // mean
            double varianceSum = local_statistics.get(2) + ((value - lastMean) * (value - mean));
            local_statistics.set(2, varianceSum); // varianceSum

            if (Double.compare(value, local_statistics.get(3)) < 0) {
                // current value is less than existing min value
                local_statistics.set(3, value);
            } else if (Double.compare(value, local_statistics.get(4)) > 0) {
                // current value is greater than existing masx value
                local_statistics.set(4, value);
            }
        }
        int label = Integer.parseInt(sample[sample.length - 1]);
        UpdateLabelCounters(node, label, weight);
        UpdateNMin(node, weight);
    }

    /**
     * @param node  For a given node
     * @param label For a given label (0 or 1)
     *
     *              <p> This function extracts from a node the LabelsCount HashMap the counter (.get)
     *              and then updates it end put it again back to the HashMap (.put)
     */

    public void UpdateLabelCounters(Node node, Integer label, int weight) {
        HashMap<Integer, Integer> labels_hash_map = node.getLabelCounts();
        labels_hash_map.put(label, labels_hash_map.get(label) + weight);
        UpdateNodeClassLabel(node);
    }

    /**
     * @param node Update the class label of a given node comparing the LabelCounts of class 0 and 1
     */

    public void UpdateNodeClassLabel(Node node) {
        HashMap<Integer, Integer> labels_hash_map = node.getLabelCounts();
        int label0 = labels_hash_map.get(0);
        int label1 = labels_hash_map.get(1);
        if (label0 > label1) {
            node.label = 0;
        } else {
            node.label = 1;
        }
    }

    /**
     * <p> This function updates the nmin (= how many examples I have seen in a given node so far)
     * This function is called every time a new example is arriving to a node (= not when an examples is passing
     * through a node aka traversal function)
     */

    public void UpdateNMin(Node node, int weight) {
        node.nmin = node.nmin + weight;
    }

}
