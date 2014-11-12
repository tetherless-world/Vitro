package edu.cornell.mannlib.vitro.webapp.controller.freemarker;

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

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import edu.rpi.twc.dcods.vivo.CKANAPI;
import edu.rpi.twc.dcods.vivo.DCOId;
import edu.rpi.twc.dcods.vivo.ServerInfo;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.IOUtils;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.shared.Lock;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class AddDistributionDatasetController extends FreemarkerHttpServlet{
    
    private static final long serialVersionUID = 1L;
    private String redirectSubjectUrl = "";
    private String dcoNamespace = ServerInfo.getInstance().getDcoNamespace();
    private String dcoOntNamespace = ServerInfo.getInstance().getDcoOntoNamespace();
    private String absoluteMachineURL = ServerInfo.getInstance().getAbsoluteMachineURL();
    private String machineURL = ServerInfo.getInstance().getMachineURL();
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
    	CkanMultipartHttpServletRequest depositRequest = null;
    	
		if (isMultipart) {
			depositRequest = new CkanMultipartHttpServletRequest(req, MAXIMUM_FILE_SIZE);
		}
		
		System.out.println("CKAN object done");
		
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
	    
	    String actualDownloadURL = depositRequest.getUploadAddr();
	    
		dcoidN3Optional = key + " <"+dcoOntNamespace+"hasFile> ?newIndividual .";
		String instanceInverseStatement = "?newIndividual <"+dcoOntNamespace+"fileFor> "+key+" .";
		//Creating dco:File. Instead of the data property download url, we take the range of the downloadURL to be
		//a dco:File, therefore we need to create and object, with data properties of dcoid,label and url.
		DCOId dcoid = new DCOId();
		// Go generate the new dco id
		dcoid.generateDCOId();
		String newDcoId = dcoid.getDCOId();
		String instanceStatement = " ?newIndividual a <"+dcoOntNamespace+"File> .";
		String instanceDcoidStatment = " ?newIndividual <"+dcoOntNamespace+"dcoId> \""+newDcoId+"\" .";
		String instanceFileurlStatement = " ?newIndividual <"+dcoOntNamespace+"downloadURL> \""+actualDownloadURL+"\" .";
		String instanceLabelStatement = " ?newIndividual <http://www.w3.org/2000/01/rdf-schema#label> \""+depositRequest.getFileName()+"\" .";
	    
		configuration.addN3Optional(dcoidN3Optional);
		configuration.addN3Optional(instanceInverseStatement);
		configuration.addN3Optional(instanceStatement);
		configuration.addN3Optional(instanceDcoidStatment);
		configuration.addN3Optional(instanceFileurlStatement);
		configuration.addN3Optional(instanceLabelStatement);
		HashMap<String,String> newResources = new HashMap<String,String>();
		newResources.put("newIndividual", null);
		configuration.setNewResources(newResources);
		
	    ProcessRdfForm prf = 
				new ProcessRdfForm(configuration, new NewURIMakerVitro(vreq.getWebappDaoFactory()));    
        AdditionsAndRetractions changes;
        try {
			changes = prf.process(configuration, submission,vreq);
			//Modify the dcoid record to reflect the newly allocated resource url
			String individualURI = submission.getNewIndividualURI().get("newIndividual");
			//http://deepcarbon.net/vivo/individual/n6739
			String registeredURI = dcoNamespace + "/individual?uri=" + individualURI;
			dcoid.operate(registeredURI, "URL", "modifyurl");
			//System.out.println("DCOID generated;Process RDFS:\r\n"+changes.toString());
			N3EditUtils.preprocessModels(changes, configuration, vreq);		
			//System.out.println("Apply changes to model\r\n");
			ProcessRdfForm.applyChangesToWriteModel(changes, queryModel, writeModel, EditN3Utils.getEditorUri(vreq));
			//System.out.println("Configuration for back button\r\n");
	        //For data property processing, need to update edit configuration for back button 
			N3EditUtils.updateEditConfigurationForBackButton(configuration, submission, vreq, writeModel);
			//System.out.println("Clean ups\r\n");
	        PostEditCleanupController.doPostEditCleanup(vreq);
	        
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		configuration.setUrlToReturnTo(configuration.getUrlToReturnTo().replace(dcoNamespace, machineURL));
		return configuration.getUrlToReturnTo();
		
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
