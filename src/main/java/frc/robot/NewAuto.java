package frc.robot;

import java.io.Console;
import java.io.IOException;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj.controller.RamseteController;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.geometry.Rotation2d;
import edu.wpi.first.wpilibj.geometry.Translation2d;
import edu.wpi.first.wpilibj.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveKinematics;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveOdometry;
import edu.wpi.first.wpilibj.kinematics.DifferentialDriveWheelSpeeds;
import edu.wpi.first.wpilibj.trajectory.Trajectory;
import edu.wpi.first.wpilibj.trajectory.TrajectoryConfig;
import edu.wpi.first.wpilibj.trajectory.TrajectoryGenerator;
import edu.wpi.first.wpilibj.trajectory.TrajectoryUtil;
import edu.wpi.first.wpilibj.trajectory.constraint.CentripetalAccelerationConstraint;
import edu.wpi.first.wpilibj.trajectory.constraint.DifferentialDriveKinematicsConstraint;
import edu.wpi.first.wpilibj.trajectory.constraint.DifferentialDriveVoltageConstraint;
import edu.wpi.first.wpilibj.util.Units;

/**
 * Add your docs here.
 */
public class NewAuto {
	Robot robot;
	// RamseteController ramsete = new RamseteController(1.6, 0.7);
	RamseteController ramsete = new RamseteController();
	DifferentialDriveKinematics driveKinematics = new DifferentialDriveKinematics(-Units.inchesToMeters(25.5));
	public DifferentialDriveOdometry driveOdometry;
	TrajectoryConfig config = new TrajectoryConfig(Units.feetToMeters(4.5), Units.feetToMeters(3));
	public double autoStartTime = 0;
	public Trajectory trajectory = null;
	double RPM_TO_MPS = (9.0 / 84.0) * Units.inchesToMeters(6) * Math.PI;
	DifferentialDriveWheelSpeeds prevSpeeds = new DifferentialDriveWheelSpeeds(0, 0);
	double prevTime = 0;
	double curTime = 0;
	int state = 0;
	int bouncePathState;
	double timeStampLoop2 = 0;
	double shootCountdown = 0;
	double alignTime = 0;
	double intakeTime = 0;
	Pose2d GoalParams;
	double GoalXTolerance = 0.2;
	double GoalYTolerance = 0.2;
	double GoalAngleTolerance = 90;
	double bouncePathStart = 0;
	boolean pathsDone = false;

	public NewAuto(Robot robot) {
		this.robot = robot;
	}

	// public void pathweaverLoop(){
	// hi
	// }

	/*
	 * public void generateTrajectory() { configConstraints(); var startWaypoint =
	 * new Pose2d(0, 0, Rotation2d.fromDegrees(0)); var endWaypoint = new
	 * Pose2d(Units.feetToMeters(22), 0.0, Rotation2d.fromDegrees(0)); var
	 * interiorWaypoints = List.of( new Translation2d(Units.feetToMeters(5),
	 * Units.feetToMeters(5)), new Translation2d(Units.feetToMeters(10),
	 * Units.feetToMeters(-5)), new Translation2d(Units.feetToMeters(12.5),
	 * Units.feetToMeters(5)), new Translation2d(Units.feetToMeters(16),
	 * Units.feetToMeters(-5)), new Translation2d(Units.feetToMeters(18),
	 * Units.feetToMeters(5)) ); trajectory =
	 * TrajectoryGenerator.generateTrajectory(startWaypoint, interiorWaypoints,
	 * endWaypoint, config); }
	 */

	// Copy of the generateTrajectory function, but using parameters to allow for
	// easier looping for bouncepath
	/**
	 * generate the parameter with interior points without angle measurements
	 * 
	 * @param reversed if the drive direction should be reversed
	 * @param points format: {{startX, startY, startAngle}, {int1X, int2Y}, ..., {endX, endY, endAngle}}
	 */
	public void generateTrajectoryWithParameters(boolean reversed, double[][] points) {

		//gets its first and last parameters to make the start and end points with directions
		double startX = points[0][0];
		double startY = points[0][1];
		double startAngle = points[0][2];
		double endX = points[(points.length - 1)][0];
		double endY = points[(points.length - 1)][1];
		double endAngle = points[(points.length - 1)][2];
		configConstraints();
		config.setReversed(reversed);
		//we have to use pose2d's because we need to have a starting and ending angle
		var startWaypoint = new Pose2d(Units.feetToMeters(startX), Units.feetToMeters(startY),
				Rotation2d.fromDegrees(startAngle));
		var endWaypoint = new Pose2d(Units.feetToMeters(endX), Units.feetToMeters(endY),
				Rotation2d.fromDegrees(endAngle));
		//creates empty list to allow the loop to add infinite points
		List<Translation2d> interiorWaypoints = new ArrayList<>();
		if (points.length > 2) {
			for (int i = 1; i < (points.length - 1); i++) {
				double iX = points[i][0];
				double iY = points[i][1];
				interiorWaypoints.add(new Translation2d(Units.feetToMeters(iX), Units.feetToMeters(iY)));
			}
		} else {
			//make sure that someone does not just have a starting point and an ending point
			System.out.println("please have at least 3 points");
			return;
		}
		//generate the trajectory and set as a global variable
		trajectory = TrajectoryGenerator.generateTrajectory(startWaypoint, interiorWaypoints, endWaypoint, config);
		//reset time after path is generated to make sure that the path is generated so it does not speed to the next point and freak out
		autoStartTime = Timer.getFPGATimestamp();
		GoalParams = new Pose2d(Units.feetToMeters(endX), Units.feetToMeters(endY), Rotation2d.fromDegrees(endAngle));
	}

