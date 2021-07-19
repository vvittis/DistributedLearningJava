package HoeffdingTree;

import java.io.Serializable;
import java.util.*;

public class Node implements Serializable {

    //implements Serializable
    private static final long serialVersionUID=2L;

    // Variables node
    public Integer splitAttr;       // splitting attribute
    public Double splitValue;       // splitting value as returned by calculate_information_gain
    public Integer label;           // the label of the node
    HashMap<Integer, Integer> labelCounts = new HashMap<>(); // the counter of each label
    public Integer nmin;            // Keep tracking the number of samples seen
    public Double information_gain; // the information gain corresponding to the best attribute and the best value
    public Integer setOfAttr;       // set of features
    public HashMap<Integer, LinkedList<Double>> samples = new HashMap<>(); // number of values for each column
    public ArrayList<Integer> label_List = new ArrayList<>(); // list of labels corresponding to samples of node
    Node leftNode;                 // leftNode
    Node rightNode;                // rightNode
    Node parentNode;               // parentNode
    public int m_features;         // randomly selected subset of features
    public int max_examples_seen;  // the number of examples between checks for growth(n_min)
    public double delta;           // one minus the desired probability of choosing the correct feature at any given node
    public double tie_threshold;   // tie threshold between splitting values of selected features for split

    // left , >=
    // right , <

    // Constructor

    public Node() { }


    // Getter and Setter

    public HashMap<Integer, LinkedList<Double>> getSamples() {
        return samples;
    }

    public HashMap<Integer, Integer> getLabelCounts() {
        return labelCounts;
    }

    public Integer getNmin() {
        return this.nmin;
    }

    public int getClassLabel(Node node) {
        return node.label;
    }

    public Integer getSplitAttr() {
        return splitAttr;
    }

    public Double getSplitValue() {
        return splitValue;
    }

    public Double getInformation_gain() {
        return information_gain;
    }

    public Integer getSetOfAttr() {
        return setOfAttr;
    }

    public ArrayList<Integer> getLabel_List() {
        return label_List;
    }

    public Node getLeftNode() {
        return leftNode;
    }

    public Node getRightNode() {
        return rightNode;
    }

    public Node getParentNode() {
        return parentNode;
    }

    public int getM_features() { return m_features; }

    public int getMax_examples_seen() { return max_examples_seen; }

    public double getDelta() { return delta; }

    public double getTie_threshold() { return tie_threshold; }


    // Methods

    /**
     * @param node      - For a given HoeffdingTree.Node
     * @param attribute - For a given Attribute
     * @return - Get the whole list containing the values of this attribute
     */
    public LinkedList<Double> getAttributeList(Node node, int attribute) { return node.samples.get(attribute); }

    /**
     * @param number_of_attributes <p> Each node has a Samples HashMap and a LabelCounters HashMap
     *                             in order to add new stuff to them, we have to initialize them
     *                             For Samples HashMap:
     *                             We know apriori the number of attributes that we have, so we create as many lists as the number of attributes
     *                             For LabelCounters HashMap:
     *                             We know that we have a binary problem (0:Non Fraud, 1:Fraud) so we need only 2 <0,?> and <1,?> in order to keep track the
     *                             label counts
     */
    public void InitializeHashMapSamplesAndLabelCounts(int number_of_attributes) {

        // Initialize HashMap for Samples
        HashMap<Integer, LinkedList<Double>> samples = this.getSamples();
        for (int i = 0; i < number_of_attributes; i++) {
            LinkedList<Double> list = new LinkedList<>();
            samples.put(i, list);
        }

        // Initialize HashMap for LabelCounters
        HashMap<Integer, Integer> labelcounters = this.getLabelCounts();
        labelcounters.put(0, 0);
        labelcounters.put(1, 0);
    }

    /**
     * <p> This function updates the nmin (= how many examples I have seen in a given node so far)
     * This function is called every time a new example is arriving to a node (= not when an examples is passing
     * through a node aka traversal function)
     */
    public void UpdateNMin(Node node) {
        node.nmin = node.nmin + 1;
    }

