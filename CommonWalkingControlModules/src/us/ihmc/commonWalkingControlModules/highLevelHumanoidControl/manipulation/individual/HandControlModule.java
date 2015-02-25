package us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual;

import static us.ihmc.yoUtilities.stateMachines.StateMachineTools.addRequestedStateTransition;

import java.util.LinkedHashMap;
import java.util.Map;

import us.ihmc.commonWalkingControlModules.configurations.ArmControllerParameters;
import us.ihmc.commonWalkingControlModules.controlModules.RigidBodySpatialAccelerationControlModule;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.AbstractJointSpaceHandControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.InverseKinematicsTaskspaceHandPositionControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.JointSpaceHandControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.LoadBearingPlaneHandControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.LowLevelInverseKinematicsTaskspaceHandPositionControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.LowLevelJointSpaceHandControlControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states.TaskspaceHandPositionControlState;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumBasedController;
import us.ihmc.commonWalkingControlModules.packetProducers.HandPoseStatusProducer;
import us.ihmc.commonWalkingControlModules.packetProviders.ControlStatusProducer;
import us.ihmc.utilities.humanoidRobot.model.FullRobotModel;
import us.ihmc.utilities.math.geometry.FramePose;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.robotSide.RobotSide;
import us.ihmc.utilities.screwTheory.OneDoFJoint;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.ScrewTools;
import us.ihmc.utilities.screwTheory.TwistCalculator;
import us.ihmc.yoUtilities.controllers.YoPIDGains;
import us.ihmc.yoUtilities.controllers.YoSE3PIDGains;
import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;
import us.ihmc.yoUtilities.dataStructure.variable.BooleanYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.DoubleYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.EnumYoVariable;
import us.ihmc.yoUtilities.graphics.YoGraphicsListRegistry;
import us.ihmc.yoUtilities.math.trajectories.ConstantPoseTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.FinalApproachPoseTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.InitialClearancePoseTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.LeadInOutPoseTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.MultipleWaypointsOneDoFJointTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.OneDoFJointQuinticTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.OneDoFJointWayPointTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.PoseTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.StraightLinePoseTrajectoryGenerator;
import us.ihmc.yoUtilities.math.trajectories.providers.YoVariableDoubleProvider;
import us.ihmc.yoUtilities.stateMachines.State;
import us.ihmc.yoUtilities.stateMachines.StateChangedListener;
import us.ihmc.yoUtilities.stateMachines.StateMachine;
import us.ihmc.yoUtilities.stateMachines.StateTransition;
import us.ihmc.yoUtilities.stateMachines.StateTransitionAction;
import us.ihmc.yoUtilities.stateMachines.StateTransitionCondition;

