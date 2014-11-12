package edu.cornell.mannlib.vitro.webapp.controller.freemarker;
import java.security.SecureRandom;
import java.io.IOException;

import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.auth.requestedAction.Actions;
import edu.cornell.mannlib.vitro.webapp.beans.Individual;
import edu.cornell.mannlib.vitro.webapp.controller.VitroHttpServlet;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.TemplateProcessingHelper.TemplateProcessingException;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.DirectRedirectResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.RedirectResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.ResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.TemplateResponseValues;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditSubmissionUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.MultiValueEditSubmission;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.N3EditUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.ProcessRdfForm;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.RedirectResponseValues;
import edu.cornell.mannlib.vitro.webapp.dao.ModelAccess;
import edu.cornell.mannlib.vitro.webapp.dao.NewURIMakerVitro;
import edu.cornell.mannlib.vitro.webapp.dao.jena.event.EditEvent;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.AdditionsAndRetractions;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.ModelSelector;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.controller.PostEditCleanupController;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.processEdit.EditN3Utils;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFService;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFServiceException;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import edu.rpi.twc.dcods.vivo.CKANAPI;
import edu.rpi.twc.dcods.vivo.CKANException;
import edu.rpi.twc.dcods.vivo.Client;
import edu.rpi.twc.dcods.vivo.Connection;
import edu.rpi.twc.dcods.vivo.DCOId;
import edu.rpi.twc.dcods.vivo.Dataset;
import edu.rpi.twc.dcods.vivo.Extra;
import edu.rpi.twc.dcods.vivo.ServerInfo;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.shared.Lock;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class DCOAddDatasetController extends FreemarkerHttpServlet{
    
    private static final long serialVersionUID = 1L;
    private String redirectSubjectUrl = "";
    private String dcoNamespace = ServerInfo.getInstance().getDcoNamespace();
    private String dcoOntNamespace = ServerInfo.getInstance().getDcoOntoNamespace();
    private String absoluteMachineURL = ServerInfo.getInstance().getAbsoluteMachineURL();
    private String machineURL = ServerInfo.getInstance().getMachineURL();
    private String ckanURL = ServerInfo.getInstance().getCkanURL();
    private boolean debug = true;
    
    /** Limit file size to 6 megabytes. */
	public static final int MAXIMUM_FILE_SIZE = 6 * 1024 * 1024;
    
    @Override
	protected Actions requiredActions(VitroRequest vreq) {
    	
		return SimplePermission.DO_FRONT_END_EDITING.ACTIONS;
	}
    
    protected String processPost(HttpServletRequest req) throws ServletException, IOException{
    	
    	/*
		System.out.println("All attributes:");
    	Enumeration allAttri = req.getAttributeNames();
		while(allAttri.hasMoreElements()){
			String attrName = allAttri.nextElement().toString();
			System.out.println(attrName+":"+req.getAttribute(attrName));
		}
		
		System.out.println("All parameters:");
    	Enumeration allParam = req.getParameterNames();
		while(allParam.hasMoreElements()){
			String paraName = allParam.nextElement().toString();
			System.out.println(paraName+":"+req.getParameter(paraName));
		}		
		*/
    	
    	boolean isMultipart = ServletFileUpload.isMultipartContent(req);
    	DatasetDistributionFileUploadRequest depositRequest = null;
    	
		if (isMultipart) {
			depositRequest = new DatasetDistributionFileUploadRequest(req,MAXIMUM_FILE_SIZE);
		}
		
		VitroRequest vreq = depositRequest.getVreq();
		EditConfigurationVTwo configuration = depositRequest.getConfig(); 
			
    	//EditConfigurationVTwo configuration = EditConfigurationUtils.getEditConfiguration(req);
		
    	MultiValueEditSubmission submission = null;
		if(configuration!=null){
			System.out.println("Configuration is not null while entering the function");
			submission = new MultiValueEditSubmission(vreq.getParameterMap(), configuration);
		}
		else
			System.out.println("configuration is null again...");
		
		submission.setNewIndividualURI(null);
		
		Model queryModel = configuration.getQueryModelSelector().getModel(vreq,this.getServletContext());		
	    Model writeModel = configuration.getWriteModelSelector().getModel(vreq,this.getServletContext());
	    
	    String dcoidN3Optional = "";
	    //String key = "newIndividual";
	    String key = "<"+configuration.getSubjectUri()+">";
	    
	    if(debug){
	    	System.out.println("Ready to create CKAN repository");
	    	System.out.println("Uploading files...");
	    }
		// Create data repo in CKAN according to distribution name
		// And get the repo URL
	    String distributionName = depositRequest.getDistributionName();
		String repoURL = depositRequest.getAccessAddr();
		
		// Get parameter of interests
	    String datasetName = depositRequest.getDatasetName();
	    /**
	     * TODO:Should have multiple names here for different files
	     */
	    ArrayList<String> filenames = depositRequest.getFileNames();
	    ArrayList<String> actualDownloadURLs = depositRequest.getUploadAddresses();

	    if(debug){
	    	System.out.println("Upload done!");
	    	System.out.println("Creating triples");
	    	System.out.println("Show download URLs");

	    	for(int i=0;i<actualDownloadURLs.size();i++){
	    		System.out.println(actualDownloadURLs.get(i));
	    	}
	    }
	    
		// Prepare queries
		String insertTemplate = "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX vivo:<http://vivoweb.org/ontology/core#>\r\n"
				+ "INSERT DATA {\r\n"
				+ "GRAPH <http://vitro.mannlib.cornell.edu/default/vitro-kb-2>\r\n"
				+ "{\r\n"
				+ "QUERY-HERE"
				+ "}\r\n"
				+ "}";
		String USERURI = configuration.getSubjectUri();
		
		String AUTHORSHIPURI = nextSessionId();
		DCOId dcoid = new DCOId();		
		dcoid.operate(AUTHORSHIPURI, "URL", "create");
		String AUTHORSHIPDCOID = dcoid.getDCOId();
		String AUTHORSHIPDCOIDPURE = dcoid.getDCOId().substring(25);
		
		String DATASETURI = nextSessionId();
		String DATASETNAME = datasetName;
		dcoid = new DCOId();
		dcoid.operate(DATASETURI, "URL", "create");
		String DATASETDCOID = dcoid.getDCOId();
		String DATASETDCOIDPURE = dcoid.getDCOId().substring(25);
		
		String DISTRIBUTIONURI = nextSessionId();
		String DISTRIBUTIONNAME = distributionName;
		dcoid = new DCOId();
		dcoid.operate(DISTRIBUTIONURI, "URL", "create");
		String DISTRIBUTIONDCOID = dcoid.getDCOId();
		String DISTRIBUTIONDCOIDPURE = dcoid.getDCOId().substring(25);
		
		String ACCESSURL = repoURL;
		ArrayList<String> FILENAMES = filenames;
		ArrayList<String> DOWNLOADURLS = actualDownloadURLs;
		ArrayList<String> FILEURIS = new ArrayList<String>();
		ArrayList<String> FILEDCOIDS = new ArrayList<String>();
		ArrayList<String> FILEDCOIDSPURE = new ArrayList<String>();
		
		for(int i = 0; i<filenames.size(); i++){
			String FILEURI = nextSessionId();
			dcoid = new DCOId();
			dcoid.operate(FILEURI, "URL", "create");
			FILEURIS.add(FILEURI);
			String FILEDCOID = dcoid.getDCOId();
			FILEDCOIDS.add(FILEDCOID);
			FILEDCOIDSPURE.add(FILEDCOID.substring(25));
		}
		
		String authorshipTemplate = "<AUTHORSHIPURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/BFO_0000002>. \r\n"
				+ "<AUTHORSHIPURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://vivoweb.org/ontology/core#Authorship>. \r\n"
				+ "<AUTHORSHIPURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Thing>. \r\n"
				+ "<AUTHORSHIPURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/BFO_0000001>. \r\n"
				+ "<AUTHORSHIPURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/BFO_0000020>. \r\n"
				+ "<AUTHORSHIPURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://vivoweb.org/ontology/core#Relationship>. \r\n"
				+ "<AUTHORSHIPURI> <http://vivoweb.org/ontology/core#relates> <DATASETURI>. \r\n"
				+ "<AUTHORSHIPURI> <http://vitro.mannlib.cornell.edu/ns/vitro/0.7#mostSpecificType> <http://vivoweb.org/ontology/core#Authorship>. \r\n"
				+ "<AUTHORSHIPURI> <http://vivoweb.org/ontology/core#rank> \"1\"^^<http://www.w3.org/2001/XMLSchema#int>. \r\n"
				+ "<AUTHORSHIPURI> <http://info.deepcarbon.net/schema#hasDcoId> <AUTHORSHIPDCOID>. \r\n"
				+ "<AUTHORSHIPDCOID> a <http://info.deepcarbon.net/schema#DCOID> . \r\n"
				+ "<AUTHORSHIPDCOID> <http://www.w3.org/2000/01/rdf-schema#label> \"AUTHORSHIPDCOIDPURE\" . \r\n"
				+ "<AUTHORSHIPDCOID> <http://info.deepcarbon.net/schema#dcoIdFor> <AUTHORSHIPURI>. \r\n"
				+ "<USERURI> vivo:relatedBy <AUTHORSHIPURI>. \r\n";
		
		
		String datasetTemplate = "<DATASETURI> <http://info.deepcarbon.net/schema#hasDistribution> <DISTRIBUTIONURI>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/BFO_0000002>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://vivoweb.org/ontology/core#Dataset>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/BFO_0000031>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Thing>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://vivoweb.org/ontology/core#InformationResource>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/BFO_0000001>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.obolibrary.org/obo/IAO_0000030>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://purl.org/ontology/bibo/Document>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://info.deepcarbon.net/schema#Object>. \r\n"
				+ "<DATASETURI> <http://vitro.mannlib.cornell.edu/ns/vitro/0.7#mostSpecificType> <http://vivoweb.org/ontology/core#Dataset>. \r\n"
				+ "<DATASETURI> <http://www.w3.org/2000/01/rdf-schema#label> \"DATASETNAME\"^^<http://www.w3.org/2001/XMLSchema#string>. \r\n"
				+ "<DATASETURI> <http://info.deepcarbon.net/schema#hasDcoId> <DATASETDCOID>. \r\n"
				+ "<DATASETDCOID> a <http://info.deepcarbon.net/schema#DCOID> . \r\n"
				+ "<DATASETDCOID> <http://www.w3.org/2000/01/rdf-schema#label> \"DATASETDCOIDPURE\" . \r\n"
				+ "<DATASETDCOID> <http://info.deepcarbon.net/schema#dcoIdFor> <DATASETURI>. \r\n"
				+ "<DATASETURI> <http://vivoweb.org/ontology/core#relatedBy> <AUTHORSHIPURI>. \r\n";
		
		String distributionTemplate = "<DISTRIBUTIONURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Thing>. \r\n"
				+ "<DISTRIBUTIONURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://info.deepcarbon.net/schema#Object>. \r\n"
				+ "<DISTRIBUTIONURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/ns/dcat#Distribution>. \r\n"
				+ "<DISTRIBUTIONURI> <http://info.deepcarbon.net/schema#accessURL> \"ACCESSURL\". \r\n"
				+ "<DISTRIBUTIONURI> <http://info.deepcarbon.net/schema#distributionFor> <DATASETURI>. \r\n"
				+ "<DISTRIBUTIONURI> <http://www.w3.org/2000/01/rdf-schema#label> \"DISTRIBUTIONNAME\". \r\n"
				+ "<DISTRIBUTIONURI> <http://info.deepcarbon.net/schema#hasDcoId> <DISTRIBUTIONDCOID>. \r\n"
				+ "<DISTRIBUTIONDCOID> a <http://info.deepcarbon.net/schema#DCOID> . \r\n"
				+ "<DISTRIBUTIONDCOID> <http://www.w3.org/2000/01/rdf-schema#label> \"DISTRIBUTIONDCOIDPURE\" . \r\n"
				+ "<DISTRIBUTIONDCOID> <http://info.deepcarbon.net/schema#dcoIdFor> <DISTRIBUTIONURI>. \r\n";

		String distHasFileTemplate = "<DISTRIBUTIONURI> <http://info.deepcarbon.net/schema#hasFile> <FILEURI>. \r\n";
		String distHasFileTriples = "";
		for(int i = 0; i < FILEURIS.size(); i++){
			distHasFileTriples = distHasFileTriples + distHasFileTemplate.replace("FILEURI", FILEURIS.get(i));
		}
		
		String fileTemplate = "<FILEURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Thing>. \r\n"
				+ "<FILEURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://info.deepcarbon.net/schema#File>. \r\n"
				+ "<FILEURI> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://info.deepcarbon.net/schema#Object>. \r\n"
				+ "<FILEURI> <http://vitro.mannlib.cornell.edu/ns/vitro/0.7#mostSpecificType> <http://info.deepcarbon.net/schema#File>. \r\n"
				+ "<FILEURI> <http://info.deepcarbon.net/schema#downloadURL> \"DOWNLOADURL\". \r\n"
				+ "<FILEURI> <http://info.deepcarbon.net/schema#fileFor> <DISTRIBUTIONURI>. \r\n"
				+ "<FILEURI> <http://www.w3.org/2000/01/rdf-schema#label> \"FILENAME\". \r\n"
				+ "<FILEURI> <http://info.deepcarbon.net/schema#hasDcoId> <FILEDCOID>. \r\n"
				+ "<FILEDCOID> a <http://info.deepcarbon.net/schema#DCOID> . \r\n"
				+ "<FILEDCOID> <http://www.w3.org/2000/01/rdf-schema#label> \"FILEDCOIDPURE\" . \r\n"
				+ "<FILEDCOID> <http://info.deepcarbon.net/schema#dcoIdFor> <FILEURI>. \r\n";
		
		/**
		 * TODO: Should adapt to multiple files uploading;different files have different URIs, names, DCOIDs and download urls
		 */
		String allInsertStatement = authorshipTemplate + datasetTemplate + distributionTemplate + distHasFileTriples;
		allInsertStatement = allInsertStatement.replace("AUTHORSHIPURI", AUTHORSHIPURI).replace("DATASETURI",DATASETURI).replace("DATASETNAME", DATASETNAME).replace("DATASETDCOIDPURE",DATASETDCOIDPURE).replace("AUTHORSHIPDCOIDPURE",AUTHORSHIPDCOIDPURE)
		.replace("USERURI", USERURI).replace("DISTRIBUTIONURI", DISTRIBUTIONURI).replace("DISTRIBUTIONDCOIDPURE", DISTRIBUTIONDCOIDPURE).replace("DISTRIBUTIONNAME", DISTRIBUTIONNAME).replace("ACCESSURL", ACCESSURL).replace("AUTHORSHIPDCOID",AUTHORSHIPDCOID)
		.replace("DATASETDCOID",DATASETDCOID).replace("DISTRIBUTIONDCOID", DISTRIBUTIONDCOID);
		
		String filesTriple = "";
		String singleFileTriple = "";
		if(debug){
			System.out.println(FILENAMES.size()+" files are detected");
			for(String name : FILENAMES){
				System.out.println(name);
			}
		}
		
		for(int i = 0; i<FILENAMES.size(); i++){
			singleFileTriple = fileTemplate.replace("FILEURI", FILEURIS.get(i)).replace("DOWNLOADURL", DOWNLOADURLS.get(i)).replace("FILENAME", FILENAMES.get(i)).replace("FILEDCOIDPURE", FILEDCOIDSPURE.get(i))
					.replace("DISTRIBUTIONURI", DISTRIBUTIONURI).replace("FILEDCOID", FILEDCOIDS.get(i));
			filesTriple = filesTriple + singleFileTriple;
		}
		allInsertStatement = allInsertStatement + filesTriple;
		
		insertTemplate = insertTemplate.replace("QUERY-HERE", allInsertStatement);
		if(debug){
			System.out.println("Creating triples done!");
			System.out.println("Calling VIVO API to commit the triples...");
		}
		// Push queries using VIVO API
		int statusCode = 0;
		try {
			statusCode = sendPost(ServerInfo.getInstance().getRootPassword(),ServerInfo.getInstance().getRootName(),insertTemplate);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(debug){
			System.out.println("Response code:"+statusCode);		
			System.out.println("Commiting triples done!");
		}
		
		// Return to the page
		configuration.setUrlToReturnTo(configuration.getUrlToReturnTo().replace("http://localhost:8080", absoluteMachineURL));
		if(debug){
			System.out.println("Going back to the individual page:");
			System.out.println(configuration.getUrlToReturnTo().toString());
		}
		return configuration.getUrlToReturnTo();
		
    }
	
	public String nextSessionId() {
		
		String subjectURI = "";
		
		do{
			String prefix = ServerInfo.getInstance().getDcoNamespace();
			SecureRandom random = new SecureRandom();
		    String uuid = new BigInteger(130, random).toString(32);
		    //Make sure there is no such a triple
		    subjectURI = prefix+"/individual/"+uuid;
		}while(sparqlForAccessURL(subjectURI).length()>=10);
		
	    return subjectURI;
	}

	private int sendPost(String password, String email, String data) throws Exception {
		 
		String url = "http://128.213.3.13:8080/vivo/api/sparqlUpdate";
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		//add reuqest header
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", "Mozilla/5.0");
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
 
		String payload = "password="+password+"&email="+email+"&update="+data;
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
		return responseCode;
	}
	
	public int vivoapiInsert(String password, String email, String data){
		
		String endpoint = "http://128.213.3.13:8080/vivo/api/sparqlUpdate?";
		String payload = "password="+password+"&email="+email+"&update="+data;
		try {
			payload = URIUtil.encodeQuery(payload);
		} catch (URIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String requestURL = endpoint + payload;
		
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(requestURL);
				
		// add request header
		request.addHeader("User-Agent", "Mozilla/5.0");
		HttpResponse response = null;
		StringBuffer result = new StringBuffer();
		try {
			response = client.execute(request);
			
			BufferedReader rd = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));
		 
			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response.getStatusLine().getStatusCode();
	}
	public String sparqlForAccessURL(String subjectUrl){
		
		String endpoint = "http://128.213.3.13:8080/vivo/admin/sparqlquery?query=";
		String queryInString = 
		        "PREFIX dco: <"+ServerInfo.getInstance().getDcoOntoNamespace()+">  "+
		        "select ?p"+
		        "where { "+
		         "<"+subjectUrl+"> ?p ?o .  "+
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
			accessURL = result.toString().split("p")[1];
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return accessURL;
	}

   	
    @Override
	public ResponseValues processRequest(VitroRequest vreq) {
    	
    	String entityToReturnTo = absoluteMachineURL;
    	
		try {
			entityToReturnTo = this.processPost(vreq);	
			//System.out.println("The return url is "+entityToReturnTo);
		} catch (ServletException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
		return new RedirectResponseValues(entityToReturnTo);
		    	
    }
    
    private static class ResettableStreamHttpServletRequest extends HttpServletRequestWrapper {

		private byte[] rawData;
		private HttpServletRequest request;
		private ResettableServletInputStream servletStream;
		
		public ResettableStreamHttpServletRequest(HttpServletRequest request) {
			super(request);
			this.request = request;
			this.servletStream = new ResettableServletInputStream();
		}
		
		
		public void resetInputStream() {
			servletStream.stream = new ByteArrayInputStream(rawData);
		}
		
		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (rawData == null) {
				rawData = IOUtils.toByteArray(this.request.getReader());
				servletStream.stream = new ByteArrayInputStream(rawData);
			}
			return servletStream;
		}
		
		@Override
		public BufferedReader getReader() throws IOException {
			if (rawData == null) {
				rawData = IOUtils.toByteArray(this.request.getReader());
				servletStream.stream = new ByteArrayInputStream(rawData);
			}
			return new BufferedReader(new InputStreamReader(servletStream));
		}
		
		
		private class ResettableServletInputStream extends ServletInputStream {
		
			private InputStream stream;
		
			@Override
			public int read() throws IOException {
				return stream.read();
			}
		}
	}
    
   
}
