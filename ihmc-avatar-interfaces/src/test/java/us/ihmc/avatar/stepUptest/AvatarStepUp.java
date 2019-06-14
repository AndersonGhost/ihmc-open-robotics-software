package us.ihmc.avatar.stepUptest;

import static us.ihmc.robotics.Assert.*;

import controller_msgs.msg.dds.*;
import org.junit.jupiter.api.*;
import us.ihmc.atlas.*;
import us.ihmc.avatar.*;
import us.ihmc.avatar.drcRobot.*;
import us.ihmc.avatar.factory.*;
import us.ihmc.avatar.initialSetup.OffsetAndYawRobotInitialSetup;
import us.ihmc.avatar.testTools.DRCSimulationTestHelper;
import us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.CoPGeneration.*;
import us.ihmc.commonWalkingControlModules.configurations.*;
import us.ihmc.commonWalkingControlModules.controlModules.TrajectoryStatusMessageHelper.*;
import us.ihmc.commons.thread.*;
import us.ihmc.communication.packets.*;
import us.ihmc.euclid.geometry.*;
import us.ihmc.euclid.referenceFrame.*;
import us.ihmc.euclid.referenceFrame.tools.*;
import us.ihmc.euclid.transform.*;
import us.ihmc.euclid.tuple2D.*;
import us.ihmc.euclid.tuple3D.*;
import us.ihmc.euclid.tuple4D.*;
import us.ihmc.euclid.tuple4D.interfaces.*;
import us.ihmc.footstepPlanning.*;
import us.ihmc.footstepPlanning.flatGroundPlanning.*;
import us.ihmc.footstepPlanning.graphSearch.aStar.*;
import us.ihmc.footstepPlanning.graphSearch.aStar.AStarBestEffortTest.*;
import us.ihmc.footstepPlanning.graphSearch.nodeExpansion.*;
import us.ihmc.footstepPlanning.graphSearch.parameters.*;
import us.ihmc.footstepPlanning.graphSearch.planners.*;
import us.ihmc.footstepPlanning.simplePlanners.*;
import us.ihmc.footstepPlanning.tools.*;
import us.ihmc.humanoidRobotics.communication.packets.*;
import us.ihmc.humanoidRobotics.footstep.*;
import us.ihmc.idl.IDLSequence.Double;
import us.ihmc.idl.IDLSequence.Object;
import us.ihmc.mecano.frames.*;
import us.ihmc.mecano.multiBodySystem.interfaces.*;
import us.ihmc.robotModels.*;
import us.ihmc.robotics.*;
import us.ihmc.robotics.geometry.*;
import us.ihmc.robotics.kinematics.*;
import us.ihmc.robotics.math.trajectories.trajectorypoints.*;
import us.ihmc.robotics.math.trajectories.trajectorypoints.lists.*;
import us.ihmc.robotics.partNames.*;
import us.ihmc.robotics.robotSide.*;
import us.ihmc.robotics.trajectories.*;
import us.ihmc.simulationConstructionSetTools.bambooTools.*;
import us.ihmc.simulationConstructionSetTools.util.environments.environmentRobots.*;
import us.ihmc.simulationConstructionSetTools.util.environments.planarRegionEnvironments.*;
import us.ihmc.simulationToolkit.controllers.*;
import us.ihmc.simulationconstructionset.*;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.util.simulationRunner.*;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.*;
import us.ihmc.simulationconstructionset.util.simulationTesting.*;

import us.ihmc.tools.*;
import us.ihmc.yoVariables.registry.*;
import us.ihmc.yoVariables.variable.*;