	//does the same thing as above but the internal points have to have an angle with it
	/**
	 * generate the parameter with interior points without angle measurements
	 * 
	 * @param reversed if the drive direction should be reversed
	 * @param points format: {{startX, startY, startAngle}, {int1X, int2Y, int1Angle}, ..., {endX, endY, endAngle}}
	 */
	public void generateTrajectoryWithQuinticParameters(boolean reversed, double[][] points) {
		configConstraints();
		config.setReversed(reversed);
		// it is pose2d instead of translation2d because pose2d includes angles
		List<Pose2d> interiorWaypoints = new ArrayList<>();
		if (points.length > 1) {
			for (int i = 0; i < (points.length); i++) {
				double iX = points[i][0];
				double iY = points[i][1];
				double iAngle = points[i][2];
				interiorWaypoints.add(new Pose2d(Units.feetToMeters(iX), Units.feetToMeters(iY), Rotation2d.fromDegrees(iAngle)));
			}
		} else {
			System.out.println("please have at least 2 points");
			return;
		}
		trajectory = TrajectoryGenerator.generateTrajectory(interiorWaypoints, config);
		autoStartTime = Timer.getFPGATimestamp();
		GoalParams = new Pose2d(Units.feetToMeters(points[(points.length-1)][0]), Units.feetToMeters(points[(points.length-1)][1]), Rotation2d.fromDegrees(points[(points.length-1)][2]));
	}

	/**
	 * used to have the params for limits with the ramsette auto
	 */
	public void configConstraints() {
		DifferentialDriveKinematicsConstraint driveContraint = new DifferentialDriveKinematicsConstraint(
				driveKinematics, 4);
		//commented this out because of the error of the min acceleration being greater than its max acceleration and this fixed it
		// DifferentialDriveVoltageConstraint voltConstraint = new
		// DifferentialDriveVoltageConstraint(
		// robot.motor.driveFeedForward, driveKinematics, 10);

		//set its max turning acceleration so it does not freak out and overshoot as much
		CentripetalAccelerationConstraint maxTurnAcceleration = new CentripetalAccelerationConstraint(0.5);
		config.addConstraint(driveContraint);
		// config.addConstraint(voltConstraint);
		config.addConstraint(maxTurnAcceleration);
	}

	/** 
	* used to calculate how the robot should move using ramsette
	*/
	public void loop() {
		double time = Timer.getFPGATimestamp() - autoStartTime;

		double rightPosition = robot.motor.rightEncoder.getPosition();
		double leftPosition = robot.motor.leftEncoder.getPosition();
		driveOdometry.update(Rotation2d.fromDegrees(robot.motor.ahrs.getFusedHeading() * -1), leftPosition,
				rightPosition);

		Trajectory.State goal = trajectory.sample(time);
		ChassisSpeeds speeds = ramsete.calculate(driveOdometry.getPoseMeters(), goal);
		DifferentialDriveWheelSpeeds wheelSpeeds = driveKinematics.toWheelSpeeds(speeds);
		double dt = time - prevTime;
		double leftVelocity = wheelSpeeds.leftMetersPerSecond;
		double rightVelocity = wheelSpeeds.rightMetersPerSecond;
		double rightAcceleration = (rightVelocity - prevSpeeds.rightMetersPerSecond) / dt;
		double leftAcceleration = (leftVelocity - prevSpeeds.rightMetersPerSecond) / dt;
		robot.motor.setRightVelocity(rightVelocity, rightAcceleration);
		robot.motor.setLeftVelocity(leftVelocity, leftAcceleration);

		prevTime = time;
		prevSpeeds = wheelSpeeds;
		// System.out.println("goal: " + goal.poseMeters);
		// System.out.println("real: " + driveOdometry.getPoseMeters());
		// if(driveOdometry.getPoseMeters() == goal.poseMeters){
		// robot.pneumatics.CPMSolenoid.set(Value.kForward);
		// }
	}

