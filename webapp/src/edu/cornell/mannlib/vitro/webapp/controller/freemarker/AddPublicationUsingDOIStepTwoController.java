package edu.cornell.mannlib.vitro.webapp.controller.freemarker;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.auth.requestedAction.Actions;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.RedirectResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.ResponseValues;
import edu.cornell.mannlib.vitro.webapp.controller.freemarker.responsevalues.TemplateResponseValues;
import edu.rpi.twc.dcods.vivo.DCOId;
import edu.rpi.twc.dcods.vivo.PublicationMetadataImportException;
import edu.rpi.twc.dcods.vivo.ServerInfo;
import edu.rpi.twc.dcods.vivo.SparqlQueryUtils;

public class AddPublicationUsingDOIStepTwoController extends FreemarkerHttpServlet {
	
	private static final long serialVersionUID = 1L;
	private String absoluteMachineURL = ServerInfo.getInstance().getAbsoluteMachineURL();
	private String rootUserPassword = ServerInfo.getInstance().getRootPassword();
	private String rootUserEmail = ServerInfo.getInstance().getRootName();
	private String returnURL = absoluteMachineURL;
	
	@Override
	protected Actions requiredActions(VitroRequest vreq) {
		return SimplePermission.DO_FRONT_END_EDITING.ACTIONS;
	}
	
