package controller_msgs.msg.dds;

/**
 * Topic data type of the struct "HeightQuadTreeMessage" defined in "HeightQuadTreeMessage_.idl". Use this class to provide the TopicDataType to a Participant.
 *
 * This file was automatically generated from HeightQuadTreeMessage_.idl by us.ihmc.idl.generator.IDLGenerator.
 * Do not update this file directly, edit HeightQuadTreeMessage_.idl instead.
 */
public class HeightQuadTreeMessagePubSubType implements us.ihmc.pubsub.TopicDataType<controller_msgs.msg.dds.HeightQuadTreeMessage>
{
   public static final java.lang.String name = "controller_msgs::msg::dds_::HeightQuadTreeMessage_";
   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   public HeightQuadTreeMessagePubSubType()
   {

   }

   public static int getMaxCdrSerializedSize()
   {
      return getMaxCdrSerializedSize(0);
   }

   public static int getMaxCdrSerializedSize(int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      for (int a = 0; a < 5000; ++a)
      {
         current_alignment += controller_msgs.msg.dds.HeightQuadTreeLeafMessagePubSubType.getMaxCdrSerializedSize(current_alignment);
      }

      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(controller_msgs.msg.dds.HeightQuadTreeMessage data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(controller_msgs.msg.dds.HeightQuadTreeMessage data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);

      current_alignment += 4 + us.ihmc.idl.CDR.alignment(current_alignment, 4);
      for (int a = 0; a < data.getLeaves().size(); ++a)
      {
         current_alignment += controller_msgs.msg.dds.HeightQuadTreeLeafMessagePubSubType.getCdrSerializedSize(data.getLeaves().get(a), current_alignment);
      }

      return current_alignment - initial_alignment;
   }

   public static void write(controller_msgs.msg.dds.HeightQuadTreeMessage data, us.ihmc.idl.CDR cdr)
   {

      cdr.write_type_5(data.getDefaultHeight());

      cdr.write_type_5(data.getResolution());

      cdr.write_type_5(data.getXSize());

      cdr.write_type_5(data.getYSize());

      if (data.getLeaves().size() <= 5000)
         cdr.write_type_e(data.getLeaves());
      else
         throw new RuntimeException("leaves field exceeds the maximum length");
   }

   public static void read(controller_msgs.msg.dds.HeightQuadTreeMessage data, us.ihmc.idl.CDR cdr)
   {

      data.setDefaultHeight(cdr.read_type_5());

      data.setResolution(cdr.read_type_5());

      data.setXSize(cdr.read_type_5());

      data.setYSize(cdr.read_type_5());

      cdr.read_type_e(data.getLeaves());
   }

   public static void staticCopy(controller_msgs.msg.dds.HeightQuadTreeMessage src, controller_msgs.msg.dds.HeightQuadTreeMessage dest)
   {
      dest.set(src);
   }

   @Override
   public void serialize(controller_msgs.msg.dds.HeightQuadTreeMessage data, us.ihmc.pubsub.common.SerializedPayload serializedPayload)
         throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, controller_msgs.msg.dds.HeightQuadTreeMessage data)
         throws java.io.IOException
   {
      deserializeCDR.deserialize(serializedPayload);
      read(data, deserializeCDR);
      deserializeCDR.finishDeserialize();
   }

   @Override
   public final void serialize(controller_msgs.msg.dds.HeightQuadTreeMessage data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_5("default_height", data.getDefaultHeight());

      ser.write_type_5("resolution", data.getResolution());

      ser.write_type_5("x_size", data.getXSize());

      ser.write_type_5("y_size", data.getYSize());

      ser.write_type_e("leaves", data.getLeaves());
   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, controller_msgs.msg.dds.HeightQuadTreeMessage data)
   {
      data.setDefaultHeight(ser.read_type_5("default_height"));

      data.setResolution(ser.read_type_5("resolution"));

      data.setXSize(ser.read_type_5("x_size"));

      data.setYSize(ser.read_type_5("y_size"));

      ser.read_type_e("leaves", data.getLeaves());
   }

   @Override
   public controller_msgs.msg.dds.HeightQuadTreeMessage createData()
   {
      return new controller_msgs.msg.dds.HeightQuadTreeMessage();
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

   public void serialize(controller_msgs.msg.dds.HeightQuadTreeMessage data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(controller_msgs.msg.dds.HeightQuadTreeMessage data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }

   public void copy(controller_msgs.msg.dds.HeightQuadTreeMessage src, controller_msgs.msg.dds.HeightQuadTreeMessage dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public HeightQuadTreeMessagePubSubType newInstance()
   {
      return new HeightQuadTreeMessagePubSubType();
   }
}