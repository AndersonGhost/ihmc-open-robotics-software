package us.ihmc.robotEnvironmentAwareness.exoRealSense;

import java.awt.Color;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import controller_msgs.msg.dds.StereoVisionPointCloudMessage;
import sensor_msgs.PointCloud2;
import us.ihmc.communication.IHMCROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.packets.MessageTools;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.ros2.Ros2Node;
import us.ihmc.utilities.ros.RosMainNode;
import us.ihmc.utilities.ros.subscriber.AbstractRosTopicSubscriber;
import us.ihmc.utilities.ros.subscriber.RosPointCloudSubscriber;
import us.ihmc.utilities.ros.subscriber.RosPointCloudSubscriber.UnpackedPointCloud;

/*
 * class for creating connection between Ros publishing /camera/depth/color/points (realsense D415 camera) and Ros2
 */
public class RealSenseBridgeRos2 extends AbstractRosTopicSubscriber<PointCloud2>
{
   //params
   private int MAX_NUMBER_OF_POINTS;
   
   //constructor
   public RealSenseBridgeRos2(String sourceURI, String rosWrapperTopic, Ros2Node ros2Node, String ros2Topic, int maxNumberOfPoints) throws URISyntaxException
   {
      super(PointCloud2._TYPE);
      URI masterURI = new URI(sourceURI);
      RosMainNode rosMainNode = new RosMainNode(masterURI, "StereoVisionPublisher", true);
      rosMainNode.attachSubscriber(rosWrapperTopic, this);
      rosMainNode.execute();

      this.MAX_NUMBER_OF_POINTS = maxNumberOfPoints;
      stereoVisionPublisher = ROS2Tools.createPublisher(ros2Node, StereoVisionPointCloudMessage.class, ros2Topic);
   }

   //variables   
   final IHMCROS2Publisher<StereoVisionPointCloudMessage> stereoVisionPublisher;
   UnpackedPointCloud pointCloudData;
   Point3D[] pointCloud;
   Color[] colors;
   Random random = new Random();
   int numberOfPoints;
   int indexToRemove;
   int lastIndex;
   long timestamp;
   float[] pointCloudBuffer;
   int[] colorsInteger;
   Point3D scanPoint;
   StereoVisionPointCloudMessage stereoVisionMessage;
   
   //functions
   @Override
   public void onNewMessage(PointCloud2 cloudHolder)
   {
      //getting message
      pointCloudData = RosPointCloudSubscriber.unpackPointsAndIntensities(cloudHolder);
      pointCloud = pointCloudData.getPoints();
      colors = pointCloudData.getPointColors();

      //size reduction
      numberOfPoints = pointCloud.length;
      while (numberOfPoints > MAX_NUMBER_OF_POINTS)
      {
         indexToRemove = random.nextInt(numberOfPoints);
         lastIndex = numberOfPoints - 1;

         pointCloud[indexToRemove] = pointCloud[lastIndex];
         colors[indexToRemove] = colors[lastIndex];

         numberOfPoints--;
      }

      //conversion
      timestamp = cloudHolder.getHeader().getStamp().totalNsecs();
      pointCloudBuffer = new float[3 * numberOfPoints];
      colorsInteger = new int[numberOfPoints];

      for (int i = 0; i < numberOfPoints; i++)
      {
         scanPoint = pointCloud[i];

         pointCloudBuffer[3 * i + 0] = (float) scanPoint.getX();
         pointCloudBuffer[3 * i + 1] = (float) scanPoint.getY();
         pointCloudBuffer[3 * i + 2] = (float) scanPoint.getZ();

         colorsInteger[i] = colors[i].getRGB();
      }

      stereoVisionMessage = MessageTools.createStereoVisionPointCloudMessage(timestamp, pointCloudBuffer, colorsInteger);

      //publishing
      stereoVisionPublisher.publish(stereoVisionMessage);
   }
}