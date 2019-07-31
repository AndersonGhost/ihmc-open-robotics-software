package us.ihmc.robotEnvironmentAwareness.fusion.data;

import java.util.ArrayList;
import java.util.List;

import gnu.trove.list.array.TIntArrayList;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.interfaces.Point2DReadOnly;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.robotics.linearAlgebra.PrincipalComponentAnalysis3D;

/**
 * This data set includes points which are in a superpixel.
 * The data has its own id and basic planar region information such as center and normal.
 * The adjacent score is to determine which segments are adjacent sufficiently.
 */
public class RawSuperPixelData implements SuperPixelData
{
   public static final int DEFAULT_SEGMENT_ID = -1;
   private int id = DEFAULT_SEGMENT_ID;

   private final int imageSegmentLabel;
   private final TIntArrayList adjacentSegmentLabels = new TIntArrayList();
   private final List<Point3D> points = new ArrayList<>();

   private final Point2D segmentCenterInImage = new Point2D();

   private final Point3D center = new Point3D();
   private final Vector3D normal = new Vector3D();

   private final Vector3D standardDeviation = new Vector3D();
   private double normalVariance = Double.NaN;
   private int normalConsensus = Integer.MIN_VALUE;

   private boolean isSparse = true;

   private static final boolean useAdjacentScore = true;
   private static final int numberOfAdjacentPixels = 10;
   private final TIntArrayList adjacentScore = new TIntArrayList();

   public RawSuperPixelData(int labelID)
   {
      imageSegmentLabel = labelID;

      center.setToNaN();
      normal.setToNaN();
      standardDeviation.setToNaN();
   }

   @Override
   public Point3DReadOnly getCenter()
   {
      return center;
   }

   @Override
   public Vector3DReadOnly getNormal()
   {
      return normal;
   }

   @Override
   public void setCenter(Point3DReadOnly center)
   {
      this.center.set(center);
   }

   @Override
   public void setNormal(Vector3DReadOnly normal)
   {
      this.normal.set(normal);
   }

   @Override
   public void setStandardDeviation(Vector3DReadOnly standardDeviation)
   {
      this.standardDeviation.set(standardDeviation);
   }

   @Override
   public void setNormalQuality(double normalVariance, int normalConsensus)
   {
      this.normalVariance = normalVariance;
      this.normalConsensus = normalConsensus;
   }

   public boolean contains(int otherLabel)
   {
      if (useAdjacentScore)
      {
         for (int i = 0; i < adjacentSegmentLabels.size(); i++)
         {
            if (adjacentSegmentLabels.get(i) == otherLabel)
            {
               adjacentScore.replace(i, adjacentScore.get(i) + 1);
               return true;
            }
         }
         return false;
      }
      else
      {
         return adjacentSegmentLabels.contains(otherLabel);
      }
   }

   public void addAdjacentSegmentLabel(int otherLabel)
   {
      adjacentSegmentLabels.add(otherLabel);
      adjacentScore.add(1);
   }

   public void addPoint(Point3D point)
   {
      points.add(point);
   }

   public void filteringFlyingPoints(double threshold, int neighborsThreshold)
   {
      List<Point3D> filteredPoints = new ArrayList<>();
      for (Point3D point : points)
      {
         double closestDistance = Double.POSITIVE_INFINITY;
         int numberOfNeighbors = 0;
         for (Point3D otherPoint : points)
         {
            double distance = point.distance(otherPoint);

            if (distance < closestDistance)
            {
               if (point != otherPoint)
                  closestDistance = distance;
            }
            if (distance < threshold)
            {
               numberOfNeighbors++;
            }
         }
         if (closestDistance < threshold && numberOfNeighbors > neighborsThreshold)
            filteredPoints.add(point);
      }
      points.clear();
      points.addAll(filteredPoints);
   }

   public void update()
   {
      if (useAdjacentScore)
      {
         TIntArrayList newAdjacentSegmentLabels = new TIntArrayList();
         TIntArrayList newAdjacentScore = new TIntArrayList();
         for (int i = 0; i < adjacentSegmentLabels.size(); i++)
         {
            if (adjacentScore.get(i) > numberOfAdjacentPixels)
            {
               newAdjacentSegmentLabels.add(adjacentSegmentLabels.get(i));
               newAdjacentScore.add(adjacentScore.get(i));
            }
         }
         adjacentSegmentLabels.clear();
         adjacentSegmentLabels.addAll(newAdjacentSegmentLabels);
         adjacentScore.clear();
         adjacentScore.addAll(newAdjacentScore);
      }
   }

   public void updateSparsity(double threshold)
   {
      isSparse = standardDeviation.getZ() > threshold;
   }

   /**
    * Not to be sparse,
    * The number of points inside the area within a radius from the center should be over the ratio to all points in segment.
    * @param radius
    * @param threshold
    */
   public void filteringCentrality(double radius, double threshold)
   {
      if (!isSparse)
      {
         int numberOfInliers = 0;
         for (Point3D point : points)
         {
            double distance = center.distance(point);
            if (distance < radius)
               numberOfInliers++;
         }

         if (numberOfInliers < threshold * getWeight())
         {
            isSparse = true;
         }
      }
   }

   /**
    * Not to be sparse,
    * Secondary axis should be over the minLength.
    * Secondary axis should be within threshold * primary axis length.
    * @param minLength
    * @param threshold
    */
   public void filteringEllipticity(double minLength, double threshold)
   {
      if (!isSparse)
      {
         double lengthPrimary = standardDeviation.getX();
         double lengthSecondary = standardDeviation.getY();
         if (lengthSecondary < minLength || lengthSecondary > lengthPrimary * threshold)
         {
            isSparse = true;
         }
      }
   }

   public void setId(int id)
   {
      this.id = id;
   }

   public void setSegmentCenter(int u, int v)
   {
      segmentCenterInImage.set(u, v);
   }

   public Point2DReadOnly getSegmentCenter()
   {
      return segmentCenterInImage;
   }

   public boolean isSparse()
   {
      return isSparse;
   }

   public int[] getAdjacentSegmentLabels()
   {
      return adjacentSegmentLabels.toArray();
   }

   public double getWeight()
   {
      return (double) points.size();
   }



   public int getId()
   {
      return id;
   }

   public int getImageSegmentLabel()
   {
      return imageSegmentLabel;
   }

   public List<Point3D> getPoints()
   {
      return points;
   }
}
