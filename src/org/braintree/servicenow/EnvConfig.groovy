package org.braintree.servicenow

public class EnvConfig implements Serializable {
    public static final String GSNOW_QA = "gsnow-qa"
    public static final String GSNOW_QA_ENDPOINT = "https://eshome-qa.es.paypalcorp.com/gsnow/api/sn"
    public static final String GSNOW_QA_CREDENTIAL = "GSNOW_QA_TOKEN"

    public static final String GSNOW_PROD = "gsnow"
    public static final String GSNOW_PROD_ENDPOINT = "https://engineering.paypalcorp.com/gsnow/api/sn"
    public static final String GSNOW_PROD_CREDENTIAL = "GSNOW_PROD_TOKEN"

    public static final String SNOW_DEV = "snow-dev"
    public static final String SNOW_DEV_ENDPOINT = "https://paypaldevproject.service-now.com/api/payp2/table"
    public static final String SNOW_DEV_CREDENTIAL= "paypaldevproject-snow"
}
