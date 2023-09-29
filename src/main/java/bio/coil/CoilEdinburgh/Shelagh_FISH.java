/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package bio.coil.CoilEdinburgh;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.ChannelSplitter;
import ij.plugin.ZProjector;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import io.scif.*;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.MaskInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import java.io.IOException;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * </p>
 * <p>
 * You should replace the parameter fields with your own inputs and outputs,
 * and replace the {@link run} method implementation with your own logic.
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Users Plugins>Shelagh FISH")
public class Shelagh_FISH<T extends RealType<T>> implements Command {
    //
    // Feel free to add more parameters here...
    //
    @Parameter
    private FormatService formatService;

    @Parameter
    private DatasetIOService datasetIOService;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService ops;

    @Parameter
    private ROIService roiService;

    @Parameter(label = "File: ")
    public File file;

    @Parameter(label = "FISH Tolerance: ", style="directory")
    public double tolerance;

    RoiManager roiManager;
    double pixelWidth;
    double pixelHeight;
    double pixelDepth;
    ImagePlus projDAPI;
    String newDirectory;

    @Override
    public void run() {

        roiManager = new RoiManager();
        IJ.open(file.toString());
        ImagePlus imp = WindowManager.getCurrentImage();
        ImagePlus[] channels = ChannelSplitter.split(imp);
        ImagePlus impDAPI = channels[2];
        ImagePlus impFISH = channels[1];

        String directory = file.getAbsolutePath();
        newDirectory = directory.substring(0, directory.lastIndexOf(".")) + "_Output";
        new File(newDirectory).mkdir();

        IJ.run(imp, "Enhance Contrast", "saturated=0.35");
        pixelWidth = impDAPI.getCalibration().pixelWidth;
        pixelHeight = impDAPI.getCalibration().pixelHeight;
        pixelDepth = impDAPI.getCalibration().pixelDepth;

        double[][] xyGreen = findXYpositions(impFISH,"Green", tolerance);
        double[][] xyzGreen = findZPositions(impFISH, xyGreen);

        Roi[] cellOutlines = findCellOutlines(impDAPI);
        double[][] xyzCellGreen = whichCell(xyzGreen,cellOutlines);

        Roi[][] cell3D = get3DCellROIs(cellOutlines, impDAPI);

        ArrayList<Double> output = new ArrayList<>();

        double[][] distances = findDistance(xyzCellGreen,cell3D, cellOutlines);
        makeSlices(impFISH, impDAPI, distances, xyzCellGreen);
        projDAPI.setTitle("DAPI_proj");
        ImagePlus projFISH = ZProjector.run(impFISH, "max");
        projFISH.show();
        projFISH.setTitle("projFISH");
        IJ.run("Merge Channels...", "c2=[projFISH] c4=[DAPI_proj] create");
        ImagePlus xyOutput = WindowManager.getCurrentImage();
        xyOutput.setTitle("XY_Overview");
        impFISH.close();
        impDAPI.close();
        projDAPI.close();
        projFISH.close();
        String CreateName = Paths.get( newDirectory,xyOutput.getShortTitle()).toString();
        IJ.saveAs(xyOutput, "Tiff", CreateName);
        makeResultsFile(xyzCellGreen, distances);
    }

