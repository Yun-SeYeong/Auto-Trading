package demo.coin;

import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.CoinHistory;
import demo.coin.dao.CoinHistoryKey;
import demo.coin.repository.CoinHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@SpringBootTest
@Slf4j
public class CollectTest {

    @Autowired
    CoinHistoryRepository coinHistoryRepository;

    @Test
    String getTickerTest() {
        WebClient client = WebClient.create("https://api.bithumb.com/public/ticker");

        Mono<String> coinsMono = client.get()
                .uri("/ALL_KRW")
                .retrieve()
                .bodyToMono(String.class);

        String coins = coinsMono.block();

        System.out.println("coins = " + coins);
        return coins;
    }

    @Test
    Set<String> getCoinNames() {
        String json = getTickerTest();

        Set<String> names = JsonPath.read(json, "$.data.keys()");

        for (String name : names) {
            System.out.println("name = " + name);
        }
        return names;
    }

    @Test
    void saveCoins() {
        String json = getTickerTest();

        Map<String, Map<String, ?>> coinMap = JsonPath.read(json, "$.data");

        System.out.println("coinMap = " + coinMap);

        System.out.println("coinMap.get(\"BTC\"): " + coinMap.get("BTC"));
        System.out.println("coinMap.get(\"BTC\"): " + coinMap.get("BTC").get("opening_price"));

        for (String name: coinMap.keySet()) {
            if (!name.equals("date")) {
                System.out.println("name = " + name);

                System.out.println("coinMap: " + coinMap.get(name));

                CoinHistory coinHistory = CoinHistory.builder()
                        .createdTime(LocalDate.now())
                        .name(name)
                        .openingPrice(Double.parseDouble(coinMap.get(name).get("opening_price").toString()))
                        .minPrice(Double.parseDouble(coinMap.get(name).get("min_price").toString()))
                        .maxPrice(Double.parseDouble(coinMap.get(name).get("max_price").toString()))
                        .unitsTraded(Double.parseDouble(coinMap.get(name).get("units_traded").toString()))
                        .accTradeValue(Double.parseDouble(coinMap.get(name).get("acc_trade_value").toString()))
                        .prevClosingPrice(Double.parseDouble(coinMap.get(name).get("prev_closing_price").toString()))
                        .unitsTraded24H(Double.parseDouble(coinMap.get(name).get("units_traded_24H").toString()))
                        .accTradeValue24H(Double.parseDouble(coinMap.get(name).get("acc_trade_value_24H").toString()))
                        .fluctate24H(Double.parseDouble(coinMap.get(name).get("fluctate_24H").toString()))
                        .fluctateRate24H(Double.parseDouble(coinMap.get(name).get("fluctate_rate_24H").toString()))
                        .build();

                System.out.println("coinHistory = " + coinHistory);

                coinHistoryRepository.save(coinHistory);
            }
        }
    }

    @Test
    void getCoinHistory() {
        Set<String> coinNames = getCoinNames();
        WebClient client = WebClient.create("https://api.bithumb.com/public/candlestick");

        int total = coinNames.size() - 1;
        int i = 1;
        for (String coinName: coinNames) {
            if (!coinName.equals("date")){
                Mono<String> coinsMono = client.get()
                        .uri("/" + coinName + "_KRW_24H")
                        .retrieve()
                        .bodyToMono(String.class);

                String coins = coinsMono.block();
//                System.out.println("coins = " + coins);

                List<List<?>> coinList = JsonPath.read(coins, "$.data");

                List<CoinHistoryKey> coinHistoryKeyList = new ArrayList<>();

                for (List<?> coinDayHistory : coinList) {
                    Optional<CoinHistory> coinHistoryOptional = coinHistoryRepository.findById(CoinHistoryKey.builder()
                            .name(coinName)
                            .createdTime(Instant.ofEpochMilli((Long) coinDayHistory.get(0)).atZone(ZoneId.systemDefault()).toLocalDate())
                            .build());

                    if (coinHistoryOptional.isEmpty()){
                        CoinHistory coinHistory = CoinHistory.builder()
                                .name(coinName)
                                .createdTime(Instant.ofEpochMilli((Long) coinDayHistory.get(0)).atZone(ZoneId.systemDefault()).toLocalDate())
                                .openingPrice(Double.parseDouble(coinDayHistory.get(1).toString()))
                                .closingPrice(Double.parseDouble(coinDayHistory.get(2).toString()))
                                .maxPrice(Double.parseDouble(coinDayHistory.get(3).toString()))
                                .minPrice(Double.parseDouble(coinDayHistory.get(4).toString()))
                                .unitsTraded24H(Double.parseDouble(coinDayHistory.get(5).toString()))
                                .build();

                        System.out.println("coinHistory = " + coinHistory);

                        coinHistoryRepository.save(coinHistory);
                    }
                }
            }
            log.info("진행도: " + i + "/" + total + " [" + (((double) i/total)*100) + "%]");
            i++;
        }
    }
}
