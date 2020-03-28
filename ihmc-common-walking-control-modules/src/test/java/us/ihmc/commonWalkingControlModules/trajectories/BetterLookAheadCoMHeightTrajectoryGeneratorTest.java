package us.ihmc.commonWalkingControlModules.trajectories;

import com.jme3.animation.Pose;
import org.junit.jupiter.api.Test;
import us.ihmc.commonWalkingControlModules.desiredFootStep.NewTransferToAndNextFootstepsData;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.yoVariables.registry.YoVariableRegistry;

public class BetterLookAheadCoMHeightTrajectoryGeneratorTest
{
   private static final double minimumHeight = 0.75;
   private static final double nominalHeight = 0.8;
   private static final double maximumHeight = 0.95;
   private static final double doubleSupportIn = 0.3;

   @Test
   public void testFlat()
   {
      NewTransferToAndNextFootstepsData transferToAndNextFootstepsData = new NewTransferToAndNextFootstepsData();
      FramePoint3D startCoM = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.0, nominalHeight);
      FramePoint3D start = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.125, 0.0);
      FramePoint3D end = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.5, -0.125, 0.0);

      transferToAndNextFootstepsData.setTransferToPosition(end);

      runTest(start, startCoM, RobotSide.RIGHT, transferToAndNextFootstepsData);
   }

   @Test
   public void testBigSteppingDown()
   {
      double stepDownHeight = -0.4;

      NewTransferToAndNextFootstepsData transferToAndNextFootstepsData = new NewTransferToAndNextFootstepsData();
      FramePoint3D startCoM = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.0, -stepDownHeight + nominalHeight);
      FramePoint3D start = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.125, -stepDownHeight);
      FramePoint3D end = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.2, -0.125, 0.0);

      transferToAndNextFootstepsData.setTransferToPosition(end);

      runTest(start, startCoM, RobotSide.RIGHT, transferToAndNextFootstepsData);
   }

   @Test
   public void testSteppingDown()
   {
      double stepDownHeight = -0.2;

      NewTransferToAndNextFootstepsData transferToAndNextFootstepsData = new NewTransferToAndNextFootstepsData();
      FramePoint3D startCoM = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.0, -stepDownHeight + nominalHeight);
      FramePoint3D start = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.125, -stepDownHeight);
      FramePoint3D end = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.2, -0.125, 0.0);

      transferToAndNextFootstepsData.setTransferToPosition(end);

      runTest(start, startCoM, RobotSide.RIGHT, transferToAndNextFootstepsData);
   }

   @Test
   public void testSteppingUp()
   {
      double stepHeight = 0.2;

      NewTransferToAndNextFootstepsData transferToAndNextFootstepsData = new NewTransferToAndNextFootstepsData();
      FramePoint3D startCoM = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.0, nominalHeight);
      FramePoint3D start = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.0, 0.125, 0.0);
      FramePoint3D end = new FramePoint3D(ReferenceFrame.getWorldFrame(), 0.2, -0.125, stepHeight);

      transferToAndNextFootstepsData.setTransferToPosition(end);

      runTest(start, startCoM, RobotSide.RIGHT, transferToAndNextFootstepsData);
   }

   private void runTest(FramePoint3DReadOnly startFoot, FramePoint3DReadOnly startPosition,
                        RobotSide stepSide, NewTransferToAndNextFootstepsData transferToAndNextFootstepsData)
   {
      YoGraphicsListRegistry graphicsListRegistry = new YoGraphicsListRegistry();
      YoVariableRegistry registry = new YoVariableRegistry("test");

      SimulationConstructionSet scs = new SimulationConstructionSet();
      Robot robot = new Robot("dummy");
      scs.setRobot(robot);
      scs.getRootRegistry().addChild(registry);


      SideDependentList<PoseReferenceFrame> soleFrames = new SideDependentList<>();
      for (RobotSide robotSide : RobotSide.values)
      {
         soleFrames.put(robotSide, new PoseReferenceFrame(robotSide.getLowerCaseName() + "Frame", ReferenceFrame.getWorldFrame()));
      }
      PoseReferenceFrame comFrame = new PoseReferenceFrame("comFrame", ReferenceFrame.getWorldFrame());
      BetterLookAheadCoMHeightTrajectoryGenerator lookAhead = new BetterLookAheadCoMHeightTrajectoryGenerator(minimumHeight,
                                                                                                              nominalHeight,
                                                                                                              maximumHeight,
                                                                                                              0.0,
                                                                                                              doubleSupportIn,
                                                                                                              comFrame,
                                                                                                              soleFrames,
                                                                                                              robot.getYoTime(),
                                                                                                              graphicsListRegistry,
                                                                                                              registry);
      scs.addYoGraphicsListRegistry(graphicsListRegistry);


      scs.startOnAThread();

      RobotSide supportSide = stepSide.getOppositeSide();

      soleFrames.get(supportSide).setPositionAndUpdate(startFoot);
      comFrame.setPositionAndUpdate(startPosition);

      lookAhead.reset();
      lookAhead.setSupportLeg(supportSide);
      lookAhead.initialize(transferToAndNextFootstepsData, 0.0);

      scs.tickAndUpdate();

      ThreadTools.sleepForever();
   }
}