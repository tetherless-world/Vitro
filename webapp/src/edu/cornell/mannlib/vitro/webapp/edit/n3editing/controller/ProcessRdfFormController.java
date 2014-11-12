/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.edit.n3editing.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

import edu.cornell.mannlib.vitro.webapp.auth.identifier.IdentifierBundle;
import edu.cornell.mannlib.vitro.webapp.auth.identifier.RequestIdentifiers;
import edu.cornell.mannlib.vitro.webapp.auth.identifier.common.HasProfile;
import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.auth.requestedAction.Actions;
import edu.cornell.mannlib.vitro.webapp.beans.DataProperty;
import edu.cornell.mannlib.vitro.webapp.beans.DataPropertyStatement;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.FreemarkerHttpServlet;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.RedirectResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.ResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.TemplateResponseValues;
import edu.cornell.mannlib.vitro.webapp.dao.NewURIMakerVitro;
import edu.cornell.mannlib.vitro.webapp.dao.WebappDaoFactory;
import edu.cornell.mannlib.vitro.webapp.edit.EditLiteral;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.AdditionsAndRetractions;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditSubmissionUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.MultiValueEditSubmission;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.N3EditUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.ProcessRdfForm;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.RdfLiteralHash;
import edu.rpi.twc.dcods.vivo.CKANException;
import edu.rpi.twc.dcods.vivo.Client;
import edu.rpi.twc.dcods.vivo.Connection;
import edu.rpi.twc.dcods.vivo.DCOId;
import edu.rpi.twc.dcods.vivo.Dataset;
import edu.rpi.twc.dcods.vivo.Extra;
import edu.rpi.twc.dcods.vivo.ServerInfo;

/**
 * This servlet will convert a request to an EditSubmission, 
 * find the EditConfiguration associated with the request, 
 * use ProcessRdfForm to process these to a set of RDF additions and retractions,
 * the apply these to the models. 
 */
public class ProcessRdfFormController extends FreemarkerHttpServlet{
	
    private Log log = LogFactory.getLog(ProcessRdfFormController.class);
    private String absoluteMachineURL = ServerInfo.getInstance().getAbsoluteMachineURL();
    private String namespace = ServerInfo.getInstance().getDcoNamespace();
    private String ckanURL = ServerInfo.getInstance().getCkanURL();
    private String dcoOntoNamespace = ServerInfo.getInstance().getDcoOntoNamespace();
    private String predicateAccessURL = dcoOntoNamespace+"accessURL";
    private String predicateDcoID = dcoOntoNamespace+"dcoId";
    private String predicateHasDcoId = dcoOntoNamespace+"hasDcoId";
    private String predicateOwnedBy = dcoOntoNamespace+"ownedBy";
    
    @Override
	protected Actions requiredActions(VitroRequest vreq) {
    	return SimplePermission.DO_FRONT_END_EDITING.ACTIONS;
	}

