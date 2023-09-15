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
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

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
    double dist;

    @Override
    public void run() {

        roiManager = new RoiManager();
        boolean first = true;
        IJ.open(file.toString());
        ImagePlus imp = WindowManager.getCurrentImage();
        ImagePlus[] channels = ChannelSplitter.split(imp);
        ImagePlus impDAPI = channels[2];
        ImagePlus impFISH = channels[1];

        String directory = file.getAbsolutePath();
        String newDirectory = directory.substring(0, directory.lastIndexOf(".")) + "_Output";
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
        channel = WindowManager.getCurrentImage();
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

        IJ.run(imp, "Subtract Background...", "rolling=500 stack");
        imp.setDisplayMode(IJ.GRAYSCALE);
        ImageProcessor ip = imp.getProcessor();
        imp.setZ(0);
        IJ.setAutoThreshold(imp, "Default dark no-reset");
        Roi[][] outputCells = new Roi[outlines.length][imp.getNSlices()];
        //for each outline,
        for(int i = 0; i< outlines.length; i++) {
            //Go through the stack
            for (int j = 0; j < imp.getNSlices(); j++) {
                imp.setRoi(outlines[i]);
                imp.setZ(j);
                IJ.run(imp, "Analyze Particles...", "size=100-Infinity pixel add slice");
                Roi[] rois = roiManager.getRoisAsArray();
                Roi output = rois[0];
                if (rois.length > 0) {
                    for (Roi roi : rois) {
                        if (roi.getStatistics().area > output.getStatistics().area) {
                            output = roi;
                        }
                    }
                } else {
                    output = new PointRoi(outlines[i].getContourCentroid()[0], outlines[i].getContourCentroid()[1]);
                }
                outputCells[i][j] = output;
            }
        }

        return outputCells;
        //Return the Array of Roi Arrays
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