    private void makeResultsFile( double[][] xyzCell, double[][] distances){

        String CreateName = Paths.get( newDirectory , "Distances.csv").toString();
        File resultsFile = new File(CreateName);


        int i = 1;
        while (resultsFile.exists()){
            CreateName= Paths.get( newDirectory,"Distances_"+i+".csv").toString();
            resultsFile = new File(CreateName);
            i++;
        }
        IJ.log(CreateName);
        try{
            FileWriter fileWriter = new FileWriter(CreateName,true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write("File= " + file.getName());
            bufferedWriter.newLine();
            bufferedWriter.write("Green Threshold:, " + tolerance );
            bufferedWriter.newLine();
            bufferedWriter.write("Spot, Cell, Distance");
            bufferedWriter.newLine();
            for(int j=0; j<distances.length;j++) {
                if (distances[j][1]!=0) {
                    bufferedWriter.write( j + ","+xyzCell[j][3]+ ","+ distances[j][7]);
                    bufferedWriter.newLine();
                }
            }
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
    }

    private void makeSlices(ImagePlus impFISH, ImagePlus impDAPI, double[][] distances, double[][] xyzSpot){
        IJ.run(impFISH, "Subtract Background...", "rolling=150 stack");
        impFISH.setTitle("FISH");
        impFISH.show();
        impDAPI.setTitle("DAPI");

        for (int i = 0; i< distances.length; i++ ){
            if(distances[i][0]!=0){
                Line lineScan = new Line(distances[i][0],distances[i][1],distances[i][2],distances[i][3]);
                impDAPI.setRoi(lineScan);
                IJ.run(impDAPI, "Reslice [/]...", "output="+pixelDepth+" slice_count=1");
                ImagePlus impDAPISlice = WindowManager.getCurrentImage();
                impDAPISlice.setTitle("DAPI_Slice");
                IJ.run(impDAPISlice, "Enhance Contrast", "saturated=0.35");
                impFISH.setRoi(lineScan);
                IJ.run(impFISH, "Reslice [/]...", "output="+pixelDepth+" slice_count=1");
                ImagePlus impFishSlice = WindowManager.getCurrentImage();
                setImageNumbersLinesSlice(impFishSlice,distances[i],xyzSpot[i], i);
                impFishSlice.setTitle("FISH_Slice");
                IJ.run(impFishSlice, "Enhance Contrast", "saturated=0.35");
                IJ.run("Merge Channels...", "c2=[FISH_Slice] c4=[DAPI_Slice] create");
                ImagePlus mergedSlice = WindowManager.getCurrentImage();
                String CreateName = Paths.get( newDirectory,i+"_Z_slice").toString();
                IJ.saveAs(mergedSlice, "Tiff", CreateName);
            }
        }
    }

    private void setImageNumbersLinesSlice(ImagePlus imp, double[] distances, double[] spot, int i){
        IJ.setForegroundColor(255, 255, 255);
        double resliceRatio = pixelWidth/pixelDepth;
        int y = imp.getDimensions()[1];
        ImageProcessor ip = imp.getProcessor();
        Font font = new Font("SansSerif", Font.BOLD, 10);
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.HALF_DOWN);
        ip.setFont(font);
        ip.setColor(Color.white);
        String number = df.format(distances[7])+" \u00B5m" ;
        String spotNumber = String.valueOf(i);
        double delta_x = distances[0]-distances[4];
        double delta_y = distances[1]-distances[5];
        double delta_Spotx = distances[0]-spot[0];
        double delta_Spoty = distances[1]-spot[1];
        int xpos = (int) Math.sqrt((delta_x*delta_x + delta_y*delta_y));
        int ypos = (int) (distances[6]/resliceRatio);
        int spotx = (int) Math.sqrt((delta_Spotx*delta_Spotx + delta_Spoty*delta_Spoty));
        int spoty = (int)(spot[2]/resliceRatio);
        //IJ.log(spospot[3]+ " "+ distances[7]);
        ip.drawString(number, xpos, ypos);
        ip.drawString(spotNumber, 10, 20);
        ip.setLineWidth(1);
        ip.drawLine(spotx,spoty,xpos,ypos);
        imp.updateAndDraw();

    }

    private double[][] findXYpositions(ImagePlus channel,String name, double tolerance) {
        ImagePlus channelZproject = ZProjector.run(channel, "max");
        channelZproject.setTitle(name+" Z-project");
        channelZproject.show();
        ImageProcessor ip = channelZproject.getProcessor();
        MaximumFinder maxFinder = new MaximumFinder();
        Polygon maxima = maxFinder.getMaxima(ip, tolerance, true);
        int[] x = maxima.xpoints;
        int[] y = maxima.ypoints;
        int n = maxima.npoints;
        double[][] xy = new double[n][2];
        for (int i = 0; i < n; i++) {
            xy[i][0] = (double) x[i];
            xy[i][1] = (double) y[i];
        }
        return xy;
    }

    private double[][] findZPositions(ImagePlus channel, double[][] xyPositions) {

        double[][] zPositions = new double[xyPositions.length][3];

        for (int i = 0; i < xyPositions.length; i++) {
            double maxIntensity = 0;
            zPositions[i][0] = xyPositions[i][0];
            zPositions[i][1] = xyPositions[i][1];
            for (int j = 0; j < channel.getNSlices(); j++) {
                channel.setSlice(j);
                PointRoi point = new PointRoi(xyPositions[i][0], xyPositions[i][1]);
                channel.setRoi(point);
                double intensity = channel.getStatistics().max;
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                    zPositions[i][2] = j;
                }

            }
        }
        return zPositions;
    }