	@Override 
	protected ResponseValues processRequest(VitroRequest vreq) {			
		//get the EditConfiguration 
		EditConfigurationVTwo configuration = EditConfigurationUtils.getEditConfiguration(vreq);
        if(configuration == null)
            return handleMissingConfiguration(vreq);

        //get the EditSubmission
        MultiValueEditSubmission submission = new MultiValueEditSubmission(vreq.getParameterMap(), configuration);        	
        EditSubmissionUtils.putEditSubmissionInSession(vreq.getSession(), submission);
       
        //if errors, return error response
	ResponseValues errorResponse = doValidationErrors(vreq, configuration, submission);
	if( errorResponse != null )
	    return errorResponse;

        // get the models to work with in case the write model and query model are not the defaults 
	Model queryModel = configuration.getQueryModelSelector().getModel(vreq, getServletContext());		
        Model writeModel = configuration.getWriteModelSelector().getModel(vreq,getServletContext());  
	    
	    //If data property check for back button confusion
	    boolean isBackButton = checkForBackButtonConfusion(configuration, vreq, queryModel);
	    if(isBackButton) {
	    	//Process back button issues and do a return here
	    	return doProcessBackButton(configuration, submission, vreq);
	    }
	    //Get user profile if there is one bundled
	    /*
	    IdentifierBundle ids = RequestIdentifiers.getIdBundleForRequest((HttpServletRequest)vreq);
	    Collection<String> profileUris = HasProfile.getProfileUris(ids);
	    String profileUri = "";
	    if (!profileUris.isEmpty())
	        profileUri = profileUris.iterator().next();
	    */
	    //Otherwise, process as usual
	    
	    AdditionsAndRetractions changes;
	/* 
        try {

            ProcessRdfForm prf = 
                new ProcessRdfForm(configuration, new NewURIMakerVitro(vreq.getWebappDaoFactory()));        
            changes = prf.process(configuration, submission, vreq);  
            
        } catch (Exception e) {
            throw new Error(e);
        }	    
	*/
	    try {	    	
    		// @dco code used to generate dco-id for all new individuals
    		Map<String, String> newResources = configuration.getNewResources();
    		if (newResources.size() > 0) {
    			Set<String>  newResourcesKeys= newResources.keySet();
    			System.out.println("The resource key set is "+newResourcesKeys.toString());
    			for (String key: newResourcesKeys) {
				/*
	    			if(profileUri.length()!=0){
	    				configuration.addN3Optional("?"+key+" <"+predicateOwnedBy+"> <"+profileUri+"> .");
	    			}
	    			*/	
	    			// Note that if the newly created instance is a distribution, then we create a corresponded dataset on CKAN, add that 
	    			// url on the ckan website to the distribution individual
	    			
	    			if(configuration.getPredicateUri()!=null&&configuration.getPredicateUri().toLowerCase().endsWith("distribution")
	    					&&submission.getLiteralsFromForm().get("label")!=null){
	    				
		    			//System.out.println("The name for the object is "+(submission.getLiteralsFromForm().get("label").toString().split("\\[")[1].split("\\^\\^")[0]));
	    				String dataRepoName = submission.getLiteralsFromForm().get("label").toString().split("\\[")[1].split("\\^\\^")[0].split("\\]")[0];
	    				
	    				String apiKey = "96c00a49-9604-42ca-84b1-e43674f6c0f8";
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
	    		        
	    		        	try {
	    		        	
	    					Dataset result = c.createDataset(ds);
	    					System.out.println("Here is everything "+ckanURL+"/dataset/"+result.getName());
	    					String value = ckanURL+"/dataset/"+result.getName();
				    	   	String accessURLTriple = "?" + key + " <"+predicateAccessURL+"> " + '"' + value + '"' + " .";
				    		configuration.addN3Optional(accessURLTriple);
	    					
	    				} catch (CKANException e) {
	    					// TODO Auto-generated catch block
	    					e.printStackTrace();
	    				}
	    		        
	    			}
	    			
	    			// add the new dco id object to the map so we can modify the url later
	    			//newDcoIds.put(key, dcoid);
    			}
    			// This is where VIVO is actually creating the new individuals. All the properties have been created, all the information
    			// gathered. Included in this is the new dco id. Now, go create the new individuals in the store
	    		ProcessRdfForm prf = 
	    	        	new ProcessRdfForm(configuration, new NewURIMakerVitro(vreq.getWebappDaoFactory()));    
	        	changes = prf.process(configuration, submission, vreq);
    		}
    		else {
    			ProcessRdfForm prf = 
    					new ProcessRdfForm(configuration, new NewURIMakerVitro(vreq.getWebappDaoFactory()));    
    			changes = prf.process(configuration, submission, vreq);
    		}
	    } catch (Exception e) {
	    	throw new Error(e);
	    }
        
		
	    if( configuration.isUseDependentResourceDelete() )
		changes = ProcessRdfForm.addDependentDeletes(changes, queryModel);		
		
	    N3EditUtils.preprocessModels(changes, configuration, vreq);		
		
	    ProcessRdfForm.applyChangesToWriteModel(changes, queryModel, writeModel, N3EditUtils.getEditorUri(vreq) );
		
	    //Here we are trying to get the entity to return to URL,  
	    //More involved processing for data property apparently
	    String entityToReturnTo = N3EditUtils.processEntityToReturnTo(configuration, submission, vreq);
		
            //For data property processing, need to update edit configuration for back button 
	    N3EditUtils.updateEditConfigurationForBackButton(configuration, submission, vreq, writeModel);
            PostEditCleanupController.doPostEditCleanup(vreq);
            return PostEditCleanupController.doPostEditRedirect(vreq, entityToReturnTo);
	}

