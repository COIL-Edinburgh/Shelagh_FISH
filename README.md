This plugin takes as input a 3+ channel z-stack image with FISH signal in channel 2 and DAPI in channel 3 and finds the 
distance from the spot to the edge of the nucleus.
## Pre-requisites:

To use this script you need to have Cellpose installed on your computer and the BIOP Cellpose wrapper installed in your
IJ plugins. To install Cellpose to use with ImageJ on a windows computer;
- Install Anaconda from https://www.anaconda.com/products/distribution
- Add Anaconda to path https://www.datacamp.com/tutorial/installing-anaconda-windows, you need to have admin rights on
  your computer.
- Install Cellpose https://github.com/MouseLand/cellpose#local-installation
- Add the ijl-utilities-wrapper jar to your ImageJ plugins folder (download the jar here: 
  https://mvnrepository.com/artifact/ch.epfl.biop/ijl-utilities-wrappers/0.3.23)
- BIOP should appear as a plugin, navigate to BIOP -> Cellpose -> Define Env and prefs
- Set CellposeEnvDirectory to your Anaconda\envs\cellpose . On my computer this is 
  C:\Users\username\Anaconda3\envs\cellpose (if this doesn’t exist you have not managed to install Cellpose correctly).
- Set EnvType to conda
- Only UseGpu should be ticked
- You can now use Cellpose through ImageJ

##Method:

The plugin;

- Opens a file (3+ channels) and splits channels, channel 3 should be DAPI and Channel 2 the FISH signal    
- Creates a new folder to save results, the folder will be in the same file as the input image and have the same name. 
- Gets the xyz scale of the image from the metadata
- Finds XYZ positions of green maxima
    - XY positions are found from a maximum z-projection of the FISH channel.
    - Applies Find Maxima... with a user input tolerance (default 2000) 00000to find the x-y positions
    - Z positions are found by determining the slice in the FISH channel with the brightest pixel at the xy position
    - The intensities of the pixels at the xyz position in the FISH and DAPI channels are also recorded.
  
- Finds the XYZ Cell outlines and the intensity statistics per cell in the DAPI channel.
  - Uses the Max Z-projection of the DAPI channel and cellpose cyto2 model (Pachitariu, M. & Stringer, C. (2022). 
    Nature methods) via the BIOP-wrapper (https://github.com/BIOP/ijl-utilities-wrappers) to segment the cell outlines
    in XY.
  - Finds the slice-by-slice ROIs for each cell by first performing a 150 pixel rolling-ball background subtraction to 
    the DAPI channel and then applying Yen threshold to the entire stack. ROIs for each cell were determined as the 
    largest ROI within the bounding outline and above the threshold and drawn with the cell numbers on to the 
    XYZ_CellOutlines.tif. 
  - Stats per cell (MAX, MIN, Mean and Median pixel intensities) were calculated from the pixel values of all ROIs in
    the Z-stack.
    
- Finds which cell each spot belongs to using the XY cell outlines.
- Finds the distances of the spot to the nearest cell edge in XY and then in Z 
- Makes the Z-slice output image stack by performing a reslice along the line from the cell centre to the edge through
  the spot, these lines are shown on the XY_Overview.tif. The reslices are concatenated into a single image, on each 
  reslice the spot number, distance to the edge and the line that corresponds to that distance are displayed.
- Create and Save output images:
    - Merge the DAPI XY_Overview (with distances drawn on) and FISH (green) channel Z-projection
    - Merge the DAPI XYZ_CellOutlines (with cell outlines) and FISH Z-stacks
    - Save both the XY and XYZ merged overview images
- Make the results file with distances and intensity data for each spot: 
  - Spot, Cell, Cell Width, Cell Height, Distance, Spot Intensity (DAPI), Spot Intensity (FISH),Cell Max DAPI,
    Cell Min DAPI, Cell Mean, Cell Median. 
- Close all Images and ROI manager

