package us.ihmc.atlas.jfxvisualizer;

import controller_msgs.msg.dds.REAStateRequestMessage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.commons.lang3.tuple.Pair;
import us.ihmc.atlas.AtlasRobotModel;
import us.ihmc.atlas.AtlasRobotVersion;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.avatar.networkProcessor.footstepPlanAndProcessModule.FootstepPlanAndProcessModule;
import us.ihmc.communication.IHMCRealtimeROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.footstepPlanning.FootstepPlannerOutput;
import us.ihmc.footstepPlanning.FootstepPlanningModule;
import us.ihmc.footstepPlanning.communication.FootstepPlannerMessagerAPI;
import us.ihmc.footstepPlanning.log.FootstepPlannerLogger;
import us.ihmc.footstepPlanning.ui.FootstepPlannerUI;
import us.ihmc.footstepPlanning.ui.RemoteUIMessageConverter;
import us.ihmc.javaFXToolkit.messager.SharedMemoryJavaFXMessager;
import us.ihmc.pubsub.DomainFactory;
import us.ihmc.robotEnvironmentAwareness.communication.REACommunicationProperties;
import us.ihmc.ros2.RealtimeRos2Node;

import java.util.HashMap;
import java.util.function.ToDoubleFunction;

/**
 * This class provides a visualizer for the footstep planner module.
 * It allows user to create plans, log and load plans from disk, tune parameters,
 * and debug plans.
 */
public class AtlasFootstepPlannerUI extends Application
{
   private static final Vector3D rootJointToMidFootOffset = new Vector3D(0.0518637, 0.0, -0.8959426);
   private static final boolean launchPlannerToolbox = true;
   private static final double GOAL_DISTANCE_PROXIMITY = 0.1;

   private SharedMemoryJavaFXMessager messager;
   private RemoteUIMessageConverter messageConverter;

   private FootstepPlannerUI ui;

   private FootstepPlanAndProcessModule planningAndProcessingModule;

