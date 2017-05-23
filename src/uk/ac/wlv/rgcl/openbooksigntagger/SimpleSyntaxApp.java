/*
 * Copyright 2016 dinel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.wlv.rgcl.openbooksigntagger;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.creole.ANNIEConstants;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;
import gate.util.InvalidOffsetException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Class which does the necessary initialisations for the SignTagger. Based on 
 * code written by Iustin Dornescu in the FIRST project (http://first-asd.eu)
 * 
 * @author dinel
 */

public class SimpleSyntaxApp {
    static boolean gateInited = false;
    static final int HTML_OUTPUT = 1;
    static final int TXT_OUTPUT = 2;
    static final int XML_OUTPUT = 3;

    static void gateInit(Properties props) throws GateException, IOException{
        if(!gateInited) { 
            File gateHome = new File(props.getProperty("GateHome"));
            Gate.setGateHome(gateHome);
            Gate.init();
            
            // Load ANNIE plugin
            Gate.getCreoleRegister().registerDirectories(new File(
                    props.getProperty("GatePlugins"), "ANNIE").toURI().toURL());

            // Load Groovy plugin
            Gate.getCreoleRegister().registerDirectories(new File(
                    props.getProperty("GatePlugins"), "Groovy").toURI().toURL());
            gateInited = true;
        }
    }
    
    private static void printBeginningDocument(PrintStream output, int outputType) {
        if(outputType == HTML_OUTPUT) output.print("<div id='annotated-doc'>");
        if(outputType == XML_OUTPUT) output.print("<annotated-doc>");
    }
    
    private static void printEndDocument(PrintStream output, int outputType) {
        if(outputType == HTML_OUTPUT) output.print("</div><!--EDoc-->");
        if(outputType == XML_OUTPUT) output.print("</annotated-doc>");
    }
    
    private static void printBeginningSentence(PrintStream output, int outputType) {
        if(outputType == HTML_OUTPUT) output.print("<div class='sentence'>");
        if(outputType == XML_OUTPUT) output.print("<sentence>");
    }
    
    private static void printEndSentence(PrintStream output, int outputType) {
        if(outputType == HTML_OUTPUT) output.print("</div><!--ESent-->");
        if(outputType == TXT_OUTPUT) output.print("\n");
        if(outputType == XML_OUTPUT) output.print("</sentence>");
    }
    
    private static void printBeginningSign(PrintStream output, int outputType, String type) {
        if(outputType == HTML_OUTPUT) output.print("<div class='sign " + type + "'>");
        if(outputType == XML_OUTPUT) output.print("<sign type='" + type + "'>");
    }
    
    private static void printEndSign(PrintStream output, int outputType) {
        if(outputType == HTML_OUTPUT) output.print("</div><!--ESign-->");
        if(outputType == XML_OUTPUT) output.print("</sign>");
    }
    
    private static void printSignType(PrintStream output, int outputType, String type) {
        if(outputType == HTML_OUTPUT) output.print(
                "<div class='sign-label label-" + type + "'>" + type + "</div><!--ESL-->");
        if(outputType == TXT_OUTPUT) output.print(type + " ");
    }
    
    private static void printToken(PrintStream output, int outputType, String token, String pos) {
        if(outputType == TXT_OUTPUT) output.print(token);
        if(outputType == XML_OUTPUT) {
            if(token.trim().isEmpty()) output.print(" ");
            else output.print("<token pos='" + pos + "'>" + token + "</token>");
        }
        if(outputType == HTML_OUTPUT) output.print(token);
    }
    
    private static void printStatistics(PrintStream output, HashMap<String, Integer> stats) {
        output.print("<script>\n");
        output.print("var stats = [];\n");
        stats.keySet().stream().forEach((sign) -> {
            output.print("stats['" + sign + "'] = '" + stats.get(sign) + "';\n");
        });        
        output.print("</script>");
        
    }
    
