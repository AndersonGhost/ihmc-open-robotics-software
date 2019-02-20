package us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.nodeChecking;

import us.ihmc.commons.MathTools;
import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.axisAngle.AxisAngle;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.orientation.interfaces.Orientation3DReadOnly;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple2D.Vector2D;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.QuadrupedFootstepPlannerNodeRejectionReason;
import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.footstepSnapping.FootstepNodeSnapData;
import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.footstepSnapping.FootstepNodeSnapper;
import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.graph.FootstepNode;
import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.parameters.FootstepPlannerParameters;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.robotics.geometry.PlanarRegion;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.QuadrantDependentList;
import us.ihmc.robotics.robotSide.RobotEnd;
import us.ihmc.robotics.robotSide.RobotQuadrant;
import us.ihmc.robotics.robotSide.RobotSide;

import java.util.List;

import static us.ihmc.humanoidRobotics.footstep.FootstepUtils.worldFrame;

public class SnapBasedNodeChecker extends FootstepNodeChecker
{
   private static final boolean DEBUG = false;

   private final FootstepPlannerParameters parameters;
   private final FootstepNodeSnapper snapper;

   public SnapBasedNodeChecker(FootstepPlannerParameters parameters, FootstepNodeSnapper snapper)
   {
      this.parameters = parameters;
      this.snapper = snapper;
   }

   @Override
   public void setPlanarRegions(PlanarRegionsList planarRegions)
   {
      super.setPlanarRegions(planarRegions);
      snapper.setPlanarRegions(planarRegions);
   }

   @Override
   public boolean isNodeValidInternal(FootstepNode node, FootstepNode previousNode)
   {
      RobotQuadrant movingQuadrant = node.getMovingQuadrant();

      FootstepNodeSnapData snapData = snapper.snapFootstepNode(node);
      RigidBodyTransform snapTransform = snapData.getSnapTransform();
      if (snapTransform.containsNaN())
      {
         if (DEBUG)
         {
            PrintTools.debug("Was not able to snap node:\n" + node);
         }
         rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.COULD_NOT_SNAP);
         return false;
      }

      if (previousNode == null)
      {
         return true;
      }


      double previousYaw = previousNode.getNominalYaw();
      double currentYaw = node.getNominalYaw();

      Vector2D clearanceVector = new Vector2D(parameters.getMinXClearanceFromFoot(), parameters.getMinYClearanceFromFoot());
      AxisAngle previousOrientation = new AxisAngle(previousYaw, 0.0, 0.0);
      previousOrientation.transform(clearanceVector);