public class HandControlModule
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoVariableRegistry registry;

   private final StateMachine<HandControlState> stateMachine;
   private final RigidBodySpatialAccelerationControlModule handSpatialAccelerationControlModule;

   private final Map<OneDoFJoint, OneDoFJointQuinticTrajectoryGenerator> quinticPolynomialTrajectoryGenerators;
   private final Map<OneDoFJoint, OneDoFJointWayPointTrajectoryGenerator> waypointsPolynomialTrajectoryGenerators;
   private final Map<OneDoFJoint, MultipleWaypointsOneDoFJointTrajectoryGenerator> wholeBodyWaypointsPolynomialTrajectoryGenerators;

   private final ConstantPoseTrajectoryGenerator holdPoseTrajectoryGenerator;
   private final StraightLinePoseTrajectoryGenerator straightLinePoseTrajectoryGenerator;
   private final FinalApproachPoseTrajectoryGenerator finalApproachPoseTrajectoryGenerator;
   private final InitialClearancePoseTrajectoryGenerator initialClearancePoseTrajectoryGenerator;
   private final LeadInOutPoseTrajectoryGenerator leadInOutPoseTrajectoryGenerator;

   private final BooleanYoVariable isExecutingHandStep;

   private final YoVariableDoubleProvider trajectoryTimeProvider;

   private final Map<OneDoFJoint, Double> jointCurrentPositionMap;

   private final TaskspaceHandPositionControlState taskSpacePositionControlState;
   private final AbstractJointSpaceHandControlState jointSpaceHandControlState;
   private final LoadBearingPlaneHandControlState loadBearingControlState;

   private final EnumYoVariable<HandControlState> requestedState;
   private final OneDoFJoint[] oneDoFJoints;
   private final String name;
   private final RobotSide robotSide;
   private final TwistCalculator twistCalculator;
   private final RigidBody chest, hand;

   private final FullRobotModel fullRobotModel;

   private final double controlDT;

   private final YoSE3PIDGains taskspaceGains, taskspaceLoadBearingGains;

   private final StateChangedListener<HandControlState> stateChangedlistener;
   private final HandPoseStatusProducer handPoseStatusProducer;
   private final BooleanYoVariable hasHandPoseStatusBeenSent;

   public HandControlModule(RobotSide robotSide, MomentumBasedController momentumBasedController, ArmControllerParameters armControlParameters,
         YoPIDGains jointspaceGains, YoSE3PIDGains taskspaceGains, YoSE3PIDGains taskspaceLoadBearingGains, ControlStatusProducer controlStatusProducer,
         HandPoseStatusProducer handPoseStatusProducer, YoVariableRegistry parentRegistry)
   {
      this.handPoseStatusProducer = handPoseStatusProducer;
      String namePrefix = robotSide.getCamelCaseNameForStartOfExpression();
      name = namePrefix + getClass().getSimpleName();

      hasHandPoseStatusBeenSent = new BooleanYoVariable(namePrefix + "HasHandPoseStatusBeenSent", parentRegistry);
      hasHandPoseStatusBeenSent.set(false);

      stateChangedlistener = new StateChangedListener<HandControlState>()
      {
         @Override
         public void stateChanged(State<HandControlState> oldState, State<HandControlState> newState, double time)
         {
            hasHandPoseStatusBeenSent.set(false);
         }
      };

      this.controlDT = momentumBasedController.getControlDT();

      this.taskspaceGains = taskspaceGains;
      this.taskspaceLoadBearingGains = taskspaceLoadBearingGains;

      fullRobotModel = momentumBasedController.getFullRobotModel();
      hand = fullRobotModel.getHand(robotSide);
      chest = fullRobotModel.getChest();
      ReferenceFrame handFrame = hand.getBodyFixedFrame();
      int jacobianId = momentumBasedController.getOrCreateGeometricJacobian(chest, hand, handFrame);

      this.robotSide = robotSide;
      registry = new YoVariableRegistry(name);
      twistCalculator = momentumBasedController.getTwistCalculator();

      oneDoFJoints = ScrewTools.filterJoints(ScrewTools.createJointPath(chest, hand), OneDoFJoint.class);

      requestedState = new EnumYoVariable<HandControlState>(name + "RequestedState", "", registry, HandControlState.class, true);
      requestedState.set(null);

      trajectoryTimeProvider = new YoVariableDoubleProvider(name + "TrajectoryTime", registry);

      quinticPolynomialTrajectoryGenerators = new LinkedHashMap<OneDoFJoint, OneDoFJointQuinticTrajectoryGenerator>();

      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         OneDoFJointQuinticTrajectoryGenerator trajectoryGenerator = new OneDoFJointQuinticTrajectoryGenerator(oneDoFJoint.getName() + "Trajectory",
               oneDoFJoint, trajectoryTimeProvider, registry);
         quinticPolynomialTrajectoryGenerators.put(oneDoFJoint, trajectoryGenerator);
      }

      waypointsPolynomialTrajectoryGenerators = new LinkedHashMap<>();

      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         OneDoFJointWayPointTrajectoryGenerator trajectoryGenerator = new OneDoFJointWayPointTrajectoryGenerator(oneDoFJoint.getName() + "Trajectory", oneDoFJoint, trajectoryTimeProvider, 15, registry);
         waypointsPolynomialTrajectoryGenerators.put(oneDoFJoint, trajectoryGenerator);
      }
      
      wholeBodyWaypointsPolynomialTrajectoryGenerators = new LinkedHashMap<OneDoFJoint, MultipleWaypointsOneDoFJointTrajectoryGenerator>();
      
      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         MultipleWaypointsOneDoFJointTrajectoryGenerator multiWaypointTrajectoryGenerator = new MultipleWaypointsOneDoFJointTrajectoryGenerator(oneDoFJoint.getName(), oneDoFJoint, 15, registry);
         wholeBodyWaypointsPolynomialTrajectoryGenerators.put(oneDoFJoint, multiWaypointTrajectoryGenerator);
      }

      DoubleYoVariable simulationTime = momentumBasedController.getYoTime();
      stateMachine = new StateMachine<HandControlState>(name, name + "SwitchTime", HandControlState.class, simulationTime, registry);

      handSpatialAccelerationControlModule = new RigidBodySpatialAccelerationControlModule(name, twistCalculator, hand,
            fullRobotModel.getHandControlFrame(robotSide), controlDT, registry);
      handSpatialAccelerationControlModule.setGains(taskspaceGains);

      YoGraphicsListRegistry yoGraphicsListRegistry = momentumBasedController.getDynamicGraphicObjectsListRegistry();
      boolean visualize = true;
      holdPoseTrajectoryGenerator = new ConstantPoseTrajectoryGenerator(name + "Hold", true, worldFrame, parentRegistry);
      straightLinePoseTrajectoryGenerator = new StraightLinePoseTrajectoryGenerator(name + "StraightLine", true, worldFrame, registry, visualize,
            yoGraphicsListRegistry);
      finalApproachPoseTrajectoryGenerator = new FinalApproachPoseTrajectoryGenerator(name, true, worldFrame, registry, visualize, yoGraphicsListRegistry);
      initialClearancePoseTrajectoryGenerator = new InitialClearancePoseTrajectoryGenerator(name + "MoveAway", true, worldFrame, registry, visualize,
            yoGraphicsListRegistry);
      leadInOutPoseTrajectoryGenerator = new LeadInOutPoseTrajectoryGenerator(name + "Swing", true, worldFrame, registry, visualize, yoGraphicsListRegistry);

      loadBearingControlState = new LoadBearingPlaneHandControlState(namePrefix, HandControlState.LOAD_BEARING, robotSide, momentumBasedController,
            fullRobotModel.getElevator(), hand, jacobianId, registry);

      if (armControlParameters.doLowLevelPositionControl())
      {
         jointSpaceHandControlState = new LowLevelJointSpaceHandControlControlState(namePrefix, HandControlState.JOINT_SPACE, robotSide, oneDoFJoints,
               momentumBasedController, armControlParameters, controlDT, registry);
      }
      else
      {
         jointSpaceHandControlState = new JointSpaceHandControlState(namePrefix, HandControlState.JOINT_SPACE, robotSide, oneDoFJoints,
               momentumBasedController, armControlParameters, jointspaceGains, controlDT, registry);
      }

      if (armControlParameters.useInverseKinematicsTaskspaceControl())
      {
         if (armControlParameters.doLowLevelPositionControl())
         {
            taskSpacePositionControlState = new LowLevelInverseKinematicsTaskspaceHandPositionControlState(namePrefix, HandControlState.TASK_SPACE_POSITION,
                  robotSide, momentumBasedController, jacobianId, chest, hand, yoGraphicsListRegistry, armControlParameters, controlStatusProducer, controlDT,
                  registry);
         }
         else
         {
            taskSpacePositionControlState = new InverseKinematicsTaskspaceHandPositionControlState(namePrefix, HandControlState.TASK_SPACE_POSITION, robotSide,
                  momentumBasedController, jacobianId, chest, hand, yoGraphicsListRegistry, armControlParameters, controlStatusProducer, jointspaceGains,
                  controlDT, registry);
         }
      }
      else
      {
         taskSpacePositionControlState = new TaskspaceHandPositionControlState(namePrefix, HandControlState.TASK_SPACE_POSITION, momentumBasedController,
               jacobianId, chest, hand, yoGraphicsListRegistry, registry);
      }

      setupStateMachine();

      jointCurrentPositionMap = new LinkedHashMap<OneDoFJoint, Double>();
      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         jointCurrentPositionMap.put(oneDoFJoint, oneDoFJoint.getQ());
      }

      isExecutingHandStep = new BooleanYoVariable(namePrefix + "DoingHandstep", registry);

      parentRegistry.addChild(registry);
   }

   private void setupStateMachine()
   {
      addRequestedStateTransition(requestedState, false, jointSpaceHandControlState, jointSpaceHandControlState);
      addRequestedStateTransition(requestedState, false, jointSpaceHandControlState, taskSpacePositionControlState);
      addRequestedStateTransition(requestedState, false, jointSpaceHandControlState, loadBearingControlState);

      addRequestedStateTransition(requestedState, false, taskSpacePositionControlState, taskSpacePositionControlState);
      addRequestedStateTransition(requestedState, false, taskSpacePositionControlState, jointSpaceHandControlState);
      addRequestedStateTransition(requestedState, false, taskSpacePositionControlState, loadBearingControlState);

      addRequestedStateTransition(requestedState, false, loadBearingControlState, taskSpacePositionControlState);
      addRequestedStateTransition(requestedState, false, loadBearingControlState, jointSpaceHandControlState);

      setupTransitionToSupport(taskSpacePositionControlState);

      stateMachine.addState(jointSpaceHandControlState);
      stateMachine.addState(taskSpacePositionControlState);
      stateMachine.addState(loadBearingControlState);

      stateMachine.attachStateChangedListener(stateChangedlistener);
   }

   private void setupTransitionToSupport(final State<HandControlState> fromState)
   {
      StateTransitionCondition swingToSupportCondition = new StateTransitionCondition()
      {
         @Override
         public boolean checkCondition()
         {
            return isExecutingHandStep.getBooleanValue() && fromState.isDone();
         }
      };
      StateTransitionAction swingToSupportAction = new StateTransitionAction()
      {
         @Override
         public void doTransitionAction()
         {
            isExecutingHandStep.set(false);
            handSpatialAccelerationControlModule.setGains(taskspaceLoadBearingGains);
         }
      };
      StateTransition<HandControlState> swingToSupportTransition = new StateTransition<HandControlState>(HandControlState.LOAD_BEARING,
            swingToSupportCondition, swingToSupportAction);
      fromState.addStateTransition(swingToSupportTransition);
   }

   public void doControl()
   {
      stateMachine.checkTransitionConditions();
      stateMachine.doAction();

      checkAndSendHandPoseStatusIsCompleted();
   }

   private void checkAndSendHandPoseStatusIsCompleted()
   {
      boolean isExecutingHandPose = stateMachine.getCurrentStateEnum() == HandControlState.TASK_SPACE_POSITION;
      isExecutingHandPose |= stateMachine.getCurrentStateEnum() == HandControlState.JOINT_SPACE;

      if (handPoseStatusProducer != null && isExecutingHandPose && isDone() && !hasHandPoseStatusBeenSent.getBooleanValue())
      {
         handPoseStatusProducer.sendCompletedStatus(robotSide);
         hasHandPoseStatusBeenSent.set(true);
      }
   }

   public boolean isDone()
   {
      return stateMachine.getCurrentState().isDone();
   }

   public void executeTaskSpaceTrajectory(PoseTrajectoryGenerator poseTrajectory)
   {
      handSpatialAccelerationControlModule.setGains(taskspaceGains);
      taskSpacePositionControlState.setTrajectory(poseTrajectory, handSpatialAccelerationControlModule);
      requestedState.set(taskSpacePositionControlState.getStateEnum());
      stateMachine.checkTransitionConditions();
      isExecutingHandStep.set(false);

      if (handPoseStatusProducer != null)
         handPoseStatusProducer.sendStartedStatus(robotSide);
   }

   public void moveInStraightLine(FramePose finalDesiredPose, double time, ReferenceFrame trajectoryFrame, double swingClearance)
   {
      if (stateMachine.getCurrentStateEnum() != HandControlState.LOAD_BEARING)
      {
         FramePose pose = computeDesiredFramePose(trajectoryFrame);
         straightLinePoseTrajectoryGenerator.registerAndSwitchFrame(trajectoryFrame);
         straightLinePoseTrajectoryGenerator.setInitialPose(pose);
         straightLinePoseTrajectoryGenerator.setFinalPose(finalDesiredPose);
         straightLinePoseTrajectoryGenerator.setTrajectoryTime(time);
         executeTaskSpaceTrajectory(straightLinePoseTrajectoryGenerator);
      }
      else
      {
         loadBearingControlState.getContactNormalVector(initialDirection);
         moveAwayObject(finalDesiredPose, initialDirection, swingClearance, time, trajectoryFrame);
      }
   }

   private final FrameVector initialDirection = new FrameVector();
   private final FrameVector finalDirection = new FrameVector();


   public void moveTowardsObjectAndGoToSupport(FramePose finalDesiredPose, FrameVector surfaceNormal, double clearance, double time,
         ReferenceFrame trajectoryFrame, boolean goToSupportWhenDone, double holdPositionDuration)
   {
      finalDirection.setIncludingFrame(surfaceNormal);
      finalDirection.negate();

      moveTowardsObject(finalDesiredPose, finalDirection, clearance, time, trajectoryFrame);
      loadBearingControlState.setContactNormalVector(surfaceNormal);
      loadBearingControlState.setControlModule(handSpatialAccelerationControlModule);
      taskSpacePositionControlState.setHoldPositionDuration(holdPositionDuration);
      isExecutingHandStep.set(goToSupportWhenDone);
   }

   private void moveTowardsObject(FramePose finalDesiredPose, FrameVector finalDirection, double clearance, double time, ReferenceFrame trajectoryFrame)
   {
      if (stateMachine.getCurrentStateEnum() != HandControlState.LOAD_BEARING)
      {
         FramePose pose = computeDesiredFramePose(trajectoryFrame);
         finalApproachPoseTrajectoryGenerator.registerAndSwitchFrame(trajectoryFrame);
         finalApproachPoseTrajectoryGenerator.setInitialPose(pose);
         finalApproachPoseTrajectoryGenerator.setFinalPose(finalDesiredPose);
         finalApproachPoseTrajectoryGenerator.setTrajectoryTime(time);
         finalApproachPoseTrajectoryGenerator.setFinalApproach(finalDirection, clearance);
         executeTaskSpaceTrajectory(finalApproachPoseTrajectoryGenerator);
      }
      else
      {
         loadBearingControlState.getContactNormalVector(initialDirection);
         moveAwayObjectTowardsOtherObject(finalDesiredPose, initialDirection, finalDirection, clearance, time, trajectoryFrame);
      }
   }

   public void moveAwayObject(FramePose finalDesiredPose, FrameVector initialDirection, double clearance, double time, ReferenceFrame trajectoryFrame)
   {
      FramePose pose = computeDesiredFramePose(trajectoryFrame);
      initialClearancePoseTrajectoryGenerator.registerAndSwitchFrame(trajectoryFrame);
      initialClearancePoseTrajectoryGenerator.setInitialPose(pose);
      initialClearancePoseTrajectoryGenerator.setFinalPose(finalDesiredPose);
      initialClearancePoseTrajectoryGenerator.setInitialClearance(initialDirection, clearance);
      initialClearancePoseTrajectoryGenerator.setTrajectoryTime(time);
      executeTaskSpaceTrajectory(initialClearancePoseTrajectoryGenerator);
   }

   private void moveAwayObjectTowardsOtherObject(FramePose finalDesiredPose, FrameVector initialDirection, FrameVector finalDirection, double clearance,
         double time, ReferenceFrame trajectoryFrame)
   {
      FramePose pose = computeDesiredFramePose(trajectoryFrame);
      leadInOutPoseTrajectoryGenerator.registerAndSwitchFrame(trajectoryFrame);
      leadInOutPoseTrajectoryGenerator.setInitialLeadOut(pose, initialDirection, clearance);
      leadInOutPoseTrajectoryGenerator.setFinalLeadIn(finalDesiredPose, finalDirection, clearance);
      leadInOutPoseTrajectoryGenerator.setTrajectoryTime(time);
      executeTaskSpaceTrajectory(leadInOutPoseTrajectoryGenerator);
   }

   private FramePose computeDesiredFramePose(ReferenceFrame trajectoryFrame)
   {
      FramePose pose;
      if (stateMachine.getCurrentState() instanceof TaskspaceHandPositionControlState)
      {
         pose = taskSpacePositionControlState.getDesiredPose();
      }
      else
      {
         // FIXME: make this be based on desired joint angles
         pose = new FramePose(fullRobotModel.getHandControlFrame(robotSide));
      }

      pose.changeFrame(trajectoryFrame);
      return pose;
   }

   public void requestLoadBearing()
   {
      requestedState.set(loadBearingControlState.getStateEnum());
      loadBearingControlState.setControlModule(handSpatialAccelerationControlModule);
   }

   public void moveUsingQuinticSplines(Map<OneDoFJoint, Double> desiredJointPositions, double time)
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         if (!desiredJointPositions.containsKey(oneDoFJoints[i]))
            throw new RuntimeException("not all joint positions specified");
      }

      trajectoryTimeProvider.set(time);

      for (OneDoFJoint oneDoFJoint : desiredJointPositions.keySet())
      {
         quinticPolynomialTrajectoryGenerators.get(oneDoFJoint).setFinalPosition(desiredJointPositions.get(oneDoFJoint));
      }

      jointSpaceHandControlState.setTrajectories(quinticPolynomialTrajectoryGenerators);
      requestedState.set(jointSpaceHandControlState.getStateEnum());
      stateMachine.checkTransitionConditions();
      
      if (handPoseStatusProducer != null)
         handPoseStatusProducer.sendStartedStatus(robotSide);
   }

   @Deprecated
   public void moveJointspaceWithWaypoints(Map<OneDoFJoint, double[]> desiredJointPositions, double time)
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         if (!desiredJointPositions.containsKey(oneDoFJoints[i]))
            throw new RuntimeException("not all joint positions specified");
      }

      trajectoryTimeProvider.set(time);

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         waypointsPolynomialTrajectoryGenerators.get(oneDoFJoints[i]).setDesiredPositions(desiredJointPositions.get(oneDoFJoints[i]));
      }

      jointSpaceHandControlState.setTrajectories(waypointsPolynomialTrajectoryGenerators);
      requestedState.set(jointSpaceHandControlState.getStateEnum());
      stateMachine.checkTransitionConditions();
      
