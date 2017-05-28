/*
 * Copyright Gavin Feeney 2017
 * Licence: Apache Licence 2.0
 * This code is used to create data for a deep learning analysis on wing shapes
 */
package deeplearning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

public class DeepLearning {

    // here go the variables that can be changed for the model
    // variables defining number of prime numbers to go to
    static int numPrimes = 2;
    static String chosenDerivative = "Alpha, CL"; // needs to be in form of .csv file line
    // runs each datcom file in a separate thread
    // may need to handle threads for more than 1000 threads

    static void runDatcom(String relativePath, int folderCount) {
        System.out.println("Starting to run .dcm files");
        w.setConsoleText("Starting to run .dcm files");
        try {
            ExecutorService pool = Executors.newFixedThreadPool(100); // limit to 100 threads
            
            for (int i = 1; i <= folderCount; i++) {

                // need to limit amount of open Datcom.exe instances to 100
                int datcomInstances = checkForDatcomRunning();
                if (datcomInstances >= 100) {
                    try {
                        Thread.sleep(5000); // sleep for 5 seconds
                    } catch (InterruptedException ex) {
                        Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                Runner runner1 = new Runner();
                runner1.relativePath = relativePath;
                runner1.folder = "" + i;

                pool.submit(runner1);

                //runner1.start();
                
                // added this to slow down the loop slightly 
                // give progress updates
                if(folderCount>=1000 && i%500 == 0) {
                    double done = i;
                    done = ((done/folderCount)*100);
                    String figure = String.format("%2.3f", (done));
                    System.out.println("Percentage of files run: "+figure+"%");
                    w.setConsoleText("Percentage of files run: "+figure+"%");
                }
                if(folderCount<1000 && i%100 == 0) {
                    double done = i;
                    done = ((done/folderCount)*100);
                    String figure = String.format("%2.2f", (done));
                    System.out.println("Percentage of files run: "+figure+"%");
                    w.setConsoleText("Percentage of files run: "+figure+"%");
                }
            }
            System.out.println("Finished queueing input files");
            w.setConsoleText("Finished queueing input files");
            System.out.println("Sleeping to allow Datcom to catch up");
            w.setConsoleText("Sleeping to allow Datcom to catch up");
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
            for (int i = 1; i < folderCount; i++) {
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
                    // then remove the empty lines
                    for (int j = 0; j < rawOutput.size(); j++) {
                        if (rawOutput.get(j).toString().equalsIgnoreCase("")) {
                            rawOutput.remove(j);
                            j--;
                        }
                    }
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
                            // then add data to correct arraylist i.e. output column
                            outputColumn.add(remaining);
                        }
                    }
                    pool.shutdown(); // no longer need pool of threads
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            // now delete the folders created
            System.out.println("Import successful, deleting folders");
            w.setConsoleText("Import successful, deleting folders");
            System.out.println("This may take a while");
            w.setConsoleText("This may take a while");
            for (int i = 1; i <= inputColumn1.size(); i++) {
                deleteFolders(relativePath, "" + i);
            }
            System.out.println("" + inputColumn1.size() + " folders deleted");
            w.setConsoleText("" + inputColumn1.size() + " folders deleted");
            // and finally write all the correct data to a .csv file for DL analysis
            System.out.println("Attempting to create .csv file output for deep learning");
            w.setConsoleText("Attempting to create .csv file output for deep learning");
            System.out.println("Scaling data between 0 and 1");
            w.setConsoleText("Scaling data between 0 and 1");
            // first need to catch data for descale
            // then create file showing min/max for descaling
            String delimiter = ",";
            String newLine = "\n"; // used for clarity
            FileWriter fw3 = new FileWriter("descalingdata.csv");
            fw3.append("CHRDR MAX, " + getMax(inputColumn1) + newLine);
            fw3.append("CHRDR MIN, " + getMin(inputColumn1) + newLine);
            fw3.append("CHRDTP MAX, " + getMax(inputColumn2) + newLine);
            fw3.append("CHRDTP MIN, " + getMin(inputColumn2) + newLine);
            fw3.append("CHRDBP MAX, " + getMax(inputColumn3) + newLine);
            fw3.append("CHRDBP MIN, " + getMin(inputColumn3) + newLine);
            fw3.append("SSPN MAX, " + getMax(inputColumn4) + newLine);
            fw3.append("SSPN MIN, " + getMin(inputColumn4) + newLine);
            fw3.append("SSPNOP MAX, " + getMax(inputColumn5) + newLine);
            fw3.append("SSPNOP MIN, " + getMin(inputColumn5) + newLine);
            fw3.append("SSPNE MAX, " + getMax(inputColumn6) + newLine);
            fw3.append("SSPNE MIN, " + getMin(inputColumn6) + newLine);
            fw3.flush();
            fw3.close();
            System.out.println("A file has been created to show min/max values for descaling");
            w.setConsoleText("A file has been created to show min/max values for descaling");
            // send all arraylists off to be scaled
            outputColumn = getScaledData(outputColumn);
            inputColumn1 = getScaledData(inputColumn1);
            inputColumn2 = getScaledData(inputColumn2);
            inputColumn3 = getScaledData(inputColumn3);
            inputColumn4 = getScaledData(inputColumn4);
            inputColumn5 = getScaledData(inputColumn5);
            inputColumn6 = getScaledData(inputColumn6);
            for(int x=1;x<outputColumn.size();x=x+30) {
                System.out.println(outputColumn.get(x));
            }
            
            FileWriter fileWriter = new FileWriter("deeplearning.csv");
            for (int i = 0; i < outputColumn.size(); i++) {
                fileWriter.append((outputColumn.get(i).toString()));
                fileWriter.append(delimiter);
                fileWriter.append((inputColumn1.get(i).toString()));
                fileWriter.append(delimiter);
                fileWriter.append((inputColumn2.get(i).toString()));
                fileWriter.append(delimiter);
                fileWriter.append((inputColumn3.get(i).toString()));
                fileWriter.append(delimiter);
                fileWriter.append((inputColumn4.get(i).toString()));
                fileWriter.append(delimiter);
                fileWriter.append((inputColumn5.get(i).toString()));
                fileWriter.append(delimiter);
                fileWriter.append((inputColumn6.get(i).toString()));
                fileWriter.append(newLine);
            }
            fileWriter.flush();
            fileWriter.close();
            //System.out.println("Main .csv file created");
            //w.setConsoleText("Main .csv file created");
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
            
            endTime = System.nanoTime();
            long duration = (endTime - startTime); // divide by 1000000000 to get seconds
            duration = duration / 1000000000;
            System.out.println("Program took " + duration + " seconds to run, i.e. roughly " + (duration / 60) + " minutes");
            w.setConsoleText("Program took " + duration + " seconds to run, i.e. roughly " + (duration / 60) + " minutes");
        } catch (IOException ex) {
            Logger.getLogger(DeepLearning.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Error detected");
        } 
    }

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

    static void gatherVarNamesAndValues() {
        // these need to be in order of appearance in file
        // if not in order, errors will occur, check with "list" array main method
        varList.add("CHRDR=");
        varList.add("CHRDTP=");
        varList.add("CHRDBP=");
        varList.add("SSPN=");
        varList.add("SSPNOP=");
        varList.add("SSPNE=");
        varMinList.add(15); // CHRDR
        varMinList.add(2.5); // CHRDTP
        varMinList.add(7.5); // CHRDBP
        varMinList.add(37.6); // SSPN
        varMinList.add(15.8); // SSPNOP
        varMinList.add(31.4); // SSPNE - not used as will be set to (SSPN - 6.2)
        varMaxList.add(35); // CHRDR
        varMaxList.add(12.5); // CHRDTP
        varMaxList.add(20); // CHRDBP
        varMaxList.add(78.4); // SSPN
        varMaxList.add(31.2); // SSPNOP
        varMaxList.add(72.2); // SSPNE - not used as will be set to (SSPN - 6.2)
    }
    // define the list of prime numbers in an array
    static ArrayList listPrimes = new ArrayList();
    // this is the list of variables and their values
    static ArrayList varList = new ArrayList(); // will be string
    static ArrayList varMinList = new ArrayList(); // will be double
    static ArrayList varMaxList = new ArrayList(); // will be double
    // define the arraylists for holding data to be sent to .csv for DL analysis
    static ArrayList outputColumn = new ArrayList();
    static ArrayList inputColumn1 = new ArrayList();
    static ArrayList inputColumn2 = new ArrayList();
    static ArrayList inputColumn3 = new ArrayList();
    static ArrayList inputColumn4 = new ArrayList();
    static ArrayList inputColumn5 = new ArrayList();
    static ArrayList inputColumn6 = new ArrayList();
    // this is the console to display output in
    static OutputWindow w = new OutputWindow();
    // for recording time taken to run software
    static long startTime = 0;
    static long endTime = 0;

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

    // used to scale the arraylist data to [0,1] for deep learning
    static ArrayList getScaledData(ArrayList list) {
        ArrayList returnData = new ArrayList();
        double min = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            if (Double.parseDouble(list.get(i).toString()) < min) {
                min = Double.parseDouble(list.get(i).toString());
            }
        }
        double max = Double.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            if (Double.parseDouble(list.get(i).toString()) > max) {
                max = Double.parseDouble(list.get(i).toString());
            }
        }
        double currentValue;
        double range = max - min;
        for (int i = 0; i < list.size(); i++) {
            currentValue = Double.parseDouble(list.get(i).toString());
            currentValue -= min;
            currentValue = currentValue / range;
            String value = String.format("%.3f", currentValue); // 3 decimals
            returnData.add(value);
            if(i==200) {
                //System.out.println(value);
            }
        }
        //System.out.println(min);
        //System.out.println(max);
        return returnData;
    }

