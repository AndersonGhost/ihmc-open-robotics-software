package us.ihmc.commonWalkingControlModules.controlModules.legConfiguration;

import us.ihmc.commonWalkingControlModules.configurations.LegConfigurationParameters;
import us.ihmc.commonWalkingControlModules.controlModules.legConfiguration.gains.LegConfigurationGainsReadOnly;
import us.ihmc.commonWalkingControlModules.controlModules.legConfiguration.states.*;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.InverseDynamicsCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.PrivilegedJointSpaceCommand;
import us.ihmc.commonWalkingControlModules.momentumBasedController.HighLevelHumanoidControllerToolbox;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FrameVector3DReadOnly;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.robotics.partNames.LegJointName;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.stateMachine.core.StateMachine;
import us.ihmc.robotics.stateMachine.factories.StateMachineFactory;
import us.ihmc.yoVariables.providers.DoubleProvider;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoEnum;

public class LegConfigurationControlModule
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoVariableRegistry registry;

   private final PrivilegedJointSpaceCommand privilegedAccelerationCommand = new PrivilegedJointSpaceCommand();

   private final YoEnum<LegConfigurationType> requestedState;

   private final StateMachine<LegConfigurationType, LegControlState> stateMachine;

   private final LegConfigurationController controller;

   private final YoDouble highPrivilegedWeight;
   private final YoDouble mediumPrivilegedWeight;
   private final YoDouble lowPrivilegedWeight;

   private final YoBoolean useFullyExtendedLeg;
   private final YoDouble desiredAngleWhenStraight;
   private final YoDouble desiredAngleWhenExtended;
   private final YoDouble desiredAngleWhenBracing;

   private final YoDouble collapsingDuration;
   private final YoDouble collapsingDurationFractionOfStep;

   private static final int hipPitchJointIndex = 0;
   private static final int kneePitchJointIndex = 1;
   private static final int anklePitchJointIndex = 2;

   private final LegConfigurationControlToolbox toolbox;

   public LegConfigurationControlModule(RobotSide robotSide, HighLevelHumanoidControllerToolbox controllerToolbox,
                                        LegConfigurationParameters legConfigurationParameters, YoVariableRegistry parentRegistry)
   {
      String sidePrefix = robotSide.getCamelCaseNameForStartOfExpression();
      String namePrefix = sidePrefix + "Leg";
      registry = new YoVariableRegistry(sidePrefix + getClass().getSimpleName());

      OneDoFJointBasics hipPitchJoint = controllerToolbox.getFullRobotModel().getLegJoint(robotSide, LegJointName.HIP_PITCH);
      OneDoFJointBasics kneePitchJoint = controllerToolbox.getFullRobotModel().getLegJoint(robotSide, LegJointName.KNEE_PITCH);
      OneDoFJointBasics anklePitchJoint = controllerToolbox.getFullRobotModel().getLegJoint(robotSide, LegJointName.ANKLE_PITCH);

      toolbox = new LegConfigurationControlToolbox(sidePrefix, hipPitchJoint, kneePitchJoint, anklePitchJoint, legConfigurationParameters, registry);

      privilegedAccelerationCommand.addJoint(hipPitchJoint, Double.NaN);
      privilegedAccelerationCommand.addJoint(kneePitchJoint, Double.NaN);
      privilegedAccelerationCommand.addJoint(anklePitchJoint, Double.NaN);

      highPrivilegedWeight = new YoDouble(sidePrefix + "HighPrivilegedWeight", registry);
      mediumPrivilegedWeight = new YoDouble(sidePrefix + "MediumPrivilegedWeight", registry);
      lowPrivilegedWeight = new YoDouble(sidePrefix + "LowPrivilegedWeight", registry);

      highPrivilegedWeight.set(legConfigurationParameters.getLegPrivilegedHighWeight());
      mediumPrivilegedWeight.set(legConfigurationParameters.getLegPrivilegedMediumWeight());
      lowPrivilegedWeight.set(legConfigurationParameters.getLegPrivilegedLowWeight());

      useFullyExtendedLeg = new YoBoolean(namePrefix + "UseFullyExtendedLeg", registry);

      desiredAngleWhenStraight = new YoDouble(namePrefix + "DesiredAngleWhenStraight", registry);
      desiredAngleWhenExtended = new YoDouble(namePrefix + "DesiredAngleWhenExtended", registry);
      desiredAngleWhenBracing = new YoDouble(namePrefix + "DesiredAngleWhenBracing", registry);
      desiredAngleWhenStraight.set(legConfigurationParameters.getKneeAngleWhenStraight());
      desiredAngleWhenExtended.set(legConfigurationParameters.getKneeAngleWhenExtended());
      desiredAngleWhenBracing.set(legConfigurationParameters.getKneeAngleWhenBracing());

      collapsingDuration = new YoDouble(namePrefix + "SupportKneeCollapsingDuration", registry);
      collapsingDurationFractionOfStep = new YoDouble(namePrefix + "SupportKneeCollapsingDurationFractionOfStep", registry);
      collapsingDurationFractionOfStep.set(legConfigurationParameters.getSupportKneeCollapsingDurationFractionOfStep());

      // set up states and state machine
      YoDouble time = controllerToolbox.getYoTime();
      requestedState = YoEnum.create(namePrefix + "RequestedState", "", LegConfigurationType.class, registry, true);
      requestedState.set(null);

      stateMachine = setupStateMachine(namePrefix, legConfigurationParameters.attemptToStraightenLegs(), time);
      controller = new LegConfigurationController(sidePrefix, toolbox, controllerToolbox.getFullRobotModel(), registry);

      parentRegistry.addChild(registry);
   }

   private StateMachine<LegConfigurationType, LegControlState> setupStateMachine(String namePrefix, boolean attemptToStraightenLegs,
                                                                                 DoubleProvider timeProvider)
   {
      StateMachineFactory<LegConfigurationType, LegControlState> factory = new StateMachineFactory<>(LegConfigurationType.class);
      factory.setNamePrefix(namePrefix).setRegistry(registry).buildYoClock(timeProvider);

      factory.addStateAndDoneTransition(LegConfigurationType.STRAIGHTEN, new StraighteningKneeControlState(namePrefix, toolbox, registry),
                                        LegConfigurationType.STRAIGHT);
      factory.addState(LegConfigurationType.STRAIGHT, new StraightKneeControlState(toolbox));
      factory.addState(LegConfigurationType.BENT, new BentKneeControlState(toolbox));
      factory.addState(LegConfigurationType.COLLAPSE, new CollapseKneeControlState(namePrefix, toolbox, collapsingDuration, registry));

      for (LegConfigurationType from : LegConfigurationType.values())
      {
         factory.addRequestedTransition(from, requestedState);
         factory.addRequestedTransition(from, from, requestedState);
      }

      return factory.build(attemptToStraightenLegs ? LegConfigurationType.STRAIGHT : LegConfigurationType.BENT);
   }

   public void setLegGains(LegConfigurationGainsReadOnly straightLegGains, LegConfigurationGainsReadOnly bentLegGains)
   {
      toolbox.setStraightLegGains(straightLegGains);
      toolbox.setBentLegGains(bentLegGains);
   }

   public void initialize()
   {
   }

   public void doControl()
   {
      toolbox.setDesiredStraightLegAngle(getDesiredStraightLegAngle());

      stateMachine.doActionAndTransition();

      LegControlState currentState = stateMachine.getCurrentState();

      controller.setKneePitchPrivilegedConfiguration(currentState.getKneePitchPrivilegedConfiguration());
      controller.setLegConfigurationGains(currentState.getLegConfigurationGains());
      controller.computeKneeAcceleration();

      double privilegedKneeAcceleration = controller.getKneeAcceleration();
      double privilegedHipPitchAcceleration = -0.5 * privilegedKneeAcceleration;
      double privilegedAnklePitchAcceleration = -0.5 * privilegedKneeAcceleration;

      privilegedAccelerationCommand.setOneDoFJoint(hipPitchJointIndex, privilegedHipPitchAcceleration);
      privilegedAccelerationCommand.setOneDoFJoint(kneePitchJointIndex, privilegedKneeAcceleration);
      privilegedAccelerationCommand.setOneDoFJoint(anklePitchJointIndex, privilegedAnklePitchAcceleration);

      double kneePitchPrivilegedConfigurationWeight = getWeight();
      privilegedAccelerationCommand.setWeight(hipPitchJointIndex, kneePitchPrivilegedConfigurationWeight);
      privilegedAccelerationCommand.setWeight(kneePitchJointIndex, kneePitchPrivilegedConfigurationWeight);
      privilegedAccelerationCommand.setWeight(anklePitchJointIndex, kneePitchPrivilegedConfigurationWeight);

      computeDesiredLegAcceleration();
   }

   public void setStepDuration(double stepDuration)
   {
      collapsingDuration.set(collapsingDurationFractionOfStep.getDoubleValue() * stepDuration);
   }

   public void setFullyExtendLeg(boolean fullyExtendLeg)
   {
      useFullyExtendedLeg.set(fullyExtendLeg);
   }

   public void prepareForLegBracing()
   {
      toolbox.setUseBracingAngle(true);
   }

   public void doNotBrace()
   {
      toolbox.setUseBracingAngle(false);
   }

   public void setLegControlWeight(LegControlWeight legControlWeight)
   {
      toolbox.setLegControlWeight(legControlWeight);
   }

   public void setKneeAngleState(LegConfigurationType controlType)
   {
      requestedState.set(controlType);
   }

   public LegConfigurationType getCurrentKneeControlState()
   {
      return stateMachine.getCurrentStateKey();
   }

   public InverseDynamicsCommand<?> getInverseDynamicsCommand()
   {
      return privilegedAccelerationCommand;
   }

   public double getWeight()
   {
      switch (toolbox.getLegControlWeight().getEnumValue())
      {
      case LOW:
         return lowPrivilegedWeight.getDoubleValue();
      case MEDIUM:
         return mediumPrivilegedWeight.getDoubleValue();
      default:
         return highPrivilegedWeight.getDoubleValue();
      }
   }

   public FrameVector3DReadOnly getDesiredLegAcceleration()
   {
      return legAcceleration;
   }

   private double getDesiredStraightLegAngle()
   {
      if (toolbox.useBracingAngle())
         return desiredAngleWhenBracing.getDoubleValue();
      else if (useFullyExtendedLeg.getBooleanValue())
         return desiredAngleWhenExtended.getDoubleValue();
      else
         return desiredAngleWhenStraight.getDoubleValue();
   }

   private final FramePoint3D hipPosition = new FramePoint3D();
   private final FramePoint3D anklePosition = new FramePoint3D();

   private final FrameVector3D legAcceleration = new FrameVector3D();

   private void computeDesiredLegAcceleration()
   {
      hipPosition.setToZero(toolbox.getHipPitchJoint().getFrameAfterJoint());
      anklePosition.setToZero(toolbox.getAnklePitchJoint().getFrameBeforeJoint());
      hipPosition.changeFrame(worldFrame);
      anklePosition.changeFrame(worldFrame);

      legAcceleration.sub(hipPosition, anklePosition);
      legAcceleration.normalize();
      legAcceleration.scale(controller.getKneeAcceleration());
   }
}
