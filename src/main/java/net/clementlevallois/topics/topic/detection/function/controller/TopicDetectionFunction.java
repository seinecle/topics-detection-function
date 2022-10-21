/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package net.clementlevallois.topics.topic.detection.function.controller;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.clementlevallois.cowo.controller.CowoFunction;
import net.clementlevallois.utils.Multiset;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class TopicDetectionFunction {

    public static void main(String[] args) {
        System.out.println("Hello World!");
    }

    public Map<Integer, Multiset<String>> analyze(TreeMap<Integer, String> mapOfLines, String selectedLanguage, Set<String> userSuppliedStopwords, boolean shouldreplaceStopwords, boolean isScientificCorpus, int precisionModularity) {
        CowoFunction cowo = new CowoFunction();
        String gexf = cowo.analyze(mapOfLines, selectedLanguage, userSuppliedStopwords, 5, shouldreplaceStopwords, isScientificCorpus, 3, 3, "none");

        /* TURN THE GEXF RETURNED FROM THE COWO API INTRO A GRAPH MODEL  */
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        //Import file
        Container container;
        FileImporter fi = new ImporterGEXF();
        InputStream is = new ByteArrayInputStream(gexf.getBytes());
        container = importController.importFile(is, fi);
        container.closeLoader();
        DefaultProcessor processor = new DefaultProcessor();

        processor.setWorkspace(pc.getCurrentWorkspace());
        processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
        processor.process();

        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        GraphModel gm = graphController.getGraphModel();
        Graph graph = gm.getGraph();

        Modularity modularity = new Modularity();
        modularity.setUseWeight(true);
        modularity.setRandom(false);

//converting the value set by the user (between 0 and 100) to a value in the [0,2] range which fits the calculus of modularity by Gephi
// see https://stackoverflow.com/q/929103/798502
        double oldRange = 100d;
        double newRange = 2d;
        double resolution = (((double) precisionModularity) * newRange / oldRange);
        System.out.println("resolution is: " + resolution);
        modularity.setResolution(resolution);
        modularity.execute(graph);

//finding topics
        Map<Integer, Multiset<String>> communitiesResult = new HashMap();

        for (Node next : graph.getNodes()) {
            Integer v = (Integer) next.getAttribute(Modularity.MODULARITY_CLASS);
            if (!communitiesResult.containsKey(v)) {
                communitiesResult.put(v, new Multiset());
            }
            Multiset<String> termsInCommunity = communitiesResult.get(v);
            termsInCommunity.addSeveral(next.getLabel(), (Integer) next.getAttribute("countTerms"));
            communitiesResult.put(v, termsInCommunity);
        }

        return communitiesResult;
    }
}
