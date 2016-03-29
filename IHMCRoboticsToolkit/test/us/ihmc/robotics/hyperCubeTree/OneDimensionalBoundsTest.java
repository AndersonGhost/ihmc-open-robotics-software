package us.ihmc.robotics.hyperCubeTree;

import org.junit.Test;

import us.ihmc.tools.testing.TestPlanAnnotations.DeployableTestMethod;

import static org.junit.Assert.assertEquals;

public class OneDimensionalBoundsTest
{
   private static final double eps = 1e-14;

	@DeployableTestMethod(estimatedDuration = 0.0)
	@Test(timeout = 30000)
   public void testIntersection()
   {
      OneDimensionalBounds zeroToOne = new OneDimensionalBounds(0.0, 1.0);
      OneDimensionalBounds zeroToTwenty = new OneDimensionalBounds(0.0, 20.0);
      OneDimensionalBounds result = zeroToOne.intersectionWith(zeroToTwenty);
      assertEquals(1.0,result.max(),eps);
   }

}