    private Roi[] findCellOutlines(ImagePlus channel){

        IJ.run(channel, "Z Project...", "projection=[Max Intensity]");
        projDAPI = WindowManager.getCurrentImage();
        IJ.run("Cellpose Advanced", "diameter=" + 100 + " cellproba_threshold=" + 0.0 + " flow_threshold=" + 0.4
                + " anisotropy=" + 1.0 + " diam_threshold=" + 12.0 +  " model=" + "cyto2" + " nuclei_channel=" + 0
                + " cyto_channel=" + 1 + " dimensionmode=" + "2D" + " stitch_threshold=" + -1 + " omni=" + false
                + " cluster=" + false + " additional_flags="+"" );
        Roi[] cellOutlines = getROIsfromMask();
        return cellOutlines;
    }

    private Roi[] getROIsfromMask() {

        //Gets the current image (the mask output from cellpose)
        ImagePlus mask = WindowManager.getCurrentImage();
        ImageStatistics stats = mask.getStatistics();
        //For each ROI (intensity per cell mask is +1 to intensity
        for (int i = 1; i < stats.max + 1; i++) {
            //Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "add");
        }
        Roi[] outlines = roiManager.getRoisAsArray();
        roiManager.reset();
        return outlines;
    }

    private double[][] whichCell(double[][] xyz, Roi[] outlines) {

        double[][] xyzCell = new double[xyz.length][4];
        for (int i = 0; i < xyz.length; i++) {
            xyzCell[i][0] = xyz[i][0];
            xyzCell[i][1] = xyz[i][1];
            xyzCell[i][2] = xyz[i][2];
            for (int j = 0; j < outlines.length; j++) {
                if (outlines[j].contains((int) xyz[i][0], (int) xyz[i][1])) {
                    xyzCell[i][3] = j + 1;
                }
            }

        }
        return xyzCell;
    }

    private Roi[][] get3DCellROIs(Roi[] outlines, ImagePlus imp){

        imp.show();
        IJ.run(imp, "Subtract Background...", "rolling=150 stack");
        imp.setDisplayMode(IJ.GRAYSCALE);
        ImageProcessor ip = imp.getProcessor();
        imp.setZ(0);
        IJ.setAutoThreshold(imp, "Yen dark no-reset");
        Roi[][] outputCells = new Roi[outlines.length][imp.getNSlices()];
        //for each outline,
        for(int i = 0; i< outlines.length; i++) {
        //Go through the stack
            for (int j = 0; j < imp.getNSlices(); j++) {
                imp.setRoi(outlines[i]);
                imp.setZ(j);
                IJ.run(imp, "Analyze Particles...", "size=100-Infinity pixel add slice");
                Roi[] rois = roiManager.getRoisAsArray();
                Roi output;
                if (rois.length > 0) {
                    output = rois[0];
                    for (Roi roi : rois) {
                        if (roi.getStatistics().area > output.getStatistics().area) {
                            output = roi;
                        }
                    }
                } else {
                    output = new PointRoi(outlines[i].getContourCentroid()[0], outlines[i].getContourCentroid()[1]);
                }
                drawRoi(output,imp,j);
                outputCells[i][j] = output;
                roiManager.reset();
            }
        }

        return outputCells;
        //Return the Array of Roi Arrays
    }

    private double[][] findDistance(double[][] xyzCell, Roi[][] cell3D, Roi[] outline){
        //for each point
        double[][] output = new double[xyzCell.length][8];
        //output 1-4 start and end of the line to be sliced, output 5-7 xyz position of nearest intersect, output 8 distance from spot
        for(int i = 0; i< xyzCell.length; i++) {
            if(xyzCell[i][3]!= 0){
                int cell = (int) xyzCell[i][3]-1;
                double x_spot = xyzCell[i][0];
                double y_spot = xyzCell[i][1];
                double x_outline = outline[cell].getContourCentroid()[0];
                double y_outline = outline[cell].getContourCentroid()[1];
                double[] line = get2Dline(x_spot,y_spot,x_outline,y_outline,100);
                System.arraycopy(line,0, output[i],0,4);
                Line lineScan = new Line(line[0],line[1],line[2],line[3]);
                double[][] intersects = getIntersects(line, cell3D[cell]);
                double[] nearestIntersect = getNearestIntersect(intersects, xyzCell[i], outline[cell]);
                System.arraycopy(nearestIntersect,0, output[i],4,4);
                projDAPI.setRoi(lineScan);
                line[2] = nearestIntersect[0];
                line[3] = nearestIntersect[1];
                setImageNumbersLines(line, i);
            }
            //Find line in z between cell outline centre and the spot
            //For each Roi
            //look along the line for points that cross the Roi
            //if there is a point find the x-y-z distance to the spot
        }
        return output;
    }