    /**
     * @param node  For a given node
     * @param label For a given label (0 or 1)
     *
     *              <p> This function extracts from a node the LabelsCount HashMap the counter (.get)
     *              and then updates it end put it again back to the HashMap (.put)
     */
    public void UpdateLabelCounters(Node node, Integer label) {
        HashMap<Integer, Integer> labels_hash_map = node.getLabelCounts();
        labels_hash_map.put(label, labels_hash_map.get(label) + 1);
        UpdateNodeClassLabel(node);
    }

    /**
     * @param node Update the class label of a given node comparing the LabelCounts of class 0 and 1
     */
    public void UpdateNodeClassLabel(Node node) {
        HashMap<Integer, Integer> labels_hash_map = node.getLabelCounts();
        int label0 = labels_hash_map.get(0);
        int label1 = labels_hash_map.get(1);
        if (label0 > label1) { node.label = 0; }
        else { node.label = 1; }
    }

    /**
     * @param node For a given node
     * @return whether or not a given node is homogeneous or not
     * <p> If both counters of labels are not equal to 0 then the given node is not homogeneous
     *      &&
     *     class2   class1
     *      0   AND 0 = 0 none from each class
     *      0   AND 1 = 0 only from class2  -> homogeneous
     *      1   AND 0 = 0 only from class1  -> homogeneous
     *      1   AND 1 = 1 from both classes -> not homogeneous
     *
     * </p>
     */
    public boolean CheckHomogeneity(Node node) {
        HashMap<Integer, Integer> labels_hash_map = node.getLabelCounts();
        return labels_hash_map.get(0) != 0 && labels_hash_map.get(1) != 0;
    }

    /**
     * @param m_features randomly selected subset of features
     * @param max_examples_seen the number of examples between checks for growth(n_min)
     * @param delta one minus the desired probability of choosing the correct feature at any given node
     * @param tie_threshold tie threshold between splitting values of selected features for split
     * <p> This function is responsible for Creating the Hoeffding tree.</p>
     */
    public void CreateHT(int m_features, int max_examples_seen,double delta,double tie_threshold) {
        InitializeHashMapSamplesAndLabelCounts(m_features);
        this.label = 0;
        this.information_gain = null;
        this.nmin = 0;
        this.leftNode = null;
        this.rightNode = null;
        this.parentNode = null;
        this.splitAttr = null;
        this.splitValue = null;
        this.setOfAttr = m_features;
        this.delta = delta;
        this.max_examples_seen = max_examples_seen;
        this.m_features = m_features;
        this.tie_threshold = tie_threshold;
    }

    /**
     * @param node For a given node(root)
     * @param sample An array of values of features which will be use for testing
     *              <p> Traverse the tree using sample and return the label of node at which it ends </p>
     */
    public int TestHT(Node node, String[] sample) {
        Node updatedNode = TraverseTree(node, sample);
        return updatedNode.label;
    }

    /**
     * </p>This function is responsible for finding the maximum depth of tree
     *
     * @param node root of tree
     * @return "height" of the tree
     */
    public int MaxDepth(Node node) {
        if (node == null) { return 0; }
        else {
            // Compute the depth of each subtree
            int lDepth = MaxDepth(node.leftNode);
            int rDepth = MaxDepth(node.rightNode);

            // Use the larger one
            if (lDepth > rDepth) return (lDepth + 1);
            else return (rDepth + 1);
        }
    }

    /**
     * @param node This function clears all the Hoeffding Tree. All it needs is the root
     */
    public void RemoveHT(Node node){

        if( node == null ){ return;}
        else{
            RemoveHT(node.leftNode);
            RemoveHT(node.rightNode);

            node.label = null;
            node.information_gain = 0.0;
            node.nmin = 0;
            node.leftNode = null;
            node.rightNode = null;
            node.parentNode = null;
            node.splitAttr = null;
            node.splitValue = null;
            node.setOfAttr = null;
            node.label_List.clear();
            node.samples.clear();
            node.m_features = 0;
            node.max_examples_seen = 0;
            node.delta = 0.0;
            node.tie_threshold = 0.0;
            System.gc();
        }
    }

