package g419.liner2.core.chunker.factory;


import g419.corpus.ConsolePrinter;
import g419.corpus.io.reader.AbstractDocumentReader;
import g419.corpus.io.reader.ReaderFactory;
import g419.corpus.structure.Document;
import g419.lib.cli.ParameterException;
import g419.liner2.core.LinerOptions;
import g419.liner2.core.chunker.AnnotationWekaClassifierChunker;
import g419.liner2.core.chunker.Chunker;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ChunkerFactoryItemAnnotationWekaClassifier extends ChunkerFactoryItem {

  public ChunkerFactoryItemAnnotationWekaClassifier() {
    super("classifier");
  }

  @Override
  public Chunker getChunker(final Ini.Section description, final ChunkerManager cm) throws Exception {
    ConsolePrinter.log("Training annotation classifier");

    final String inputClassifier = description.get("base-chunker");
    Chunker baseChunker = null;

    if (inputClassifier != null) {
      baseChunker = cm.getChunkerByName(inputClassifier);
      if (baseChunker == null) {
        throw new ParameterException("Annotation Classifier: undefined base chunker: " + inputClassifier);
      }
    }

    final List<String> features = new ArrayList<>();

    final File featuresFile = new File(description.get("features"));
    if (!featuresFile.exists()) {
      throw new FileNotFoundException("Error while parsing features:" + description.get("features") + " is not an existing file!");
    }
    final String iniPath = featuresFile.getAbsoluteFile().getParentFile().getAbsolutePath();
    final BufferedReader br = new BufferedReader(new FileReader(featuresFile));
    final StringBuffer sb = new StringBuffer();
    String feature = br.readLine();
    while (feature != null) {
      if (!feature.isEmpty() && !feature.startsWith("#")) {
        feature = feature.trim().replace("{INI_PATH}", iniPath);
        features.add(feature);
      }
      feature = br.readLine();
    }

    final AnnotationWekaClassifierChunker chunker = new AnnotationWekaClassifierChunker(baseChunker, features);

    final String mode = description.get("mode");
    final String modelPath = description.get("store");
    final File modelFile = new File(modelPath);
    if (mode.equals("load") && modelFile.exists()) {
      chunker.deserialize(modelPath);
    } else if (mode.equals("train")) {
      train(description, chunker, cm);
      modelFile.createNewFile();
      chunker.serialize(modelPath);
    } else {
      throw new Exception("Unrecognized mode for annotation classifier chunker: " + mode + "(Valid: train/load)");
    }
    return chunker;
  }

  private void train(final Profile.Section description, final AnnotationWekaClassifierChunker chunker, final ChunkerManager cm) throws Exception {
    final String[] parameters;
    if (description.containsKey("parameters")) {
      parameters = description.get("parameters").split(",");
    } else {
      parameters = new String[0];
    }
    List<Pattern> types = new ArrayList<>();
    if (description.containsKey("types")) {
      types = LinerOptions.getGlobal().parseTypes(description.get("types"));
    }

    final String inputFile = description.get("training-data");

    // Setup training data
    ArrayList<Document> trainData = new ArrayList<>();
    if (inputFile.equals("{CV_TRAIN}")) {
      trainData = cm.trainingData;
    } else {
      final String inputFormat = description.get("format");
      final AbstractDocumentReader reader =
          ReaderFactory.get().getStreamReader(inputFile, inputFormat);
      Document document = reader.nextDocument();
      while (document != null) {
        trainData.add(document);
        document = reader.nextDocument();
      }
    }


    ConsolePrinter.log("--> Training on file=" + inputFile);
    chunker.setTypes(types);
    for (final Document document : trainData) {
      chunker.updateClassDomain(document);
    }
    chunker.initializeTraining(description.get("classifier"), parameters, description.get("strategy"));
    for (final Document document : trainData) {
      chunker.addTrainingData(document);
    }
    chunker.train();
  }

}
