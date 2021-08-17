package Utilities;

/**
 * DefaultValues.java
 * @author Vasilis Vittis 2021 Technical University of Crete.
 *
 * @implNote number of hoeffding trees: A variable can take almost infinite value.
 *                                      It is dataset dependent because there is finite number of possible combinations
 *                                      of features destined to the trees. ex. Selecting 2 features out of 3 produces
 *                                      only 3 possible. There is no point of having more than 3 trees.
 *           combination function     : Combination function can take 3 values.
 *                                      1 => Majority Voting
 *                                      2 => Weighted Voting with threshold-θ
 *                                      3 => Weighted Voting with top-k
 *           weighted_voting_parameter: In case of Weighted Voting
 *                                      If Weighted Voting with threshold-θ then => ε (0,1)
 *                                      If Weighted Voting with top-k            => ε (1,number_of_hoeffding_trees).
 *           age_of_maturity          : Indicates after how many instances seen,
 *                                      we will start accepting testing instances.
 *                                      Aka how long our model is in a stable phase.
 *           drift_detection_method_id: 0 => No Drift Detector
 *                                      1 => Reactive Drift Detection Method (RDDM)
 */
public class DefaultValues {

    int number_of_hoeffding_trees = 1;
    int combination_function = 3;
    double weighted_voting_parameter = 1;
    int age_of_maturity = 1000;
    int drift_detection_method_id = 1;

    public DefaultValues(){};

    public int getNumber_of_hoeffding_trees() {
        return number_of_hoeffding_trees;
    }

    public int getCombination_function() {
        return combination_function;
    }

    public double getWeighted_voting_parameter() {
        return weighted_voting_parameter;
    }

    public int getAge_of_maturity() {
        return age_of_maturity;
    }

    public int getDrift_detection_method_id() {
        return drift_detection_method_id;
    }

}