   @Override
   public void start(Stage primaryStage) throws Exception
   {
      DRCRobotModel drcRobotModel = new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_NO_HANDS, RobotTarget.REAL_ROBOT, false);
      DRCRobotModel previewModel = new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_NO_HANDS, RobotTarget.REAL_ROBOT, false);
      messager = new SharedMemoryJavaFXMessager(FootstepPlannerMessagerAPI.API);

      RealtimeRos2Node ros2Node = ROS2Tools.createRealtimeRos2Node(DomainFactory.PubSubImplementation.FAST_RTPS, "ihmc_footstep_planner_ui");
      AtlasLowLevelMessenger robotLowLevelMessenger = new AtlasLowLevelMessenger(ros2Node, drcRobotModel.getSimpleRobotName());
      IHMCRealtimeROS2Publisher<REAStateRequestMessage> reaStateRequestPublisher
            = ROS2Tools.createPublisher(ros2Node, REAStateRequestMessage.class, REACommunicationProperties.subscriberTopicNameGenerator);
      messageConverter = new RemoteUIMessageConverter(ros2Node, messager, drcRobotModel.getSimpleRobotName());

      messager.startMessager();
      messager.submitMessage(FootstepPlannerMessagerAPI.GoalDistanceProximity, GOAL_DISTANCE_PROXIMITY);

      ui = FootstepPlannerUI.createMessagerUI(primaryStage,
                                              messager,
                                              drcRobotModel.getFootstepPlannerParameters(),
                                              drcRobotModel.getVisibilityGraphsParameters(),
                                              drcRobotModel.getFootstepPostProcessingParameters(),
                                              drcRobotModel,
                                              previewModel,
                                              drcRobotModel.getContactPointParameters(),
                                              drcRobotModel.getWalkingControllerParameters(),
                                              createDefaultJointAngleMap(),
                                              rootJointToMidFootOffset);
      ui.setRobotLowLevelMessenger(robotLowLevelMessenger);
      ui.setREAStateRequestPublisher(reaStateRequestPublisher);
      ui.show();

      if (launchPlannerToolbox)
      {
         planningAndProcessingModule = new FootstepPlanAndProcessModule(drcRobotModel, DomainFactory.PubSubImplementation.FAST_RTPS);
         FootstepPlanningModule planningModule = planningAndProcessingModule.getPlanningModule();

         // Create logger and connect to messager
         FootstepPlannerLogger logger = new FootstepPlannerLogger(planningModule);
         Runnable loggerRunnable = () -> logger.logSessionAndReportToMessager(messager);
         messager.registerTopicListener(FootstepPlannerMessagerAPI.RequestGenerateLog, b -> new Thread(loggerRunnable).start());

         // Automatically send graph data over messager
         planningModule.addStatusCallback(status -> handleMessagerCallbacks(planningModule, status));
      }
   }

   private static ToDoubleFunction<String> createDefaultJointAngleMap()
   {
      HashMap<String, Double> defaultJointAngleMap = new HashMap<>();

      defaultJointAngleMap.put("back_bky", -0.05);
      defaultJointAngleMap.put("l_leg_aky", 0.5 * -0.75);
      defaultJointAngleMap.put("r_leg_aky", 0.5 * -0.75);
      defaultJointAngleMap.put("l_leg_kny", 0.5 * 1.65);
      defaultJointAngleMap.put("r_leg_kny", 0.5 * 1.65);
      defaultJointAngleMap.put("back_bkz", 0.0);
      defaultJointAngleMap.put("back_bkx", 0.0);

      defaultJointAngleMap.put("l_leg_hpz", 0.0);
      defaultJointAngleMap.put("l_leg_hpx", 0.09);
      defaultJointAngleMap.put("l_leg_hpy", 0.5 * -0.933);
      defaultJointAngleMap.put("l_leg_akx", -0.09);
      defaultJointAngleMap.put("r_leg_hpz", 0.0);
      defaultJointAngleMap.put("r_leg_hpx", -0.09);
      defaultJointAngleMap.put("r_leg_hpy", 0.5 * -0.933);
      defaultJointAngleMap.put("r_leg_akx", 0.09);
      defaultJointAngleMap.put("l_arm_shz", 0.0);
      defaultJointAngleMap.put("l_arm_shx", -1.3);
      defaultJointAngleMap.put("l_arm_ely", 2.0);
      defaultJointAngleMap.put("l_arm_elx", 0.5);
      defaultJointAngleMap.put("l_arm_wry", 0.01);
      defaultJointAngleMap.put("l_arm_wrx", 0.0);
      defaultJointAngleMap.put("l_arm_wry2", 0.0);
      defaultJointAngleMap.put("r_arm_shz", 0.0);
      defaultJointAngleMap.put("r_arm_shx", 1.3);
      defaultJointAngleMap.put("r_arm_ely", 2.0);
      defaultJointAngleMap.put("r_arm_elx", -0.5);
      defaultJointAngleMap.put("r_arm_wry", 0.01);
      defaultJointAngleMap.put("r_arm_wrx", 0.0);
      defaultJointAngleMap.put("r_arm_wry2", 0.0);

      return jointName -> defaultJointAngleMap.getOrDefault(jointName, 0.0);
   }

   private void handleMessagerCallbacks(FootstepPlanningModule planningModule, FootstepPlannerOutput status)
   {
      if (status.getFootstepPlanningResult() != null && status.getFootstepPlanningResult().terminalResult())
      {
         messager.submitMessage(FootstepPlannerMessagerAPI.GraphData,
                                Pair.of(planningModule.getEdgeDataMap(), planningModule.getIterationData()));
         messager.submitMessage(FootstepPlannerMessagerAPI.StartVisibilityMap, planningModule.getBodyPathPlanner().getSolution().getStartMap());
         messager.submitMessage(FootstepPlannerMessagerAPI.GoalVisibilityMap, planningModule.getBodyPathPlanner().getSolution().getGoalMap());
         messager.submitMessage(FootstepPlannerMessagerAPI.InterRegionVisibilityMap, planningModule.getBodyPathPlanner().getSolution().getInterRegionVisibilityMap());
         messager.submitMessage(FootstepPlannerMessagerAPI.VisibilityMapWithNavigableRegionData, planningModule.getBodyPathPlanner().getSolution().getVisibilityMapsWithNavigableRegions());
      }
   }

   @Override
   public void stop() throws Exception
   {
      super.stop();

      messager.closeMessager();
      messageConverter.destroy();
      ui.stop();

      if (planningAndProcessingModule != null)
         planningAndProcessingModule.closeAndDispose();

      Platform.exit();
   }

   public static void main(String[] args)
   {
      launch(args);
   }
}