package us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.ICPGeneration;

import java.util.ArrayList;
import java.util.List;

import gnu.trove.list.array.TIntArrayList;
import us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.CMPGeneration.ReferenceCMPTrajectoryGenerator;
import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFramePoint3DBasics;
import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.euclid.referenceFrame.interfaces.FrameTuple3DBasics;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsList;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.ArtifactList;
import us.ihmc.robotics.math.trajectories.FrameTrajectory3D;
import us.ihmc.robotics.math.trajectories.PositionTrajectoryGenerator;
import us.ihmc.robotics.math.trajectories.SegmentedFrameTrajectory3D;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoFramePoint3D;
import us.ihmc.yoVariables.variable.YoFrameVector3D;
import us.ihmc.yoVariables.variable.YoInteger;

import javax.management.relation.RoleUnresolved;

/**
 * @author Tim Seyde
 */

public class ReferenceICPTrajectoryGenerator implements PositionTrajectoryGenerator
{
   private final static boolean CONTINUOUSLY_ADJUST_FOR_ICP_DISCONTINUITY = true;

   private final static int FIRST_SEGMENT = 0;

   private final static int defaultSize = 300;

   private static final int maxNumberOfSegments = 35;
   private final static double POINT_SIZE = 0.005;
   private static final boolean VISUALIZE = true;

   private final boolean debug;
   private final boolean visualize;

   private final List<FramePoint3D> cmpDesiredFinalPositions = new ArrayList<>();

   private final List<FramePoint3D> icpDesiredInitialPositions = new ArrayList<>();
   private final List<FramePoint3D> icpDesiredFinalPositions = new ArrayList<>();

   private final List<FramePoint3D> icpDesiredInitialPositionsFromCoPs = new ArrayList<>();
   private final List<FramePoint3D> icpDesiredFinalPositionsFromCoPs = new ArrayList<>();

   private final FramePoint3D icpPositionDesiredCurrent = new FramePoint3D();
   private final FrameVector3D icpVelocityDesiredCurrent = new FrameVector3D();
   private final FrameVector3D icpAccelerationDesiredCurrent = new FrameVector3D();
   private final FrameVector3D icpVelocityDynamicsCurrent = new FrameVector3D();
   private final YoFrameVector3D yoICPVelocityDynamicsCurrent;

   private final FramePoint3D icpPositionDesiredFinalCurrentSegment = new FramePoint3D();
   private final FramePoint3D icpPositionDesiredTerminal = new FramePoint3D();

   private final YoBoolean isInitialTransfer;

   private final YoBoolean continuouslyAdjustForICPContinuity;
   private final YoBoolean areICPDynamicsSatisfied;

   private final YoInteger totalNumberOfCMPSegments;
   private final YoInteger numberOfFootstepsToConsider;
   private final YoInteger currentSegmentIndex;

   private final YoDouble startTimeOfCurrentPhase;
   private final YoDouble localTimeInCurrentPhase;
   private final YoDouble omega0;

   private int numberOfFootstepsRegistered;

   private final List<FrameTrajectory3D> copTrajectories = new ArrayList<>();
   private final List<FrameTrajectory3D> cmpTrajectories = new ArrayList<>();

   private final TIntArrayList icpPhaseExitCornerPointIndices = new TIntArrayList();
   private final TIntArrayList icpPhaseEntryCornerPointIndices = new TIntArrayList();

   private final SmoothCapturePointToolbox icpToolbox = new SmoothCapturePointToolbox();
   private List<FrameTuple3DBasics> icpQuantityCalculatedInitialConditionList = new ArrayList<>();
   private List<FrameTuple3DBasics> icpQuantitySetInitialConditionList = new ArrayList<>();

   private final SmoothCapturePointAdjustmentToolbox icpAdjustmentToolbox = new SmoothCapturePointAdjustmentToolbox(icpToolbox);

   private final List<YoFramePoint3D> icpWaypoints = new ArrayList<>();