    public static void printDocument(Document doc, int outputType) throws InvalidOffsetException {
        PrintStream outputStream = System.out;
        HashMap<String, Integer> statistics = new HashMap<>();
        
        printBeginningDocument(outputStream, outputType);
        
        AnnotationSet annSet = doc.getAnnotations();
        AnnotationSet namedAnnSet = doc.getNamedAnnotationSets().get("syntax");
        AnnotationSet sentences = annSet.get(
                ANNIEConstants.SENTENCE_ANNOTATION_TYPE);
        
        for(Annotation sentence : sentences.inDocumentOrder()) {
            AnnotationSet tokens = annSet.get(
                    sentence.getStartNode().getOffset(), 
                    sentence.getEndNode().getOffset());
            
            printBeginningSentence(outputStream, outputType);
            
            for(Annotation token : tokens.inDocumentOrder()) {                                
                AnnotationSet signs = namedAnnSet.get(
                        "sync",
                        token.getStartNode().getOffset(), 
                        token.getEndNode().getOffset());
                
                signs.inDocumentOrder().stream().forEach((sign) -> {
                    if((token.getType().equals(ANNIEConstants.TOKEN_ANNOTATION_TYPE) ||
                        token.getType().equals(ANNIEConstants.SPACE_TOKEN_ANNOTATION_TYPE)) &&
                       sign.getStartNode().getOffset().equals(token.getStartNode().getOffset())) {
                        printBeginningSign(outputStream, outputType, 
                                sign.getFeatures().get("type").toString());
                        
                        if(statistics.containsKey(sign.getFeatures().get("type").toString())) {
                            statistics.put(
                                    sign.getFeatures().get("type").toString(),                             
                                    statistics.get(sign.getFeatures().get("type").toString()) + 1);
                        } else {
                            statistics.put(sign.getFeatures().get("type").toString(), 1);
                        }
                    }
                });
                
                if(token.getType().equals(ANNIEConstants.TOKEN_ANNOTATION_TYPE) ||
                   token.getType().equals(ANNIEConstants.SPACE_TOKEN_ANNOTATION_TYPE)) {
                    printToken(outputStream, outputType, doc.getContent().getContent(
                            token.getStartNode().getOffset(), 
                            token.getEndNode().getOffset()).toString(),
                            token.getFeatures().containsKey("category") ? 
                                    token.getFeatures().get("category").toString() : "");
                    /*outputStream.print(doc.getContent().getContent(
                            token.getStartNode().getOffset(), 
                            token.getEndNode().getOffset()));*/
                }

                signs.inDocumentOrder().stream().forEach((sign) -> {
                    if((token.getType().equals(ANNIEConstants.TOKEN_ANNOTATION_TYPE) ||
                        token.getType().equals(ANNIEConstants.SPACE_TOKEN_ANNOTATION_TYPE)) &&
                       sign.getEndNode().getOffset().equals(token.getEndNode().getOffset())) {
                        printEndSign(outputStream, outputType);
                        printSignType(outputStream, outputType, 
                                sign.getFeatures().get("type").toString());
                    }
                });                     
            }
            printEndSentence(outputStream, outputType);                        
        }
        printEndDocument(outputStream, outputType);
                
        if(outputType == HTML_OUTPUT) {
            printStatistics(outputStream, statistics);
        }
    }
    
    public static CommandLine createCommandLineParser(String[] args) {
        Options options = new Options();
        
        Option help = new Option("help", "displays this message");
        
        Option displayFormat = Option.builder("of")
                .longOpt("output-format")
                .argName("format")
                .hasArg()
                .desc("specifies the format of the output. Valid values for "
                        + "<format> are txt, xml and html. The XML format is "
                        + "the default one.")                
                .build();
        
        options.addOption(help);
        options.addOption(displayFormat);
        
        DefaultParser parser = new DefaultParser();
        CommandLine cmd = null;
        
        try {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "java <classpath> "
                    + "uk.ac.wlv.rgcl.openbooksigntagger.SimpleSyntaxApp "
                    + " [options] <input file>", options ); 
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "Main", options );       
        }
        
        return cmd;
    }


    /**
     * @param args
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        /* read properties */
        PropertiesLoader propsLoader = new PropertiesLoader();
        Properties props = null;
                
        CommandLine cmd = SimpleSyntaxApp.createCommandLineParser(args);
        if(cmd == null) {
            System.exit(-1);
        }
        
        try { 
            propsLoader.readProperties();
        } catch (IOException ex) { 
            System.out.println("Working Directory = " +
              System.getProperty("user.dir"));
            System.err.println(
                      "The system is not configured properly. A dummy configuration \n" 
                    + "file was created. Please fill in the relevant information and \n"
                    + "remove the '.dummy' extension. Processing will stop now. \n");
            
            propsLoader.writeProperties();
                        
            System.exit(-1);
        }
                
        try {
            props = propsLoader.getProperties();
            
            gateInit(props);

            SignTagger processor = new SignTagger(props);
            
            //Document doc = Factory.newDocument(new File(args[0]).toURI().toURL());           
            String[] input_file = cmd.getArgs();
            Document doc = Factory.newDocument(new File(input_file[0]).toURI().toURL());           

            processor.process(doc);
            
            int outputFormat = XML_OUTPUT;
            if(cmd.hasOption("output-format")) {
                if(cmd.getOptionValue("output-format").equals("html")) outputFormat = HTML_OUTPUT;
                if(cmd.getOptionValue("output-format").equals("txt")) outputFormat = TXT_OUTPUT;
            }                        
            
            SimpleSyntaxApp.printDocument(doc, outputFormat);
        } catch (ResourceInstantiationException e) {
            Logger.getLogger(SimpleSyntaxApp.class.getName()).log(Level.SEVERE, null, e);
        } catch (GateException | IOException e) {
            Logger.getLogger(SimpleSyntaxApp.class.getName()).log(Level.SEVERE, null, e);
        }
    }

}
