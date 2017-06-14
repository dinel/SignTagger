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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that stores parameters in a properties file
 * @author dinel
 */

public class PropertiesLoader {
    private Properties props;
    
    public void readProperties() throws IOException {
        FileReader input;
        try {
            input = new FileReader(new File("config.properties"));
            props = new Properties();
            props.load(input); 
        } catch (FileNotFoundException ex) {           
            throw new IOException("Configuration file cannot be found");
        }
    }
    
    public void writeProperties() throws FileNotFoundException, IOException {
        try (OutputStream output = new FileOutputStream("config.properties.dummy")) {
            props = new Properties();
            props.setProperty("GateHome", "<set path>");
            props.setProperty("GatePlugins", "<set path>");
            props.setProperty("CRFPath", "<set path>");
            props.setProperty("GroovyScriptPath", "<set path>");
            props.setProperty("ResourcesPrefix", "<set path>");
            props.setProperty("TempPath", "<set path>");
            props.store(output, null);
        }
    }
    
    public Properties getProperties() {
        return props;
    }
    
    public static String addFinalSlash(String path) {
        if(path.endsWith("/")) return path;
        else return path + "/";
    }
    
}