   public ReferenceICPTrajectoryGenerator(String namePrefix, YoDouble omega0,
                                          YoInteger numberOfFootstepsToConsider,
                                          YoBoolean isInitialTransfer, boolean debug, YoVariableRegistry registry,
                                          YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      this.omega0 = omega0;
      this.numberOfFootstepsToConsider = numberOfFootstepsToConsider;
      this.isInitialTransfer = isInitialTransfer;
      this.debug = debug;
      this.visualize = VISUALIZE && yoGraphicsListRegistry != null;

      areICPDynamicsSatisfied = new YoBoolean(namePrefix + "AreICPDynamicsSatisfied", registry);
      areICPDynamicsSatisfied.set(false);

      continuouslyAdjustForICPContinuity = new YoBoolean(namePrefix + "ContinuouslyAdjustForICPContinuity", registry);
      continuouslyAdjustForICPContinuity.set(CONTINUOUSLY_ADJUST_FOR_ICP_DISCONTINUITY);

      totalNumberOfCMPSegments = new YoInteger(namePrefix + "TotalNumberOfICPSegments", registry);

      startTimeOfCurrentPhase = new YoDouble(namePrefix + "StartTimeCurrentPhase", registry);
      localTimeInCurrentPhase = new YoDouble(namePrefix + "LocalTimeCurrentPhase", registry);
      localTimeInCurrentPhase.set(0.0);

      yoICPVelocityDynamicsCurrent = new YoFrameVector3D(namePrefix + "ICPVelocityDynamics", ReferenceFrame.getWorldFrame(), registry);

      currentSegmentIndex = new YoInteger(namePrefix + "CurrentSegment", registry);

      for (int i = 0; i < defaultSize; i++)
      {
         icpDesiredInitialPositions.add(new FramePoint3D());
         icpDesiredFinalPositions.add(new FramePoint3D());

         icpDesiredInitialPositionsFromCoPs.add(new FramePoint3D());
         icpDesiredFinalPositionsFromCoPs.add(new FramePoint3D());

         cmpDesiredFinalPositions.add(new FramePoint3D());
      }

      icpQuantityCalculatedInitialConditionList.add(new FramePoint3D());
      while (icpQuantityCalculatedInitialConditionList.size() < defaultSize)
      {
         icpQuantityCalculatedInitialConditionList.add(new FrameVector3D());
      }

      icpQuantitySetInitialConditionList.add(new FramePoint3D());
      while (icpQuantitySetInitialConditionList.size() < defaultSize)
      {
         icpQuantitySetInitialConditionList.add(new FrameVector3D());
      }

      if (visualize)
      {
         ArtifactList icpWaypointList = new ArtifactList("ICP Waypoints");
         YoFramePoint3D waypointStart = new YoFramePoint3D("ICPWaypoint" + 0, ReferenceFrame.getWorldFrame(), registry);

         YoGraphicPosition waypointStartViz = new YoGraphicPosition("ICP Waypoint" + 0, waypointStart, POINT_SIZE, YoAppearance.Blue(), YoGraphicPosition.GraphicType.DIAMOND_WITH_CROSS);

         icpWaypointList.add(waypointStartViz.createArtifact());
         icpWaypoints.add(waypointStart);

         for (int i = 0; i < defaultSize; i++)
         {
            YoFramePoint3D waypoint = new YoFramePoint3D("ICPWaypoint" + i + 1, ReferenceFrame.getWorldFrame(), registry);

            YoGraphicPosition waypointViz = new YoGraphicPosition("ICP Waypoint" + i + 1, waypoint, POINT_SIZE, YoAppearance.Blue(), YoGraphicPosition.GraphicType.DIAMOND_WITH_CROSS);

            icpWaypointList.add(waypointViz.createArtifact());

            icpWaypoints.add(waypoint);
         }

         yoGraphicsListRegistry.registerArtifactList(icpWaypointList);
      }
   }

   public void setNumberOfRegisteredSteps(int numberOfFootstepsRegistered)
   {
      this.numberOfFootstepsRegistered = numberOfFootstepsRegistered;
   }

