import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

public class Main {

	public static void main(String[] args)
			throws FileNotFoundException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		ArrayList<Future<LinkedTreeMap<String, Object>>> searchResultsFromThreads = new ArrayList<Future<LinkedTreeMap<String, Object>>>();
		ExecutorService pool = Executors.newFixedThreadPool(5);
		String path = args[0];
		String fileName = args[1];
		JsonReader reader = new JsonReader(new FileReader(path + "\\" + fileName));
		LinkedTreeMap<String, Object> jsonResult = new Gson().fromJson(reader, LinkedTreeMap.class);

		@SuppressWarnings("unchecked")
		List<LinkedTreeMap<String, Object>> results = (ArrayList<LinkedTreeMap<String, Object>>) jsonResult
				.get("Regions");

		if (null != results && !results.isEmpty()) {
			for (LinkedTreeMap<String, Object> result : results) {
				String regionName = result.get("RegionName").toString();
				searchResultsFromThreads.add(pool.submit(new MinFinder(path, "/jsons/prices-" + regionName + ".json")));

			}

		}

		ArrayList<LinkedTreeMap<String, Object>> searchResults = new ArrayList<LinkedTreeMap<String, Object>>();

		pool.shutdown();
		System.out.println("Cheapest Spot-Instances from all regions:");
		for (Future<LinkedTreeMap<String, Object>> res : searchResultsFromThreads) {
			LinkedTreeMap<String, Object> oneRes = res.get();
			searchResults.add(oneRes);
			System.out.println(prettyJson(oneRes));
		}

		HashMap<Integer, Float> idxPrice = new HashMap<Integer, Float>();
		ValueComparator vc = new ValueComparator(idxPrice);
		TreeMap<Integer, Float> sortedMap = new TreeMap<Integer, Float>(vc);

		int i = 0;
		for (LinkedTreeMap<String, Object> res : searchResults) {
			Float price = Float.parseFloat(res.get("SpotPrice").toString());
			// System.out.println(name);
			// prices.add(name);
			idxPrice.put(i, price);
			i++;
		}
		sortedMap.putAll(idxPrice);
		System.out.println();
		System.out.println("Cheapest Champion:");
		System.out.println("###########################################");
		System.out.println(prettyJson(searchResults.get(sortedMap.entrySet().iterator().next().getKey())));
		System.out.println("###########################################");

		File newFile = new File(path + "\\" + "bestinstanceever.json");
		PrintWriter pw = new PrintWriter(newFile);
		pw.println(new Gson().toJson(searchResults.get(sortedMap.entrySet().iterator().next().getKey())));
		pw.close();
	}

	public static String prettyJson(LinkedTreeMap<String, Object> entry) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(new Gson().toJson(entry)));
	}

}

class MinFinder implements Callable<LinkedTreeMap<String, Object>> {

	private String path;
	private String regionName;

	public MinFinder(String path, String regionName) {
		this.path = path;
		this.regionName = regionName;
	}

	@Override
	public LinkedTreeMap<String, Object> call() throws Exception {

		ArrayList<LinkedTreeMap<String, Object>> spotInstance = new ArrayList<LinkedTreeMap<String, Object>>();
		HashMap<Integer, Float> idxPrice = new HashMap<Integer, Float>();
		ValueComparator vc = new ValueComparator(idxPrice);
		TreeMap<Integer, Float> sortedMap = new TreeMap<Integer, Float>(vc);
		JsonReader reader;
		try {
			reader = new JsonReader(new FileReader(path + "\\" + regionName));

			LinkedTreeMap<String, Object> jsonResult = new Gson().fromJson(reader, LinkedTreeMap.class);

			@SuppressWarnings("unchecked")
			List<LinkedTreeMap<String, Object>> results = (ArrayList<LinkedTreeMap<String, Object>>) jsonResult
					.get("SpotPriceHistory");

			int i = 0;
			if (null != results && !results.isEmpty()) {
				for (LinkedTreeMap<String, Object> result : results) {
					spotInstance.add(result);
					Float name = Float.parseFloat(result.get("SpotPrice").toString());
					idxPrice.put(i, name);
					i++;
				}

			}

			sortedMap.putAll(idxPrice);
			Integer idx = sortedMap.entrySet().iterator().next().getKey();
			spotInstance.get(idx).put("Index", idx);
			String zone = spotInstance.get(idx).get("AvailabilityZone").toString();
			if (!Character.isDigit((zone.substring(zone.length() - 1)).toCharArray()[0])) {
				zone = zone.substring(0, zone.length() - 1);
			}
			spotInstance.get(idx).put("Zone", zone);

			File newFile = new File(path + "/regions/" + zone + "\\" + "best-" + zone + ".json");
			PrintWriter pw = new PrintWriter(newFile);
			pw.println(Main.prettyJson(spotInstance.get(idx)));
			pw.close();

			return spotInstance.get(idx);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}

}