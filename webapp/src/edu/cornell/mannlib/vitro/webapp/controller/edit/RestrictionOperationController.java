/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.controller.edit;

import java.util.HashMap;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.TypeMapper;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.Restriction;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.vocabulary.DAML_OIL;

import edu.cornell.mannlib.vedit.beans.EditProcessObject;
import edu.cornell.mannlib.vedit.controller.BaseEditController;
import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.dao.ModelAccess;
import edu.cornell.mannlib.vitro.webapp.dao.ModelAccess.ModelID;
import edu.cornell.mannlib.vitro.webapp.dao.jena.event.EditEvent;

public class RestrictionOperationController extends BaseEditController {

	private static final Log log = LogFactory.getLog(RestrictionOperationController.class.getName());
	
	public void doPost(HttpServletRequest req, HttpServletResponse response) {
        if (!isAuthorizedToDisplayPage(req, response, SimplePermission.EDIT_ONTOLOGY.ACTIONS)) {
        	return;
        }

		VitroRequest request = new VitroRequest(req);
		String defaultLandingPage = getDefaultLandingPage(request);
		
	    try {
	    	
	    	OntModel ontModel = ModelAccess.on(
	    	        getServletContext()).getOntModel(ModelID.BASE_TBOX);
		    
            HashMap epoHash = null;
            EditProcessObject epo = null;
            try {
                epoHash = (HashMap) request.getSession().getAttribute("epoHash");
                epo = (EditProcessObject) epoHash.get(request.getParameter("_epoKey"));
            } catch (NullPointerException e) {
                //session or edit process expired
                response.sendRedirect(defaultLandingPage);
                return;
            }

            if (epo == null) {
                response.sendRedirect(defaultLandingPage);
                return;
            } 
                
            if ( (request.getParameter("_cancel") == null ) ) {
                processRestriction(request, epo, ontModel);   
            }
                        
            //if no page forwarder was set, just go back to referring page:
            String referer = epo.getReferer();
            if (referer == null) {
                response.sendRedirect(defaultLandingPage);
            } else {
                response.sendRedirect(referer);
            }
		    
	    } catch (Exception e) {
	    	log.error(e, e);
	    	try {
	    		response.sendRedirect(defaultLandingPage);
	    		return;
	    	} catch (Exception f) {
                log.error(f, f);
                throw new RuntimeException(f);
	    	}
	    }
	}
	
	private void processRestriction(VitroRequest request, EditProcessObject epo, OntModel ontModel) {
        ontModel.enterCriticalSection(Lock.WRITE);
        try {
            
            ontModel.getBaseModel().notifyEvent(new EditEvent(request.getUnfilteredWebappDaoFactory().getUserURI(),true));
            
            if ("delete".equals(request.getParameter("_action"))) {
                processDelete(request, ontModel);   
            }  else {
                processCreate(request, epo, ontModel);            
            }
            
        } finally {
            ontModel.getBaseModel().notifyEvent(new EditEvent(request.getUnfilteredWebappDaoFactory().getUserURI(),false));
            ontModel.leaveCriticalSection();
        }
    
    }

    private void processDelete(VitroRequest request, OntModel ontModel) {
        
        String restId = request.getParameter("restrictionId");
        
        if (restId != null) {
            
            OntClass restrictedClass = ontModel.getOntClass( request.getParameter( "classUri" ) );
            
            OntClass rest = null;
            
            for ( Iterator i = restrictedClass.listEquivalentClasses(); i.hasNext(); ) {
                OntClass equivClass = (OntClass) i.next();
                if (equivClass.isAnon() && equivClass.getId().toString().equals(restId)) {
                    rest = equivClass;
                }
            }
            
            if ( rest == null ) { 
                for ( Iterator i = restrictedClass.listSuperClasses(); i.hasNext(); ) {
                    OntClass  superClass = (OntClass) i.next();
                    if (superClass.isAnon() && superClass.getId().toString().equals(restId)) {
                        rest = superClass;
                    }
                }   
            }
            
            /**
             * removing by graph subtraction so that statements with blank nodes
             * stick together and are processed appropriately by the bulk update
             * handler
             */
            if ( rest != null ) {
                Model temp = ModelFactory.createDefaultModel();
                temp.add(rest.listProperties());
                ontModel.getBaseModel().remove(temp);                
            }
            
        }
    }
    
