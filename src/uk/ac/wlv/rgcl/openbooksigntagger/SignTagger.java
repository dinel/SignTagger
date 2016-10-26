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

import gate.Corpus;
import gate.CorpusController;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.ProcessingResource;
import gate.creole.ResourceInstantiationException;
import gate.creole.SerialAnalyserController;
import gate.util.GateException;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper for a Gate embedded application which runs CRF++ as a classifier. 
 * Based on code written by Iustin Dornescu in the FIRST project 
 * (http://first-asd.eu)
 * 
 * @author dinel
 */

public class SignTagger  {

    /**
     * A pool of workers
     */
    private BlockingQueue<CorpusController> pool;

    /**
     * This allows up to 1 working threads
     * Initially it was 2, but not necessary as we do not run in a webapp
     */
    final private int POOL_SIZE = 1;

    public SignTagger(Properties props) {
        init(props);
    }

    /**
     * Creates the Gate application workers
     * Assumes Gate.init()! 
     */
    public void init(Properties props) {
        if (pool != null)return;
        pool = new LinkedBlockingQueue<>();
        try {
            for(int i = 0; i < POOL_SIZE; i++) {
                    pool.add(loadController(props));
            }
        } catch (GateException | IOException e) {
            Logger.getLogger(SignTagger.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    public void destroy() {
        pool.stream().forEach((c) -> {
            Factory.deleteResource(c);
        });
    }


    Document process(Document doc) {
        Document d = null;
        try {
            d = processGate(doc);
        } catch (GateException | IOException e) {
            Logger.getLogger(SignTagger.class.getName()).log(Level.SEVERE, null, e);
        }
        return d;
    }

    /**
     * Creates a GATE application
     * @return
     * @throws GateException
     * @throws IOException
     */
    protected CorpusController loadController(Properties props) throws GateException, IOException{
        //load application
        SerialAnalyserController application = (SerialAnalyserController) Factory.createResource(
                        "gate.creole.SerialAnalyserController", Factory.newFeatureMap(),
                        Factory.newFeatureMap(), "SYNC_" + Gate.genSym() );

        // load each standard PR 
        for(String prname:new String[]{
                        "gate.creole.tokeniser.DefaultTokeniser",
                        "gate.creole.splitter.SentenceSplitter",
                        "gate.creole.POSTagger"
        }) {
                FeatureMap params = Factory.newFeatureMap(); // use default parameters
                ProcessingResource pr = (ProcessingResource)
                                Factory.createResource(prname, params);
                application.add(pr);
        } // for each ANNIE PR

        //load prepareCRF groovy script
        ProcessingResource pr = loadSignProcessor(props);
        if (pr!=null) application.add(pr);

        return application;
    }

    ProcessingResource loadSignProcessor(Properties props) throws ResourceInstantiationException{
        FeatureMap params = Factory.newFeatureMap();
        FeatureMap scriptParams = Factory.newFeatureMap();

        params.put("inputASName","");
        params.put("outputASName","syntax"); 
                
        params.put("scriptURL", 
                PropertiesLoader.addFinalSlash(props.getProperty("GroovyScriptPath")) +
                        "exportCRFpr.v0.7.groovy");

        //which model to use
        //if useSyntax=true, then Stanford annotations are assumed to be present 
        scriptParams.put("useSyntax","false");
        
        //files/paths
        String tmpFileName = "out-crf-"+(int)(100+Math.random()*900)+"-tmp.txt";
        scriptParams.put("outFileBuffer", PropertiesLoader.addFinalSlash(props.getProperty("TempPath"))
                + tmpFileName);
        scriptParams.put("prefix", PropertiesLoader.addFinalSlash(props.getProperty("ResourcesPrefix")));
        scriptParams.put("crfprefix", PropertiesLoader.addFinalSlash(props.getProperty("CRFPath")));
        
        //other params
        scriptParams.put("trainMode","false");//use Gold annotations ("Original markups")
        scriptParams.put("buildMode","false");//train a new Model on the generated file
        scriptParams.put("predictMode","true");//use model to make predictions

        params.put("scriptParams", scriptParams);
        ProcessingResource pr= (ProcessingResource) 
                Factory.createResource("gate.groovy.ScriptPR", params);
                
        return pr;
    }

    Document processGate(Document doc) throws GateException, IOException{
        //1. process with gate
        //2. generate features vectors
        //3. get model predictions
        //4. create obstacle detection annotations

        CorpusController app = null;
        try {
            app=pool.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            // Create a Corpus to use. 
            Corpus corpus = Factory.newCorpus("BatchProcessApp Corpus");
            app.setCorpus(corpus);
            corpus.add(doc);
            // run the application
            app.execute();

            // remove the document from the corpus again
            corpus.clear();
            //doc.getAnnotations("").clear();
            return doc;
        }finally{
            pool.add(app);
        }
    }    
}