	//In case of back button confusion
	//Currently returning an error message: 
	//Later TODO: Per Brian Caruso's instructions, replicate
	//the logic in the original datapropertyBackButtonProblems.jsp
	private ResponseValues doProcessBackButton(EditConfigurationVTwo configuration,
			MultiValueEditSubmission submission, VitroRequest vreq) {
		  
		//The bulk of the processing should probably/already sits in ProcessRdfForm so that should remain there
		//The issue is what then to do with the actual redirect? What do we redirect to?
		HashMap<String,Object> map = new HashMap<String,Object>();
   	 	map.put("errorMessage", "Back button confusion has occurred");
		ResponseValues values = new TemplateResponseValues("error-message.ftl", map);        
		return values;
	}

	//Check for "back button" confusion specifically for data property editing although need to check if this applies to object property editing?
	//TODO: Check if only applicable to data property editing
	private boolean checkForBackButtonConfusion(EditConfigurationVTwo editConfig, VitroRequest vreq, Model model) {
		//back button confusion limited to data property
		if(EditConfigurationUtils.isObjectProperty(editConfig.getPredicateUri(), vreq)) {
			return false;
		}
		
		WebappDaoFactory wdf = vreq.getWebappDaoFactory();
		 if ( ! editConfig.isDataPropertyUpdate())
	            return false;
	        
        Integer dpropHash = editConfig.getDatapropKey();
        DataPropertyStatement dps = 
            RdfLiteralHash.getPropertyStmtByHash(editConfig.getSubjectUri(), 
                    editConfig.getPredicateUri(), dpropHash, model);
        if (dps != null)
            return false;
        
        DataProperty dp = wdf.getDataPropertyDao().getDataPropertyByURI(
                editConfig.getPredicateUri());
        if (dp != null) {
            if (dp.getDisplayLimit() == 1 /* || dp.isFunctional() */)
                return false;
            else
                return true;
        }
        return false;

	}
	
	private ResponseValues doValidationErrors(VitroRequest vreq,
			EditConfigurationVTwo editConfiguration, MultiValueEditSubmission submission) {
		
		Map<String, String> errors = submission.getValidationErrors();
		
		if(errors != null && !errors.isEmpty()){
			String form = editConfiguration.getFormUrl();
			vreq.setAttribute("formUrl", form);
			vreq.setAttribute("view", vreq.getParameter("view"));	
			//Need to ensure that edit key is set so that the correct
			//edit configuration and edit submission are retrieved
			//This can also be set as a parameter instead
			String formUrl = editConfiguration.getFormUrl();
			formUrl += "&editKey=" + editConfiguration.getEditKey();
	        return new RedirectResponseValues(formUrl);
		}
		return null; //no errors		
	}

    /**
     * If the edit configuration cannot be found in the Session,
     * show a message and display any request parameters so that the user's
     * efforts  won't be lost.
     */
    protected ResponseValues handleMissingConfiguration(VitroRequest vreq){
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("params",vreq.getParameterMap());
        return new TemplateResponseValues( "missingEditConfig.ftl", map );
    }
	
	//Move to EditN3Utils but keep make new uris here
	public static class Utilities {
		
		private static Log log = LogFactory.getLog(ProcessRdfFormController.class);
	    
		public static String assertionsType = "assertions";
		public static String retractionsType = "retractions";
		
		public static boolean isDataProperty(EditConfigurationVTwo configuration, VitroRequest vreq) {
			return EditConfigurationUtils.isDataProperty(configuration.getPredicateUri(), vreq);
		}
		
		public static boolean isObjectProperty(EditConfigurationVTwo configuration, VitroRequest vreq) {
			
			return EditConfigurationUtils.isObjectProperty(configuration.getPredicateUri(), vreq);
		}
		
	    public static List<String> makeListCopy(List<String> list) {
	    	List<String> copyOfN3 = new ArrayList<String>();
            for( String str : list){
                copyOfN3.add(str);
            }
            return copyOfN3;
	    }
	     
