package edu.rpi.twc.dcods.vivo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.client.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * TODO Put here a description of what this class does.
 *
 * @author cheny18.
 *         Created Sep 14, 2013.
 */
public class CKANAPI {

	private String rawDataString;
	private String fileName="tempfile";
	private String ckanDataRepoName;
	private static String apiKey = "96c00a49-9604-42ca-84b1-e43674f6c0f8";
	
	public static void main(String args[]){
		
		String test = ServerInfo.getInstance().getCkanURL()+"/storage/f/2013-10-15-064857/DataScience_2013_Assignment-2.pdf";
		int len = test.split("\\.").length;
		
		System.out.println(test.split("\\.")[len-1]);
	}
	public CKANAPI(){
		
	}
	
	public CKANAPI(String ckanDataRepoName){
		this.ckanDataRepoName = ckanDataRepoName;
	}
	
	public CKANAPI(String rawData,String fileName){
		this.rawDataString = rawData;
		this.fileName = fileName;
	}
	
	
	private final static String USER_AGENT = "Mozilla/5.0";
	
	// Check if there is already a repo exists;
	// Return value reflects the existence of the repo
	// If exists, then just return the url of the dataRepo;Otherwise, return an empty string
	public String checkExistence(String dataRepoName){
		Client client = new Client( new Connection(ServerInfo.getInstance().getCkanURL()), "");

        try {
            // Get the search results for the word gold
            Dataset.SearchResults search_results = client.findDatasets(dataRepoName);
            for (Dataset dataset : search_results.results ) {
                int count = 0;

                System.out.println("Adding resources from " + dataset.getTitle() + "\n");
                for (Resource resource : dataset.getResources() ) {
                    count = count + 1;
                    System.out.println( " " + count + ". "  + resource.getName() );
                    System.out.println( "    Format: "      + resource.getFormat() );
                    System.out.println( "    Mimetype: "    + resource.getMimetype() );
                    System.out.println( "    Description: " + resource.getDescription() );
                    System.out.println( "    URL: "         + resource.getUrl() + "\n");
                }
                System.out.println("");
            }
        } catch ( CKANException e ) {
            System.out.println(e);
        }
        
		
		return "";
	}
	