   public void reset()
   {
      cmpTrajectories.clear();
      totalNumberOfCMPSegments.set(0);
      localTimeInCurrentPhase.set(0.0);

      icpPhaseEntryCornerPointIndices.resetQuick();
      icpPhaseExitCornerPointIndices.resetQuick();
   }

   public void resetCoPs()
   {
      copTrajectories.clear();
   }


   private void getICPInitialConditionsForAdjustment(double time, int currentSwingSegment)
   {
      getICPInitialConditionsForAdjustment(time, icpDesiredFinalPositionsFromCoPs, copTrajectories, currentSwingSegment, omega0.getDoubleValue());
   }

   private void getICPInitialConditionsForAdjustment(double localTime, List<FramePoint3D> exitCornerPointsFromCoPs, List<FrameTrajectory3D> copPolynomials3D,
                                                    int currentSwingSegment, double omega0)
   {
      if (currentSwingSegment < 0)
      {
         FrameTrajectory3D copPolynomial3D = copPolynomials3D.get(0);
         for (int i = 0; i < copPolynomials3D.get(0).getNumberOfCoefficients() / 2; i++)
         {
            FrameTuple3DBasics icpQuantityInitialCondition = icpQuantityCalculatedInitialConditionList.get(i);

            copPolynomial3D.getDerivative(i, localTime, icpQuantityInitialCondition);
         }
      }
      else
      {
         FrameTrajectory3D copPolynomial3D = copPolynomials3D.get(currentSwingSegment);
         for (int i = 0; i < copPolynomials3D.get(0).getNumberOfCoefficients() / 2; i++)
         {
            FrameTuple3DBasics icpQuantityInitialCondition = icpQuantityCalculatedInitialConditionList.get(i);

            icpToolbox.calculateICPQuantityFromCorrespondingCMPPolynomial3D(omega0, localTime, i, copPolynomial3D,
                                                                            exitCornerPointsFromCoPs.get(currentSwingSegment), icpQuantityInitialCondition);
         }
      }
   }

   public void initializeForTransfer(double initialTime, List<? extends SegmentedFrameTrajectory3D> transferCMPTrajectories,
                                     List<? extends SegmentedFrameTrajectory3D> swingCMPTrajectories)
   {
      reset();
      startTimeOfCurrentPhase.set(initialTime);

      icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

      int numberOfSteps = Math.min(numberOfFootstepsRegistered, numberOfFootstepsToConsider.getIntegerValue());
      for (int stepIndex = 0; stepIndex < numberOfSteps; stepIndex++)
      {
         SegmentedFrameTrajectory3D transferCMPTrajectory = transferCMPTrajectories.get(stepIndex);
         int cmpSegments = transferCMPTrajectory.getNumberOfSegments();
         for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
         {
            cmpTrajectories.add(transferCMPTrajectory.getSegment(cmpSegment));
            totalNumberOfCMPSegments.increment();
         }
         icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
         icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

         SegmentedFrameTrajectory3D swingCMPTrajectory = swingCMPTrajectories.get(stepIndex);
         cmpSegments = swingCMPTrajectory.getNumberOfSegments();
         for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
         {
            cmpTrajectories.add(swingCMPTrajectory.getSegment(cmpSegment));
            totalNumberOfCMPSegments.increment();
         }
         icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
         icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
      }

      SegmentedFrameTrajectory3D transferCMPTrajectory = transferCMPTrajectories.get(numberOfSteps);
      int cmpSegments = transferCMPTrajectory.getNumberOfSegments();
      for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
      {
         cmpTrajectories.add(transferCMPTrajectory.getSegment(cmpSegment));
         totalNumberOfCMPSegments.increment();
      }
      icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

      initialize();
   }

