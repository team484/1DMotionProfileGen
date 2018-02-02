import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * This class uses robot performance data (recorded to a CSV file) to generate
 * a motion profile path. To calculate a path of a given length, run generate
 */
public class CalculateProfile {
	ArrayList<State> forwardPath; //the raw robot acceleration data
	ArrayList<State> reversePath; //the raw robot deceleration data

	/**
	 * Parses CSV file for robot performance data
	 * @param file - CSV file
	 */
	public CalculateProfile(File file) {
		//Create reader
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		String line;
		try {
			//Iterate through columns of CSV file
			while ((line = reader.readLine()) != null) {
				String[] rows = line.split(","); //[output, pos, rate, time]
				if (rows.length > 3) { 
					double output = Double.parseDouble(rows[0]);
					double pos = Double.parseDouble(rows[1]);
					double rate = Double.parseDouble(rows[2]);
					double time = Double.parseDouble(rows[3]);

					//If row refers to acceleration data, add it to the
					//forwardPath, do the reverse for deceleration data
					if (output > 0) {
						forwardPath.add(new State(output,pos,rate,time));
					} else if (output < 0) {
						reversePath.add(new State(output,pos,rate,time));
					}
				}
			}
			Collections.reverse(reversePath);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

	}

	ArrayList<State> forwardPathProgress; //Calculated acceleration region of path
	ArrayList<State> reversePathProgress; //Calculated deceleration region of path
	Iterator<State> forwardIterator; //iterator for forwardPath
	Iterator<State> reverseIterator; //iterator for reversePath
	double frontEnd, tailEnd; //Current position of forward/reversePathProgres

	/**
	 * Gets the next value in the iterator and adds it to the path
	 * @param pathProgress - forward or reverse path progress
	 * @param iter - the iterator for the path
	 * @return true if could generate new value for path
	 */
	private boolean getNext(ArrayList<State> pathProgress, Iterator<State> iter) {
		if (frontEnd >= tailEnd) return false; //if the accel and decel paths have already crossed
		if (iter.hasNext()) {
			pathProgress.add(iter.next());
		} else if (pathProgress.size() > 1){
			//If there is no more performance data on robot, guess the next state
			State lastState = pathProgress.get(pathProgress.size() - 1);
			State preLastState = pathProgress.get(pathProgress.size() - 2);
			if (lastState.output > 0) {
				State nextState = new State(
						lastState.output, 
						lastState.pos + lastState.rate,
						lastState.rate,
						lastState.time*2 - preLastState.time);
			} else {
				return false;
			}
		} else {
			return false;
		}
		//Update the location of the paths
		frontEnd = getLast(forwardPathProgress).pos;
		tailEnd = getLast(reversePathProgress).pos;
		return true;
	}

	/**
	 * Just gets the last value in a ArrayList<State>.
	 * Yes, I know this could just take in a ArrayList<> instead.
	 * @param array - the array
	 * @return the last value in the array
	 */
	private State getLast(ArrayList<State> array) {
		if (array.size() > 0) {
			return array.get(array.size() - 1);
		}
		return null;
	}

	/**
	 * Generates a motion profile path for a given path length (distance).
	 * The process works by generating an acceleration path going forward and
	 * a deceleration path going backwards until the 2 paths meet. This results
	 * in a combined path where the robot is accelerating continuously until the
	 * moment when it needs to brake.
	 * @param distance - the distance of the path
	 * @return the generated path
	 */
	public ArrayList<State> generate(double distance) {
		//Preparing variables
		forwardPathProgress = new ArrayList<State>();
		reversePathProgress = new ArrayList<State>();
		forwardIterator = forwardPath.iterator();
		reverseIterator = reversePath.iterator();
		frontEnd = 0;
		tailEnd = distance;
		ArrayList<State> finalPath = null;
		boolean stopForDecel = false;
		while(frontEnd < tailEnd) {
			//Get next forward state
			if (!getNext(forwardPathProgress, forwardIterator)) {
				break;
			}

			//Get next reverse state
			if (!getNext(reversePathProgress, reverseIterator)) {
				stopForDecel = true;
				break;
			}

			//Ensure that the slower accelerating direction gets caught up to the faster one
			if (getLast(forwardPathProgress).rate > getLast(reversePathProgress).rate) {
				while(getLast(forwardPathProgress).rate > getLast(reversePathProgress).rate) {
					if (!getNext(reversePathProgress, reverseIterator)) {
						stopForDecel = true;
						break;
					}
				}
			} else if (getLast(forwardPathProgress).rate < getLast(reversePathProgress).rate) {
				while(getLast(forwardPathProgress).rate < getLast(reversePathProgress).rate) {
					if (!getNext(forwardPathProgress, forwardIterator)) {
						break;
					}
				}
			}

		}

		//runs if the previous code stopped because there was no more deceleration data
		while(frontEnd < tailEnd && stopForDecel) {
			//Add points to the accel end until it reaches the decel point
			if (!getNext(forwardPathProgress, forwardIterator)) {
				break;
			}
		}

		//If there is overlap in the path components
		if (frontEnd > tailEnd) {
			//Removes the overlap of the two paths by deleting the last value of
			//the slower accelerating path
			if (forwardPathProgress.size() > reversePathProgress.size()) {
				forwardPathProgress.remove(forwardPathProgress.size() - 1);
			} else if (forwardPathProgress.size() < reversePathProgress.size()) {
				reversePathProgress.remove(reversePathProgress.size() - 1);
			}
		}

		finalPath = new ArrayList<State>();
		//Add acceleration component to final motion profile path
		for (State state : forwardPathProgress) {
			finalPath.add(state);
		}

		//Add deceleration component to final motion profile path
		double reversePathStartTime = getLast(reversePathProgress).time;
		double forwardPathEndTime = getLast(forwardPathProgress).time;
		for (int i = reversePathProgress.size() - 1; i >= 0; i--) {
			State state = reversePathProgress.get(i);
			state.output += forwardPathEndTime - reversePathStartTime;
			finalPath.add(state);
		}

		return finalPath;
	}


	/**
	 * Struct to store robot state (pose) data
	 */
	public class State {
		public double pos,time,rate,output;

		/**
		 * Constructor
		 * @param output - motor output
		 * @param pos - displacement along path
		 * @param rate - robot velocity
		 * @param time - time since start of path
		 */
		public State(double output, double pos, double rate, double time) {
			this.output = output;
			this.pos = pos;
			this.rate = rate;
			this.time = time;
		}
	}
}