package com.sol.product.etf.realtime;

import java.util.List;
import java.util.Set;

public final class EtfTickerWhitelist {

    // 위험버킷 (주식형 ETF) 11종
    // 안전버킷 (채권형/단기자금형 ETF) 15종
    public static final List<String> TICKERS = List.of(
            "433330", "446720", "452360", "476030", "411540",
            "292500", "399110", "493420", "484880", "0152E0", "0105E0",
            "438560", "438570", "474390", "488980", "0092C0",
            "0016X0", "436140", "0141T0", "469830", "363510",
            "497880", "484890", "461600", "447620", "0192S0"
    );

    private static final Set<String> TICKER_SET = Set.copyOf(TICKERS);

    private EtfTickerWhitelist() {}

    public static boolean contains(String ticker) {
        return TICKER_SET.contains(ticker);
    }
}