	/** 
	 * function to generate the new trajectories based on the global variable, bouncePathState
	 */
	private void newPath() {
		//from the front bumper to the navX in inches divided by 12 to get feet
		double frontToNavX = (18/12);
		// last point is the position so the navX hits the point
		double[][] pointsa = { { 0, 0, 0 }, { 5, 0 }, { 7.5 - frontToNavX, 5, 90 } };
		double[][] pointsb = { pointsa[(pointsa.length - 1)], { 7.5 - frontToNavX, 0 }, { 10.75 - frontToNavX, -5}, { 15 - frontToNavX, -5}, { 15 - frontToNavX, 5, -90} };
		double[][] pointsc = { pointsb[(pointsb.length - 1)], { 15 - frontToNavX, -5 }, { 22 - frontToNavX, -5 }, { 22.5 - frontToNavX, 5, 90} };
		double[][] pointsd = { pointsc[(pointsc.length - 1)], { 22.5 - frontToNavX, -0.5 }, { 27.5 - frontToNavX, -1, -180 } };
		//create a new path from the above parameters (all measurements in feet and angles)
		switch (bouncePathState) {
			case 0:
				generateTrajectoryWithParameters(false, pointsa);
				break;
			case 1:
				generateTrajectoryWithParameters(true, pointsb);
				break;
			case 2:
				generateTrajectoryWithParameters(false, pointsc);
				break;
			case 3:
				generateTrajectoryWithParameters(true, pointsd);
				break;
			default:
				//tells the code it is done so it does not increment the path to infinity (and save resources in doing so)
				pathsDone = true;
		}
		//put all of the goal parameters onto the network tables
		robot.tables.driveGoalPosX.setDouble(GoalParams.getX());
		robot.tables.driveGoalPosY.setDouble(GoalParams.getY());
		robot.tables.driveGoalPosAngle.setDouble(GoalParams.getRotation().getDegrees());
		robot.tables.drivePathPos.setNumber(bouncePathState);
	}

	/**
	 * done to prevent the creating of a path every loop and save the CPU cycles
	 */
	private void incrementPath() {
		if(!pathsDone){
			System.out.println("bouncePathState pre ++: " + bouncePathState);
			bouncePathState++;
			robot.motor.setRightVelocity(0, 0);
			robot.motor.setLeftVelocity(0, 0);
			newPath();
			// robot.intake.toggle();
			System.out.println("bouncePathState post ++: " + bouncePathState);
		}
	}

	// Bounce Path loop
	public void bouncePath() {
		if (trajectory == null) {
			bouncePathState = 0;
			newPath();
			driveOdometry.resetPosition(trajectory.getInitialPose(),
					Rotation2d.fromDegrees(-1 * robot.motor.ahrs.getFusedHeading()));
		}

		// Test whether the robot is within the tolerance of the end point
		boolean outXTolerance = (((driveOdometry.getPoseMeters().getX()) > GoalParams.getX() + GoalXTolerance)
				|| ((driveOdometry.getPoseMeters().getX()) < GoalParams.getX() - GoalXTolerance));
		boolean outYTolerance = (((driveOdometry.getPoseMeters().getY()) > GoalParams.getY() + GoalYTolerance)
				|| ((driveOdometry.getPoseMeters().getY()) < GoalParams.getY() - GoalYTolerance));
		boolean outAngleTolerance = (((driveOdometry.getPoseMeters().getRotation().getDegrees()) > GoalParams
				.getRotation().getDegrees() + GoalAngleTolerance)
				|| ((driveOdometry.getPoseMeters().getRotation().getDegrees()) < GoalParams.getRotation().getDegrees()
						- GoalAngleTolerance));

		// if within tolerance increment path number the loop otherwise do drive loop
		if ((!outXTolerance && !outYTolerance && !outAngleTolerance)) {
			incrementPath();
		} else {
			loop();
		}
		//put its current drive parameters onto the network tables
		robot.tables.drivePosX.setDouble(driveOdometry.getPoseMeters().getX());
		robot.tables.drivePosY.setDouble(driveOdometry.getPoseMeters().getY());
		robot.tables.drivePosAngle.setDouble(driveOdometry.getPoseMeters().getRotation().getDegrees());
	}

	public void startAuto() {
		robot.motor.leftEncoder.setPosition(0);
		robot.motor.rightEncoder.setPosition(0);
		driveOdometry = new DifferentialDriveOdometry(Rotation2d.fromDegrees(-1 * robot.motor.ahrs.getFusedHeading()));
		autoStartTime = Timer.getFPGATimestamp();
		state = 0;
		bouncePathState = 0;
		// robot.intake.toggle();
	}

}
