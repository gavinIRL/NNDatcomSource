/*
 * Copyright Gavin Feeney 2017
 * Licence: Apache Licence 2.0
 * This code is used to create data for a deep learning analysis on wing shapes
 */
package deeplearning;

/**
 *
 * @author Gavin Feeney [gavin.feeney01@gmail.com]
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import java.util.concurrent.ThreadLocalRandom;

public class DeepLearningRandom {

    /*
     * here goes the tweakable variables to describe the model
     * SSPNE always after SSPN
     * Variables and their values must correspond!!!!!!
     * Airfoils held separately
     */
    static int numDatcomSamples = 20; // number files to create
    static String[] varNames = {"CHRDR=", "CHRDTP=", "CHRDBP=", "SSPN=", "SSPNOP=",
        "SSPNE=", "ALT(1)=", "MACH(1)=", "XCG=", "ZCG=", "XW=", "ZW=", "ALIW=",
        "ZH=", "TWISTA=", "SAVSI=", "SAVSO=", "DHDADO=", "CHRDR=", "CHRDTP=", "SSPN=",
        "SSPNE=", "SAVSI=", "CHRDR=", "CHRDTP=", "SSPN=", "SSPNE=", "SAVSI=", "DHDADI=",
        "ALSCHD(1)="};
    static String[] varNLNames = {"WGPLNF", "WGPLNF", "WGPLNF", "WGPLNF", "WGPLNF",
        "WGPLNF", "FLTCON", "FLTCON", "SYNTHS", "SYNTHS", "SYNTHS", "SYNTHS", "SYNTHS",
        "SYNTHS", "WGPLNF", "WGPLNF", "WGPLNF", "WGPLNF", "VTPLNF", "VTPLNF", "VTPLNF",
        "VTPLNF", "VTPLNF", "HTPLNF", "HTPLNF", "HTPLNF", "HTPLNF", "HTPLNF", "HTPLNF",
        "FLTCON"};
    static double[] varMin = {15.0, 2.5, 7.5, 37.6, 15.8,
        31.4, 0.0, 0.25, 45.0, -3.0, 30.0, -4.0, 0.0,
        5.7, -3.0, 15.0, 15.0, 0.0, 15, 3, 15,
        13.1, 15, 8, 2, 14, 12.7, 15, 3,
        -3.9};
    static double[] varMax = {35, 12.5, 20, 78.4, 31.2,
        72.2, 10000.0, 0.6, 55.0, 3.0, 40.0, 1.0, 4.0,
        6.7, 0.0, 30.0, 30.0, 10.0, 23, 6.6, 27,
        25.1, 45, 16, 6, 28, 26.7, 45, 10,
        17.9};
    static int[] airfoilMin = {0, 0, 4, 4, 4}; // WCam,WPos,WThick,VThick,HThick
    static int[] airfoilMax = {6, 6, 16, 16, 16};
    // here goes the derivativs to extract from results
    static String[] derivatives = {"Alpha, CL", "Alpha, Cd", "Alpha, Cm"};
    /*
     * These will hold the list of variables and their value
     */
    static ArrayList varList = new ArrayList(); // will be string
    static ArrayList varNLList = new ArrayList(); // will be string (NL=Namelist)
    static ArrayList varMinList = new ArrayList(); // will be double
    static ArrayList varMaxList = new ArrayList(); // will be double
    // this is the console to display output in
    static OutputWindow w = new OutputWindow();
    // for recording time taken to run software
    static long startTime = 0;
    static long endTime = 0;
    // here is the array to record the input values
    static double inputs[][];
    // here goes the array to hold the output values
    static double outputs[][];
    // here goes the array to hold airfoil data
    static double airfoils[][];
    static ArrayList outputColumn1 = new ArrayList();

    // this method reads in the old csv output
    private static ArrayList readOldCSV(File results) throws FileNotFoundException, IOException {
        ArrayList returnLines = new ArrayList();
        FileReader fr = new FileReader(results);
        BufferedReader br = new BufferedReader(fr);

        String currentLine;
        while ((currentLine = br.readLine()) != null) {
            returnLines.add(currentLine);
            //System.out.println(currentLine);
        }
        //System.out.println("End of bufferedreader reading");

        fr.close();
        br.close();
        return returnLines;
    }

    // checks the tasklist to see how many instances of datcom are running
    static int checkForDatcomRunning() {
        try {
            int processCount = 0;
            String line;
            String pidInfo = "";
            Process p;
            p = Runtime.getRuntime().exec(System.getenv("windir") + "\\system32\\" + "tasklist.exe");
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = input.readLine()) != null) {
                pidInfo += line;
                //System.out.println(line);
                if (line.contains("Datcom.exe")) {
                    processCount++;
                }
            }
            input.close();
            if (processCount > 0) {
                return processCount;
            }
        } catch (IOException ex) {
            Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
        }
        return 0;
    }

    // here is where the values to change are set
    // and also the min and max values
    static void gatherVarNamesAndValues() {
        for (int i = 0; i < varNames.length; i++) {
            varList.add(varNames[i]);
            varNLList.add(varNLNames[i]);
            varMinList.add(varMin[i]);
            varMaxList.add(varMax[i]);
        }
        inputs = new double[varList.size()][numDatcomSamples];
        outputs = new double[derivatives.length][numDatcomSamples];
        airfoils = new double[5][numDatcomSamples]; // Always 5 wide
    }

    static double getMax(ArrayList list) {
        double max = Double.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            if (Double.parseDouble(list.get(i).toString()) > max) {
                max = Double.parseDouble(list.get(i).toString());
            }
        }
        return max;
    }

    static double getMin(ArrayList list) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            if (Double.parseDouble(list.get(i).toString()) < min) {
                min = Double.parseDouble(list.get(i).toString());
            }
        }
        return min;
    }

    // creates a folder given the number
    public static void createFolder(String path) {
        File dir = new File(path);
        dir.mkdir();
    }

    // deletes existing folders
    static void deleteFolders(String path, String folderName) {
        //System.out.println(path + "/" + folderName);
        File toDelete = new File(path + "/" + folderName);
        boolean success = FileUtils.deleteQuietly(toDelete);
        if (success == true) {
            //System.out.println("Folder Was Deleted=>" + folderName);
        } else {
            System.out.println("Warning! Folder Could Not Be Deleted=>" + folderName);
            w.setConsoleText("Warning! Folder Could Not Be Deleted=>" + folderName);
        }
    }

    static void deleteExistingFolders(String relativePath) {
        // delete all the existing folders
        int folderCount = 1;
        boolean folderCreatedCheck = false;
        int deletedCount = 0;
        while (folderCreatedCheck == false) {
            File dir2 = new File("" + folderCount);
            folderCreatedCheck = dir2.mkdir();
            if (folderCreatedCheck != true) {
                deleteFolders(relativePath, "" + folderCount);
                folderCount++;
                deletedCount++;
            } else {
                deleteFolders(relativePath, "" + folderCount);
                folderCreatedCheck = true;
            }
        }
        System.out.println("Deleted " + deletedCount + " existing folders");
        w.setConsoleText("Deleted " + deletedCount + " existing folders");
    }

    // creates dcm files in created folders
    public static void createDatcomFiles(String path, String folderName, ArrayList data) {
        try {
            // create the datcom file first in the correct folder
            File file = new File(path + "/" + folderName + "/" + folderName + ".dcm");
            file.createNewFile();
            FileWriter writer = new FileWriter(file);

            for (int i = 0; i < data.size(); i++) {
                writer.write((String) data.get(i) + "\n");
                writer.flush();
            }
            writer.close();

            // then create the .ini file in the same folder
            File file2 = new File(path + "/" + folderName + "/" + "Datcom.ini");
            file2.createNewFile();
            FileWriter writer2 = new FileWriter(file2);

            String xml1 = "[Outputs]";
            String xml2 = "AC = Off";
            String xml3 = "Display_AC = Off";
            String xml4 = "AC3D_fuselage_lines = Off";
            String xml5 = "Pause_At_End = Off";
            String xml6 = "Matlab_3D = Off";
            String xml7 = "JSBSim = Off";
            String xml8 = "Airfoil = Off";
            String xml9 = "Fuselage = Off";
            String xml10 = "CSV = On";
            String xml11 = "Old_CSV = On";
            String xml12 = "Log = Off";
            writer2.write(xml1 + "\n" + xml2 + "\n" + xml3 + "\n" + xml4 + "\n"
                    + xml5 + "\n" + xml6 + "\n" + xml7 + "\n" + xml8 + "\n"
                    + xml9 + "\n" + xml10 + "\n" + xml11 + "\n" + xml12);
            writer2.flush();
            writer2.close();

        } catch (IOException ex) {
            Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // returns random double given min and max
    static String getRandomValue(String minSt, String maxSt) {
        String returnValue = "";
        double min = Double.parseDouble(minSt);
        double max = Double.parseDouble(maxSt);
        Random r = new Random();
        double randomValue = min + (max - min) * r.nextDouble();
        // then round to a certain number of places
        returnValue = String.format("%.5g", randomValue); // setting to 5 can create errors????
        return returnValue;
    }

    //returns random int given min and max
    static int getRandomValue(int min, int max) {
        int returnValue = ThreadLocalRandom.current().nextInt(min, max + 1);
        return returnValue;
    }

    public static void main(String[] args) {
        // show output window
        w.setVisible(true);
        // run initial methods
        startTime = System.currentTimeMillis();
        gatherVarNamesAndValues();
        // Create the arraylist and add stock 737 Data
        ArrayList list = new ArrayList();
        list.add("CASEID Boeing 737-800");
        list.add("$FLTCON NMACH=1.0,MACH(1)=0.5,NALT=1.0,ALT(1)=0.0,NALPHA=3.0,");
        list.add("ALSCHD(1)=-4.0,4.0,18.0,");
        list.add("GAMMA=0.0,LOOP=2.0$");
        list.add("$SYNTHS XCG=50.6,ZCG=0.0,");
        list.add("XW=34.9,ZW=-1.4,ALIW=1.0,");
        list.add("XH=92.15,ZH=6.2,");
        list.add("XV=81.2,ZV=5.0$");
        list.add("$BODY NX=14.0,BNOSE=2.0,BTAIL=2.0,BLN=2.0,BLA=20.0,");
        list.add("X(1)=0.0,1.38,4.83,6.9,8.97,13.8,27.6,70.75,81.15,84.55,91.45,98.35,105.5,105.7,");
        list.add("ZU(1)=0.0,2.07,3.45,4.38,5.87,6.9,8.28,8.28,8.28,8.28,7.94,7.59,7.5,6.9,");
        list.add("ZL(1)=0.0,-1.73,-3.45,-3.8,-4.14,-4.49,-4.83,-4.83,-3.45,-2.76,-0.81,1.04,4.14,6.9,");
        list.add("R(1)=0.0,1.38,2.76,3.45,4.14,5.18,6.21,6.21,5.87,5.52,4.14,2.76,0.69,0.0,");
        list.add("S(1)=0.0,8.23,28.89,44.31,65.06,92.63,128.81,127.81,108.11,95.68,56.88,28.39,3.64,0.0,");
        list.add("P(1)=0.0,10.43,19.63,23.77,28.86,34.2,40.12,40.12,36.87,34.68,26.76,19.03,8.07,0.0$");
        list.add("$WGPLNF CHRDR=23.3,CHRDTP=5.31,CHRDBP=12.85,SSPN=47.4,SSPNOP=31.2,SSPNE=41.2,");
        list.add("CHSTAT=0.25,TWISTA=-1.0,TYPE=1.0,SAVSI=25.0,SAVSO=25.0,DHDADI=0.0,DHDADO=6.0$");
        list.add("NACA-W-1-4412");
        list.add("$VTPLNF CHRDR=19.0,CHRDTP=4.8,SSPN=21.4,SSPNE=19.5,TWISTA=0.0,");
        list.add("CHSTAT=0.25,TYPE=1.0,SAVSI=35.0$");
        list.add("NACA-V-1-0012");
        list.add("$HTPLNF CHRDR=11.9,CHRDTP=3.927,SSPN=20.8,SSPNE=19.5,TWISTA=0.0,");
        list.add("CHSTAT=0.25,TYPE=1.0,SAVSI=30.0,DHDADI=7.0$");
        list.add("NACA-H-1-0012");
        list.add("DAMP");
        list.add("NEXT CASE");

        // find relative path/working directory first
        Path currentRelativePath = Paths.get("");
        String relativePath = currentRelativePath.toAbsolutePath().toString();
        // and delete existing folders
        deleteExistingFolders(relativePath);

        for (int fileCounter = 1; fileCounter <= numDatcomSamples; fileCounter++) {
            // create arraylist to temporarily hold data to send to file
            ArrayList toFile = new ArrayList();
            for (int x = 0; x < list.size(); x++) {
                toFile.add(list.get(x));
            }

            double sspnHolder = 0.0; // this is just to catch sspne vs sspn error
            for (int varCounter = 0; varCounter < varList.size(); varCounter++) {
                String namelistFlag = varNLList.get(varCounter).toString();
                String variableFlag = varList.get(varCounter).toString();
                // identify namelist starting line
                int startNamelist = 0;
                for (int i = 0; i < list.size(); i++) {
                    String line = list.get(i).toString();
                    if (line.contains(namelistFlag) == true) {
                        startNamelist = i;
                        i += list.size(); //to end loop
                    }
                }
                int lineIndex = 0;
                int columnIndex = 0;

                for (int i = startNamelist; i < (list.size()); i++) {
                    String line = toFile.get(i).toString();
                    if (line.contains(variableFlag) == true) {
                        lineIndex = i;
                        columnIndex = line.indexOf(variableFlag);
                        columnIndex += variableFlag.length();
                        // columnList.add(columnIndex); legacy feature, not required
                        // then delete current value
                        if (varList.get(varCounter).toString() == "ALSCHD(1)=") {
                            columnIndex += 5;
                        }
                        int commaIndex = line.indexOf(",", columnIndex);

                        if (commaIndex == -1) { // ie a line with the $ and no comma
                            String beforeValue = line.substring(0, columnIndex);
                            String afterValue = "$";
                            String newLine = beforeValue + afterValue;
                            list.set(i, newLine);
                        } else {
                            String beforeValue = line.substring(0, columnIndex);
                            String afterValue = line.substring(commaIndex);
                            String newLine = beforeValue + afterValue;
                            list.set(i, newLine);
                        }
                        i += list.size(); //to end loop
                    }
                }
                String fullLine = list.get(lineIndex).toString();
                String firstHalf = fullLine.substring(0, columnIndex);
                String secondHalf = fullLine.substring(columnIndex);
                // then assemble the line
                String combined = firstHalf;
                // condition is just to catch sspne vs sspn
                String random = getRandomValue(varMinList.get(varCounter).toString(), varMaxList.get(varCounter).toString());

                // catch sspne vs sspn errors
                if (varList.get(varCounter).toString() == "SSPN=") {
                    sspnHolder = Double.parseDouble(random);
                    combined += sspnHolder;
                    inputs[varCounter][fileCounter - 1] = Double.parseDouble(random);
                } // catch the sspne
                else if (varList.get(varCounter).toString() == "SSPNE=") {
                    double diff = 0.0;
                    switch (varNLList.get(varCounter).toString()) {
                        case "WGPLNF":
                            diff = 6.2;
                            break;
                        case "VTPLNF":
                            diff = 1.9;
                            break;
                        case "HTPLNF":
                            diff = 1.3;
                            break;
                        default:
                            System.out.println("Error detected");
                            break;
                    }
                    // otherwise it'll throw errors
                    String preventError = String.format("%.5g", (sspnHolder - diff));
                    combined += preventError;
                    inputs[varCounter][fileCounter - 1] = Double.parseDouble(preventError);
                } // catch too large sspnop error
                else if (varList.get(varCounter).toString() == "SSPNOP=") {
                    double diff = 10.0;
                    double actual = Double.parseDouble(random);
                    if (((sspnHolder - actual) / 10) < 1.1) {
                        diff = 1.3 * ((sspnHolder - actual) / 10);
                        if (sspnHolder < 46) {
                            diff = sspnHolder / 3.0;
                        }
                        if (sspnHolder < 40) {
                            diff = sspnHolder / 2.0;
                        }
                        String preventError = String.format("%.5g", (sspnHolder - diff));
                        combined += preventError;
                        inputs[varCounter][fileCounter - 1] = Double.parseDouble(preventError);
                    } else {
                        combined += random;
                        inputs[varCounter][fileCounter - 1] = Double.parseDouble(random);
                    }

                } // all other cases
                else {
                    combined += random;
                    inputs[varCounter][fileCounter - 1] = Double.parseDouble(random);
                }
                combined += secondHalf;
                toFile.set(lineIndex, combined);
            }
            // now create random airfoils
            toFile = generateRandomAirfoils(toFile, fileCounter);
            // then create folder and .scm file
            createFolder("" + fileCounter);
            createDatcomFiles(relativePath, "" + fileCounter, toFile);

            if ((fileCounter) % (numDatcomSamples / 5) == 0) {
                int progress = fileCounter / (numDatcomSamples / 5);
                progress = progress * 20;
                if (progress != 100) {
                    System.out.println("File creation completion: " + progress + "%");
                    w.setConsoleText("File creation completion: " + progress + "%");
                } else {
                    System.out.println("File creation complete!");
                    w.setConsoleText("File creation complete!");
                    System.out.println("Number of files created: " + numDatcomSamples);
                    w.setConsoleText("Number of files created: " + numDatcomSamples);
                }

            }
        }
        // then run the files
        runDatcom(relativePath, numDatcomSamples);
    }

    static void runDatcom(String relativePath, int folderCount) {
        FileWriter fw3 = null;
        try {
            System.out.println("Starting to run .dcm files");
            w.setConsoleText("Starting to run .dcm files");
            for (int i = 1; i <= folderCount; i++) {
                // always have between 50 and 150 running at once
                if (i % 100 == 0) {
                    int datcomInstances = checkForDatcomRunning();
                    while (datcomInstances > 50) {
                        try {
                            Thread.sleep(500); // sleep for 0.5 seconds
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        datcomInstances = checkForDatcomRunning();
                    }
                    if (i % 300 == 0) { // can change this to give more/less frequent updates
                        double done = i;
                        done = ((done / folderCount) * 100);
                        String figure = String.format("%2.2f", (done));
                        System.out.println("Percentage of files run: " + figure + "%");
                        w.setConsoleText("Percentage of files run: " + figure + "%");
                    }

                }
                Runner runner1 = new Runner();
                runner1.relativePath = relativePath;
                runner1.folder = "" + i;
                runner1.start();
            }
            System.out.println("Finished queueing input files");
            w.setConsoleText("Finished queueing input files");
            System.out.println("Sleeping to allow Datcom to catch up");
            w.setConsoleText("Sleeping to allow Datcom to catch up");
            // then let datcom catch up
            try {
                Thread.sleep(5000);
                int waitCount = 5;
                int datcomFinished = checkForDatcomRunning();
                while (datcomFinished != 0) {
                    System.out.println("Still waiting..." + waitCount + "seconds..." + datcomFinished + " processes still running");
                    w.setConsoleText("Still waiting..." + waitCount + "seconds..." + datcomFinished + " processes still running");
                    Thread.sleep(5000);
                    waitCount += 5;
                    datcomFinished = checkForDatcomRunning();
                }
                System.out.println("Finished waiting, beginning results import");
                w.setConsoleText("Finished waiting, beginning results import");
            } catch (InterruptedException ex) {
                Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
            }
            // now time to import data into Arraylists to send to .csv file for DL
            Path currentRelativePath = Paths.get("");
            String path = currentRelativePath.toAbsolutePath().toString();
            for (int i = 1; i <= folderCount; i++) {
                try {
                    File file = new File(path + "/" + i + "/" + i + ".csv");
                    while (file.exists() != true) {
                        try {
                            System.out.println("Datcom appears to have not completed yet or else .csv is missing");
                            w.setConsoleText("Datcom appears to have not completed yet or else .csv is missing");
                            System.out.println("Trying again in 10 seconds");
                            w.setConsoleText("Trying again in 10 seconds");
                            System.out.println("Folder number: " + i);
                            w.setConsoleText("Folder number: " + i);
                            Thread.sleep(10000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    // read in the file
                    ArrayList rawOutput = readOldCSV(file);
                    // then remove the asterisks
                    for (int j = 0; j < rawOutput.size(); j++) {
                        if (rawOutput.get(j).toString().equalsIgnoreCase("*****************************")) {
                            rawOutput.remove(j);
                            j--;
                        }
                        // test print
                        //System.out.println("Line "+i+":"+rawOutput.get(i));
                    }
                    // the location of the chosen derivative
                    for (int j = 0; j < rawOutput.size(); j++) {
                        if (rawOutput.get(j).toString().equalsIgnoreCase("")) {
                            rawOutput.remove(j);
                            j--;
                        }
                    }
                    //System.out.println("folder:"+i);
                    // then add the derivatives to output array as necessary
                    for (int x = 0; x < derivatives.length; x++) {
                        String chosenDerivative = derivatives[x];
                        int derivLocation = 0; // the location of the chosen derivative
                        for (int j = 0; j < rawOutput.size(); j++) {
                            String line = rawOutput.get(j).toString();
                            if (line.equalsIgnoreCase(chosenDerivative)) {
                                derivLocation = j;
                            }
                        }
                        if (derivLocation == 0) {
                            System.out.println("There is an error with the chosen derivative");
                            w.setConsoleText("There is an error with the chosen derivative");
                        } else {
                            //taking the middle angle of attack of three
                            derivLocation += 2;
                            String line = rawOutput.get(derivLocation).toString();
                            // then remove the comma and angle itself to just leave the value
                            if (line.contains(",")) {
                                int start = line.indexOf(",");
                                String remaining = line.substring(start + 1); // danger of off-by-one error here
                                remaining = remaining.replaceAll("\\s+", ""); // remove whitespace

                                double outputValue = Double.parseDouble(remaining);
                                // then add data to correct arraylist i.e. output column
                                //outputColumn1.add(remaining);
                                outputs[x][i - 1] = outputValue;
                            }
                        }

                    }

                } catch (FileNotFoundException ex) {
                    Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            //System.out.println("Working to here");
            // this is used to check things are working as intended
            //for(int i = 0;i<numDatcomSamples;i++) {
            //    System.out.println(""+outputs[0][i]);
            //}
            // now delete the folders created
            System.out.println("Import successful, deleting folders");
            w.setConsoleText("Import successful, deleting folders");
            System.out.println("This may take a while, please be patient");
            w.setConsoleText("This may take a while, please be patient");
            for (int i = 1; i <= numDatcomSamples; i++) {
                deleteFolders(relativePath, "" + i);
            }
            System.out.println("" + numDatcomSamples + " folders deleted");
            w.setConsoleText("" + numDatcomSamples + " folders deleted");
            // and finally write all the correct data to a .csv file for DL analysis
            System.out.println("Attempting to create .csv file output for deep learning");
            w.setConsoleText("Attempting to create .csv file output for deep learning");
            System.out.println("Scaling data between 0 and 1");
            w.setConsoleText("Scaling data between 0 and 1");
            // send input data off to be scaled
            for (int k = 0; k < varNames.length; k++) {
                ArrayList dataScaled = new ArrayList();
                for (int l = 0; l < numDatcomSamples; l++) {
                    dataScaled.add(inputs[k][l]);
                }
                dataScaled = getScaledData(dataScaled, k, 4);
                //System.out.println("" + dataScaled.get(1).toString());
                // then send scaled data back to 2D array
                for (int l = 0; l < numDatcomSamples; l++) {
                    inputs[k][l] = Double.parseDouble(dataScaled.get(l).toString());
                }
            }
            // send airfoil data to be scaled
            for (int k = 0; k < airfoilMin.length; k++) {
                ArrayList dataScaled = new ArrayList();
                for (int l = 0; l < numDatcomSamples; l++) {
                    dataScaled.add(airfoils[k][l]);
                }
                dataScaled = getScaledData(dataScaled, k, 4, true);
                // then send scaled data back to 2D array
                for (int l = 0; l < numDatcomSamples; l++) {
                    airfoils[k][l] = Double.parseDouble(dataScaled.get(l).toString());
                }
            }
            // descaling outputs
            String delimiter = ",";
            String newLine = "\n"; // used for clarity
            fw3 = new FileWriter("descalingdata.csv");
            // send output data off to be scaled
            for (int k = 0; k < derivatives.length; k++) {
                ArrayList dataScaled = new ArrayList();
                for (int l = 0; l < numDatcomSamples; l++) {
                    dataScaled.add(outputs[k][l]);
                }
                double min = getMin(dataScaled);
                double max = getMax(dataScaled);
                dataScaled = getScaledData(dataScaled, k, 4, min, max);
                // need to save min and max for descaling
                fw3.append("" + derivatives[k] + ",min," + min + "" + newLine);
                fw3.append("" + derivatives[k] + ",max," + max + "" + newLine);
                // then send scaled output to 2D array
                for (int l = 0; l < numDatcomSamples; l++) {
                    outputs[k][l] = Double.parseDouble(dataScaled.get(l).toString());
                }
            }
            fw3.flush();
            fw3.close();
            // then send scaled data to .csv file
            // might be an error with one comma too many, although it never causes issues
            FileWriter fileWriter = new FileWriter("deeplearning.csv");
            for (int i = 0; i < numDatcomSamples; i++) {
                for (int j = 0; j < derivatives.length; j++) {
                    fileWriter.append("" + outputs[j][i]);
                    fileWriter.append(delimiter);
                }
                for (int j = 0; j < varNames.length; j++) {
                    fileWriter.append("" + inputs[j][i]);
                    fileWriter.append(delimiter);
                }
                for (int j = 0; j < airfoilMin.length; j++) {
                    fileWriter.append("" + airfoils[j][i]);
                    fileWriter.append(delimiter);
                }
                fileWriter.append(newLine);
            }
            fileWriter.flush();
            fileWriter.close();
            System.out.println("Attempting to isolate 10% for model validation/verification");
            w.setConsoleText("Attempting to isolate 10% for model validation/verification");
            FileReader fr = new FileReader("deeplearning.csv");
            BufferedReader br = new BufferedReader(fr);
            String currentLine;
            int lineCount = 1;
            ArrayList keepValues = new ArrayList();
            ArrayList validationValues = new ArrayList();
            while ((currentLine = br.readLine()) != null) {
                if (lineCount % 10 == 0) {
                    // keep for validation
                    validationValues.add(currentLine);
                } else {
                    keepValues.add(currentLine);
                }
                lineCount++;
            }
            //System.out.println("End of bufferedreader reading");
            fr.close();
            br.close();
            // then delete old .csv file to create two new files
            System.out.println("Data isolated, deleting old file");
            w.setConsoleText("Data isolated, deleting old file");
            File toDelete = new File("deeplearning.csv");
            boolean success = FileUtils.deleteQuietly(toDelete);
            if (success == true) {
                //System.out.println("Folder Was Deleted=>" + folderName);
            } else {
                System.out.println("Warning! File Could Not Be Deleted");
                w.setConsoleText("Warning! File Could Not Be Deleted");
                System.out.println("Please Close All CSV Files If Opened");
                w.setConsoleText("Please Close All CSV Files If Opened");
            }
            System.out.println("Attempting to create separate training and validation files");
            w.setConsoleText("Attempting to create separate training and validation files");
            FileWriter fw1 = new FileWriter("trainingdata.csv");
            for (int i = 0; i < keepValues.size(); i++) {
                fw1.append(keepValues.get(i).toString());
                fw1.append(newLine);
            }
            fw1.flush();
            fw1.close();
            FileWriter fw2 = new FileWriter("validationdata.csv");
            for (int i = 0; i < validationValues.size(); i++) {
                fw2.append(validationValues.get(i).toString());
                fw2.append(newLine);
            }
            fw2.flush();
            fw2.close();
            System.out.println("Success! Both csv files have been created");
            w.setConsoleText("Success! Both csv files have been created");
            System.out.println("Program has now terminated");
            w.setConsoleText("Program has now terminated");

            endTime = System.currentTimeMillis();
            long duration = (endTime - startTime); // divide by 1000 to get seconds
            duration = duration / 1000;
            System.out.println("Program took " + duration + " seconds to run, i.e. roughly " + (duration / 60) + " minutes");
            w.setConsoleText("Program took " + duration + " seconds to run, i.e. roughly " + (duration / 60) + " minutes");
        } catch (IOException ex) {
            Logger.getLogger(DeepLearningRandom.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Error detected");
        }
    }

    // method to scale input data between 0 and 1 for deep learning
    static ArrayList getScaledData(ArrayList list, int index, int places) {
        DecimalFormat df = new DecimalFormat("#.###");
        if (places == 4) {
            df = new DecimalFormat("#.####");
        } else if (places == 5) {
            df = new DecimalFormat("#.#####");
        }
        ArrayList returnData = new ArrayList();
        double min = varMin[index];
        double max = varMax[index];
        double currentValue;
        double range = max - min;
        for (int i = 0; i < list.size(); i++) {
            currentValue = Double.parseDouble(list.get(i).toString());
            currentValue -= min;
            currentValue = currentValue / range;

            df.setRoundingMode(RoundingMode.HALF_UP);
            String value = df.format(currentValue);
            returnData.add(value);
            if (i == 200) {
                //System.out.println(value);
            }
        }
        return returnData;
    }
    // method to scale airfoil data between 0 and 1 for deep learning (there is duplication but not enough time to optimise)

    static ArrayList getScaledData(ArrayList list, int index, int places, boolean airfoil) {
        DecimalFormat df = new DecimalFormat("#.###");
        if (places == 4) {
            df = new DecimalFormat("#.####");
        } else if (places == 5) {
            df = new DecimalFormat("#.#####");
        }
        ArrayList returnData = new ArrayList();
        double min = airfoilMin[index];
        double max = airfoilMax[index];
        double currentValue;
        double range = max - min;
        for (int i = 0; i < list.size(); i++) {
            currentValue = Double.parseDouble(list.get(i).toString());
            currentValue -= min;
            currentValue = currentValue / range;

            df.setRoundingMode(RoundingMode.HALF_UP);
            String value = df.format(currentValue);
            returnData.add(value);
            if (i == 200) {
                //System.out.println(value);
            }
        }
        return returnData;
    }
    // method to scale output data between 0 and 1 for deep learning

    static ArrayList getScaledData(ArrayList list, int index, int places, double min, double max) {
        ArrayList returnData = new ArrayList();
        DecimalFormat df = new DecimalFormat("#.##");
        if (places == 3) {
            df = new DecimalFormat("#.###");
        } else if (places == 4) {
            df = new DecimalFormat("#.####");
        }
        double currentValue;
        double range = max - min;
        for (int i = 0; i < list.size(); i++) {
            currentValue = Double.parseDouble(list.get(i).toString());
            currentValue -= min;
            currentValue = currentValue / range;

            df.setRoundingMode(RoundingMode.HALF_UP);
            String value = df.format(currentValue);
            returnData.add(value);
            if (i == 200) {
                //System.out.println(value);
            }
        }
        return returnData;
    }

    // method to randomise airfoils for the .dcm file
    static ArrayList generateRandomAirfoils(ArrayList list, int fileCounter) {
        int wCam = getRandomValue(airfoilMin[0], airfoilMax[0]);
        int wPos = getRandomValue(airfoilMin[1], airfoilMax[1]);
        int wThick = getRandomValue(airfoilMin[2], airfoilMax[2]);
        int vThick = getRandomValue(airfoilMin[3], airfoilMax[3]);
        int hThick = getRandomValue(airfoilMin[4], airfoilMax[4]);
        // then eliminate "too short" errors
        String thicknessStringW = "" + wThick;
        if (wThick < 10) {
            thicknessStringW = "0" + wThick;
        }
        String thicknessStringV = "" + vThick;
        if (vThick < 10) {
            thicknessStringV = "0" + vThick;
        }
        String thicknessStringH = "" + hThick;
        if (hThick < 10) {
            thicknessStringH = "0" + hThick;
        }
        // then set the lines with random airfoils
        list.set(17, "NACA-W-1-" + wCam + "" + wPos + "" + thicknessStringW);
        list.set(20, "NACA-V-1-00" + thicknessStringV);
        list.set(23, "NACA-H-1-00" + thicknessStringH);
        // then record the data and return the arraylist
        airfoils[0][fileCounter-1] = wCam;
        airfoils[1][fileCounter-1] = wPos;
        airfoils[2][fileCounter-1] = wThick;
        airfoils[3][fileCounter-1] = vThick;
        airfoils[4][fileCounter-1] = hThick;
        return list;
    }
}
