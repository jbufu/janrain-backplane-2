package com.janrain.backplane.server;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tom Raney
 */
public class Scope {

    public static final int MAX_PARAMETERS = 100;
    public static final String SEPARATOR = " ";
    public static final String DELIMITER = ":";

    public static final String[] VALID_KEYS = {"bus","sticky","channel","type","source","payload"};

    public static boolean isValidKey(String key) {
        for (String validKey: VALID_KEYS) {
            if (key.equals(validKey)) {
                return true;
            }
        }
        return false;
    }

    public Scope(String scopes) throws BackplaneServerException {
        this.scopes = parseScopeString(scopes);
    }

    //private

    private Map<String,ArrayList<String>> scopes;

    private Map<String,ArrayList<String>> parseScopeString(String scope) throws BackplaneServerException {
        HashMap<String,ArrayList<String>> scopes = new HashMap<String, ArrayList<String>>();

        if (StringUtils.isNotEmpty(scope)) {
            // TODO: is there a maximum length for the scope string?
            //
            String[] tokens = scope.split(Scope.SEPARATOR,Scope.MAX_PARAMETERS);
            // all scope tokens need to have the ":" key/value delimiter
            for (String token:tokens) {
                if (!token.contains(Scope.DELIMITER)) {
                    throw new BackplaneServerException("Malformed scope");
                }
                String[] keyValue = token.split(Scope.DELIMITER);
                if (!Scope.isValidKey(keyValue[0])) {
                    throw new BackplaneServerException(keyValue[0] + " is not a valid scope field name");
                }
                List<String> values = scopes.get(keyValue[0]);
                if (values == null) {
                    values = new ArrayList<String>();
                }
                values.add(keyValue[1]);
            }
        }
        return scopes;
    }

}
