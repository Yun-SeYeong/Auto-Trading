package demo.coin.controller;

import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.CoinHistory;
import demo.coin.repository.CoinHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinHistoryServiceImpl implements CoinHistoryService{
    private final CoinHistoryRepository coinHistoryRepository;

    String getTicker() {
        WebClient client = WebClient.create("https://api.bithumb.com/public/ticker");

        Mono<String> coinsMono = client.get()
                .uri("/ALL_KRW")
                .retrieve()
                .bodyToMono(String.class);

        String coins = coinsMono.block();

        System.out.println("coins = " + coins);
        return coins;
    }

    Set<String> getCoinNames() {
        String json = getTicker();

        Set<String> names = JsonPath.read(json, "$.data.keys()");

        for (String name : names) {
            System.out.println("name = " + name);
        }
        return names;
    }

    @Override
    public void downloadHistory() {
        new Thread(() -> {
            Set<String> coinNames = getCoinNames();
            WebClient client = WebClient.create("https://api.bithumb.com/public/candlestick");

            int total = coinNames.size() - 1;
            int i = 1;

            for (String coinName: coinNames) {
                if (!coinName.equals("date")) {
                    Mono<String> coinsMono = client.get()
                            .uri("/" + coinName + "_KRW_24H")
                            .retrieve()
                            .bodyToMono(String.class);

                    String coins = coinsMono.block();
                    System.out.println("coins = " + coins);

                    List<List<?>> coinList = JsonPath.read(coins, "$.data");

                    System.out.println("coinList.get(0).get(0) = " + Double.parseDouble(coinList.get(0).get(0).toString()));
                    System.out.println("time"
                            + Instant.ofEpochMilli((Long) coinList.get(0).get(0)).atZone(ZoneId.systemDefault()).toLocalDate());

                    for (List<?> coinDayHistory : coinList) {
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
                    log.info("진행도: " + i + "/" + total + " [" + (((double) i/total)*100) + "%]");
                    i++;
                }
            }
        }).start();

    }
}
