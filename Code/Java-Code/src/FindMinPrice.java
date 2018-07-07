import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;

/* This java program crawls trough an existing folder-structure in order to do 2 executive steps:
 * 		1. Determine the cheapest spot-instance price for a given instance-type and region
 * 		2. Determine the 2 cheapest pot-instance prices for a given instance-type
 * Prerequisites:
 * 		1. Existing file: regions.json
 * 		2. Existing file: instance-types.json
 * 		3. Existing folder structure:
 * 			1. For every instance-type
 * 			2. Every region within the instance-type folders
 * 		4. Existing spot-prices as jsons files within every region folder for every instance-type:
 * 			1. Name-structure for json-file: "INSTANCENAME-prices-REGION.json"
 * 
 * Output:
 * 		1. For every instance-type in each region folder: best-REGION.json
 * 			The best price for that very region and instance type
 * 		2. For every instance-type folder: "spot-pair.json"
 * 			The 2 cheapest spot-instances for given instance-type
 *  */
public class FindMinPrice {

	public static void main(String[] args)
			throws FileNotFoundException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		ArrayList<Future<LinkedTreeMap<String, Object>>> cheapestTypesForRegions = new ArrayList<Future<LinkedTreeMap<String, Object>>>();
		ExecutorService pool = Executors.newFixedThreadPool(5);
		/* Working directory needed where the current shell is executed */
		String path = args[0];
		/* Path to the instance-types */
		String instanceFile = args[1];
		/* Path to regions */
		String regionFfile = args[2];

		JsonReader instanceTypes = new JsonReader(new FileReader(path + "/" + instanceFile));
		LinkedTreeMap<String, Object> instanceTypesJVM = new Gson().fromJson(instanceTypes, LinkedTreeMap.class);

		JsonReader regions = new JsonReader(new FileReader(path + "/" + regionFfile));
		LinkedTreeMap<String, Object> regionsJVM = new Gson().fromJson(regions, LinkedTreeMap.class);

		@SuppressWarnings("unchecked")
		List<LinkedTreeMap<String, Object>> allInstanceTypes = (ArrayList<LinkedTreeMap<String, Object>>) instanceTypesJVM
				.get("types");

		@SuppressWarnings("unchecked")
		List<LinkedTreeMap<String, Object>> allRegions = (ArrayList<LinkedTreeMap<String, Object>>) regionsJVM
				.get("Regions");

		/*
		 * For every instance-type: iterate trough all regions using the region file.
		 * 
		 * Start thread FindMindForInstanceAndRegion to find the cheapest from the
		 * region and return as Future which is then stored in cheapestTypesForRegions.
		 * "INSTANCENAME-prices-REGION.json"
		 * 
		 * Call get2BestForInstanceType with cheapestTypesForRegions to get the 2
		 * cheapest spot-instances for that instance-type. "spot-pair.json"
		 * 
		 */
		if (null != allInstanceTypes && !allInstanceTypes.isEmpty()) {
			for (LinkedTreeMap<String, Object> instanceType : allInstanceTypes) {
				String instanceTypeName = instanceType.get("type").toString();
				if (null != allInstanceTypes && !allInstanceTypes.isEmpty()) {
					for (LinkedTreeMap<String, Object> region : allRegions) {
						String regionName = region.get("RegionName").toString();
						cheapestTypesForRegions.add(pool.submit(new FindMindForInstanceAndRegion(
								path + "/spots/" + instanceTypeName + "/" + regionName + "/", path + "/spots/",
								instanceTypeName + "/" + regionName + "/" + instanceTypeName + "-prices-" + regionName
										+ ".json")));
					}
					get2BestForInstanceType(cheapestTypesForRegions, path + "/spots/" + instanceTypeName);
					cheapestTypesForRegions.clear();
				}

			}

		}

		pool.shutdown();