   public void initializeForSwing(double initialTime, List<? extends SegmentedFrameTrajectory3D> transferCMPTrajectories,
                                  List<? extends SegmentedFrameTrajectory3D> swingCMPTrajectories)
   {
      reset();
      startTimeOfCurrentPhase.set(initialTime);

      icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

      SegmentedFrameTrajectory3D swingCMPTrajectory = swingCMPTrajectories.get(0);
      int cmpSegments = swingCMPTrajectory.getNumberOfSegments();
      for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
      {
         cmpTrajectories.add(swingCMPTrajectory.getSegment(cmpSegment));
         totalNumberOfCMPSegments.increment();
      }
      icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
      icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

      int numberOfSteps = Math.min(numberOfFootstepsRegistered, numberOfFootstepsToConsider.getIntegerValue());
      for (int stepIndex = 1; stepIndex < numberOfSteps; stepIndex++)
      {
         SegmentedFrameTrajectory3D transferCMPTrajectory = transferCMPTrajectories.get(stepIndex);
         cmpSegments = transferCMPTrajectory.getNumberOfSegments();
         for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
         {
            cmpTrajectories.add(transferCMPTrajectory.getSegment(cmpSegment));
            totalNumberOfCMPSegments.increment();
         }
         icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
         icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

         swingCMPTrajectory = swingCMPTrajectories.get(stepIndex);
         cmpSegments = swingCMPTrajectory.getNumberOfSegments();
         for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
         {
            cmpTrajectories.add(swingCMPTrajectory.getSegment(cmpSegment));
            totalNumberOfCMPSegments.increment();
         }
         icpPhaseEntryCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
         icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());
      }

      SegmentedFrameTrajectory3D transferCMPTrajectory = transferCMPTrajectories.get(numberOfSteps);
      cmpSegments = transferCMPTrajectory.getNumberOfSegments();
      for (int cmpSegment = 0; cmpSegment < cmpSegments; cmpSegment++)
      {
         cmpTrajectories.add(transferCMPTrajectory.getSegment(cmpSegment));
         totalNumberOfCMPSegments.increment();
      }
      icpPhaseExitCornerPointIndices.add(totalNumberOfCMPSegments.getIntegerValue());

