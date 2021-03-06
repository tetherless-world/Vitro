/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.controller.edit;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.cornell.mannlib.vedit.beans.EditProcessObject;
import edu.cornell.mannlib.vedit.beans.FormObject;
import edu.cornell.mannlib.vedit.beans.Option;
import edu.cornell.mannlib.vedit.controller.BaseEditController;
import edu.cornell.mannlib.vitro.webapp.auth.permissions.SimplePermission;
import edu.cornell.mannlib.vitro.webapp.beans.Individual;
import edu.cornell.mannlib.vitro.webapp.beans.IndividualImpl;
import edu.cornell.mannlib.vitro.webapp.beans.VClass;
import edu.cornell.mannlib.vitro.webapp.controller.Controllers;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.dao.IndividualDao;
import edu.cornell.mannlib.vitro.webapp.dao.VClassDao;
import edu.cornell.mannlib.vitro.webapp.dao.WebappDaoFactory;

public class IndividualTypeRetryController extends BaseEditController {
	
	private static final Log log = LogFactory.getLog(IndividualTypeRetryController.class.getName());

	public void doGet (HttpServletRequest request, HttpServletResponse response) {
		if (!isAuthorizedToDisplayPage(request, response,
				SimplePermission.DO_BACK_END_EDITING.ACTIONS)) {
        	return;
        }

        //create an EditProcessObject for this and put it in the session
        EditProcessObject epo = super.createEpo(request);
		
        VitroRequest vreq = new VitroRequest(request);
        
        WebappDaoFactory wadf = vreq.getUnfilteredAssertionsWebappDaoFactory();
        IndividualDao iDao = wadf.getIndividualDao();
        VClassDao vcDao = wadf.getVClassDao();
        
        String individualURI = request.getParameter("IndividualURI");
        
        Individual ind = iDao.getIndividualByURI(individualURI);
        if (ind == null) {
        	ind = new IndividualImpl(individualURI);
        }
        request.setAttribute("individual", ind);
        
		List<VClass> allVClasses = vcDao.getAllVclasses();
	    sortForPickList(allVClasses, vreq);
			
		Set<String> prohibitedURIset = new HashSet<String>();
		for (Iterator<VClass> indClassIt = ind.getVClasses(false).iterator(); indClassIt.hasNext(); ) {
			VClass vc = indClassIt.next();
			if(vc.isAnonymous()) {
			    continue;
			}
			prohibitedURIset.add(vc.getURI());
			for (Iterator<String> djURIIt = vcDao.getDisjointWithClassURIs(vc.getURI()).iterator(); djURIIt.hasNext(); ) {
				String djURI = djURIIt.next();
	            prohibitedURIset.add(djURI);
			}
		}
		
		List<VClass> eligibleVClasses = new ArrayList<VClass>();
		for (VClass vc : allVClasses) {
		    if(vc.getURI() != null && !(prohibitedURIset.contains(vc.getURI()))) {
		        eligibleVClasses.add(vc);
		    }
		}
		
		FormObject foo = new FormObject();
		epo.setFormObject(foo);
		HashMap optionMap = new HashMap();
		foo.setOptionLists(optionMap);

		List<Option> typeOptionList = new ArrayList<Option>(); 
		for (VClass vc : eligibleVClasses) {
			Option opt = new Option(vc.getURI(), vc.getPickListName());
			typeOptionList.add(opt);
		}
		
		optionMap.put("types",typeOptionList);
		
	       RequestDispatcher rd = request.getRequestDispatcher(Controllers.BASIC_JSP);
	       request.setAttribute("editAction","individualTypeOp");
	        request.setAttribute("bodyJsp","/templates/edit/formBasic.jsp");
	        request.setAttribute("scripts","/templates/edit/formBasic.js");
        	request.setAttribute("formJsp","/templates/edit/specific/individualType_retry.jsp");
        	request.setAttribute("title","Individual Type Editing Form");
	        request.setAttribute("_action","insert");
	        setRequestAttributes(request,epo);

	        try {
	            rd.forward(request, response);
	        } catch (Exception e) {
	            log.error(this.getClass().getName()+" could not forward to view.");
	            log.error(e.getMessage());
	            log.error(e.getStackTrace());
	        }
	        
	}
	
	public void doPost (HttpServletRequest request, HttpServletResponse response) {
		// shouldn't be posting to this controller
	}	
	
}
