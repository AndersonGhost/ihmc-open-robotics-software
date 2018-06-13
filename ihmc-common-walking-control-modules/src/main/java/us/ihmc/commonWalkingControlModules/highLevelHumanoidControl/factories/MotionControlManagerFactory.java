package us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gnu.trove.map.hash.TObjectDoubleHashMap;
import us.ihmc.commonWalkingControlModules.configurations.JumpControllerParameters;
import us.ihmc.commonWalkingControlModules.configurations.ParameterTools;
import us.ihmc.commonWalkingControlModules.controlModules.flight.ControlManagerInterface;
import us.ihmc.commonWalkingControlModules.controlModules.rigidBody.RigidBodyControlManager;
import us.ihmc.commonWalkingControlModules.controlModules.rigidBody.RigidBodyControlMode;
import us.ihmc.commonWalkingControlModules.controllerCore.command.feedbackController.FeedbackControlCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.feedbackController.FeedbackControlCommandList;
import us.ihmc.commonWalkingControlModules.momentumBasedController.HighLevelHumanoidControllerToolbox;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.MomentumOptimizationSettings;
import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.robotics.controllers.pidGains.PID3DGainsReadOnly;
import us.ihmc.robotics.controllers.pidGains.PIDGainsReadOnly;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.yoVariables.providers.DoubleProvider;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class MotionControlManagerFactory
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final YoVariableRegistry momentumRegistry = new YoVariableRegistry("MomentumRegistry");

   private final JumpControllerParameters jumpControllerParameters;
   private final MomentumOptimizationSettings momentumOptimizationSettings;
   private HighLevelHumanoidControllerToolbox controllerToolbox;

   private final List<ControlManagerInterface> controlManagers = new ArrayList<>();
   private final Map<String, RigidBodyControlManager> rigidBodyManagerMapByBodyName = new HashMap<>();
   private final Map<String, PID3DGainsReadOnly> taskspaceOrientationGainMap = new HashMap<>();
   private final Map<String, PID3DGainsReadOnly> taskspacePositionGainMap = new HashMap<>();
   private final Map<String, Vector3DReadOnly> taskspaceAngularWeightMap = new HashMap<>();
   private final Map<String, Vector3DReadOnly> taskspaceLinearWeightMap = new HashMap<>();
   private final Map<String, DoubleProvider> jointspaceWeightMap = new HashMap<>();
   private final Map<String, PIDGainsReadOnly> jointGainMap = new HashMap<>();
   private final Map<String, DoubleProvider> userModeWeightMap = new HashMap<>();

   public MotionControlManagerFactory(JumpControllerParameters jumpControllerParameters, YoVariableRegistry parentRegistry)
   {
      this.jumpControllerParameters = jumpControllerParameters;
      this.momentumOptimizationSettings = jumpControllerParameters.getMomentumOptimizationSettings();

      ParameterTools.extractJointGainMap(jumpControllerParameters.getJointSpaceControlGains(), jointGainMap, registry);
      ParameterTools.extract3DGainMap("Orientation", jumpControllerParameters.getTaskspaceOrientationControlGains(), taskspaceOrientationGainMap, registry);
      ParameterTools.extract3DGainMap("Position", jumpControllerParameters.getTaskspacePositionControlGains(), taskspacePositionGainMap, registry);
      ParameterTools.extractJointWeightMap("JointspaceWeight", momentumOptimizationSettings.getJointspaceWeights(), jointspaceWeightMap, momentumRegistry);
      ParameterTools.extractJointWeightMap("UserModeWeight", momentumOptimizationSettings.getUserModeWeights(), userModeWeightMap, momentumRegistry);
      ParameterTools.extract3DWeightMap("AngularWeight", momentumOptimizationSettings.getTaskspaceAngularWeights(), taskspaceAngularWeightMap,
                                        momentumRegistry);
      ParameterTools.extract3DWeightMap("LinearWeight", momentumOptimizationSettings.getTaskspaceLinearWeights(), taskspaceLinearWeightMap, momentumRegistry);

      parentRegistry.addChild(momentumRegistry);
      parentRegistry.addChild(registry);
   }

   public RigidBodyControlManager getOrCreateRigidBodyManager(RigidBody bodyToControl, RigidBody baseBody, ReferenceFrame controlFrame,
                                                              ReferenceFrame baseFrame, Collection<ReferenceFrame> trajectoryFrames)
   {
      if (bodyToControl == null)
         return null;

      String bodyName = bodyToControl.getName();
      if (rigidBodyManagerMapByBodyName.containsKey(bodyName))
      {
         RigidBodyControlManager manager = rigidBodyManagerMapByBodyName.get(bodyName);
         if (manager != null)
            return manager;
      }

      if (!hasJumpControllerParameters(RigidBodyControlManager.class))
         return null;
      if (!hasMomentumOptimizationSettings(RigidBodyControlManager.class))
         return null;
      if (!hasHighLevelHumanoidControllerToolbox(RigidBodyControlManager.class))
         return null;

      // Gains
      PID3DGainsReadOnly taskspaceOrientationGains = taskspaceOrientationGainMap.get(bodyName);
      PID3DGainsReadOnly taskspacePositionGains = taskspacePositionGainMap.get(bodyName);

      // Weights
      Vector3DReadOnly taskspaceAngularWeight = taskspaceAngularWeightMap.get(bodyName);
      Vector3DReadOnly taskspaceLinearWeight = taskspaceLinearWeightMap.get(bodyName);

      TObjectDoubleHashMap<String> homeConfiguration = jumpControllerParameters.getOrCreateJointHomeConfiguration();
      Pose3D homePose = jumpControllerParameters.getOrCreateBodyHomeConfiguration().get(bodyName);
      RigidBody elevator = controllerToolbox.getFullRobotModel().getElevator();
      YoDouble yoTime = controllerToolbox.getYoTime();

      ContactablePlaneBody contactableBody = controllerToolbox.getContactableBody(bodyToControl);
      YoGraphicsListRegistry graphicsListRegistry = controllerToolbox.getYoGraphicsListRegistry();
      RigidBodyControlMode defaultControlMode = jumpControllerParameters.getDefaultControlModesForRigidBodies().get(bodyName);

      RigidBodyControlManager manager = new RigidBodyControlManager(bodyToControl, baseBody, baseBody, homeConfiguration, homePose, trajectoryFrames,
                                                                    controlFrame, baseFrame, contactableBody, defaultControlMode, yoTime, graphicsListRegistry,
                                                                    registry);
      manager.setGains(jointGainMap, taskspaceOrientationGains, taskspacePositionGains);
      manager.setWeights(jointspaceWeightMap, taskspaceAngularWeight, taskspaceLinearWeight, userModeWeightMap);

      rigidBodyManagerMapByBodyName.put(bodyName, manager);
      return manager;
   }

   private boolean hasHighLevelHumanoidControllerToolbox(Class<?> managerClass)
   {
      if (controllerToolbox != null)
         return true;
      missingObjectWarning(HighLevelHumanoidControllerToolbox.class, managerClass);
      return false;
   }

   private boolean hasJumpControllerParameters(Class<?> managerClass)
   {
      if (jumpControllerParameters != null)
         return true;
      missingObjectWarning(JumpControllerParameters.class, managerClass);
      return false;
   }

   private boolean hasMomentumOptimizationSettings(Class<?> managerClass)
   {
      if (momentumOptimizationSettings != null)
         return true;
      missingObjectWarning(MomentumOptimizationSettings.class, managerClass);
      return false;
   }

   private void missingObjectWarning(Class<?> missingObjectClass, Class<?> managerClass)
   {
      PrintTools.warn(this, missingObjectClass.getSimpleName() + " has not been set, cannot create: " + managerClass.getSimpleName());
   }

   public FeedbackControlCommandList createFeedbackControlTemplate()
   {
      FeedbackControlCommandList templateFeedbackCommandList = new FeedbackControlCommandList();
      Collection<RigidBodyControlManager> bodyManagers = rigidBodyManagerMapByBodyName.values();
      for (RigidBodyControlManager bodyManager : bodyManagers)
      {
         if (bodyManager != null)
            templateFeedbackCommandList.addCommand(bodyManager.createFeedbackControlTemplate());
      }
      for (ControlManagerInterface controlManager : controlManagers)
      {
         FeedbackControlCommand<?> feedbackControlCommand = controlManager.createFeedbackControlTemplate();
         if (feedbackControlCommand != null)
         {
            templateFeedbackCommandList.addCommand(feedbackControlCommand);
         }
      }
      return templateFeedbackCommandList;
   }

   public void setHighLevelHumanoidControllerToolbox(HighLevelHumanoidControllerToolbox controllerToolbox)
   {
      this.controllerToolbox = controllerToolbox;
   }
}