    /**
     * @param node For a given node
     *             <p> Return the root of Hoeffding tree </p>
     */
    public Node FindRoot(Node node) {

        if (node.parentNode == null) { return node; }
        else {
            Node newNode = node.parentNode;
            while (newNode.parentNode != null) { newNode = newNode.parentNode; }
            return newNode;
        }
    }

    /**
     * @param node For a given HoeffdingTree.Node
     * @return Whether or not a node needs a split
     * The splitting condition is:
     * If a have seen max_examples_seen and the given node is homogeneous
     */
    public boolean NeedForSplit(Node node) { return node.getNmin() >= node.max_examples_seen && CheckHomogeneity(node); }
    // Stop splitting based on setOfAttr or entropy of node(=0)


    /**
     * @param node   For a given node
     * @param sample An array of values of attributes aka sample
     *               <p> It is responsible to update the tree.
     *               Jobs:
     *               1. Checks whether or not a split is needed for that given node
     *               2. If not then traverse the tree and finds the node where the given example has to be inserted
     *               3. Inserts the new sample to the node returned from the traversal of the tree.
     */
    public void UpdateHT(Node node, String[] sample) {

        Node updatedNode = TraverseTree(node, sample);

        if (NeedForSplit(updatedNode)) {
            AttemptSplit(updatedNode);
            updatedNode = TraverseTree(node, sample);
            InsertNewSample(updatedNode, sample);
        }
        else { InsertNewSample(updatedNode, sample); }
    }

    /**
     * @param node   For a given node
     * @param sample The sample that has to be added in the given node
     *               <p>
     *               Jobs
     *               1. Add the value of each attribute to the corresponding list of the node
     *               2. Update the labelCounter given the label which comes with the sample
     *               3. Update the nmin - aka that you have added another sample to the node
     */
    public void InsertNewSample(Node node, String[] sample) {
        for (int i = 0; i < sample.length - 1; i++) {
            LinkedList<Double> list = getAttributeList(node, i);
            list.add(Double.parseDouble(sample[i]));
        }

        int label = Integer.parseInt(sample[sample.length - 1]);
        node.label_List.add(label);
        UpdateLabelCounters(node, label);
        UpdateNMin(node);
    }

    /**
     * @param node For a given node
     *             <p> It attempt to split the node </p>
     *             <p> First, find the best attributes to split the node </p>
     *             <p> Second,if the best attributes satisfy the condition(based on epsilon,tie_threshold) then became the splitting of node </p>
     */
    public void AttemptSplit(Node node) {
        double[][] G = FindTheBestAttribute(node); // informationGain,splitAttr,splitValue-row
        double G1 = G[0][0]; // highest information gain
        double G2 = G[0][1]; // second-highest information gain

        // Calculate epsilon
        double epsilon = CalculateHoeffdingBound(node);

        // Attempt split ///
        if ((((G1 - G2) > epsilon) || (G1 - G2) < node.tie_threshold) ) {
            double[] values = new double[3];
            for (int i = 0; i < 3; i++) { values[i] = G[i][0]; }
            SplitFunction(node, values);
            return;
        }

        // Reset information gain of node if not done the split
        node.information_gain = 0.0;

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
        double fraction = numerator /denominator;
        return Math.sqrt(fraction);
    }

