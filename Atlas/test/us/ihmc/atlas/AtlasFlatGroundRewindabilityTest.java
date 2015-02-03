package us.ihmc.atlas;

import us.ihmc.darpaRoboticsChallenge.DRCFlatGroundRewindabilityTest;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotModel;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.utilities.code.unitTesting.BambooAnnotations.CustomJob;
import us.ihmc.utilities.code.unitTesting.BambooAnnotations.CustomJobType;

@CustomJob(job = CustomJobType.Utilities)
public class AtlasFlatGroundRewindabilityTest extends DRCFlatGroundRewindabilityTest
{

   @Override
   public DRCRobotModel getRobotModel()
   {
      return new AtlasRobotModel(AtlasRobotVersion.DRC_NO_HANDS, AtlasRobotModel.AtlasTarget.SIM, false);
   }

   @Override
   public String getSimpleRobotName()
   {
      return BambooTools.getSimpleRobotNameFor(BambooTools.SimpleRobotNameKeys.ATLAS);
   }

}
