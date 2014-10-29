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
import org.wikibrain.core.model.Title;
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
	
	public static LocalPageDao lpDao = null;
	public static LocalLinkDao llDao = null;
	public static SpatialDataDao sdDao = null;
	public static SpatialContainmentDao scDao = null;
	public static UniversalPageDao upDao = null;
	public static SRMetric metric = null;
	public static GeodeticCalculator calc = null;
	
    public static void main(String args[]) throws ConfigurationException, DaoException{

        //Part 0: Initialize the data accessing objects (DAOs) and configurations we need to use.
        //It is not required to understand this part :)
        Logger LOG = Logger.getLogger(QueryExample.class.getName());
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        lpDao = conf.get(LocalPageDao.class);
        llDao = conf.get(LocalLinkDao.class);
        sdDao = conf.get(SpatialDataDao.class);
        scDao = conf.get(SpatialContainmentDao.class);
        upDao = conf.get(UniversalPageDao.class);
        PhraseAnalyzer pa = conf.get(PhraseAnalyzer.class, "anchortext");
        metric = conf.get(SRMetric.class, "ensemble", "language", "simple");
        calc = new GeodeticCalculator();

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
        
        UniversalPage uomPage = universalPage1;
        Geometry uomPoint = g1;
        
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
        TIntSet containedIds = getSimpleArticlesFromCountry("China");
        TIntIterator iterator = containedIds.iterator();
        System.out.println("\nPart 4 result");
        
        double minDistance = 99999999;
        LocalPage closestSchool = null;
        int schoolCount = 0;
        
        while (iterator.hasNext()){
            int localPageId = upDao.getById(iterator.next()).getLocalId(Language.SIMPLE);
            LocalPage page = lpDao.getById(Language.SIMPLE, localPageId);
            String title = page.getTitle().getCanonicalTitle();
            if (title.contains("College") || title.contains("University")) {
            	System.out.println(page);
            	schoolCount++;
            	Geometry point = sdDao.getGeometry(upDao.getByLocalPage(page).getUnivId(), "wikidata");
            	
            	double distance = computeDistance(uomPoint, point);
            	if (distance < minDistance) {
            		minDistance = distance;
            		closestSchool = page;
            	}
            }
        }
        
        System.out.println("School count: " + schoolCount);
        System.out.println("Closest school: " + closestSchool);

        //Part 5: How to calculate the distance between points (representing the geotags of Wikipedia articles)
        System.out.println("\nPart 5 result");
        Geometry point1 = sdDao.getGeometry(upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, "Tiananmen Square")).getUnivId(), "wikidata");
        Geometry point2 = sdDao.getGeometry(upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, "Statue of Liberty")).getUnivId(), "wikidata");
        computeDistance(point1, point2);

        //Part 6: How to get all the parseable inlinks of a page
        /**
         * Parseable links are links that, in general, are entered manually by Wikipedia editors. Unparseable links come from
         * "templates" in Wikipedia. For more information, see the suggested reading for this week.
         *
         * Inlinks are links TO Wikipedia articles. Outlinks are links FROM (or ON) Wikipedia articles.
         */
        LocalPage localPage2 = lpDao.getByTitle(Language.SIMPLE, "University of Minnesota");
        //check the defination of getLinks for details (in IntelliJ, click "getLinks" while pressing Ctrl)
        Iterable<LocalLink> inlinks = getInlinksFromLocalPage(localPage2); 
        System.out.println("\nPart 6 result");
        Iterator<LocalLink> inlinkItr = inlinks.iterator();
        System.out.println("Inlinks:");
        while(inlinkItr.hasNext()){
            System.out.println(inlinkItr.next());
        }
        
        String [] states = {"California", "Minnesota", "Illinois", "Florida", "West Virginia"};
        int uomPageId = localPage2.getLocalId();
        
        for (String state : states) {
        	TIntSet articles = getSimpleArticlesFromState(state);
        	TIntIterator aIterator = articles.iterator();
        	
        	int articleCount = 0;

            while (aIterator.hasNext()){
                int localPageId = upDao.getById(aIterator.next()).getLocalId(Language.SIMPLE);
                LocalPage page = lpDao.getById(Language.SIMPLE, localPageId);
                // System.out.println("page: " + page);
                
                Iterable<LocalLink> alinks = getInlinksFromLocalPage(page); 
                Iterator<LocalLink> alinkItr = alinks.iterator();
                
                int subCount = 0;
                while(alinkItr.hasNext()){
                    alinkItr.next();
                    subCount++;
                }          
                articleCount += subCount;
                
                if (state.equals("Minnesota") && localPageId == uomPageId) {
                	System.out.println("uom page inlink count: " + subCount);
                }
            }
            
            System.out.println("State: " + state + " inlinks: " + articleCount);
        }



        //Part 7: How to Calculate Semantic Relatedness
        /**
         * If two articles have a high semantic relatedness, it means the concepts they are about are quite related.
         */
        LocalPage localPage3 = lpDao.getByTitle(Language.SIMPLE, "Hamburger");
        LocalPage localPage4 = lpDao.getByTitle(Language.SIMPLE, "Pizza");
        LocalPage localPage5 = lpDao.getByTitle(Language.SIMPLE, "Chair");

        System.out.println("\nPart 7 result");
        computeSR(localPage3, localPage4); //
        computeSR(localPage4, localPage5);
        
        computeSR("Cat", "Dog");
        computeSR("Hamburger", "French fries");
        computeSR("New York City", "San Francisco");
        computeSR("Cat", "Elephant");
        
        LocalPage pizzaPage = localPage4;
        TIntSet gerIds = getSimpleArticlesFromCountry("Germany");
        iterator = gerIds.iterator();

        LocalPage closestPage = null;
        double bestSR = 0;
        
        while (iterator.hasNext()){
            int localPageId = upDao.getById(iterator.next()).getLocalId(Language.SIMPLE);
            LocalPage page = lpDao.getById(Language.SIMPLE, localPageId);   
            double score = computeSR(pizzaPage, page).getScore();
            
            if (score > bestSR) {
            	bestSR = score;
            	closestPage = page;
            	System.out.println("Closest title now: " + closestPage + " score: " + score);
            }
        }
    }

	private static TIntSet getSimpleArticlesFromState(String string) throws DaoException {
		return getSimpleArticlesFromLayer(string, "state");
	}

	private static TIntSet getSimpleArticlesFromCountry(String string) throws DaoException {
		return getSimpleArticlesFromLayer(string, "country");
	}

	private static Iterable<LocalLink> getInlinksFromLocalPage(
			LocalPage localPage) throws DaoException {
		return llDao.getLinks(Language.SIMPLE, localPage.getLocalId(), false, true, LocalLink.LocationType.NONE);
        
	}

	private static TIntSet getSimpleArticlesFromLayer(String string, String layer) throws DaoException {
        Geometry polygon = sdDao.getGeometry(upDao.getByLocalPage(lpDao.getByTitle(Language.SIMPLE, string)).getUnivId(), layer);
        Set<String> subLayer = new HashSet<String>();
        subLayer.add("wikidata");
        /**
         * We use the trove implementation of Set instead of the native Java one for performance purpose
         * Nothing special going on here, just consider it as a normal set.
         */
        TIntSet result = scDao.getContainedItemIds(polygon, "earth", subLayer, SpatialContainmentDao.ContainmentOperationType.CONTAINMENT);
        return result;
	}

	private static double computeSR(String string1, String string2) throws DaoException {
		double result = computeSR(getSimplePage(string1),getSimplePage(string2)).getScore();
		System.out.println("Semantic Relatedness between " + string1 + " and " + string2 + " is " + result);
		return result;
	}

	private static LocalPage getSimplePage(String string) throws DaoException {
		return lpDao.getByTitle(Language.SIMPLE, string);
	}

	private static SRResult computeSR(LocalPage page1, LocalPage page2) throws DaoException {
		SRResult result = metric.similarity(page1.getLocalId(), page2.getLocalId(), false);
		return result;
	}

	private static double computeDistance(Geometry point1, Geometry point2) {
        calc.setStartingGeographicPoint(point1.getCentroid().getX(), point1.getCentroid().getY());
        calc.setDestinationGeographicPoint(point2.getCentroid().getX(), point2.getCentroid().getY());

        double distance = calc.getOrthodromicDistance()/1000;
        System.out.println("Distance between " + point1 + " and " + point2 + distance + "km");
        return distance;
	}
}
