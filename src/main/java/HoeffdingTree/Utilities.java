package HoeffdingTree;

import java.util.Arrays;
import java.util.Random;


public class Utilities {

    // Variables for method Calculate_metrics
    /* public double true_positive = 0.0;   // true_label = fraud (1) and predicted_label = fraud (1)
    public double true_negative = 0.0;   // true_label = non-fraud (0) and predicted_label = non-fraud (0)
    public double false_positive = 0.0;  // true_label = non-fraud (0) and predicted_label = fraud (1)
    public double false_negative = 0.0;  // true_label = fraud (1) and predicted_label = non-fraud (0)*/

    /**
     * @param val Array of double values
     * @return Quartiles of array val
     */
    public static double[] Quartiles(double[] val) {
        double[] ans = new double[3];

        for (int quartileType = 1; quartileType < 4; quartileType++) {
            double length = val.length + 1;
            double quartile;
            double newArraySize = (length * ((double) (quartileType) * 25 / 100)) - 1;
            Arrays.sort(val);
            if (newArraySize % 1 == 0) {
                quartile = val[(int) (newArraySize)];
            } else {
                int newArraySize1 = (int) (newArraySize);
                quartile = (val[newArraySize1] + val[newArraySize1 + 1]) / 2;
            }
            ans[quartileType - 1] = quartile;
        }
        return ans;
    }

    // Calculate_metrics
    /* public void calculate_metrics(int true_label, int predicted_label) {
        System.out.println("True Label " + true_label + " Predicted_label " + predicted_label);

        if (true_label == 1 && predicted_label == 1) { this.true_positive++; }
        else if (true_label == 0 && predicted_label == 0) { this.true_negative++; }
        else if (true_label == 0 && predicted_label == 1) { this.false_positive++; }
        else { this.false_negative++; }
        System.out.println("TP: " + this.true_positive +" TN: " + this.true_negative +" FP: " + this.false_positive +" FN: " + this.false_positive);

        // Accuracy    = (TP+TN)/ (TP + TN + FP + FN)
        double accuracy = (this.true_positive + this.true_negative) / (this.true_positive + this.true_negative + this.false_negative + this.false_positive);
        System.out.print("Accuracy: " + accuracy);

        // Sensitivity = TP / (TP + FN)
        double sensitivity = this.true_positive / (this.true_positive + this.false_negative);
        System.out.print(" Sensitivity: " + sensitivity);

        // Specificity = TN / (TN + FP)
        double specificity = this.true_negative / (this.true_negative + this.false_positive);
        System.out.print(" Specificity: " + specificity);

        // Precision   = TP / (TP + FP)
        double precision = this.true_positive / (this.true_positive + this.false_positive);
        System.out.print(" Precision: " + precision);

        // F1-score    = 2*(Precision * Sensitivity)/(Precision + Sensitivity)
        double f1_score = 2 * (precision * sensitivity) / (precision + sensitivity);
        System.out.print(" F1 score: " + f1_score);
        System.out.println();
    }*/

    /**
     * @param m   = how many features I want the Hoeffding Tree to have
     * @param Max = What is the range aka how many features I have to select from
     */
    public static int[] ReservoirSampling(int m, int Max) {
        Random rand = new Random();
        int[] ans = new int[m];
        for (int k = 0; k < m; ++k)
            ans[k] = k;
        for (int k = m; k <= Max; ++k) {
            int v = rand.nextInt(k + 1);
            if (v < m) {
                ans[v] = k;
            }
        }
        Arrays.sort(ans);
        return ans;
    }

}