    // adds the required amount of prime numbers to the arraylist
    static void gatherPrimes() {
        int count = 0;
        for (int i = 2; count < numPrimes; i++) {
            if (isPrime(i) == true) {
                listPrimes.add(i);
                count++;
                //System.out.println(""+i);
            }
        }
    }

    //checks whether an int is prime or not.
    static boolean isPrime(int n) {
        if (n == 2) {
            return true;
        }
        //check if n is a multiple of 2
        if (n % 2 == 0) {
            return false;
        } else //if not, then just check the odds
        {
            for (int i = 3; i * i <= n; i += 2) {
                if (n % i == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // creates a folder given the number
    public static void createFolder(String path) {
        File dir = new File(path);
        dir.mkdir();
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

    // returns value for reporting 10 percent of files complete
    static int getTwentyPercent() {
        double returnValue = 0;
        int sumPrimesMinusOne = 0;
        for (int i = 0; i < listPrimes.size(); i++) {
            sumPrimesMinusOne += (Integer.parseInt((listPrimes.get(i).toString())) - 1);
        }
        //System.out.println("primes:" + sumPrimesMinusOne);
        returnValue = sumPrimesMinusOne * 2;
        returnValue -= 2.0;
        returnValue = Math.pow(returnValue, (-2.0 + varList.size()));
        returnValue = returnValue * sumPrimesMinusOne;
        //System.out.println("100percent"+returnValue);
        returnValue = returnValue / 5;
        Double d = new Double(returnValue);
        int value = d.intValue();
        return value;
    }

    public static void main(String[] args) {
        w.setVisible(true);
        // run initial methods
        startTime = System.nanoTime();
        gatherPrimes();
        gatherVarNamesAndValues();
        // Create the arraylist and add stock 737 Data
        ArrayList list = new ArrayList();
        list.add("CASEID Boeing 737-800");
        list.add("$FLTCON NMACH=1.0,MACH(1)=0.5,WT=83000.0,NALT=1.0,ALT(1)=0.0,NALPHA=3.0,");
        list.add("ALSCHD(1)=0.0,4.0,8.0,");
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

        // delete existing folders
        deleteExistingFolders(relativePath);

        // variable for the codewords required
        // One namelist codeword for now
        String namelistFlag = "WGPLNF";

        // identify namelist starting line
        int startNamelist = 0;
        for (int i = 0; i < list.size(); i++) {
            String line = list.get(i).toString();
            if (line.contains(namelistFlag) == true) {
                startNamelist = i;
                i += list.size(); //to end loop
            }
        }
        // find row and column index, and delete current values for variables
        // and also remember the positions by adding to array
        String variableFlag = null;
        int lineIndex = 0;
        int columnIndex = 0;
        // this holds column position of the variable positions
        ArrayList columnList = new ArrayList();
        for (int j = 0; j < varList.size(); j++) {
            variableFlag = varList.get(j).toString();
            for (int i = startNamelist; i < (list.size()); i++) {
                String line = list.get(i).toString();
                if (line.contains(variableFlag) == true) {
                    lineIndex = i;
                    columnIndex = line.indexOf(variableFlag);
                    columnIndex += variableFlag.length();
                    columnList.add(columnIndex);
                    // then delete current value
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
        }
        // this is ok if all variables are on same line but needs to change if not
        // this will be done by a second array similar to columnList
        // for now just reassigning value to another variable as a reminder
        int lineIndexAll = lineIndex;

        //System.out.println(list.get(lineIndexAll) + "Test Line");
        // variable to hold number of folders created so far
        int totalFolderNumber = 0;
        // arraylist to temp hold data to send to file
        ArrayList toFile = new ArrayList();
        ArrayList toFile2 = new ArrayList();
        ArrayList toFile3 = new ArrayList();
        ArrayList toFile4 = new ArrayList();
        ArrayList toFile5 = new ArrayList();
        // main creation loop
        for (int i = 0; i < listPrimes.size(); i++) {
            // e.g. if given a value of 3, there should be 
            // two increments at 1/3 and 2/3 respectively
            int denominator = Integer.parseInt(listPrimes.get(i).toString());
            int increments = denominator - 1;
            // populate the folders with files
            // split the relevant line in two

            // first up is sspne
            for (int j = 0; j < increments; j++) {
                String fullLine = list.get(lineIndexAll).toString();
                String firstHalf = fullLine.substring(0, Integer.parseInt(columnList.get(5).toString()));
                String secondHalf = fullLine.substring(Integer.parseInt(columnList.get(5).toString()));

                toFile.clear();
                for (int x = 0; x < list.size(); x++) {
                    toFile.add(list.get(x));
                }
                String combined = firstHalf;
                double range = Double.parseDouble(varMaxList.get(5).toString()) - Double.parseDouble(varMinList.get(5).toString());
                double deltaValuePerInc = (range / denominator);
                double valueThisIteration = Double.parseDouble(varMinList.get(5).toString()) + ((j + 1) * deltaValuePerInc);
                String value = String.format("%.5g", valueThisIteration); // always 3 decimal places
                combined += value;
                combined += secondHalf;
                String valueSSPNE = value;

                double valueSSPNThisIteration = valueThisIteration + 6.2;
                String valueSSPN = String.format("%.5g", valueSSPNThisIteration);

                //System.out.println(secondHalf);
                //System.out.println(combined);
                //System.out.println("I value: " + i + " and J value: " + j);
                //System.out.println("den: "+denominator);
                toFile.set(lineIndexAll, combined);
                //System.out.println(toFile.get(lineIndexAll));
                for (int l = 0; l < listPrimes.size(); l++) {
                    denominator = Integer.parseInt(listPrimes.get(l).toString());
                    increments = denominator - 1;
                    // sspnop
                    for (int k = 0; k < increments; k++) {
                        toFile2.clear();
                        String fullLine2 = toFile.get(lineIndexAll).toString();
                        String firstHalf2 = fullLine2.substring(0, Integer.parseInt(columnList.get(4).toString()));
                        String secondHalf2 = fullLine2.substring(Integer.parseInt(columnList.get(4).toString()));
                        for (int x = 0; x < toFile.size(); x++) {
                            toFile2.add(toFile.get(x));
                        }

                        range = Double.parseDouble(varMaxList.get(4).toString()) - Double.parseDouble(varMinList.get(4).toString());
                        deltaValuePerInc = (range / denominator);
                        valueThisIteration = Double.parseDouble(varMinList.get(4).toString()) + ((k + 1) * deltaValuePerInc);
                        value = String.format("%.5g", valueThisIteration);
                        String valueSSPNOP = value;

                        combined = firstHalf2;
                        combined += value;
                        combined += secondHalf2;
                        toFile2.set(lineIndexAll, combined);
                        //System.out.println(toFile2.get(lineIndexAll));
                        //totalFolderNumber++;
                        //System.out.println("i: " + i + ",j: " + j+",K: "+k+", l: "+l);
                        for (int m = 0; m < listPrimes.size(); m++) {
                            denominator = Integer.parseInt(listPrimes.get(m).toString());
                            increments = denominator - 1;
                            // CHRDBP
                            for (int n = 0; n < increments; n++) {
                                toFile3.clear();
                                String fullLine3 = toFile2.get(lineIndexAll).toString();
                                String firstHalf3 = fullLine3.substring(0, Integer.parseInt(columnList.get(2).toString()));
                                String secondHalf3 = fullLine3.substring(Integer.parseInt(columnList.get(2).toString()));
                                for (int x = 0; x < toFile.size(); x++) {
                                    toFile3.add(toFile.get(x));
                                }

                                range = Double.parseDouble(varMaxList.get(2).toString()) - Double.parseDouble(varMinList.get(2).toString());
                                deltaValuePerInc = (range / denominator);
                                valueThisIteration = Double.parseDouble(varMinList.get(2).toString()) + ((n + 1) * deltaValuePerInc);
                                value = String.format("%.5g", valueThisIteration);

                                combined = firstHalf3;
                                combined += value;
                                combined += secondHalf3;
                                String valueCHRDBP = value;
                                toFile3.set(lineIndexAll, combined);

                                for (int p = 0; p < listPrimes.size(); p++) {
                                    denominator = Integer.parseInt(listPrimes.get(p).toString());
                                    increments = denominator - 1;
                                    // CHRDTP
                                    for (int q = 0; q < increments; q++) {
                                        toFile4.clear();
                                        String fullLine4 = toFile3.get(lineIndexAll).toString();
                                        String firstHalf4 = fullLine4.substring(0, Integer.parseInt(columnList.get(1).toString()));
                                        String secondHalf4 = fullLine4.substring(Integer.parseInt(columnList.get(1).toString()));
                                        for (int x = 0; x < toFile.size(); x++) {
                                            toFile4.add(toFile.get(x));
                                        }

                                        range = Double.parseDouble(varMaxList.get(1).toString()) - Double.parseDouble(varMinList.get(1).toString());
                                        deltaValuePerInc = (range / denominator);
                                        valueThisIteration = Double.parseDouble(varMinList.get(1).toString()) + ((q + 1) * deltaValuePerInc);
                                        value = String.format("%.5g", valueThisIteration);
                                        String valueCHRDTP = value;

                                        combined = firstHalf4;
                                        combined += value;
                                        combined += secondHalf4;
                                        toFile4.set(lineIndexAll, combined);
                                        for (int r = 0; r < listPrimes.size(); r++) {
                                            denominator = Integer.parseInt(listPrimes.get(r).toString());
                                            increments = denominator - 1;
                                            // CHRDR
                                            for (int s = 0; s < increments; s++) {
                                                toFile5.clear();
                                                String fullLine5 = toFile4.get(lineIndexAll).toString();
                                                String firstHalf5 = fullLine5.substring(0, Integer.parseInt(columnList.get(0).toString()));
                                                String secondHalf5 = fullLine5.substring(Integer.parseInt(columnList.get(0).toString()));
                                                for (int x = 0; x < toFile.size(); x++) {
                                                    toFile5.add(toFile.get(x));
                                                }

                                                range = Double.parseDouble(varMaxList.get(0).toString()) - Double.parseDouble(varMinList.get(0).toString());
                                                deltaValuePerInc = (range / denominator);
                                                valueThisIteration = Double.parseDouble(varMinList.get(0).toString()) + ((s + 1) * deltaValuePerInc);
                                                value = String.format("%.5g", valueThisIteration);

                                                combined = firstHalf5;
                                                combined += value;
                                                combined += secondHalf5;
                                                String valueCHRDR = value;

                                                // need to add sspn value now which is fixed relative to sspne
                                                String firstHalfSSPN = combined.substring(0, Integer.parseInt(columnList.get(3).toString()) + 18);
                                                String secondHalfSSPN = combined.substring(Integer.parseInt(columnList.get(3).toString()) + 18);
                                                combined = firstHalfSSPN;
                                                combined += valueSSPN;
                                                combined += secondHalfSSPN;

                                                // then create the folders and populate with files
                                                toFile5.set(lineIndexAll, combined);
                                                totalFolderNumber++;
                                                createFolder("" + totalFolderNumber);
                                                createDatcomFiles(relativePath, "" + totalFolderNumber, toFile5);

                                                // add the appropriate data to the correct arraylists for transfer to .csv file
                                                inputColumn1.add(valueCHRDR);
                                                inputColumn2.add(valueCHRDTP);
                                                inputColumn3.add(valueCHRDBP);
                                                inputColumn4.add(valueSSPN);
                                                inputColumn5.add(valueSSPNOP);
                                                inputColumn6.add(valueSSPNE);

                                                // given update on progress
                                                if (totalFolderNumber % getTwentyPercent() == 0) {
                                                    System.out.println("File creation completion: " + ((100 * totalFolderNumber) / (getTwentyPercent() * 5)) + "%");
                                                    w.setConsoleText("File creation completion: " + ((100 * totalFolderNumber) / (getTwentyPercent() * 5)) + "%");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Number of folders created: " + totalFolderNumber);
        w.setConsoleText("Number of folders created: " + totalFolderNumber);
        // this will be 768, 145k, 4.31mil for 2,3,4 prime numbers respectively

        // and finally, set up the thread pool and run the files
        runDatcom(relativePath, totalFolderNumber);

        // for testing
        //runDatcom(relativePath, 50);








    }
}

class Runner extends Thread {

    String relativePath = "";
    String folder = "";

    @Override
    public void run() {
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c",
                "start", folder + ".dcm", "");
        builder.directory(new File(relativePath + "/" + folder));
        //System.out.println("New Path is: " + builder.directory());
        try {
            Process p = builder.start();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
