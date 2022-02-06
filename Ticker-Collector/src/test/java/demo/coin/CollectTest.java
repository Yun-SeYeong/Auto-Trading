package demo.coin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.*;
import demo.coin.repository.CoinHistoryRepository;
import demo.coin.repository.DayCandleRepository;
import demo.coin.repository.MarketOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@SpringBootTest
@Slf4j
public class CollectTest {

    @Autowired
    CoinHistoryRepository coinHistoryRepository;

    @Autowired
    DayCandleRepository dayCandleRepository;

    @Autowired
    MarketOrderRepository marketOrderRepository;

    @Autowired
    ObjectMapper objectMapper;

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
    List<String> getMarketName() {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> coinNamesMono = client.get()
                .uri("/market/all")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String coinNames = coinNamesMono.block();
        System.out.println("coinNames = " + coinNames);

        List<String> nameList = JsonPath.read(coinNames, "$.*.market");
        System.out.println("nameList = " + nameList);

        return nameList;
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
    void saveCoinHistory() throws Exception{
        WebClient webHookClient = WebClient.create("https://hooks.slack.com/services");

        webHookClient.post()
                .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                .bodyValue(SlackMessage.builder()
                        .text("Start collecting...")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        List<String> marketNames = getMarketName();

        WebClient client = WebClient.create("https://api.upbit.com/v1");

        int i = 1;
        for (String marketName : marketNames) {
            Mono<String> candlesMono = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/candles/days")
                            .queryParam("market", marketName)
                            .queryParam("count", 2)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class);

            String candles = candlesMono.block();

            System.out.println("candles = " + candles);
            webHookClient.post()
                    .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                    .bodyValue(SlackMessage.builder()
                            .text("[COLLECT] COIN:" + marketName + " (" + i + "/" + marketNames.size() + ")[" + ((int) (((double) i/marketNames.size())*100)) + "%]")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<DayCandle> dayCandleList = objectMapper.readValue(candles, new TypeReference<>() {});

            dayCandleList.remove(dayCandleList.get(0));

            dayCandleRepository.saveAll(dayCandleList);

            Thread.sleep(1000);
            i++;
        }

        webHookClient.post()
                .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                .bodyValue(SlackMessage.builder()
                        .text("End collecting.")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Test
    void makeOrder() {
        LocalDateTime now = LocalDate.now().atStartOfDay();
        List<DayCandle> candleList = dayCandleRepository.findAllByLogic1(now.minusDays(1), now);

        System.out.println("candleList = " + candleList);

        int limit = 5;

        for (int i = 0; i < limit; i++) {
            MarketOrder order = MarketOrder.builder()
                    .market(candleList.get(i).getMarket())
                    .candleDateTimeUtc(candleList.get(i).getCandleDateTimeUtc())
                    .targetPrice((candleList.get(i).getHighPrice()
                            .subtract(candleList.get(i).getLowPrice()))
                            .multiply(new BigDecimal("0.6"))
                            .add(candleList.get(i).getTradePrice()))
                    .build();

            System.out.println("order = " + order);

            marketOrderRepository.save(order);
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
