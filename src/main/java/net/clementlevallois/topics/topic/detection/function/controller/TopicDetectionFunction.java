/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package net.clementlevallois.topics.topic.detection.function.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.clementlevallois.cowo.controller.CowoFunction;
import net.clementlevallois.utils.Multiset;
import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
import org.gephi.graph.api.Node;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.statistics.plugin.GraphDistance;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class TopicDetectionFunction {

    public static void main(String[] args) {

        TopicDetectionFunction function = new TopicDetectionFunction();
        GraphModel gm = function.loadTestGexf();
        function.findTopicsInGexf(gm, 55);

    }

    public Map<Integer, Multiset<String>> analyze(TreeMap<Integer, String> mapOfLines, String selectedLanguage, Set<String> userSuppliedStopwords, boolean shouldreplaceStopwords, boolean isScientificCorpus, int precisionModularity, int maxNGrams, int minCharNumber) {
        CowoFunction cowo = new CowoFunction();
        String gexf = cowo.analyze(mapOfLines, selectedLanguage, userSuppliedStopwords, minCharNumber, shouldreplaceStopwords, isScientificCorpus, 3, 3, "none", maxNGrams);

        GraphModel gm = importGexfAsGraph(gexf);

        Map<Integer, Multiset<String>> topics = findTopicsInGexf(gm, precisionModularity);
        return topics;

    }

    public Map<Integer, Multiset<String>> findTopicsInGexf(GraphModel gm, int precisionModularity) {

        Map<Integer, Multiset<String>> communitiesResult = new HashMap();

        Graph graph = gm.getGraph();

        Modularity modularity = new Modularity();
        modularity.setUseWeight(true);
        modularity.setRandom(false);
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        AppearanceModel appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);

//converting the value set by the user (between 0 and 100) to a value in the [0,2] range which fits the calculus of modularity by Gephi
// see https://stackoverflow.com/q/929103/798502
        double oldRange = 100d;
        double newRange = 2d;
        double resolution = (((double) precisionModularity) * newRange / oldRange);
        System.out.println("resolution is: " + resolution);
        modularity.setResolution(resolution);
        modularity.execute(graph);

        NodePartitionFilter partitionFilter = new NodePartitionFilter(appearanceModel, appearanceModel.getNodePartition(gm.getNodeTable().getColumn(Modularity.MODULARITY_CLASS)));
        partitionFilter.unselectAll();
        Collection values = partitionFilter.getPartition().getValues(graph);
        for (Object value : values) {
            System.out.println("partition: " + value);
        }
        for (Object value : values) {
            partitionFilter.addPart(value);
            Query query = filterController.createQuery(partitionFilter);
            GraphView view = filterController.filter(query);
            gm.setVisibleView(view);
            GraphDistance distance = new GraphDistance();
            distance.setDirected(true);
            distance.execute(gm.getGraphVisible());
            Column centralityColumn = gm.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
            double maxCentrality = 0d;
            for (Node node : gm.getGraphVisible().getNodes().toArray()) {
                double centrality = (double) node.getAttribute(centralityColumn);
                System.out.println("centrality: " + centrality);
            }
            partitionFilter.unselectAll();
        }

//finding topics
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

    private GraphModel importGexfAsGraph(String gexf) {
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

        return gm;

    }

    private GraphModel loadTestGexf() {
        GraphModel gm = null;
        try {
            Path exampleGexf = Path.of("G:\\Mon Drive\\Twitch stream\\assets\\datasets\\accounts that the NYT follows\\NYT_friends_network.gexf");

            String gexfFileAsString = Files.readString(exampleGexf, StandardCharsets.UTF_8);

            //Init a project - and therefore a workspace
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();

            //Get controllers and models
            ImportController importController = Lookup.getDefault().lookup(ImportController.class);

            //Import file
            Container container;
            FileImporter fi = new ImporterGEXF();

            //Append imported data to GraphAPI
            container = importController.importFile(new StringReader(gexfFileAsString), fi);
            container.closeLoader();

            DefaultProcessor processor = new DefaultProcessor();
            processor.setWorkspace(pc.getCurrentWorkspace());
            processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
            processor.process();

            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            gm = graphController.getGraphModel();

            Map<Integer, Multiset<String>> communitiesResult = new HashMap();


            AppearanceModel appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();
            FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
            System.out.println("ddd");
            Graph graph = gm.getGraph();

            Modularity modularity = new Modularity();
            modularity.setUseWeight(true);
            modularity.setRandom(false);

        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return gm;

    }
}
