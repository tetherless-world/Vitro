package edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.generators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;

import edu.cornell.mannlib.vitro.webapp.beans.Individual;
import edu.cornell.mannlib.vitro.webapp.beans.ObjectProperty;
import edu.cornell.mannlib.vitro.webapp.beans.VClass;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.dao.ModelAccess;
import edu.cornell.mannlib.vitro.webapp.dao.VitroVocabulary;
import edu.cornell.mannlib.vitro.webapp.dao.WebappDaoFactory;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.DateTimeWithPrecisionVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationVTwo;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.fields.FieldVTwo;
import edu.cornell.mannlib.vitro.webapp.utils.FrontEndEditingUtils;
import edu.cornell.mannlib.vitro.webapp.utils.FrontEndEditingUtils.EditMode;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.VTwo.EditConfigurationUtils;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.preprocessors.DefaultAddMissingIndividualFormModelPreprocessor;
import edu.cornell.mannlib.vitro.webapp.edit.n3editing.configuration.validators.AntiXssValidation;
import edu.rpi.twc.dcods.vivo.ServerInfo;


public class AddDistributionGenerator extends BaseEditConfigurationGenerator implements EditConfigurationGenerator{

	final static String vivoCore = "http://vivoweb.org/ontology/core#";
	final static String dco = ServerInfo.getInstance().getDcoOntoNamespace();
	final static String toDateCreated = dco + "dateCreated";
	final static String valueType = vivoCore + "DateTimeValue";
	final static String dateTimeValue = vivoCore + "dateTime";
	final static String dateTimePrecision = vivoCore + "dateTimePrecision";
	private String subjectUri = null;
	private String predicateUri = null;
	private String objectUri = null;
	private static String objectVarName = "newIndividual";
	private String template = "addDistributionDataset.ftl";
	private boolean isObjectPropForm = false;
	private String machineURL = "http://localhost:8080";
	
	@Override
	public EditConfigurationVTwo getEditConfiguration(VitroRequest vreq,
			HttpSession session) {
		
	   EditConfigurationVTwo editConfiguration = new EditConfigurationVTwo();
         //this is just the name, not the full path of the template
       editConfiguration.setTemplate(template);  
       initBasics(editConfiguration, vreq);
       initPropertyParameters(vreq, session, editConfiguration);
       editConfiguration.setVarNameForSubject("subject");
       editConfiguration.setVarNameForPredicate("predicate");
       editConfiguration.setSubjectUri(EditConfigurationUtils.getSubjectUri(vreq));
       //editConfiguration.setFields(Map<String, FieldVTwo>.Entry);
       editConfiguration.setUrlToReturnTo(EditConfigurationUtils.getSubjectUri(vreq).replace("http://deepcarbon.net", machineURL));
       
       //System.out.println("Generator configuraiton is here\r\n"+editConfiguration.toString());
       //System.out.println("Subject URL is"+EditConfigurationUtils.getSubjectUri(vreq));
       //you probably don't need this since we won't actually be using the configuration, try this without it 
       prepare(vreq, editConfiguration);
       
	   //Assumes this is a simple case of subject predicate vear
	   //editConfiguration.setN3Required(this.generateN3Required(vreq));
	   	//n3 optional
	   	editConfiguration.setN3Optional(this.generateN3Optional(vreq));
	   
	   	//editConfiguration.setNewResources(this.generateNewResources(vreq));
	   	
	   	//In scope
	   	this.setUrisAndLiteralsInScope(editConfiguration);
	   	
	   	//on Form
	   	this.setUrisAndLiteralsOnForm(editConfiguration, vreq);
	   	
	   	editConfiguration.setFilesOnForm(new ArrayList<String>());
	   	
	   	//Sparql queries
	   	this.setSparqlQueries(editConfiguration);
	   	
	   	//set fields
	   	setFields(editConfiguration, vreq, EditConfigurationUtils.getPredicateUri(vreq));
	   	
	   	//add preprocesoors
	   	addPreprocessors(vreq, editConfiguration);
	   	prepareForUpdate(vreq, session, editConfiguration);
	   	
	   	//Form title and submit label now moved to edit configuration template
	   	//TODO: check if edit configuration template correct place to set those or whether
	   	//additional methods here should be used and reference instead, e.g. edit configuration template could call
	   	//default obj property form.populateTemplate or some such method
	   	//Select from existing also set within template itself
	   	setTemplate(editConfiguration, vreq);
	   	
	   	editConfiguration.addValidator(new AntiXssValidation());

		//Add access URL test
	   	FieldVTwo accessUrlField = new FieldVTwo();
	   	accessUrlField.setName("accessUrl");
	   	accessUrlField.setRangeLang("accessURLValue");
		editConfiguration.addField(accessUrlField);
		
		//Steal the field skipToUrl to hold accessURL
		//editConfiguration.setSkipToUrl("");
		
	   	//editConfiguration.addField(field);
		prepare(vreq,editConfiguration);
		System.out.println("Generator configurations");
		System.out.println(editConfiguration.toString());
		
	   	return editConfiguration;
	}
	
