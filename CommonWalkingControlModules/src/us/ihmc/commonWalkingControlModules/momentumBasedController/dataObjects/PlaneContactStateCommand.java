package us.ihmc.commonWalkingControlModules.momentumBasedController.dataObjects;

import java.util.List;

import javax.vecmath.Point2d;

import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FramePoint2d;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.lists.FrameTupleArrayList;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.RigidBody;

public class PlaneContactStateCommand extends InverseDynamicsCommand<PlaneContactStateCommand>
{
   private RigidBody rigidBody;
   private long id = -1L;
   private double coefficientOfFriction = Double.NaN;
   private double wRhoSmoother = 0.0;
   private final int initialSize = 8;
   private final FrameTupleArrayList<FramePoint> contactPoints = FrameTupleArrayList.createFramePointArrayList(initialSize);
   private final FrameVector contactNormal = new FrameVector(ReferenceFrame.getWorldFrame(), 0.0, 0.0, 1.0);

   public PlaneContactStateCommand()
   {
      super(InverseDynamicsCommandType.PLANE_CONTACT_STATE);
   }

   public void setId(long id)
   {
      this.id = id;
   }

   public void setContactingRigidBody(RigidBody rigidBody)
   {
      this.rigidBody = rigidBody;
   }

   public void setCoefficientOfFriction(double coefficientOfFriction)
   {
      this.coefficientOfFriction = coefficientOfFriction;
   }

   public void setWRhoSmoother(double wRhoSmoother)
   {
      this.wRhoSmoother = wRhoSmoother;
   }

   public void clearContactPoints()
   {
      contactPoints.clear();
   }

   public void addPointInContact(FramePoint newPointInContact)
   {
      contactPoints.add().setIncludingFrame(newPointInContact);
   }

   public void addPointInContact(FramePoint2d newPointInContact)
   {
      contactPoints.add().setXYIncludingFrame(newPointInContact);
   }

   public void setPointsInContact(List<FramePoint> newPointsInContact)
   {
      contactPoints.copyFromListAndTrimSize(newPointsInContact);
   }

   public void setPoint2dsInContact(ReferenceFrame contactFrame, List<Point2d> newPointsInContact)
   {
      clearContactPoints();
      for (int i = 0; i < newPointsInContact.size(); i++)
         contactPoints.add().setXYIncludingFrame(contactFrame, newPointsInContact.get(i));
   }

   public void setContactNormal(FrameVector contactNormal)
   {
      this.contactNormal.setIncludingFrame(contactNormal);
   }

   public long getId()
   {
      return id;
   }

   public boolean isEmpty()
   {
      return contactPoints.isEmpty();
   }

   public int getNumberOfContactPoints()
   {
      return contactPoints.size();
   }

   public double getCoefficientOfFriction()
   {
      return coefficientOfFriction;
   }

   public RigidBody getContactingRigidBody()
   {
      return rigidBody;
   }

   public void getContactPoint(int index, FramePoint contactPointToPack)
   {
      contactPointToPack.setIncludingFrame(contactPoints.get(index));
   }

   public void getContactNormal(FrameVector contactNormalToPack)
   {
      contactNormalToPack.setIncludingFrame(contactNormal);
   }

   public double getWRhoSmoother()
   {
      return wRhoSmoother;
   }

   @Override
   public void set(PlaneContactStateCommand other)
   {
      rigidBody = other.rigidBody;
      coefficientOfFriction = other.coefficientOfFriction;
      contactPoints.copyFromListAndTrimSize(other.contactPoints);
      contactNormal.setIncludingFrame(other.contactNormal);
   }

   @Override
   public String toString()
   {
      String ret = getClass().getSimpleName() + ": contacting rigid body = " + rigidBody + ", number of contact points = " + getNumberOfContactPoints();
      return ret;
   }
}