		System.out.println(
				"[*] Java program added best results as .json files in each region folder for every archtitecture.");

	}

	public static String prettyJson(LinkedTreeMap<String, Object> entry) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(new Gson().toJson(entry)));
	}

	public static void get2BestForInstanceType(
			ArrayList<Future<LinkedTreeMap<String, Object>>> cheapestTypeForRegionFutures, String path)
			throws InterruptedException, ExecutionException, FileNotFoundException {
		ArrayList<LinkedTreeMap<String, Object>> cheapestTypeForRegions = new ArrayList<LinkedTreeMap<String, Object>>();

		for (Future<LinkedTreeMap<String, Object>> cheapestTypeForRegionFuture : cheapestTypeForRegionFutures) {
			LinkedTreeMap<String, Object> cheapestTypeForRegion = cheapestTypeForRegionFuture.get();
			cheapestTypeForRegions.add(cheapestTypeForRegion);

		}

		/*
		 * Save the spot-instances as a HashMap cheapestTypeIndx2PriceMap which is
		 * mapping the index in the array to the price. Then use the mapValueComparator
		 * with the instance of cheapestTypeIndx2PriceMap as a base. After the whole
		 * cheapestTypeForRegions is iterated through use
		 * sortedCheapestTypeIndx2PriceMap to get a sorted version of
		 * cheapestTypeIndx2PriceMap.
		 */

		HashMap<Integer, Float> cheapestTypeIndx2PriceMap = new HashMap<Integer, Float>();
		ValueComparator mapValueComparator = new ValueComparator(cheapestTypeIndx2PriceMap);
		TreeMap<Integer, Float> sortedCheapestTypeIndx2PriceMap = new TreeMap<Integer, Float>(mapValueComparator);

		int i = 0;
		for (LinkedTreeMap<String, Object> cheapestTypeForRegion : cheapestTypeForRegions) {
			if (cheapestTypeForRegion.isEmpty())
				continue;
			Float price = Float.parseFloat(cheapestTypeForRegion.get("SpotPrice").toString());
			cheapestTypeIndx2PriceMap.put(i, price);
			i++;
		}
		sortedCheapestTypeIndx2PriceMap.putAll(cheapestTypeIndx2PriceMap);

		/*
		 * Get the indices that are stored from the first 2 elements of
		 * sortedCheapestTypeIndx2PriceMap and access the whole spot-entry from
		 * cheapestTypeForRegions and print it to a file.
		 */
		File newFile = new File(path + "/" + "spot-pair.json");
		PrintWriter pw = new PrintWriter(newFile);
		Iterator<Entry<Integer, Float>> it = sortedCheapestTypeIndx2PriceMap.entrySet().iterator();
		pw.println("[");
		pw.println(prettyJson(cheapestTypeForRegions.get(it.next().getKey())));
		pw.println(",");
		pw.println(prettyJson(cheapestTypeForRegions.get(it.next().getKey())));
		pw.println("]");
		pw.close();

	}

}

class FindMindForInstanceAndRegion implements Callable<LinkedTreeMap<String, Object>> {

	private String path;
	private String priceList;
	private String resultpath;

	public FindMindForInstanceAndRegion(String resultpath, String path, String priceList) {
		this.path = path;
		this.priceList = priceList;
		this.resultpath = resultpath;
	}

	@Override
	public LinkedTreeMap<String, Object> call() throws Exception {

		/*
		 * The json entries are put into an array-list spotInstances so it becomes
		 * easier to access. The map spotInstancesIndx2Price maps every entry from
		 * spotInstances(the index of it) to a float value which is the price. The
		 * valueComparator is instantiated with the empty spotInstancesIndx2Price so it
		 * has a base collection to compare entries. Then a new collection is
		 * instantiated with the valueComparator so each time an element is put into the
		 * collection it is sorted.
		 */
		ArrayList<LinkedTreeMap<String, Object>> spotInstances = new ArrayList<LinkedTreeMap<String, Object>>();
		HashMap<Integer, Float> spotInstancesIndx2Price = new HashMap<Integer, Float>();
		ValueComparator valueComparator = new ValueComparator(spotInstancesIndx2Price);
		TreeMap<Integer, Float> sortedSpotInstances = new TreeMap<Integer, Float>(valueComparator);
		JsonReader priceListJVM;
		try {
			priceListJVM = new JsonReader(new FileReader(path + "/" + priceList));

			LinkedTreeMap<String, Object> priceListJson = new Gson().fromJson(priceListJVM, LinkedTreeMap.class);

			@SuppressWarnings("unchecked")
			List<LinkedTreeMap<String, Object>> spotPriceHistory = (ArrayList<LinkedTreeMap<String, Object>>) priceListJson
					.get("SpotPriceHistory");

			/* Read prices from json and put it into spotInstances */
			int i = 0;
			if (null != spotPriceHistory && !spotPriceHistory.isEmpty()) {
				for (LinkedTreeMap<String, Object> result : spotPriceHistory) {
					spotInstances.add(result);
					Float spotPrice = Float.parseFloat(result.get("SpotPrice").toString());
					spotInstancesIndx2Price.put(i, spotPrice);
					i++;
				}

			}

			sortedSpotInstances.putAll(spotInstancesIndx2Price);
			if (sortedSpotInstances.isEmpty())
				return new LinkedTreeMap<String, Object>();
			/*
			 * Depending on the final collection either an empty one is returned if the
			 * previous was empty to avoid a null-pointer exception.s
			 */

			Integer cheapestEntryIdx = sortedSpotInstances.entrySet().iterator().next().getKey();
			/* Extend entry with Idx */
			spotInstances.get(cheapestEntryIdx).put("Index", cheapestEntryIdx);
			String zone = spotInstances.get(cheapestEntryIdx).get("AvailabilityZone").toString();
			/*
			 * Get zone but remove final char if it is not a numeric one: eg. eu-west-1b ->
			 * we need eu-west-1
			 */
			if (!Character.isDigit((zone.substring(zone.length() - 1)).toCharArray()[0])) {
				zone = zone.substring(0, zone.length() - 1);
			}
			/* Extend with zone */
			spotInstances.get(cheapestEntryIdx).put("Zone", zone);

			File newFile = new File(resultpath + "/" + "best-" + zone + ".json");
			/* Used that entry in spotInstances to print to a file */
			PrintWriter pw = new PrintWriter(newFile);
			pw.println(FindMinPrice.prettyJson(spotInstances.get(cheapestEntryIdx)));
			pw.close();

			return spotInstances.get(cheapestEntryIdx);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}
}