	private Map<String, String> generateNewResources(VitroRequest vreq) {
		HashMap<String, String> newResources = new HashMap<String, String>();
		//Null triggers default namespace
		newResources.put(objectVarName, null);
		return newResources;
	}
	//Need to replace edit key
    //TODO:Check if we need to recheck forward to create new or assume that is the case since
    //we're using this generator
    //In this case we always set a new edit key as the original jsp checked 'isForwardToCreateNew'
    //which condition would require that an entirely new edit key be created
    private void setEditKey(HttpSession session, EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	String editKey = EditConfigurationVTwo.newEditKey(session);
    	editConfiguration.setEditKey(editKey);
    }
    
	private void setTemplate(EditConfigurationVTwo editConfiguration,
			VitroRequest vreq) {
    	editConfiguration.setTemplate(template);
		
	}

	//Initialize setup: process parameters
	//Doesn't look like we need to set up separate processing for data property form
    private void initProcessParameters(VitroRequest vreq, HttpSession session, EditConfigurationVTwo editConfiguration) {
    	String formUrl = EditConfigurationUtils.getFormUrlWithoutContext(vreq);

    	subjectUri = EditConfigurationUtils.getSubjectUri(vreq);
    	predicateUri = EditConfigurationUtils.getPredicateUri(vreq);
    
    	editConfiguration.setFormUrl(formUrl);
    	
    	editConfiguration.setUrlPatternToReturnTo("/individual");
    	
    	editConfiguration.setVarNameForSubject("subject");
    	editConfiguration.setSubjectUri(subjectUri);
    	editConfiguration.setEntityToReturnTo(subjectUri);
    	editConfiguration.setVarNameForPredicate("predicate");
    	editConfiguration.setPredicateUri(predicateUri);
    	
    	
    	//this needs to be set for the editing to be triggered properly, otherwise the 'prepare' method
    	//pretends this is a data property editing statement and throws an error
    	//"object"       : [ "newIndividual" ,  "${objectUriJson}" , "URI"],
    	if(EditConfigurationUtils.isObjectProperty(predicateUri, vreq)) {
    		//not concerned about remainder, can move into default obj prop form if required
    		this.isObjectPropForm = true;
    		this.initObjectParameters(vreq);
    		this.processObjectPropForm(vreq, editConfiguration);
    	} else {
    		System.out.println("Add missing individual called for a data property instead of object property");
    	}
    }
    
	private void initObjectParameters(VitroRequest vreq) {
		//in case of object property
		String thisObjectUri = EditConfigurationUtils.getObjectUri(vreq);
		if(thisObjectUri != null && !thisObjectUri.isEmpty()) {
			objectUri = EditConfigurationUtils.getObjectUri(vreq);
		}
		//otherwise object uri will stay null - since don't want to set it to empty string
	}

	//this particular form uses a different var name for object "newIndividual"
	private void processObjectPropForm(VitroRequest vreq, EditConfigurationVTwo editConfiguration) {
    	editConfiguration.setVarNameForObject(objectVarName);    	
    	//If is replace with new, set Object resource to null
    	if(isReplaceWithNew(vreq)) {
    		editConfiguration.setObject(null);
    	} else {
    		editConfiguration.setObject(objectUri);
    	}
    	//this needs to be set for the editing to be triggered properly, otherwise the 'prepare' method
    	//pretends this is a data property editing statement and throws an error
    	//TODO: Check if null in case no object uri exists but this is still an object property    	
    }
    
    
    
    //Get N3 required 
    //Handles both object and data property    
    private List<String> generateN3Required(VitroRequest vreq) {
    	List<String> n3ForEdit = new ArrayList<String>();
    	n3ForEdit.add(getN3PrefixesAsString() + "\n" + getN3ForName());
    	n3ForEdit.add("?subject ?predicate ?" + objectVarName + " .");
    	n3ForEdit.add(getN3PrefixesAsString() + "\n" + "?" + objectVarName + " rdf:type <" + getRangeClassUri(vreq) + "> . ");
    	return n3ForEdit;
    }
    
