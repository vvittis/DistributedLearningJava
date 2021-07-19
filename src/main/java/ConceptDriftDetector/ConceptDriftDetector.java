package ConceptDriftDetector;

import java.io.Serializable;

public interface ConceptDriftDetector extends Serializable {
    int STABLE = 0;
    int WARNING = 1;
    int DRIFT = 2;
    int FALSE_ALARM = -1;

    void FindConceptDrift(double error_rate);

    int getCurrentDriftStatus();


}
