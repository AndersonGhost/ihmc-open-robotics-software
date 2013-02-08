package us.ihmc.commonWalkingControlModules.controlModules;

import java.util.ArrayList;
import java.util.List;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import us.ihmc.commonWalkingControlModules.WrenchDistributorTools;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.PlaneContactState;
import us.ihmc.commonWalkingControlModules.controlModules.nativeOptimization.ContactPointWrenchOptimizerNative;
import us.ihmc.utilities.exeptions.NoConvergenceException;
import us.ihmc.utilities.math.MatrixTools;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FramePoint2d;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.SpatialForceVector;
import us.ihmc.utilities.screwTheory.Wrench;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.YoVariableRegistry;

public class ContactPointGroundReactionWrenchDistributor implements GroundReactionWrenchDistributor
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final ReferenceFrame centerOfMassFrame;

   private final double[] diagonalCWeights = new double[Wrench.SIZE];
   private double epsilonRho;

   private final int rhoDimension = ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS
                                    * ContactPointWrenchOptimizerNative.NUMBER_OF_POINTS_PER_CONTACT * ContactPointWrenchOptimizerNative.MAX_NUMBER_OF_CONTACTS;

   private final DenseMatrix64F desiredWrench = new DenseMatrix64F(Wrench.SIZE, 1);

   private final DenseMatrix64F aMatrix = new DenseMatrix64F(Wrench.SIZE, rhoDimension);
   private final DenseMatrix64F normalForceSelectorBMatrix = new DenseMatrix64F(ContactPointWrenchOptimizerNative.MAX_NUMBER_OF_CONTACTS, rhoDimension);
   private final DenseMatrix64F rho = new DenseMatrix64F(rhoDimension, 1);
   private final double[] minimumNormalForces = new double[ContactPointWrenchOptimizerNative.MAX_NUMBER_OF_CONTACTS];

   private final double[] aMatrixAsDoubleArray = new double[aMatrix.getNumElements()];
   private final double[] normalForceSelectorBMatrixAsDoubleArray = new double[normalForceSelectorBMatrix.getNumElements()];
   private final double[] desiredWrenchAsDoubleArray = new double[desiredWrench.getNumElements()];

   // intermediate result storage:
   private final ArrayList<FrameVector> normalizedSupportVectors = new ArrayList<FrameVector>(ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS);
   private final DenseMatrix64F supportVectorMatrixVBlock = new DenseMatrix64F(Wrench.SIZE / 2, ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS);
   private final FramePoint tempContactPoint = new FramePoint(ReferenceFrame.getWorldFrame());
   private final FrameVector tempVector = new FrameVector(ReferenceFrame.getWorldFrame());
   private final DenseMatrix64F aBlock = new DenseMatrix64F(Wrench.SIZE,
                                            ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS
                                            * ContactPointWrenchOptimizerNative.NUMBER_OF_POINTS_PER_CONTACT);
   private final DenseMatrix64F rhoBlock = new DenseMatrix64F(ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS
                                              * ContactPointWrenchOptimizerNative.NUMBER_OF_POINTS_PER_CONTACT, 1);
   private final DenseMatrix64F tempWrenchMatrix = new DenseMatrix64F(Wrench.SIZE, 1);
   private final Wrench tempWrench = new Wrench();

   private final ContactPointWrenchOptimizerNative contactPointWrenchOptimizerNative = new ContactPointWrenchOptimizerNative();
   private final CenterOfPressureResolver centerOfPressureResolver = new CenterOfPressureResolver();

   private final BooleanYoVariable converged = new BooleanYoVariable("converged", registry);
   private final BooleanYoVariable hasNotConvergedInPast = new BooleanYoVariable("hasNotConvergedInPast", registry);

   public ContactPointGroundReactionWrenchDistributor(ReferenceFrame centerOfMassFrame, YoVariableRegistry parentRegistry)
   {
      this.centerOfMassFrame = centerOfMassFrame;

      for (int i = 0; i < ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS; i++)
      {
         normalizedSupportVectors.add(new FrameVector(ReferenceFrame.getWorldFrame()));
      }

      parentRegistry.addChild(registry);
   }

   public void setWeights(double[] diagonalCWeights, double epsilonRho)
   {
      for (int i = 0; i < diagonalCWeights.length; i++)
      {
         this.diagonalCWeights[i] = diagonalCWeights[i];
      }

      this.epsilonRho = epsilonRho;
   }

   public void setMinimumNormalForces(double[] minimumNormalForces)
   {
      for (int i = 0; i < minimumNormalForces.length; i++)
      {
         this.minimumNormalForces[i] = minimumNormalForces[i];
      }
   }

   public void solve(GroundReactionWrenchDistributorOutputData distributedWrench, GroundReactionWrenchDistributorInputData inputData)
   {
      distributedWrench.reset();

      SpatialForceVector desiredGroundReactionWrench = inputData.getDesiredNetSpatialForceVector();

      desiredGroundReactionWrench.changeFrame(centerOfMassFrame);
      desiredGroundReactionWrench.packMatrix(desiredWrench);

      aMatrix.zero();
      normalForceSelectorBMatrix.zero();

      ArrayList<PlaneContactState> contactStates = inputData.getContactStates();

      int contactNumber = 0;
      for (PlaneContactState contactState : contactStates)
      {
         List<FramePoint2d> contactPoints2d = contactState.getContactPoints2d();
         int nContactPoints = contactPoints2d.size();

         WrenchDistributorTools.getSupportVectors(normalizedSupportVectors, contactState.getCoefficientOfFriction(), contactState.getPlaneFrame());

         // B
         WrenchDistributorTools.computeSupportVectorMatrixBlock(supportVectorMatrixVBlock, normalizedSupportVectors, contactState.getPlaneFrame());
         placeBBlock(normalForceSelectorBMatrix, supportVectorMatrixVBlock, contactNumber, nContactPoints);

         // force part of A
         WrenchDistributorTools.computeSupportVectorMatrixBlock(supportVectorMatrixVBlock, normalizedSupportVectors, centerOfMassFrame);
         placeAForceBlock(aMatrix, supportVectorMatrixVBlock, contactNumber, nContactPoints);

         int aTorquePartColumn = contactNumber * ContactPointWrenchOptimizerNative.NUMBER_OF_POINTS_PER_CONTACT
                                 * ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS;
         for (FramePoint2d contactPoint2d : contactPoints2d)
         {
            // torque part of A
            tempContactPoint.set(contactPoint2d.getReferenceFrame(), contactPoint2d.getX(), contactPoint2d.getY(), 0.0);
            tempContactPoint.changeFrame(centerOfMassFrame);

            for (FrameVector supportVector : normalizedSupportVectors)
            {
               tempVector.setToZero(centerOfMassFrame);
               tempVector.cross(tempContactPoint, supportVector);
               placeATorqueBlock(aMatrix, tempVector, aTorquePartColumn++);
            }
         }

         contactNumber++;
      }

      MatrixTools.denseMatrixToArrayColumnMajor(aMatrix, aMatrixAsDoubleArray);
      MatrixTools.denseMatrixToArrayColumnMajor(normalForceSelectorBMatrix, normalForceSelectorBMatrixAsDoubleArray);
      MatrixTools.denseMatrixToArrayColumnMajor(desiredWrench, desiredWrenchAsDoubleArray);

      try
      {
         contactPointWrenchOptimizerNative.solve(aMatrixAsDoubleArray, diagonalCWeights, normalForceSelectorBMatrixAsDoubleArray, desiredWrenchAsDoubleArray,
                 minimumNormalForces, epsilonRho);
         converged.set(true);
      }
      catch (NoConvergenceException e)
      {
         if (!hasNotConvergedInPast.getBooleanValue())
         {
            e.printStackTrace();
            System.err.println("WARNING: Only showing the stack trace of the first " + e.getClass().getSimpleName()
                  + ". This may be happening more than once. See value of YoVariable " + converged.getName() + ".");            
         }
         converged.set(false);
         hasNotConvergedInPast.set(true);
      }

      double[] rhoArray = contactPointWrenchOptimizerNative.getRho();
      rho.set(rho.getNumRows(), rho.getNumCols(), false, rhoArray);

      contactNumber = 0;

      for (PlaneContactState contactState : contactStates)
      {
         // aBlock
         int aStartColumn = contactNumber * aBlock.getNumCols();
         int aEndColumn = (contactNumber + 1) * aBlock.getNumCols();
         CommonOps.extract(aMatrix, 0, aBlock.getNumRows(), aStartColumn, aEndColumn, aBlock, 0, 0);

         // rhoBlock
         int rhoStartRow = contactNumber * rhoBlock.getNumRows();
         int rhoEndRow = (contactNumber + 1) * rhoBlock.getNumRows();
         CommonOps.extract(rho, rhoStartRow, rhoEndRow, 0, 1, rhoBlock, 0, 0);

         // wrench
         CommonOps.mult(aBlock, rhoBlock, tempWrenchMatrix);
         tempWrench.set(centerOfMassFrame, tempWrenchMatrix);
         tempWrench.changeFrame(contactState.getPlaneFrame());

         // force, CoP, normal torque
         FrameVector force = new FrameVector(contactState.getPlaneFrame());
         tempWrench.packLinearPart(force);
         FramePoint2d centerOfPressure = new FramePoint2d(contactState.getPlaneFrame());
         double normalTorque = centerOfPressureResolver.resolveCenterOfPressureAndNormalTorque(centerOfPressure, tempWrench, contactState.getPlaneFrame());

         distributedWrench.set(contactState, force, centerOfPressure, normalTorque);
         contactNumber++;
      }
   }

   private static void placeBBlock(DenseMatrix64F normalForceSelectorBMatrix, DenseMatrix64F supportVectorMatrixBlock, int contactNumber, int nContactPoints)
   {
      int bMatrixRow = contactNumber;
      int supportVectorMatrixRow = 2;    // z force
      int nBlocks = nContactPoints;
      int startColumn = contactNumber
                        * (ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS * ContactPointWrenchOptimizerNative.NUMBER_OF_POINTS_PER_CONTACT);
      for (int columnBlockNumber = 0; columnBlockNumber < nBlocks; columnBlockNumber++)
      {
         CommonOps.extract(supportVectorMatrixBlock, supportVectorMatrixRow, supportVectorMatrixRow + 1, 0, supportVectorMatrixBlock.getNumCols(),
                           normalForceSelectorBMatrix, bMatrixRow, startColumn);
         startColumn += supportVectorMatrixBlock.getNumCols();
      }
   }

   private static void placeAForceBlock(DenseMatrix64F aMatrix, DenseMatrix64F aForceBlock, int contactNumber, int nContactPoints)
   {
      int nBlocks = nContactPoints;
      int startRow = Wrench.SIZE / 2;
      int startColumn = contactNumber
                        * (ContactPointWrenchOptimizerNative.NUMBER_OF_SUPPORT_VECTORS * ContactPointWrenchOptimizerNative.NUMBER_OF_POINTS_PER_CONTACT);
      for (int columnBlockNumber = 0; columnBlockNumber < nBlocks; columnBlockNumber++)
      {
         CommonOps.extract(aForceBlock, 0, aForceBlock.getNumRows(), 0, aForceBlock.getNumCols(), aMatrix, startRow, startColumn);
         startColumn += aForceBlock.getNumCols();
      }
   }

   private static void placeATorqueBlock(DenseMatrix64F aMatrix, FrameVector aTorqueColumn, int columnNumber)
   {
      int startRow = 0;
      MatrixTools.setDenseMatrixFromTuple3d(aMatrix, aTorqueColumn.getVector(), startRow, columnNumber);
   }

}
