package ConceptDriftDetector;

public class DDM implements ConceptDriftDetector {

    private int instances_seen; // The total number of instances seen
    private double pi;          // The error rate of the learning algorithm from the Bernoulli trials
    private double si;          //
    private double p_min;
    private double s_min;
    private final int warning_threshold;
    private final int drift_threshold;
    private boolean stable_phase;
    private boolean warning_signal;
    private boolean drift_signal;
    private boolean false_alarm_signal;



    public DDM() {
        System.out.println("Hi ConceptDriftDetector.DDM constructor");
        this.instances_seen = 1;
        this.pi = 0;
        this.si = 0;
        this.p_min = Double.MAX_VALUE;
        this.s_min = Double.MAX_VALUE;
        this.stable_phase = true;
        this.warning_signal = false;
        this.drift_signal = false;
        this.false_alarm_signal = false;
        this.warning_threshold = 2;
        this.drift_threshold = 3;
    }


//    public void ResetConceptDriftDetector() {
//        InitializeConceptDriftDetector();
//    }


    /* We have to see the error-rate as a Bernoulli Trial
     *  If the prediction is wrong then the trial is considered as failed
     *  In the other case, it is considered as success.
     * */
    public void FindConceptDrift(double error_rate) {
        System.out.println("Hi inside ConceptDriftDetector.DDM FindConceptDrift");
        instances_seen++;
        /* As mentioned, "Considering that the probability distribution is unchanged when the context is static
         * the proposition starts with n > 30 examples." */
        if (instances_seen < 30) {
            return;
        }
        /* There is no point on checking the condition of the concept drift in case of a valid drift detection.*/
        if (this.drift_signal) {
            return;
        }
        /* The Binomial distribution gives the general form of the probability for the random variable that represents
         * the number of errors in a sample of n examples. For each point i in the sequence, the error-rate is
         * the probability of observe False, pi, with standard deviation given by si = sqrt(pi(1 − pi)/i).
         * That's why pi = error_rate */
        pi = error_rate;
        si = Math.sqrt(pi * (1 - pi) / instances_seen);
        double sum = pi + si;
        /*"Every time a new example i is processed those values are updated
         *when pi + si is lower than pmin + smin." -p.5 from ConceptDriftDetector.DDM paper
         * */
        if (sum < p_min + s_min) {
            p_min = pi;
            s_min = si;
        }

        /* Check for warning drift. More precise check for warning only if there is no active drift.*/
        if (sum > p_min + warning_threshold * s_min) {
            // Warning Detection
            warning_signal = true;
            stable_phase = false;

            if (sum > p_min + drift_threshold * s_min) {
                // Drift Detection
                warning_signal = false;
                drift_signal = true;
                stable_phase = true;
            }
        } else if (sum < p_min + warning_threshold * s_min && warning_signal) {
            // False Alarm
            /* If the current sum is decreased and there is an active warning signal then warning is no longer active*/
            warning_signal = false;
            drift_signal = false;
            false_alarm_signal = true;
            stable_phase = true;

        }

    }

    public int getCurrentDriftStatus() {
        if (getWarningSignal()) {
            return WARNING;
        } else if (getDriftSignal()) {
            return DRIFT;
        } else if (getFalseAlarmSignal()) {
            return FALSE_ALARM;
        } else{
            return STABLE;
        }
    }

    public boolean getWarningSignal() {
        return this.warning_signal;
    }

    public boolean getDriftSignal() {
        return this.drift_signal;
    }

    public boolean getFalseAlarmSignal() {
        return this.false_alarm_signal;
    }
}


//            if (true_label != -1 ) {
//                previous_instance_id.update(instance_id);
//            }
//            if (true_label == -1 && purpose_id == -1) {
//                // Here we are inside WARNING.
//               System.out.println(previous_instance_id.value());
//                System.out.println("Inside Warning "+previous_instance_id.value());
//                this.warning_signal.update(true);
//                this.background_empty_state.update(true);
//                this.background_age_of_maturity.update(1);
//                /* Creating a new background Tree*/
//                HoeffdingTree.HoeffdingTree hoeffdingTree = new HoeffdingTree.HoeffdingTree();
//                hoeffdingTree.CreateHoeffdingTree(2, 3, 10, 0.96, 0.15, this.combination_function, hoeffding_tree_id);
//                background_hoeffdingTreeValueState.update(hoeffdingTree);
//            } else if (true_label == -1 && purpose_id == -2 && age_of_maturity.value() > age_of_maturity_input) {
//                // Here we are inside DRIFT.
//                System.out.println("Inside Drift "+previous_instance_id.value());
//                this.drift_signal.update(true);
//                this.warning_signal.update(false);
//                HoeffdingTree.HoeffdingTree background_tree = background_hoeffdingTreeValueState.value();
//                hoeffdingTreeValueState.update(background_tree);
//                background_hoeffdingTreeValueState.clear();
//
//            } else if (true_label == -1 && purpose_id == 0 && age_of_maturity.value() > age_of_maturity_input) {
//                // Here we are inside FALSE ALARM.
//                this.drift_signal.update(false);
//                this.false_alarm_signal.update(true);
//                this.warning_signal.update(false);
//                background_hoeffdingTreeValueState.clear();
//
//            }
//                    if (this.warning_signal.value() && this.background_age_of_maturity.value() == 1) {
//                        // WARNING SIGNAL WITH EMPTY BACKGROUND TREE
//                        /*The background tree is unable to follow the test and train notion. So only for the first input we only train the model*/
//                        this.background_age_of_maturity.update(2);
//                        HoeffdingTree.HoeffdingTree background_tree = background_hoeffdingTreeValueState.value();
//                        background_tree.UpdateHoeffdingTree(background_tree.root, features);
//                        background_hoeffdingTreeValueState.update(background_tree);
//                    } else if (this.warning_signal.value() && this.background_age_of_maturity.value() > 1) {
//                        // WARNING SIGNAL WITH BACKGROUND TREE CONTAINING ONLY ONE TRAINING INSTANCE. (AKA READY FOR TEST-THEN-TRAIN NOTION)
//                        HoeffdingTree.HoeffdingTree background_tree = background_hoeffdingTreeValueState.value();
//                        background_tree.TestHoeffdingTree(background_tree.root, features, purpose_id);
//                        background_tree.UpdateHoeffdingTree(background_tree.root, features);
//                  System.out.println("Training HT with id " + hoeffding_tree_id + " which has weight " + ht.getWeight() + " predicts " + background_prediction + " for the instance with id " + instance_id + " while the true label is " + true_label);
//                        background_hoeffdingTreeValueState.update(background_tree);
//                        // CRUCIAL PREDICTION = -1 AND TRUE LABEL = -1 AND PURPOSE ID = -1
//                      collector.collect(new Tuple6<>(instance_id, -1, -1, -1, background_tree.getWeight(), hoeffding_tree_id));
//                    }