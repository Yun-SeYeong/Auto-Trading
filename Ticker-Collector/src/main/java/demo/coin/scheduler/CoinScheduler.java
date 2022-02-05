package demo.coin.scheduler;

import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.CoinHistory;
import demo.coin.dao.CoinHistoryKey;
import demo.coin.repository.CoinHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoinScheduler {
    private final CoinHistoryRepository coinHistoryRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        log.info("Collecting start...");

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

        log.info("Collecting success!");
    }

    Set<String> getCoinNames() {
        String json = getTickerTest();

        Set<String> names = JsonPath.read(json, "$.data.keys()");

        return names;
    }

    String getTickerTest() {
        WebClient client = WebClient.create("https://api.bithumb.com/public/ticker");

        Mono<String> coinsMono = client.get()
                .uri("/ALL_KRW")
                .retrieve()
                .bodyToMono(String.class);

        String coins = coinsMono.block();

        return coins;
    }
}
