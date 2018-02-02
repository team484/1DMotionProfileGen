import java.io.File;

import javax.swing.JFileChooser;


public class Profiler {
	public static void main(String[] args) {
		final JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fc.showOpenDialog(null);
		File testFile = fc.getSelectedFile();
		System.out.println(testFile.toString());
	}
}
