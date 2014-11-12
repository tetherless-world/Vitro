package edu.cornell.mannlib.vitro.webapp.controller.freemarker;


/* $This file is distributed under the terms of the license in /doc/license.txt$ */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.shared.Lock;

import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.dao.ModelAccess;
import edu.cornell.mannlib.vitro.webapp.dao.NewURIMakerVitro;
import edu.cornell.mannlib.vitro.webapp.dao.jena.event.EditEvent;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.AdditionsAndRetractions;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.N3EditUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.ProcessRdfForm;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.processEdit.EditN3Utils;
import edu.cornell.mannlib.vitro.webapp.filestorage.uploadrequest.FileUploadServletRequest;
import edu.rpi.twc.dcods.vivo.CKANAPI;
import edu.rpi.twc.dcods.vivo.CKANException;
import edu.rpi.twc.dcods.vivo.Client;
import edu.rpi.twc.dcods.vivo.Connection;
import edu.rpi.twc.dcods.vivo.Dataset;
import edu.rpi.twc.dcods.vivo.Extra;
import edu.rpi.twc.dcods.vivo.ServerInfo;

/**
 * A wrapper for a servlet request that holds multipart content. Parsing the
 * request will consume the parameters, so we need to hold them here to answer
 * any parameter-related requests. File-related information will also be held
 * here, to answer file-related requests.
 * 
 * @cheny18 May 22nd, 2014
 * The class is responsible for creating dataset, distribution and affiliated files all in one page. 
 */
class DatasetDistributionFileUploadRequest extends FileUploadServletRequest {

	private static final String[] EMPTY_ARRAY = new String[0];

	private Map<String, List<String>> parameters;
	private Map<String, List<FileItem>> files;
	private String datasetName;
	private String distributionName;
	private ArrayList<String> fileNames;
	private ArrayList<String> uploadFileAddresses;
	private String accessAddr;
	private String uploadAddr;
	private final VitroRequest vreq;
	private final EditConfigurationVTwo configuration;
	private FileUploadException fileUploadException;
	private String ckanURL = ServerInfo.getInstance().getCkanURL();
	
	/**
	 * Parse the multipart request. Store the info about the request parameters
	 * and the uploaded files.
	 */
	public DatasetDistributionFileUploadRequest(HttpServletRequest request,
			int maxFileSize) throws IOException {
		super(request);		
		Map<String, List<String>> parameters = new HashMap<String, List<String>>();
		Map<String, List<FileItem>> files = new HashMap<String, List<FileItem>>();
		CKANAPI myCkanApi = new CKANAPI();
		String webFileName = "";
		String remoteUrl = "";
		
		//this.parameters = Collections.unmodifiableMap(parameters);
		
		this.parameters = request.getParameterMap();
		
		this.files = (Map<String, List<FileItem>>) request.getAttribute("MultipartRequestWrapper_fileItemMap");
		System.out.println("Parameters are: " + this.parameters);
		System.out.println("Files are: " + this.files);
		request.setAttribute(FILE_ITEM_MAP, this.files);
		this.vreq = (VitroRequest)request;
		
		// Write things to the model
		String editKey = request.getParameter("editKey");
		
		EditConfigurationVTwo configuration = EditConfigurationUtils.getEditConfiguration(request,editKey);
		System.out.println("Hopefully the configuration is "+configuration);
		this.configuration = configuration;

		//Now try to send a sparql request to the endpoint to get back the access url for the dataset, for a single individual
		//Get the subjectUri
		remoteUrl = request.getParameter("remote_url");
		
		fileNames = new ArrayList<String>();
		uploadFileAddresses = new ArrayList<String>();
		
		if(remoteUrl.length()==0){
			//Deposit to CKAN and get back the download url
			/**
			 * TODO:Here is the code to change to enable multiple files uploading;Basically, just a loop to go through each file
			 */
			System.out.println(this.files.get("file").size()+" files are detected!!!"); 
			List<FileItem> uploadedFiles = this.files.get("file");
			for(int i = 0; i<uploadedFiles.size(); i++){
				fileNames.add(uploadedFiles.get(i).getName());
				uploadAddr = myCkanApi.upload_rawdata(uploadedFiles.get(i).getInputStream(),uploadedFiles.get(i).getName());
				uploadFileAddresses.add(uploadAddr);
			}
			
		}else{
			//Not uploading the file; just create a triple, push to the triple store, and store the link here
			uploadFileAddresses.add(remoteUrl);
			fileNames.add(remoteUrl.substring(remoteUrl.lastIndexOf("/")+1));
			uploadAddr = remoteUrl;
		}
		this.datasetName = request.getParameter("dataset-name");
		/**
		 * Distribution name will be a time stamp 
		 */
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
		//get current date time with Date()
		Date date = new Date();
		this.distributionName = dateFormat.format(date);
		   
		accessAddr = createCkanRepo(distributionName,ckanURL);

		//Only tries to associate the downloadURL with accessURL if it is to the CKAN repo
		//Otherwise, do nothing
		if(accessAddr.contains(ckanURL)){
			if(webFileName.length()>1 ){
				System.out.println("For the raw files...");
				for(int i = 0; i<fileNames.size(); i++){
					myCkanApi.associateRepoWithDataset(accessAddr, uploadFileAddresses.get(i), fileNames.get(i));		
				}
			}
			else{
				System.out.println("For the link files...");
				System.out.println("!!!\r\n"+uploadAddr+" at "+uploadAddr.lastIndexOf('/'));
				myCkanApi.associateRepoWithDataset(accessAddr, uploadAddr, uploadAddr.substring(uploadAddr.lastIndexOf('/')+1));
			}
		}
		
	}
	
