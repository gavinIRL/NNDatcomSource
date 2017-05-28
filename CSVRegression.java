package org.deeplearning4j.examples.dataexamples;

import java.io.*;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Created by Gavin on 28/05/2017.
 */
public class CSVRegression {

    private static Logger log = LoggerFactory.getLogger(CSVExample.class);

    public static void main(String[] args) throws  Exception{
        //First: get the dataset using the record reader. CSVRecordReader handles loading/parsing
        int numLinesToSkip = 0;
        String delimiter = ",";


        int seed = 123;
        double learningRate = 0.01;

        int numInputs = 6;
        int batchSize = 50;
        int numOutputs = 1;
        int nEpochs = 75;
        int numHiddenNodes = 20;

        // training
        RecordReader recordReader = new CSVRecordReader(numLinesToSkip,delimiter);
        recordReader.initialize(new FileSplit(new File("trainingdata.csv")));
        DataSetIterator trainIter = new RecordReaderDataSetIterator(recordReader,batchSize,0,0,true);

        //evaluation
        RecordReader recordReaderTest = new CSVRecordReader(numLinesToSkip,delimiter);
        recordReaderTest.initialize(new FileSplit(new File("validationdata.csv")));
        DataSetIterator testIter = new RecordReaderDataSetIterator(recordReaderTest,batchSize,0,0,true);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(seed)
            .iterations(1)
            .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
            .learningRate(learningRate)
            .updater(Updater.NESTEROVS).momentum(0.9)
            .list()
            .layer(0, new DenseLayer.Builder().nIn(numInputs).nOut(numHiddenNodes)
                .activation(Activation.TANH).build())
            .layer(1, new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                .activation(Activation.TANH).build())
            .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                .activation(Activation.IDENTITY)
                .nIn(numHiddenNodes).nOut(numOutputs).build())
            .pretrain(false).backprop(true).build();

        //System.out.println(conf.toJson());
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        model.setListeners(new ScoreIterationListener(10));

        for(int n=0;n<nEpochs;n++) {
            model.fit(trainIter);
        }
        System.out.println("Evaluate model...");
        Evaluation eval = new Evaluation(numOutputs);
        while(testIter.hasNext()) {
            DataSet t = testIter.next();
            INDArray features = t.getFeatureMatrix();
            INDArray labels = t.getLabels();
            INDArray predicted = model.output(features,false);
            eval.eval(labels,predicted);
        }
        System.out.println(eval.stats());
    }
}
