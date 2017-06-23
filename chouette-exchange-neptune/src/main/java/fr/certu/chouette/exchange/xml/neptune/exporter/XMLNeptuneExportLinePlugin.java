package fr.certu.chouette.exchange.xml.neptune.exporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.Setter;

import org.apache.log4j.Logger;

import chouette.schema.ChouetteArea;
import chouette.schema.ChouetteLineDescription;
import chouette.schema.ChouettePTNetwork;
import chouette.schema.ChouettePTNetworkTypeType;
import chouette.schema.ITL;
import fr.certu.chouette.common.ChouetteException;
import fr.certu.chouette.exchange.xml.neptune.exception.ExchangeExceptionCode;
import fr.certu.chouette.exchange.xml.neptune.exception.ExchangeRuntimeException;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.AreaCentroidProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.CompanyProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.ConnectionLinkProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.JourneyPatternProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.LineProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.PTLinkProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.PTNetworkProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.RouteProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.RoutingConstraintProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.StopAreaProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.StopPointProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.TimetableProducer;
import fr.certu.chouette.exchange.xml.neptune.exporter.producer.VehicleJourneyProducer;
import fr.certu.chouette.exchange.xml.neptune.report.NeptuneReport;
import fr.certu.chouette.model.neptune.AreaCentroid;
import fr.certu.chouette.model.neptune.Company;
import fr.certu.chouette.model.neptune.ConnectionLink;
import fr.certu.chouette.model.neptune.JourneyPattern;
import fr.certu.chouette.model.neptune.Line;
import fr.certu.chouette.model.neptune.PTLink;
import fr.certu.chouette.model.neptune.Route;
import fr.certu.chouette.model.neptune.StopArea;
import fr.certu.chouette.model.neptune.StopPoint;
import fr.certu.chouette.model.neptune.Timetable;
import fr.certu.chouette.model.neptune.VehicleJourney;
import fr.certu.chouette.model.neptune.type.ChouetteAreaEnum;
import fr.certu.chouette.plugin.exchange.FormatDescription;
import fr.certu.chouette.plugin.exchange.IExportPlugin;
import fr.certu.chouette.plugin.exchange.ParameterDescription;
import fr.certu.chouette.plugin.exchange.ParameterValue;
import fr.certu.chouette.plugin.exchange.SimpleParameterValue;
import fr.certu.chouette.plugin.report.Report;
import fr.certu.chouette.plugin.report.ReportHolder;

public class XMLNeptuneExportLinePlugin implements IExportPlugin<Line>
{

   private static final Logger       logger = Logger.getLogger(XMLNeptuneExportLinePlugin.class);

   private FormatDescription         description;
   @Setter
   private LineProducer              lineProducer;
   @Setter
   private PTNetworkProducer         networkProducer;
   @Setter
   private RouteProducer             routeProducer;
   @Setter
   private JourneyPatternProducer    journeyPatternProducer;
   @Setter
   private VehicleJourneyProducer    vehicleJourneyProducer;
   @Setter
   private StopPointProducer         stopPointProducer;
   @Setter
   private PTLinkProducer            ptLinkProducer;
   @Setter
   private CompanyProducer           companyProducer;
   @Setter
   private StopAreaProducer          stopAreaProducer;
   @Setter
   private AreaCentroidProducer      areaCentroidProducer;
   @Setter
   private ConnectionLinkProducer    connectionLinkProducer;
   @Setter
   private TimetableProducer         timetableProducer;
   @Setter
   private RoutingConstraintProducer routingConstraintProducer;

   public XMLNeptuneExportLinePlugin()
   {
      description = new FormatDescription(this.getClass().getName());
      description.setName("NEPTUNE");
      List<ParameterDescription> params = new ArrayList<ParameterDescription>();
      ParameterDescription param1 = new ParameterDescription("outputFile", ParameterDescription.TYPE.FILEPATH, false,
            true);
      param1.setAllowedExtensions(Arrays.asList(new String[] { "xml", "zip" }));
      params.add(param1);
      description.setParameterDescriptions(params);
   }

   @Override
   public FormatDescription getDescription()
   {
      return description;
   }

