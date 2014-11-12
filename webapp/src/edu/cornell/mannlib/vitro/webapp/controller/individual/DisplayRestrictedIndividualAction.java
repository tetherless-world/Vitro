package edu.cornell.mannlib.vitro.webapp.controller.individual;
import edu.cornell.mannlib.vitro.webapp.auth.requestedAction.ifaces.RequestedAction;
import edu.cornell.mannlib.vitro.webapp.beans.Individual;
import edu.cornell.mannlib.vitro.webapp.beans.UserAccount;
/**
 * Ask for authorization to display this individual to this user.
 */
public class DisplayRestrictedIndividualAction extends RequestedAction {
    private final UserAccount user;
    private final Individual individual;
    public DisplayRestrictedIndividualAction(UserAccount user, Individual individual) {
        this.user = user;
        this.individual = individual;
        
    }
    public UserAccount getUser() {
        return user;
    }
    public Individual getIndividual() {
        return individual;
    }
}