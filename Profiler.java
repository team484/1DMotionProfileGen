import java.io.File;
import java.util.ArrayList;

import javax.swing.JFileChooser;



public class Profiler {
	public static void main(String[] args) {
		final JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fc.showOpenDialog(null);
		File testFile = fc.getSelectedFile();
		System.out.println(testFile.toString());
		CalculateProfile profCalc = new CalculateProfile(testFile);
		ArrayList<CalculateProfile.State> path = profCalc.generate(5);
		for (CalculateProfile.State state : path) {
			System.out.println(state.output + ", " + state.pos + ", " + state.rate + ", " + state.time);
		}
	}
}
