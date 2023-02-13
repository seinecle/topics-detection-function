/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.topics.topic.detection.function.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.filters.plugin.graph.EgoBuilder.EgoFilter;
import org.gephi.filters.plugin.operator.INTERSECTIONBuilder.IntersectionOperator;
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

/**
 * This demo shows how to create and execute filter queries.
 * <p>
 * The demo creates three filters queries and execute them:
 * <ul><li>Filter degrees, remove nodes with degree < 10</li> <
 * li>Filter with partition, keep nodes with 'source' column equal to
 * 'Blogorama'</li>
 * <li>Intersection between degrees and partition, AND filter with two precedent
 * filters</li>
 * <li>Ego filter</li></ul>
 * <p>
 * When a filter query is executed, it creates a new graph view, which is a copy
 * of the graph structure that went through the filter pipeline. Several filters
 * can be chained by setting sub-queries. A query is a tree where the root is
 * the last executed filter.
 *
 * @author Mathieu Bastian
 */
public class Test {

    public static void main(String args[]) throws IOException {
        //Init a project - and therefore a workspace
        Path exampleGexf = Path.of("G:\\Mon Drive\\Twitch stream\\assets\\datasets\\accounts that the NYT follows\\NYT_friends_network.gexf");

        String gexfFileAsString = Files.readString(exampleGexf, StandardCharsets.UTF_8);

        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        AppearanceModel appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();

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
        GraphModel graphModel = graphController.getGraphModel();
        //Filter, remove degree < 10
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        DegreeRangeFilter degreeFilter = new DegreeRangeFilter();
        degreeFilter.init(graphModel.getGraph());
        degreeFilter.setRange(new Range(10, Integer.MAX_VALUE));     //Remove nodes with degree < 10
        Query query = filterController.createQuery(degreeFilter);
        GraphView view = filterController.filter(query);
        graphModel.setVisibleView(view);    //Set the filter result as the visible view

        //Count nodes and edges on filtered graph
        DirectedGraph graph = graphModel.getDirectedGraphVisible();
        System.out.println("Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

        //Filter, keep partition 'Blogarama'. Build partition with 'source' column in the data
        NodePartitionFilter partitionFilter = new NodePartitionFilter(appearanceModel, appearanceModel.getNodePartition(graphModel.getNodeTable().getColumn("source")));
        partitionFilter.unselectAll();
        partitionFilter.addPart("Blogarama");
        Query query2 = filterController.createQuery(partitionFilter);
        GraphView view2 = filterController.filter(query2);
        graphModel.setVisibleView(view2);    //Set the filter result as the visible view

        //Count nodes and edges on filtered graph
        graph = graphModel.getDirectedGraphVisible();
        System.out.println("Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

        //Combine two filters with AND - Set query and query2 as sub-query of AND
        IntersectionOperator intersectionOperator = new IntersectionOperator();
        Query query3 = filterController.createQuery(intersectionOperator);
        filterController.setSubQuery(query3, query);
        filterController.setSubQuery(query3, query2);
        GraphView view3 = filterController.filter(query3);
        graphModel.setVisibleView(view3);    //Set the filter result as the visible view

        //Count nodes and edges on filtered graph
        graph = graphModel.getDirectedGraphVisible();
        System.out.println("Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

        //Ego filter
        EgoFilter egoFilter = new EgoFilter();
        egoFilter.setPattern("obamablog.com"); //Regex accepted
        egoFilter.setDepth(1);
        Query queryEgo = filterController.createQuery(egoFilter);
        GraphView viewEgo = filterController.filter(queryEgo);
        graphModel.setVisibleView(viewEgo);    //Set the filter result as the visible view

        //Count nodes and edges on filtered graph
        graph = graphModel.getDirectedGraphVisible();
        System.out.println("Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());
    }
}
