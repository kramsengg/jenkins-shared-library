package org.braintree.servicenow

enum ServiceNowErrors {
    INVALID_TEMPLATE(
        "Problem in creating record",
        "Action Plan, Verification Plan / Success Criteria, Back Out Plan field(s) required to create change ticket"
    ),
    INVALID_CONFIGURATION_ITEM(
        "is not a valid value for reference field cmdb_ci",
        "Error in creating record"
    )

    private String message
    private String detail

    private ServiceNowErrors(String message, String detail) {
        this.message = message
        this.detail = detail
    }

    public String getMessage() {
        return message
    }

    public String getDetail() {
        return detail
    }

    @Override
    public String toString() {
        return message + ": " + detail
    }
}