    private void processCreate(VitroRequest request, EditProcessObject epo, OntModel origModel) {
        
        Model temp = ModelFactory.createDefaultModel();
        Model dynamicUnion = ModelFactory.createUnion(temp, origModel);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, dynamicUnion);
        
        OntProperty onProperty = ontModel.getOntProperty( (String) request.getParameter("onProperty") );
        
        String conditionTypeStr = request.getParameter("conditionType");
        
        String restrictionTypeStr = (String) epo.getAttribute("restrictionType");
        Restriction rest = null;
        
        OntClass ontClass = ontModel.getOntClass( (String) epo.getAttribute("VClassURI") );
        
        String roleFillerURIStr = request.getParameter("ValueClass");
        Resource roleFiller = null;
        if (roleFillerURIStr != null) {
            roleFiller = ontModel.getResource(roleFillerURIStr);
        }               
        
        int cardinality = -1;
        String cardinalityStr = request.getParameter("cardinality");
        if (cardinalityStr != null) {
            cardinality = Integer.decode(cardinalityStr); 
        }
        
        if (restrictionTypeStr.equals("allValuesFrom")) {
            rest = ontModel.createAllValuesFromRestriction(null,onProperty,roleFiller);
        } else if (restrictionTypeStr.equals("someValuesFrom")) {
            rest = ontModel.createSomeValuesFromRestriction(null,onProperty,roleFiller);
        } else if (restrictionTypeStr.equals("hasValue")) {
            String valueURI = request.getParameter("ValueIndividual");
            if (valueURI != null) {
                Resource valueRes = ontModel.getResource(valueURI);
                if (valueRes != null) {
                    rest = ontModel.createHasValueRestriction(null, onProperty, valueRes);
                }
            } else {
                String valueLexicalForm = request.getParameter("ValueLexicalForm");
                if (valueLexicalForm != null) {
                    String valueDatatype = request.getParameter("ValueDatatype");
                    Literal value = null;
                    if (valueDatatype != null && valueDatatype.length() > 0) {
                        RDFDatatype dtype = null;
                        try {
                            dtype = TypeMapper.getInstance().getSafeTypeByName(valueDatatype);
                        } catch (Exception e) {
                            log.warn ("Unable to get safe type " + valueDatatype + " using TypeMapper");
                        }
                        if (dtype != null) {
                            value = ontModel.createTypedLiteral(valueLexicalForm, dtype);
                        } else {
                            value = ontModel.createLiteral(valueLexicalForm);
                        }
                    } else {
                        value = ontModel.createLiteral(valueLexicalForm);
                    }
                    rest = ontModel.createHasValueRestriction(null, onProperty, value);
                }
            }
        } else if (restrictionTypeStr.equals("minCardinality")) {
            rest = ontModel.createMinCardinalityRestriction(null,onProperty,cardinality);
        } else if (restrictionTypeStr.equals("maxCardinality")) {
            rest = ontModel.createMaxCardinalityRestriction(null,onProperty,cardinality);
        } else if (restrictionTypeStr.equals("cardinality")) {
            rest = ontModel.createCardinalityRestriction(null,onProperty,cardinality);
        }
        
        if (conditionTypeStr.equals("necessary")) {
            ontClass.addSuperClass(rest);
        } else if (conditionTypeStr.equals("necessaryAndSufficient")) {
            ontClass.addEquivalentClass(rest);
        }
        
        origModel.add(temp);
        
    }
    
}
