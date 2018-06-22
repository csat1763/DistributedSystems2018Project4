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

public class FindMinPrice {

	public static void main(String[] args)
			throws FileNotFoundException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		ArrayList<Future<LinkedTreeMap<String, Object>>> searchResultsFromThreads = new ArrayList<Future<LinkedTreeMap<String, Object>>>();
		ExecutorService pool = Executors.newFixedThreadPool(5);
		String path = args[0];
		String instanceFile = args[1];
		String regionFfile = args[2];
		JsonReader reader = new JsonReader(new FileReader(path + "\\" + instanceFile));
		LinkedTreeMap<String, Object> jsonResult = new Gson().fromJson(reader, LinkedTreeMap.class);

		JsonReader reader2 = new JsonReader(new FileReader(path + "\\" + regionFfile));
		LinkedTreeMap<String, Object> jsonResult2 = new Gson().fromJson(reader2, LinkedTreeMap.class);

		@SuppressWarnings("unchecked")
		List<LinkedTreeMap<String, Object>> results = (ArrayList<LinkedTreeMap<String, Object>>) jsonResult
				.get("types");

		@SuppressWarnings("unchecked")
		List<LinkedTreeMap<String, Object>> results2 = (ArrayList<LinkedTreeMap<String, Object>>) jsonResult2
				.get("Regions");

		if (null != results && !results.isEmpty()) {
			for (LinkedTreeMap<String, Object> result : results) {
				String type = result.get("type").toString();
				if (null != results && !results.isEmpty()) {
					for (LinkedTreeMap<String, Object> result2 : results2) {
						String region = result2.get("RegionName").toString();
						searchResultsFromThreads.add(pool.submit(
								new MinFinder2(path + "\\spots\\" + type + "\\" + region + "\\", path + "\\spots\\",
										type + "\\" + region + "\\" + type + "-prices-" + region + ".json")));
						// c5.2xlarge-prices-ap-northeast-1.json
					}
					getBestForInstanceType(searchResultsFromThreads, path + "\\spots\\" + type);
					searchResultsFromThreads.clear();
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

	public static void getBestForInstanceType(ArrayList<Future<LinkedTreeMap<String, Object>>> searchResultsFromThreads,
			String path) throws InterruptedException, ExecutionException, FileNotFoundException {
		ArrayList<LinkedTreeMap<String, Object>> searchResults = new ArrayList<LinkedTreeMap<String, Object>>();

		// System.out.println("Cheapest Spot-Instances from all regions:");
		for (Future<LinkedTreeMap<String, Object>> res : searchResultsFromThreads) {
			LinkedTreeMap<String, Object> oneRes = res.get();
			searchResults.add(oneRes);
			// System.out.println(prettyJson(oneRes));
		}

		HashMap<Integer, Float> idxPrice = new HashMap<Integer, Float>();
		ValueComparator vc = new ValueComparator(idxPrice);
		TreeMap<Integer, Float> sortedMap = new TreeMap<Integer, Float>(vc);

		int i = 0;
		for (LinkedTreeMap<String, Object> res : searchResults) {
			if (res.isEmpty())
				continue;
			Float price = Float.parseFloat(res.get("SpotPrice").toString());
			// System.out.println(name);
			// prices.add(name);
			idxPrice.put(i, price);
			i++;
		}
		sortedMap.putAll(idxPrice);
		// System.out.println();
		// System.out.println("Cheapest Champion:");
		// System.out.println("###########################################");
		// System.out.println(prettyJson(searchResults.get(sortedMap.entrySet().iterator().next().getKey())));
		// System.out.println("###########################################");

		File newFile = new File(path + "\\" + "spot-pair.json");
		PrintWriter pw = new PrintWriter(newFile);
		Iterator<Entry<Integer, Float>> it = sortedMap.entrySet().iterator();
		pw.println("[");
		pw.println(prettyJson(searchResults.get(it.next().getKey())));
		pw.println(",");
		pw.println(prettyJson(searchResults.get(it.next().getKey())));
		pw.println("]");
		pw.close();

	}

}

class MinFinder2 implements Callable<LinkedTreeMap<String, Object>> {

	private String path;
	private String regionName;
	private String resultpath;

	public MinFinder2(String resultpath, String path, String regionName) {
		this.path = path;
		this.regionName = regionName;
		this.resultpath = resultpath;
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
			if (sortedMap.isEmpty())
				return new LinkedTreeMap<String, Object>();
			Integer idx = sortedMap.entrySet().iterator().next().getKey();
			spotInstance.get(idx).put("Index", idx);
			String zone = spotInstance.get(idx).get("AvailabilityZone").toString();
			if (!Character.isDigit((zone.substring(zone.length() - 1)).toCharArray()[0])) {
				zone = zone.substring(0, zone.length() - 1);
			}
			spotInstance.get(idx).put("Zone", zone);

			File newFile = new File(resultpath + "\\" + "best-" + zone + ".json");

			PrintWriter pw = new PrintWriter(newFile);
			pw.println(Main.prettyJson(spotInstance.get(idx)));
			pw.close();
			// System.out.println(resultpath + "\\" + "best-" + zone + ".json");

			return spotInstance.get(idx);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}
}