//      if (handPoseStatusProducer != null)
//         handPoseStatusProducer.sendStartedStatus(robotSide);
   }

   public void moveJointspaceWithWaypoints(RobotSide robotSide, double[] timeArray, double[][] positionArray, double[][] velocityArray)
   {
      if (positionArray.length != oneDoFJoints.length)
      {
         throw new RuntimeException("not all joint positions specified");
      }

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         wholeBodyWaypointsPolynomialTrajectoryGenerators.get(oneDoFJoints[i]).setWaypoints(timeArray, positionArray[i], velocityArray[i]);
      }

      jointSpaceHandControlState.setTrajectories(wholeBodyWaypointsPolynomialTrajectoryGenerators);
      requestedState.set(jointSpaceHandControlState.getStateEnum());
      stateMachine.checkTransitionConditions();
      
//      if (handPoseStatusProducer != null)
//         handPoseStatusProducer.sendStartedStatus(robotSide);
   }

   public boolean isLoadBearing()
   {
      return stateMachine.getCurrentStateEnum() == HandControlState.LOAD_BEARING;
   }

   public void holdPositionInBase()
   {
      FramePose currentDesiredHandPose = computeDesiredFramePose(chest.getBodyFixedFrame());

      holdPoseTrajectoryGenerator.registerAndSwitchFrame(chest.getBodyFixedFrame());
      holdPoseTrajectoryGenerator.setConstantPose(currentDesiredHandPose);

      executeTaskSpaceTrajectory(holdPoseTrajectoryGenerator);
   }

   public void holdPositionInJointSpace()
   {
      for (OneDoFJoint oneDoFJoint : oneDoFJoints)
      {
         jointCurrentPositionMap.put(oneDoFJoint, oneDoFJoint.getQ());
      }

      double epsilon = 1e-2;
      moveUsingQuinticSplines(jointCurrentPositionMap, epsilon);
   }

   public boolean isControllingPoseInWorld()
   {
      State<HandControlState> currentState = stateMachine.getCurrentState();

      if (currentState == taskSpacePositionControlState)
         return taskSpacePositionControlState.getReferenceFrame() == worldFrame;

      return false;
   }

   public RobotSide getRobotSide()
   {
      return robotSide;
   }
}
