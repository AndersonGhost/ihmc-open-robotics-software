package us.ihmc.commonWalkingControlModules.capturePoint.heightForBalance;

import us.ihmc.commons.MathTools;

public class VaryingHeightPrimaryConditionEvaluator
{
   private VaryingHeightPrimaryConditionEnum primaryConditionEnum;
   private VaryingHeightPrimaryConditionEnum primaryConditionPreviousTick;
   private final double zMin;
   private final double minKneeAngle;
   private final double maxKneeAngle;

   private boolean primaryConditionHasChanged;

   public VaryingHeightPrimaryConditionEvaluator(double zMin, double minKneeAngle, double maxKneeAngle)
   {
      this.zMin=zMin;
      this.minKneeAngle=minKneeAngle;
      this.maxKneeAngle=maxKneeAngle;

      primaryConditionEnum = VaryingHeightPrimaryConditionEnum.DEFAULT;
      primaryConditionPreviousTick = primaryConditionEnum;
   }

   /**
    * Primary condition: 2 Constraints (min/max, based on height energy) and the phases (Pos/neg alignment, prepare). Phase changes can only occur in the order
    * that the angle direction (angleGrows) is pointing at.
    */
   public VaryingHeightPrimaryConditionEnum computeAndGetPrimaryConditionEnum(double aMinPredicted, double aMaxPredicted,double z, double dz, double zMax, double kneeAngle, double errorAngle, double errorAngleEndOfSwing,
                                                                              boolean angleGrows, double negAlignTresh, double posAlignTresh,
                                                                              VaryingHeightPrimaryConditionEnum varyingHeightConditionPreviousTick, boolean useAngleForConditions,
                                                                              boolean distancePosAlignment, double copCoMOrthDistance, boolean nonDynamicCase, double tToSwitch, boolean useThreshold)
   {
      this.primaryConditionPreviousTick = varyingHeightConditionPreviousTick;
      // MAXZ
      if((z+MathTools.sign(dz)*dz*dz/(2* -aMinPredicted)>zMax|| kneeAngle<minKneeAngle)    )                             // max height/vel and singularity
      {
         primaryConditionEnum = VaryingHeightPrimaryConditionEnum.MAXZ;
      }
      // MINZ
      else if (z+MathTools.sign(dz)*dz*dz/(2*aMaxPredicted)<zMin || kneeAngle >maxKneeAngle )                         // min height / min vel
      {
         primaryConditionEnum = VaryingHeightPrimaryConditionEnum.MINZ;
      }
      // ALIGNED_POS (errorAngle or distance based)
      else if(useThreshold&&(errorAngle<posAlignTresh && errorAngle>-posAlignTresh) ||(!useAngleForConditions && distancePosAlignment)  || tToSwitch<0.02)                    // alignment ICPe and 'pendulum' positive force
      {
         primaryConditionEnum = VaryingHeightPrimaryConditionEnum.ALIGNED_POS;
         if(varyingHeightConditionPreviousTick==VaryingHeightPrimaryConditionEnum.PREPARE_POS && angleGrows && MathTools.epsilonEquals(errorAngle,posAlignTresh,0.2))
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.PREPARE_POS;
         }
         else if (varyingHeightConditionPreviousTick==VaryingHeightPrimaryConditionEnum.PREPARE_POS && !angleGrows && MathTools.epsilonEquals(errorAngle,-posAlignTresh,0.2))
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.PREPARE_POS;
         }
         else if (varyingHeightConditionPreviousTick==VaryingHeightPrimaryConditionEnum.MAXZ )
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.MAXZ;
         }

      }
      // ALIGNED_NEG (errorAngle or distance based)
      else if(useThreshold&&(errorAngle>negAlignTresh|| errorAngle<-negAlignTresh)  ||(!useAngleForConditions && !distancePosAlignment)  )                                                       // alignment ICPe and 'pendulum' negative force
      {
         primaryConditionEnum = VaryingHeightPrimaryConditionEnum.ALIGNED_NEG;
         if(varyingHeightConditionPreviousTick==VaryingHeightPrimaryConditionEnum.PREPARE_NEG && angleGrows && MathTools.epsilonEquals(errorAngle,-negAlignTresh,0.2))
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.PREPARE_NEG;
         }
         else if (varyingHeightConditionPreviousTick==VaryingHeightPrimaryConditionEnum.PREPARE_NEG && !angleGrows && MathTools.epsilonEquals(errorAngle,negAlignTresh,0.2))
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.PREPARE_NEG;
         }
         else if (varyingHeightConditionPreviousTick == VaryingHeightPrimaryConditionEnum.MINZ )
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.MINZ;
         }
      }
      else //if d(useAngleForConditions)      // preparing for future angle
      {
         if(errorAngleEndOfSwing<posAlignTresh && errorAngleEndOfSwing>-posAlignTresh || varyingHeightConditionPreviousTick == VaryingHeightPrimaryConditionEnum.PREPARE_NEG)
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.PREPARE_NEG;
            if(varyingHeightConditionPreviousTick==VaryingHeightPrimaryConditionEnum.ALIGNED_POS)
            {
               primaryConditionEnum = VaryingHeightPrimaryConditionEnum.ALIGNED_POS;
            }
         }
         else if (errorAngleEndOfSwing>negAlignTresh || errorAngleEndOfSwing<-negAlignTresh || varyingHeightConditionPreviousTick == VaryingHeightPrimaryConditionEnum.PREPARE_POS)
         {
            primaryConditionEnum = VaryingHeightPrimaryConditionEnum.PREPARE_POS;
            if (varyingHeightConditionPreviousTick == VaryingHeightPrimaryConditionEnum.ALIGNED_NEG)
            {
               primaryConditionEnum = VaryingHeightPrimaryConditionEnum.ALIGNED_NEG;
            }
            else if (varyingHeightConditionPreviousTick == VaryingHeightPrimaryConditionEnum.MAXZ)
            {
               primaryConditionEnum = VaryingHeightPrimaryConditionEnum.MAXZ;
            }
         }
      }
      return primaryConditionEnum;
   }
   public boolean getPrimaryConditionHasChanged()
   {
      if(primaryConditionPreviousTick!=primaryConditionEnum)
      {
         primaryConditionHasChanged = true;
      }
      else
      {
         primaryConditionHasChanged = false;
      }
      return primaryConditionHasChanged;
   }
}