	@Override
	public ResponseValues processRequest(VitroRequest vreq) {
		// Check if the publication with the given DOI is already in the system.
		String doi = vreq.getParameter("doi");
		String checkPublicationExistenQuery = 
			"SELECT * WHERE {" + 
			"{ ?pub <http://purl.org/ontology/bibo/doi> \"" + doi.toLowerCase() + "\" . } UNION { ?pub <http://purl.org/ontology/bibo/doi> \"" + doi.toUpperCase() + "\" . } " + 
			"?pub <http://www.w3.org/2000/01/rdf-schema#label> ?label . }";
		JSONArray checkPublicationExistenQueryResults = SparqlQueryUtils.vivoSparqlSelect(checkPublicationExistenQuery);
		if (checkPublicationExistenQueryResults.length() >0 ) {
			HashMap<String,Object> map = new HashMap<String,Object>();
			map.put("errorMessage", "There is already a publication entry with this DOI in the system.");
			try {
				JSONObject existingPub = (JSONObject) checkPublicationExistenQueryResults.get(0);
				String existingPubURI = existingPub.getJSONObject("pub").getString("value");
				String existingPubLabel = existingPub.getJSONObject("label").getString("value");
		       	map.put("link", existingPubURI);
		       	map.put("linkLabel", existingPubLabel);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return new TemplateResponseValues("error-message.ftl", map);
		} else {
			try {
				// Publication type must be given.
				if (vreq.getParameter("pubType").isEmpty() || vreq.getParameter("title").isEmpty())
					throw new PublicationMetadataImportException("Publication type must be specified.");
				// If a new venue is to be created, its type must be given.
				if (!vreq.getParameter("venue").startsWith("http:") && vreq.getParameter("venueType").isEmpty())
					throw new PublicationMetadataImportException("Venue type must be specified.");
				
				// Get form input.
				Map<String, String> parameters = new HashMap<String, String> ();
		    	Enumeration allParams = vreq.getParameterNames();
				while (allParams.hasMoreElements()) {
					String paraName = allParams.nextElement().toString();
					parameters.put(paraName, vreq.getParameter(paraName));
				}
				//System.out.println("Parameters: " + parameters);
				
				// Generate the n3 to be inserted.
				String newPublicationN3 = generateN3(parameters);
	//			System.out.println("New publication n3:\n" + newPublicationN3);
				
				// Insert the N3 using VIVO API
				int vivoInsertRequestStatusCode = 0;
				vivoInsertRequestStatusCode = SparqlQueryUtils.vivoSparqlInsert(rootUserPassword, rootUserEmail, newPublicationN3);
				//System.out.println("VIVO insert request response code:" + vivoInsertRequestStatusCode);		
				//System.out.println("Insert done!");
				
				return new RedirectResponseValues(returnURL);
			} catch (Throwable th) {
				HashMap<String,Object> map = new HashMap<String,Object>();
		       	map.put("errorMessage", th.toString());
		       	return new TemplateResponseValues("error-message.ftl", map);
			}
		}
	}
	
	private String generateN3(Map<String, String> parameters) {
		String triples = "";
		for (Map.Entry<String, String> parameter : parameters.entrySet()) {
			String key = parameter.getKey();
			//System.out.println("Key: " + key);
			String value = parameter.getValue();
			//System.out.println("Value: " + value);
			if (key.equals("title") && !value.isEmpty()) {
				triples += "?newPub rdfs:label \"" + value + "\" . \n";
			}
			if (key.equals("pubType") && !value.isEmpty()) {
				triples += "?newPub a <" + value + "> . \n";
			}
			if (key.equals("doi") && !value.isEmpty()) {
				triples += "?newPub bibo:doi \"" + value + "\" . \n";
			}
			if (key.equals("pubYear") && !value.isEmpty()) {
				triples += "?newPub dco:yearOfPublication \"" + value + "\"^^xsd:gYear . \n";
			}
			if (key.equals("venue") && !value.isEmpty()) {
				if (value.startsWith("http:")) {
					triples += "?newPub vivo:hasPublicationVenue <" + value + "> . \n" +
						  "<" + value + "> vivo:publicationVenueFor ?newPub . \n";
				}
				else {
					if (!parameters.get("venueType").isEmpty()) {
						triples += "?newVenue a <" + parameters.get("venueType") + "> . \n" +
							  "?newVenue rdfs:label \"" + value + "\" . \n" +
							  "?newPub vivo:hasPublicationVenue ?newVenue . \n" +
							  "?newVenue vivo:publicationVenueFor ?newPub . \n";
						String venueURI = generateIndividualURI();
						DCOId dcoId = new DCOId();
						dcoId.operate(venueURI, "URI", "create");
						String pubDCOId = dcoId.getDCOId();
						String pubDCOIdLabel = pubDCOId.substring(25);
						triples += "?newVenue dco:hasDcoId <" + pubDCOId + "> . \n" +
							  "<" + pubDCOId + "> a dco:DCOID . \n" +
							  "<" + pubDCOId + "> rdfs:label \"" + pubDCOIdLabel + "\" . \n" +
							  "<" + pubDCOId + "> dco:dcoIdFor ?newVenue . \n";
						triples = triples.replaceAll("\\?\\bnewVenue\\b", "<" + venueURI + ">");
					}
				}
			}
			if (key.startsWith("author") && !value.isEmpty()) {
				String order = key.split("-")[1];
				if (value.startsWith("http:")) {
					triples += "?newPub vivo:relatedBy ?newAuthorship . \n" +
						  "?newAuthorship a vivo:Authorship . \n" +
						  "?newAuthorship vivo:relates <" + value + "> . \n" +
						  "?newAuthorship vivo:rank \"" + order + "\"^^xsd:int . \n";
				}
				else {
					String[] names = value.split(",");
					String familyName = names[0].trim();
					String givenName = names[1].trim();
					triples += "?newPub vivo:relatedBy ?newAuthorship . \n" +
						  "?newAuthorship a vivo:Authorship . \n" +
						  "?newAuthorship vivo:relates ?newAuthor . \n" +
						  "?newAuthorship vivo:rank \"" + order + "\"^^xsd:int . \n" +
						  "?newAuthor a foaf:Person . \n" +
						  "?newAuthor rdfs:label \"" + value + "\" . \n" + 
						  "?newAuthor obo:ARG_2000028 ?newVCard . \n" +
						  "?newVCard a vcard:Individual . \n" +
						  "?newVCard obo:ARG_2000029 ?newAuthor . \n" +
						  "?newVCard vcard:hasName ?newName . \n" +
						  "?newName a vcard:Name . \n" +
						  "?newName vcard:familyName \"" + familyName + "\" . \n" +
						  "?newName vcard:givenName \"" + givenName + "\" . \n";
					String nameURI = generateIndividualURI();
					String vCardURI = generateIndividualURI();
					String authorURI = generateIndividualURI();
					DCOId dcoId = new DCOId();
					dcoId.operate(authorURI, "URI", "create");
					String pubDCOId = dcoId.getDCOId();
					String pubDCOIdLabel = pubDCOId.substring(25);
					triples += "?newAuthor dco:hasDcoId <" + pubDCOId + "> . \n" +
						  "<" + pubDCOId + "> a dco:DCOID . \n" +
						  "<" + pubDCOId + "> rdfs:label \"" + pubDCOIdLabel + "\" . \n" +
						  "<" + pubDCOId + "> dco:dcoIdFor ?newAuthor . \n";
					triples = triples.replaceAll("\\?\\bnewName\\b", "<" + nameURI + ">");
					triples = triples.replaceAll("\\?\\bnewVCard\\b", "<" + vCardURI + ">");
					triples = triples.replaceAll("\\?\\bnewAuthor\\b", "<" + authorURI + ">");
				}
				String authorshipURI = generateIndividualURI();
				triples = triples.replaceAll("\\?\\bnewAuthorship\\b", "<" + authorshipURI + ">");
			}
		}
		String publicationURI = generateIndividualURI();
		DCOId dcoId = new DCOId();
		dcoId.operate(publicationURI, "URI", "create");
		String pubDCOId = dcoId.getDCOId();
		String pubDCOIdLabel = pubDCOId.substring(25);
		triples += "?newPub dco:hasDcoId <" + pubDCOId + "> . \n" +
			  "<" + pubDCOId + "> a dco:DCOID . \n" +
			  "<" + pubDCOId + "> rdfs:label \"" + pubDCOIdLabel + "\" . \n" +
			  "<" + pubDCOId + "> dco:dcoIdFor ?newPub . \n";
		triples = triples.replaceAll("\\?\\bnewPub\\b", "<" + publicationURI + ">");
		
		String n3 = 
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
				"PREFIX vivo: <http://vivoweb.org/ontology/core#>\n" +
				"PREFIX bibo: <http://purl.org/ontology/bibo/>\n" +
				"PREFIX xsd:   <http://www.w3.org/2001/XMLSchema#>\n" +
				"PREFIX dco: <http://info.deepcarbon.net/schema#>\n" +
				"PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n" +
				"PREFIX obo: <http://purl.obolibrary.org/obo/>\n" +
				"PREFIX vcard: <http://www.w3.org/2006/vcard/ns#>\n" +
				"INSERT DATA {\n" +
				"GRAPH <http://vitro.mannlib.cornell.edu/default/vitro-kb-2>\n" +
				"{\n" + triples +
				"\n}}";
		returnURL = publicationURI;
		return n3;
	}

	private String generateIndividualURI() {
		String uri = new String();
		String queryStr = "ASK { <" + uri + "> ?p ?o }";
		String prefix = ServerInfo.getInstance().getDcoNamespace();
		do {
		    uri = prefix + "/individual/" + UUID.randomUUID().toString();	    
		} while(SparqlQueryUtils.vivoSparqlAsk(queryStr));
	    return uri;
	}
	
}