import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class AvatarStepUp implements MultiRobotTestInterface
{
   public us.ihmc.euclid.tuple3D.Point3D waypointtotransfer;
   //private AvatarStepUp planner;
   private YoVariableRegistry registry = new YoVariableRegistry("testRegistry");
   private AStarFootstepPlanner planner;
   private YoBoolean walkPaused;
   private AvatarSimulation avatarSimulation;
   private Robot[] robots;
   //protected SimulationTestingParameters simulationTestingParameters;

   private int numberOfFootstepByPlanner;
   private double stepLength = 0.7;
   private double stepWidth = 0.13;
   private final static ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromSystemProperties();
   private final AtlasRobotVersion version = AtlasRobotVersion.ATLAS_UNPLUGGED_V5_NO_HANDS;
   private final AtlasRobotModel robotModel = new AtlasRobotModel(version,RobotTarget.SCS,false);
   private final AtlasJointMap jointMap = new AtlasJointMap(version,robotModel.getPhysicalProperties());//cannot use getRobotModel its of type DRCRobotModel while AtlasJintMap requires AtlasRobotModel object
   private ArmJointName[] armJoint = getArmJointNames();
   private Random random = new Random(42);
   //private DRCRobotModel robotModela = getRobotModel();
   private FullHumanoidRobotModel fullRobotModel = robotModel.createFullRobotModel();

   private final boolean IS_PAUSING_ON = false;  //should be false for now
   private final boolean IS_CHEST_ON = true;
   private final boolean IS_LEFTARM_ON = true;
   private final boolean IS_RIGHTARM_ON = true;
   private final boolean IS_PELVIS_ON = true;
   private final boolean IS_FOOTSTEP_ON = true;




   private DRCSimulationTestHelper drcSimulationTestHelper;
   //private final AtlasR

   @BeforeEach
   public  void showMemoryUsageBeforeTest()
   {
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " before test.");
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());
   }

   @BeforeEach
   public void setup()
   {
      FootstepPlannerParameters parameters = new BestEffortPlannerParameters(3);
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      ParameterBasedNodeExpansion expansion = new ParameterBasedNodeExpansion(parameters);
      this.planner = AStarFootstepPlanner.createPlanner(parameters, null, footPolygons, expansion, registry);
      planner.setTimeout(0.5);
   }

   @AfterEach

   public void destroySimulationAndRecycleMemory()
   {
      if (simulationTestingParameters.getKeepSCSUp())
      {
         ThreadTools.sleepForever();
      }

      if(drcSimulationTestHelper != null)
      {
         drcSimulationTestHelper.destroySimulation();
         drcSimulationTestHelper = null;
      }

      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + "after test.");
      BambooTools.reportTestFinishedMessage(simulationTestingParameters.getShowWindows());
   }

   @AfterEach
   public void tearDown()
   {
      planner = null;
      ReferenceFrameTools.clearWorldFrameTree();
   }

   protected ArmJointName[] getArmJointNames()
   {
      return jointMap.getArmJointNames();
   }

   protected int getArmTrajectoryPoints()
   {
      return 4;
   }

   protected int getArmDoF()
   {
      return 6;
   }


   //start writing your step up code here now
   @Test
   public void stepUpSmall() throws SimulationExceededMaximumTimeException
   {
      double stepHeight = 0.3;
      double swingHeight = 0.10; //maybe needs to be changed
      //walkingStair(stepHeight, swingHeight);
      setPublishers(IS_LEFTARM_ON, IS_RIGHTARM_ON, IS_CHEST_ON, IS_FOOTSTEP_ON, IS_PELVIS_ON, IS_PAUSING_ON,  stepHeight, swingHeight);

   }

   @Test
   public void stepUpBig() throws SimulationExceededMaximumTimeException
   {
      double stepHeight = 0.5;

   }

   /**
    * initialize all the publishers you want to send to the messages and create environemnt too
    * @param leftArm - set true for left arm trajectory
    * @param rightArm - set true for right arm trajectory
    * @param chest - set true for chest trajectory
    * @param isfootstepsON - set true to initialize preset footsteps till the steps and then the AStarfootstep planner kicks in
    * @param pelvis - set true for setting nomial pelvis height
    * @param pauseWhileWalking - set true to puase just before the hole and complete bending action
    * @param stepHeight - environment parameter
    * @param swingHeight - swing phase parameter
    * @throws SimulationExceededMaximumTimeException
    */
   @Test
   public void setPublishers(boolean leftArm, boolean rightArm, boolean chest, boolean isfootstepsON, boolean pelvis, boolean pauseWhileWalking, double stepHeight, double swingHeight) throws SimulationExceededMaximumTimeException
   {
      DRCRobotModel robotModel = setTestEnvironment(stepHeight);

      FootstepDataListMessage footsteps = createFootSteps(robotModel, stepHeight, swingHeight);

      if(leftArm)
      {
         ArmTrajectoryMessage leftArmTrajectoryMessages = createArmLeftTrajectory();
         drcSimulationTestHelper.publishToController(leftArmTrajectoryMessages);
      }

      if(rightArm)
      {
         ArmTrajectoryMessage rightArmTrajectoryMessages = createArmRightTrajectory();
         drcSimulationTestHelper.publishToController(rightArmTrajectoryMessages);
      }

      if(chest)
      {
         createAndPublishChestTrajectory(ReferenceFrame.getWorldFrame(),drcSimulationTestHelper.getReferenceFrames().getPelvisZUpFrame());
      }

      if(isfootstepsON)
      {

         drcSimulationTestHelper.publishToController(footsteps);


      }

      if(pelvis)
      {
         PelvisHeightTrajectoryMessage pelvisDHeight = createPelvisZUp(stepHeight);
         drcSimulationTestHelper.publishToController(pelvisDHeight);
      }

      if(pauseWhileWalking)
      {
         pauseWhileWalking(IS_PAUSING_ON);
      }

      WalkingControllerParameters walkingControllerParameters = getRobotModel().getWalkingControllerParameters();
      double stepTime = walkingControllerParameters.getDefaultSwingTime() + walkingControllerParameters.getDefaultTouchdownTime();
      double initialFinalTransfer = walkingControllerParameters.getDefaultInitialTransferTime();

      // robot fell
      Assert.assertTrue(drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(footsteps.getFootstepDataList().size() * stepTime +2.0*initialFinalTransfer + 12.0));

      // robot did not fall but did not reach goal
      assertreached(footsteps);

      ThreadTools.sleepForever(); //does not kill the simulation
   }

   /**
    * create right arm trajectory in joint space
    * @return Arm Trajectory Message
    */
   private ArmTrajectoryMessage createArmLeftTrajectory()
   {

      ArrayList<OneDoFJointTrajectoryMessage> leftandrightinfo = new ArrayList<>();

      ArmTrajectoryMessage leftHandMessage = HumanoidMessageTools.createArmTrajectoryMessage(RobotSide.LEFT);


      ArmJointName[] armJointName = getArmJointNames();

      ArrayList<OneDoFJointTrajectoryMessage> leftArmTrajectory = new ArrayList<>();


      for(int armJointindex = 0 ; armJointindex < getArmDoF(); ++armJointindex)
      {
         OneDoFJointTrajectoryMessage leftJointTrajectory = new OneDoFJointTrajectoryMessage();


         for (int trajectoryPointIndex =0; trajectoryPointIndex < getArmTrajectoryPoints(); ++trajectoryPointIndex)
         {
            leftJointTrajectory.getTrajectoryPoints().add().set(HumanoidMessageTools.createTrajectoryPoint1DMessage((double) (2*trajectoryPointIndex +1), getRandomJointAngle(RobotSide.LEFT,armJoint[armJointindex],fullRobotModel),(double) 0));

         }
         leftHandMessage.getJointspaceTrajectory().getJointTrajectoryMessages().add().set(leftJointTrajectory);

         leftArmTrajectory.add(leftJointTrajectory);

      }

      return leftHandMessage;
   }

   /**
    * create left arm trajectory in joint space
    * @return Arm Trajectory Message
    */
   private ArmTrajectoryMessage createArmRightTrajectory()
   {

      ArmTrajectoryMessage rightHandMessage = HumanoidMessageTools.createArmTrajectoryMessage(RobotSide.RIGHT);


      ArrayList<OneDoFJointTrajectoryMessage> rightArmTrajectory = new ArrayList<>();

      for(int armJointindex = 0 ; armJointindex < getArmDoF(); ++armJointindex)
      {

         OneDoFJointTrajectoryMessage rightJointTrajectory = new OneDoFJointTrajectoryMessage();

         for (int trajectoryPointIndex =0; trajectoryPointIndex < getArmTrajectoryPoints(); ++trajectoryPointIndex)
         {

            rightJointTrajectory.getTrajectoryPoints().add().set(HumanoidMessageTools.createTrajectoryPoint1DMessage((double) (2*trajectoryPointIndex +1), getRandomJointAngle(RobotSide.RIGHT, armJoint[armJointindex], fullRobotModel), (double) 0));
         }

         rightHandMessage.getJointspaceTrajectory().getJointTrajectoryMessages().add().set(rightJointTrajectory);

         rightArmTrajectory.add(rightJointTrajectory);
      }

      return rightHandMessage;
   }

   /**
    * the first line is when the message of pausing kicks in. You need to adjust the timing manually. If the robot is in middle of completing an action it will complete it before
    * stopping. The second time is w.r.t to the first one (e.g 3s pause). After the motion is resume the Reference frames somehow get mixed up and the whole controller crashes.
    * Should be kept false for the meanwhile. Should update soon
    */
   private void pauseWhileWalking(boolean pauseONorOFF) throws SimulationExceededMaximumTimeException
   {

      if(pauseONorOFF)
      {
         drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(10.5);
         PauseWalkingMessage pauseWalkingMessage = HumanoidMessageTools.createPauseWalkingMessage(true);
         drcSimulationTestHelper.publishToController(pauseWalkingMessage);
         drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(3.0);
         PauseWalkingMessage resumeWalkingMessag = HumanoidMessageTools.createPauseWalkingMessage(false);
         drcSimulationTestHelper.publishToController(resumeWalkingMessag);
      }
   }

   /**
    * method to create footsteps using initial pose and final pose with  AStar footstep planner. Called in createFootsteps method
    * @param stepLength
    * @param stepWidth
    * @return footstepdatamessage
    */
   private FootstepDataMessage[] createFootstepUsingFootstepPlanner(double stepLength, double stepWidth)
   {

      Point3D initialWaypoint = waypointtotransfer; //transfer the previous waypoint to a local variable

      Point2D initialStance = new Point2D(initialWaypoint.getX(), initialWaypoint.getY());
      FramePose2D initiaWaypoint2d = new FramePose2D(ReferenceFrame.getWorldFrame(), initialStance, 0.0);

      double finalXVale = (1+2.5*stepLength) +0.2;
      Point2D finalStance = new Point2D( finalXVale,stepWidth);
      FramePose2D finalWaypoint2d = new FramePose2D(ReferenceFrame.getWorldFrame(), finalStance, 0.0);

      RobotSide initialSide = RobotSide.LEFT;
      // starting foot position for left
      FramePose3D initialWaypoint3d = FlatGroundPlanningUtils.poseFormPose2d(initiaWaypoint2d, 0.0);
      FramePose3D finalWaypoint3d = FlatGroundPlanningUtils.poseFormPose2d(finalWaypoint2d,0.0);
      SimpleFootstep simpleFootstep = new SimpleFootstep(RobotSide.LEFT, finalWaypoint3d);

      FootstepPlan footstep = PlannerTools.runPlanner(planner, initialWaypoint3d, initialSide, finalWaypoint3d,null, true);



      numberOfFootstepByPlanner = footstep.getNumberOfSteps();
      FootstepDataMessage[] footsteps = new FootstepDataMessage[numberOfFootstepByPlanner];
      RobotSide side = RobotSide.LEFT;
      for (int footstepIndex = 0; footstepIndex < footstep.getNumberOfSteps(); footstepIndex++)
      {
         Point3D waypoint = new Point3D(footstep.getFootstep(footstepIndex).getSoleFramePose().getPosition().getX(), stepWidth, 0.0);
         FrameQuaternion orientation = new FrameQuaternion();
         FootstepDataMessage footstepDataMessage = HumanoidMessageTools.createFootstepDataMessage(side, waypoint, orientation);
         footsteps[footstepIndex] = footstepDataMessage;
         side = side.getOppositeSide();
         stepWidth = -stepWidth;
      }

      return footsteps;
   }

   /**
    * creates chest waypoints to be reached with the set time limit with publishes it after all the waypoints are set. THe ordering is an issue. he sequence is the very first message you publish
    * and then the add all the remaining stance in a queue with the same message_ID (in our case it is -1). THey get executed from last to the second form top. TrajectoryTime is w.r.t
    * to the simulation.
    * @param dataframe
    * @param trajectoryFrame
    */
   private void createAndPublishChestTrajectory(ReferenceFrame dataframe, ReferenceFrame trajectoryFrame)
   {
      double trajectoryTime = 10.5;
      FrameQuaternion chestOrientation1 = new FrameQuaternion(ReferenceFrame.getWorldFrame());
      FrameQuaternion chestOrientation2 = new FrameQuaternion(ReferenceFrame.getWorldFrame());
      FrameQuaternion chestOrientation3 = new FrameQuaternion(ReferenceFrame.getWorldFrame());
      FrameQuaternion chestOrientation4 = new FrameQuaternion(ReferenceFrame.getWorldFrame());
      //chestOrientation.appendYawRotation(2.0); //there also these append methods that you can use to mention only yaw,roll or pitch angles.
      double leanAngle = 20.0; //original values 20.0 and yaw was -2.36
      chestOrientation1.setYawPitchRollIncludingFrame(ReferenceFrame.getWorldFrame(), 0.00, Math.toRadians(leanAngle), 0.0);
      //Quaternion desiredchestOrientation = new Quaternion(chestOrientation);
      chestOrientation2.setYawPitchRollIncludingFrame(ReferenceFrame.getWorldFrame(), -2.36, 0.0, 0.0);
      chestOrientation3.setYawPitchRollIncludingFrame(ReferenceFrame.getWorldFrame(), 0.0, 0.0, 1.2);
      chestOrientation4.setYawPitchRollIncludingFrame(ReferenceFrame.getWorldFrame(), 0.0, 0.0, 0.0);
      FrameQuaternion[] desiredChestOrientations = {chestOrientation1, chestOrientation2, chestOrientation3,chestOrientation4};

      //ChestTrajectoryMessage chestPoint = new ChestTrajectoryMessage();
      //executes this one first then the goes in reverse starting from the ver last one
      ChestTrajectoryMessage bend = HumanoidMessageTools.createChestTrajectoryMessage(trajectoryTime, desiredChestOrientations[0], dataframe, trajectoryFrame);
      drcSimulationTestHelper.publishToController(bend);

      ChestTrajectoryMessage straight = HumanoidMessageTools.createChestTrajectoryMessage(trajectoryTime + 5.5, desiredChestOrientations[3], dataframe, trajectoryFrame);
      straight.getSo3Trajectory().getQueueingProperties().setExecutionMode(ExecutionMode.QUEUE.toByte());
      straight.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(-1);
      //bend.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(yaw.getSequenceId());
      drcSimulationTestHelper.publishToController(straight);

      /*

      ChestTrajectoryMessage yaw = HumanoidMessageTools.createChestTrajectoryMessage(trajectoryTime, desiredChestOrientations[1], dataframe, trajectoryFrame);
      yaw.getSo3Trajectory().getQueueingProperties().setExecutionMode(ExecutionMode.QUEUE.toByte());
      yaw.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(-1);
      //bend.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(yaw.getSequenceId());
      drcSimulationTestHelper.publishToController(yaw);

      ChestTrajectoryMessage roll = HumanoidMessageTools.createChestTrajectoryMessage(trajectoryTime, desiredChestOrientations[2], dataframe, trajectoryFrame);
      roll.getSo3Trajectory().getQueueingProperties().setExecutionMode(ExecutionMode.QUEUE.toByte());
      roll  .getSo3Trajectory().getQueueingProperties().setPreviousMessageId(-1);
      //bend.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(yaw.getSequenceId());
      drcSimulationTestHelper.publishToController(roll);
      /*
      ChestTrajectoryMessage straight1 = HumanoidMessageTools.createChestTrajectoryMessage(trajectoryTime, desiredChestOrientations[3], dataframe, trajectoryFrame);
      straight1.getSo3Trajectory().getQueueingProperties().setExecutionMode(ExecutionMode.QUEUE.toByte());
      straight1.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(-1);
      //bend.getSo3Trajectory().getQueueingProperties().setPreviousMessageId(yaw.getSequenceId());
      drcSimulationTestHelper.publishToController(straight1);

      System.out.println("yaw ID: " + bend.getSequenceId());
      System.out.println("bend ID:" + bend.getSequenceId());
      System.out.println("yaw1 ID: " + roll.getSequenceId());
      System.out.println("yaw2 ID: " + straight.getSequenceId());*/
   }

   /**
    * sets trajectory waypoints for pelvis. gets the Z values from when the simulation starts (time -  'simualateandbloack..' secs later) and sets the new desired
    * Z position w.r.t to the world reference frame to be reached within the given time limit.
    * @param stepHeight
    * @return
    */
   private PelvisHeightTrajectoryMessage createPelvisZUp(double stepHeight)
   {
      double nominalPelvisHeight;
      MovingReferenceFrame pelvisZUpFrame = drcSimulationTestHelper.getReferenceFrames().getPelvisZUpFrame();
      FramePoint3D reference = new FramePoint3D(pelvisZUpFrame);

      //createChestTrajectory(ReferenceFrame.getWorldFrame(), pelvisZUpFrame); //calling chest Trajectory method

      reference.changeFrame(ReferenceFrame.getWorldFrame());
      nominalPelvisHeight = reference.getZ(); //now you have the Z value from world frame perspective

      PelvisHeightTrajectoryMessage pelvisHeightTrajectoryMessage = new PelvisHeightTrajectoryMessage();
      pelvisHeightTrajectoryMessage.setEnableUserPelvisControl(true);
      pelvisHeightTrajectoryMessage.setEnableUserPelvisControlDuringWalking(true);

      EuclideanTrajectoryPointMessage waypoint1 = pelvisHeightTrajectoryMessage.getEuclideanTrajectory().getTaskspaceTrajectoryPoints().add();
      waypoint1.getPosition().setZ(nominalPelvisHeight);
      waypoint1.setTime(9.5);

      EuclideanTrajectoryPointMessage waypoint2 = pelvisHeightTrajectoryMessage.getEuclideanTrajectory().getTaskspaceTrajectoryPoints().add();
      waypoint2.getPosition().setZ(0.2*nominalPelvisHeight);
      waypoint2.setTime(12.5);

      EuclideanTrajectoryPointMessage waypoint3 = pelvisHeightTrajectoryMessage.getEuclideanTrajectory().getTaskspaceTrajectoryPoints().add();
      waypoint3.getPosition().setZ(nominalPelvisHeight);
      waypoint3.setTime(17.5);

      return pelvisHeightTrajectoryMessage;
   }

   /**
    *  methods to create and set waypoints. Called in create footsteps method
    */
   private FootstepDataMessage footsteps(int i,RobotSide side, Point3D waypoint, FrameQuaternion quaternion, double swingTime, double transferTime)
   {
      ReferenceFrame soleFrame = drcSimulationTestHelper.getControllerFullRobotModel().getSoleFrame(side);
      FramePoint3D footPosition = new FramePoint3D(soleFrame);
      FootstepDataMessage footstepDataMessage = HumanoidMessageTools.createFootstepDataMessage(side, waypoint, quaternion);
      footstepDataMessage.setTrajectoryType(TrajectoryType.DEFAULT.toByte());
      double pitch = Math.toRadians(10.0);
      double[] zways = {0.1,0.3,0.3};
      double[] xways = {0.85,1.28,1.31};
      SE3TrajectoryPointMessage[] refpoints = new SE3TrajectoryPointMessage[3];

      if (i > 0)
      {
         if (i == 1) //right
         {
            footstepDataMessage.setTrajectoryType(TrajectoryType.CUSTOM.toByte());
            Point3D waypoint1 = footstepDataMessage.getCustomPositionWaypoints().add();
            waypoint1.set(xways[i-1] - 0.16, -0.13, zways[i-1]);
            //quaternion.setYawPitchRoll(0.1, 0.0, 0.0);
            Point3D waypoint2 = footstepDataMessage.getCustomPositionWaypoints().add();
            waypoint2.set(xways[i-1] - 0.08, -0.13, zways[i-1]);

         }
         else if(i == 2) //left
         {
            footstepDataMessage.setTrajectoryType(TrajectoryType.CUSTOM.toByte());
            //quaternion.setYawPitchRoll(-0.1, 0.0, 0.0);
            Point3D waypoint1 = footstepDataMessage.getCustomPositionWaypoints().add();
            waypoint1.set(xways[i-2] - 0.2, 0.13, zways[i-1]+0.1 );
            Point3D waypoint2 = footstepDataMessage.getCustomPositionWaypoints().add();
            waypoint2.set(xways[i-1] - 0.02, 0.13, zways[i-1] );

         }
         else //right
         {
            footstepDataMessage.setTrajectoryType(TrajectoryType.CUSTOM.toByte());
            //quaternion.setYawPitchRoll(-0.1, 0.0, 0.0);
            Point3D waypoint1 = footstepDataMessage.getCustomPositionWaypoints().add();
            waypoint1.set(xways[i-2] - 0.35, -0.13, zways[i-1]+0.1);
            Point3D waypoint2 = footstepDataMessage.getCustomPositionWaypoints().add();
            waypoint2.set(xways[i-1] - 0.1, -0.13, zways[i-1]);
         }

      }

      footstepDataMessage.setSwingDuration(swingTime);
      footstepDataMessage.setTransferDuration(transferTime);

      return footstepDataMessage;
   }

   /**
    * takes in appropriate paramters and creates the whole list of footstespdatamessage that is then passed to the controller using the publisher
    * @param robotModel
    * @param stepHeight
    * @param swingHeight
    * @return
    */
   private FootstepDataListMessage createFootSteps(DRCRobotModel robotModel,double stepHeight, double swingHeight)
   {
      //create a list of desired footstep
      double stepWidth = 0.13;
      double swingTime = 2.0;
      double defaultTransferTime = robotModel.getWalkingControllerParameters().getDefaultTransferTime();

      ///create a object for the footstepdtatalistmessage class
      //FootstepDataListMessage footstepDataListMessage = HumanoidMessageTools.createFootstepDataListMessage(swingTime, defaultTransferTime);
      FootstepDataListMessage footstepDataListMessage = new FootstepDataListMessage();
      //footstepDataListMessage.setExecutionTiming(ExecutionTiming.CONTROL_ABSOLUTE_TIMINGS.toByte());
      //footstepDataListMessage.setAreFootstepsAdjustable(false); //try changing this later to see the changes

      RobotSide[] robotSides = drcSimulationTestHelper.createRobotSidesStartingFrom(RobotSide.LEFT, 4);

      //RobotSide side = RobotSide.LEFT;
      RobotSide tempright = RobotSide.RIGHT;
      MovingReferenceFrame soleZUpFramel = drcSimulationTestHelper.getReferenceFrames().getSoleZUpFrame(robotSides[0]); //left side
      MovingReferenceFrame soleZUpFramer = drcSimulationTestHelper.getReferenceFrames().getSoleZUpFrame(tempright); //right side
      FramePose3D solereferencel = new FramePose3D(soleZUpFramel);
      FramePose3D solereferencer = new FramePose3D(soleZUpFramer);
      solereferencel.changeFrame(ReferenceFrame.getWorldFrame());
      double xsole = solereferencel.getPosition().getX();
      double ysolel = solereferencel.getPosition().getY();
      double ysoler = solereferencer.getPosition().getY();
      double zsole = solereferencel.getPosition().getZ();
      //add them to a object of class footstepdatalistmessage
      for (int i = 0 ; i < 4; i++)   //get there in 4 steps - trying making this autonomous in future
      {
         //update robot side and variables
         xsole += 0.18;
         if (i>1)
         {
            xsole += 0.25;
         }

         if (i >=2)
         {
            if(i ==3)
            {
               xsole -= 0.4;
            }
            zsole = stepHeight;
         }
         if (i%2 == 0) //left
         {
            Point3D waypoint = new Point3D(xsole, stepWidth, zsole);
            if(i==2) {waypointtotransfer = waypoint;}
            FrameQuaternion frameQuaternion = new FrameQuaternion();
            footstepDataListMessage.getFootstepDataList().add().set(footsteps(i,robotSides[i] ,waypoint, frameQuaternion, swingTime, defaultTransferTime));

         }
         else //right
         {
            Point3D waypoint = new Point3D(xsole, -stepWidth, zsole);
            FrameQuaternion frameQuaternion = new FrameQuaternion();
            footstepDataListMessage.getFootstepDataList().add().set(footsteps(i,robotSides[i] ,waypoint, frameQuaternion, swingTime, defaultTransferTime));
         }

         System.out.println("x position sole: " + xsole);
         System.out.println("yr position sole: " + ysoler);
         System.out.println("z position sole: "+ zsole);
      }

      FootstepDataMessage[] toBeAddedFootsteps = createFootstepUsingFootstepPlanner(stepLength,stepWidth);
      for(int footstepIndex = 0; footstepIndex < toBeAddedFootsteps.length; footstepIndex++)
      {

         if (footstepIndex == 1)
         {

            Point3D newpoint = new Point3D(toBeAddedFootsteps[footstepIndex].getLocation().getX() -0.3,toBeAddedFootsteps[footstepIndex].getLocation().getY() , toBeAddedFootsteps[footstepIndex].getLocation().getZ());
            toBeAddedFootsteps[footstepIndex].getLocation().set(newpoint);
            footstepDataListMessage.getFootstepDataList().add().set(toBeAddedFootsteps[footstepIndex]);
         }
         else
         {footstepDataListMessage.getFootstepDataList().add().set(toBeAddedFootsteps[footstepIndex]);}
      }

      return footstepDataListMessage;
   }

   private static void recursivelyAddPinJoints(Joint joint, List<PinJoint> pinJoints)
   {
      if (joint instanceof PinJoint) // other joint types are - FloatingJoint, FreeJoint, FloatingPlanarJoint, PinJoint, SliderJoint and NullJoints
         pinJoints.add((PinJoint) joint); //type casting

      for(Joint child : joint. getChildrenJoints())
      {
         recursivelyAddPinJoints(child, pinJoints); //calling itself to add the child joints too
      }
   }

   protected double getRandomJointAngle(RobotSide side, ArmJointName armJointName, FullHumanoidRobotModel fullHumanoidRobotModel)
   {
      OneDoFJointBasics armJoint = fullHumanoidRobotModel.getArmJoint(side, armJointName);
      if (armJoint!= null)
      {
         double jointAngle = armJoint.getJointLimitLower() + (armJoint.getJointLimitUpper() - armJoint.getJointLimitLower()) * random.nextDouble();
         return jointAngle;
      }
      else
      {
         return 0.0;
      }
   }



   private void assertreached(FootstepDataListMessage footsteps)
   {
      int numberofsteps = footsteps.getFootstepDataList().size();
      Point3D lastStep = footsteps.getFootstepDataList().get(numberofsteps-1).getLocation();
      Point3D nextToLastStep = footsteps.getFootstepDataList().get(numberofsteps - 2).getLocation();

      Point3D midStance = new Point3D();
      midStance.interpolate(lastStep, nextToLastStep, 0.5);

      Point3D midpoint = new Point3D(midStance);
      midpoint.addZ(1.0);

      Point3D bounds = new Point3D(0.25, 0.25, 1.0);

      BoundingBox3D boundingBox3D = BoundingBox3D.createUsingCenterAndPlusMinusVector(midpoint, bounds);
      drcSimulationTestHelper.assertRobotsRootJointIsInBoundingBox(boundingBox3D);
   }
   private DRCRobotModel setTestEnvironment(double stepHeight) throws SimulationExceededMaximumTimeException
   {
    /*      //create environment
      AdjustableStairsEnvironment adjustableStairsEnvironment = new AdjustableStairsEnvironment();
      //adjustableStairsEnvironment.setCourseStartDistance(0.5);
      //adjustableStairsEnvironment.setLandingPlatformParameters(0,0,0,0);
      //adjustableStairsEnvironment.setRailingParameters(0,0,0,0,0,false);
      adjustableStairsEnvironment.setStairsParameters(2,1.0,0.4,0.5);
      adjustableStairsEnvironment.generateTerrains();
*/


      String className = getClass().getSimpleName();
      //SingleStepEnvironment environment = new SingleStepEnvironment(stepHeight, 0.7);
      Wallswithstairs wall = new Wallswithstairs(0.5, 1.5, stepHeight);

      //Point3D doorPosition = new Point3D(2.0 + 1.0, 0.0, 0.0);
      //ContactableDoorRobot door = new ContactableDoorRobot("doorRobot", doorPosition);
      //YoVariableRegistry doorTestRegistry = new YoVariableRegistry("doorTestRegistry");
      //contactableRobots.add(door);
      //robots = new Robot[1];
      //robots[0] = door;
      //SimulationConstructionSet scs = new SimulationConstructionSet(robots, SimulationConstructionSetParameters.createFromSystemProperties());
      //scs.addYoVariableRegistry(doorTestRegistry);
      //door.createAvailableContactPoints(0,15,15,0.02,true);
      //BlockingSimulationRunner blockingSimulationRunner = new BlockingSimulationRunner(scs, 60);
      //blockingSimulationRunner.simulateAndBlock(20.0);

      DRCRobotModel robotModel = getRobotModel();

      //pass this reference to the simulator
      drcSimulationTestHelper = new DRCSimulationTestHelper(simulationTestingParameters, robotModel, wall);

      //drcSimulationTestHelper = new DRCSimulationTestHelper(simulationTestingParameters, DRCRobotModel atlasRobotModel, adjustableStairsEnvironment);
      drcSimulationTestHelper.setStartingLocation(new OffsetAndYawRobotInitialSetup(0.5, 0.0, 0.0, 0.0)); //setting starting location
      drcSimulationTestHelper.createSimulation(className);
      //PushRobotController pushRobotController = new PushRobotController(drcSimulationTestHelper.getRobot(), robotModel.createFullRobotModel().getChest().getParentJoint().getName(), new Vector3D(0.0, 0.0, 0.15));


      setUpCamera();
      ThreadTools.sleep(1000);
      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(0.25);
      //createTorqueGraphs(drcSimulationTestHelper.getSimulationConstructionSet(), getRobotModel().createHumanoidFloatingRootJointRobot(false));
      //printMinMax(drcSimulationTestHelper.getSimulationConstructionSet());
      assertTrue(success);

      return robotModel;
   }

   private void setUpCamera()
   {
      Point3D cameraFix = new Point3D(0.0,0.0,0.89);
      Point3D cameraPosition = new Point3D(10.0,2.0,1.37);
      drcSimulationTestHelper.setupCameraForUnitTest(cameraFix, cameraPosition);
   }

   private class BestEffortPlannerParameters extends DefaultFootstepPlanningParameters
   {
      private final int minimumStepsForBestEffortPlan;

      BestEffortPlannerParameters(int minimumStepsForBestEffortPlan)
      {
         this.minimumStepsForBestEffortPlan = minimumStepsForBestEffortPlan;
      }

      @Override
      public boolean getReturnBestEffortPlan()
      {
         return true;
      }

      @Override
      public int getMinimumStepsForBestEffortPlan(){return minimumStepsForBestEffortPlan;}
   }
   //public us.ihmc.euclid.tuple3D.Point3D getWaypoint()
   //@Override
   //
   /*
   public FootstepPlanner getPlanner()
   {
      return planner;
   }*/