      initialize();
   }

   public void initializeForTransferFromCoPs(List<? extends SegmentedFrameTrajectory3D> transferCoPTrajectories,
                                             List<? extends SegmentedFrameTrajectory3D> swingCoPTrajectories)
   {
      resetCoPs();

      int numberOfSteps = Math.min(numberOfFootstepsRegistered, numberOfFootstepsToConsider.getIntegerValue());
      for (int stepIndex = 0; stepIndex < numberOfSteps; stepIndex++)
      {
         SegmentedFrameTrajectory3D transferCoPTrajectory = transferCoPTrajectories.get(stepIndex);
         int copSegments = transferCoPTrajectory.getNumberOfSegments();
         for (int copSegment = 0; copSegment < copSegments; copSegment++)
         {
            copTrajectories.add(transferCoPTrajectory.getSegment(copSegment));
         }

         SegmentedFrameTrajectory3D swingCoPTrajectory = swingCoPTrajectories.get(stepIndex);
         copSegments = swingCoPTrajectory.getNumberOfSegments();
         for (int copSegment = 0; copSegment < copSegments; copSegment++)
         {
            copTrajectories.add(swingCoPTrajectory.getSegment(copSegment));
         }
      }

      SegmentedFrameTrajectory3D transferCoPTrajectory = transferCoPTrajectories.get(numberOfSteps);
      int copSegments = transferCoPTrajectory.getNumberOfSegments();
      for (int copSegment = 0; copSegment < copSegments; copSegment++)
      {
         copTrajectories.add(transferCoPTrajectory.getSegment(copSegment));
      }

      icpToolbox.computeDesiredCornerPoints(icpDesiredInitialPositionsFromCoPs, icpDesiredFinalPositionsFromCoPs, copTrajectories, omega0.getDoubleValue());

      if(isInitialTransfer.getBooleanValue())
      {
         getICPInitialConditionsForAdjustment(localTimeInCurrentPhase.getDoubleValue(), -1);
         setInitialConditionsForAdjustment();
      }
   }

   public void initializeForSwingFromCoPs(List<? extends SegmentedFrameTrajectory3D> transferCoPTrajectories,
                                          List<? extends SegmentedFrameTrajectory3D> swingCoPTrajectories)
   {
      resetCoPs();

      SegmentedFrameTrajectory3D swingCoPTrajectory = swingCoPTrajectories.get(0);
      int copSegments = swingCoPTrajectory.getNumberOfSegments();
      for (int copSegment = 0; copSegment < copSegments; copSegment++)
      {
         copTrajectories.add(swingCoPTrajectory.getSegment(copSegment));
      }

      int numberOfSteps = Math.min(numberOfFootstepsRegistered, numberOfFootstepsToConsider.getIntegerValue());
      for (int stepIndex = 1; stepIndex < numberOfSteps; stepIndex++)
      {
         SegmentedFrameTrajectory3D transferCoPTrajectory = transferCoPTrajectories.get(stepIndex);
         copSegments = transferCoPTrajectory.getNumberOfSegments();
         for (int copSegment = 0; copSegment < copSegments; copSegment++)
         {
            copTrajectories.add(transferCoPTrajectory.getSegment(copSegment));
         }

         swingCoPTrajectory = swingCoPTrajectories.get(stepIndex);
         copSegments = swingCoPTrajectory.getNumberOfSegments();
         for (int copSegment = 0; copSegment < copSegments; copSegment++)
         {
            copTrajectories.add(swingCoPTrajectory.getSegment(copSegment));
         }
      }

      SegmentedFrameTrajectory3D transferCoPTrajectory = transferCoPTrajectories.get(numberOfSteps);
      copSegments = transferCoPTrajectory.getNumberOfSegments();
      for (int copSegment = 0; copSegment < copSegments; copSegment++)
      {
         copTrajectories.add(transferCoPTrajectory.getSegment(copSegment));
      }

      icpToolbox.computeDesiredCornerPoints(icpDesiredInitialPositionsFromCoPs, icpDesiredFinalPositionsFromCoPs, copTrajectories, omega0.getDoubleValue());
   }

   @Override
   public void initialize()
   {
      if (isInitialTransfer.getBooleanValue())
      {
         FrameTrajectory3D cmpPolynomial3D = cmpTrajectories.get(0);
         cmpPolynomial3D.compute(cmpPolynomial3D.getInitialTime());
      }

      icpToolbox.computeDesiredCornerPoints(icpDesiredInitialPositions, icpDesiredFinalPositions, cmpTrajectories, omega0.getDoubleValue());

      icpPositionDesiredTerminal.set(icpDesiredFinalPositions.get(cmpTrajectories.size() - 1));
   }


   public void setInitialConditionsForAdjustment()
   {
      for(int i = 0; i < icpQuantityCalculatedInitialConditionList.size() && i < icpQuantitySetInitialConditionList.size(); i++)
         icpQuantitySetInitialConditionList.get(i).setIncludingFrame(icpQuantityCalculatedInitialConditionList.get(i));
   }

   public void adjustDesiredTrajectoriesForInitialSmoothing()
   {
      if ((isInitialTransfer.getBooleanValue() || (continuouslyAdjustForICPContinuity.getBooleanValue())) && copTrajectories.size() > 1)
      {
         icpAdjustmentToolbox.adjustDesiredTrajectoriesForInitialSmoothing3D(omega0.getDoubleValue(), copTrajectories, icpQuantitySetInitialConditionList,
                                                                             icpDesiredInitialPositionsFromCoPs, icpDesiredFinalPositionsFromCoPs);

      }
   }

   @Override
   public void compute(double time)
   {
      if (visualize)
      {
          for (int i = 0; i < icpWaypoints.size(); i++)
          {
             icpWaypoints.get(i).setToNaN();
          }
      }

      if (cmpTrajectories.size() > 0)
      {
         localTimeInCurrentPhase.set(time - startTimeOfCurrentPhase.getDoubleValue());

         currentSegmentIndex.set(getCurrentSegmentIndex(localTimeInCurrentPhase.getDoubleValue(), cmpTrajectories));
         FrameTrajectory3D cmpPolynomial3D = cmpTrajectories.get(currentSegmentIndex.getIntegerValue());
         getICPPositionDesiredFinalFromSegment(icpPositionDesiredFinalCurrentSegment, currentSegmentIndex.getIntegerValue());

         // ICP
         icpToolbox.computeDesiredCapturePointPosition(omega0.getDoubleValue(), localTimeInCurrentPhase.getDoubleValue(), icpPositionDesiredFinalCurrentSegment,
                                                       cmpPolynomial3D, icpPositionDesiredCurrent);
         icpToolbox.computeDesiredCapturePointVelocity(omega0.getDoubleValue(), localTimeInCurrentPhase.getDoubleValue(), icpPositionDesiredFinalCurrentSegment,
                                                       cmpPolynomial3D, icpVelocityDesiredCurrent);
         icpToolbox.computeDesiredCapturePointAcceleration(omega0.getDoubleValue(), localTimeInCurrentPhase.getDoubleValue(),
                                                           icpPositionDesiredFinalCurrentSegment, cmpPolynomial3D, icpAccelerationDesiredCurrent);

         int currentCoPSegmentIndex = getCurrentSegmentIndex(localTimeInCurrentPhase.getDoubleValue(), copTrajectories);
         getICPInitialConditionsForAdjustment(localTimeInCurrentPhase.getDoubleValue(), currentCoPSegmentIndex); // TODO: add controller dt for proper continuation
         if (debug)
            checkICPDynamics(localTimeInCurrentPhase.getDoubleValue(), icpVelocityDesiredCurrent, icpPositionDesiredCurrent, cmpPolynomial3D);

         if (visualize)
         {
            int waypointIndex = 0;
            icpWaypoints.get(waypointIndex++).set(icpDesiredInitialPositions.get(0));

            for (int segmentIndex = 0; segmentIndex < icpDesiredFinalPositions.size(); segmentIndex++, waypointIndex++)
            {
               icpWaypoints.get(waypointIndex).set(icpDesiredFinalPositions.get(segmentIndex));
            }
         }
      }
   }

   private void checkICPDynamics(double time, FrameVector3D icpVelocityDesiredCurrent, FramePoint3D icpPositionDesiredCurrent,
                                 FrameTrajectory3D cmpPolynomial3D)
   {
      cmpPolynomial3D.compute(time);

      icpVelocityDynamicsCurrent.sub(icpPositionDesiredCurrent, cmpPolynomial3D.getFramePosition());
      icpVelocityDynamicsCurrent.scale(omega0.getDoubleValue());

      yoICPVelocityDynamicsCurrent.set(icpVelocityDynamicsCurrent);

      areICPDynamicsSatisfied.set(icpVelocityDesiredCurrent.epsilonEquals(icpVelocityDynamicsCurrent, 10e-5));
   }

   private int getCurrentSegmentIndex(double timeInCurrentPhase, List<FrameTrajectory3D> trajectories)
   {
      int currentSegmentIndex = FIRST_SEGMENT;
      int numberOfTrajectories = trajectories.size() - 1;
      while (timeInCurrentPhase > trajectories.get(currentSegmentIndex).getFinalTime()
            && Math.abs(trajectories.get(currentSegmentIndex).getFinalTime() - trajectories.get(currentSegmentIndex + 1).getInitialTime()) < 1.0e-5)
      {
         if (debug && currentSegmentIndex > numberOfTrajectories)
         {
            double finalTime = trajectories.get(numberOfTrajectories).getFinalTime();
            String info = "The timeInCurrentPhase" + timeInCurrentPhase + " exceeds the maximum trajectory time " + finalTime + ". " +
                    "There are " + numberOfTrajectories + " trajectories in the list. Their order is as follows: ";
            for (int i = 0; i < trajectories.size(); i++)
            {
               info += "\n\t Start = " + trajectories.get(i).getInitialTime() + ". End = " + trajectories.get(i).getFinalTime();
            }
            throw new RuntimeException(info);
         }

         currentSegmentIndex++;
         if (currentSegmentIndex + 1 > trajectories.size())
         {
            return currentSegmentIndex;
         }
      }
      return currentSegmentIndex;
   }

   public void getICPPositionDesiredFinalFromSegment(FramePoint3D icpPositionDesiredFinal, int segment)
   {
      icpPositionDesiredFinal.set(icpDesiredFinalPositions.get(segment));
   }

   @Override
   public void getPosition(FramePoint3D positionToPack)
   {
      positionToPack.set(icpPositionDesiredCurrent);
   }

   public void getPosition(YoFramePoint3D positionToPack)
   {
      positionToPack.set(icpPositionDesiredCurrent);
   }

   @Override
   public void getVelocity(FrameVector3D velocityToPack)
   {
      velocityToPack.set(icpVelocityDesiredCurrent);
   }

   public void getVelocity(YoFrameVector3D velocityToPack)
   {
      velocityToPack.set(icpVelocityDesiredCurrent);
   }

   @Override
   public void getAcceleration(FrameVector3D accelerationToPack)
   {
      accelerationToPack.set(icpAccelerationDesiredCurrent);
   }

   public void getAcceleration(YoFrameVector3D accelerationToPack)
   {
      accelerationToPack.set(icpAccelerationDesiredCurrent);
   }

   @Override
   public void getLinearData(FramePoint3D positionToPack, FrameVector3D velocityToPack, FrameVector3D accelerationToPack)
   {
      getPosition(positionToPack);
      getVelocity(velocityToPack);
      getAcceleration(accelerationToPack);
   }

   public void getLinearData(YoFramePoint3D positionToPack, YoFrameVector3D velocityToPack, YoFrameVector3D accelerationToPack)
   {
      getPosition(positionToPack);
      getVelocity(velocityToPack);
      getAcceleration(accelerationToPack);
   }

   @Override
   public void showVisualization()
   {

   }

   @Override
   public void hideVisualization()
   {

   }

   @Override
   public boolean isDone()
   {
      return false;
   }

   public List<? extends FramePoint3DReadOnly> getICPPositionDesiredInitialList()
   {
      return icpDesiredInitialPositions;
   }

   public List<? extends FramePoint3DReadOnly> getICPPositionDesiredFinalList()
   {
      return icpDesiredFinalPositions;
   }

   public void getICPPhaseEntryCornerPoints(List<? extends FixedFramePoint3DBasics> icpPhaseEntryCornerPointsToPack)
   {
      int i = 0;
      for (; i < icpPhaseEntryCornerPointIndices.size(); i++)
      {
         FramePoint3D icpPhaseEntryCornerPoint = icpDesiredInitialPositions.get(icpPhaseEntryCornerPointIndices.get(i));
         icpPhaseEntryCornerPointsToPack.get(i).set(icpPhaseEntryCornerPoint);
         icpPhaseEntryCornerPointsToPack.get(i).add(0.0, 0.0, 0.03);
      }
      for (; i < icpPhaseEntryCornerPointsToPack.size(); i++)
         icpPhaseEntryCornerPointsToPack.get(i).setToNaN();

   }

   public void getICPPhaseExitCornerPoints(List<? extends FixedFramePoint3DBasics> icpPhaseExitCornerPointsToPack)
   {
      int i = 0;
      for (; i < icpPhaseExitCornerPointIndices.size(); i++)
      {
         FramePoint3D icpPhaseExitCornerPoint = icpDesiredFinalPositions.get(icpPhaseExitCornerPointIndices.get(i) - 1);
         icpPhaseExitCornerPointsToPack.get(i).set(icpPhaseExitCornerPoint);
         icpPhaseExitCornerPointsToPack.get(i).add(0.0, 0.0, 0.05);
      }
      for (; i < icpPhaseExitCornerPointsToPack.size(); i++)
         icpPhaseExitCornerPointsToPack.get(i).setToNaN();
   }

   public List<? extends FramePoint3DReadOnly> getICPPositonFromCoPDesiredFinalList()
   {
      return icpDesiredFinalPositionsFromCoPs;
   }

   public int getTotalNumberOfSegments()
   {
      return totalNumberOfCMPSegments.getIntegerValue();
   }
}
