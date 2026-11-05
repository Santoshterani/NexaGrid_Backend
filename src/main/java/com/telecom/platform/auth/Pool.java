package com.telecom.platform.auth;

import com.telecom.platform.common.ApiException;

/** Two separate login pools: operator-side staff and customer-side users. */
public enum Pool {
    STAFF("users", "u"), CUSTOMER("customer_users", "cu");

    public final String table;
    public final String alias;

    Pool(String table, String alias) {
        this.table = table;
        this.alias = alias;
    }

    public static Pool from(String s) {
        return switch (s.toLowerCase()) {
            case "staff" -> STAFF;
            case "customer" -> CUSTOMER;
            default -> throw ApiException.notFound("Unknown login pool");
        };
    }

    public String path() { return name().toLowerCase(); }
}