	public String createCkanRepo(String repoName,String ckanURL){
	   	//System.out.println("The name for the object is "+(submission.getLiteralsFromForm().get("label").toString().split("\\[")[1].split("\\^\\^")[0]));
			String dataRepoName = repoName;
			
			String apiKey = "89c2d25c-4ea3-44b4-9fc5-a1f7367fee92";
			//This is a distribution instance, create a corresponded ckan instance with the name of the distribution
			System.out.println("Just check ckanURL is:\r\n"+ckanURL);
			System.out.println("Try with instance");
			ServerInfo.getInstance();
			System.out.println("Try with attribute");
			ServerInfo.getInstance().getCkanURL();
			Client c = new Client( new Connection(ckanURL, apiKey,true),apiKey);
	        
	        Dataset ds = new Dataset();
	        ds.setName( UUID.randomUUID().toString() );
	        ds.setTitle(dataRepoName);
	        ds.setNotes("About "+dataRepoName);
	        
	        List<Extra> extras = new ArrayList<Extra>();
	        extras.add( new Extra("Extra Field", "\"Extra Value\"") );
	        ds.setExtras(extras);
	        
	        String repoURL = "";
	        try {
	        	
				Dataset result = c.createDataset(ds);
				System.out.println("Here is everything "+ckanURL+"/dataset/"+result.getName());
				repoURL = ckanURL+"/dataset/"+result.getName();
				
			} catch (CKANException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	   		return repoURL;
	   	}
	
	public String sparqlForAccessURL(String subjectUrl){
		
		String endpoint = "http://info.deepcarbon.net/vivo/admin/sparqlquery?query=";
		String individualName = subjectUrl;
		String queryInString = 
		        "PREFIX dco: <"+ServerInfo.getInstance().getDcoOntoNamespace()+">  "+
		        "select ?accessAddr "+
		        "where { "+
		         "<"+individualName+"> dco:accessURL ?accessAddr .  "+
		        "} \n ";
		String encodedQuery = "";
		try {
			encodedQuery = URIUtil.encodeQuery(queryInString);
		} catch (URIException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		String queryParams = "&resultFormat=vitro%3Acsv&rdfResultFormat=RDF%2FXML";
		String url = endpoint+encodedQuery+queryParams;
		
		System.out.println("Request URL is\r\n"+url);
		
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		
		String accessURL = "";
		
		// add request header
		request.addHeader("User-Agent", "Mozilla/5.0");
		HttpResponse response;
		try {
			response = client.execute(request);
			
			BufferedReader rd = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));
		 
			StringBuffer result = new StringBuffer();
			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
			System.out.println("query result is:\r\n"+result.toString());
			accessURL = result.toString().split("accessAddr")[1];
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return accessURL;
	}

	public String getDatasetName(){
		return this.datasetName;
	}
	
	public String getDistributionName(){
		return this.distributionName;
	}
	
	public ArrayList<String> getFileNames(){
		if(this.fileNames.size()>0)
			return this.fileNames;
		else{
			ArrayList<String> singleAddr = new ArrayList<String>(Arrays.asList(uploadAddr.substring(uploadAddr.lastIndexOf("/")+1)));
			return singleAddr;
		}
	}
	
	public String getAccessAddr(){
		return this.accessAddr;
	}
	public ArrayList<String> getUploadAddresses(){
		return this.uploadFileAddresses;
	}
	public VitroRequest getVreq(){
		return this.vreq;
	}
	
	public EditConfigurationVTwo getConfig(){
		return this.configuration;
	}
	
	public String getEntityToReturnTo(){
		
		return this.parameters.get("subjectUri").get(0);
	}
	/**
	 * Pull any parameters out of the URL.
	 */
	private void parseQueryString(String queryString,
			Map<String, List<String>> parameters) {
		System.out.println("Query string is : '" + queryString + "'");
		if (queryString != null) {
			String[] pieces = queryString.split("&");

			for (String piece : pieces) {
				int equalsHere = piece.indexOf('=');
				if (piece.trim().isEmpty()) {
					// Ignore an empty piece.
				} else if (equalsHere <= 0) {
					// A parameter without a value.
					addToParameters(parameters, decode(piece), "");
				} else {
					// A parameter with a value.
					String key = piece.substring(0, equalsHere);
					String value = piece.substring(equalsHere + 1);
					addToParameters(parameters, decode(key), decode(value));
				}
			}
		}
		System.out.println("Parameters from query string are: " + parameters);
	}

	/**
	 * Remove any special URL-style encoding.
	 */
	private String decode(String encoded) {
		try {
			return URLDecoder.decode(encoded, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.out.println(e);
			return encoded;
		}
	}

	/**
	 * Find the temporary storage directory for this webapp.
	 */
	private File figureTemporaryDirectory(HttpServletRequest request) {
		return (File) request.getSession().getServletContext().getAttribute(
				"javax.servlet.context.tempdir");
	}

	/**
	 * Create an upload handler that will throw an exception if the file is too
	 * large.
	 */
	private ServletFileUpload createUploadHandler(int maxFileSize, File tempDir) {
		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD);
		factory.setRepository(tempDir);

		ServletFileUpload upload = new ServletFileUpload(factory);
		upload.setSizeMax(maxFileSize);

		return upload;
	}

	/** Either create a new List for the value, or add to an existing List. */
	private void addToParameters(Map<String, List<String>> map, String name,
			String value) {
		if (!map.containsKey(name)) {
			map.put(name, new ArrayList<String>());
		}
		map.get(name).add(value);
	}

	/** Either create a new List for the file, or add to an existing List. */
	private void addToFileItems(Map<String, List<FileItem>> map, FileItem file) {
		String name = file.getFieldName();
		if (!map.containsKey(name)) {
			map.put(name, new ArrayList<FileItem>());
		}
		map.get(name).add(file);
	}

	/** Minimize the code that uses the unchecked cast. */
	@SuppressWarnings("unchecked")
	private List<FileItem> parseRequestIntoFileItems(HttpServletRequest req,
			ServletFileUpload upload) throws FileUploadException {
		return upload.parseRequest(req);
	}

	// ----------------------------------------------------------------------
	// This is a multipart request, so make the file info available. If there
	// was an exception during parsing, make that available too.
	// ----------------------------------------------------------------------

	@Override
	public boolean isMultipart() {
		return true;
	}

	@Override
	public Map<String, List<FileItem>> getFiles() {
		return files;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * There may be more than one file item with the given name. If the first
	 * one is empty (size is zero), keep looking for a non-empty one.
	 * </p>
	 */
	@Override
	public FileItem getFileItem(String name) {
		List<FileItem> items = files.get(name);
		if (items == null) {
			return null;
		}

		for (FileItem item : items) {
			if (item.getSize() > 0L) {
				return item;
			}
		}

		return null;
	}

	@Override
	public FileUploadException getFileUploadException() {
		return fileUploadException;
	}

	@Override
	public boolean hasFileUploadException() {
		return fileUploadException != null;
	}

	// ----------------------------------------------------------------------
	// Parameter-related methods won't find anything on the delegate request,
	// since parsing consumed the parameters. So we need to look to the parsed
	// info for the answers.
	// ----------------------------------------------------------------------

	@Override
	public String getParameter(String name) {
		if (parameters.containsKey(name)) {
			return parameters.get(name).get(0);
		} else {
			return null;
		}
	}

	@Override
	public Enumeration<?> getParameterNames() {
		return Collections.enumeration(parameters.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		if (parameters.containsKey(name)) {
			return parameters.get(name).toArray(EMPTY_ARRAY);
		} else {
			return null;
		}
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		Map<String, String[]> result = new HashMap<String, String[]>();
		for (Entry<String, List<String>> entry : parameters.entrySet()) {
			result.put(entry.getKey(), entry.getValue().toArray(EMPTY_ARRAY));
		}
		System.out.println("resulting parameter map: " + result);
		return result;
	}

}
