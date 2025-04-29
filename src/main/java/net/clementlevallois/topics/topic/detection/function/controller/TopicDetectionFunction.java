package net.clementlevallois.topics.topic.detection.function.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
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
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.plugin.ExporterGEXF;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.statistics.plugin.GraphDistance;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class TopicDetectionFunction {

    final Map<Integer, Multiset<Integer>> linesAndTheirKeyTopics = new HashMap();
    Map<Integer, Multiset<String>> topicsNumberToKeyTerms;
    String gexfOfSemanticNetwork;
    boolean removeAccents = false;
    private String sessionId = "";
    private String callbackURL = "";
    private String dataPersistenceId = "";
    private boolean messagesEnabled = false;

    public Map<Integer, Multiset<Integer>> getLinesAndTheirKeyTopics() {
        return linesAndTheirKeyTopics;
    }

    public Map<Integer, Multiset<String>> getTopicsNumberToKeyTerms() {
        return topicsNumberToKeyTerms;
    }

    public String getGexfOfSemanticNetwork() {
        return gexfOfSemanticNetwork;
    }

    public void setRemoveAccents(boolean removeAccents) {
        this.removeAccents = removeAccents;
    }

    public static void main(String[] args) {

        // to conduct tests
        Path exampleGexf = Path.of("G:\\Mon Drive\\Twitch stream\\assets\\datasets\\accounts that the NYT follows\\NYT_friends_network.gexf");

        TopicDetectionFunction function = new TopicDetectionFunction();
        GraphModel gm = function.loadTestGexf(exampleGexf);
        function.findTopicsInGexf(gm, 70);
    }

    public void setSessionIdAndCallbackURL(String sessionId, String callbackURL, String dataPersistenceId) {
        this.sessionId = sessionId;
        this.callbackURL = callbackURL;
        this.dataPersistenceId = dataPersistenceId;
        messagesEnabled = true;
    }

    public void analyze(TreeMap<Integer, String> mapOfLines, String selectedLanguage, Set<String> userSuppliedStopwords, boolean shouldreplaceStopwords, boolean isScientificCorpus, int precisionModularity, int maxNGrams, int minCharNumber, int minTermFreq, boolean lemmatize) {
        CowoFunction cowo = new CowoFunction();
        cowo.setFlattenToAScii(removeAccents);
        if (messagesEnabled) {
            cowo.setSessionIdAndCallbackURL(sessionId, callbackURL, dataPersistenceId);
        }
        String gexf = cowo.analyze(mapOfLines, selectedLanguage, userSuppliedStopwords, minCharNumber, shouldreplaceStopwords, isScientificCorpus, false, false, 3, minTermFreq, "none", maxNGrams, lemmatize);

        GraphModel gm = importGexfAsGraph(gexf);

        topicsNumberToKeyTerms = findTopicsInGexf(gm, precisionModularity);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (Map.Entry<Integer, String> nextLine : mapOfLines.entrySet()) {
                scope.fork(() -> {
                    Integer lineNumber = nextLine.getKey();
                    String line = nextLine.getValue().toLowerCase();
                    Multiset<Integer> keyTopicsForThisLine = new Multiset<>();
                    for (Map.Entry<Integer, Multiset<String>> nextTopic : topicsNumberToKeyTerms.entrySet()) {
                        Integer topicNumber = nextTopic.getKey();
                        Multiset<String> keywords = nextTopic.getValue();
                        for (String keyword : keywords.getElementSet()) {
                            if (line.contains(keyword)) {
                                keyTopicsForThisLine.addSeveral(topicNumber, keywords.getCount(keyword));
                            }
                        }
                    }
                    synchronized (linesAndTheirKeyTopics) {
                        linesAndTheirKeyTopics.put(lineNumber, keyTopicsForThisLine);
                    }
                    return null;
                });
            }
            scope.join();
            scope.throwIfFailed();
        } catch (InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public Map<Integer, Multiset<String>> findTopicsInGexf(GraphModel gm, int precisionModularity) {

        Map<Integer, Multiset<String>> communitiesResult = new HashMap();

        Graph graph = gm.getGraph();

        Modularity modularity = new Modularity();
        modularity.setUseWeight(true);
        modularity.setRandom(false);

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

        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        Workspace currentWorkspace = pc.getCurrentWorkspace();

        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        ExporterGEXF exporterGexf = (ExporterGEXF) ec.getExporter("gexf");
        exporterGexf.setWorkspace(currentWorkspace);
        exporterGexf.setExportDynamic(false);
        exporterGexf.setExportPosition(false);
        exporterGexf.setExportSize(false);
        exporterGexf.setExportColors(false);

        StringWriter stringWriter = new StringWriter();
        ec.exportWriter(stringWriter, exporterGexf);
        try {
            stringWriter.close();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        gexfOfSemanticNetwork = stringWriter.toString();

        NodePartitionFilter partitionFilter = new NodePartitionFilter(appearanceModel, appearanceModel.getNodePartition(gm.getNodeTable().getColumn(Modularity.MODULARITY_CLASS)));
        partitionFilter.unselectAll();
        Collection values = partitionFilter.getPartition().getValues(graph);
        for (Object value : values) {
            Integer communityNumber = (Integer) value;
            partitionFilter.addPart(value);
            Query query = filterController.createQuery(partitionFilter);
            GraphView view = filterController.filter(query);
            gm.setVisibleView(view);
            GraphDistance distance = new GraphDistance();
            distance.setDirected(true);
            distance.setNormalized(true);
            distance.execute(gm.getGraphVisible());
            Column centralityColumn = gm.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
            for (Node node : gm.getGraphVisible().getNodes().toArray()) {
                double centrality = (double) node.getAttribute(centralityColumn);
                double biggerCentrality = centrality * 1_000_000;
                int centralityRounded = Long.valueOf(Math.round(biggerCentrality)).intValue();
                if (!communitiesResult.containsKey(communityNumber)) {
                    communitiesResult.put(communityNumber, new Multiset());
                }
                Multiset<String> termsInCommunity = communitiesResult.get(communityNumber);
                termsInCommunity.addSeveral(node.getLabel(), (Integer) centralityRounded);
                communitiesResult.put(communityNumber, termsInCommunity);
            }
            partitionFilter.unselectAll();
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

    private GraphModel loadTestGexf(Path exampleGexf) {
        GraphModel gm = null;
        try {

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

        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return gm;
    }
}