    /**
     * @param node HoeffdingTree.Node which attempts to split
     * @return <p> Finds the best splitting attribute and splitting value for a given node based on Information Gain</p>
     */
    public double[][] FindTheBestAttribute(Node node) {

        double[][] multiples = new double[3][2]; //informationGain,splitAttr,splitValue(rows of array)
        for (int i = 0; i < node.m_features; i++) {

            LinkedList<Double> list = getAttributeList(node, i);
            double[] val = new double[list.size()];
            for (int j = 0; j < list.size(); j++) { val[j] = list.get(j); }

            // Calculate splitting value
            double[] splitValues = Utilities.Quartiles(val);

            // Calculate informationGain of node for each value and kept the max
            if (i == 0) {
                // GXa = the best attribute and GXb = the second best attribute
                double GXa = InformationGain(node, i, splitValues[0]);
                double GXb = 0.0;
                // Place the best attribute to the 0 position and the second best to the 1 position...
                if (GXa >= GXb) {
                    multiples[0][0] = GXa;
                    multiples[1][0] = i;
                    multiples[2][0] = splitValues[0];
                    multiples[0][1] = GXb;
                    multiples[1][1] = i + 1;
                    multiples[2][1] = splitValues[1];
                    // ... else to the opposite
                }
                else {
                    multiples[0][1] = GXa;
                    multiples[1][1] = i;
                    multiples[2][1] = splitValues[0];
                    multiples[0][0] = GXb;
                    multiples[1][0] = i + 1;
                    multiples[2][0] = splitValues[1];
                }
                // In case the current attribute is not the first (0) attribute
            }
            else {
                // Get the G of the first quartile...
                double tempG = InformationGain(node, i, splitValues[0]);
                // If the tempG is greater from the already best G, put the tempG in the 0 position and the previous G
                // to the second.. The previous second has to be overwritten and therefore discarded
                if (tempG > multiples[0][0]) {
                    multiples[0][1] = multiples[0][0];
                    multiples[1][1] = multiples[1][0];
                    multiples[2][1] = multiples[2][0];

                    multiples[0][0] = tempG;
                    multiples[1][0] = i;
                    multiples[2][0] = splitValues[0];
                }
                // if the tempG is less from the best attribute BUT greater than the second... we put the tempG to the
                // 1 position and keep the already best to the 0 position
                else if (tempG > multiples[0][1]) {
                    multiples[0][1] = tempG;
                    multiples[1][1] = i;
                    multiples[2][1] = splitValues[0];
                }
                // else do nothing
            }
            for (int j = 1; j < splitValues.length; j++) {
                double tempG = InformationGain(node, i, splitValues[j]);
                if (tempG > multiples[0][0]) {
                    multiples[0][1] = multiples[0][0];
                    multiples[1][1] = multiples[1][0];
                    multiples[2][1] = multiples[2][0];
                    multiples[0][0] = tempG;
                    multiples[1][0] = i;
                    multiples[2][0] = splitValues[j];
                }
                else if (tempG > multiples[0][1]) {
                    multiples[0][1] = tempG;
                    multiples[1][1] = i;
                    multiples[2][1] = splitValues[j];
                }
            }
        }
        return multiples;
    }

    /**
     * @param node For a given node
     * @param sample An array of values of features which will be use for traverse the tree
     * @return The label of node at which it ends
     */
    public Node TraverseTree(Node node, String[] sample) {

        if (node.leftNode == null && node.rightNode == null) { return node; }
        else {
            //Left child node
            if ( Double.parseDouble(sample[node.splitAttr]) <= node.splitValue ) {
                return TraverseTree(node.leftNode, sample);
            }
            //Right child node
            else { return TraverseTree(node.rightNode, sample); }
        }
    }

    /**
     * @param node For a given node
     * @param values Correspond to informationGain,splitAttribute,splitValue for node
     *               <p> It is responsible to split the node and create the left and right child-node </p>
     */
    public void SplitFunction(Node node, double[] values) {

        // Generate nodes
        Node child1 = new Node();
        Node child2 = new Node();

        // Initialize parent and child nodes
        child1.parentNode = node;
        child2.parentNode = node;
        node.leftNode = child1;
        node.rightNode = child2;

        // Initialize informationGain,splitAttribute,splitValue for node(parent-node)
        node.information_gain = values[0];
        node.splitAttr = (int) values[1];
        node.splitValue = values[2];

        // Initialize nmin,information_gain,label for children nodes
        child1.nmin = 0;
        child2.nmin = 0;
        child1.information_gain = 0.0;
        child2.information_gain = 0.0;
        child1.label = -1;
        child2.label = -1;

        // Initialize set of attributes for children nodes
        child1.setOfAttr = node.setOfAttr - 1;
        child2.setOfAttr = child1.setOfAttr;

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
        child1.InitializeHashMapSamplesAndLabelCounts(node.m_features);
        child2.InitializeHashMapSamplesAndLabelCounts(node.m_features);

        // Clear samples,labelCounts,label_List,setOfAttr on parent node
        node.labelCounts.clear();
        node.samples.clear();
        node.label_List.clear();
        node.setOfAttr = null;

    }

