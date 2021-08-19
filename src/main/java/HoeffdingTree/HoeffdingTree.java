package HoeffdingTree;


import Utilities.Utilities;

import java.io.Serializable;

public class HoeffdingTree implements Serializable {

    //implements Serializable
    public static final long serialVersionUID = 42L;

    public double instances_seen;
    public double correctly_classified;
    public double weight;
    public int tree_phase;
    public int combination_function;
    public int hoeffding_tree_id;
    public int[] m_features; // list of labels corresponding to samples of node
    public Node root = new Node();

    public HoeffdingTree() {}


    /**
     * @param m_features              random subset of features
     * @param Max                     range aka how many features I have to select from input's feature
     * @param max_examples_seen       the number of examples between checks for growth(n_min)
     * @param delta                   one minus the desired probability of choosing the correct feature at any given node
     * @param tie_threshold           tie threshold between splitting values of selected features for split
     * @param combination_function_id majority voting = 1 , weighted voting = 2, des-p = 3 , knora-u = 4
     *                                <p> Create the Hoeffding tree for given parameters </p>
     */

    public void NEW_CreateHoeffdingTree
            (int m_features, int Max, int max_examples_seen, double delta, double tie_threshold, int combination_function_id, int hoeffding_tree_id, int background_tree_indicator) {
        root.CreateHT(m_features, max_examples_seen, delta, tie_threshold);
        instances_seen = 0.0;
        correctly_classified = 0.0;
        weight = 0.0;
        this.hoeffding_tree_id = hoeffding_tree_id;
        combination_function = combination_function_id;
        initialize_m_features(m_features, Max - 1, hoeffding_tree_id);
        this.tree_phase = background_tree_indicator;
    }

    /**
     * @param node  For a given node(root)
     * @param input An array of values of attributes
     *              <p> It is responsible to update the tree </p>
     */
    public void UpdateHoeffdingTree(Node node, String[] input, int weight) {
        String[] selectedInput = this.select_m_features(input);
        node.UpdateHT(node, selectedInput, weight);
    }

    /**
     * @param node     For a given node
     * @param input    An array of values of features which will be use for testing
     * @param keyTuple Use for distinction between predicted,testing and training tuples aka purposeID
     *                 <p> For testing and predicted tuples simply does the test and return the label</p>
     *                 <p> For training examples does the test and update the weight of tree </p>
     *                 <p> purposeId=-5  correspond to testing examples </p>
     *                 <p> purposeId=-10  correspond to predicted examples </p>
     *                 <p> purposeId=5  correspond to training examples </p>
     */
    public int TestHoeffdingTree(Node node, String[] input, int keyTuple) {

        int predicted_value = 0;
        String[] selectedInput = this.select_m_features(input);

        if (this.instances_seen == 1) {
            // Only for the first training instance
            setWeight();
        }
        // Weighted Voting
        this.instances_seen++;
        // Training instance
        predicted_value = node.TestHT(node, selectedInput);
        if (predicted_value == Integer.parseInt(selectedInput[selectedInput.length - 1])) { // predicted value equal to the true label
            this.correctly_classified++;
        } else if (predicted_value == -1) {
            // that means that at some point there was a split which had created two new children nodes
            // and at some other point a new instance traversed to that empty node.
            this.instances_seen--;
        }
        this.setWeight(this.correctly_classified, this.instances_seen);

        return predicted_value;
    }


    /**
     * <p>This function clears all the Hoeffding Tree.</p>
     */
    public void RemoveHoeffdingTree() {
        this.root.RemoveHT(this.root);
        this.instances_seen = 0;
        this.correctly_classified = 0;
        this.weight = 0;
        this.m_features = null;
        System.gc();
    }

    public void setWeight() {
        this.weight = 0;
    }

    public void setWeight(double correctly_classified, double instances_seen) {
        this.weight = correctly_classified / instances_seen;
    }

    public double getAccuracy() {
        return this.weight;
    }

    public double getErrorRate() {return 1 - this.weight;}

    /**
     * @param m   how many features I want the Hoeffding Tree to have
     * @param Max What is the range aka how many features I have to select from input's feature
     */
    public void initialize_m_features(int m, int Max, int htid) {
        this.m_features = Utilities.ReservoirSampling(m, Max);
//        this.m_features = Utilities.Utilities.SendAllPossibleCombinations(htid);
    }

    /**
     * @param input_string An array of values of features
     *                     <p> Return the selected features of input_string </p>
     */
    public String[] select_m_features(String[] input_string) {

        String[] output_string = new String[this.m_features.length + 1];
        for (int i = 0; i < this.m_features.length; i++) {
            output_string[i] = input_string[this.m_features[i]];
        }

        output_string[this.m_features.length] = input_string[input_string.length - 1];
        return output_string;
    }

    /**
     * <p> Print the selected features of tree </p>
     */
    public void print_m_features() {
        System.out.print("HT " + hoeffding_tree_id + " Selected Features: ");
        for (int m_feature : this.m_features) {
            System.out.print(m_feature + " ");
        }
        System.out.println();
    }

}