      if (MathTools.epsilonEquals(node.getX(movingQuadrant), previousNode.getX(movingQuadrant), Math.abs(clearanceVector.getX())) && MathTools
            .epsilonEquals(node.getY(movingQuadrant), previousNode.getY(movingQuadrant), Math.abs(clearanceVector.getY())))
      {
         if (DEBUG)
         {
            PrintTools.info("The node " + node + " is trying to step in place.");
         }
         rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_IN_PLACE);
         return false;
      }

      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         if (robotQuadrant == movingQuadrant)
            continue;

         if (MathTools.epsilonEquals(node.getX(movingQuadrant), previousNode.getX(robotQuadrant), Math.abs(clearanceVector.getX())) && MathTools
               .epsilonEquals(node.getY(movingQuadrant), previousNode.getY(robotQuadrant), Math.abs(clearanceVector.getY())))
         {
            if (DEBUG)
            {
               PrintTools.info("The node " + node + " is stepping on another foot.");
            }
            rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_ON_OTHER_FOOT);
            return false;
         }
      }




      double yaw = AngleTools.computeAngleDifferenceMinusPiToPi(currentYaw, previousYaw);
      if (!MathTools.intervalContains(yaw, parameters.getMinimumStepYaw(), parameters.getMaximumStepYaw()))
      {
         if (DEBUG)
         {
            PrintTools.info("The node " + node + " results in too much yaw.");
         }
         rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_YAWING_TOO_MUCH);
         return false;
      }

      FramePoint3D newStepPosition = new FramePoint3D(worldFrame, node.getX(movingQuadrant), node.getY(movingQuadrant), 0.0);
      snapTransform.transform(newStepPosition);

      QuadrantDependentList<Point3D> previousSnappedStepPositions = getSnappedStepPositions(previousNode);
      Point3D previousStepPosition = previousSnappedStepPositions.get(movingQuadrant);


      double heightChange = Math.abs(newStepPosition.getZ() - previousStepPosition.getZ());
      if (heightChange > parameters.getMaximumStepChangeZ())
      {
         if (DEBUG)
         {
            PrintTools.debug("Too much height difference (" + Math.round(100.0 * heightChange) + "cm) to previous node:\n" + node);
         }
         rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_HIGH_OR_LOW);
         return false;
      }

      QuadrantDependentList<PoseReferenceFrame> footFrames = getFootFrames(previousSnappedStepPositions, previousOrientation);
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         FramePoint3D expectedXGaitPoint = new FramePoint3D(footFrames.get(robotQuadrant));

         double forwardOffset = movingQuadrant.getEnd() == robotQuadrant.getEnd() ? 0.0 : movingQuadrant.getEnd() == RobotEnd.FRONT ? previousNode.getNominalStanceLength() : -previousNode.getNominalStanceLength();
         double sideOffset = movingQuadrant.getSide() == robotQuadrant.getSide() ? 0.0 : movingQuadrant.getSide() == RobotSide.LEFT ? previousNode.getNominalStanceWidth() : -previousNode.getNominalStanceWidth();
         expectedXGaitPoint.add(forwardOffset, sideOffset, 0.0);

         newStepPosition.changeFrame(footFrames.get(robotQuadrant));

         // check forward/backward
         if ((newStepPosition.getX() - expectedXGaitPoint.getX()) > parameters.getMaximumStepReach())
         {
            rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_FAR_FORWARD);
            return false;
         }
         else if (newStepPosition.getX() - expectedXGaitPoint.getX() < parameters.getMinimumStepLength())
         {
            rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_FAR_BACKWARD);
            return false;
         }

         // check left/right
         if (movingQuadrant.getSide() == RobotSide.LEFT)
         {
            if (newStepPosition.getY() - expectedXGaitPoint.getY() > parameters.getMaximumStepWidth())
            {
               rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_FAR_OUTWARD);
               return false;
            }
            if (newStepPosition.getY() - expectedXGaitPoint.getY() < parameters.getMinimumStepWidth())
            {
               rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_FAR_INWARD);
               return false;
            }
         }
         else
         {
            if (newStepPosition.getY() - expectedXGaitPoint.getY() < -parameters.getMaximumStepWidth())
            {
               rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_FAR_OUTWARD);
               return false;
            }
            if (newStepPosition.getY() - expectedXGaitPoint.getY() > -parameters.getMinimumStepWidth())
            {
               rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.STEP_TOO_FAR_INWARD);
               return false;
            }
         }
      }
      newStepPosition.changeFrame(worldFrame);



      if (hasPlanarRegions() && isObstacleBetweenSteps(newStepPosition, previousStepPosition, planarRegionsList.getPlanarRegionsAsList(),
                                                       parameters.getBodyGroundClearance()))
      {
         if (DEBUG)
         {
            PrintTools.debug("Found an obstacle between the nodes " + node + " and " + previousNode);
         }
         rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.OBSTACLE_BLOCKING_STEP);
         return false;
      }

      if (hasPlanarRegions() && isObstacleBetweenFeet(newStepPosition, movingQuadrant, previousSnappedStepPositions, planarRegionsList.getPlanarRegionsAsList(),
                                                      parameters.getBodyGroundClearance()))
      {
         if (DEBUG)
         {
            PrintTools.debug("Found an obstacle between the nodes " + node + " and " + previousNode);
         }
         rejectNode(node, previousNode, QuadrupedFootstepPlannerNodeRejectionReason.OBSTACLE_BLOCKING_BODY);
         return false;
      }

      return true;
   }

   private QuadrantDependentList<Point3D> getSnappedStepPositions(FootstepNode node)
   {
      QuadrantDependentList<Point3D> snappedStepPositions = new QuadrantDependentList<>();

      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         FootstepNodeSnapData snapData = snapper.snapFootstepNode(node.getXIndex(robotQuadrant), node.getYIndex(robotQuadrant));
         RigidBodyTransform footSnapTransform = snapData.getSnapTransform();
         Point3D stepPosition = new Point3D(node.getX(robotQuadrant), node.getY(robotQuadrant), 0.0);
         footSnapTransform.transform(stepPosition);
         snappedStepPositions.put(robotQuadrant, stepPosition);
      }

      return snappedStepPositions;
   }

   private QuadrantDependentList<PoseReferenceFrame> getFootFrames(QuadrantDependentList<Point3D> stepPositions, Orientation3DReadOnly orientation)
   {
      QuadrantDependentList<PoseReferenceFrame> footFrames = new QuadrantDependentList<>();
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         PoseReferenceFrame footFrame = new PoseReferenceFrame(robotQuadrant.getCamelCaseName() + "FootFrame", ReferenceFrame.getWorldFrame());
         footFrame.setPoseAndUpdate(stepPositions.get(robotQuadrant), orientation);

         footFrames.put(robotQuadrant, footFrame);
      }

      return footFrames;
   }

   /**
    * This is meant to test if there is a wall that the body of the robot would run into when shifting
    * from one step to the next. It is not meant to eliminate swing overs.
    */
   private static boolean isObstacleBetweenSteps(Point3DReadOnly footPosition, Point3DReadOnly previousFootPosition, List<PlanarRegion> planarRegions,
                                                 double groundClearance)
   {
      PlanarRegion bodyPath = createBodyCollisionRegionFromTwoFeet(footPosition, previousFootPosition, groundClearance, 2.0);

      for (PlanarRegion region : planarRegions)
      {
         if (!region.intersect(bodyPath).isEmpty())
            return true;
      }

      return false;
   }

   /**
    * This is meant to test if there is a wall that the body of the robot would run into when shifting
    * from one step to the next. It is not meant to eliminate swing overs.
    */
   private static boolean isObstacleBetweenFeet(Point3DReadOnly newFootPosition, RobotQuadrant newFootQuadrant,
                                                QuadrantDependentList<Point3D> previousStepPositions, List<PlanarRegion> planarRegions, double groundClearance)
   {
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         if (robotQuadrant == newFootQuadrant)
            continue;

         PlanarRegion bodyPath = createBodyCollisionRegionFromTwoFeet(newFootPosition, previousStepPositions.get(robotQuadrant), groundClearance, 2.0);

         for (PlanarRegion region : planarRegions)
         {
            if (!region.intersect(bodyPath).isEmpty())
               return true;
         }
      }

      return false;
   }

   /**
    * Given two footstep positions this will create a vertical planar region above the points. The region
    * will be aligned with the vector connecting the nodes. It's lower edge will be the specified
    * distance above the higher of the two nodes and the plane will have the specified height.
    */
   public static PlanarRegion createBodyCollisionRegionFromTwoFeet(Point3DReadOnly footA, Point3DReadOnly footB, double clearance, double height)
   {
      double lowerZ = Math.max(footA.getZ(), footB.getZ()) + clearance;
      Point3D point0 = new Point3D(footA.getX(), footA.getY(), lowerZ);
      Point3D point1 = new Point3D(footA.getX(), footA.getY(), lowerZ + height);
      Point3D point2 = new Point3D(footB.getX(), footB.getY(), lowerZ);
      Point3D point3 = new Point3D(footB.getX(), footB.getY(), lowerZ + height);

      Vector3D xAxisInPlane = new Vector3D();
      xAxisInPlane.sub(point2, point0);
      xAxisInPlane.normalize();
      Vector3D yAxisInPlane = new Vector3D(0.0, 0.0, 1.0);
      Vector3D zAxis = new Vector3D();
      zAxis.cross(xAxisInPlane, yAxisInPlane);

      RigidBodyTransform transform = new RigidBodyTransform();
      transform.setRotation(xAxisInPlane.getX(), xAxisInPlane.getY(), xAxisInPlane.getZ(), yAxisInPlane.getX(), yAxisInPlane.getY(), yAxisInPlane.getZ(),
                            zAxis.getX(), zAxis.getY(), zAxis.getZ());
      transform.setTranslation(point0);
      transform.invertRotation();

      point0.applyInverseTransform(transform);
      point1.applyInverseTransform(transform);
      point2.applyInverseTransform(transform);
      point3.applyInverseTransform(transform);

      ConvexPolygon2D polygon = new ConvexPolygon2D();
      polygon.addVertex(point0.getX(), point0.getY());
      polygon.addVertex(point1.getX(), point1.getY());
      polygon.addVertex(point2.getX(), point2.getY());
      polygon.addVertex(point3.getX(), point3.getY());
      polygon.update();

      return new PlanarRegion(transform, polygon);
   }

   @Override
   public void addStartNode(FootstepNode startNode, QuadrantDependentList<RigidBodyTransform> startNodeTransforms)
   {
      for (RobotQuadrant robotQuadrant : RobotQuadrant.values)
      {
         snapper.addSnapData(startNode.getXIndex(robotQuadrant), startNode.getYIndex(robotQuadrant), new FootstepNodeSnapData(startNodeTransforms.get(robotQuadrant)));
      }
   }
}