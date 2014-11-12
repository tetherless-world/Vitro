package edu.rpi.twc.dcods.vivo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class SparqlQueryUtils {
	
	public static final String endpoint = "http://info.deepcarbon.net/endpoint";
	public static final String vivoSparqlUpdateAPI = "http://128.213.3.13:8080/vivo/api/sparqlUpdate";
	
	public static JSONArray vivoSparqlSelect(String sparqlQuery) {
    	String queryStr = 
    			"PREFIX dco: <" + ServerInfo.getInstance().getDcoOntoNamespace() + "> " +
    			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
    			"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " +
    			"PREFIX vivo: <http://vivoweb.org/ontology/core#> " +
    			"PREFIX foaf: <http://xmlns.com/foaf/0.1/> " +
    			"PREFIX vcard: <http://www.w3.org/2006/vcard/ns#> " +
    			"PREFIX obo: <http://purl.obolibrary.org/obo/> " +
    			sparqlQuery;
    	String encodedQuery = new String();
		try {
			encodedQuery = URIUtil.encodeQuery(queryStr);
		} catch (URIException e1) {
			e1.printStackTrace();
		}
		String outputFormat = "&output=json";
		String url = endpoint + "/sparql?query=" + encodedQuery + outputFormat;
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		HttpResponse response;
		BufferedReader bufferedReader;
		JSONArray results = new JSONArray();
		try {
			response = client.execute(request);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream inputStream = entity.getContent();
	            		bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
	           		StringBuilder builder = new StringBuilder();
	            		for (String line = null; (line = bufferedReader.readLine()) != null;) {
	                		builder.append(line).append("\n");
	            		}
	            		JSONObject jsonObject = new JSONObject(builder.toString());
	            		results = jsonObject.getJSONObject("results").getJSONArray("bindings");
	            		bufferedReader.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
	        	client.getConnectionManager().shutdown();
	    	}
	return results;
    }
	
	public static boolean vivoSparqlAsk(String queryStr) {
		String endpoint = "http://info.deepcarbon.net/endpoint";
    	System.out.println("ASK SPARQL query: " + queryStr);
    	String encodedQuery = new String();
		try {
			encodedQuery = URIUtil.encodeQuery(queryStr);
		} catch (URIException e1) {
			e1.printStackTrace();
		}
		String outputFormat = "&output=json";
		String url = endpoint + "/sparql?query=" + encodedQuery + outputFormat;
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		HttpResponse response;
		BufferedReader bufferedReader;
		boolean result = Boolean.FALSE;
		try {
			response = client.execute(request);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream inputStream = entity.getContent();
	            		bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
	           		StringBuilder builder = new StringBuilder();
	            		for (String line = null; (line = bufferedReader.readLine()) != null;) {
	                		builder.append(line).append("\n");
	            		}
	            		JSONObject jsonObject = new JSONObject(builder.toString());
	            		result = Boolean.parseBoolean(jsonObject.getString("boolean"));
	            		bufferedReader.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
	        	client.getConnectionManager().shutdown();
	    	}
	System.out.println("ASK query result: " + result);
	return result;
	}
	
	public static int vivoSparqlInsert(String password, String email, String data) throws Exception {	 		
		URL obj = new URL(vivoSparqlUpdateAPI);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		//add reuqest header
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", "Mozilla/5.0");
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
 
		String payload = "password=" + password + "&email=" + email + "&update=" + data;
		try {
			payload = URIUtil.encodeQuery(payload);
		} catch (URIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String urlParameters = payload;
 
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(urlParameters);
		wr.flush();
		wr.close();
 
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'POST' request to URL : " + vivoSparqlUpdateAPI);
//		System.out.println("Post parameters : " + urlParameters);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
 
		//print result
		return responseCode;
	}

}