    private double[] getNearestIntersect(double[][] intersects, double[] xyzSpot, Roi cell){

        double[] xyzIntersect = new double[4];
        xyzIntersect[3] = Double.MAX_VALUE;
        double xcell = cell.getContourCentroid()[0];
        double ycell = cell.getContourCentroid()[1];
        for(int i = 0; i< intersects.length; i++){
            if(xyzSpot[0]!=xcell || xyzSpot[1]!=ycell) {
                double x = pixelWidth * (xyzSpot[0] - intersects[i][0]);
                double y = pixelHeight * (xyzSpot[1] - intersects[i][1]);
                double z = pixelDepth * (xyzSpot[2] - i);
                double distance = Math.sqrt(x * x + y * y + z * z);
                if (distance < xyzIntersect[3]) {
                    xyzIntersect[0]=intersects[i][0];
                    xyzIntersect[1]=intersects[i][1];
                    xyzIntersect[2]=i;
                    xyzIntersect[3]=distance;
                }
            }
        }
        return xyzIntersect;
    }

    private double[][] getIntersects(double[] line, Roi[] cell){
        double[][] positions = new double[cell.length][2];
        Line lineScan = new Line(line[0], line[1], line[2], line[3]);
        Point[] linePoints = lineScan.getContainedPoints();
        for (int i = 0; i < cell.length; i++) {
            if (cell[i].getType() == 4) {
                boolean changed = false;
                int j = 0;
                boolean start = cell[i].containsPoint(linePoints[0].x, linePoints[0].y);
                while (!changed && j < linePoints.length) {
                    positions[i][0] = linePoints[j].x;
                    positions[i][1] = linePoints[j].y;
                    boolean inside = cell[i].containsPoint(linePoints[j].x, linePoints[j].y);
                    if (start != inside) {
                        changed = true;
                    }
                    j++;
                }
            }
        }
        return positions;
    }

    private double[] get2Dline (double x, double y, double xCell, double yCell, double distance) {

        double angle = Math.atan((yCell-y)/(xCell-x));
        double lineStartY = yCell ;
        double deltaX = distance*Math.cos(angle);
        double deltaY = distance*Math.sin(angle);
        double lineEndY = yCell + deltaY;
        double lineStartX = xCell;
        double lineEndX = xCell + deltaX;

        if ( (x-xCell) < 0 ){
            lineEndX = xCell - deltaX;
            //if((y-yCell) < 0) {
            lineEndY = yCell - deltaY;
            //}
        }

        double[] output = new double[]{lineStartX,lineStartY,lineEndX,lineEndY};

        return output;
    }

    private void drawRoi(Roi roi, ImagePlus imp, int zslice){
        IJ.setForegroundColor(255, 255, 255);
        ImageProcessor ip = imp.getProcessor();
        ip.setSliceNumber(zslice);
        Font font = new Font("SansSerif", Font.BOLD, 10);
        ip.setFont(font);
        ip.setColor(Color.white);
        ip.draw(roi);
        imp.updateAndDraw();
    }

    private void setImageNumbersLines(double[] positions, double i){
        IJ.setForegroundColor(255, 255, 255);
        ImageProcessor ip = projDAPI.getProcessor();
        Font font = new Font("SansSerif", Font.BOLD, 10);
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.HALF_DOWN);
        ip.setFont(font);
        ip.setColor(Color.white);
        String number = df.format(i);
        int xpos = (int) positions[2];
        int ypos = (int) positions[3];
        ip.drawString(number, xpos, ypos);
        ip.setLineWidth(1);
        ip.drawLine((int)positions[0],(int)positions[1],(int)positions[2],(int)positions[3]);

        projDAPI.updateAndDraw();
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(Shelagh_FISH.class, true);
    }

}
