package ConceptDriftDetector;

public class ConceptDriftFactory {


    public static ConceptDriftDetector createConceptDriftDetector(int drift_detection_method_id){
        if(drift_detection_method_id == 1){
            return new DDM();
        }else if( drift_detection_method_id == 2){
            return new EDDM();
        }
        else{
            return new EDDM();
        }
    }


}