    private List<String> getN3Prefixes() {
    	List<String> prefixStrings = new ArrayList<String>();
    	prefixStrings.add("@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
    	prefixStrings.add("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .");
    	return prefixStrings;
    }
    
    private String getN3PrefixesAsString() {
    	String prefixes = StringUtils.join(getN3Prefixes(), "\n");
    	return prefixes;
    }
    
    private String getN3ForName() {
    	return "?" + objectVarName + " rdfs:label ?name .";
    }
    
    private List<String> generateN3Optional(VitroRequest vreq) {
    	//flag uri and asserted types need to be added here
    	List<String> n3Optional = new ArrayList<String>();
    	
    	//##!This used to work!!
    	//n3Optional.add("?" + objectVarName + " ?inverseProp ?subject .");
    	
    	//asserted types string buffer is empty in the original jsp
    	//TODO: Review original comments in jsp to see what could go here
    	//n3Optional.add(getN3AssertedTypes(vreq));
    	n3Optional.add(getN3PrefixesAsString() + "\n" + "?" + objectVarName + " rdf:type <" + getFlagURI(vreq) + "> . ");
    	return n3Optional;
    	
    }
    
    private String getFlagURI(VitroRequest vreq) {
    	WebappDaoFactory wdf = vreq.getWebappDaoFactory();
    	String flagURI = wdf.getVClassDao().getTopConcept().getURI(); 
    	return flagURI;
	}
	private String getN3AssertedTypes(VitroRequest vreq) {
		return null;
	}
	//Set queries
    private String retrieveQueryForInverse () {
    	String queryForInverse =  "PREFIX owl:  <http://www.w3.org/2002/07/owl#>"
			+ " SELECT ?inverse_property "
			+ "    WHERE { ?inverse_property owl:inverseOf ?predicate } ";
    	return queryForInverse;
    }
    
    private void setUrisAndLiteralsInScope(EditConfigurationVTwo editConfiguration) {
    	HashMap<String, List<String>> urisInScope = new HashMap<String, List<String>>();
    	//Add subject uri and predicate turo to uris in scope
    	urisInScope.put(editConfiguration.getVarNameForSubject(), 
    			Arrays.asList(new String[]{editConfiguration.getSubjectUri()}));
    	urisInScope.put(editConfiguration.getVarNameForPredicate(), 
    			Arrays.asList(new String[]{editConfiguration.getPredicateUri()}));
    	editConfiguration.setUrisInScope(urisInScope);    	
    	editConfiguration.setLiteralsInScope(new HashMap<String, List<Literal>>());
    }
    
    //n3 should look as follows
    //?subject ?predicate ?objectVar 
    
    private void setUrisAndLiteralsOnForm(EditConfigurationVTwo editConfiguration, VitroRequest vreq) {
    	List<String> urisOnForm = new ArrayList<String>();
    	List<String> literalsOnForm = new ArrayList<String>();
    	literalsOnForm.add("name");
    	editConfiguration.setUrisOnform(urisOnForm);
    	editConfiguration.setLiteralsOnForm(literalsOnForm);
    }
   
    
    //This is for various items
    private void setSparqlQueries(EditConfigurationVTwo editConfiguration) {
    	//Sparql queries defining retrieval of literals etc.
    	editConfiguration.setSparqlForAdditionalLiteralsInScope(new HashMap<String, String>());
    	
    	Map<String, String> urisInScope = new HashMap<String, String>();
    	urisInScope.put("inverseProp", this.retrieveQueryForInverse());
    	editConfiguration.setSparqlForAdditionalUrisInScope(urisInScope);
    	
    	editConfiguration.setSparqlForExistingLiterals(generateSparqlForExistingLiterals());
    	editConfiguration.setSparqlForExistingUris(generateSparqlForExistingUris());
    	
    }
    
    
    private HashMap<String, String> generateSparqlForExistingUris() {
    	HashMap<String, String> map = new HashMap<String, String>();
    	return map;
    }
    
    private HashMap<String, String> generateSparqlForExistingLiterals() {
    	HashMap<String, String> map = new HashMap<String, String>();
    	String query = "PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#> ";
    	query += "SELECT ?existingName WHERE { ?" + objectVarName + " rdfs:label ?existingName . }";
    	map.put("name", query);
    	
    	return map;
    }

    
    private void setFields(EditConfigurationVTwo editConfiguration, VitroRequest vreq, String predicateUri) {
    	Map<String, FieldVTwo> fields = new HashMap<String, FieldVTwo>();
    	if(EditConfigurationUtils.isObjectProperty(EditConfigurationUtils.getPredicateUri(vreq), vreq)) {
    		    			      
    	    //make name field
    	    FieldVTwo field = new FieldVTwo();
	        field.setName("name");      	        
	        
	        List<String> validators = new ArrayList<String>();
	        validators.add("nonempty");
	        field.setValidators(validators);   
	                            
	        fields.put(field.getName(), field);
    	        
    	} else {
    		System.out.println("Is not object property so fields not set");
    	}
    	
    	editConfiguration.setFields(fields);
    }

	private String getRangeClassUri(VitroRequest vreq) {
		Individual subject = EditConfigurationUtils.getSubjectIndividual(vreq);
		ObjectProperty prop = EditConfigurationUtils.getObjectProperty(vreq);
		
	    WebappDaoFactory wdf = vreq.getWebappDaoFactory();
	    if( prop.getRangeVClassURI() == null ) {
	        // If property has no explicit range, we will use e.g. owl:Thing.
	        // Typically an allValuesFrom restriction will come into play later.
	        VClass top = wdf.getVClassDao().getTopConcept();
	        prop.setRangeVClassURI(top.getURI());
	    }
	    
	    VClass rangeClass = null;
	    String typeOfNew = getTypeOfNew(vreq);
	    if(typeOfNew != null )
	    	rangeClass = wdf.getVClassDao().getVClassByURI( typeOfNew );
	    if( rangeClass == null ){
	    	rangeClass = wdf.getVClassDao().getVClassByURI(prop.getRangeVClassURI());
	    	if( rangeClass == null ) throw new Error ("Cannot find class for range for property.  Looking for " + prop.getRangeVClassURI() );    
	    }
		return rangeClass.getURI();
	}

	private void prepareForUpdate(VitroRequest vreq, HttpSession session, EditConfigurationVTwo editConfiguration) {
    	//Here, retrieve model from 
		/**
		 * edit by Yu Chen April 13th,2014
		 * Model is retrieved using the new method
		 */
    	//Model model = (Model) session.getServletContext().getAttribute("jenaOntModel");
    	OntModel model = ModelAccess.on(vreq).getJenaOntModel();
		System.out.println("attributes are:");
		
		Enumeration allAttri = session.getServletContext().getAttributeNames();
		while(allAttri.hasMoreElements()){
			String param = (String) allAttri.nextElement();
			System.out.println(param);
		}

    	//if object property
    	if(EditConfigurationUtils.isObjectProperty(EditConfigurationUtils.getPredicateUri(vreq), vreq)){
	    	Individual objectIndividual = EditConfigurationUtils.getObjectIndividual(vreq);
	    	if(!isReplaceWithNew(vreq) && 
	    			(isForwardToCreateButEdit(vreq) || 
	    			objectIndividual != null)
	    		) {
	    		editConfiguration.prepareForObjPropUpdate(model);
	    	}  else {
	    		//new object to be created
	    		System.out.println("distribution gen");
	            editConfiguration.prepareForNonUpdate(model);
	        }
    	} else {
    		System.out.println("Data property not object property so update can't be done correctly");
    		
    	}
    }
    
	private void addPreprocessors(VitroRequest vreq, EditConfigurationVTwo editConfiguration) {
		if(isReplaceWithNew(vreq)) {
			editConfiguration.addModelChangePreprocessor(
					new DefaultAddMissingIndividualFormModelPreprocessor(
							subjectUri, predicateUri, objectUri));
			
		}
	}
	
    //Command processing
    private boolean isTypeOfNew(VitroRequest vreq) {
    	String typeOfNew = getTypeOfNew(vreq);
    	return (typeOfNew != null && !typeOfNew.isEmpty());
    }
    
    private String getTypeOfNew(VitroRequest vreq) {
    	return  vreq.getParameter("typeOfNew");
    }
    
    private boolean isReplaceWithNew(VitroRequest vreq) {
    	ObjectProperty objectProp = EditConfigurationUtils.getObjectProperty(vreq);
    	boolean isEditOfExistingStmt = isEditOfExistingStatement(vreq);
    	String command = vreq.getParameter("cmd");
    	return (isEditOfExistingStmt 
    			&& "create".equals(command)) 
    	       && (objectProp != null)
    	       && (objectProp.getOfferCreateNewOption() == true);                
    }
    
    private boolean isForwardToCreateButEdit(VitroRequest vreq) {
    	boolean isEditOfExistingStmt = isEditOfExistingStatement(vreq);
    	ObjectProperty objectProp = EditConfigurationUtils.getObjectProperty(vreq);
    	String command = vreq.getParameter("cmd");
    	return (isEditOfExistingStmt 
    			&& (! "create".equals(command))
    			&& (objectProp != null) 
    	       && (objectProp.getOfferCreateNewOption() == true) 
    	       && (objectProp.getSelectFromExisting() == false)
    	     );
    }
    
    //Is edit of existing statement only applicable to object properties
    private boolean isEditOfExistingStatement(VitroRequest vreq) {
    	//TODO: Check if also applicable to data property, currently returning false
    	if(EditConfigurationUtils.isDataProperty(EditConfigurationUtils.getPredicateUri(vreq), vreq)) {
    		return false;
    	}
    	Individual object = EditConfigurationUtils.getObjectIndividual(vreq);
    	return (object != null);

    }
		

}
