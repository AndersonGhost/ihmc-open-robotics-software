package us.ihmc.commonWalkingControlModules.capturePoint.heightForBalance;

import static java.lang.Math.sqrt;

public class VaryingHeightTimeToConstraintsPredictor
{
   private final double zMin;


   /**
    * Gives an estimate of the time until the maximum velocity or position constraint is reached, given a predicted constant acceleration and deceleration.
    * Those are a predefined fraction of the min/max acceleration for the controller.
    *
    * IS 0 A GOOD VALUE WHEN NAN?
    *
    * @param zMin

    */
   public VaryingHeightTimeToConstraintsPredictor(double zMin)
   {
      this.zMin=zMin;

   }

   /**
    * Predicted time to minimum position, future velocity incorporated
    * @param zCurrent current height
    * @param dzCurrent current height velocity
    * @return
    */
   public double getTMinPosReachedPredicted(double zCurrent, double dzCurrent, double aMinPredicted, double aMaxPredicted)
   {
      double zMinForPrediction = 1.03 * zMin;
      double a = 0.5 * (aMinPredicted - aMinPredicted * aMinPredicted / aMaxPredicted);
      double b = dzCurrent - dzCurrent * aMinPredicted / aMaxPredicted;
      double c = zCurrent - zMinForPrediction - 0.5 * dzCurrent * dzCurrent / aMaxPredicted;
      double tMinPosReachedPredicted = (-b - sqrt(b * b - 4 * a * c)) / (2 * a);
      tMinPosReachedPredicted = Math.max(0, tMinPosReachedPredicted);
      if(Double.isNaN(tMinPosReachedPredicted)){tMinPosReachedPredicted=0;}
      return tMinPosReachedPredicted;
   }

   /**
    * Predicted time to maximum position, future velocity incorporated
    * @param zCurrent
    * @param dzCurrent
    * @param zMax Max height, changes halfway swing
    * @return
    */
   public double getTMaxPosReachedPredicted(double zCurrent, double dzCurrent, double zMax, double aMinPredicted, double aMaxPredicted)
   {
      double zMaxForPrediction = zMax;
      double a = 0.5 * (aMaxPredicted + aMaxPredicted * aMaxPredicted / -aMinPredicted);
      double b = dzCurrent + dzCurrent * aMaxPredicted / -aMinPredicted;
      double c = zCurrent - zMaxForPrediction + 0.5 * dzCurrent * dzCurrent / -aMinPredicted;
      double tMaxPosReachedPredicted = (-b + sqrt(b * b - 4 * a * c)) / (2 * a);
      tMaxPosReachedPredicted = Math.max(0, tMaxPosReachedPredicted);
      if(Double.isNaN(tMaxPosReachedPredicted)){tMaxPosReachedPredicted=0;}
      return tMaxPosReachedPredicted;
   }

   /**
    * Predicted time to minimum velocity
    * @param dzCurrent
    * @return
    */
   public double getTMinVelReachedPredicted(double dzCurrent, double aMin, double vMin)
   {
      double tMinVelReachedPredicted = (vMin - dzCurrent) / aMin;
      tMinVelReachedPredicted = Math.max(0, tMinVelReachedPredicted);
      if(Double.isNaN(tMinVelReachedPredicted)){tMinVelReachedPredicted=0;}
      return tMinVelReachedPredicted;
   }
   public double getTMaxVelReachedPredicted(double dzCurrent, double aMax, double vMax)
   {
      double tMaxVelReachedPredicted = (vMax - dzCurrent) / aMax;
      tMaxVelReachedPredicted = Math.max(0, tMaxVelReachedPredicted);
      if(Double.isNaN(tMaxVelReachedPredicted)){tMaxVelReachedPredicted=0;}
      return tMaxVelReachedPredicted;
   }

}