	//Associate a dataset with a data repo on ckan by name
	//Return value is the accessURL for the corresponding data repo
	public String associateRepoWithDataset(String dataRepoURL, String datasetDownloadURL,String rawDataName){
		
				
				int arrayLen = dataRepoURL.split("/").length;
				String datasetName = dataRepoURL.split("/")[arrayLen-1];
				int dotTokenArray = datasetDownloadURL.split("\\.").length;
				String dataFormat = datasetDownloadURL.split("\\.")[dotTokenArray-1];
				
				String udcoAPIAddr = ServerInfo.getInstance().getCkanURL()+"/api/rest/dataset/";
				HttpClient httpClient = new DefaultHttpClient();
				
				// Get back the dataset , if there is any, coutn the number, grab thoose old ones, add new resource accordingly, genreate teh new message 
				HttpGet get = new HttpGet(udcoAPIAddr+datasetName);
			
				String newResourceArrayStr = "";
				int currentResourceSize = 0;
				JsonObject allJsonParams = new JsonObject();
				try {
					HttpResponse getResponse = httpClient.execute(get);
					BufferedReader rd = new BufferedReader(new InputStreamReader(getResponse.getEntity().getContent()));
				      String line = "";
				      String allContent = "";
				      while ((line = rd.readLine()) != null) {
				    	  allContent +=line;
				        System.out.println(line);
				      }
				      
					String bodyString = allContent;
					System.out.println("The GET response is "+bodyString);
					JsonObject jsonObj = (new JsonParser()).parse(bodyString).getAsJsonObject();
					//Count how many resources are already there, preserve them, set the next pointer to be the length of the current array, 
					//And add the resource to the jsonArray
					currentResourceSize = jsonObj.get("resources").getAsJsonArray().size();
					
					// Setting up the resource message
					JsonObject newResourceObj = new JsonObject();
					newResourceObj.addProperty("description", rawDataName);
					newResourceObj.addProperty("format", dataFormat);
					newResourceObj.addProperty("url", datasetDownloadURL);
					
					JsonArray newResources = jsonObj.get("resources").getAsJsonArray();
					newResources.add(newResourceObj);
					newResourceArrayStr = newResources.toString();
					
					allJsonParams.add("resources", newResources);
					allJsonParams.addProperty("num_resources", currentResourceSize+1);
					
					
				} catch (HttpException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				System.out.println("new resource string is "+allJsonParams);
				
				HttpClient httpclient = new DefaultHttpClient();
				HttpPost httppost = new HttpPost(udcoAPIAddr+datasetName);
				
				try {
					httppost.setHeader("Authorization", apiKey);
					httppost.setEntity(new StringEntity(allJsonParams.toString(),"UTF-8"));
					HttpResponse response;
					
					response = httpclient.execute(httppost);
					HttpEntity entity = response.getEntity();
					
					if (entity != null) {
						//Execute and get the response.
					    InputStream instream = entity.getContent();
					    System.out.println("The response is");
					    IOUtils.copy(instream, System.out);
					}
					
				} catch (UnsupportedEncodingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
		return dataRepoURL;
	}
	
	//Create a new datarepo
	//Return the accessURL for the datarepo
	public String createDataRepo(String dataRepoName){
		
		return "";
	}
	
	
	// HTTP GET request
	private void sendGet() throws Exception {
 
		String url = ServerInfo.getInstance().getCkanURL()+"/api/3/action/package_list";
		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		// optional default is GET
		con.setRequestMethod("GET");
 
		//add request header
		con.setRequestProperty("User-Agent", USER_AGENT);
		
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + url);
		System.out.println("Response Code : " + responseCode);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
 
		//print result
		Gson gson = new Gson();
		System.out.println("Response text is "+response.toString());
		JsonElement element = gson.fromJson(response.toString(), JsonElement.class);
		JsonObject jsonObj = element.getAsJsonObject();
		
		Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
		String json = prettyGson.toJson(jsonObj);
		System.out.println(json);
		 
	}
		
	// HTTP POST request
	private void sendPost() throws Exception {
 
		String url = "https://selfsolve.apple.com/wcResults.do";
		URL obj = new URL(url);
		HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
 
		//add reuqest header
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
 
		String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";
 
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(urlParameters);
		wr.flush();
		wr.close();
 
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'POST' request to URL : " + url);
		System.out.println("Post parameters : " + urlParameters);
		System.out.println("Response Code : " + responseCode);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
 
		//print result
		System.out.println(response.toString());
 
	}
	public String upload_rawdata(InputStream fileStream,String orgFileName) throws IOException{
		
		String url = ServerInfo.getInstance().getCkanURL()+"/storage/upload_handle";
		String api_key = "96c00a49-9604-42ca-84b1-e43674f6c0f8";
		//Generate a date timestamp
		long currentTime = System.currentTimeMillis();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-hhmmss",Locale.US);

        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("US/Central"));
        calendar.setTimeInMillis(currentTime);
        System.out.println("GregorianCalendar -"+sdf.format(calendar.getTime()));
        String ts = sdf.format(calendar.getTime());
        
        //Get current dir
        String dir = CKANAPI.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        dir = dir.split("CKANAPI.class")[0];
        System.out.println("temp dir is:\r\n"+dir);
        /*
        File tmpFile = new File(dir+fileName);
		System.out.println("file created is:\r\n"+dir+fileName);
         */
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),"WCCTempFile.tmp");
        OutputStream outputStream = new FileOutputStream(tmpFile);
        System.out.println("Looks like tmp is ok...");
		int read = 0;
		byte[] bytes = new byte[1024];
	
		while ((read = fileStream.read(bytes)) != -1) {
			outputStream.write(bytes, 0, read);
		}
	
		String filename = orgFileName.replace(" ","-");
		//Generate sub url
		String url_path = ts+"/"+filename;
		System.out.println("uploaded file:\r\n");
		System.out.println(url_path);
		
		HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        //String mimeType = Magic.getMagicMatch((new File(dataPath)), false).getMimeType();
        //System.out.println(mimeType);
        String boundary = "----------ThIs_Is_tHe_bouNdaRY_$";
        
        post.addHeader("Content-Type","multipart/form-data; boundary="+boundary);
        post.addHeader("Authorization",api_key);
        post.addHeader("X-CKAN-API-KEY",api_key);
        
        MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE, boundary, null);
        entity.addPart("key",new StringBody(url_path));
        entity.addPart("file",new FileBody(tmpFile));
        //entity.addPart("file",new StringBody(content));
        
        post.setEntity(entity);
        
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int)entity.getContentLength());
        entity.writeTo(out);
        byte[] entityContentAsBytes = out.toByteArray();
        // or convert to string
        String entityContentAsString = new String(out.toByteArray());
        
        HttpResponse response = client.execute(post);
        String storagePrefix = ServerInfo.getInstance().getCkanURL()+"/storage/f/";
	    String resourcePath = storagePrefix+url_path;
	    System.out.println("The path for the resource is"+resourcePath);
        System.out.println(response.toString());
        tmpFile.delete();
        
        return resourcePath;
       		
	}
	public String upload_rawdata(String dataPath) throws IOException{
       		System.out.println("data path:\r\n"+dataPath); 
		String url = ServerInfo.getInstance().getCkanURL()+"/storage/upload_handle";
		String api_key = "96c00a49-9604-42ca-84b1-e43674f6c0f8";
		//Generate a date timestamp
		long currentTime = System.currentTimeMillis();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-hhmmss",Locale.US);

        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("US/Central"));
        calendar.setTimeInMillis(currentTime);
        System.out.println("GregorianCalendar -"+sdf.format(calendar.getTime()));
        String ts = sdf.format(calendar.getTime());
        
		//Append the filename with the timestamp, replace ' ' with '-'
		String[] tokens = dataPath.split("/");
		
		String filename = tokens[tokens.length-1];
		filename = filename.replace(" ","-");
		//Generate sub url
		String url_path = ts+"/"+filename;
		System.out.println(url_path);
		
		HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        //String mimeType = Magic.getMagicMatch((new File(dataPath)), false).getMimeType();
        //System.out.println(mimeType);
        String boundary = "----------ThIs_Is_tHe_bouNdaRY_$";
        
        post.addHeader("Content-Type","multipart/form-data; boundary="+boundary);
        post.addHeader("Authorization",api_key);
        post.addHeader("X-CKAN-API-KEY",api_key);
        
        MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE, boundary, null);
        entity.addPart("key",new StringBody(url_path));
        
        entity.addPart("file",new FileBody(new File(dataPath)));
        //entity.addPart("file",new StringBody(content));
        
        post.setEntity(entity);
        
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int)entity.getContentLength());
        entity.writeTo(out);
        byte[] entityContentAsBytes = out.toByteArray();
        // or convert to string
        String entityContentAsString = new String(out.toByteArray());
       
        System.out.println("The head is\r\n");
        for(org.apache.http.Header h: post.getAllHeaders()){
        	System.out.println("Name:"+h.getName()+" Value: "+h.getValue());
        }
        
        System.out.println("Request being sent is \r\n"+entityContentAsString);
        
        HttpResponse response = client.execute(post);
        String storagePrefix = ServerInfo.getInstance().getCkanURL()+"/storage/f/";
	    String resourcePath = storagePrefix+url_path;
	    System.out.println("The path for the resource is"+resourcePath);
        System.out.println(response.toString());
      
        return resourcePath;
       
	}
	
	public String upload_rawdata(String rawDataString,String fileName) throws IOException{
		
		String url = ServerInfo.getInstance().getCkanURL()+"/storage/upload_handle";
		String api_key = "96c00a49-9604-42ca-84b1-e43674f6c0f8";
		//Generate a date timestamp
		long currentTime = System.currentTimeMillis();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-hhmmss",Locale.US);

        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("US/Central"));
        calendar.setTimeInMillis(currentTime);
        System.out.println("GregorianCalendar -"+sdf.format(calendar.getTime()));
        String ts = sdf.format(calendar.getTime());
        
		//Append the filename with the timestamp, replace ' ' with '-'
        
		String filename = fileName;
		filename = filename.replace(" ","-");
		
		//Generate sub url
		String url_path = ts+"/"+filename;
		System.out.println(url_path);
		
		HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        //String mimeType = Magic.getMagicMatch((new File(dataPath)), false).getMimeType();
        //System.out.println(mimeType);
        String boundary = "----------ThIs_Is_tHe_bouNdaRY_$";
        
        post.addHeader("Content-Type","multipart/form-data; boundary="+boundary);
        post.addHeader("Authorization",api_key);
        post.addHeader("X-CKAN-API-KEY",api_key);
        
        MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE, boundary, null);
        entity.addPart("key",new StringBody(url_path));
        entity.addPart("file",new StringBody(rawDataString));
        
        post.setEntity(entity);
        
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int)entity.getContentLength());
        entity.writeTo(out);
        byte[] entityContentAsBytes = out.toByteArray();
        // or convert to string
        String entityContentAsString = new String(out.toByteArray());
       
        System.out.println("The head is\r\n");
        for(org.apache.http.Header h: post.getAllHeaders()){
        	System.out.println("Name:"+h.getName()+" Value: "+h.getValue());
        }
        
        System.out.println("Request being sent is \r\n"+entityContentAsString);
        
        HttpResponse response = client.execute(post);
        String storagePrefix = ServerInfo.getInstance().getCkanURL()+"/storage/f/";
	    String resourcePath = storagePrefix+url_path;
	    System.out.println("The path for the resource is"+resourcePath);
        System.out.println(response.toString());
       
        return resourcePath;
	}


	//Send metadata to CKAN and deposit
	public int postMetaDataToCKAN(){
		
		return 0;
	}
	
	//Send raw dataset to CKAN and deposit
	public int postRawDataToCKAN(){
		
		return 0;
	}
	
	//Get metadata from CKAN
	public int getMetaDataFromCKAN(){
		
		return 0;
	}
}
