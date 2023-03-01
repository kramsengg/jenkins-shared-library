package org.braintree.servicenow.exceptions

import net.sf.json.JSONObject

class ServiceNowException extends Exception {
    public ServiceNowException(JSONObject json) {
        super("${json.error.message}: ${json.error.detail}")
    }

    public ServiceNowException(String message) {
        super(message)
    }
}