   @Override
   public void doExport(List<Line> beans, List<ParameterValue> parameters, ReportHolder reportContainer)
   throws ChouetteException
   {
      NeptuneReport report = new NeptuneReport(NeptuneReport.KEY.EXPORT);
      report.setStatus(Report.STATE.OK);
      reportContainer.setReport(report);

      String fileName = null;

      if (beans == null)
      {
         throw new IllegalArgumentException("no beans to export");
      }

      for (ParameterValue value : parameters)
      {
         if (value instanceof SimpleParameterValue)
         {
            SimpleParameterValue svalue = (SimpleParameterValue) value;
            if (svalue.getName().equals("outputFile"))
            {
               fileName = svalue.getFilepathValue();
               if (fileName == null) 
               {
                  logger.warn("outputFile changed as FILEPATH type");
                  fileName = svalue.getFilenameValue();
               }
            }

         }
      }
      if (fileName == null)
      {
         throw new IllegalArgumentException("outputFile required");
      }

      String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

      if (beans.size() > 1 && fileExtension.equals("xml"))
      {
         throw new IllegalArgumentException("cannot export multiple lines in one XML file");
      }

      NeptuneFileWriter neptuneFileWriter = new NeptuneFileWriter();
      File outputFile = new File(fileName);
      if (!outputFile.getParentFile().exists())
      {
         outputFile.getParentFile().mkdirs();
      }
      if (fileExtension.equals("xml"))
      {

         ChouettePTNetworkTypeType rootObject = exportLine(beans.get(0));

         neptuneFileWriter.write(rootObject, outputFile);
      }
      else
      {

         try
         {
            // Create the ZIP file
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(fileName));

            // Compress the files
            for (Iterator<Line> iterator = beans.iterator(); iterator.hasNext();)
            {
               Line line = iterator.next();
               iterator.remove();

               ChouettePTNetworkTypeType rootObject = exportLine(line);

               logger.info("exporting "+line.getName()+" ("+line.getObjectId()+")");
               
               String name = line.getObjectId().split(":")[2];

               ByteArrayOutputStream stream = new ByteArrayOutputStream();
               neptuneFileWriter.write(rootObject, stream);

               // Add ZIP entry to output stream.
               ZipEntry entry = new ZipEntry(name + ".xml");
               out.putNextEntry(entry);

               out.write(stream.toByteArray());

               // Complete the entry
               out.closeEntry();
               System.gc();

            }

            // Complete the ZIP file
            out.close();
         }
         catch (IOException e)
         {
            logger.error("cannot create zip file", e);
            throw new ExchangeRuntimeException(ExchangeExceptionCode.ERR_XML_WRITE, e);
         }
      }
   }

   private ChouettePTNetworkTypeType exportLine(Line line)
   {
      ChouettePTNetwork rootObject = new ChouettePTNetwork();

      if (line != null)
      {
         line.complete();
         if (line.getPtNetwork() != null)
         {
            rootObject.setPTNetwork(networkProducer.produce(line.getPtNetwork()));
         }

         HashSet<Company> companies = new HashSet<Company>();
         if (line.getCompany() != null)
         {
            companies.add(line.getCompany());
         }

         ChouetteLineDescription chouetteLineDescription = new ChouetteLineDescription();
         chouette.schema.Line castorLine = lineProducer.produce(line);
         chouetteLineDescription.setLine(castorLine);

         HashSet<JourneyPattern> journeyPatterns = new HashSet<JourneyPattern>();
         HashSet<PTLink> ptLinks = new HashSet<PTLink>();
         for (Route route : line.getRoutes())
         {
            chouetteLineDescription.addChouetteRoute(routeProducer.produce(route));
            if (route.getJourneyPatterns() != null)
            {
               journeyPatterns.addAll(route.getJourneyPatterns());
            }
            if (route.getPtLinks() != null)
            {
               ptLinks.addAll(route.getPtLinks());
            }
         }

         HashSet<VehicleJourney> vehicleJourneys = new HashSet<VehicleJourney>();
         HashSet<StopPoint> stopPoints = new HashSet<StopPoint>();
         for (JourneyPattern journeyPattern : journeyPatterns)
         {
            chouetteLineDescription.addJourneyPattern(journeyPatternProducer.produce(journeyPattern));
            if (journeyPattern.getVehicleJourneys() != null)
            {
               vehicleJourneys.addAll(journeyPattern.getVehicleJourneys());
            }
            if (journeyPattern.getStopPoints() != null)
            {
               stopPoints.addAll(journeyPattern.getStopPoints());
            }
         }

         HashSet<Timetable> timetables = new HashSet<Timetable>();
         HashSet<String> vehicleJourneyObjectIds = new HashSet<String>();
         for (VehicleJourney vehicleJourney : vehicleJourneys)
         {
            vehicleJourneyObjectIds.add(vehicleJourney.getObjectId());
            if (vehicleJourney.getCompany() != null)
            {
               companies.add(vehicleJourney.getCompany());
            }
            else
            {
               vehicleJourney.setCompany(line.getCompany());
            }
            chouetteLineDescription.addVehicleJourney(vehicleJourneyProducer.produce(vehicleJourney));
            if (vehicleJourney.getTimetables() != null)
            {
               timetables.addAll(vehicleJourney.getTimetables());
            }
         }

         HashSet<StopArea> stopAreas = new HashSet<StopArea>();
         HashSet<String> stopRefs = new HashSet<String>(); // for cleaning
         // stoparea contains
         // refs
         for (StopPoint stopPoint : stopPoints)
         {
            stopRefs.add(stopPoint.getObjectId());
            chouetteLineDescription.addStopPoint(stopPointProducer.produce(stopPoint));
            stopAreas.addAll(extractStopAreaHierarchy(stopPoint.getContainedInStopArea(), line));
         }

         for (PTLink ptLink : ptLinks)
         {
            chouetteLineDescription.addPtLink(ptLinkProducer.produce(ptLink));
         }

         for (Company company : companies)
         {
            rootObject.addCompany(companyProducer.produce(company));
         }

         ChouetteArea chouetteArea = new ChouetteArea();
         HashSet<AreaCentroid> areaCentroids = new HashSet<AreaCentroid>();
         HashSet<ConnectionLink> connectionLinks = new HashSet<ConnectionLink>();
         for (StopArea stopArea : stopAreas)
         {
            stopRefs.add(stopArea.getObjectId());
         }
         for (StopArea stopArea : stopAreas)
         {
            chouette.schema.StopArea chouetteStopArea = stopAreaProducer.produce(stopArea);
            // remove external stopPoints or stopareas
            List<String> pointRefs = chouetteStopArea.getContainsAsReference();
            for (Iterator<String> iterator = pointRefs.iterator(); iterator.hasNext();)
            {
               String ref = iterator.next();
               if (!stopRefs.contains(ref))
               {
                  iterator.remove();
               }

            }
            chouetteArea.addStopArea(chouetteStopArea);
            if (stopArea.getAreaCentroid() != null)
            {
               areaCentroids.add(stopArea.getAreaCentroid());
            }
            if (stopArea.getConnectionLinks() != null)
            {
               connectionLinks.addAll(stopArea.getConnectionLinks());
            }
         }

         for (AreaCentroid areaCentroid : areaCentroids)
         {
            chouetteArea.addAreaCentroid(areaCentroidProducer.produce(areaCentroid));
         }

         rootObject.setChouetteArea(chouetteArea);

         for (ConnectionLink connectionLink : connectionLinks)
         {
            rootObject.addConnectionLink(connectionLinkProducer.produce(connectionLink));
         }

         for (Timetable timetable : timetables)
         {
            rootObject.addTimetable(timetableProducer.produce(timetable));
         }

         // routing Constraints 
         if (line.getRoutingConstraints() != null)
         {
            for (StopArea routingConstraint : line.getRoutingConstraints())
            {
               if (stopRefs.contains(routingConstraint.getObjectId()))
               {
                  ITL castorITL = routingConstraintProducer.produceITL(line, routingConstraint);
                  chouetteLineDescription.addITL(castorITL);
               }
               else
               {
                  // TODO ? routing constraint without stop on line
               }
            }
         }

         rootObject.setChouetteLineDescription(chouetteLineDescription);

         // cleaning a little
         rootObject.getPTNetwork().removeAllLineId();
         rootObject.getPTNetwork().addLineId(chouetteLineDescription.getLine().getObjectId());

         // remove unreferenced vj from timetables
         for (chouette.schema.Timetable timetable : rootObject.getTimetable())
         {
            List<String> vjs = timetable.getVehicleJourneyIdAsReference();
            List<String> ids = new ArrayList<String>();
            ids.addAll(vjs);
            for (String id : ids)
            {
               if (!vehicleJourneyObjectIds.contains(id))
               {
                  timetable.removeVehicleJourneyId(id);
               }
            }
         }


      }

      return rootObject;
   }

   private List<StopArea> extractStopAreaHierarchy(StopArea stopArea, Line line)
   {
      List<StopArea> stopAreas = new ArrayList<StopArea>();
      if (stopArea != null)
      {
         if (stopArea.getAreaType().equals(ChouetteAreaEnum.ITL))
         {
            boolean validRestrictionConstraint = false;
            for (Line routingLine : stopArea.getRoutingConstraintLines())
            {
               if (line.equals(routingLine))
               {
                  validRestrictionConstraint = true;
                  break;
               }
            }
            if (!validRestrictionConstraint)
               return stopAreas;
         }
         // logger.debug("add StopArea " + stopArea.getObjectId());
         stopAreas.add(stopArea);
         if (stopArea.getParents() != null)
         {
            for (StopArea parent : stopArea.getParents())
            {
               stopAreas.addAll(extractStopAreaHierarchy(parent, line));
            }
         }
      }

      return stopAreas;
   }
}
