package controller_msgs.msg.dds;

/**
 * Topic data type of the struct "Polygon2DMessage" defined in "Polygon2DMessage_.idl". Use this class to provide the TopicDataType to a Participant.
 *
 * This file was automatically generated from Polygon2DMessage_.idl by us.ihmc.idl.generator.IDLGenerator.
 * Do not update this file directly, edit Polygon2DMessage_.idl instead.
 */
public class Polygon2DMessagePubSubType implements us.ihmc.pubsub.TopicDataType<controller_msgs.msg.dds.Polygon2DMessage>
{
   public static final java.lang.String name = "controller_msgs::msg::dds_::Polygon2DMessage_";
   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   public Polygon2DMessagePubSubType()
   {

   }

   public static int getMaxCdrSerializedSize()
   {
      return getMaxCdrSerializedSize(0);
   }

   public static int getMaxCdrSerializedSize(int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += geometry_msgs.msg.dds.PointPubSubType.getMaxCdrSerializedSize(current_alignment);

      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(controller_msgs.msg.dds.Polygon2DMessage data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(controller_msgs.msg.dds.Polygon2DMessage data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += geometry_msgs.msg.dds.PointPubSubType.getCdrSerializedSize(data.getVertices(), current_alignment);

      return current_alignment - initial_alignment;
   }

   public static void write(controller_msgs.msg.dds.Polygon2DMessage data, us.ihmc.idl.CDR cdr)
   {

      geometry_msgs.msg.dds.PointPubSubType.write(data.getVertices(), cdr);
   }

   public static void read(controller_msgs.msg.dds.Polygon2DMessage data, us.ihmc.idl.CDR cdr)
   {

      geometry_msgs.msg.dds.PointPubSubType.read(data.getVertices(), cdr);
   }

   public static void staticCopy(controller_msgs.msg.dds.Polygon2DMessage src, controller_msgs.msg.dds.Polygon2DMessage dest)
   {
      dest.set(src);
   }

   @Override
   public void serialize(controller_msgs.msg.dds.Polygon2DMessage data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, controller_msgs.msg.dds.Polygon2DMessage data) throws java.io.IOException
   {
      deserializeCDR.deserialize(serializedPayload);
      read(data, deserializeCDR);
      deserializeCDR.finishDeserialize();
   }

   @Override
   public final void serialize(controller_msgs.msg.dds.Polygon2DMessage data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_a("vertices", new geometry_msgs.msg.dds.PointPubSubType(), data.getVertices());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, controller_msgs.msg.dds.Polygon2DMessage data)
   {
      ser.read_type_a("vertices", new geometry_msgs.msg.dds.PointPubSubType(), data.getVertices());
   }

   @Override
   public controller_msgs.msg.dds.Polygon2DMessage createData()
   {
      return new controller_msgs.msg.dds.Polygon2DMessage();
   }

   @Override
   public int getTypeSize()
   {
      return us.ihmc.idl.CDR.getTypeSize(getMaxCdrSerializedSize());
   }

   @Override
   public java.lang.String getName()
   {
      return name;
   }

   public void serialize(controller_msgs.msg.dds.Polygon2DMessage data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(controller_msgs.msg.dds.Polygon2DMessage data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }

   public void copy(controller_msgs.msg.dds.Polygon2DMessage src, controller_msgs.msg.dds.Polygon2DMessage dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public Polygon2DMessagePubSubType newInstance()
   {
      return new Polygon2DMessagePubSubType();
   }
}