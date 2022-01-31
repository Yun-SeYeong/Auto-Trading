package demo.coin.scheduler;

import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.CoinHistory;
import demo.coin.repository.CoinHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CoinScheduler {
    private final CoinHistoryRepository coinHistoryRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        System.out.println("Collecting start...");

        String json = getTickerTest();

        Map<String, Map<String, ?>> coinMap = JsonPath.read(json, "$.data");

        System.out.println("coinMap = " + coinMap);

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

        System.out.println("Collecting success!");
    }

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
}
