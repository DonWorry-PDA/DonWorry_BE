package com.sol.product.external.ls.dto;

public record LsWsRequest(Header header, Body body) {

    public record Header(String token, String tr_type) {}

    public record Body(String tr_cd, String tr_key) {}

    public static LsWsRequest subscribe(String token, String ticker) {
        return new LsWsRequest(
                new Header(token, "3"),
                new Body("US3", String.format("U%-9s", ticker))
        );
    }

    public static LsWsRequest unsubscribe(String token, String ticker) {
        return new LsWsRequest(
                new Header(token, "4"),
                new Body("US3", String.format("U%-9s", ticker))
        );
    }
}