/*
   private void getPinJoints(Robot robot, List<PinJoint> pinJoint) //call the recursivelyAddPinJoints to get a list of all PinJoint(only) and ignore the other types of joints
   {
      for(Joint rootJoint : robot.getRootJoints())
      {
         recursivelyAddPinJoints(rootJoint, pinJoint);
      }
   }
   private void createTorqueGraphs(SimulationConstructionSet scs, Robot robot)
   {

      List<PinJoint> pinJoints = new ArrayList<>();

      getPinJoints(robot, pinJoints);

      List<String> torsojoints = new ArrayList<String>();
      List<String> leftlegjoints = new ArrayList<String>();
      List<String> rightlegjoints = new ArrayList<String>();

      for (PinJoint joint : pinJoints)
      {
         String name = joint.getTauYoVariable().getName();

         if (name.contains("l_leg"))
         {
            leftlegjoints.add(name);
         }
         else if (name.contains("r_leg"))
         {
            rightlegjoints.add(name);
         }
         else if (name.contains("back"))
         {
            torsojoints.add(name);
         }

         addGraph(scs, torsojoints);
         addGraph(scs, leftlegjoints);
         addGraph(scs, rightlegjoints);
      }
   }

      private void addGraph(SimulationConstructionSet scs, List<String> joint)
      {
         //scs.setupGraph(joint.toArray(new String[0])); //casting it to string object so that it can be passed to plot graph
      }

      private void printMinMax(SimulationConstructionSet scs)
      {
         StandardSimulationGUI window = scs.getGUI();
         GraphArrayPanel panel = window.getGraphArrayPanel();
         ArrayList<YoGraph> graphs = panel.getGraphsOnThisPanel();

         if (graphs.size() != 0)
         {
            return;
         }

         for(YoGraph graph: graphs)
         {
            ArrayList<DataEntry> entries = graph. getEntriesOnThisGraph();
            entries.forEach(entry -> System.out.println(entry.getVariableName() + "Max Torque: [" + Math.max(Math.abs(entry.getMin()), Math.abs(entry.getMax()))+ "]"));
         }
      }
*/
}



