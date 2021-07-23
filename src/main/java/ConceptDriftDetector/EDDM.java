package ConceptDriftDetector;

public class EDDM implements ConceptDriftDetector{


    public EDDM(){
        // System.out.println("Hi inside Constructor EDDM");
    }
    @Override
    public void FindConceptDrift(double error_rate) {
        // System.out.println("Hi inside FindConceptDrift EDDM!");

    }

    @Override
    public int getCurrentDriftStatus() {
        return 0;
    }

    @Override
    public int getSignal() {
        return 0;
    }

    @Override
    public void updateCurrentDriftStatus() {

    }

    @Override
    public void ResetConceptDrift() {

    }
}