    /**
     * @param node For a given node
     * @param splitAttr Splitting attribute
     * @param splitValue Splitting value for splitAttr
     *                    <p> Calculate the Information Gain based on on splitAttr and splitValue </p>
     * @return Information Gain of node based on splitAttr and splitValue
     */
    public double InformationGain(Node node, int splitAttr, double splitValue) {

        // Calculate count for label 0,1
        int labelCount0Up = 0;
        int labelCount1Up = 0;
        int labelCount0Low = 0;
        int labelCount1Low = 0;
        for (int i = 0; i < node.samples.get(splitAttr).size(); i++) {

            if (Double.compare(node.getSamples().get(splitAttr).get(i), splitValue) >= 0) {
                if (node.label_List.get(i) == 0) { labelCount0Up++; }
                else { labelCount1Up++; }
            }
            else{
                if (node.label_List.get(i) == 0) { labelCount0Low++; }
                else { labelCount1Low++; }
            }
        }

        // Calculate entropy node
        double log0 = Math.log((double) node.labelCounts.get(0) / node.nmin) / Math.log(2);
        double log1 = Math.log((double) node.labelCounts.get(1) / node.nmin) / Math.log(2);
        if (node.labelCounts.get(0) == 0) { log0 = 0; }
        else if (node.labelCounts.get(1) == 0) { log1 = 0; }
        double entropyNode = (-1) * (((double) node.labelCounts.get(0) / node.nmin) * log0) + (-1) * (((double) node.labelCounts.get(1) / node.nmin) * log1);

        // Update information_gain node
        node.information_gain = entropyNode;

        // Calculate entropy based on splitAttr,for left and right node
        // Left HoeffdingTree.Node
        double entropyLeftNode;
        int totalCountLeftNode = labelCount0Up + labelCount1Up;
        log0 = Math.log((double) labelCount0Up / totalCountLeftNode) / Math.log(2);
        log1 = Math.log((double) labelCount1Up / totalCountLeftNode) / Math.log(2);
        if (labelCount0Up == 0) { log0 = 0; }
        else if (labelCount1Up == 0) { log1 = 0; }

        if (totalCountLeftNode == 0) { entropyLeftNode = 0; }
        else { entropyLeftNode = (-1) * (((double) labelCount0Up / totalCountLeftNode) * log0) + (-1) * (((double) labelCount1Up / totalCountLeftNode) * log1); }

        // Right HoeffdingTree.Node
        double entropyRightNode;
        int totalCountRightNode = labelCount0Low + labelCount1Low;
        log0 = Math.log((double) labelCount0Low / totalCountRightNode) / Math.log(2);
        log1 = Math.log((double) labelCount1Low / totalCountRightNode) / Math.log(2);
        if (labelCount0Low == 0) { log0 = 0; }
        else if (labelCount1Low == 0) { log1 = 0; }

        if (totalCountRightNode == 0) { entropyRightNode = 0; }
        else { entropyRightNode = (-1) * (((double) labelCount0Low / totalCountRightNode) * log0) + (-1) * (((double) labelCount1Low / totalCountRightNode) * log1); }

        //Calculate weighted average entropy of splitAttr
        double weightedEntropy = (((double) totalCountLeftNode / node.nmin) * entropyLeftNode) + (((double) totalCountRightNode / node.nmin) * entropyRightNode);

        return entropyNode - weightedEntropy;

    }

}