	     //TODO: Check if this would be correct with multiple values and uris being passed back
	     //First, need to order by uris in original and new values probably and 
	     //for literals, order by? Alphabetical or numeric value? Hard to say
	     public static boolean hasFieldChanged(String fieldName,
	             EditConfigurationVTwo editConfig, MultiValueEditSubmission submission) {
	         List<String> orgValue = editConfig.getUrisInScope().get(fieldName);
	         List<String> newValue = submission.getUrisFromForm().get(fieldName);
	         //Sort both just in case
	         if(orgValue != null) {
	        	 Collections.sort(orgValue);
	         }
	         if(newValue != null) {
	        	 Collections.sort(newValue);
	         }
	         if (orgValue != null && newValue != null) {
	             if (orgValue.equals(newValue))
	                 return false;
	             else
	                 return true;
	         }

	         List<Literal> orgLit = editConfig.getLiteralsInScope().get(fieldName);
	         List<Literal> newLit = submission.getLiteralsFromForm().get(fieldName);
	         //TODO: Sort ? Need custom comparator
	         //Collections.sort(orgLit);
	         //Collections.sort(newLit);
	         //for(Literal l: orgLit)
	         //boolean fieldChanged = !EditLiteral.equalLiterals(orgLit, newLit);
	         //TODO:Check if below acts as expected
	         boolean fieldChanged = !orgLit.equals(newLit);
	         if(!fieldChanged) {
	        	 int orgLen = orgLit.size();
	        	 int newLen = newLit.size();
	        	 if(orgLen != newLen) {
	        		 fieldChanged = true;
	        	 } else {
	        		 int i;
	        		 for(i = 0; i < orgLen; i++) {
	        			 if(!EditLiteral.equalLiterals(orgLit.get(i), newLit.get(i))) {
	        				 fieldChanged = true;
	        				 break;
	        			 }
	        		 }
	        	 }
	         }
	         log.debug("field " + fieldName + " "
	                 + (fieldChanged ? "did Change" : "did NOT change"));
	         return fieldChanged;
	     }
	     		
		//Get predicate local anchor
		public static String getPredicateLocalName(EditConfigurationVTwo editConfig) {
			String predicateLocalName = null;
	        if( editConfig != null ){
                String predicateUri = editConfig.getPredicateUri();            
                if( predicateUri != null ){
                    try{
                        Property prop = ResourceFactory.createProperty(predicateUri);
                        predicateLocalName = prop.getLocalName();
                    
                    }catch (com.hp.hpl.jena.shared.InvalidPropertyURIException e){                  
                        log.debug("could not convert predicateUri into a valid URI",e);
                    }                               
                }
	        }
	        return predicateLocalName;
		}
		//Get URL pattern for return
		public static String getPostEditUrlPattern(VitroRequest vreq, EditConfigurationVTwo editConfig) {
			String cancel = vreq.getParameter("cancel");
	        String urlPattern = null;

            String urlPatternToReturnTo = null;
            String urlPatternToCancelTo = null;
            if (editConfig != null) {
                urlPatternToReturnTo = editConfig.getUrlPatternToReturnTo();
                urlPatternToCancelTo = vreq.getParameter("url");
            }
            // If a different cancel return path has been designated, use it. Otherwise, use the regular return path.
            if (cancel != null && cancel.equals("true") && !StringUtils.isEmpty(urlPatternToCancelTo)) {
                urlPattern = urlPatternToCancelTo;
            } else if (!StringUtils.isEmpty(urlPatternToReturnTo)) {
                urlPattern = urlPatternToReturnTo;       
            } else {
                urlPattern = "/individual";         
            }
            return urlPattern;
		}
		
		//Get resource to redirect to
		public static String getResourceToRedirect(VitroRequest vreq, EditConfigurationVTwo editConfig, String entityToReturnTo ) {
			String resourceToRedirectTo = null;
			if( editConfig != null ){

                if( editConfig.getEntityToReturnTo() != null && editConfig.getEntityToReturnTo().startsWith("?") ){             
                    resourceToRedirectTo = entityToReturnTo;            
                }else{            
                    resourceToRedirectTo = editConfig.getEntityToReturnTo();
                }
                
                //if there is no entity to return to it is likely a cancel
                if( resourceToRedirectTo == null || resourceToRedirectTo.length() == 0 )
                    resourceToRedirectTo = editConfig.getSubjectUri();                
            }
			return resourceToRedirectTo;
		}
		

			
	}

}
