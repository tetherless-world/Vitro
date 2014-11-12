package edu.cornell.mannlib.vitro.webapp.controller.freemarker;


/* $This file is distributed under the terms of the license in /doc/license.txt$ */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import edu.rpi.twc.dcods.vivo.Client;
import edu.rpi.twc.dcods.vivo.Connection;
import edu.rpi.twc.dcods.vivo.ServerInfo;

/**
 * A wrapper for a servlet request that holds multipart content. Parsing the
 * request will consume the parameters, so we need to hold them here to answer
 * any parameter-related requests. File-related information will also be held
 * here, to answer file-related requests.
 */
class CkanMultipartHttpServletRequest extends FileUploadServletRequest {

	private static final String[] EMPTY_ARRAY = new String[0];

	private Map<String, List<String>> parameters;
	private Map<String, List<FileItem>> files;
	private String uploadAddr;
	private String accessAddr;
	private String fileName;
	private final VitroRequest vreq;
	private final EditConfigurationVTwo configuration;
	private FileUploadException fileUploadException;
	private String ckanURL = ServerInfo.getInstance().getCkanURL();
	
	/**
	 * Parse the multipart request. Store the info about the request parameters
	 * and the uploaded files.
	 */
	public CkanMultipartHttpServletRequest(HttpServletRequest request,
			int maxFileSize) throws IOException {
		super(request);		
		Map<String, List<String>> parameters = new HashMap<String, List<String>>();
		Map<String, List<FileItem>> files = new HashMap<String, List<FileItem>>();
		CKANAPI myCkanApi = new CKANAPI();
		String webFileName = "";
		String remoteUrl = "";
		
		/*
		System.out.println("CKAN multipart request here");
		//Try getting all fields
		Enumeration enumAttr = request.getAttributeNames();
		while(enumAttr.hasMoreElements()){
			String attrName = (String)enumAttr.nextElement();
			System.out.println(attrName+":"+request.getAttribute(attrName));
		}
		
		Enumeration enumParam = request.getParameterNames();
		while(enumParam.hasMoreElements()){
			String paramName = (String)enumParam.nextElement();
			System.out.println(paramName+":"+request.getParameter(paramName));
		}
		*/
		
	
		//File tempDir = figureTemporaryDirectory(request);
		//ServletFileUpload upload = createUploadHandler(maxFileSize, tempDir);
		
		
		/*
		try {
			List<FileItem> items = parseRequestIntoFileItems(request, upload);
			
			for (FileItem item : items) {
				// Process a regular form field
				if (item.isFormField()) {
					addToParameters(parameters, item.getFieldName(), item
							.getString("UTF-8"));
					System.out.println("Form field (parameter) " + item.getFieldName()
							+ "=" + item.getString());
					if(item.getFieldName().equals("name")){
						webFileName = item.getString();
						fileName = item.getString();
					}
					if(item.getFieldName().equals("remote_url")){
						remoteUrl = item.getString();
						if(remoteUrl.length()>0){
							//If user entered remote url, then escape now not without uploading files
							uploadAddr = remoteUrl;
							break;
						}
					}
				} else {
					if(remoteUrl.length()==0){
						//Only upload the file if the input field is empty
						
						//Deposit to CKAN and get back the download url
						uploadAddr = myCkanApi.upload_rawdata(item.getInputStream(),item.getName());
						System.out.println("The uploaded file address is "+uploadAddr);
						System.out.println("File " + item.getFieldName() + ": "
								+ item.getName());
					}
				}
			}
		} catch (FileUploadException e) {
			fileUploadException = e;
			request.setAttribute(
					FileUploadServletRequest.FILE_UPLOAD_EXCEPTION, e);
		}
		*/
		
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
		String querySubject = configuration.getSubjectUri();
		String accessURL = this.sparqlForAccessURL(querySubject);
		remoteUrl = request.getParameter("remote_url");
		if(remoteUrl.length()==0){
			//Deposit to CKAN and get back the download url
			webFileName = this.files.get("file").get(0).getName();
			this.fileName = request.getParameter("file-name");
			System.out.println("File name:\r\n"+webFileName);
			uploadAddr = myCkanApi.upload_rawdata(this.files.get("file").get(0).getInputStream(),webFileName);
			System.out.println("The uploaded file address is "+uploadAddr);
		}else{
			//Not uploading the file; just create a triple, push to the triple store, and store the link here
			this.fileName = request.getParameter("file-name");
			uploadAddr = remoteUrl;
		}
		
		//Only tries to associate the downloadURL with accessURL if it is to the CKAN repo
		//Otherwise, do nothing
		if(accessURL.contains(ckanURL)){
			if(webFileName.length()>0 )
				myCkanApi.associateRepoWithDataset(accessURL, uploadAddr, fileName);		
			else
				myCkanApi.associateRepoWithDataset(accessURL, uploadAddr, "default-dataset-name");
		}
		
	}
	
	public String sparqlForAccessURL(String subjectUrl){
		
		String endpoint = "https://info.deepcarbon.net/vivo/admin/sparqlquery?query=";
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

	public String getFileName(){
		if(fileName!=null){
		    if(fileName.length()>0)
		        return this.fileName;
		    else
			return "default-file-name";
		}else
		    return "default-file-name";
	}
	
	public String getAccessAddr(){
		return this.accessAddr;
	}
	public String getUploadAddr(){
		return this.uploadAddr;
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
