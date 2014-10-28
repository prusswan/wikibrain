package org.wikibrain.cookbook.MOOC;

import com.vividsolutions.jts.geom.Geometry;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import org.geotools.referencing.GeodeticCalculator;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.core.model.UniversalPage;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.phrases.PhraseAnalyzer;
import org.wikibrain.spatial.dao.SpatialContainmentDao;
import org.wikibrain.spatial.dao.SpatialDataDao;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.SRResult;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by spatial on 10/9/14.
 * @author Toby Li
 *
 * This is an example program provided for students in the University of Minnesota's 
 * Spatial Computing MOOC to learn how to execute basic queries that they'll need to
 * use in the Module 5 programming assignment.
 */

public class QueryExample {
    public static void main(String args[]) throws ConfigurationException, DaoException{

        //Part 0: Initialize the data accessing objects (DAOs) and configurations we need to use.
        //It is not required to understand this part :)
        Logger LOG = Logger.getLogger(QueryExample.class.getName());
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        LocalPageDao lpDao = conf.get(LocalPageDao.class);
        LocalLinkDao llDao = conf.get(LocalLinkDao.class);
        SpatialDataDao sdDao = conf.get(SpatialDataDao.class);
        SpatialContainmentDao scDao = conf.get(SpatialContainmentDao.class);
        UniversalPageDao upDao = conf.get(UniversalPageDao.class);
        PhraseAnalyzer pa = conf.get(PhraseAnalyzer.class, "anchortext");
        SRMetric metric = conf.get(SRMetric.class, "ensemble", "language", "simple");

        //Part 1: Getting a Wikipedia page by its title
        /**
         * Using LocalPageDao to get a Wikipedia page using language edition and title
         * Note: You can always check the definition of functions for specific usage (In IntellliJ, click a function while pressing Ctrl)
         */
        LocalPage localPage1 = lpDao.getByTitle(Language.SIMPLE, "University of Minnesota");
        System.out.println("\nPart 1 result");
        System.out.println(localPage1);

        //Part 2: How to resolve a phrase to a Wikipedia page
        Map<LocalId, Float> resolution = pa.resolve(Language.SIMPLE, "Georgia", 5);
        System.out.println("\nPart 2 result");
        for(Map.Entry<LocalId, Float> entry: resolution.entrySet()){
            System.out.println(lpDao.getById(entry.getKey()).getTitle().getCanonicalTitle() + " " + entry.getValue());
        }

        //Part 3: How to get the geometry for a Wikipedia page with a geotag
        /**
         * "UniversalPage" is a cross-language concept, each universal page corresponds to multiple
         * local pages, each of which is about the same concept but in different language edition of Wikipedia.
         *
         * Universal page ID is also used as the key in the database for storing geometries
         */

        UniversalPage universalPage1 = upDao.getByLocalPage(localPage1);
        Geometry g1 = sdDao.getGeometry(universalPage1.getUnivId(), "wikidata");
        System.out.println("\nPart 3 result");
        System.out.println("University of Minnesota: " + g1);

        UniversalPage universalPage2 = upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, "Minnesota"));
        /**
         * As you can see, there might be multiple geometries for one Wikipedia concept
         * In this case, we have both a point representation of Minnesota in the wikidata layer, and also a
         * polygon representation in the state layer.
         *
         * By default, everything is stored in "wikidata" layer as points
         * US states are also stored in "state" layer as polygons
         * Countries are also stored in "country" layer as polygons
         */
        Geometry g2 = sdDao.getGeometry(universalPage2.getUnivId(), "wikidata");
        Geometry g3 = sdDao.getGeometry(universalPage2.getUnivId(), "state");
        System.out.println("Minnesota: " + g2);
        System.out.println("Minnesota: " + g3);


        //Part 4: How to get all the Wikipedia articles contained in a polygon
        Geometry polygon = sdDao.getGeometry(upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, "Alaska")).getUnivId(), "state");
        Set<String> subLayer = new HashSet<String>();
        subLayer.add("wikidata");
        /**
         * We use the trove implementation of Set instead of the native Java one for performance purpose
         * Nothing special going on here, just consider it as a normal set.
         */
        TIntSet containedIds = scDao.getContainedItemIds(polygon, "earth", subLayer, SpatialContainmentDao.ContainmentOperationType.CONTAINMENT);
        TIntIterator iterator = containedIds.iterator();
        System.out.println("\nPart 4 result");
        while (iterator.hasNext()){
            int localPageId = upDao.getById(iterator.next()).getLocalId(Language.SIMPLE);
            System.out.println(lpDao.getById(Language.SIMPLE, localPageId));
        }

        //Part 5: How to calculate the distance between points (representing the geotags of Wikipedia articles)
        GeodeticCalculator calc = new GeodeticCalculator();
        Geometry point1 = sdDao.getGeometry(upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, "Tiananmen Square")).getUnivId(), "wikidata");
        Geometry point2 = sdDao.getGeometry(upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, "Statue of Liberty")).getUnivId(), "wikidata");
        calc.setStartingGeographicPoint(point1.getCentroid().getX(), point1.getCentroid().getY());
        calc.setDestinationGeographicPoint(point2.getCentroid().getX(), point1.getCentroid().getY());
        System.out.println("\nPart 5 result");
        System.out.println("Distance between Tiananmen Square and Statue of Liberty " + calc.getOrthodromicDistance()/1000 + "km");

        //Part 6: How to get all the parseable inlinks of a page
        /**
         * Parseable links are links that, in general, are entered manually by Wikipedia editors. Unparseable links come from
         * "templates" in Wikipedia. For more information, see the suggested reading for this week.
         *
         * Inlinks are links TO Wikipedia articles. Outlinks are links FROM (or ON) Wikipedia articles.
         */
        LocalPage localPage2 = lpDao.getByTitle(Language.SIMPLE, "University of Minnesota");
        //check the defination of getLinks for details (in IntelliJ, click "getLinks" while pressing Ctrl)
        Iterable<LocalLink> inlinks = llDao.getLinks(Language.SIMPLE, localPage2.getLocalId(), false, true, LocalLink.LocationType.NONE);
        System.out.println("\nPart 6 result");
        Iterator<LocalLink> inlinkItr = inlinks.iterator();
        System.out.println("Inlinks:");
        while(inlinkItr.hasNext()){
            System.out.println(inlinkItr.next());
        }



        //Part 7: How to Calculate Semantic Relatedness
        /**
         * If two articles have a high semantic relatedness, it means the concepts they are about are quite related.
         */
        LocalPage localPage3 = lpDao.getByTitle(Language.SIMPLE, "Hamburger");
        LocalPage localPage4 = lpDao.getByTitle(Language.SIMPLE, "Pizza");
        LocalPage localPage5 = lpDao.getByTitle(Language.SIMPLE, "Chair");
        SRResult result34 = metric.similarity(localPage3.getLocalId(), localPage4.getLocalId(), false);
        SRResult result45 = metric.similarity(localPage4.getLocalId(), localPage5.getLocalId(), false);
        System.out.println("\nPart 7 result");
        System.out.println("Semantic Relatedness between Hamburger and Pizza is " + result34.getScore());
        System.out.println("Semantic Relatedness between Hamburger and Chair is " + result45.getScore());


    }
}
