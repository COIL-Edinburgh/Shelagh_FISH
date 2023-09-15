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
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import io.scif.*;
import io.scif.bf.BioFormatsFormat;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import loci.common.Location;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
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
import java.io.IOException;
import java.nio.file.Paths;
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
@Plugin(type = Command.class, menuPath = "Plugins>Users Plugins>Thom Titans")
public class Thom_Titans<T extends RealType<T>> implements Command {
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

    @Parameter(label = "Open Folder: ", style="directory")
    public File filePath;

    @Parameter(label = "Model Path: ")
    public File modelpath;

    RoiManager roiManager;
    double pixelSize;

    String filename;
    @Override
    public void run() {

            File[] files = filePath.listFiles();
            roiManager = new RoiManager();
            boolean first = true;
            for (File file : files) {
                if (file.toString().contains(".nd2") && !file.toString().contains(".nd2 ")) {
                    //Open file and get filename and filepath
                    Img<T> img = openDataset(file);
                    uiService.show(img);
                    filename = FilenameUtils.removeExtension(file.getName());
                    String model = " model_path= "+modelpath.toString();
//                    if(first){
//                    //IJ.run( ImageJFunctions.wrap(img, "test"), "Cellpose setup...", "");
//                    first=false;}

                    IJ.run("Cellpose Advanced", "diameter=" + 60 + " cellproba_threshold=" + 0.0 + " flow_threshold=" + 0.4
                            + " anisotropy=" + 1.0 + " diam_threshold=" + 12.0 + " model=" + "cyto2" + " nuclei_channel=" + 0
                            + " cyto_channel=" + 1 + " dimensionmode=" + "2D" + " stitch_threshold=" + -1 + " omni=" + false
                            + " cluster=" + false + " additional_flags=" + "");
                    getROIsfromMask();
                    Roi[] outlines = roiManager.getRoisAsArray();
                    roiManager.reset();

                    uiService.show(img);
                    //IJ.run("Cellpose Advanced (custom model)", "diameter=" + 20 + " cellproba_threshold=" + 0.0 + " flow_threshold=" + 0.4
                    //        + " anisotropy=" + 1.0 + " diam_threshold=" + 12.0 +  model + " model=" + "own_cyto2_model" +" nuclei_channel=" + 0
                    //        + " cyto_channel=" + 1 + " dimensionmode=" + "2D" + " stitch_threshold=" + -1 + " omni=" + false
                    //        + " cluster=" + false  + " additional_flags=" + "");
                    IJ.run("Cellpose Advanced (custom model)", "diameter=30 cellproba_threshold=0.0 flow_threshold=0.4 " +
                            "anisotropy=1.0 diam_threshold=12.0 model_path="+modelpath + "model="+modelpath+" " +
                            "nuclei_channel=0 cyto_channel=1 dimensionmode=2D " +
                            "stitch_threshold=-1.0 omni=false cluster=false additional_flags=");
                    getROIsfromMask();
                    Roi[] outlines2 = roiManager.getRoisAsArray();
                    roiManager.reset();

                    boolean[] budding = isBudding(outlines,outlines2);
                    try {
                        MakeResults(budding,outlines2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    ImagePlus output = ImageJFunctions.wrap(img, "Output");
                    output.show();
                    for (int i = 0; i<outlines2.length;i++){
                        drawNumbers(i+1,output,outlines2[i]);
                    }
                    IJ.save(output, Paths.get(String.valueOf(filePath), filename + "_Overlay.tif").toString());
                    IJ.run("Close All", "");
                }
            }
        }

        private boolean[] isBudding(Roi[] outlines, Roi[] outlines2){
        boolean[] output = new boolean[outlines2.length];
        int[] number = new int[outlines2.length];
        for(int i = 0; i<outlines2.length; i++){
            for (int j = 0; j< outlines.length; j++){
                if(outlines[j].contains((int)outlines2[i].getContourCentroid()[0],(int)outlines2[i].getContourCentroid()[1])){
                    number[i] = j;
                }
            }
        }
        for (int k=0;k<outlines2.length;k++){
            int n = number[k];
            int count=0;
            for (int m= 0; m<outlines2.length;m++){
                if(number[m]==n){
                    count++;
                }
            }
            if (count>1){
                output[k]= true;
            }else {
                output[k]=false;
            }
        }
        return output;
        }

    private void drawNumbers(int Counter, ImagePlus ProjectedWindow, Roi roi) {
        ImageProcessor ip = ProjectedWindow.getProcessor();
        Font font = new Font("SansSerif", Font.PLAIN, 12);
        ip.setFont(font);
        ip.setColor(Color.white);
        String cellnumber = String.valueOf(Counter);
        ip.draw(roi);
        ip.drawString(cellnumber, (int) roi.getContourCentroid()[0], (int) roi.getContourCentroid()[1]);
        ProjectedWindow.updateAndDraw();
    }


    public void MakeResults(boolean[] budding, Roi[] outlines2) throws IOException {
        Date date = new Date(); // This object contains the current date value
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy, hh:mm:ss");
        String CreateName = Paths.get(String.valueOf(filePath), "_Results.csv").toString();
        try {
            FileWriter fileWriter = new FileWriter(CreateName, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write(formatter.format(date));
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write("File, Number, Area_(um^2), LongAxis_(um), Short_Axis_(um), Circularity, Budding");//write header 1
            bufferedWriter.newLine();
            for (int i =0; i < outlines2.length; i++){//for each slice create and write the output string
                bufferedWriter.newLine();
                double circularity = 4 * Math.PI * outlines2[i].getStatistics().area/(outlines2[i].getLength()*outlines2[i].getLength());
                bufferedWriter.write(filename+ ","+(i+1)+","+outlines2[i].getStatistics().area*pixelSize*pixelSize+","+ outlines2[i].getFeretValues()[0]*
                        pixelSize+","+ outlines2[i].getFeretValues()[2]*pixelSize+","+circularity+","+Boolean.toString(budding[i]));
            }
            bufferedWriter.close();
        } catch (IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
    }

    private void cursorOutline(Img<T> image, Roi[] rois, int value){

        for (Roi roi : rois) {
            MaskInterval maskIntKin = roiService.toMaskInterval(roi);
            IterableInterval<T> maskKin = Views.interval(image, maskIntKin);
            net.imglib2.Cursor<T> cursorKin = maskKin.localizingCursor();
            colorIn(maskKin, cursorKin, roi, value);
        }

    }

    private void colorIn(IterableInterval<T> mask, net.imglib2.Cursor<T> cursor, Roi roi, int value ) {
        for (int k = 0; k < mask.size(); k++) {
            //RealType<T> value = cursor.get();
            int x = (int) cursor.positionAsDoubleArray()[0];
            int y = (int) cursor.positionAsDoubleArray()[1];

            //If the pixel is in the bounding ROI
            if (roi.contains(x, y)) {
                cursor.get().setReal(value);
            }
            //Move the cursors forwards
            cursor.fwd();
        }
    }

    //Adds the Masks created by cellpose to the ROI manager
    public void getROIsfromMask() {

        //Gets the current image (the mask output from cellpose)
        ImagePlus mask = WindowManager.getCurrentImage();
        ImageStatistics stats = mask.getStatistics();
        //For each ROI (intensity per cell mask is +1 to intensity
        for (int i = 1; i < stats.max + 1; i++) {
            //Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "add");
        }
    }

        public Img<T> openDataset(File dataset) {
            Dataset imageData = null;
            String filePath = dataset.getPath();
            try {
                imageData = datasetIOService.open(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Map<String, Object> prop = imageData.getProperties();
            DefaultImageMetadata metaData = (DefaultImageMetadata) prop.get("scifio.metadata.image");
            pixelSize = metaData.getAxes().get(0).calibratedValue(1);
            assert imageData != null;

            return (Img<T>)imageData.getImgPlus();
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
        ij.command().run(Thom_Titans.class, true);
    }

}
