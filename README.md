This is an example Maven project implementing an ImageJ2 command.

- Open a file (3+ channels) and split channels, channel 3 should be DAPI and Channel 2 the FISH signal    
- Create a new folder to save results, folder will be in the same file as the input image and have the same name. 
- Get the xyz scale of the image from the metadata
- Find XYZ positions of green maxima
    -    xy positions are found from a maximum z-projection of the FISH channel.
    - a Find Maxima... with a user input tolerance (default 2000) is used to find the x-y positions
    - z positions are found by determining the slice in the FISH channel with the brightest pixel at the xy position
    - The intensities of the pixels at the xyz position in the FISH and DAPI channels are also recorded.
    
         
- Find the XYZ Cell outlines and the intensity stats per cell in the DAPI channel
  - Uses the Max Z-projection of the DAPI channel and cellpose cyto2 model (Pachitariu, M. & Stringer, C. (2022). Nature methods)
    via the BIOP-wrapper (https://github.com/BIOP/ijl-utilities-wrappers) to segment the cell outlines in XY.
  - 
- Find which cell each spot belongs to
- Find the distances of the spot to the nearest cell edge and the intensity at the spot in each channel
- Make the Z-slice output image stack
- Merge the DAPI (with distances drawn on) and FISH (green) channel Z-projections
- Merge the DAPI (with cell outlines) and FISH Z-stacks
- Save both the XY and XYZ merged overview images
- Make the results file with distances and intensity data for each cell
- Close all Images and ROI manager

