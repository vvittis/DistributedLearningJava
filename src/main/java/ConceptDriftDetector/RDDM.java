package ConceptDriftDetector;

public class RDDM implements ConceptDriftDetector
{

    private int instances_seen; // The total number of instances seen
    private double pi;          // The error rate of the learning algorithm from the Bernoulli trials
    private double si;          //
    private double p_min;
    private double s_min;
    private final int warning_threshold;
    private final int drift_threshold;
    private boolean warning_signal;
    private boolean warning_phase;
    private boolean drift_signal;
    private boolean drift_phase;
    private boolean false_alarm_signal;
    private boolean stable_phase;



    public RDDM()
    {
        System.out.println("Hi ConceptDriftDetector.VDDM constructor");
        this.instances_seen = 1;
        this.pi = 0;
        this.si = 0;
        this.p_min = Double.MAX_VALUE;
        this.s_min = Double.MAX_VALUE;
        this.stable_phase  = true;
        this.warning_signal = false;
        this.warning_phase = false;
        this.drift_phase = false;
        this.drift_signal = false;
        this.false_alarm_signal = false;
        this.warning_threshold = 2;
        this.drift_threshold = 3;
    }


    //    public void ResetConceptDriftDetector() {
    //        InitializeConceptDriftDetector();
    //    }

    public void ResetConceptDrift()
    {
        this.instances_seen = 1;
        this.pi = 0;
        this.si = 0;
        this.p_min = Double.MAX_VALUE;
        this.s_min = Double.MAX_VALUE;
        this.stable_phase  = true;
        this.warning_signal = false;
        this.warning_phase = false;
        this.drift_signal = false;
        this.drift_phase = false;
        this.false_alarm_signal = false;
    }
    /* We have to see the error-rate as a Bernoulli Trial
     *  If the prediction is wrong then the trial is considered as failed
     *  In the other case, it is considered as success.
     * */
    public void FindConceptDrift(double error_rate)
    {
//        System.out.println("Hi inside ConceptDriftDetector.DDM FindConceptDrift");
        instances_seen++;
        /* As mentioned, "Considering that the probability distribution is unchanged when the context is static
         * the proposition starts with n > 30 examples." */
        if (instances_seen < 30)
        {
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
        if (sum < p_min + s_min)
        {
            p_min = pi;
            s_min = si;
        }

        /* Check for warning drift. More precise check for warning only if there is no active drift.*/
        if (sum > p_min + warning_threshold * s_min)
        {
            // Warning Detection
            warning_signal     = true;
            drift_signal       = false;
            false_alarm_signal = false;

            if (sum > p_min + drift_threshold * s_min )
            {
                // Drift Detection

                warning_signal     = false;
                drift_signal       = true;
            }
        }
        else if (sum < p_min + warning_threshold * s_min && warning_phase)
        {
            // False Alarm
            /* If the current sum is decreased and there is an active warning signal then warning is no longer active*/
            warning_signal     = false;
            drift_signal       = false;
            false_alarm_signal = true;

        }

    }

    public int getSignal()
    {
        if (getWarningSignal())
        {
            return WARNING;
        }
        else if (getDriftSignal())
        {
            return DRIFT;
        }
        else if (getFalseAlarmSignal())
        {
            return FALSE_ALARM;
        }
        else
        {
            return STABLE;
        }

    }

    @Override
    public void updateCurrentDriftStatus() {
        int current_state = getCurrentDriftStatus();
//        System.out.println("Current state: "+ current_state+" and signal: "+ getSignal());
        if(current_state == 0 && getWarningSignal()){
            this.warning_phase = true;
            this.stable_phase = false;
        }
        else if( current_state == 1 && getDriftSignal()){
            this.drift_phase = true;
            this.stable_phase = false;
            this.warning_phase = false;
        }
        else if( (current_state == 1 || current_state == 2) && getFalseAlarmSignal()){
            this.stable_phase = true;
            this.drift_phase = false;
            this.warning_phase = false;
        }

    }

    public int getCurrentDriftStatus()
    {
        if(warning_phase)
        {
            return WARNING;      // 1
        }
        else if (drift_phase){
            return  DRIFT;       // 2
        }
        else if(stable_phase)
        {
            return STABLE;       // 0
        }
        else{
            return -100;
        }
    }


    public boolean getWarningSignal()
    {
        return this.warning_signal;
    }

    public boolean getDriftSignal()
    {
        return this.drift_signal;
    }

    public boolean getFalseAlarmSignal()
    {
        return this.false_alarm_signal;
    }
}
